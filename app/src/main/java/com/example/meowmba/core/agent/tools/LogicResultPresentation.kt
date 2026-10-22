package com.example.meowmba.core.agent.tools

import kotlinx.serialization.json.*

/** Native identities stay in task data; the companion speaks a bounded summary. */
object LogicResultPresentation {
    fun summary(toolId: String, raw: String): String {
        if (!toolId.startsWith("logic.") || toolId == "logic.run_goal") return raw
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            fun text(key: String) = obj[key]?.jsonPrimitive?.contentOrNull.orEmpty()
            when (toolId) {
                "logic.inspect" -> {
                    val tracks = obj["tracks"]?.jsonArray.orEmpty().map { it.jsonObject["name"]?.jsonPrimitive?.content.orEmpty() }
                    val pluginNames = obj["channel_strips"]?.jsonArray.orEmpty().flatMap { strip ->
                        strip.jsonObject["plugins"]?.jsonArray.orEmpty().map { it.jsonObject["name"]?.jsonPrimitive?.content.orEmpty() }
                    }.distinct()
                    buildString {
                        append("Logic is connected. I can see ${tracks.size} tracks")
                        if (tracks.isNotEmpty()) append(": ${tracks.take(6).joinToString()}")
                        append(".")
                        if (pluginNames.isNotEmpty()) append(" Plugins: ${pluginNames.take(8).joinToString()}.")
                        if (obj["frontmost"]?.jsonPrimitive?.booleanOrNull != true) append(" Bring Logic to the front on the Mac before editing.")
                    }
                }
                "logic.screen" -> "I read ${text("total")} matching Logic controls. The task details include their available actions."
                "logic.verify_audio" -> if (obj["verified_audio"]?.jsonPrimitive?.booleanOrNull == true)
                    "The WAV decodes correctly: ${text("channels")} channels at ${text("sample_rate")} Hz, ${text("duration_seconds")} seconds. " +
                        if (obj["signal_present"]?.jsonPrimitive?.booleanOrNull == true) "Audio signal is present." else "The file is silent."
                    else "The audio export was not verified."
                "logic.cancel" -> "Stop requested. Changes already applied remain; I need to check Logic before continuing."
                else -> when (text("state")) {
                    "verified" -> "Logic confirmed ${text("target")}: ${text("parameter").ifBlank { "field" }} = ${text("observed_display").ifBlank { text("observed") }}."
                    "observed" -> "Logic's screen changed. I need to inspect the result before continuing."
                    else -> "Logic result: ${text("state").ifBlank { "unknown" }}. Inspect the task receipt before another edit."
                }
            }.take(1000)
        }.getOrDefault("Logic returned task details. Review them before continuing.")
    }
}
