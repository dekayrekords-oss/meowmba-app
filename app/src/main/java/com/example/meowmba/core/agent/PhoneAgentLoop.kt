package com.example.meowmba.core.agent

import com.example.meowmba.core.access.MeowAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Autonomous phone-control loop: reads the accessibility tree, asks the cloud
 * model for the next action, executes it, and repeats until the goal is done
 * or [maxSteps] is reached. Modeled on [BrowserAgentLoop].
 *
 * One approval per task (at start), not per action. The user can say "stop"
 * at any time to cancel the loop.
 */
class PhoneAgentLoop(
    private val scope: CoroutineScope,
    private val brain: suspend (prompt: String) -> String?,
    private val service: () -> MeowAccessibilityService?,
    private val announce: (String) -> Unit,
    private val onComplete: (String) -> Unit,
    private val stepDelayMs: Long = 1_500L,
    private val maxSteps: Int = 15,
) {
    var goal: String? = null
        private set
    var lastSummary: String? = null
        private set

    val isActive: Boolean get() = goal != null
    private var job: Job? = null
    private val history = mutableListOf<String>()

    /** Start a new phone-control goal. Cancels any running goal. */
    fun setGoal(newGoal: String) {
        goal = newGoal
        activeGoal = newGoal
        history.clear()
        lastSummary = null
        announce("I'll control the phone to: " + newGoal + " — say stop anytime.")
        restart()
    }

    /** Cancel the running loop. */
    fun cancel() {
        goal = null
        activeGoal = null
        job?.cancel()
        job = null
        history.clear()
    }

    private fun restart() {
        job?.cancel()
        job = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        var steps = 0
        while (scope.isActive && goal != null) {
            delay(if (steps == 0) 800L else stepDelayMs)
            val g = goal ?: break
            val svc = service() ?: run {
                announce("Accessibility service isn't connected. Enable it in Settings.")
                goal = null
                break
            }

            val screenContent = svc.readScreenCompact()
            val activePackage = svc.getActivePackage()
            val prompt = buildPrompt(g, activePackage, screenContent, history)

            val response = try { brain(prompt) } catch (_: Exception) { null }
            if (response == null) {
                announce("Brain unreachable — retrying.")
                delay(3_000L)
                continue
            }

            val action = parseAction(response)
            if (action == null) {
                // Model replied in prose — relay and stop
                val msg = response.take(200)
                lastSummary = msg
                onComplete(msg)
                goal = null
                break
            }

            if (action.type == "done") {
                val summary = action.text ?: "Done."
                lastSummary = summary
                onComplete(summary)
                goal = null
                activeGoal = null
                break
            }

            if (action.type == "wait") {
                delay(stepDelayMs)
                continue
            }

            val result = executeAction(svc, action)
            history.add(action.type + ": " + result)
            if (history.size > 5) history.removeAt(0)
            steps++

            if (steps >= maxSteps) {
                val msg = "Reached " + maxSteps + " steps. Tell me to continue or give a new goal."
                announce(msg)
                lastSummary = msg
                goal = null
                activeGoal = null
                break
            }
        }
    }

    private fun executeAction(svc: MeowAccessibilityService, action: PhoneAction): String {
        return try {
            when (action.type) {
                "tap" -> action.text?.let { svc.tapByText(it) } ?: "no text"
                "tap_index" -> action.index?.let { svc.tapByIndex(it) } ?: "no index"
                "type" -> {
                    val field = action.field ?: action.text ?: ""
                    svc.typeIntoText(field, action.value ?: "")
                }
                "scroll" -> svc.scroll(action.direction?.lowercase() != "up")
                "swipe" -> svc.swipe(action.direction ?: "up")
                "long_press" -> action.text?.let { svc.longPress(it) } ?: "no text"
                "back" -> svc.globalAction("back")
                "home" -> svc.globalAction("home")
                "recents" -> svc.globalAction("recents")
                "notifications" -> svc.globalAction("notifications")
                else -> "unknown action: " + action.type
            }
        } catch (e: Exception) {
            "error: " + (e.message ?: "unknown")
        }
    }

    companion object {
        /** Static check for WakeWordService: skip "One moment" TTS when the loop is running. */
        @Volatile var activeGoal: String? = null
            private set

        fun buildPrompt(goal: String, pkg: String, screen: String, history: List<String>): String {
            val sb = StringBuilder()
            sb.append("You control a phone via accessibility. Current app: ")
            sb.append(pkg)
            sb.append("\nGoal: ")
            sb.append(goal)
            sb.append("\n\nScreen content (numbered interactive elements):\n")
            sb.append(screen)
            if (history.isNotEmpty()) {
                sb.append("\n\nAction history (most recent last):\n")
                sb.append(history.joinToString("\n"))
            }
            sb.append("\n\nReply with ONE JSON object. Available actions:\n")
            sb.append("""{"action":"tap","text":"element text"}""")
            sb.append("\n")
            sb.append("""{"action":"tap_index","index":3}""")
            sb.append("\n")
            sb.append("""{"action":"type","field":"Search","value":"query text"}""")
            sb.append("\n")
            sb.append("""{"action":"scroll","direction":"down"}""")
            sb.append("\n")
            sb.append("""{"action":"swipe","direction":"left"}""")
            sb.append("\n")
            sb.append("""{"action":"long_press","text":"element text"}""")
            sb.append("\n")
            sb.append("""{"action":"back"}""")
            sb.append("\n")
            sb.append("""{"action":"home"}""")
            sb.append("\n")
            sb.append("""{"action":"wait"}""")
            sb.append("\n")
            sb.append("""{"action":"done","summary":"what was accomplished"}""")
            sb.append("\nIf the goal is achieved, use done. Pick ONE action only.")
            return sb.toString()
        }

        /** Parse the first JSON object from model output. */
        fun parseAction(text: String): PhoneAction? {
            val start = text.indexOf('{')
            if (start < 0) return null
            var depth = 0
            var end = -1
            var inStr = false
            var esc = false
            for (i in start until text.length) {
                val c = text[i]
                if (esc) { esc = false; continue }
                if (c == '\\') { esc = true; continue }
                if (c == '"') { inStr = !inStr; continue }
                if (inStr) continue
                if (c == '{') depth++
                if (c == '}') { depth--; if (depth == 0) { end = i; break } }
            }
            if (end < 0) return null
            return runCatching {
                val obj = Json.parseToJsonElement(text.substring(start, end + 1)) as JsonObject
                val type = obj["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return null
                PhoneAction(
                    type = type,
                    text = obj["text"]?.jsonPrimitive?.contentOrNull
                        ?: obj["summary"]?.jsonPrimitive?.contentOrNull,
                    index = obj["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    field = obj["field"]?.jsonPrimitive?.contentOrNull,
                    value = obj["value"]?.jsonPrimitive?.contentOrNull,
                    direction = obj["direction"]?.jsonPrimitive?.contentOrNull,
                )
            }.getOrNull()
        }
    }
}

data class PhoneAction(
    val type: String,
    val text: String? = null,
    val index: Int? = null,
    val field: String? = null,
    val value: String? = null,
    val direction: String? = null,
)
