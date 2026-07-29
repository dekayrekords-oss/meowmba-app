package com.example.meowmba.core.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * `web.search` — search the web via DuckDuckGo's lightweight HTML endpoint.
 * Returns up to 10 result titles, snippets, and URLs.
 *
 * SAFE risk, no confirmation needed — purely read-only.
 */
class WebSearchTool : Tool {
    override val definition = ToolDefinition(
        id = "web.search",
        displayName = "Web search",
        description = "Search the internet for information. Returns up to 10 result titles, " +
            "snippets, and links.",
        inputSchema = mapOf("query" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 15_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val query = args["query"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'query'")
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://lite.duckduckgo.com/lite/?q=$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val results = parseResults(html)
            if (results.isEmpty()) {
                return@withContext ToolResult.Success("No results found for \"$query\".")
            }
            val formatted = results.joinToString("\n\n") { (title, snippet, link) ->
                "$title\n$snippet\n$link"
            }
            ToolResult.Success("Web search results for \"$query\":\n\n$formatted")
        } catch (e: Exception) {
            ToolResult.Failure("Web search failed: ${e.message ?: "unknown error"}")
        }
    }

    private data class SearchResult(val title: String, val snippet: String, val link: String)

    private fun parseResults(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        // DuckDuckGo Lite returns a simple table with class="result".
        // Each result row has a <a> with the link/title and a <td> with the snippet.
        val resultBlocks = html.split("<tr class=\"result\">")
        if (resultBlocks.size < 2) {
            // Try alternate parsing for different HTML structure
            return parseAlternate(html)
        }
        for (block in resultBlocks.drop(1)) {
            val link = extractTagAttr(block, "a", "href")?.trim()
            val title = extractTagContent(block, "a")?.trim()
            val snippetBlock = block.substringAfter("</a>").substringBefore("</tr>")
            val snippet = stripHtml(snippetBlock).trim().replace(Regex("\\s+"), " ")
            if (link != null && title != null) {
                results.add(SearchResult(title, snippet, link))
            }
        }
        return results.take(10)
    }

    private fun parseAlternate(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val linkPattern = Regex("<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>")
        val matches = linkPattern.findAll(html)
        val snippetPattern = Regex("<td[^>]*class=\"result-snippet\"[^>]*>([^<]+)</td>")
        val snippets = snippetPattern.findAll(html).map { it.groupValues[1].trim() }.toList()
        var snippetIndex = 0
        for (match in matches) {
            val href = match.groupValues[1]
            val title = stripHtml(match.groupValues[2]).trim()
            if (href.startsWith("http") && title.isNotBlank()) {
                val snippet = if (snippetIndex < snippets.size) {
                    stripHtml(snippets[snippetIndex++]).trim()
                } else ""
                results.add(SearchResult(title, snippet, href))
            }
        }
        return results.take(10)
    }

    private fun extractTagAttr(html: String, tag: String, attr: String): String? {
        val regex = Regex("<$tag[^>]*$attr=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.get(1)
    }

    private fun extractTagContent(html: String, tag: String): String? {
        val regex = Regex("<$tag[^>]*>([^<]+)</$tag>", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.get(1)
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), "").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'")
}
