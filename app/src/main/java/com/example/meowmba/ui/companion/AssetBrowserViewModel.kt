package com.example.meowmba.ui.companion

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.files.*
import com.example.meowmba.core.cleanup.PhoneCleanup
import com.example.meowmba.core.media.ArtifactViewer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

enum class FilesOrganizeScope { LOCAL, PHONE }

data class LinkedAsset(val uri: Uri, val name: String, val folder: Boolean, val bytes: Long)
data class PhoneCleanupRow(val label: String, val detail: String, val bytes: Long)
data class AssetBrowserState(
    val entries: List<AssetEntry> = emptyList(), val truncated: Boolean = false,
    val busy: Boolean = false, val message: String? = null,
    val aiResults: List<AssetEntry>? = null, val moves: List<AssetMove> = emptyList(),
    val cleanup: List<CleanupCandidate>? = null, val trash: List<TrashEntry> = emptyList(),
    val linkedFolders: List<String> = emptyList(), val linkedItems: List<LinkedAsset> = emptyList(),
    val linkedUri: Uri? = null,
    val organizeScope: FilesOrganizeScope = FilesOrganizeScope.LOCAL,
    val phoneCleanup: List<PhoneCleanupRow> = emptyList(),
    val phoneCleanupNotes: List<String> = emptyList(),
    val mediaPermissionNeeded: Boolean = false,
)

class AssetBrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    val library = AssetLibrary(File(application.filesDir, "memba_workspace"))
    private val prefs = com.example.meowmba.core.backup.BackupPreferences.open(context, "asset_browser", 0)
    private val _state = MutableStateFlow(AssetBrowserState())
    val state = _state.asStateFlow()
    private var work: Job? = null
    init { refresh() }

    private fun job(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        work = viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(message = e.message ?: "File operation failed") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    private suspend fun reload() {
        val projects = com.example.meowmba.core.database.MeowmbaDatabase.getDatabase(context).projectDao().getAll().first()
        val scan = withContext(Dispatchers.IO) {
            library.ensureFolders()
            projects.forEach { project ->
                val folder = library.resolve("projects/project-${project.id}")
                check(folder.mkdirs() || folder.isDirectory)
                val manifest = File(folder, "project.json")
                if (manifest.createNewFile()) {
                    manifest.writeText(kotlinx.serialization.json.buildJsonObject {
                        put("name", kotlinx.serialization.json.JsonPrimitive(project.name))
                        put("projectId", kotlinx.serialization.json.JsonPrimitive(project.id))
                        put("conversationId", kotlinx.serialization.json.JsonPrimitive(project.conversationId))
                        put("kind", kotlinx.serialization.json.JsonPrimitive(project.kind))
                    }.toString())
                }
            }
            val snapshot = library.scan()
            val names = projects.associate { "projects/project-${it.id}" to it.name }
            snapshot.copy(entries = snapshot.entries.map { entry -> names[entry.path]?.let { entry.copy(name = it) } ?: entry })
        }
        val trash = withContext(Dispatchers.IO) { library.trashItems() }
        val scope = when (prefs.getString("organize_scope", "LOCAL")) {
            "PHONE" -> FilesOrganizeScope.PHONE
            else -> FilesOrganizeScope.LOCAL
        }
        _state.update { it.copy(entries = scan.entries, truncated = scan.truncated, trash = trash,
            linkedFolders = prefs.getStringSet("folders", emptySet()).orEmpty().sorted(),
            organizeScope = scope) }
    }
    fun refresh() = job { reload() }
    fun clearResults() { _state.update { it.copy(aiResults = null, moves = emptyList(), cleanup = null, phoneCleanup = emptyList(), phoneCleanupNotes = emptyList()) } }
    fun dismissPlan() { _state.update { it.copy(moves = emptyList(), cleanup = null, phoneCleanup = emptyList(), phoneCleanupNotes = emptyList()) } }
    fun message(text: String) { _state.update { it.copy(message = text) } }
    fun createFolder(path: String) = job { withContext(Dispatchers.IO) { library.createFolder(path) }; reload() }
    fun open(item: AssetEntry) {
        ArtifactViewer.open(context, library.resolve(item.path)).fold(::message) { message(it.message ?: "No compatible viewer") }
    }
    fun share(item: AssetEntry) = runCatching {
        val file = library.resolve(item.path)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = ArtifactViewer.mimeFor(file); putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(item.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${item.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { message(it.message ?: "Could not share") }
    fun importFiles(uris: List<Uri>, folder: String) = job {
        require(uris.size <= 100) { "Import up to 100 files at a time" }
        var imported = 0
        val failures = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            for (uri in uris) {
                ensureActive()
                try {
                    var name = "imported_asset"
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
                    }
                    context.contentResolver.openInputStream(uri)?.use { library.import(name, folder, it) } ?: error("Cannot read $name")
                    imported++
                } catch (e: Exception) { failures += (e.message ?: "Import failed") }
            }
        }
        reload(); message("Imported $imported file(s). Originals are unchanged." + if (failures.isNotEmpty()) " ${failures.size} failed: ${failures.first()}" else "")
    }
    fun export(item: AssetEntry, destination: Uri) = job {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(destination)?.use { out -> library.resolve(item.path).inputStream().use { it.copyTo(out) } }
                ?: error("Cannot write export")
        }
        message("Exported ${item.name}. The library copy is unchanged.")
    }
    fun setOrganizeScope(scope: FilesOrganizeScope) {
        prefs.edit().putString("organize_scope", scope.name).apply()
        _state.update {
            it.copy(
                organizeScope = scope,
                cleanup = if (scope == FilesOrganizeScope.LOCAL) it.cleanup else null,
                phoneCleanup = if (scope == FilesOrganizeScope.PHONE) it.phoneCleanup else emptyList(),
                phoneCleanupNotes = if (scope == FilesOrganizeScope.PHONE) it.phoneCleanupNotes else emptyList(),
                message = when (scope) {
                    FilesOrganizeScope.LOCAL -> "Local library — app-private workspace only."
                    FilesOrganizeScope.PHONE -> if (PhoneCleanup.mediaLibraryEnabled)
                        "Phone files — MediaStore + connected folders (read-only until imported)."
                        else "Choose phone files or connect a folder. Only imported copies can be organized with AI."
                },
            )
        }
    }
    fun cleanup() = job {
        when (_state.value.organizeScope) {
            FilesOrganizeScope.LOCAL -> {
                val (items, limited) = withContext(Dispatchers.IO) { library.cleanup() }
                _state.update {
                    it.copy(
                        cleanup = items,
                        phoneCleanup = emptyList(),
                        phoneCleanupNotes = emptyList(),
                        mediaPermissionNeeded = false,
                        message = if (limited) "Partial scan: file count, depth or 1 GB duplicate-comparison limit reached."
                        else "Review suggestions in your local library. Large and empty files may still be important.",
                    )
                }
            }
            FilesOrganizeScope.PHONE -> {
                if (!PhoneCleanup.mediaLibraryEnabled) {
                    _state.update { it.copy(cleanup = null, phoneCleanup = emptyList(),
                        phoneCleanupNotes = listOf(PhoneCleanup.pickerGuidance), mediaPermissionNeeded = false,
                        message = PhoneCleanup.pickerGuidance) }
                    return@job
                }
                val mediaOk = PhoneCleanup.hasMediaPermission(context)
                if (!mediaOk) {
                    _state.update {
                        it.copy(
                            cleanup = null,
                            phoneCleanup = emptyList(),
                            phoneCleanupNotes = listOf("Need photo/file access to scan phone media. Allow it, then Clean up again."),
                            mediaPermissionNeeded = true,
                            message = "Phone cleanup needs media permission. Connected folders below still work read-only.",
                        )
                    }
                    return@job
                }
                val plan = withContext(Dispatchers.IO) { PhoneCleanup.scan(context, "all") }
                val rows = plan.items.take(40).map { item ->
                    PhoneCleanupRow(
                        label = item.label,
                        detail = item.why + if (item.kind.isNotBlank()) " · ${item.kind}" else "",
                        bytes = item.bytes,
                    )
                }
                _state.update {
                    it.copy(
                        cleanup = null,
                        phoneCleanup = rows,
                        phoneCleanupNotes = plan.notes,
                        mediaPermissionNeeded = false,
                        message = if (rows.isEmpty()) PhoneCleanup.format(plan)
                        else "Found ${rows.size} phone items worth a look. Deletes always go through Android confirm sheets.",
                    )
                }
            }
        }
    }
    fun organizePhoneApps(existing: List<DrawerFolder>, apps: List<DrawerApp>, onDone: (List<DrawerFolder>, String) -> Unit) = job {
        val (next, msg) = withContext(Dispatchers.IO) { PhoneCleanup.organizeApps(existing, apps) }
        onDone(next, msg)
        _state.update { it.copy(message = msg) }
    }
    fun moveFiles(plans: List<AssetMove>) = job {
        val failed = mutableListOf<String>(); var done = 0
        withContext(Dispatchers.IO) { plans.forEach { plan ->
            runCatching { library.move(plan) }.onSuccess { done++ }.onFailure { failed += "${plan.source.name}: ${it.message}" }
        } }
        dismissPlan(); reload(); message("Moved $done file(s)." + if (failed.isNotEmpty()) " ${failed.joinToString("; ")}" else "")
    }
    fun trashFiles(items: List<AssetEntry>) = job {
        val failed = mutableListOf<String>(); var done = 0
        withContext(Dispatchers.IO) { items.forEach { item ->
            runCatching { library.trash(item) }.onSuccess { done++ }.onFailure { failed += "${item.name}: ${it.message}" }
        } }
        dismissPlan(); reload(); message("Moved $done file(s) to Trash. Restore them from Trash; space is freed only after permanent deletion." + if (failed.isNotEmpty()) " ${failed.joinToString("; ")}" else "")
    }
    fun restore(id: String) = job { withContext(Dispatchers.IO) { library.restore(id) }; reload(); message("File restored to its original folder.") }
    fun purge(id: String) = job { withContext(Dispatchers.IO) { library.purge(id) }; reload(); message("File permanently deleted. It cannot be restored from Trash.") }

    fun askAi(query: String, organize: Boolean, selected: List<AssetEntry>, ask: suspend (String) -> String) = job {
        val entries = (selected.ifEmpty { _state.value.entries }).filter { !it.folder }.sortedByDescending { it.modified }.take(200)
        require(entries.isNotEmpty()) { "Add some files first" }
        val reply = withTimeout(90_000) { ask(AssetAiPlanner.prompt(query, entries, organize)) }
        if (organize) {
            val plans = withContext(Dispatchers.IO) { AssetAiPlanner.moves(reply, entries, library) }
            _state.update { it.copy(moves = plans, message = if (plans.isEmpty()) "AI proposed no moves." else "Review ${plans.size} proposed moves. Nothing has moved yet.") }
        } else {
            val results = AssetAiPlanner.matches(reply, entries)
            _state.update { it.copy(aiResults = results, message = "${results.size} AI matches from ${entries.size} catalog entries. Metadata only; image/video contents were not analyzed.") }
        }
    }

    fun linkFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putStringSet("folders", prefs.getStringSet("folders", emptySet()).orEmpty() + uri.toString()).apply()
            _state.update { it.copy(linkedFolders = prefs.getStringSet("folders", emptySet()).orEmpty().sorted()) }
            browseLinked(uri)
        }.onFailure { message(it.message ?: "Folder permission was not granted") }
    }
    fun unlinkFolder(value: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(value), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val folders = prefs.getStringSet("folders", emptySet()).orEmpty() - value
        prefs.edit().putStringSet("folders", folders).apply()
        _state.update { it.copy(linkedFolders = folders.sorted(), linkedUri = null, linkedItems = emptyList()) }
    }
    fun closeLinked() { _state.update { it.copy(linkedUri = null, linkedItems = emptyList()) } }
    fun browseLinked(uri: Uri) = job {
        val items = withContext(Dispatchers.IO) {
            val id = if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri) else DocumentsContract.getTreeDocumentId(uri)
            val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id)
            val rows = mutableListOf<LinkedAsset>()
            context.contentResolver.query(childUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && rows.size < 500) {
                    rows += LinkedAsset(DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0)), cursor.getString(1) ?: "File",
                        cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR, cursor.getLong(3))
                }
            } ?: error("Folder is unavailable. Reconnect it with the folder picker.")
            rows.sortedWith(compareBy<LinkedAsset> { !it.folder }.thenBy { it.name.lowercase() })
        }
        _state.update { it.copy(linkedUri = uri, linkedItems = items, message = "Read-only phone folder (up to 500 entries). Import files to make them available to AI search and organizing.") }
    }
}
