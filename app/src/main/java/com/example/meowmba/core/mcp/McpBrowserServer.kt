package com.example.meowmba.core.mcp

import com.example.meowmba.core.browser.OffscreenBrowser
import org.json.JSONObject

/**
 * Serves the local WebView as MCP tools, so the agent can drive a real browser
 * (render JS, click, type, read the DOM) without any UI panel.
 *
 * This is the option-2 build: the swipe-left browser panel is gone, but the
 * engine survives as a set of tools behind an MCP-shaped boundary. Tools are
 * declared once here and bridged into the agent's tool registry by
 * [McpToolBridge], so the model sees them the same way it sees any other tool.
 *
 * Descriptions are written to steer the model: `browser.read` is the cheap one
 * and `browser.html` is the expensive one, and the model is told so, because
 * left alone it reaches for the DOM dump first and floods its own context.
 */
class McpBrowserServer(
    private val browser: OffscreenBrowser,
) : McpProtocol.Server {

    override fun listTools(): List<McpProtocol.McpTool> = listOf(
        McpProtocol.McpTool(
            name = "browser.navigate",
            description = "Open a URL in the local browser and wait for it to load. " +
                "Call this first; the other browser tools act on the current page.",
            inputSchema = McpProtocol.schema("url" to "Absolute http(s) URL to open."),
        ),
        McpProtocol.McpTool(
            name = "browser.read",
            description = "Read the visible text of the current page (title, headings, " +
                "paragraphs, links). PREFER this over browser.html — it is short and " +
                "already cleaned of nav and cookie chrome.",
            inputSchema = JSONObject().put("type", "object").put("properties", JSONObject()),
        ),
        McpProtocol.McpTool(
            name = "browser.links",
            description = "List the links on the current page as 'text -> url' lines. " +
                "Use this to choose where to go next.",
            inputSchema = JSONObject().put("type", "object").put("properties", JSONObject()),
        ),
        McpProtocol.McpTool(
            name = "browser.html",
            description = "Dump the raw HTML/DOM of the current page. EXPENSIVE — only " +
                "when you need markup, classes or attributes that browser.read drops.",
            inputSchema = JSONObject().put("type", "object").put("properties", JSONObject()),
        ),
        McpProtocol.McpTool(
            name = "browser.click",
            description = "Click an element. Pass a CSS selector (#id, .class, button), " +
                "or 'tag=text' to match by visible text, e.g. 'button=Sign in'.",
            inputSchema = McpProtocol.schema("selector" to "CSS selector, or tag=text."),
        ),
        McpProtocol.McpTool(
            name = "browser.type",
            description = "Type text into an input/textarea (fires input and change " +
                "events so React and friends notice). Select the field first if needed.",
            inputSchema = McpProtocol.schema(
                "selector" to "CSS selector of the input field.",
                "text" to "Text to type into the field.",
            ),
        ),
        McpProtocol.McpTool(
            name = "browser.scroll",
            description = "Scroll the page vertically by a number of pixels (negative " +
                "scrolls up). Use it to reach content that loads further down.",
            inputSchema = McpProtocol.schema("pixels" to "Pixels to scroll, e.g. 800 or -400."),
        ),
    )

    override suspend fun callTool(name: String, args: JSONObject): McpProtocol.McpResult {
        return try {
            val out = when (name) {
                "browser.navigate" -> browser.navigate(arg(args, "url"))
                "browser.read" -> browser.readText()
                "browser.links" -> browser.links()
                "browser.html" -> browser.html()
                "browser.click" -> browser.click(arg(args, "selector"))
                "browser.type" -> browser.type(arg(args, "selector"), arg(args, "text"))
                "browser.scroll" -> browser.scroll(args.optString("pixels", "800").toIntOrNull() ?: 800)
                else -> return McpProtocol.McpResult("unknown tool: $name", isError = true)
            }
            McpProtocol.McpResult(out)
        } catch (e: Exception) {
            McpProtocol.McpResult("${e.javaClass.simpleName}: ${e.message}", isError = true)
        }
    }

    private fun arg(args: JSONObject, key: String): String {
        val v = args.optString(key)
        if (v.isNullOrBlank()) throw IllegalArgumentException("missing required argument: $key")
        return v
    }
}
