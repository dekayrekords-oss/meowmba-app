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
        description = "Control the browser the user sees. Actions: open (url), " +
            "title (what page is this), links (list clickable links), click (selector or tag=text), " +
            "type (selector + text), scroll (pixels, +down/-up), screenshot (save page image). " +
            "Use this to browse, fill forms, and visually edit documents with the user.",
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
                eval("window.__meow.inject(`${html.replace("\\", "\\\\").replace("`", "\\`").replace("\${", "\\\${")}`)")
            }
            "html" -> eval("window.__meow ? window.__meow.pageHtml() : 'helpers missing'")
            "save" -> {
                val dir = pagesDir ?: return ToolResult.Failure("No pages directory configured.")
                val name = (args["name"]?.trim() ?: "page").replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                val htmlResult = eval("window.__meow ? window.__meow.pageHtml() : ''")
                if (htmlResult !is ToolResult.Success) return htmlResult
                runCatching {
                    dir.mkdirs()
                    val file = File(dir, "$name.html")
                    file.writeText(htmlResult.output)
                    ToolResult.Success("Page saved: ${file.absolutePath}")
                }.getOrElse { ToolResult.Failure("Save failed: ${it.message}") }
            }
            "openlocal" -> {
                val name = (args["name"]?.trim() ?: "page").replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                val dir = pagesDir ?: return ToolResult.Failure("No pages directory configured.")
                val file = File(dir, "$name.html")
                if (!file.exists()) return ToolResult.Failure("No saved page named $name.html")
                eval("window.location.href = 'file://${file.absolutePath}'; 'opening local page'")
            }
            else -> ToolResult.Failure("Unknown action: $action (open/title/links/click/type/scroll/screenshot)")
        }
    }

    private suspend fun eval(script: String): ToolResult =
        suspendCancellableCoroutine { cont ->
            WebViewBridge.evaluate(script) { result ->
                if (result.startsWith("ERROR")) cont.resume(ToolResult.Failure(result))
                else cont.resume(ToolResult.Success(result.ifBlank { "done" }))
            }
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
