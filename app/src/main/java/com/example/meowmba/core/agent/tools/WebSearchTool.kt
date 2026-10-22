package com.example.meowmba.core.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * `web.search` — keyless web search across several free engines (a Kotlin
 * port of donsetch's keyless multi-search: DuckDuckGo lite/html, Bing, Brave,
 * Mojeek). Each engine is scraped in parallel, urls are redirect-decoded, and
 * results are fused by URL; an engine that returns <3 hits is treated as
 * failed (CAPTCHA/empty) rather than authoritative.
 *
 * Honesty rules: every returned URL is a real http(s) target (no redirect
 * wrapper), and if every engine fails the tool says so instead of inventing
 * results.
 *
 * SAFE risk, no confirmation needed — purely read-only.
 */
class WebSearchTool(
    private val engines: List<String> = DEFAULT_ENGINES,
    private val timeoutPerEngineMs: Int = 10_000,
    private val fetchHtml: (String) -> String = { fetch(it, timeoutPerEngineMs) },
) : Tool {
    override val definition = ToolDefinition(
        id = "web.search",
        displayName = "Web search",
        description = "Search the internet across multiple free engines (no API key). " +
            "Returns up to 12 result titles, snippets, and real links.",
        inputSchema = mapOf("query" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 30_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val query = args["query"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'query'")
        try {
            val fused = search(query)
            if (fused.isEmpty()) {
                ToolResult.Success("No usable results found for \"$query\" (engines returned nothing or a challenge).")
            } else {
                val lines = fused.take(12).mapIndexed { i, hit ->
                    "${i + 1}. ${hit.title}\n${hit.snippet}\n${hit.url}"
                }
                ToolResult.Success("Web search results for \"$query\" (${fused.size} unique):\n\n${lines.joinToString("\n\n")}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.Failure("Web search failed: ${e.message ?: "unknown error"}")
        }
    }

    /** Typed evidence for callers such as shopping; no reparsing a model-facing transcript. */
    suspend fun search(query: String): List<WebHit> = withContext(Dispatchers.IO) {
        coroutineScope {
            val perEngine = engines.map { engine ->
                async {
                    val url = KeylessWebSearch.searchUrl(engine, query) ?: return@async emptyList<WebHit>()
                    try {
                        KeylessWebSearch.parse(engine, fetchHtml(url))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // One blocked engine must not cancel the successful engines.
                        emptyList()
                    }
                }
            }
            KeylessWebSearch.fuse(perEngine.awaitAll().flatten())
        }
    }

    companion object {
        val DEFAULT_ENGINES = listOf("ddg_lite", "ddg_html", "bing", "brave", "mojeek")

        /** One blocked fetch; lets an engine fail without killing the search. */
        internal fun fetch(url: String, timeoutMs: Int): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (MeowmbaBot/1.0)")
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            try {
                val stream = if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
                return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } finally {
                conn.disconnect()
            }
        }
    }
}
