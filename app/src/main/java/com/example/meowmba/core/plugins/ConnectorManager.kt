package com.example.meowmba.core.plugins

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.meowmba.core.agent.tools.ConfirmationPolicy
import com.example.meowmba.core.agent.tools.RiskLevel
import com.example.meowmba.core.agent.tools.Tool
import com.example.meowmba.core.agent.tools.ToolDefinition
import com.example.meowmba.core.agent.tools.ToolEnvironment
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.mcp.McpHttpClient
import com.example.meowmba.core.mcp.McpProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Owns the user's plugins & connectors: discovers installed apps, remembers
 * configured external MCP servers and user-created plugins, and turns each
 * enabled connector's tools into ordinary [Tool] objects registered into a
 * [ToolRegistry] so the agent can use them.
 *
 * Persistence: the connector list (ids, names, endpoints, icons) lives in a
 * JSON file under `filesDir/connectors.json`. Secrets (API keys / tokens) are
 * NOT stored here — the host supplies a [secretProvider] backed by
 * [com.example.meowmba.core.security.SecureStorageManager].
 *
 * A user-created plugin/skill ALWAYS requires a non-blank [Connector.iconToken];
 * creation returns a failure otherwise.
 */
class ConnectorManager(
    private val appContext: Context,
    private val secretProvider: (connectorId: String) -> String? = { _ -> null },
) {

    private val file: File = File(appContext.filesDir, "connectors.json")

    private val _connectors = mutableListOf<Connector>()
    val connectors: List<Connector> get() = _connectors.toList()

    /** Load persisted connectors (idempotent; call once at startup). */
    fun load() {
        _connectors.clear()
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                _connectors.add(Connector.fromJson(arr.getJSONObject(i)))
            }
        }
    }

    private fun persist() {
        runCatching {
            file.writeText(JSONArray().apply { _connectors.forEach { put(it.toJson()) } }.toString())
        }
    }

    fun byId(id: String): Connector? = _connectors.firstOrNull { it.id == id }

    /** Add or replace a connector (used for app connector on first connect). */
    fun upsert(connector: Connector) {
        val idx = _connectors.indexOfFirst { it.id == connector.id }
        if (idx >= 0) _connectors[idx] = connector else _connectors.add(connector)
        persist()
    }

    fun remove(id: String): Boolean {
        val removed = _connectors.removeAll { it.id == id }
        if (removed) persist()
        return removed
    }

    /** Toggle enabled flag + persist. */
    fun setEnabled(id: String, enabled: Boolean): Boolean {
        val c = byId(id) ?: return false
        upsert(c.copy(enabled = enabled))
        return true
    }

    /** Trigger a re-scan of which connectors are configured (secret presence). */
    fun refreshConfigured() {
        _connectors.indices.forEach { i ->
            val c = _connectors[i]
            val hasSecret = when (c.backend) {
                ConnectorBackend.MCP_HTTP -> !(secretProvider(c.id).isNullOrBlank())
                ConnectorBackend.SERVICE -> {
                    val svc = ServiceIntegrations.byId(c.id.removePrefix("svc-"))
                    if (svc == null) true else !(secretProvider(svc.storageKey).isNullOrBlank())
                }
                else -> true
            }
            if (c.configured != hasSecret) _connectors[i] = c.copy(configured = hasSecret)
        }
        persist()
    }

    /**
     * Create a user plugin/skill. Returns failure when the icon is missing or
     * blank — a user-made plugin never ships without a distinct icon.
     */
    fun createUserPlugin(name: String, description: String, iconToken: String): Result<Connector> {
        val icon = iconToken.trim()
        if (icon.isEmpty()) return Result.failure(IllegalArgumentException("A plugin needs a chosen icon."))
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Give the plugin a name."))
        val id = "plugin-" + UUID.randomUUID().toString().take(8)
        val connector = Connector(
            id = id,
            name = name.trim(),
            description = description.trim(),
            kind = ConnectorKind.USER_PLUGIN,
            backend = ConnectorBackend.LOCAL,
            iconToken = icon,
            configured = true,
            enabled = true,
        )
        _connectors.add(connector)
        persist()
        return Result.success(connector)
    }

    /**
     * Discover installed apps that have a MAIN launcher intent and are not
     * this app itself, in display order.
     */
    suspend fun discoverApps(): List<DiscoveredApp> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        runCatching {
            pm.queryIntentActivities(main, PackageManager.MATCH_ALL)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == appContext.packageName) return@mapNotNull null
                    DiscoveredApp(
                        packageName = pkg,
                        label = ri.loadLabel(pm)?.toString() ?: pkg,
                        icon = runCatching { ri.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                        capability = AppCapability.INTENT_ONLY,
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    /** The connector descriptor for a discovered app (existing if already added). */
    fun connectorForApp(app: DiscoveredApp): Connector {
        val existing = _connectors.firstOrNull { it.packageName == app.packageName }
        return existing ?: Connector(
            id = "app-" + app.packageName,
            name = app.label,
            description = "Drives ${app.label} through a plugin.",
            kind = ConnectorKind.APP,
            backend = ConnectorBackend.EMBEDDED_PLUGIN,
            packageName = app.packageName,
            iconToken = app.packageName,
            configured = false,
            enabled = false,
        )
    }

    /** Pings + lists an external MCP server so the user sees real capability first. */
    suspend fun testMcpServer(endpoint: String): Result<List<McpProtocol.McpTool>> = withContext(Dispatchers.IO) {
        val client = McpHttpClient(endpoint)
        try {
            if (!client.ping()) {
                Result.failure(IllegalStateException("No MCP server answered at that address."))
            } else {
                val tools = client.listTools()
                if (tools.isEmpty()) Result.failure(IllegalStateException("Server answered but exposed no tools."))
                else Result.success(tools)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Register an external MCP server connector (persist it first via [upsert]
     * with backend MCP_HTTP + endpoint), pings it, and pulls its tools.
     */
    suspend fun connectMcpServer(connector: Connector): Result<Unit> = withContext(Dispatchers.IO) {
        val endpoint = connector.endpoint ?: return@withContext Result.failure(IllegalArgumentException("No endpoint set."))
        val client = McpHttpClient(endpoint)
        try {
            val tools = client.listTools()
            if (tools.isEmpty()) return@withContext Result.failure(IllegalStateException("Server exposed no tools."))
            upsert(connector)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Register every enabled connector's tools into [registry]. */
    suspend fun registerToolsInto(registry: ToolRegistry) {
        _connectors.forEach { c -> if (c.enabled) registerConnectorInto(c, registry) }
    }

    private suspend fun registerConnectorInto(c: Connector, registry: ToolRegistry) {
        when (c.backend) {
            ConnectorBackend.MCP_HTTP -> {
                val endpoint = c.endpoint ?: return
                val client = McpHttpClient(endpoint)
                val remote = runCatching { client.listTools() }.getOrNull() ?: return
                remote.forEach { mt ->
                    val id = c.id + "." + mt.name
                    registry.register(RpcTool(
                        id = id,
                        displayName = mt.name.substringAfterLast('.'),
                        description = mt.description,
                        schema = mcpSchema(mt.inputSchema),
                        risk = RiskLevel.MODERATE,
                        confirm = ConfirmationPolicy.ALWAYS,
                    ) {
                        val params = JSONObject().apply { it.forEach { (k, v) -> put(k, v) } }
                        val out = client.callTool(mt.name, params)
                        if (out.isError) ToolResult.Failure(out.text) else ToolResult.Success(out.text)
                    })
                }
            }
            ConnectorBackend.EMBEDDED_PLUGIN -> {
                val pkg = c.packageName ?: return
                registry.register(RpcTool(
                    id = c.id + ".open",
                    displayName = c.name,
                    description = "Opens the ${c.name} app.",
                    schema = emptyMap(),
                    risk = RiskLevel.SAFE,
                    confirm = ConfirmationPolicy.NONE,
                ) {
                    runCatching {
                        val launch = appContext.packageManager.getLaunchIntentForPackage(pkg)
                        if (launch == null) ToolResult.Failure("${c.name} is not installed.")
                        else {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            appContext.startActivity(launch)
                            ToolResult.Success("Opened ${c.name}.")
                        }
                    }.getOrElse { ToolResult.Failure(it.message ?: "could not open") }
                })
            }
            ConnectorBackend.LOCAL -> {
                registry.register(RpcTool(
                    id = c.id + ".run",
                    displayName = c.name,
                    description = c.description,
                    schema = mapOf("input" to "string"),
                    risk = RiskLevel.SAFE,
                    confirm = ConfirmationPolicy.NONE,
                ) { args ->
                    val input = args["input"].orEmpty()
                    ToolResult.Success(SkillRunner(appContext).run(c.name, input))
                })
            }
            ConnectorBackend.SERVICE -> {
                val svc = ServiceIntegrations.byId(c.id.removePrefix("svc-")) ?: return
                ServiceIntegrations.byId(svc.id)?.let { service ->
                    service.toolFactory { secretProvider(service.storageKey) }
                        .forEach { registry.register(it) }
                }
            }
        }
    }

    /** Installed app the user picked from discovery. */
    data class DiscoveredApp(
        val packageName: String,
        val label: String,
        /** Actual launcher artwork supplied by PackageManager, never a guessed logo. */
        val icon: Bitmap? = null,
        /** Conservative: a launcher intent is not evidence of an MCP/AppFunctions API. */
        val capability: AppCapability = AppCapability.INTENT_ONLY,
    )
}

/** What Meowmba has actually discovered for an installed package. */
enum class AppCapability(val label: String, val detail: String) {
    INTENT_ONLY("Open app", "Meow can open this app; it has not advertised an MCP or AppFunctions connection."),
    APP_FUNCTIONS_RESTRICTED("AppFunctions restricted", "Android has not granted this build permission to execute this app's functions."),
    MCP_SERVER("MCP available", "This app advertised a reachable MCP connection."),
}

private fun mcpSchema(schema: JSONObject): Map<String, String> {
    val props = schema.optJSONObject("properties") ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    props.keys().forEach { key ->
        out[key] = props.optJSONObject(key)?.optString("type", "string") ?: "string"
    }
    return out
}

/** Minimal generic tool used to wrap plugin/MCP behaviour. */
private class RpcTool(
    id: String,
    displayName: String,
    description: String,
    schema: Map<String, String>,
    risk: RiskLevel,
    confirm: ConfirmationPolicy,
    private val body: suspend (Map<String, String>) -> ToolResult,
) : Tool {
    override val definition = ToolDefinition(
        id = id,
        displayName = displayName,
        description = description,
        inputSchema = schema,
        riskLevel = risk,
        confirmationPolicy = confirm,
        environments = setOf(ToolEnvironment.PHONE),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = body(args)
}
