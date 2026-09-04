package com.example.meowmba.core.plugins

import org.json.JSONObject

/**
 * Open plugin & connector system — the user-facing surface over the tool
 * registry and the MCP layer.
 *
 * A [Connector] is anything the local AI can reach: an installed app exposed
 * through a plugin, an external MCP server (HTTP, e.g. Android Studio on the
 * LAN), or a user-created plugin/skill. Each connector carries a distinct
 * icon and an optional credential (API key / token / endpoint) that the agent
 * uses to authenticate. The registry keeps the list in stable order and turns
 * each connector's tools into ordinary [com.example.meowmba.core.agent.tools.Tool]
 * instances so the existing orchestrator/permission machinery handles them.
 *
 * Security rules inherited from the project: secrets live in
 * [com.example.meowmba.core.security.SecureStorageManager]; a connector only
 * ever knows the key the user supplied for it; nothing is a shell executor.
 */

/** What kind of thing a connector is. Drives which setup fields apply. */
enum class ConnectorKind {
    /** An installed package the AI can drive through a plugin. */
    APP,

    /** An external MCP server reached over streamable HTTP (LAN / cloud). */
    MCP_SERVER,

    /** A user-authored plugin or skill (with an icon and tool definition). */
    USER_PLUGIN,
}

/** Where the connector's tool surfaces come from. */
enum class ConnectorBackend {
    /** Local in-process server (e.g. the app's own MCP-reachable tools). */
    LOCAL,

    /** A remote MCP server over streamable HTTP. */
    MCP_HTTP,

    /** A statically-embedded plugin provided by the build for a known app. */
    EMBEDDED_PLUGIN,

    /** A live service integration driven by a user-supplied API token (e.g. Spotify). */
    SERVICE,
}

/** Immutable, serializable descriptor of one connector/plugin. */
data class Connector(
    val id: String,
    val name: String,
    val description: String,
    val kind: ConnectorKind,
    val backend: ConnectorBackend,
    /**
     * Package name when [kind] == APP. Used to load the real launcher icon and
     * to build an app-opening tool.
     */
    val packageName: String? = null,
    /** MCP endpoint URL when [backend] == MCP_HTTP. */
    val endpoint: String? = null,
    /** True when the user has supplied the credential for this connector. */
    val configured: Boolean = false,
    /** Tool ids this connector contributes (filled after registration). */
    val toolIds: List<String> = emptyList(),
    /** Whether the connector is enabled for the agent. */
    val enabled: Boolean = true,
    /**
     * Every user-created plugin/skill MUST carry a distinct icon. Emoji icons
     * are UTF-8 and stored escaped in JSON (a single code point is fine); a
     * blank/missing icon is rejected at creation time.
     */
    val iconToken: String,
) {
    companion object {
        fun fromJson(o: JSONObject): Connector = Connector(
            id = o.getString("id"),
            name = o.getString("name"),
            description = o.optString("description"),
            kind = o.optString("kind").let { runCatching { ConnectorKind.valueOf(it) }.getOrDefault(ConnectorKind.USER_PLUGIN) },
            backend = o.optString("backend").let { runCatching { ConnectorBackend.valueOf(it) }.getOrDefault(ConnectorBackend.LOCAL) },
            packageName = o.optString("packageName").takeIf { it.isNotBlank() },
            endpoint = o.optString("endpoint").takeIf { it.isNotBlank() },
            configured = o.optBoolean("configured"),
            toolIds = o.optJSONArray("toolIds")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { i -> i.isNotBlank() } }
            } ?: emptyList(),
            enabled = o.optBoolean("enabled", true),
            iconToken = o.optString("iconToken"),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("description", description)
        .put("kind", kind.name)
        .put("backend", backend.name)
        .put("packageName", packageName ?: "")
        .put("endpoint", endpoint ?: "")
        .put("configured", configured)
        .put("enabled", enabled)
        .put("iconToken", iconToken)
        .apply {
            put("toolIds", org.json.JSONArray().apply { toolIds.forEach(::put) })
        }
}
