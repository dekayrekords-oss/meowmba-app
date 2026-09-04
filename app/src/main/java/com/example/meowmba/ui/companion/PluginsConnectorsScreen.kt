package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.plugins.Connector
import com.example.meowmba.core.plugins.ConnectorKind

/**
 * Plugins & MCP Connectors menu (replaces the old "Tools" tile as the home for
 * things the AI can drive). Three surfaces:
 *
 *  1. Installed apps — discovered from the launcher, shown as grid tiles with
 *     their REAL icons. Tapping an app connects it so the agent can open/drive it.
 *  2. MCP servers — add an external server (e.g. Android Studio on the LAN),
 *     test it, and store its API key/endpoint.
 *  3. User plugins & skills — create with a name, description, and a MANDATORY
 *     distinct icon (blank icons are rejected before anything is saved).
 *
 * Every row/tile shows a Connected status and an easy off/on switch. Keys and
 * endpoints are entered inline so nothing is buried in a settings page.
 */
@Composable
fun PluginsConnectorsScreen(
    state: CompanionState,
    onClose: () -> Unit,
    onConnectApp: (String, String) -> Unit,
    onTestMcp: (String) -> Unit,
    onAddMcp: (String, String, String) -> Unit,
    onCreatePlugin: (String, String, String) -> Unit,
    onToggleConnector: (String, Boolean) -> Unit,
    onRemoveConnector: (String) -> Unit,
    onSetMcpServer: (Boolean) -> Unit,
) {
    var tab by remember { mutableStateOf(ConnectorTab.APPS) }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Plugins & MCP",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabChip("My Apps", tab == ConnectorTab.APPS) { tab = ConnectorTab.APPS }
                TabChip("MCP Servers", tab == ConnectorTab.MCP) { tab = ConnectorTab.MCP }
                TabChip("Plugins & Skills", tab == ConnectorTab.PLUGINS) { tab = ConnectorTab.PLUGINS }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                ConnectorTab.APPS -> AppsTab(state, onConnectApp, onToggleConnector, onRemoveConnector)
                ConnectorTab.MCP -> McpTab(
                    state, onTestMcp, onAddMcp, onToggleConnector, onRemoveConnector, onSetMcpServer,
                )
                ConnectorTab.PLUGINS -> PluginsTab(state, onCreatePlugin, onToggleConnector, onRemoveConnector)
            }
        }
    }
}

private enum class ConnectorTab { APPS, MCP, PLUGINS }

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color(0xFF0D1117) else Color.White.copy(alpha = 0.8f),
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFFFFD75E) else Color(0xFF1F2933))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

// ── Apps ────────────────────────────────────────────────────────────────────

