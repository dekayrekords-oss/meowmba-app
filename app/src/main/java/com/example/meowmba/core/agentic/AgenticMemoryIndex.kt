package com.example.meowmba.core.agentic

/**
 * Phase 7 optional stub: second-brain / agentic memory index.
 *
 * Intentionally **does not** touch [com.example.meowmba.core.memory.MemoryCore]
 * primitives (VectorCodec, RecallEngine, MemoryCommandParser, PromptBuilder).
 * A later Git / vault second-brain can implement this interface without
 * changing companion MemoryRepository wiring.
 */
interface AgenticMemoryIndex {
    /** Index a short note for a goal. Returns false when not enabled / no-op. */
    fun note(goalId: String, text: String): Boolean

    /** Retrieve recent notes for a goal (empty when stub). */
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
