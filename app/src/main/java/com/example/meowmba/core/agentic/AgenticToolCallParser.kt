package com.example.meowmba.core.agentic

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parse OpenAI-style `tool_calls` / function-call JSON (and compact planner
 * JSON) into [ProposedToolCall] list for the agentic runtime.
 *
 * Accepts:
 * - Full chat-completions message JSON with `tool_calls: [{function:{name,arguments}}]`
 * - Compact `{"tool":"ssh_exec","argv":["uname","-a"],"profileId":"..."}`
 * - Hermes-ish `{"name":"local_shell","arguments":{"command":"uname -a"}}`
 * - Prose wrapping a JSON object / array (first balanced extract)
 *
 * Never invents tools; unknown names are still returned so the runtime can
 * emit `unknown_tool` and block — validation of registry membership is the
 * caller's job.
 */
object AgenticToolCallParser {

    fun parseAll(modelText: String, fallbackProfileId: String? = null): List<ProposedToolCall> {
        val text = modelText.trim()
        if (text.isEmpty() || text.startsWith("MODEL_ERROR:")) return emptyList()

        // Prefer explicit tool_calls array from a message / root object.
        extractJson(text)?.let { root ->
            val fromToolCalls = parseToolCallsArray(root, fallbackProfileId)
            if (fromToolCalls.isNotEmpty()) return fromToolCalls

            val single = parseSingleObject(root, fallbackProfileId)
            if (single != null) return listOf(single)

            // Root may be a bare array of tool objects.
            if (root.has("_array")) {
                val arr = root.getJSONArray("_array")
                val out = mutableListOf<ProposedToolCall>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    parseSingleObject(o, fallbackProfileId)?.let { out.add(it) }
                }
                if (out.isNotEmpty()) return out
            }
        }

        // Lightweight keyword fallback from stub/heuristic prose.
        val lower = text.lowercase()
        when {
            "tool=noop" in lower || "tool: noop" in lower || "propose noop" in lower ->
                return listOf(ProposedToolCall(tool = "noop", reason = "parsed from prose"))
            "tool=ssh_exec" in lower || "tool: ssh_exec" in lower || "propose tool=ssh_exec" in lower ->
                return listOf(
                    ProposedToolCall(
                        tool = "ssh_exec",
                        profileId = fallbackProfileId,
                        argv = listOf("uname", "-a"),
                        reason = "parsed ssh_exec from prose",
                    ),
                )
            "tool=local_shell" in lower || "local_shell" in lower && "tool=" in lower ->
                return listOf(
                    ProposedToolCall(
                        tool = "local_shell",
                        argv = listOf("uname", "-a"),
                        reason = "parsed local_shell from prose",
                    ),
                )
        }
        return emptyList()
    }

    fun parseFirst(modelText: String, fallbackProfileId: String? = null): ProposedToolCall? =
        parseAll(modelText, fallbackProfileId).firstOrNull()

    private fun parseToolCallsArray(root: JSONObject, fallbackProfileId: String?): List<ProposedToolCall> {
        val message = root.optJSONObject("message")
        val choices = root.optJSONArray("choices")
        val fromChoice = if (choices != null && choices.length() > 0) {
            choices.optJSONObject(0)?.optJSONObject("message")
        } else null
        val carrier = message ?: fromChoice ?: root
        val arr = carrier.optJSONArray("tool_calls") ?: return emptyList()
        val out = mutableListOf<ProposedToolCall>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val fn = item.optJSONObject("function") ?: item
            val name = fn.optString("name").ifBlank { item.optString("name") }.trim()
            if (name.isEmpty()) continue
            val argsRaw = fn.opt("arguments") ?: item.opt("arguments") ?: item.opt("args")
            out.add(fromNameAndArgs(name, argsRaw, fallbackProfileId, reason = "tool_calls[$i]"))
        }
        return out
    }

    private fun parseSingleObject(o: JSONObject, fallbackProfileId: String?): ProposedToolCall? {
        val name = o.optString("tool").ifBlank {
            o.optString("name")
        }.trim()
        if (name.isEmpty()) return null
        if (name.equals("tool_calls", ignoreCase = true)) return null
        val argsRaw = o.opt("arguments") ?: o.opt("args") ?: o
        return fromNameAndArgs(name, argsRaw, fallbackProfileId, reason = o.optString("reason", "json"))
    }

    private fun fromNameAndArgs(
        name: String,
        argsRaw: Any?,
        fallbackProfileId: String?,
        reason: String,
    ): ProposedToolCall {
        val argsObj: JSONObject? = when (argsRaw) {
            is JSONObject -> argsRaw
            is String -> try {
                JSONObject(argsRaw)
            } catch (_: Exception) {
                null
            }
            else -> null
        }
        val profileId = argsObj?.optString("profileId")?.trim()?.takeIf { it.isNotEmpty() }
            ?: argsObj?.optString("profile_id")?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackProfileId
        val argv = parseArgv(argsObj, argsRaw)
        return ProposedToolCall(
            tool = name,
            profileId = profileId,
            argv = argv,
            reason = reason.take(160),
        )
    }

    private fun parseArgv(argsObj: JSONObject?, argsRaw: Any?): List<String> {
        if (argsObj != null) {
            val arr = argsObj.optJSONArray("argv")
            if (arr != null) {
                return buildList {
                    for (i in 0 until arr.length()) add(arr.optString(i))
                }.filter { it.isNotBlank() }
            }
            val command = argsObj.optString("command").ifBlank {
                argsObj.optString("cmd")
            }.trim()
            if (command.isNotEmpty()) {
                return listOf("sh", "-c", command)
            }
            // SSH-style single "command" already split? ignore.
        }
        if (argsRaw is JSONArray) {
            return buildList {
                for (i in 0 until argsRaw.length()) add(argsRaw.optString(i))
            }.filter { it.isNotBlank() }
        }
        return emptyList()
    }

    /**
     * Extract first JSON object or array from arbitrary model text.
     * Arrays are wrapped as `{"_array":[...]}` for a uniform return type.
     */
    internal fun extractJson(text: String): JSONObject? {
        val startObj = text.indexOf('{')
        val startArr = text.indexOf('[')
        val start = when {
            startObj < 0 -> startArr
            startArr < 0 -> startObj
            else -> minOf(startObj, startArr)
        }
        if (start < 0) return null
        val end = findMatching(text, start) ?: return null
        val candidate = text.substring(start, end + 1)
        return try {
            if (candidate.startsWith('[')) {
                JSONObject().put("_array", JSONArray(candidate))
            } else {
                JSONObject(candidate)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findMatching(text: String, start: Int): Int? {
        val open = text[start]
        val close = if (open == '{') '}' else if (open == '[') ']' else return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }
}
