package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.OutlinedCard
import com.example.meowmba.ui.theme.Surface

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.example.meowmba.ui.theme.AlertDialog
import com.example.meowmba.ui.theme.DropdownMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meowmba.core.files.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetBrowserScreen(askAi: suspend (String) -> String, onProjects: () -> Unit, onClose: () -> Unit,
    initialPath: String = "") {
    val application = LocalContext.current.applicationContext as Application
    val vm: AssetBrowserViewModel = viewModel(factory = remember(application) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AssetBrowserViewModel(application) as T
    } })
    val state by vm.state.collectAsState()
    var category by rememberSaveable { mutableStateOf("Home") }
    var path by rememberSaveable(initialPath) { mutableStateOf(initialPath) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Newest") }
    var grid by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var edit by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }
    var confirmTrash by remember { mutableStateOf<List<AssetEntry>?>(null) }
    var confirmPurge by remember { mutableStateOf<TrashEntry?>(null) }
    var aiMode by remember { mutableStateOf<Boolean?>(null) }
    var exportItem by remember { mutableStateOf<AssetEntry?>(null) }
    val chosen = state.entries.filter { it.path in selected }
    val importFolder = if (path.isNotBlank()) path else when (category) { "Photos" -> "photos"; "Videos" -> "videos"; "Audio" -> "audio"; "Documents" -> "documents"; else -> "imports" }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.importFiles(uris, importFolder) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::linkFolder) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val item = exportItem; if (uri != null && item != null) vm.export(item, uri); exportItem = null
    }
    fun goHome() { path = ""; category = "Home"; selected = emptySet(); vm.closeLinked(); vm.clearResults() }
    fun back() {
        when { state.linkedUri != null -> vm.closeLinked(); selected.isNotEmpty() -> selected = emptySet()
            path.isNotBlank() -> path = path.substringBeforeLast('/', "")
            category != "Home" || state.aiResults != null -> goHome()
            else -> onClose() }
    }
    BackHandler { back() }
    LaunchedEffect(Unit) { vm.refresh() }
    val entries = remember(state.entries, state.aiResults, query, category, path, sort) {
        val source = state.aiResults ?: state.entries.filter { entry ->
            when {
                query.isNotBlank() -> !entry.folder
                path.isNotBlank() -> entry.path.substringBeforeLast('/', "") == path
                category == "Folders" -> '/' !in entry.path
                category == "Projects" -> entry.path.startsWith("projects/") && entry.path.count { it == '/' } == 1
                category in listOf("Home", "Recents") -> !entry.folder
                else -> entry.category == category && !entry.folder
            }
        }
        val terms = if (state.aiResults == null) query.lowercase().split(' ').filter { it.isNotBlank() } else emptyList()
        val filtered = source.filter { item -> terms.all { it in "${item.path} ${item.category}".lowercase() } }
        filtered.sortedWith(compareBy<AssetEntry> { !it.folder }.thenComparator { a, b ->
            when (sort) { "Name" -> a.name.compareTo(b.name, true); "Largest" -> b.bytes.compareTo(a.bytes); else -> b.modified.compareTo(a.modified) }
        }).let { if (category == "Home" && query.isBlank() && state.aiResults == null) it.take(12) else it }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) { Text("Files", style = MaterialTheme.typography.headlineSmall); Text("AI file manager", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, "Switch file layout") }
                Box {
                    IconButton(onClick = { addMenu = true }, enabled = !state.busy) { Icon(Icons.Default.Add, "Add files or folder") }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text("Import files") }, onClick = { addMenu = false; importPicker.launch(arrayOf("*/*")) })
                        DropdownMenuItem(text = { Text("New folder") }, onClick = { addMenu = false; edit = "folder"; editValue = "" })
                        DropdownMenuItem(text = { Text("Connect phone folder") }, onClick = { addMenu = false; folderPicker.launch(null) })
                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { addMenu = false; vm.refresh() })
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close files") }
            }
            OutlinedTextField(query, { query = it; vm.clearResults() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true, placeholder = { Text("Search your files", maxLines = 1) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = ""; vm.clearResults() }) { Icon(Icons.Default.Clear, "Clear search") } }, shape = RoundedCornerShape(20.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.organizeScope == FilesOrganizeScope.LOCAL,
                    onClick = { vm.setOrganizeScope(FilesOrganizeScope.LOCAL) },
                    enabled = !state.busy,
                    label = { Text("Local files") },
                    leadingIcon = if (state.organizeScope == FilesOrganizeScope.LOCAL) {{ Icon(Icons.Default.Folder, null, Modifier.size(18.dp)) }} else null,
                )
                FilterChip(
                    selected = state.organizeScope == FilesOrganizeScope.PHONE,
                    onClick = { vm.setOrganizeScope(FilesOrganizeScope.PHONE) },
                    enabled = !state.busy,
                    label = { Text("All files on phone") },
                    leadingIcon = if (state.organizeScope == FilesOrganizeScope.PHONE) {{ Icon(Icons.Default.PhoneAndroid, null, Modifier.size(18.dp)) }} else null,
                )
            }
            Text(
                if (state.organizeScope == FilesOrganizeScope.LOCAL)
                    "Clean up & AI organize apply to your Meow library (app-private)."
                else
                    "Clean up scans phone media via MediaStore. Connect folders for read-only browsing; import to organize with AI.",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { aiMode = false }, enabled = !state.busy && query.isNotBlank() && state.organizeScope == FilesOrganizeScope.LOCAL, label = { Text("AI search") }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { aiMode = true }, enabled = !state.busy && state.organizeScope == FilesOrganizeScope.LOCAL, label = { Text("AI organize") })
                AssistChip(onClick = vm::cleanup, enabled = !state.busy, label = { Text(if (state.organizeScope == FilesOrganizeScope.LOCAL) "Clean up library" else "Clean up phone") }, leadingIcon = { Icon(Icons.Default.CleaningServices, null, Modifier.size(18.dp)) })
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.truncated) Text("Showing a bounded index: at most 5,000 entries / 11 folder levels. Some assets are not indexed.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (category == "Home" && path.isBlank() && query.isBlank() && state.linkedUri == null && state.aiResults == null) {
                    item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text("Meow library", style = MaterialTheme.typography.titleMedium)
                            Text("${state.entries.count { !it.folder }} files · ${assetSize(state.entries.sumOf { it.bytes })}", style = MaterialTheme.typography.bodyMedium)
                            Text("Projects, media and imports. Private storage — export important work before uninstalling.", style = MaterialTheme.typography.bodySmall)
                        } }
                    }
                    items(listOf(listOf("Photos", "Videos", "Audio"), listOf("Documents", "Projects", "Archives"), listOf("Folders", "Recents", "Trash"))) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { name ->
                            OutlinedCard(onClick = { category = name; selected = emptySet() }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(assetIcon(name), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(name, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } }
                    }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.organizeScope == FilesOrganizeScope.PHONE) "Phone files & folders" else "Phone folders",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (state.organizeScope == FilesOrganizeScope.PHONE) {
                                Text(
                                    "Browse connected folders or run Clean up phone for MediaStore suggestions.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { folderPicker.launch(null) }, enabled = !state.busy) { Text("Connect folder") }
                    } }
                    items(state.linkedFolders, key = { "linked:$it" }) { link ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.browseLinked(Uri.parse(link)) }, modifier = Modifier.weight(1f), enabled = !state.busy) { Text(Uri.decode(link.substringAfterLast('/')), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            IconButton(onClick = { vm.unlinkFolder(link) }, enabled = !state.busy) { Icon(Icons.Default.LinkOff, "Disconnect folder") }
                        }
                    }
                }
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.linkedUri != null) "Connected phone folder" else if (path.isNotBlank()) "Library / $path" else if (state.aiResults != null) "AI results" else if (category == "Home") "Recent files" else category,
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (category == "Projects") TextButton(onClick = onProjects) { Text("Project chats") }
                    TextButton(onClick = { sort = when (sort) { "Newest" -> "Name"; "Name" -> "Largest"; else -> "Newest" } }) { Text(sort) }
                } }
                if (state.linkedUri != null) {
                    item { TextButton(onClick = { vm.closeLinked() }) { Text("Back to library") } }
                    items(state.linkedItems, key = { it.uri.toString() }) { item ->
                        ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text(if (item.folder) "Folder" else assetSize(item.bytes)) },
                            leadingContent = { Icon(if (item.folder) Icons.Default.Folder else assetIcon(AssetLibrary.category(item.name)), null) },
                            trailingContent = { TextButton(enabled = !state.busy, onClick = { if (item.folder) vm.browseLinked(item.uri) else vm.importFiles(listOf(item.uri), importFolder) }) { Text(if (item.folder) "Open" else "Import") } })
                    }
                } else if (category == "Trash") {
                    item { Text("Files stay here until you restore or permanently delete them. Trash still uses storage.", style = MaterialTheme.typography.bodySmall) }
                    if (state.trash.isEmpty()) item { Text("Trash is empty.") }
                    items(state.trash, key = { it.id }) { item ->
                        Card { Column(Modifier.padding(12.dp)) {
                            Text(item.original, style = MaterialTheme.typography.bodyMedium)
                            Text(assetSize(item.bytes), style = MaterialTheme.typography.bodySmall)
                            Row { TextButton(onClick = { vm.restore(item.id) }, enabled = !state.busy) { Text("Restore") }
                                TextButton(onClick = { confirmPurge = item }, enabled = !state.busy) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) } }
                        } }
                    }
                } else {
                    if (entries.isEmpty()) item { Text("No files here yet. Import files, create a folder, or generate something with Meow.", style = MaterialTheme.typography.bodyMedium) }
                    if (grid) {
                        items(entries.chunked(2), key = { "grid:${it.first().path}" }) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { item ->
                                AssetCard(item, vm, selected, { selected = if (item.path in selected) selected - item.path else selected + item.path },
                                    { if (item.folder) { path = item.path; category = "Folders"; query = ""; vm.clearResults() } else vm.open(item) }, Modifier.weight(1f))
                            }; if (row.size == 1) Spacer(Modifier.weight(1f)) }
                        }
                    } else items(entries, key = { it.path }) { item ->
                        ListItem(modifier = Modifier.clickable { if (item.folder) { path = item.path; category = "Folders"; query = ""; vm.clearResults() } else vm.open(item) },
                            headlineContent = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (item.folder) "Folder" else "${assetSize(item.bytes)} · ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.modified))}\n${item.path.substringBeforeLast('/', "Library")}", maxLines = 2) },
                            leadingContent = { AssetThumbnail(item, vm, Modifier.size(48.dp)) },
                            trailingContent = { if (!item.folder) Checkbox(item.path in selected, { selected = if (item.path in selected) selected - item.path else selected + item.path }, enabled = !state.busy) })
                    }
                }
                item { Spacer(Modifier.height(12.dp)); Text("Only library assets are visible to AI. Connected phone folders remain read-only until files are imported.", style = MaterialTheme.typography.labelSmall) }
            }
            if (chosen.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = emptySet() }) { Text("${chosen.size} selected · Clear") }
                    TextButton(onClick = { edit = "move"; editValue = if (chosen.size == 1) chosen.first().path else "documents" }, enabled = !state.busy) { Text("Move / rename") }
                    TextButton(onClick = { confirmTrash = chosen }, enabled = !state.busy) { Text("Trash") }
                    if (chosen.size == 1) {
                        TextButton(onClick = { vm.share(chosen.first()) }, enabled = !state.busy) { Text("Share") }
                        TextButton(onClick = { exportItem = chosen.first(); exportPicker.launch(chosen.first().name) }, enabled = !state.busy) { Text("Export") }
                    }
                }
            }
        }
    }
    edit?.let { mode -> AlertDialog(onDismissRequest = { edit = null }, title = { Text(if (mode == "folder") "New folder" else "Move / rename") },
        text = { Column { Text(if (mode == "folder") "Create inside ${path.ifBlank { "Library" }}." else if (chosen.size == 1) "Enter a relative destination including filename. Existing files are never overwritten." else "Enter a relative destination folder for ${chosen.size} files. Linked references may need updating.")
            OutlinedTextField(editValue, { editValue = it }, singleLine = true, label = { Text(if (mode == "folder") "Folder name" else "Destination") }) } },
        confirmButton = { TextButton(enabled = editValue.isNotBlank() && !state.busy, onClick = {
            if (mode == "folder") vm.createFolder(listOf(path, editValue).filter { it.isNotBlank() }.joinToString("/"))
            else vm.moveFiles(chosen.map { AssetMove(it, if (chosen.size == 1) editValue else "${editValue.trimEnd('/')}/${it.name}") })
            edit = null; selected = emptySet()
        }) { Text(if (mode == "folder") "Create" else "Move") } }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } }) }
    aiMode?.let { organize -> AlertDialog(onDismissRequest = { aiMode = null }, title = { Text(if (organize) "Review AI organization" else "Search with AI") },
        text = { Text("Uses your configured AI provider, which may run in the cloud. Sends only names, paths, types and sizes for up to 200 ${if (chosen.isEmpty()) "recent library files" else "selected files"}. No file contents are sent. ${if (organize) "You will review every proposed move. Nothing is changed automatically." else "Results are based on metadata, not visual recognition."}") },
        confirmButton = { TextButton(onClick = { aiMode = null; vm.askAi(query.ifBlank { "Organize loose files into clear folders by type and topic" }, organize, chosen, askAi) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { aiMode = null }) { Text("Cancel") } }) }
    if (state.phoneCleanup.isNotEmpty() || state.phoneCleanupNotes.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::dismissPlan,
            title = { Text("Phone cleanup suggestions") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        Text(
                            "Suggestions only — media deletes use Android’s system confirm sheet. Unused apps open the uninstall prompt.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    items(state.phoneCleanupNotes) { note ->
                        Text(note, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.phoneCleanup.isEmpty()) {
                        item { Text("No obvious phone media candidates right now.", Modifier.padding(top = 8.dp)) }
                    }
                    items(state.phoneCleanup, key = { "${it.label}:${it.bytes}:${it.detail}" }) { row ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(row.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (if (row.bytes > 0) "${assetSize(row.bytes)} · " else "") + row.detail,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissPlan) { Text("Got it") } },
            dismissButton = {
                if (state.organizeScope == FilesOrganizeScope.PHONE) {
                    TextButton(onClick = { folderPicker.launch(null); vm.dismissPlan() }) { Text("Connect folder") }
                }
            },
        )
    }
    state.cleanup?.let { candidates ->
        var picked by remember(candidates) { mutableStateOf(setOf<String>()) }
        AlertDialog(onDismissRequest = vm::dismissPlan, title = { Text("Review cleanup") }, text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { Text("Nothing is selected automatically. Moving to Trash is recoverable and does not free space yet.") }
                if (candidates.isEmpty()) item { Text("No duplicate, empty or large-file candidates found.") }
                items(candidates, key = { it.asset.path }) { candidate -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(candidate.asset.path in picked, { picked = if (candidate.asset.path in picked) picked - candidate.asset.path else picked + candidate.asset.path })
                    Column { Text(candidate.asset.path, style = MaterialTheme.typography.bodyMedium); Text("${assetSize(candidate.asset.bytes)} · ${candidate.reason}", style = MaterialTheme.typography.bodySmall) }
                } }
            }
        }, confirmButton = { TextButton(enabled = picked.isNotEmpty() && !state.busy, onClick = { vm.trashFiles(candidates.filter { it.asset.path in picked }.map { it.asset }) }) { Text("Move ${picked.size} to Trash") } },
            dismissButton = { TextButton(onClick = vm::dismissPlan) { Text("Close") } })
    }
    if (state.moves.isNotEmpty()) AlertDialog(onDismissRequest = vm::dismissPlan, title = { Text("Review ${state.moves.size} moves") }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) { items(state.moves) { Text("${it.source.path}\n→ ${it.destination}", Modifier.padding(vertical = 8.dp)) } }
    }, confirmButton = { TextButton(onClick = { vm.moveFiles(state.moves); selected = emptySet() }, enabled = !state.busy) { Text("Apply moves") } }, dismissButton = { TextButton(onClick = vm::dismissPlan) { Text("Cancel") } })
    confirmTrash?.let { files -> AlertDialog(onDismissRequest = { confirmTrash = null }, title = { Text("Move ${files.size} files to Trash?") },
        text = { LazyColumn(Modifier.heightIn(max = 360.dp)) { item { Text("Files can be restored from Trash. Space is freed only after permanent deletion.") }; items(files) { Text(it.path, Modifier.padding(vertical = 4.dp)) } } },
        confirmButton = { TextButton(onClick = { vm.trashFiles(files); confirmTrash = null; selected = emptySet() }, enabled = !state.busy) { Text("Move to Trash") } }, dismissButton = { TextButton(onClick = { confirmTrash = null }) { Text("Cancel") } }) }
    confirmPurge?.let { item -> AlertDialog(onDismissRequest = { confirmPurge = null }, title = { Text("Permanently delete this file?") },
        text = { Text("${item.original}\n${assetSize(item.bytes)}\n\nThis cannot be undone.") },
        confirmButton = { TextButton(onClick = { vm.purge(item.id); confirmPurge = null }, enabled = !state.busy) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text("Cancel") } }) }
}

