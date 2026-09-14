package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.Surface

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.plugins.AppGallery
import com.example.meowmba.core.plugins.Connector
import com.example.meowmba.core.plugins.ConnectorKind
import com.example.meowmba.core.plugins.ConnectorManager

/**
 * Plugins & Connections menu (replaces the old "Tools" tile as the home for
 * things the AI can work with).
 *
 * Two surfaces, modeled on the Loona Deskmate app-connections list:
 *
 *  1. My Apps — a curated gallery of services the companion can attach to
 *     (Gmail, Calendar, Slack, Zoom, Drive, built-ins like Music/Weather).
 *     Each tile shows the service's category, name and what the AI does with
 *     it. Tapping a tile opens a detail sheet with the connection options:
 *     a place to paste an API key, a direct link to where to get that key,
 *     and an enable switch — so keys & services live in the app's own sheet,
 *     not a confusing generic form.
 *
 *  2. Plugins & Skills — user-authored plugins/skills with a MANDATORY
 *     distinct icon (blank icons are rejected before anything is saved).
 *
 * The raw "MCP server endpoint" form is gone from the main tabs: that concept
 * was confusing and its function (an API key + address) now lives per-app in
 * the detail sheet.
 */
@Composable
fun PluginsConnectorsScreen(
    state: CompanionState,
    onClose: () -> Unit,
    onSaveServiceKey: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onConnectApp: (String, String) -> Unit,
    onCreatePlugin: (String, String, String) -> Unit,
    onToggleConnector: (String, Boolean) -> Unit,
    onRemoveConnector: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(ConnectorTab.APPS) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                    "Connections",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabChip("My Apps", tab == ConnectorTab.APPS) { tab = ConnectorTab.APPS }
                TabChip("On this phone", tab == ConnectorTab.INSTALLED) { tab = ConnectorTab.INSTALLED }
                TabChip("Plugins & Skills", tab == ConnectorTab.PLUGINS) { tab = ConnectorTab.PLUGINS }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                ConnectorTab.APPS -> AppsTab(
                    state,
                    onSaveServiceKey,
                    onOpenUrl,
                    onConnectApp,
                    onToggleConnector,
                    onRemoveConnector,
                )
                ConnectorTab.INSTALLED -> InstalledAppsTab(state, onConnectApp)
                ConnectorTab.PLUGINS -> PluginsTab(state, onCreatePlugin, onToggleConnector, onRemoveConnector)
            }
        }
    }
}

