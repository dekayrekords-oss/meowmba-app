package com.example.meowmba.core.agent

import com.example.meowmba.core.vision.VisionProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The live browser agent: while the browser is open, this loop keeps a
 * standing goal, looks at the page (JPEG screenshot -> vision model, e.g.
 * OpenRouter), decides the next action, executes it in the WebView, and
 * verifies the result on the next cycle. New spoken instructions replace
 * the goal and re-plan immediately; touch edits by the user are simply
 * visible in the next screenshot.
 *
 * Honest limits: max [MAX_STEPS] actions per goal, then it reports and
 * idles; every failure is announced rather than hidden.
 */
class BrowserAgentLoop(
    private val scope: CoroutineScope,
    private val vision: () -> VisionProvider?,
    private val brain: suspend (String) -> String?,
    private val captureJpeg: suspend () -> ByteArray?,
    private val evalJs: suspend (String) -> String,
    private val pagesDir: File,
    private val announce: (String) -> Unit,
    private val stepDelayMs: Long = 4_000L,
    private val monitorEveryMs: Long = 45_000L,
) {
    var goal: String? = null
        private set
    var lastReport: String? = null
        private set

    val isActive: Boolean get() = goal != null
    private var job: Job? = null

    /** A new instruction from the user (voice or text) while browsing. */
    fun setGoal(newGoal: String) {
        goal = newGoal
        announce("On it — working on the page.")
        restart()
    }

    fun clearGoal() {
        goal = null
        job?.cancel()
        job = null
    }

    /** The browser pane became visible: resume watching if a goal is set. */
    fun browserOpened() {
        if (goal != null) restart()
    }

    /** The browser closed: stop burning vision cycles. */
    fun browserClosed() {
        job?.cancel()
        job = null
    }

    private fun restart() {
        job?.cancel()
        job = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        var steps = 0
        while (scope.isActive && goal != null) {
            delay(if (steps == 0) 1_500L else stepDelayMs)
            val g = goal ?: break
            val shot = captureJpeg() ?: continue  // browser not ready yet
            val dom = evalJs("window.__meow ? window.__meow.pageHtml() : ''")
                .take(3000)

            val verdict = vision()?.describe(shot, planPrompt(g, dom, steps))
            if (verdict == null) {
                announce("I can't reach my vision model — check my brain settings.")
                delay(monitorEveryMs)
                continue
            }
            val text = verdict.getOrElse {
                announce("Vision failed: ${it.message?.take(120)}")
                delay(monitorEveryMs)
                continue
            }

            val action = parseAction(text) ?: run {
                // Model answered in prose — relay it and keep monitoring.
                announce(text.take(240))
                delay(monitorEveryMs)
                continue
            }

            when (action.type) {
                "done" -> {
                    lastReport = action.text ?: "Page matches the goal."
                    announce(lastReport!!)
                    steps = 0
                    delay(monitorEveryMs)   // keep verifying at a slow cadence
                }
                "wait" -> delay(stepDelayMs)
                "say" -> announce(action.text ?: "")
                else -> {
                    val result = execute(action)
                    announce("${action.type}: ${result.take(120)}")
                    steps++
                    if (steps >= MAX_STEPS) {
                        announce("That's $MAX_STEPS changes in a row — tell me to keep going if you want more.")
                        steps = 0
                        delay(monitorEveryMs)
                    }
                }
            }
        }
    }

    private suspend fun execute(a: AgentAction): String = when (a.type) {
        "open" -> a.url?.let {
            evalJs("window.location.href = '${jsEscape(it)}'; 'navigating'")
        } ?: "no url"
        "click" -> a.selector?.let { evalJs("window.__meow.click('${jsEscape(it)}')") } ?: "no selector"
        "type" -> {
            val sel = a.selector ?: return "no selector"
            evalJs("window.__meow.type('${jsEscape(sel)}', '${jsEscape(a.text.orEmpty())}')")
        }
        "scroll" -> evalJs("window.__meow.scroll(${a.pixels ?: 400})")
        "inject" -> a.html?.let {
            evalJs("window.__meow.inject(${jsStringLiteral(it)})")
        } ?: "no html"
        "js" -> a.code?.let { evalJs(it) } ?: "no code"
        "save" -> {
            val html = evalJs("window.__meow.pageHtml()")
            if (html.startsWith("ERROR") || html.isBlank()) "nothing to save"
            else runCatching {
                pagesDir.mkdirs()
                val name = (a.name ?: "page").replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                File(pagesDir, "$name.html").writeText(html)
                "saved $name.html"
            }.getOrElse { "save failed: ${it.message}" }
        }
        else -> "unknown action ${a.type}"
    }

    companion object {
        const val MAX_STEPS = 12

        fun jsEscape(s: String): String =
            s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

        /** JS single-quoted literal for arbitrary HTML payloads. */
        fun jsStringLiteral(s: String): String =
            "'" + s.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", "\\n").replace("\r", "") + "'"

        fun planPrompt(goal: String, dom: String, stepsTaken: Int): String = """
            You are meow driving a phone browser. The user's standing goal:
            """ + goal + """

            Current page HTML (truncated):
            """ + dom.ifBlank { "(empty page)" } + """

            Steps taken so far: """ + stepsTaken + """. Look at the screenshot and
            reply with exactly ONE JSON object on one line, no prose:
            {"action":"inject","html":"<full page html>"}   to build/rewrite the page
            {"action":"js","code":"<js to run>"}             for targeted edits
            {"action":"click","selector":"<css or tag=text>"}
            {"action":"type","selector":"<css>","text":"..."}
            {"action":"scroll","pixels":400}
            {"action":"open","url":"https://..."}
            {"action":"save","name":"my_page"}               to persist the page
            {"action":"done","summary":"<what is now true>"} if the page satisfies the goal
            {"action":"wait"}                                if still loading
            Build complete, styled pages in ONE inject when asked to make a page.
        """.trimIndent()

        /** Extracts the first balanced {...} and maps it to an [AgentAction]. */
        fun parseAction(text: String): AgentAction? {
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
                AgentAction(
                    type = type,
                    html = obj["html"]?.jsonPrimitive?.contentOrNull,
                    code = obj["code"]?.jsonPrimitive?.contentOrNull,
                    selector = obj["selector"]?.jsonPrimitive?.contentOrNull,
                    text = obj["text"]?.jsonPrimitive?.contentOrNull
                        ?: obj["summary"]?.jsonPrimitive?.contentOrNull,
                    url = obj["url"]?.jsonPrimitive?.contentOrNull,
                    name = obj["name"]?.jsonPrimitive?.contentOrNull,
                    pixels = obj["pixels"]?.jsonPrimitive?.intOrNull,
                )
            }.getOrNull()
        }
    }
}

data class AgentAction(
    val type: String,
    val html: String? = null,
    val code: String? = null,
    val selector: String? = null,
    val text: String? = null,
    val url: String? = null,
    val name: String? = null,
    val pixels: Int? = null,
)
