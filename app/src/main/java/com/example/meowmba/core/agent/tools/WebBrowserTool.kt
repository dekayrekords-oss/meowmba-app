package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.browser.WebViewBridge
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Drives the on-screen browser: open pages, click, type, scroll, and take a
 * screenshot (for the vision pipeline to look at what changed).
 *
 * The browser must be open (swipe left on the face). All DOM work goes
 * through injected helpers; selectors support CSS or tag=exact text.
 */
class WebBrowserTool(private val screenshotDir: File, private val pagesDir: File? = null) : Tool {
    override val definition = ToolDefinition(
        id = "web.browser",
        displayName = "Drive the browser",
        description = "Control the swipe-left agent browser. Actions: open (url), " +
            "workshop (blank build canvas), inject (html — build/rewrite the page), " +
            "html (full source), scrape (readable text + links, saved), " +
            "title, links, click (selector or tag=text), type (selector + text), " +
            "scroll, screenshot, save (persist the page). " +
            "Use this to build HTML or scrape a live page the user can see.",
        inputSchema = mapOf(
            "action" to "string", "url" to "string", "selector" to "string",
            "text" to "string", "pixels" to "int",
        ),
        riskLevel = RiskLevel.MODERATE,
        timeoutMs = 20_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"]?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) return ToolResult.Failure("No action given.")
        return when (action) {
            "open" -> {
                val url = args["url"]?.trim().orEmpty()
                if (url.isEmpty()) return ToolResult.Failure("No url given.")
                eval("window.location.href = '${url.replace("'", "\\'")}'; 'navigating'")
            }
            "title", "where" -> eval("window.__meow ? window.__meow.title() : location.href")
            "links" -> eval("window.__meow ? window.__meow.links() : 'helpers missing'")
            "click" -> {
                val sel = args["selector"]?.trim().orEmpty()
                if (sel.isEmpty()) return ToolResult.Failure("No selector given.")
                eval("window.__meow.click('${jsEscape(sel)}')")
            }
            "type" -> {
                val sel = args["selector"]?.trim().orEmpty()
                val text = args["text"].orEmpty()
                if (sel.isEmpty()) return ToolResult.Failure("No selector given.")
                eval("window.__meow.type('${jsEscape(sel)}', '${jsEscape(text)}')")
            }
            "scroll" -> {
                val px = args["pixels"]?.toIntOrNull() ?: 400
                eval("window.__meow.scroll($px)")
            }
            "screenshot" -> screenshot()
            "inject" -> {
                val html = args["html"].orEmpty()
                if (html.isBlank()) return ToolResult.Failure("No html given.")
                val wrote = eval(
                    "window.__meow.inject(`${html.replace("\\", "\\\\").replace("`", "\\`").replace("\${", "\\\${")}`)"
                )
                eval(WebViewBridge.DOM_HELPERS)
                wrote
            }
            "html" -> eval("window.__meow ? window.__meow.pageHtml() : 'helpers missing'")
            "workshop" -> {
                WebViewBridge.loadWorkshop()
                ToolResult.Success("Workshop canvas is open.")
            }
            "scrape" -> scrapePage(args["name"])
            "save" -> {
                val dir = pagesDir ?: return ToolResult.Failure("No pages directory configured.")
                val name = (args["name"]?.trim() ?: "page").replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                val htmlResult = eval("window.__meow ? window.__meow.pageHtml() : ''")
                if (htmlResult !is ToolResult.Success) return htmlResult
                runCatching {
                    dir.mkdirs()
                    val file = File(dir, "$name.html")
                    file.writeText(htmlResult.output)
                    // MEDIA_FILE makes the companion open the page in the
                    // browser panel right away — everything meow makes gets shown.
                    ToolResult.Success("Page saved: ${file.absolutePath}\nMEDIA_FILE:${file.absolutePath}")
                }.getOrElse { ToolResult.Failure("Save failed: ${it.message}") }
            }
            "openlocal" -> {
                val name = (args["name"]?.trim() ?: "page").replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                val dir = pagesDir ?: return ToolResult.Failure("No pages directory configured.")
                val file = File(dir, "$name.html")
                if (!file.exists()) return ToolResult.Failure("No saved page named $name.html")
                eval("window.location.href = 'file://${file.absolutePath}'; 'opening local page'")
            }
            else -> ToolResult.Failure(
                "Unknown action: $action (open/workshop/inject/html/scrape/title/links/click/type/scroll/screenshot/save)"
            )
        }
    }

    private suspend fun eval(script: String): ToolResult =
        suspendCancellableCoroutine { cont ->
            WebViewBridge.evaluate(script) { result ->
                if (result.startsWith("ERROR")) cont.resume(ToolResult.Failure(result))
                else cont.resume(ToolResult.Success(result.ifBlank { "done" }))
            }
        }

    private suspend fun scrapePage(nameArg: String?): ToolResult {
        val raw = eval("window.__meow ? window.__meow.scrape() : ''")
        if (raw !is ToolResult.Success) return raw
        val body = raw.output
        if (body.isBlank() || body == "null") return ToolResult.Failure("Nothing to scrape.")
        val dir = pagesDir ?: return ToolResult.Success(body.take(1500))
        return runCatching {
            dir.mkdirs()
            val name = (nameArg?.trim() ?: "scrape").replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
            val file = File(dir, "$name.md")
            file.writeText(body)
            ToolResult.Success("Scraped ${body.length} chars → ${file.name}\n${body.take(1200)}")
        }.getOrElse { ToolResult.Failure("Scrape save failed: ${it.message}") }
    }

    private suspend fun screenshot(): ToolResult =
        suspendCancellableCoroutine { cont ->
            WebViewBridge.screenshot(screenshotDir) { path ->
                if (path.startsWith("ERROR")) cont.resume(ToolResult.Failure(path))
                else cont.resume(ToolResult.Success("Page screenshot saved: $path"))
            }
        }

    companion object {
        internal fun jsEscape(s: String): String =
            s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    }
}
