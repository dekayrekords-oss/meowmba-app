package com.example.meowmba.core.computer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A Development Studio project and the last thing that happened in it. */
data class StudioProject(val name: String, val lastEvent: String, val at: Long, val builds: Int = 0)

/**
 * Reads Development Studio's own terminal output (its fixed status lines, see
 * assets/computer/development_studio.py) and folds it into a per-project history. Pure, testable.
 */
object DevStudioLog {
    private val ansi = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]""")

    data class State(val projects: List<StudioProject> = emptyList(), val current: String? = null, val pending: String = "")

    fun fold(state: State, chunk: String, now: Long): State {
        val text = state.pending + ansi.replace(chunk, "").replace('\r', '\n')
        val lines = text.split('\n')
        var projects = state.projects
        var current = state.current
        for (raw in lines.dropLast(1)) {
            val line = raw.trim()
            if (line.startsWith("Project:")) {
                val name = line.removePrefix("Project:").trim().trimEnd('/').substringAfterLast('/').take(40)
                if (name.isNotBlank()) { current = name; projects = upsert(projects, name, "Opened", now) }
                continue
            }
            val name = current ?: continue
            val event = when {
                line.startsWith("Checkpoint saved:") -> "Checkpoint saved"
                line.startsWith("Changed files:") -> line.substringAfter(':').trim().let { "$it files changed" }
                line == "File decision saved." -> "Change reviewed"
                line.startsWith("Agent exited.") -> "Agent finished"
                line.startsWith("Created site/index.html") -> "Website started"
                line.startsWith("Built APK:") -> "APK built"
                line.startsWith("Exit code:") -> line.substringAfter(':').trim().let { if (it == "0") "Build succeeded" else "Build failed ($it)" }
                line.startsWith("Preview:") -> "Preview running"
                line.startsWith("Preview/export stopped") -> "Preview stopped"
                line.startsWith("Stopped:") -> "Stopped"
                line.startsWith("Development Studio closed") -> "Session closed"
                else -> null
            } ?: continue
            projects = upsert(projects, name, event, now, build = event == "APK built" || event == "Build succeeded")
        }
        return State(projects, current, lines.last().takeLast(2_000))
    }

    private fun upsert(list: List<StudioProject>, name: String, event: String, now: Long, build: Boolean = false): List<StudioProject> {
        val old = list.firstOrNull { it.name == name }
        val updated = StudioProject(name, event, now, (old?.builds ?: 0) + if (build) 1 else 0)
        return (listOf(updated) + list.filter { it.name != name }).take(12)
    }

    /** "3 min ago", "2 h ago", "4 d ago". */
    fun ago(at: Long, now: Long): String {
        val m = ((now - at) / 60_000L).coerceAtLeast(0)
        return when { m < 1 -> "just now"; m < 60 -> "$m min ago"; m < 60 * 24 -> "${m / 60} h ago"; else -> "${m / (60 * 24)} d ago" }
    }
}

/** On-phone list of Development Studio projects, for the Desk computer. Stays on this phone. */
class DevStudioProjects private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("dev_studio_projects", Context.MODE_PRIVATE)
    private var state = DevStudioLog.State(load())
    private val _projects = MutableStateFlow(state.projects)
    val projects: StateFlow<List<StudioProject>> = _projects

    @Synchronized fun record(output: String, now: Long = System.currentTimeMillis()) {
        val next = DevStudioLog.fold(state, output, now)
        state = next
        if (next.projects != _projects.value) { _projects.value = next.projects; save(next.projects) }
    }

    private fun load(): List<StudioProject> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { StudioProject(it.getString("n"), it.getString("e"), it.getLong("t"), it.optInt("b")) } }
    }.getOrDefault(emptyList())

    private fun save(list: List<StudioProject>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("n", it.name).put("e", it.lastEvent).put("t", it.at).put("b", it.builds)) }
        prefs.edit().putString(KEY, a.toString()).apply()
    }

    companion object {
        private const val KEY = "projects"
        @Volatile private var instance: DevStudioProjects? = null
        fun get(context: Context): DevStudioProjects = instance ?: synchronized(this) {
            instance ?: DevStudioProjects(context.applicationContext).also { instance = it }
        }
    }
}
