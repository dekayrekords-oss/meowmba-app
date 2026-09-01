package com.example.meowmba.core.memory

import com.example.meowmba.core.audio.EnrollPhraseParser
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
    private val clockFormat = SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a zzz", Locale.US)

    fun build(
        companionName: String,
        userInput: String,
        recentMessages: List<MessageEntity>,
        recalled: List<RecallResult>,
        capabilities: List<String> = emptyList(),
        /**
         * What the self-improving harness has learned about this device, from
         * [com.example.meowmba.core.agent.prime.HarnessState.promptBlock].
         *
         * Placed AFTER the capability list and BEFORE recalled memories, which
         * is deliberate: it must be able to qualify a capability ("ssh.run has
         * never worked here") without being positioned to contradict the rules
         * above it. The block carries its own "guidance only" disclaimer, and
         * the state it comes from rejects anything that reads as a permission
         * change — the text originates in tool output, which is untrusted.
         */
        learned: String? = null,
        /** Who is speaking this turn, when two people share the mic. */
        speakerName: String? = null,
        /**
         * Live device facts (battery, robot body). The model has invented
         * "5% battery" and "I'm on a LOOI" with no tool call; this line is
         * the only allowed source for those claims.
         */
        deviceFacts: String? = null,
        /** Voice-call lane: keep answers short enough to speak immediately. */
        voiceCall: Boolean = false,
    ): String = buildString {
        // No cat roleplay, no forced "meow": plain ChatGPT-style assistant.
        append("You are a helpful AI assistant. Answer naturally and concisely.\n")
        if (voiceCall) {
            append("You are on a live voice call. Reply in one or two short spoken ")
            append("sentences. No markdown, no lists, no preamble.\n")
        }
        deviceFacts?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
        // The chat model has no clock — asked the time it answered from training
        // data, confidently and wrong. Anchor it to the real instant so any
        // time/date-adjacent question (times in a few hours, days of the week)
        // is grounded rather than guessed. The deterministic ClockAnswers path
        // already answers the bare time/date questions without a model call.
        append("The current time and date right now, in the user's timezone, is ")
        append(clockFormat.format(Date(System.currentTimeMillis()))).append(".\n")
        append("If the user asks the time or date, answer from that line — never guess.\n")
        speakerName?.trim()?.takeIf { it.isNotEmpty() }?.let { who ->
            append("You already know this person. Their name is ")
                .append(EnrollPhraseParser.displayName(who)).append(".\n")
        }
        // Measured complaint: asked "how do I install X", the model replied with
        // another question or promised to look it up. Answer; do not deflect.
        // Unconditional so it also covers the tool-free direct-answer path.
        append("If the user asked a QUESTION, answer it directly from your own ")
        append("knowledge in a few short spoken-style sentences. Never reply with ")
        append("another question, never ask for clarification or permission, and ")
        append("never say you will look it up, search, or check — just answer.\n")
        append("The user's words come from speech-to-text and may be misspelled. ")
        append("If it sounds like a greeting or \"what's up\" (including \"ulat's up\" ")
        append("or \"ovites up\"), greet them back. Never say you don't understand ")
        append("and never ask what they mean.\n")

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
            append("never promise a result is coming. Never claim you are unable to act, ")
            append("and never claim you can do something that is not on this list.\n")
            // Measured on device: the model mimicked the tool results in its own
            // history and fabricated `Playing "head tilt" (30ms)` — no task ran.
            append("History lines like \"Playing …\" or \"Video … created\" are TOOL ")
            append("RESULTS written by the app after a real task ran — they are not your ")
            append("words. Never write anything that looks like a tool result; if no task ")
            append("ran, nothing played, and saying it did is a lie.\n")
        }

        // Experience with this specific device, learned from the audit log.
        // Blank-guarded rather than null-guarded so an empty block can never
        // produce a heading with nothing under it.
        learned?.takeIf { it.isNotBlank() }?.let {
            append('\n').append(it).append('\n')
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
                val speaker = when {
                    message.role != MessageRole.USER -> companionName
                    !message.speakerName.isNullOrBlank() ->
                        EnrollPhraseParser.displayName(message.speakerName)
                    else -> "User"
                }
                append(speaker).append(": ").append(message.content).append('\n')
            }
        }

        val who = speakerName?.takeIf { it.isNotBlank() }?.let { EnrollPhraseParser.displayName(it) }
            ?: "User"
        append('\n').append(who).append(": ").append(userInput).append('\n')
        append(companionName).append(':')
    }

    /**
     * Ground-truth block for battery and the robot body. The chat model has
     * invented "I'm at 5%" and "I'm on a LOOI" with nothing connected.
     */
    fun deviceFacts(
        batteryLine: String,
        robotLive: Boolean,
        robotStatus: String,
    ): String = buildString {
        append("FACTS about this phone right now — use these, never invent others:\n")
        append("- ").append(batteryLine).append('\n')
        if (robotLive) {
            append("- A LOOI robot base IS connected (").append(robotStatus).append(").\n")
        } else {
            append("- No robot body is connected. Status: ").append(robotStatus).append(". ")
            append("You are not on any robot. Never say you are sitting on, ")
            append("plugged into, or connected to a LOOI or any robot base.\n")
        }
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
