package com.example.meowmba.core.work

import com.example.meowmba.core.agent.runtime.AgentTask
import com.example.meowmba.core.agent.runtime.TaskStatus
import com.example.meowmba.core.files.AssetLibrary
import com.example.meowmba.core.media.ArtifactViewer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable enum class ProjectTaskStatus {
    QUEUED, RUNNING, WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED, INTERRUPTED;
    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == WAITING_APPROVAL
}

@Serializable data class ProjectPlanStep(
    val id: String, val title: String, val completedByUser: Boolean = false,
)

@Serializable data class ProjectTaskRecord(
    val id: String, val title: String, val agentId: String, val toolId: String,
    val status: ProjectTaskStatus, val createdAt: Long, val updatedAt: Long,
    val resultSummary: String = "", val runtimeId: String = "",
)

@Serializable data class ProjectArtifact(
    /** Workspace-relative path, never a content URI or an unrestricted filesystem path. */
    val path: String, val name: String, val category: String, val bytes: Long,
    val modified: Long, val available: Boolean, val inProjectFolder: Boolean,
)

@Serializable data class ProjectWorkSnapshot(
    val projectId: Long, val name: String = "", val kind: String = "OTHER",
    val objective: String = "", val notes: String = "",
    val steps: List<ProjectPlanStep> = emptyList(),
    val tasks: List<ProjectTaskRecord> = emptyList(),
    val artifacts: List<ProjectArtifact> = emptyList(),
    val updatedAt: Long = 0L, val scanTruncated: Boolean = false,
    val warnings: List<String> = emptyList(), val version: Int = 1,
) {
    val outputFolder: String get() = ProjectWorkStore.outputFolder(projectId)
    val currentTasks: List<ProjectTaskRecord> get() = tasks.filter { it.status.isActive }
    val completedTaskCount: Int get() = tasks.count { it.status == ProjectTaskStatus.COMPLETED }
    val remainingStepCount: Int get() = steps.count { !it.completedByUser }
}

/**
 * Saved project context, not a second executor. Call on an I/O dispatcher. The
 * Room project ID is the stable join key; all task updates must use the project
 * captured at submission, never whichever project happens to be open now.
 *
 * Metadata is deliberately OUTSIDE Workspace: file.write cannot forge status or
 * a completed plan by editing a workspace file. Each checkpoint is bounded,
 * fsynced and atomically replaced. A process restart marks old active records
 * INTERRUPTED; it cannot replay tools or resurrect an approval.
 */
