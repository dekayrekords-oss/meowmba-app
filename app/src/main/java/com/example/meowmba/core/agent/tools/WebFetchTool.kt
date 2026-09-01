package com.example.meowmba.core.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * `web.fetch` — fetch a page and extract its readable text, a Kotlin port of
 * donsetch's fetch+extract (the part that survives without the Rust binary).
 *
 * It follows redirects, decodes common entities, strips scripts/styles/nav
 * boilerplate, and returns the article text plus the canonical title. Size is
 * bounded so a monster page cannot blow the token budget.
 *
 * SAFE risk, read-only.
 */
class WebFetchTool(
    private val timeoutMs: Int = 15_000,
    private val maxChars: Int = 12_000,
    private val fetchText: (String) -> String = { fetch(it, timeoutMs) },
) : Tool {
    override val definition = ToolDefinition(
        id = "web.fetch",
        displayName = "Fetch a page",
        description = "Fetch a URL and return its readable text content (title + article body).",
        inputSchema = mapOf("url" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 25_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val url = args["url"]?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return@withContext ToolResult.Failure("Missing or invalid 'url' (must start with http:// or https://)")
        try {
            val html = fetchText(url)
            if (html.isBlank()) return@withContext ToolResult.Failure("Empty response from $url")
            val doc = extract(html)
            val body = doc.body
            if (body.isBlank()) return@withContext ToolResult.Failure("Could not extract readable text from $url")
            val out = buildString {
                doc.title?.takeIf { it.isNotBlank() }?.let { append(it).append("\n\n") }
                append(body)
            }
            ToolResult.Success(out.take(maxChars))
        } catch (e: Exception) {
            ToolResult.Failure("Fetch failed: ${e.message ?: "unknown error"}")
        }
    }

    data class Extracted(val title: String?, val body: String)

    /** Pure, unit-testable HTML → readable text. */
    internal fun extract(html: String): Extracted {
        var t = html
        t = Regex("(?is)<head>.*?</head>").replace(t, " ")
        t = Regex("(?is)<script.*?</script>").replace(t, " ")
        t = Regex("(?is)<style.*?</style>").replace(t, " ")
        t = Regex("(?is)<nav.*?</nav>").replace(t, " ")
        t = Regex("(?is)<footer.*?</footer>").replace(t, " ")
        t = Regex("(?is)<header.*?</header>").replace(t, " ")
        t = Regex("(?is)<form.*?</form>").replace(t, " ")

        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1).orEmpty()
            .let { collapse(stripEntities(it)) }

        // Turn tags into boundaries so "text</p><p>more" reads "text\nmore".
        t = t.replace(Regex("(?i)</?(p|div|br|li|h[1-6]|tr)[^>]*>"), "\n")
        // Collapse the rest of the tags.
        t = t.replace(Regex("<[^>]+>"), " ")
        t = stripEntities(collapse(t))
        return Extracted(title.takeIf { it.isNotEmpty() }, collapse(t))
    }

    companion object {
        private fun fetch(url: String, timeoutMs: Int): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (MeowmbaBot/1.0)")
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            try {
                val stream = if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
                return stream?.bufferedReader()?.readText().orEmpty()
            } finally {
                conn.disconnect()
            }
        }

        private fun stripEntities(s: String): String =
            s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")

        private fun collapse(s: String): String =
            s.replace(Regex("\\s+"), " ").trim()
    }
}
