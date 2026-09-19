package com.example.meowmba.core.personality

import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Persistent claim history, not randomized wrappers around a fixed catalog.
 * Semantic equivalence and factual accuracy still depend on the selected model. */
class NovelFacts(private val history: File) {
    private val lock = Mutex()
    suspend fun next(generate: suspend (String) -> String): String? = lock.withLock {
        val past = withContext(Dispatchers.IO) {
            if (!history.exists()) emptyList() else history.useLines { lines ->
                lines.map { Json.parseToJsonElement(it).jsonObject }.toList()
            }
        }
        val topics = listOf("astronomy", "animals", "plants", "geology", "language", "mathematics", "inventions", "ocean life", "music", "architecture")
        repeat(3) { attempt ->
            val topic = topics[(past.size + attempt) % topics.size]
            val exclusions = past.takeLast(35).joinToString("\n") { it["claim"]!!.jsonPrimitive.content }
            val raw = generate("Give one accurate, interesting, established fact about $topic, under 60 words. " +
                "Never invent evidence or offer medical/financial advice. Return ONLY JSON with claim (canonical subject and predicate, no opener) " +
                "and fact (natural spoken explanation). Choose a genuinely different claim, not a paraphrase of these:\n$exclusions")
            val obj = runCatching { Json.parseToJsonElement(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).jsonObject }.getOrNull()
                ?: return@repeat
            val fact = obj["fact"]?.jsonPrimitive?.content?.trim().orEmpty()
            val claim = obj["claim"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (fact.length !in 20..600 || claim.length !in 8..240) return@repeat
            if (past.any { duplicate(claim, it["claim"]!!.jsonPrimitive.content) || duplicate(fact, it["fact"]!!.jsonPrimitive.content) }) return@repeat
            val record = buildJsonObject { put("claim", claim); put("fact", fact) }
            withContext(Dispatchers.IO) {
                history.parentFile?.mkdirs()
                // Reserve before speaking; failed/interrupted speech never causes a replay.
                history.appendText(record.toString() + "\n", Charsets.UTF_8)
            }
            return@withLock fact
        }
        null
    }

    companion object {
        fun duplicate(a: String, b: String): Boolean {
            fun words(value: String) = value.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length > 2 && it !in setOf("the", "and", "that", "with", "are", "has", "have") }.toSet()
            val x = words(a); val y = words(b)
            if (x.isEmpty() || y.isEmpty()) return true
            return x == y || x.intersect(y).size.toDouble() / minOf(x.size, y.size) >= .8
        }
    }
}