class ProjectWorkStore(
    private val workspaceRoot: File,
    private val metadataRoot: File = File(workspaceRoot.absoluteFile.parentFile, "project_state"),
    private val now: () -> Long = System::currentTimeMillis,
    private val runtimeId: String = PROCESS_RUNTIME_ID,
) {
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val library = AssetLibrary(workspaceRoot)
    internal val allocationJournal get() = ProjectAllocationJournal(File(metadataRoot, "allocations.json"))

    init {
        require(runtimeId.isNotBlank() && runtimeId.length <= 160) { "A bounded runtime identity is required" }
        val root = workspaceRoot.canonicalFile
        val metadata = metadataRoot.canonicalFile
        require(metadata != root && !metadata.path.startsWith(root.path + File.separator)) {
            "Project progress metadata must be outside the writable Files library"
        }
        require(!Files.isSymbolicLink(metadataRoot.toPath())) { "Linked progress storage is not supported" }
    }

    fun load(id: Long): ProjectWorkSnapshot = synchronized(LOCK) { loadLocked(id) }

    fun ensure(id: Long, name: String, kind: String, objective: String = ""): ProjectWorkSnapshot = synchronized(LOCK) {
        require(name.isNotBlank() && name.length <= 160) { "Choose a project name up to 160 characters" }
        val folder = library.resolve(outputFolder(id))
        check(folder.mkdirs() || folder.isDirectory) { "Project output folder is unavailable" }
        val old = loadLocked(id)
        saveIfChanged(old, old.copy(name = name.trim(), kind = kind.take(32),
            objective = if (old.objective.isBlank()) bounded(objective, MAX_OBJECTIVE, "Objective") else old.objective))
    }

    /** A saved plan is a proposal. Only setStepDone, called for an explicit user
     * check, marks it complete; an LLM plan does not prove tools have executed. */
    fun savePlan(id: Long, objective: String, steps: List<String>, notes: String = ""): ProjectWorkSnapshot = synchronized(LOCK) {
        require(steps.size <= MAX_STEPS) { "Use at most $MAX_STEPS plan steps" }
        val titles = steps.map { bounded(it, MAX_STEP_TITLE, "Step").trim().also { title -> require(title.isNotBlank()) { "Plan steps cannot be blank" } } }
        val old = loadLocked(id)
        // A changed objective is a new proposal, not evidence that old checks
        // still apply merely because a generic step title was reused.
        val remaining = if (objective.trim() == old.objective) old.steps.toMutableList() else mutableListOf()
        val plan = titles.map { title ->
            val match = remaining.indexOfFirst { it.title == title }
            if (match >= 0) remaining.removeAt(match) else ProjectPlanStep(UUID.randomUUID().toString(), title)
        }
        saveIfChanged(old, old.copy(objective = bounded(objective, MAX_OBJECTIVE, "Objective"),
            notes = bounded(notes, MAX_NOTES, "Notes"), steps = plan))
    }

    /** Tool updates omit fields to keep their latest saved values, not a stale
     * copy read before an approval wait or a concurrent user edit. */
    fun updatePlan(id: Long, steps: List<String>, objective: String? = null, notes: String? = null): ProjectWorkSnapshot = synchronized(LOCK) {
        val old = loadLocked(id)
        savePlan(id, objective ?: old.objective, steps, notes ?: old.notes)
    }

    /** A slow model draft cannot overwrite a brief/plan the user edited meanwhile. */
    fun saveDraftIfUnchanged(expected: ProjectWorkSnapshot, steps: List<String>): ProjectWorkSnapshot = synchronized(LOCK) {
        val latest = loadLocked(expected.projectId)
        require(latest.objective == expected.objective && latest.steps == expected.steps && latest.notes == expected.notes) {
            "The project changed while planning. Your current brief and plan were kept."
        }
        savePlan(expected.projectId, expected.objective, steps, expected.notes)
    }

    fun setStepDone(id: Long, stepId: String, done: Boolean): ProjectWorkSnapshot = synchronized(LOCK) {
        val old = loadLocked(id)
        require(old.steps.any { it.id == stepId }) { "That plan step no longer exists" }
        saveIfChanged(old, old.copy(steps = old.steps.map { if (it.id == stepId) it.copy(completedByUser = done) else it }))
    }

    fun recordTask(id: Long, task: AgentTask): ProjectWorkSnapshot = synchronized(LOCK) {
        require(task.projectId == null || task.projectId == id) { "Task belongs to a different project" }
        require(task.id.isNotBlank() && task.id.length <= 160) { "A bounded task identity is required" }
        val old = loadLocked(id)
        val existing = old.tasks.firstOrNull { it.id == task.id }
        val status = ProjectTaskStatus.valueOf(task.status.name)
        // A delayed queued/running snapshot cannot undo an observed terminal.
        if (existing != null && !existing.status.isActive && existing.status != ProjectTaskStatus.INTERRUPTED && status.isActive) return@synchronized old
        if (existing != null && existing.status != ProjectTaskStatus.QUEUED && status == ProjectTaskStatus.QUEUED) return@synchronized old
        val summary = task.result.orEmpty().take(MAX_RESULT_SUMMARY * 4).lineSequence().filterNot { it.trimStart().startsWith("MEDIA_FILE:") }
            .joinToString("\n").take(MAX_RESULT_SUMMARY)
        if (existing != null && existing.status == status && existing.resultSummary == summary &&
            existing.title == task.title.take(MAX_TASK_TITLE) && existing.runtimeId == runtimeId) return@synchronized old
        val record = ProjectTaskRecord(task.id, task.title.take(MAX_TASK_TITLE), task.agentId.take(80), task.toolId.take(100),
            status, task.createdAt, task.completedAt ?: now(), summary, runtimeId)
        val all = listOf(record) + old.tasks.filterNot { it.id == task.id }
        // Retain active records preferentially; never silently hide running work.
        require(all.count { it.status.isActive } <= MAX_TASKS) { "Too many active project tasks" }
        val retained = (all.filter { it.status.isActive } + all.filterNot { it.status.isActive }).take(MAX_TASKS)
        var warnings = old.warnings
        if (all.size > MAX_TASKS) warnings = warnings + "Only the latest $MAX_TASKS observed task records are retained."
        val outputs = if (task.status == TaskStatus.COMPLETED) ArtifactViewer.resultsForTool(task.toolId, task.result.orEmpty().takeLast(16_384)) else emptyList()
        val references = outputs.mapNotNull { file ->
            runCatching { artifact(library.entry(file).path, id) }.getOrNull()
        }
        if (references.size < outputs.size) warnings = warnings + "Some output files were outside the accessible Files library and were not indexed."
        val artifacts = (references + old.artifacts).distinctBy { it.path }
        if (artifacts.size > MAX_ARTIFACTS) warnings = warnings + "The saved artifact index is limited to $MAX_ARTIFACTS entries."
        saveIfChanged(old, old.copy(tasks = retained, artifacts = artifacts.take(MAX_ARTIFACTS),
            scanTruncated = old.scanTruncated || artifacts.size > MAX_ARTIFACTS, warnings = warnings.distinct().take(12)))
    }

    /** Only this project's folder is enumerated. Explicit tool output references
     * elsewhere in Files are rechecked individually, never moved or copied. */
    fun snapshot(id: Long): ProjectWorkSnapshot = synchronized(LOCK) {
        val old = loadLocked(id)
        val folderPath = outputFolder(id)
        val folder = library.resolve(folderPath)
        val scan = if (folder.isDirectory) AssetLibrary(folder).scan(SCAN_LIMIT) else null
        val scannedFiles = scan?.entries.orEmpty().filterNot { it.folder }
        val discovered = scannedFiles.mapNotNull { entry ->
            runCatching { artifact("$folderPath/${entry.path}", id) }.getOrNull()
        }
        val prior = old.artifacts.mapNotNull { item -> runCatching { artifact(item.path, id) }.getOrNull() }
        val all = (discovered + prior).distinctBy { it.path }.sortedByDescending { it.modified }
        saveIfChanged(old, old.copy(artifacts = all.take(MAX_ARTIFACTS), scanTruncated = scan?.truncated == true ||
            all.size > MAX_ARTIFACTS || discovered.size < scannedFiles.size || prior.size < old.artifacts.size))
    }

    private fun artifact(path: String, projectId: Long): ProjectArtifact {
        val file = library.resolve(path)
        val available = file.isFile && file.length() > 0L
        return ProjectArtifact(path, file.name, AssetLibrary.category(file.name), if (file.isFile) file.length() else 0L,
            if (file.exists()) file.lastModified() else 0L, available, path.startsWith(outputFolder(projectId) + "/"))
    }

    private fun loadLocked(id: Long): ProjectWorkSnapshot {
        val file = metadataFile(id)
        if (!file.exists()) return ProjectWorkSnapshot(id)
        val stored = try {
            require(file.isFile && file.length() <= MAX_CHECKPOINT_BYTES) { "Project checkpoint is too large" }
            val bytes = file.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_CHECKPOINT_BYTES) { "Project checkpoint is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            codec.decodeFromString<ProjectWorkSnapshot>(bytes.toString(Charsets.UTF_8)).also {
                require(it.version == 1 && it.projectId == id) { "Unsupported project checkpoint" }
                require(it.steps.size <= MAX_STEPS && it.tasks.size <= MAX_TASKS && it.artifacts.size <= MAX_ARTIFACTS)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Saved project progress could not be read; existing data was kept.", e)
        }
        val recovered = stored.copy(tasks = stored.tasks.map {
            if (it.status.isActive && it.runtimeId != runtimeId) it.copy(status = ProjectTaskStatus.INTERRUPTED,
                updatedAt = now(), resultSummary = "App restarted before a final result was observed. Review before retrying; an external action may already have happened.") else it
        })
        return saveIfChanged(stored, recovered)
    }

    private fun metadataFile(id: Long): File {
        require(id > 0L) { "A saved project ID is required" }
        require(!Files.isSymbolicLink(metadataRoot.toPath())) { "Linked progress storage is not supported" }
        return File(metadataRoot, "project-$id.json").also {
            require(!Files.isSymbolicLink(it.toPath())) { "Linked project checkpoints are not supported" }
        }
    }

    private fun saveIfChanged(old: ProjectWorkSnapshot, candidate: ProjectWorkSnapshot): ProjectWorkSnapshot {
        if (old == candidate) return old
        val next = candidate.copy(updatedAt = now())
        val target = metadataFile(next.projectId)
        check(metadataRoot.mkdirs() || metadataRoot.isDirectory) { "Project progress storage is unavailable" }
        val bytes = codec.encodeToString(next).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_CHECKPOINT_BYTES) { "Project checkpoint is full" }
        val pending = Files.createTempFile(metadataRoot.toPath(), "project-${next.projectId}-", ".pending")
        try {
            FileOutputStream(pending.toFile()).use { it.write(bytes); it.fd.sync() }
            try {
                Files.move(pending, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(pending) }
        return next
    }

    companion object {
        const val MAX_STEPS = 12
        const val MAX_OBJECTIVE = 2000
        const val MAX_NOTES = 3000
        const val MAX_STEP_TITLE = 240
        const val MAX_TASK_TITLE = 240
        const val MAX_RESULT_SUMMARY = 1200
        const val MAX_TASKS = 120
        const val MAX_ARTIFACTS = 200
        const val SCAN_LIMIT = 750
        const val MAX_CHECKPOINT_BYTES = 1_000_000
        private val PROCESS_RUNTIME_ID = UUID.randomUUID().toString()
        private val LOCK = Any()

        fun outputFolder(id: Long): String {
            require(id > 0L) { "A saved project ID is required" }
            return "projects/project-$id"
        }
        fun context(snapshot: ProjectWorkSnapshot, maxChars: Int = 4000): String = ProjectWorkContext.render(snapshot, maxChars)
        private fun bounded(value: String, limit: Int, label: String): String {
            require(value.length <= limit) { "$label must be at most $limit characters" }
            return value.trim()
        }
    }
}
