package com.example.meowmba.core.agent.tools

import kotlinx.serialization.json.*
import kotlin.math.abs

/** Exact mixer requests use observed controls without asking an LLM to invent a gesture. */
internal data class LogicVolumeIntent(val target: String, val decibels: Double) {
    fun decision(frame: JsonObject): JsonObject {
        val tracks = frame["tracks"]?.jsonArray.orEmpty().map { it.jsonObject }
        require(tracks.count { it["name"]?.jsonPrimitive?.content?.equals(target, true) == true } == 1) {
            "Logic needs one unambiguous track named $target. No volume edit was sent."
        }
        val rows = frame["controls"]?.jsonArray.orEmpty().map { it.jsonObject }
        val matches = rows.withIndex().filter { (_, row) ->
            row["kind"]?.jsonPrimitive?.content == "set_volume_db" &&
                row["target"]?.jsonPrimitive?.content?.equals(target, true) == true
        }
        require(matches.isNotEmpty()) { "No editable volume control is visible for $target." }
        // Logic can expose the same track in both mixer and inspector. Accept
        // duplicates only when their observed value and limits agree.
        require(matches.map { (_, r) -> listOf(r["value"], r["min"], r["max"]) }.distinct().size == 1) {
            "Logic exposes conflicting volume controls for $target; inspect the mixer first."
        }
        val (index, row) = matches.first()
        val low = row["min"]?.jsonPrimitive?.doubleOrNull
        val high = row["max"]?.jsonPrimitive?.doubleOrNull
        val current = row["value"]?.jsonPrimitive?.doubleOrNull
        require(low != null && high != null && current != null && current.isFinite() && decibels in low..high) {
            "The requested volume is outside the observed range for $target or its current value is unavailable."
        }
        return buildJsonObject {
            if (abs(current - decibels) < 0.0001) {
                put("action", "done")
                put("summary", "$target volume is ${decibels} dB, verified in Logic Pro.")
                putJsonArray("evidence") { add(buildJsonObject {
                    put("index", index + 1); put("value", row.getValue("value").jsonPrimitive.content)
                }) }
            } else {
                put("action", "edit"); put("index", index + 1); put("value", decibels.toString())
            }
        }
    }

    companion object {
        private val pattern = Regex(
            "^(?:please\\s+)?(?:in\\s+logic(?:\\s+pro)?\\s*,?\\s*)?(?:set|change|adjust)\\s+(.{1,120}?)\\s+(?:volume|level)\\s+to\\s+([+-]?\\d{1,3}(?:\\.\\d{1,3})?)\\s*(?:db|decibels)(?:\\s+in\\s+logic(?:\\s+pro)?)?[.!]?$",
            RegexOption.IGNORE_CASE)

        fun parse(goal: String): LogicVolumeIntent? {
            val match = pattern.matchEntire(goal.trim()) ?: return null
            val target = match.groupValues[1].trim().trim('"', '\'', '“', '”')
            if (target.isBlank() || Regex("\\b(and|then|except)\\b", RegexOption.IGNORE_CASE).containsMatchIn(target)) return null
            return LogicVolumeIntent(target, match.groupValues[2].toDouble())
        }
    }
}