private enum class ConnectorTab { APPS, INSTALLED, PLUGINS }

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun InstalledAppsTab(
    state: CompanionState,
    onConnectApp: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        Text(
            "Apps installed on this phone. A connection here lets Meow open an app only unless it separately offers MCP or Android AppFunctions.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (state.discoverableApps.isEmpty()) {
            Text("Looking for installed apps…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.discoverableApps, key = { it.packageName }) { app ->
                    InstalledAppTile(app, state.connectors.any { it.packageName == app.packageName }) {
                        onConnectApp(app.packageName, app.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppTile(
    app: ConnectorManager.DiscoveredApp,
    connected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        app.icon?.let { bitmap ->
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = app.label,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
            )
        } ?: Badge(app.label.take(1), 44, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(app.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (connected) "● Enabled" else app.capability.label,
            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp,
        )
    }
}

// ── My Apps (curated gallery) ───────────────────────────────────────────────

@Composable
private fun AppsTab(
    state: CompanionState,
    onSaveServiceKey: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onConnectApp: (String, String) -> Unit,
    onToggleConnector: (String, Boolean) -> Unit,
    onRemoveConnector: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<AppGallery.Entry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Work with the apps you already use. Tap a service to connect or manage it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))

        // Group the catalog by category, Loona-style.
        AppGallery.all.groupBy { it.category }.forEach { (category, entries) ->
            Text(
                category.uppercase(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 172.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries) { entry ->
                    GalleryTile(
                        entry = entry,
                        connected = isConnected(state, entry),
                        onClick = { selected = entry },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    selected?.let { entry ->
        AppDetailSheet(
            state = state,
            entry = entry,
            onDismiss = { selected = null },
            onSaveServiceKey = onSaveServiceKey,
            onOpenUrl = onOpenUrl,
            onConnectApp = onConnectApp,
            onToggleConnector = onToggleConnector,
        )
    }
}

private fun isConnected(state: CompanionState, entry: AppGallery.Entry): Boolean {
    val cid = entry.connectorId ?: return false
    return state.connectors.any { it.packageName == cid && it.configured }
}

/** Always-true for built-ins; the sheet decides per-integration. */
private fun connectedFor(entry: AppGallery.Entry, state: CompanionState): Connector? {
    val cid = entry.connectorId ?: return null
    return state.connectors.firstOrNull { it.packageName == cid }
}

@Composable
private fun Badge(badge: String, size: Int, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (badge.length == 1 && badge[0] == '♪') {
            Text(badge, color = fg, fontSize = 22.sp)
        } else if (badge.length == 1) {
            Text(badge, color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(badge, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GalleryTile(entry: AppGallery.Entry, connected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .clickable(onClick = onClick),
    ) {
        Badge(
            badge = entry.badge,
            size = 44,
            bg = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            fg = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            entry.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            when {
                connected -> "● Connected"
                entry.integration == AppGallery.Integration.BUILT_IN -> "Active"
                entry.integration == AppGallery.Integration.API_KEY -> "Add key"
                else -> "Soon"
            },
            color = when {
                connected || entry.integration == AppGallery.Integration.BUILT_IN -> MaterialTheme.colorScheme.primary
                entry.integration == AppGallery.Integration.API_KEY -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 9.sp,
        )
    }
}

// ── Per-app detail sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailSheet(
    state: CompanionState,
    entry: AppGallery.Entry,
    onDismiss: () -> Unit,
    onSaveServiceKey: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onConnectApp: (String, String) -> Unit,
    onToggleConnector: (String, Boolean) -> Unit,
) {
    var showKey by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var savedMsg by remember { mutableStateOf<String?>(null) }
    val connector = connectedFor(entry, state)
    val installed = entry.connectorId != null // package-backed connector

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(entry.badge, 52, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.category, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(entry.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(entry.capability, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)

            Spacer(Modifier.height(16.dp))

            when (entry.integration) {
                AppGallery.Integration.BUILT_IN -> BuiltInSection(
                    entry = entry,
                    connector = connector,
                    state = state,
                    onConnectApp = onConnectApp,
                    onToggleConnector = onToggleConnector,
                )

                AppGallery.Integration.API_KEY -> ApiKeySection(
                    entry = entry,
                    key = key,
                    onKeyChange = { key = it },
                    showKey = showKey,
                    onToggleShow = { showKey = !showKey },
                    savedMsg = savedMsg,
                    onSave = {
                        val trimmed = key.trim()
                        if (trimmed.isEmpty()) {
                            savedMsg = "Paste the key from ${entry.name} first."
                            return@ApiKeySection
                        }
                        onSaveServiceKey(entry.storageKey ?: entry.id, trimmed)
                        key = ""
                        showKey = false
                        savedMsg = "Key saved — ${entry.name} is connected."
                    },
                    onGetKey = { onOpenUrl(entry.keyLink ?: "") },
                )

                AppGallery.Integration.COMING_SOON -> ComingSoonSection(entry)
            }
        }
    }
}

@Composable
private fun BuiltInSection(
    entry: AppGallery.Entry,
    connector: Connector?,
    state: CompanionState,
    onConnectApp: (String, String) -> Unit,
    onToggleConnector: (String, Boolean) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Active — no setup needed.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Meow can already do this with the tools built into the app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }

    if (entry.connectorId != null) {
        Spacer(Modifier.height(14.dp))
        if (connector == null) {
            Button(
                onClick = { onConnectApp(entry.connectorId!!, entry.name) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enable this app", color = MaterialTheme.colorScheme.onPrimary) }
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                        Text(
                            if (connector.enabled) "Allows Meow to open and use this app" else "Paused",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = connector.enabled, onCheckedChange = { onToggleConnector(connector.id, it) })
                }
            }
        }
    }
}

@Composable
private fun ApiKeySection(
    entry: AppGallery.Entry,
    key: String,
    onKeyChange: (String) -> Unit,
    showKey: Boolean,
    onToggleShow: () -> Unit,
    savedMsg: String?,
    onSave: () -> Unit,
    onGetKey: () -> Unit,
) {
    Text(
        "Connect ${entry.name} by adding an access token or API key. The key is stored securely on this phone and only used to talk to ${entry.name}.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(12.dp))

    TextField(
        value = if (showKey) key else key.replace(Regex("."), "•"),
        onValueChange = onKeyChange,
        label = { Text("${entry.name} API key", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onToggleShow) {
                Icon(
                    imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showKey) "Hide key" else "Show key",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.onSurface,
        ),
    )

    if (entry.keyLink != null) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have a key?", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(
                " Get one here",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onGetKey)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }

    savedMsg?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = if (it.startsWith("Key saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontSize = 13.sp)
    }

    Spacer(Modifier.height(14.dp))
    Button(
        onClick = onSave,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        enabled = key.isNotBlank(),
    ) { Text("Save key", color = MaterialTheme.colorScheme.onPrimary) }
}

@Composable
private fun ComingSoonSection(entry: AppGallery.Entry) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Coming soon",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "A driver for ${entry.name} isn't wired up yet, but we'll add it when it arrives.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plugin / skill name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What it does", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = fieldColors(),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Pick an icon (required)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), fontSize = 12.sp)
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
                                .background(if (icon == candidate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = candidate }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                saveError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create plugin", color = MaterialTheme.colorScheme.onPrimary) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Your plugins & skills", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        val plugins = state.connectors.filter { it.kind == ConnectorKind.USER_PLUGIN }
        if (plugins.isEmpty()) {
            Text("None yet — create your first above.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(c.iconToken.ifBlank { "🧩" }, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(c.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Text(
                    c.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (c.enabled) "On" else "Off",
                color = if (c.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Switch(checked = c.enabled, onCheckedChange = { onToggleConnector(c.id, it) })
            IconButton(onClick = { onRemoveConnector(c.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
)