@Composable
private fun AppsTab(
    state: CompanionState,
    onConnectApp: (String, String) -> Unit,
    onToggleConnector: (String, Boolean) -> Unit,
    onRemoveConnector: (String) -> Unit,
) {
    val appConnectors = state.connectors.filter { it.kind == ConnectorKind.APP }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Apps the AI can drive through a plugin. Tap an app to connect it — the agent can then open and use it.",
            color = Color.Gray,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        if (state.connectorBusy && state.discoverableApps.isEmpty()) {
            Text("Finding your apps…", color = Color(0xFF8B949E), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.discoverableApps) { app ->
                val existing = appConnectors.firstOrNull { it.packageName == app.packageName }
                val connected = existing?.configured == true
                AppTile(
                    label = app.label,
                    packageName = app.packageName,
                    connected = connected,
                    onClick = { if (!connected) onConnectApp(app.packageName, app.label) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Connected apps", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        if (appConnectors.isEmpty()) {
            Text("No apps connected yet.", color = Color(0xFF8B949E), fontSize = 14.sp)
        } else {
            appConnectors.forEach { c ->
                ConnectorRow(c, onToggleConnector, onRemoveConnector)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AppTile(label: String, packageName: String, connected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (connected) Color(0xFF16341F) else Color(0xFF1A1F29))
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .clickable(onClick = onClick),
    ) {
        DrawerIcon(packageName = packageName, size = 44.dp, label = label)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (connected) "● Connected" else "Tap to connect",
            color = if (connected) Color(0xFF4ADE80) else Color(0xFF8B949E),
            fontSize = 9.sp,
        )
    }
}

// ── MCP servers ─────────────────────────────────────────────────────────────

@Composable
private fun McpTab(
    state: CompanionState,
    onTestMcp: (String) -> Unit,
    onAddMcp: (String, String, String) -> Unit,
    onToggleConnector: (String, Boolean) -> Unit,
    onRemoveConnector: (String) -> Unit,
    onSetMcpServer: (Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // Host your own endpoint for Android Studio: the app is itself an MCP
        // server over the LAN once started. Read-only tools, no key needed.
        Surface(
            color = if (state.mcpServerRunning) Color(0xFF0E1F16) else Color(0xFF161B22),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Host an MCP endpoint", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Expose this phone as a read-only MCP server so Android Studio can " +
                                "connect to it over the LAN.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = state.mcpServerRunning, onCheckedChange = onSetMcpServer)
                }
                if (state.mcpServerRunning) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Listening — past this into Android Studio (Settings → Tools → MCP: " +
                            "add a streamable-HTTP server URL):",
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(color = Color(0xFF0D1117), shape = RoundedCornerShape(8.dp)) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "http://${state.mcpServerAddress}:${state.mcpServerPort}/mcp",
                                color = Color(0xFF7EE787),
                                fontSize = 13.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        "Only read-only tools are exposed (device info, battery, ASR status, " +
                            "recent memories and audit entries) — nothing destructive.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Connect an external Model Context Protocol server — for example the Android Studio MCP server on your network. Enter its address, test it, then save.",
            color = Color.Gray,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        Surface(color = Color(0xFF161B22), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Connector name (e.g. Android Studio)", color = Color.Gray) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Endpoint URL", color = Color.Gray) },
                    placeholder = { Text("http://192.168.1.10:24601/mcp", color = Color.Gray) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key / token (optional)", color = Color.Gray) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.connectorTestResult?.let { (ok, msg) ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        color = if (ok) Color(0xFF4ADE80) else Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                    )
                }
                saveError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { if (endpoint.isNotBlank()) onTestMcp(endpoint.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3A55)),
                        enabled = !state.connectorBusy,
                    ) {
                        if (state.connectorBusy) Text("Testing…", color = Color.White)
                        else Text("Test", color = Color.White)
                    }
                    Button(
                        onClick = {
                            val e = endpoint.trim()
                            if (e.isBlank()) { saveError = "Enter the endpoint URL first."; return@Button }
                            if (name.isBlank()) { saveError = "Give the connector a name."; return@Button }
                            saveError = null
                            onAddMcp(name.trim(), e, apiKey.trim())
                            name = ""; endpoint = ""; apiKey = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD75E)),
                    ) { Text("Save & Connect", color = Color(0xFF1A1400)) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("MCP servers", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        val mcps = state.connectors.filter { it.kind == ConnectorKind.MCP_SERVER }
        if (mcps.isEmpty()) {
            Text("No MCP servers connected yet.", color = Color(0xFF8B949E), fontSize = 14.sp)
        } else {
            mcps.forEach { c ->
                ConnectorRow(c, onToggleConnector, onRemoveConnector)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Plugins & skills ────────────────────────────────────────────────────────

private val PLUGIN_ICONS = listOf(
    "🛠️", "⚙️", "✨", "🧠", "🎯", "📊", "🎨", "🎵", "📅", "📧",
    "🧭", "🔍", "🗂️", "🔔", "💡", "🧩", "💬", "🌐", "🏠", "🤖",
)

@Composable
private fun PluginsTab(
    state: CompanionState,
    onCreatePlugin: (String, String, String) -> Unit,
    onToggleConnector: (String, Boolean) -> Unit,
    onRemoveConnector: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Create a plugin or skill the AI can run by name. You MUST pick an icon — a plugin without a distinct icon is not allowed.",
            color = Color.Gray,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        Surface(color = Color(0xFF161B22), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plugin / skill name", color = Color.Gray) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What it does", color = Color.Gray) },
                    colors = fieldColors(),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Pick an icon (required)", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PLUGIN_ICONS.forEach { candidate ->
                        Text(
                            candidate,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (icon == candidate) Color(0xFFFFD75E) else Color(0xFF1F2933))
                                .clickable { icon = candidate }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                saveError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (name.isBlank()) { saveError = "Give the plugin a name."; return@Button }
                        if (icon == null) { saveError = "Pick an icon — a plugin needs one."; return@Button }
                        saveError = null
                        onCreatePlugin(name.trim(), description.trim(), icon!!)
                        name = ""; description = ""; icon = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD75E)),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create plugin", color = Color(0xFF1A1400)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Your plugins & skills", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        val plugins = state.connectors.filter { it.kind == ConnectorKind.USER_PLUGIN }
        if (plugins.isEmpty()) {
            Text("None yet — create your first above.", color = Color(0xFF8B949E), fontSize = 14.sp)
        } else {
            plugins.forEach { c ->
                ConnectorRow(c, onToggleConnector, onRemoveConnector)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── shared row ──────────────────────────────────────────────────────────────

@Composable
private fun ConnectorRow(
    c: Connector,
    onToggleConnector: (String, Boolean) -> Unit,
    onRemoveConnector: (String) -> Unit,
) {
    Surface(color = Color(0xFF161B22), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(c.iconToken.ifBlank { "🧩" }, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(c.name, color = Color.White, fontSize = 15.sp)
                Text(
                    if (c.configured) c.description else c.description,
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (c.enabled) "On" else "Off",
                color = if (c.enabled) Color(0xFF4ADE80) else Color(0xFF8B949E),
                fontSize = 11.sp,
            )
            Switch(checked = c.enabled, onCheckedChange = { onToggleConnector(c.id, it) })
            IconButton(onClick = { onRemoveConnector(c.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFFF6B6B))
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color.White,
    focusedBorderColor = Color(0xFFFFD75E),
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
)
