package com.example.meowmba.core.plugins

import com.example.meowmba.core.agent.tools.ConfirmationPolicy
import com.example.meowmba.core.agent.tools.RiskLevel
import com.example.meowmba.core.agent.tools.Tool
import com.example.meowmba.core.agent.tools.ToolDefinition
import com.example.meowmba.core.agent.tools.ToolEnvironment
import com.example.meowmba.core.agent.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Live service integrations — apps Meowmba can drive for real once you've
 * pasted an API token for them. Each entry knows how to build its tools
 * ([toolFactory]) and where the token comes from ([keyLink]).
 *
 * These back the [ConnectorBackend.SERVICE] connectors: when you save a key for
 * a known service in the Connections sheet, a connector is created whose tools
 * call the service's real API.
 */
object ServiceIntegrations {

    data class Service(
        val id: String,
        val name: String,
        val keyLink: String,
        val storageKey: String,
        /** Builds the live tools for this service, given a token supplier. */
        val toolFactory: (() -> String?) -> List<Tool>,
    )

    val all: List<Service> = listOf(
        Service(
            id = "spotify",
            name = "Spotify",
            keyLink = "https://developer.spotify.com",
            storageKey = "connector_spotify",
            toolFactory = { token -> spotifyTools(token) },
        ),
        Service(
            id = "github",
            name = "GitHub",
            keyLink = "https://github.com/settings/tokens",
            storageKey = "connector_github",
            toolFactory = { token -> githubTools(token) },
        ),
    )

    fun byId(id: String): Service? = all.firstOrNull { it.id == id }

    // ── Spotify ────────────────────────────────────────────────────────────

    private fun spotifyTools(token: () -> String?): List<Tool> = listOf(
        object : Tool {
            override val definition = ToolDefinition(
                id = "spotify.search",
                displayName = "Spotify search",
                description = "Search Spotify for a track, album, artist or playlist and report the top match.",
                inputSchema = mapOf("query" to "string", "type" to "track|album|artist|playlist"),
                riskLevel = RiskLevel.SAFE,
                environments = setOf(ToolEnvironment.CLOUD),
                confirmationPolicy = ConfirmationPolicy.NONE,
            )

            override suspend fun execute(args: Map<String, String>): ToolResult {
                val q = args["query"]?.takeIf { it.isNotBlank() }
                    ?: return ToolResult.Failure("What should I search Spotify for?")
                val type = args["type"]?.takeIf { it.isNotBlank() } ?: "track"
                val t = token() ?: return ToolResult.Failure(
                    "No Spotify token set. In Connections, tap Spotify and paste your token — you can get one at https://developer.spotify.com."
                )
                return runCatching {
                    val quoted = URLEncoder.encode(q, "UTF-8")
                    val json = getJson(
                        "https://api.spotify.com/v1/search?q=$quoted&type=$type&limit=5",
                        "Bearer $t",
                    )
                    val items = json.optJSONObject("${type}s")?.optJSONArray("items") ?: JSONArray()
                    if (items.length() == 0) ToolResult.Success("No Spotify results for \"$q\".")
                    else {
                        val out = (0 until items.length()).joinToString("\n") { i ->
                            formatSpotifyItem(type, items.getJSONObject(i))
                        }
                        ToolResult.Success(out)
                    }
                }.getOrElse { ToolResult.Failure("Spotify search failed: ${it.message}") }
            }
        },
    )

    private fun formatSpotifyItem(type: String, o: JSONObject): String {
        val name = o.optString("name")
        return when (type) {
            "album" -> "• $name — ${o.optJSONObject("artists")?.optString("name")} (${o.optString("release_date")})"
            "artist" -> "• $name (${o.optInt("popularity")} popularity)"
            "playlist" -> "• $name (${o.optJSONObject("owner")?.optString("display_name")})"
            else -> {
                val artists = o.optJSONArray("artists")
                val artist = if (artists != null && artists.length() > 0) artists.getJSONObject(0).optString("name") else "?"
                "• $name — $artist"
            }
        }
    }

    // ── GitHub ─────────────────────────────────────────────────────────────

    private fun githubTools(token: () -> String?): List<Tool> = listOf(
        object : Tool {
            override val definition = ToolDefinition(
                id = "github.repos",
                displayName = "GitHub repos",
                description = "List a GitHub user's public repositories with star counts.",
                inputSchema = mapOf("username" to "string"),
                riskLevel = RiskLevel.SAFE,
                environments = setOf(ToolEnvironment.CLOUD),
                confirmationPolicy = ConfirmationPolicy.NONE,
            )

            override suspend fun execute(args: Map<String, String>): ToolResult {
                val u = args["username"]?.takeIf { it.isNotBlank() }
                    ?: return ToolResult.Failure("Which GitHub user's repos should I list?")
                return runCatching {
                    val json = getJsonArray("https://api.github.com/users/${URLEncoder.encode(u, "UTF-8")}/repos", "")
                    val rows = (0 until json.length()).joinToString("\n") { i ->
                        val o = json.getJSONObject(i)
                        "• ${o.optString("name")} — ★${o.optLong("stargazers_count")} ${o.optString("description").take(60)}"
                    }
                    ToolResult.Success(if (rows.isBlank()) "No public repos for $u." else rows)
                }.getOrElse { ToolResult.Failure("GitHub lookup failed: ${it.message}") }
            }
        },
    )

    // ── transport ──────────────────────────────────────────────────────────

    private fun getJson(url: String, bearer: String): JSONObject =
        JSONObject(getBody(url, bearer))

    private fun getJsonArray(url: String, bearer: String): JSONArray =
        JSONArray(getBody(url, bearer))

    private fun getBody(url: String, bearer: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Meowmba/1.0")
            connectTimeout = 15_000
            readTimeout = 15_000
            if (bearer.isNotBlank()) setRequestProperty("Authorization", bearer)
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }
            ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: ${body.take(200)}")
        }
        return body
    }
}
