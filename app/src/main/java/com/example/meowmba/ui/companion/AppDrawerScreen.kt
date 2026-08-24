package com.example.meowmba.ui.companion

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * App drawer (swipe up from the face): solid black, opens instantly (the app
 * list and icons load in the background), with a Favorites row at the bottom
 * holding up to 8 pinned apps. Long-press an app to pin it or drop it in a
 * folder. Sort by name, date added, size, or type.
 *
 * Search is a full AI bar. Typing still filters apps. Enter (or the search
 * key) then asks Meow, runs a phone task, searches Google, or opens the
 * matching app — whichever the line is.
 */

/** Shared icon cache so reopening the drawer costs nothing. */
private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
private const val ICON_PX = 96

fun launchApp(context: Context, packageName: String) {
    runCatching {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) context.startActivity(launch)
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(DrawerSort.NAME) }
    var typeFilter by remember { mutableStateOf<DrawerAppType?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var actionApp by remember { mutableStateOf<DrawerApp?>(null) }
    var actionFolder by remember { mutableStateOf<DrawerFolder?>(null) }
    var namingFolder by remember { mutableStateOf(false) }
    var namingForPackage by remember { mutableStateOf<String?>(null) }
    var renamingFolderId by remember { mutableStateOf<String?>(null) }
    var folderName by remember { mutableStateOf("") }

    // Disk/memory cache first so swipe-up is never a blank grid. Refresh
    // labels in the background; only re-stat APKs for new packages.
    var apps by remember { mutableStateOf(DrawerApps.snapshot(context)) }
    LaunchedEffect(Unit) {
        if (apps.isEmpty()) apps = DrawerApps.snapshot(context)
        val next = withContext(Dispatchers.IO) {
            DrawerApps.warm(context)
            DrawerApps.snapshot()
        }
        if (next.isNotEmpty()) apps = next
    }

    val openFolder = folders.firstOrNull { it.id == openFolderId }
    val scoped = if (openFolder == null) apps
    else apps.filter { it.packageName in openFolder.packages }
    val visible = remember(query, scoped, sort, typeFilter) {
        DrawerCatalog.visible(scoped, query, sort, typeFilter)
    }
    val shownFolders = if (openFolder == null) {
        DrawerCatalog.matchingFolders(folders, query)
    } else emptyList()
    val exact = visible.firstOrNull { it.label.equals(query.trim(), ignoreCase = true) }
    val singleLabel = exact?.label ?: visible.singleOrNull()?.label
    val hint = DrawerQuery.hint(query, visible.size, singleLabel)
    val kind = if (query.isBlank()) null else DrawerQuery.classify(query)
    val presentTypes = remember(apps) { apps.map { it.type }.toSet().sortedBy { it.ordinal } }

    fun submit(raw: String = query) {
        val q = raw.trim()
        if (q.isBlank()) return
        query = q
        submitted = true
        focus.clearFocus()
        keyboard?.hide()
        val matches = DrawerCatalog.visible(scoped, q, sort, typeFilter)
        val exactMatch = matches.firstOrNull { it.label.equals(q, ignoreCase = true) }
        onSubmitQuery(q, matches.size, exactMatch?.label ?: matches.singleOrNull()?.label)
    }

    LaunchedEffect(listening, listenPartial) {
        if (listening && !listenPartial.isNullOrBlank()) query = listenPartial
    }
    LaunchedEffect(voiceCommit) {
        val spoken = voiceCommit?.trim().orEmpty()
        if (spoken.isBlank()) return@LaunchedEffect
        submit(spoken)
        onVoiceConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.isBlank()) submitted = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = {
                Text(
                    if (listening) "Listening…" else "Ask, search Google, or find an app",
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            trailingIcon = {
                Row {
                    IconButton(onClick = onMicClick) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = if (listening) "Stop listening" else "Speak",
                            tint = if (listening) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.85f),
                        )
                    }
                    IconButton(onClick = { submit() }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Go",
                            tint = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.White.copy(alpha = 0.55f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
            ),
        )
        if (hint != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    hint,
                    color = Color(0xFFFFD75E),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { submit() },
                )
                if (kind != DrawerQuery.Kind.WEB_SEARCH && query.isNotBlank()) {
                    Text(
                        "Google",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            val q = query.trim()
                            if (q.isNotBlank()) {
                                submitted = true
                                focus.clearFocus()
                                keyboard?.hide()
                                onSearchGoogle(DrawerQuery.webQuery(q))
                            }
                        },
                    )
                }
            }
        }
        if (submitted && (thinking || !answer.isNullOrBlank())) {
            Surface(
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Meow", color = Color(0xFFFFD75E), fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (thinking && answer.isNullOrBlank()) "Thinking…" else answer.orEmpty(),
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (openFolder != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { openFolderId = null },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    openFolder.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Rename",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { actionFolder = openFolder },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawerSort.entries.forEach { option ->
                DrawerChip(
                    label = option.label,
                    selected = sort == option,
                    onClick = { sort = option },
                )
            }
            DrawerChip(
                label = "+ Folder",
                selected = false,
                onClick = {
                    namingForPackage = null
                    renamingFolderId = null
                    folderName = ""
                    namingFolder = true
                },
            )
        }
        if (presentTypes.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DrawerChip(
                    label = "All",
                    selected = typeFilter == null,
                    onClick = { typeFilter = null },
                )
                presentTypes.forEach { type ->
                    DrawerChip(
                        label = type.label,
                        selected = typeFilter == type,
                        onClick = {
                            typeFilter = if (typeFilter == type) null else type
                        },
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(shownFolders, key = { "f:${it.id}" }) { folder ->
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { openFolderId = folder.id },
                            onLongClick = { actionFolder = folder },
                        )
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FolderTile(packages = folder.packages)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        folder.name,
                        color = Color(0xFFFFD75E),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(visible, key = { it.packageName }) { app ->
                val pinned = app.packageName in pins
                val extra = DrawerCatalog.subtitle(app, sort)
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { launchApp(context, app.packageName) },
                            onLongClick = { actionApp = app },
                        )
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DrawerIcon(packageName = app.packageName, size = 48.dp, label = app.label)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (pinned) "★ " else "") + app.label,
                        color = if (pinned) Color(0xFFFFD75E) else Color.White,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (extra.isNotBlank()) {
                        Text(
                            extra,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Favorites: up to 8 pinned apps, drawer-only.
        if (pins.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101010))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                pins.take(8).forEach { pkg ->
                    Column(
                        modifier = Modifier.combinedClickable(
                            onClick = { launchApp(context, pkg) },
                            onLongClick = { onTogglePin(pkg) },
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DrawerIcon(packageName = pkg, size = 40.dp, label = "favorite")
                    }
                }
            }
        }
        Text(
            when {
                openFolder != null -> "Long-press an app to remove it from this folder"
                pins.isEmpty() -> "Long-press an app to pin it or add it to a folder"
                else -> "Swipe left to go back"
            },
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(bottom = 8.dp),
        )
    }

    val targetApp = actionApp
    if (targetApp != null) {
        AlertDialog(
            onDismissRequest = { actionApp = null },
            containerColor = Color(0xFF1A1A1A),
            title = { Text(targetApp.label, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DialogLine(
                        if (targetApp.packageName in pins) "Unpin from favorites" else "Pin to favorites",
                    ) {
                        onTogglePin(targetApp.packageName)
                        actionApp = null
                    }
                    folders.forEach { folder ->
                        val already = targetApp.packageName in folder.packages
                        DialogLine(
                            if (already) "In ${folder.name}" else "Add to ${folder.name}",
                        ) {
                            if (!already) onAddToFolder(folder.id, targetApp.packageName)
                            actionApp = null
                        }
                    }
                    DialogLine("New folder with this app") {
                        namingForPackage = targetApp.packageName
                        renamingFolderId = null
                        folderName = ""
                        namingFolder = true
                        actionApp = null
                    }
                    if (openFolder != null) {
                        DialogLine("Remove from ${openFolder.name}") {
                            onRemoveFromFolder(openFolder.id, targetApp.packageName)
                            actionApp = null
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionApp = null }) {
                    Text("Close", color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }

    val targetFolder = actionFolder
    if (targetFolder != null) {
        AlertDialog(
            onDismissRequest = { actionFolder = null },
            containerColor = Color(0xFF1A1A1A),
            title = { Text(targetFolder.name, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DialogLine("Rename") {
                        folderName = targetFolder.name
                        namingForPackage = null
                        renamingFolderId = targetFolder.id
                        namingFolder = true
                        actionFolder = null
                    }
                    DialogLine("Delete folder") {
                        if (openFolderId == targetFolder.id) openFolderId = null
                        onDeleteFolder(targetFolder.id)
                        actionFolder = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionFolder = null }) {
                    Text("Close", color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }

    if (namingFolder) {
        val renameId = renamingFolderId
        AlertDialog(
            onDismissRequest = { namingFolder = false },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Text(if (renameId != null) "Rename folder" else "New folder", color = Color.White)
            },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it.take(24) },
                    singleLine = true,
                    placeholder = { Text("Folder name", color = Color.White.copy(alpha = 0.45f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFFFFD75E),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = folderName.trim()
                        if (name.isBlank()) return@TextButton
                        if (renameId != null) onRenameFolder(renameId, name)
                        else onCreateFolder(name, namingForPackage)
                        namingFolder = false
                        renamingFolderId = null
                        namingForPackage = null
                    },
                ) { Text("Save", color = Color(0xFFFFD75E)) }
            },
            dismissButton = {
                TextButton(onClick = { namingFolder = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }
}

@Composable
private fun DrawerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color(0xFF1A1400) else Color.White.copy(alpha = 0.8f),
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFFFFD75E) else Color(0xFF222222))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun DialogLine(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun FolderTile(packages: List<String>) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A2416)),
        contentAlignment = Alignment.Center,
    ) {
        if (packages.isEmpty()) {
            Icon(
                Icons.Default.Folder,
                contentDescription = "Folder",
                tint = Color(0xFFFFD75E),
                modifier = Modifier.size(26.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                packages.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { pkg ->
                            DrawerIcon(packageName = pkg, size = 20.dp, label = "")
                        }
                    }
                }
            }
        }
    }
}

/** Async icon with a neutral placeholder until decoded (keeps the grid fluid). */
@Composable
fun DrawerIcon(
    packageName: String,
    size: androidx.compose.ui.unit.Dp,
    label: String,
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(iconCache[packageName]) }
    if (bitmap == null) {
        LaunchedEffect(packageName) {
            bitmap = withContext(Dispatchers.IO) {
                iconCache.getOrPut(packageName) {
                    runCatching {
                        context.packageManager.getApplicationIcon(packageName)
                            .toBitmap(ICON_PX, ICON_PX)
                            .asImageBitmap()
                    }.getOrNull() ?: return@withContext null
                }
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = label,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF222222))
        )
    }
}
