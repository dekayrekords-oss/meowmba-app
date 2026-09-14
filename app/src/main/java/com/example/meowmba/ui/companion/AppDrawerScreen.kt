/* Hallmark · pre-emit critique: P4 H5 E4 S5 R4 V4
 * Native cartoon console; existing design.md colors/type and Material semantics.
 * Search stays visible; the rest scrolls as one adaptive surface, including favorites. */
package com.example.meowmba.ui.companion

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.CartoonTokens
import com.example.meowmba.ui.theme.FilterChip
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Shared icon cache: returning to the drawer does not decode every installed icon again. */
private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
private const val ICON_PX = 96

fun launchApp(context: Context, packageName: String) {
    runCatching {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let(context::startActivity)
    }
}

/**
 * One native drawer for installed apps, favorites, folders and the existing AI/task search.
 * The header alone is fixed: favorites and management controls share the app grid's scroll
 * so eight pins or large text cannot squeeze the app list out of a landscape window.
 * [installedApps] and [onLaunchApp] allow deterministic previews without probing a test host.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppDrawerScreen(
    onClose: () -> Unit,
    pins: List<String> = emptyList(),
    onTogglePin: (String) -> Unit = {},
    folders: List<DrawerFolder> = emptyList(),
    onCreateFolder: (name: String, firstPackage: String?) -> Unit = { _, _ -> },
    onAddToFolder: (folderId: String, packageName: String) -> Unit = { _, _ -> },
    onRemoveFromFolder: (folderId: String, packageName: String) -> Unit = { _, _ -> },
    onRenameFolder: (folderId: String, name: String) -> Unit = { _, _ -> },
    onDeleteFolder: (folderId: String) -> Unit = {},
    onSubmitQuery: (raw: String, matchCount: Int, singleLabel: String?) -> Unit = { _, _, _ -> },
    onSearchGoogle: (String) -> Unit = {},
    answer: String? = null,
    thinking: Boolean = false,
    listening: Boolean = false,
    listenPartial: String? = null,
    onMicClick: () -> Unit = {},
    voiceCommit: String? = null,
    onVoiceConsumed: () -> Unit = {},
    installedApps: List<DrawerApp>? = null,
    onLaunchApp: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(DrawerSort.NAME) }
    var typeFilter by rememberSaveable { mutableStateOf<DrawerAppType?>(null) }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var organizing by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var actionApp by remember { mutableStateOf<DrawerApp?>(null) }
    var actionFolder by remember { mutableStateOf<DrawerFolder?>(null) }
    var namingFolder by rememberSaveable { mutableStateOf(false) }
    var namingForPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var folderName by rememberSaveable { mutableStateOf("") }
    var apps by remember(installedApps) { mutableStateOf(installedApps ?: DrawerApps.snapshot(context)) }
    var loading by remember(installedApps) { mutableStateOf(installedApps == null) }
    var loadFailed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(installedApps, refresh) {
        if (installedApps != null) return@LaunchedEffect
        loading = true
        loadFailed = false
        try {
            apps = withContext(Dispatchers.IO) {
                DrawerApps.warm(context)
                DrawerApps.snapshot()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            loading = false
        }
    }

    val openFolder = folders.firstOrNull { it.id == openFolderId }
    val scoped = if (openFolder == null) apps else apps.filter { it.packageName in openFolder.packages }
    val visible = remember(query, scoped, sort, typeFilter) { DrawerCatalog.visible(scoped, query, sort, typeFilter) }
    val shownFolders = if (openFolder == null && typeFilter == null) DrawerCatalog.matchingFolders(folders, query) else emptyList()
    val favoriteApps = if (openFolder == null && query.isBlank() && typeFilter == null) {
        pins.distinct().take(8).mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    } else emptyList()
    val exact = visible.firstOrNull { it.label.equals(query.trim(), ignoreCase = true) }
    val hint = DrawerQuery.hint(query, visible.size, exact?.label ?: visible.singleOrNull()?.label)
    val presentTypes = remember(apps) { apps.map { it.type }.toSet().sortedBy { it.ordinal } }

    fun goBack() { if (openFolder != null) openFolderId = null else onClose() }
    BackHandler(enabled = !namingFolder && actionApp == null && actionFolder == null) { goBack() }
    fun openApp(app: DrawerApp) { onLaunchApp?.invoke(app.packageName) ?: launchApp(context, app.packageName) }
    fun beginFolder(pkg: String? = null) {
        namingForPackage = pkg
        renamingFolderId = null
        folderName = ""
        namingFolder = true
    }
    fun submit(raw: String = query) {
        val q = raw.trim()
        if (q.isBlank()) return
        query = q
        submitted = true
        focus.clearFocus()
        keyboard?.hide()
        val matches = DrawerCatalog.visible(scoped, q, sort, typeFilter)
        val match = matches.firstOrNull { it.label.equals(q, ignoreCase = true) }
        onSubmitQuery(q, matches.size, match?.label ?: matches.singleOrNull()?.label)
    }
    LaunchedEffect(listening, listenPartial) {
        if (listening && !listenPartial.isNullOrBlank()) { query = listenPartial; submitted = false }
    }
    LaunchedEffect(voiceCommit) {
        val spoken = voiceCommit?.trim().orEmpty()
        if (spoken.isNotBlank()) { submit(spoken); onVoiceConsumed() }
    }

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding()) {
        Surface(color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (openFolder != null) IconButton(onClick = { openFolderId = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to all apps")
                    } else DrawerOrbit(Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(openFolder?.name ?: "App drawer", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (openFolder != null) "Folder · ${scoped.size} apps" else "Your apps & shortcuts",
                            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.semantics { contentDescription = "Return to avatar" }) {
                        Text("Avatar", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; submitted = false },
                    modifier = Modifier.fillMaxWidth().testTag("drawer-search"),
                    placeholder = { Text(if (listening) "Listening…" else "Find apps, ask or search", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    trailingIcon = {
                        Row {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; submitted = false }) {
                                Icon(Icons.Default.Close, "Clear search")
                            }
                            IconButton(onClick = { submit() }, enabled = query.isNotBlank()) {
                                Icon(Icons.Default.Search, "Submit search")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outline,
                    ),
                )
            }
        }
        val gridMinimum = (96f * LocalDensity.current.fontScale.coerceIn(1f, 1.8f)).dp
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridMinimum),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("drawer-grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "search-actions", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = {
                            val q = query.trim()
                            if (q.isNotBlank()) {
                                submitted = true; focus.clearFocus(); keyboard?.hide()
                                onSearchGoogle(DrawerQuery.webQuery(q))
                            }
                        }, enabled = query.isNotBlank(), contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.semantics { contentDescription = "Search in Google app" }) {
                            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Google", maxLines = 1)
                        }
                        OutlinedButton(onClick = onMicClick, contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.semantics { contentDescription = if (listening) "Stop listening" else "Speak" }) {
                            Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(18.dp),
                                tint = if (listening) colors.secondary else colors.primary)
                            Spacer(Modifier.width(6.dp)); Text(if (listening) "Stop" else "Speak", maxLines = 1)
                        }
                    }
                    Text(hint ?: "Type to find an app. Enter to ask Meow or run a task.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                }
            }
            if (submitted && (thinking || !answer.isNullOrBlank())) item(key = "answer", span = { GridItemSpan(maxLineSpan) }) {
                Surface(shape = RoundedCornerShape(18.dp), color = colors.secondaryContainer) {
                    Column(Modifier.padding(14.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        Text("Response", fontWeight = FontWeight.Bold, color = colors.onSecondaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(if (thinking && answer.isNullOrBlank()) "Thinking…" else answer.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSecondaryContainer)
                    }
                }
            }
            item(key = "organize", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box {
                            OutlinedButton(onClick = { sortMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Text("Sort: ${sort.label}", maxLines = 1); Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                DrawerSort.entries.forEach { option -> DropdownMenuItem(
                                    text = { Text(option.label) }, onClick = { sort = option; sortMenu = false },
                                    trailingIcon = { if (sort == option) Icon(Icons.Default.Check, "Selected") },
                                ) }
                            }
                        }
                        if (presentTypes.size > 1) Box {
                            OutlinedButton(onClick = { typeMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Text(typeFilter?.label ?: "All types", maxLines = 1); Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                                DropdownMenuItem(text = { Text("All types") }, onClick = { typeFilter = null; typeMenu = false })
                                presentTypes.forEach { type -> DropdownMenuItem(text = { Text(type.label) },
                                    onClick = { typeFilter = type; typeMenu = false }) }
                            }
                        }
                        FilterChip(organizing, { organizing = !organizing }, { Text(if (organizing) "Done" else "Organize", maxLines = 1) },
                            leadingIcon = { Icon(if (organizing) Icons.Default.Check else Icons.Default.Edit, null, Modifier.size(18.dp)) })
                    }
                    if (organizing) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { beginFolder() }) { Icon(Icons.Default.CreateNewFolder, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("New folder") }
                            if (openFolder != null) TextButton(onClick = { actionFolder = openFolder }) { Text("Folder options") }
                        }
                    }
                }
            }
            if (favoriteApps.isNotEmpty()) {
                item(key = "favorites-heading", span = { GridItemSpan(maxLineSpan) }) {
                    DrawerSection("Favorites", "${favoriteApps.size}/8 pinned", Icons.Default.Star)
                }
                items(favoriteApps, key = { "pin:${it.packageName}" }) { app ->
                    DrawerAppTile(app, "pin:${app.packageName}", true, "", organizing,
                        onClick = { openApp(app) }, onOptions = { actionApp = app })
                }
            }
            if (shownFolders.isNotEmpty()) {
                item(key = "folders-heading", span = { GridItemSpan(maxLineSpan) }) { DrawerSection("Folders", "${shownFolders.size}", Icons.Default.Folder) }
                items(shownFolders, key = { "folder:${it.id}" }) { folder ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.tertiaryContainer)
                        .border(CartoonTokens.outline, colors.outlineVariant, RoundedCornerShape(18.dp))
                        .combinedClickable(role = Role.Button, onClick = { openFolderId = folder.id; query = ""; submitted = false },
                            onLongClickLabel = "Folder options", onLongClick = { actionFolder = folder })
                        .testTag("folder:${folder.id}").padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FolderTile(folder.packages)
                        Spacer(Modifier.height(8.dp))
                        Text(folder.name, color = colors.onTertiaryContainer, style = MaterialTheme.typography.labelLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text("${folder.packages.count { pkg -> apps.any { it.packageName == pkg } }} apps",
                            style = MaterialTheme.typography.labelSmall, color = colors.onTertiaryContainer)
                        if (organizing) TextButton(onClick = { actionFolder = folder },
                            modifier = Modifier.semantics { contentDescription = "Options for folder ${folder.name}" }) { Text("Options") }
                    }
                }
            }
            item(key = "apps-heading", span = { GridItemSpan(maxLineSpan) }) {
                DrawerSection(if (query.isNotBlank()) "Matching apps" else if (openFolder != null) "In this folder" else "All apps",
                    "${visible.size}", Icons.Default.Apps)
            }
            if (visible.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when { loading -> "Loading your apps…"; loadFailed -> "App list unavailable"; query.isNotBlank() || typeFilter != null -> "No matching apps"; openFolder != null -> "This folder is empty"; else -> "No launchable apps found" },
                            style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                        Text(when { loading -> "Reading the installed app list."; loadFailed -> "Your saved folders and favorites are still here. Try refreshing the list."; query.isNotBlank() -> "Try another name, ask Meow, or use Google above."; typeFilter != null -> "Choose All types to see more apps."; openFolder != null -> "Return to all apps, then use Organize to add apps to this folder."; else -> "Only apps with a launchable screen appear here." },
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        if (loadFailed) TextButton(onClick = { refresh++ }) { Text("Retry app list") }
                        if (query.isNotBlank() || typeFilter != null) TextButton(onClick = { query = ""; typeFilter = null; submitted = false }) { Text("Clear filters") }
                    }
                }
            }
            items(visible, key = { "app:${it.packageName}" }) { app ->
                DrawerAppTile(app, "app:${app.packageName}", app.packageName in pins, DrawerCatalog.subtitle(app, sort), organizing,
                    onClick = { openApp(app) }, onOptions = { actionApp = app })
            }
            item(key = "help", span = { GridItemSpan(maxLineSpan) }) {
                Text(if (organizing) "Options let you pin apps and manage folders. Apps stay installed when a folder is removed."
                    else "Hold an app for shortcuts, or choose Organize to show its options.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }

    actionApp?.let { app ->
        AlertDialog(onDismissRequest = { actionApp = null }, title = { Text(app.label) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                val pinned = app.packageName in pins
                DialogLine(if (pinned) "Unpin from favorites" else if (pins.distinct().size >= 8) "Favorites full (8)" else "Pin to favorites",
                    enabled = pinned || pins.distinct().size < 8) { onTogglePin(app.packageName); actionApp = null }
                folders.forEach { folder ->
                    val already = app.packageName in folder.packages
                    DialogLine(if (already) "Remove from ${folder.name}" else "Add to ${folder.name}") {
                        if (already) onRemoveFromFolder(folder.id, app.packageName) else onAddToFolder(folder.id, app.packageName)
                        actionApp = null
                    }
                }
                DialogLine("New folder with this app") { beginFolder(app.packageName); actionApp = null }
            } },
            confirmButton = { TextButton(onClick = { actionApp = null }) { Text("Close") } },
        )
    }
    actionFolder?.let { folder ->
        AlertDialog(onDismissRequest = { actionFolder = null }, title = { Text(folder.name) },
            text = { Column {
                Text("Manage this folder. Its apps stay installed.", style = MaterialTheme.typography.bodyMedium)
                DialogLine("Rename") { folderName = folder.name; namingForPackage = null; renamingFolderId = folder.id; namingFolder = true; actionFolder = null }
                DialogLine("Delete folder") { if (openFolderId == folder.id) openFolderId = null; onDeleteFolder(folder.id); actionFolder = null }
            } }, confirmButton = { TextButton(onClick = { actionFolder = null }) { Text("Close") } },
        )
    }
    if (namingFolder) AlertDialog(onDismissRequest = { namingFolder = false },
        title = { Text(if (renamingFolderId != null) "Rename folder" else "New folder") },
        text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it.take(24) }, singleLine = true,
            label = { Text("Folder name") }, supportingText = { Text("${folderName.length}/24") }, modifier = Modifier.testTag("folder-name")) },
        confirmButton = { TextButton(enabled = folderName.isNotBlank(), onClick = {
            val name = folderName.trim()
            if (name.isNotEmpty()) {
                val renameId = renamingFolderId
                if (renameId != null) onRenameFolder(renameId, name) else onCreateFolder(name, namingForPackage)
                namingFolder = false; renamingFolderId = null; namingForPackage = null
            }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { namingFolder = false }) { Text("Cancel") } },
    )
}

@Composable
private fun DrawerSection(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerAppTile(app: DrawerApp, tag: String, pinned: Boolean, extra: String, organizing: Boolean,
    onClick: () -> Unit, onOptions: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(if (pinned) colors.primaryContainer else colors.surfaceContainer)
        .border(CartoonTokens.outline, colors.outlineVariant, RoundedCornerShape(18.dp))
        .combinedClickable(role = Role.Button, onClick = onClick, onLongClickLabel = "App options", onLongClick = onOptions)
        .testTag(tag).padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            DrawerIcon(app.packageName, 52.dp, app.label)
            if (pinned) Icon(Icons.Default.Star, "Pinned", Modifier.align(Alignment.BottomEnd).size(18.dp)
                .background(colors.primaryContainer, CircleShape), tint = colors.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(app.label, style = MaterialTheme.typography.labelLarge, color = if (pinned) colors.onPrimaryContainer else colors.onSurface,
            maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (organizing) TextButton(onClick = onOptions, contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.semantics { contentDescription = "Options for ${app.label}" }) { Text("Options", maxLines = 1) }
    }
}

@Composable
private fun DialogLine(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp)) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

@Composable
private fun FolderTile(packages: List<String>) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(52.dp).background(colors.tertiaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (packages.isEmpty()) Icon(Icons.Default.Folder, null, Modifier.size(38.dp), tint = colors.tertiary)
        else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            packages.take(4).chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { DrawerIcon(it, 22.dp, "") }
            } }
        }
    }
}

/** Tiny static orbit mark, painted from theme roles; no animation clock or bitmap. */
@Composable
private fun DrawerOrbit(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        val u = size.minDimension
        drawCircle(colors.secondaryContainer, u * .3f, center)
        drawCircle(colors.secondary, u * .3f, center, style = Stroke(u * .055f))
        drawOval(colors.primary, Offset(u * .02f, u * .36f), Size(u * .96f, u * .28f), style = Stroke(u * .055f))
        drawCircle(colors.primary, u * .06f, Offset(u * .8f, u * .17f))
        drawCircle(colors.secondary, u * .04f, Offset(u * .12f, u * .78f))
    }
}

/** Async icon with a readable initial until decoded; never blocks drawer composition. */
@Composable
fun DrawerIcon(packageName: String, size: Dp, label: String) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(iconCache[packageName]) }
    if (bitmap == null) LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            iconCache[packageName] ?: runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
            }.getOrNull()?.also { iconCache[packageName] = it }
        }
    }
    val bmp = bitmap
    if (bmp != null) Image(bmp, contentDescription = label.takeIf { it.isNotEmpty() }, modifier = Modifier.size(size).clip(RoundedCornerShape(14.dp)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Text(label.firstOrNull()?.uppercase().orEmpty(), color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}
