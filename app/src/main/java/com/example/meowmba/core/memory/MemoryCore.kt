package com.example.meowmba.core.memory

import com.example.meowmba.core.audio.WakePhraseMatcher
import com.example.meowmba.core.database.ImportanceLevel
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.core.database.MessageEntity
import com.example.meowmba.core.database.MessageRole
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * Pure, Android-free memory primitives. Everything in this file is
 * unit-testable on the JVM.
 */

/** Lossless FloatArray <-> ByteArray codec for Room BLOB storage. */
object VectorCodec {
    fun encode(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.float }
    }
}

/**
 * Deterministic on-device text embedder (signed feature hashing over word
 * tokens, L2-normalized). It runs anywhere with zero model files and gives
 * honest keyword-level similarity. Swap in a MediaPipe Text Embedder (or a
 * server embedder) behind [EmbeddingInterface] for true semantic vectors —
 * recall ranking does not change.
 */
class LocalHashEmbedder(private val dimensions: Int = 128) : EmbeddingInterface {
    override suspend fun getEmbedding(text: String): FloatArray {
        val vector = FloatArray(dimensions)
        val tokens = WakePhraseMatcher.normalize(text).split(' ').filter { it.isNotBlank() }
        for (token in tokens) {
            val h = token.hashCode()
            val idx = abs(h) % dimensions
            vector[idx] += if (h % 2 == 0) 1f else -1f
        }
        val norm = sqrt(vector.fold(0f) { acc, v -> acc + v * v })
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}

/** One recalled memory plus the explanation the user is entitled to see. */
data class RecallResult(
    val memory: MemoryEntity,
    val score: Float,
    val reason: String
)

/**
 * Ranks candidate memories against a query embedding: cosine similarity
 * weighted by importance. Every result carries a human-readable reason so
 * the UI can show WHY a memory was recalled (a hard product requirement).
 */
object RecallEngine {
    const val DEFAULT_LIMIT = 3
    const val MIN_SCORE = 0.20f

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return if (na == 0f || nb == 0f) 0f else dot / (sqrt(na) * sqrt(nb))
    }

    fun rank(
        queryEmbedding: FloatArray,
        queryText: String,
        candidates: List<MemoryEntity>,
        limit: Int = DEFAULT_LIMIT,
        minScore: Float = MIN_SCORE
    ): List<RecallResult> {
        val snippet = queryText.take(28).trim()
        return candidates
            .mapNotNull { memory ->
                val raw = memory.embedding ?: return@mapNotNull null
                val similarity = cosine(queryEmbedding, VectorCodec.decode(raw))
                // Importance gently reweights raw similarity (HIGH ~ +25%)
                val score = similarity * (1f + memory.importance.value * 0.05f)
                if (score < minScore) return@mapNotNull null
                RecallResult(
                    memory = memory,
                    score = score,
                    reason = "matched \"$snippet\" · similarity %.2f · %s importance".format(
                        similarity, memory.importance.name.lowercase()
                    )
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }
}

/**
 * Detects explicit "remember" voice/text commands.
 *
 *   "remember that I like tea"  -> content "I like tea"
 *   "remember this"             -> command with no content (use last message)
 *   "what's the time"           -> not a command (null)
 */
object MemoryCommandParser {
    data class MemoryCommand(val content: String?)

    private val TRIGGERS = listOf(
        "remember that ", "remember this ", "remember ",
        "don't forget that ", "don't forget ", "note that "
    )

    fun parse(text: String): MemoryCommand? {
        val normalized = WakePhraseMatcher.normalize(text)
        for (trigger in TRIGGERS) {
            if (normalized.startsWith(trigger)) {
                val rest = normalized.removePrefix(trigger).trim()
                if (rest == "this" || rest == "that") return MemoryCommand(null)
                return MemoryCommand(rest.ifBlank { null })
            }
        }
        // Exact bare triggers with no content
        if (normalized == "remember this" || normalized == "remember that" || normalized == "remember") {
            return MemoryCommand(null)
        }
        return null
    }
}

/**
 * Assembles the context-augmented prompt sent to the model provider:
 * persona line, recalled memories (with dates), recent conversation, and the
 * current user turn. Pure and deterministic.
 */
object PromptBuilder {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun build(
        companionName: String,
        userInput: String,
        recentMessages: List<MessageEntity>,
        recalled: List<RecallResult>,
        capabilities: List<String> = emptyList()
    ): String = buildString {
        append("You are ").append(companionName)
        append(", a helpful AI companion with a cat avatar. Answer naturally and concisely.\n")

        // Without this, the chat model has no idea the tool layer exists and
        // truthfully tells the user it is "text only" — even though the
        // registry is full of working tools. Describe the hands it has.
        if (capabilities.isNotEmpty()) {
            append("\nYou are not a text-only chatbot. You can take real actions on this phone ")
            append("by asking for them; the app runs them and shows the user an approval card ")
            append("for anything risky. Things you can actually do:\n")
            capabilities.forEach { append("- ").append(it).append('\n') }
            // "Say you're doing it" was a lie in the only case this prompt is used.
            // When a request IS routed to a tool, the app answers and this model never
            // sees the turn; so if the model is reading this, nothing is running and
            // nothing is going to. Claiming otherwise is what produced "I'm looking at
            // your device right now to tell you what model is running" followed by
            // silence, forever.
            append("You are only WRITING A REPLY right now — no action is running while ")
            append("you write, and none will start because of what you say here. So never ")
            append("claim to be doing, checking, looking at, or fetching anything, and ")
            append("never promise a result is coming. Offer to do it and ask them to say ")
            append("the word. Never claim you are unable to act, and never claim you can ")
            append("do something that is not on this list.\n")
        }

        if (recalled.isNotEmpty()) {
            append("\nRelevant things you remember:\n")
            recalled.forEach { result ->
                append("- (").append(dateFormat.format(Date(result.memory.timestamp))).append(") ")
                    .append(result.memory.content).append('\n')
            }
        }

        if (recentMessages.isNotEmpty()) {
            append("\nConversation so far:\n")
            recentMessages.forEach { message ->
                val speaker = if (message.role == MessageRole.USER) "User" else companionName
                append(speaker).append(": ").append(message.content).append('\n')
            }
        }

        append("\nUser: ").append(userInput).append('\n')
        append(companionName).append(':')
    }
}

/** Builds the user-facing JSON export of the memory store. */
object MemoryExporter {
    fun toJson(memories: List<MemoryEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val sb = StringBuilder()
        sb.append("{\n  \"app\": \"Meowmba\",\n  \"version\": 1,\n  \"memories\": [\n")
        memories.forEachIndexed { index, memory ->
            sb.append("    {\n")
            sb.append("      \"id\": ").append(memory.id).append(",\n")
            sb.append("      \"type\": \"").append(memory.type.name).append("\",\n")
            sb.append("      \"content\": \"").append(escape(memory.content)).append("\",\n")
            sb.append("      \"timestamp\": \"").append(dateFormat.format(Date(memory.timestamp))).append("\",\n")
            sb.append("      \"importance\": \"").append(memory.importance.name).append("\",\n")
            sb.append("      \"tags\": [")
                .append(memory.tags.joinToString(", ") { "\"${escape(it)}\"" })
                .append("]\n")
            sb.append("    }").append(if (index < memories.lastIndex) "," else "").append('\n')
        }
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