private fun assetIcon(category: String) = when (category) {
    "Photos" -> Icons.Default.Image; "Videos" -> Icons.Default.Movie; "Audio" -> Icons.Default.AudioFile
    "Projects", "Folders" -> Icons.Default.Folder; "Archives" -> Icons.Default.FolderZip
    "Trash" -> Icons.Default.DeleteOutline; "Recents" -> Icons.Default.Schedule; else -> Icons.Default.Description
}
internal fun assetSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1000 -> "%.1f KB".format(bytes / 1000.0)
    else -> "$bytes B"
}
@Composable private fun AssetThumbnail(item: AssetEntry, vm: AssetBrowserViewModel, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, item.path, item.modified) {
        if (item.category == "Photos" && !item.folder) value = withContext(Dispatchers.IO) { runCatching {
            val file = vm.library.resolve(item.path)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 160).coerceAtLeast(1) }
            BitmapFactory.decodeFile(file.path, options)
        }.getOrNull() }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), item.name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            ?: Icon(assetIcon(item.category), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
@Composable private fun AssetCard(item: AssetEntry, vm: AssetBrowserViewModel, selected: Set<String>, onSelect: () -> Unit, onOpen: () -> Unit, modifier: Modifier) {
    OutlinedCard(onClick = onOpen, modifier = modifier) { Column(Modifier.padding(10.dp)) {
        AssetThumbnail(item, vm, Modifier.fillMaxWidth().height(100.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text(item.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (!item.folder) Checkbox(item.path in selected, { onSelect() }) }
        Text(if (item.folder) "Folder" else assetSize(item.bytes), style = MaterialTheme.typography.bodySmall)
    } }
}
