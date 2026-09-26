package com.example.meowmba.core.agent

import com.example.meowmba.core.vision.VisionProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val onSource: (kind: String, code: String) -> Unit = { _, _ -> },
    private val stepDelayMs: Long = 4_000L,
    private val monitorEveryMs: Long = 45_000L,
) {
    var goal: String? = null
        private set
    var lastReport: String? = null
        private set

    val isActive: Boolean get() = goal != null
    private var job: Job? = null
    private var generation = 0

    /** A new instruction from the user (voice or text) while browsing. */
    fun setGoal(newGoal: String) {
        generation++
        goal = newGoal
        announce("On it — working on the page.")
        restart()
    }

    fun clearGoal() {
        generation++
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
        generation++
        job?.cancel()
        val run = generation
        job = scope.launch {
            try { runLoop() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { if (run == generation) announce("Page work stopped unexpectedly. The last preview is unchanged; ask me to try again.") }
            finally { if (run == generation) goal = null }
        }
    }

    private suspend fun runLoop() {
        var steps = 0
        var looks = 0
        var pendingLook = false
        var attempts = 0
        while (currentCoroutineContext().isActive && goal != null && attempts++ < MAX_STEPS) {
            delay(if (steps == 0) 250L else stepDelayMs)
            val g = goal ?: break

            // Scrape is a DOM read. It does not need a screenshot or a vision model.
            if (isDirectScrape(g) && steps == 0) {
                val href = evalJs("String(location.href)")
                if (href.contains("meow.local") || href.contains("about:blank")) {
                    announce("Open a URL first, then say scrape this page.")
                    return
                }
                val result = execute(AgentAction(type = "scrape", name = "scrape"))
                lastReport = result
                announce(result.take(240))
                steps = 1
                return
            }

            val isBuild = isBuildGoal(g)
            // A build with nothing on screen yet must not wait on the model.
            // Measured: "create a website" opened the pane and then sat on
            // the workshop stub because the brain returned prose, not JSON.
            if (isBuild && steps == 0) {
                val draft = scaffoldHtml(g)
                execute(AgentAction(type = "inject", html = draft))
                publishSource(AgentAction(type = "inject", html = draft))
                announce("First draft is up — I'll restyle it next.")
                pendingLook = true
                steps = 1
                continue
            }
            val text = if (pendingLook && looks < MAX_LOOKS) {
                looks++
                pendingLook = false
                lookAtPage(g, looks)
            } else {
                val dom = currentDom()
                val fromBrain = brain(planPrompt(g, dom, steps))
                pickPlanner(fromBrain, null)
            }
            currentCoroutineContext().ensureActive()
            if (text == null) {
                announce("I can't reach a model to drive the page. Check Settings for OpenRouter.")
                return
            }
            if (text.startsWith("ERROR:")) {
                announce(text.removePrefix("ERROR:").trim().take(240))
                return
            }

            currentCoroutineContext().ensureActive()
            var action = parseAction(text)
            if (action == null) {
                announce(text.take(240))
                return
            }
            if (shouldRefuseDone(isBuild, looks) && action.type == "done") {
                announce("That's still a draft — restyling it.")
                val retry = brain(planPrompt(g, currentDom(), steps))
                action = parseAction(retry.orEmpty()) ?: action
            }
            currentCoroutineContext().ensureActive()
            publishSource(action)

            when (action.type) {
                "done" -> {
                    lastReport = action.text ?: "Page matches the goal."
                    announce(lastReport!!)
                    return
                }
                "wait" -> delay(stepDelayMs)
                "say" -> announce(action.text ?: "")
                else -> {
                    val result = execute(action)
                    announce("${action.type}: ${result.take(120)}")
                    if (action.type == "inject") pendingLook = true
                    steps++
                    if (steps >= MAX_STEPS) {
                        announce("That's $MAX_STEPS changes in a row — tell me to keep going if you want more.")
                        return
                    }
                }
            }
        }
        announce("Page work reached its limit and stopped. Ask me to continue if you want more changes.")
    }

    private fun publishSource(action: AgentAction) {
        when (action.type) {
            "inject" -> action.html?.let { onSource("html", it) }
            "js" -> action.code?.let { onSource("js", it) }
            else -> Unit
        }
    }

    private suspend fun currentDom(): String =
        evalJs("window.__meow ? window.__meow.pageHtml() : ''").take(12_000)

    /** Screenshot the live page and ask the vision model to restyle or approve. */
    private suspend fun lookAtPage(goal: String, look: Int): String? {
        announce("Looking at how the page landed…")
        delay(900)
        val shot = captureJpeg()
        val v = vision()
        val prompt = lookPrompt(goal, currentDom(), look)
        if (shot != null && v != null) {
            val seen = v.describe(shot, prompt)
            val text = seen.getOrNull()
            if (text != null) return text
            announce("Vision hiccup: ${seen.exceptionOrNull()?.message?.take(80) ?: "empty"}")
        }
        return brain(prompt)
    }

    private suspend fun execute(a: AgentAction): String {
        currentCoroutineContext().ensureActive()
        return executeActive(a)
    }

    private suspend fun executeActive(a: AgentAction): String = when (a.type) {
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
            onSource("html", it)
            com.example.meowmba.core.browser.WebViewBridge.loadHtml(it)
            "injected ${it.length} chars"
        } ?: "no html"
        "scrape" -> {
            val raw = evalJs("window.__meow ? window.__meow.scrape() : ''")
            if (raw.isBlank() || raw == "null") "nothing to scrape"
            else runCatching {
                pagesDir.mkdirs()
                val name = (a.name ?: "scrape").replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                val file = File(pagesDir, "$name.md")
                file.writeText(raw)
                onSource("scrape", raw)
                "scraped ${raw.length} chars → ${file.name}: ${raw.take(240)}"
            }.getOrElse { "scrape failed: ${it.message}" }
        }
        "js" -> a.code?.let {
            onSource("js", it)
            evalJs(it)
        } ?: "no code"
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
        const val MAX_LOOKS = 3

        /** Text brain first — vision is optional and often not configured. */
        fun pickPlanner(fromBrain: String?, fromVision: String?): String? {
            if (!fromBrain.isNullOrBlank()) return fromBrain
            if (!fromVision.isNullOrBlank()) return fromVision
            return null
        }

        fun isBuildGoal(goal: String): Boolean {
            return com.example.meowmba.core.work.BrowserRequestParser.parse(goal)?.job ==
                com.example.meowmba.core.work.BrowserJob.BUILD
        }

        /** Topic words from a voice goal, for the instant first draft. */
        fun topicFromGoal(goal: String): String {
            val stripped = goal.lowercase()
                .replace(Regex("\\b(hey|ok|okay|meow|please|can you|could you|would you|" +
                    "make|build|design|write|draft|code|create|generate|me|us|a|an|the|" +
                    "for|about|called|named|website|web site|webpage|web page|web app|" +
                    "page|html|landing|homepage|home page|site)\\b"), " ")
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return stripped.ifBlank { "new site" }
                .split(' ')
                .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
                .take(48)
        }

        /** Self-contained dark landing page so a build is never a blank workshop. */
        fun scaffoldHtml(goal: String): String {
            val topic = topicFromGoal(goal)
            val title = topic.replaceFirstChar { it.uppercaseChar() }
            return """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>$title</title>
<style>
  :root { color-scheme: dark; }
  body{margin:0;background:#0d1117;color:#e6edf3;font-family:system-ui,sans-serif}
  header{padding:48px 24px 32px;background:linear-gradient(160deg,#161b22,#0d1117 70%)}
  h1{font-size:32px;margin:0 0 10px;letter-spacing:-.03em}
  .lead{color:#8b949e;max-width:28em;line-height:1.5;font-size:16px}
  main{padding:24px;display:grid;gap:14px}
  section{background:#161b22;border:1px solid #30363d;border-radius:14px;padding:18px}
  h2{margin:0 0 8px;font-size:16px;color:#58a6ff}
  p{margin:0;color:#c9d1d9;line-height:1.45}
</style>
</head>
<body>
<header>
  <h1>$title</h1>
  <p class="lead">A first draft for “$topic”. I’ll restyle this in the next pass.</p>
</header>
<main>
  <section><h2>About</h2><p>What $topic is, in one short paragraph you can edit.</p></section>
  <section><h2>Highlights</h2><p>Three things that should stand out on a phone screen.</p></section>
  <section><h2>Next</h2><p>Tell me what to change — colors, copy, layout, or a whole rewrite.</p></section>
</main>
</body>
</html>
            """.trimIndent()
        }

        /** HTML the model dumped as a fence or a raw document, not JSON. */
        fun extractHtml(text: String): String? {
            val fence = Regex("```(?:html)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
                .find(text)
            val fenced = fence?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (fenced.contains("<html", ignoreCase = true) ||
                fenced.contains("<!doctype", ignoreCase = true) ||
                fenced.contains("<body", ignoreCase = true)
            ) {
                return fenced
            }
            val lower = text.lowercase()
            val start = listOf("<!doctype", "<html", "<body")
                .map { lower.indexOf(it) }
                .filter { it >= 0 }
                .minOrNull() ?: return null
            val end = lower.lastIndexOf("</html>")
            return if (end > start) text.substring(start, end + 7) else text.substring(start)
        }

        fun shouldRefuseDone(isBuild: Boolean, looksTaken: Int): Boolean =
            isBuild && looksTaken == 0

        fun isDirectScrape(goal: String): Boolean {
            val t = goal.lowercase()
            return t.contains("scrape") || t.contains("extract the text") ||
                t.contains("extract the links") || t.contains("pull the text") ||
                t.contains("pull the links") || t.contains("grab the text")
        }

        fun jsEscape(s: String): String =
            s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

        /** JS single-quoted literal for arbitrary HTML payloads. */
        fun jsStringLiteral(s: String): String =
            "'" + s.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", "\\n").replace("\r", "") + "'"

        fun planPrompt(goal: String, dom: String, stepsTaken: Int): String = """
            You are meow designing a page in a phone browser. Goal:
            """ + goal + """

            Current HTML (truncated):
            """ + dom.ifBlank { "(empty workshop)" } + """

            Steps so far: """ + stepsTaken + """.
            Reply with exactly ONE JSON object, no prose:
            {"action":"inject","html":"<full html document>"}
            {"action":"done","summary":"..."}
            {"action":"wait"}
            First step must be inject. Never done until a designed page is on screen.
            The HTML MUST be a complete document that looks good at phone width:
            - dark canvas (#0d1117), never a white document
            - real CSS: hero, type scale, spacing, accent #58a6ff, cards or sections
            - viewport meta, system-ui, no unstyled body text
            - a title, a short lead, and 2–3 sections — not a wall of paragraphs
            Escape quotes inside html as \".
        """.trimIndent()

        fun lookPrompt(goal: String, dom: String, look: Int): String = """
            You are looking at a SCREENSHOT of the page you just built. Goal:
            """ + goal + """

            Look number """ + look + """. HTML excerpt:
            """ + dom.take(2000) + """

            If this is plain text on white, default browser fonts, a wall of
            paragraphs, or anything that looks like a draft document — FAIL.
            Inject a complete restyled HTML document (dark canvas, hero, type,
            color, cards). Do not tweak one CSS rule.
            Only {"action":"done","summary":"..."} if it already looks like a
            designed landing page you would ship.
            Reply with ONE JSON object, no prose.
            {"action":"inject","html":"<full html>"} or {"action":"done","summary":"..."}
        """.trimIndent()

        /** Extracts the first balanced {...} and maps it to an [AgentAction]. */
        fun parseAction(text: String): AgentAction? {
            val start = text.indexOf('{')
            if (start >= 0) {
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
                if (end >= 0) {
                    val parsed = runCatching {
                        val obj = Json.parseToJsonElement(text.substring(start, end + 1)) as JsonObject
                        val type = obj["action"]?.jsonPrimitive?.contentOrNull?.lowercase()
                            ?: return@runCatching null
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
                    if (parsed != null) return parsed
                }
            }
            // Models often dump a markdown HTML fence instead of the JSON
            // contract. Treat that as inject so a page still lands.
            return extractHtml(text)?.let { AgentAction(type = "inject", html = it) }
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
