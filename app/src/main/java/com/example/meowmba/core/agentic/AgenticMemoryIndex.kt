package com.example.meowmba.core.agentic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Phase 7 optional stub: second-brain / agentic memory index.
 *
 * Intentionally does not touch companion MemoryCore primitives.
 * A later Git / vault second-brain can implement this interface without
 * changing companion MemoryRepository wiring.
 */
interface AgenticMemoryIndex {
    /** Index a short note for a goal / project key. Returns false when not enabled / no-op. */
    fun note(goalId: String, text: String): Boolean

    /** Retrieve recent notes for a goal / project key (empty when stub). */
    fun recall(goalId: String, limit: Int = 5): List<String>
}

/** Default no-op stub — safe to construct when the agentic flag is OFF. */
class NoOpAgenticMemoryIndex : AgenticMemoryIndex {
    override fun note(goalId: String, text: String): Boolean = false
    override fun recall(goalId: String, limit: Int): List<String> = emptyList()
}

/**
 * Process-local in-memory notes when agentic runtime is ON.
 * Bounded; never stores secrets (callers must sanitize).
 */
class InMemoryAgenticMemoryIndex(
    private val maxNotesPerGoal: Int = 32,
) : AgenticMemoryIndex {
    private val notes = java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>()

    override fun note(goalId: String, text: String): Boolean {
        val trimmed = text.trim()
        if (goalId.isBlank() || trimmed.isEmpty()) return false
        val bucket = notes.getOrPut(goalId) { mutableListOf() }
        synchronized(bucket) {
            bucket.add(trimmed.take(500))
            while (bucket.size > maxNotesPerGoal) bucket.removeAt(0)
        }
        return true
    }

    override fun recall(goalId: String, limit: Int): List<String> {
        val bucket = notes[goalId] ?: return emptyList()
        synchronized(bucket) {
            return bucket.takeLast(limit.coerceAtLeast(0))
        }
    }
}

/**
 * SharedPreferences-backed notes keyed by project/goal id.
 * Survives process death; still never stores secrets.
 */
class PrefsAgenticMemoryIndex(
    context: Context,
    private val maxNotesPerGoal: Int = 32,
    prefsName: String = "agentic_memory_index",
) : AgenticMemoryIndex {
    private val prefs: SharedPreferences =
        com.example.meowmba.core.backup.BackupPreferences.open(context.applicationContext, prefsName, Context.MODE_PRIVATE)

    override fun note(goalId: String, text: String): Boolean {
        val key = projectKey(goalId)
        val trimmed = text.trim()
        if (key.isEmpty() || trimmed.isEmpty()) return false
        synchronized(this) {
            val list = load(key).toMutableList()
            list.add(trimmed.take(500))
            while (list.size > maxNotesPerGoal) list.removeAt(0)
            save(key, list)
        }
        return true
    }

    override fun recall(goalId: String, limit: Int): List<String> {
        val key = projectKey(goalId)
        if (key.isEmpty()) return emptyList()
        synchronized(this) {
            return load(key).takeLast(limit.coerceAtLeast(0))
        }
    }

    private fun projectKey(goalId: String): String = goalId.trim().take(120)

    private fun prefKey(project: String): String = "notes_$project"

    private fun load(project: String): List<String> {
        val raw = prefs.getString(prefKey(project), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "").trim()
                    if (s.isNotEmpty()) add(s)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(project: String, notes: List<String>) {
        val arr = JSONArray()
        notes.forEach { arr.put(it) }
        prefs.edit().putString(prefKey(project), arr.toString()).apply()
    }
}
