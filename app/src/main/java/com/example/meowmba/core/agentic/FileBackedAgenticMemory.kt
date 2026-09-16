package com.example.meowmba.core.agentic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File-backed second-brain MVP under the app files dir.
 *
 * Layout:
 *   files/agentic-memory/<projectKey>/notes.jsonl
 *   files/agentic-memory/exports/notes-<ts>.json
 *
 * Not a full git vault (git binary + pack plumbing is too heavy / unreliable
 * inside the APK). Durable project notes with JSON export/import instead.
 * Never stores secrets — callers must sanitize.
 */
class FileBackedAgenticMemoryIndex(
    context: Context,
    private val maxNotesPerGoal: Int = 64,
    rootDirName: String = "agentic-memory",
) : AgenticMemoryIndex {
    private val root: File =
        File(context.applicationContext.filesDir, rootDirName).also { it.mkdirs() }
    private val prefsFallback = PrefsAgenticMemoryIndex(context)

    override fun note(goalId: String, text: String): Boolean {
        val key = projectKey(goalId)
        val trimmed = text.trim()
        if (key.isEmpty() || trimmed.isEmpty()) return false
        // Keep prefs mirror for recall compatibility with older builds.
        prefsFallback.note(goalId, trimmed)
        synchronized(this) {
            val file = notesFile(key)
            file.parentFile?.mkdirs()
            val entry = JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("text", trimmed.take(500))
                .toString()
            file.appendText(entry + "\n")
            trimFile(file)
        }
        return true
    }

    override fun recall(goalId: String, limit: Int): List<String> {
        val key = projectKey(goalId)
        if (key.isEmpty()) return emptyList()
        synchronized(this) {
            val fromFile = loadNotes(key)
            if (fromFile.isNotEmpty()) return fromFile.takeLast(limit.coerceAtLeast(0))
        }
        return prefsFallback.recall(goalId, limit)
    }

    /** Export all project notes to a single JSON file under exports/. */
    fun exportAll(): File {
        val exports = File(root, "exports").also { it.mkdirs() }
        val out = File(exports, "notes-${System.currentTimeMillis()}.json")
        val rootObj = JSONObject()
        val projects = JSONObject()
        synchronized(this) {
            root.listFiles()
                ?.filter { it.isDirectory && it.name != "exports" }
                ?.forEach { dir ->
                    val arr = JSONArray()
                    loadNotes(dir.name).forEach { arr.put(it) }
                    projects.put(dir.name, arr)
                }
        }
        rootObj.put("version", 1)
        rootObj.put("exportedAt", System.currentTimeMillis())
        rootObj.put("projects", projects)
        out.writeText(rootObj.toString(2))
        return out
    }

    /** Import notes from a previously exported JSON file. Merges by project key. */
    fun importAll(file: File): Int {
        if (!file.isFile) return 0
        val rootObj = JSONObject(file.readText())
        val projects = rootObj.optJSONObject("projects") ?: return 0
        var imported = 0
        val keys = projects.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = projects.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val text = arr.optString(i, "").trim()
                if (text.isNotEmpty()) {
                    note(key, text)
                    imported++
                }
            }
        }
        return imported
    }

    fun listProjects(): List<String> =
        synchronized(this) {
            root.listFiles()
                ?.filter { it.isDirectory && it.name != "exports" }
                ?.map { it.name }
                ?.sorted()
                .orEmpty()
        }

    fun exportDir(): File = File(root, "exports").also { it.mkdirs() }

    private fun projectKey(goalId: String): String =
        goalId.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    private fun notesFile(project: String): File =
        File(File(root, project), "notes.jsonl")

    private fun loadNotes(project: String): List<String> {
        val file = notesFile(project)
        if (!file.isFile) return emptyList()
        return try {
            file.readLines()
                .mapNotNull { line ->
                    val t = line.trim()
                    if (t.isEmpty()) return@mapNotNull null
                    try {
                        JSONObject(t).optString("text").trim().takeIf { it.isNotEmpty() }
                    } catch (_: Exception) {
                        t.takeIf { it.isNotEmpty() }
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun trimFile(file: File) {
        val lines = file.readLines()
        if (lines.size <= maxNotesPerGoal) return
        file.writeText(lines.takeLast(maxNotesPerGoal).joinToString("\n", postfix = "\n"))
    }
}
