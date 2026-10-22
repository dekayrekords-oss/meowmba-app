package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Videocam
import com.example.meowmba.ui.theme.AlertDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.core.database.MemoryType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Memory screen (opened from the Memory tile).
 *
 * The user's memory controls, per the memory architecture:
 * search, inspect, delete, clear-all, export, retention, and a pause switch
 * that stops all memory writes. Every recalled memory elsewhere in the app
 * shows its reason; this screen shows the raw store behind it.
 */
@Composable
fun MemoryScreen(
    state: CompanionState,
    onSearch: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onRetentionChange: (Int) -> Unit,
    onPausedChange: (Boolean) -> Unit,
    onExport: () -> String,
    onClose: () -> Unit,
    onCorrect: (Long, String) -> Unit = { _, _ -> },
    onPin: (Long, Boolean) -> Unit = { _, _ -> }
) {
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var correction by remember { mutableStateOf("") }
    var pinning by remember { mutableStateOf<MemoryEntity?>(null) }

    var selectedTab by remember { mutableStateOf(MemoryTab.ALL) }
    val context = LocalContext.current

    val filtered = if (selectedTab == MemoryTab.ALL) state.memorySearchResults ?: state.allMemories
    else {
        val pool = state.memorySearchResults ?: state.allMemories
        when (selectedTab) {
            MemoryTab.IMAGES -> pool.filter { "image" in it.tags || "canvas" in it.tags }
            MemoryTab.VIDEOS -> pool.filter { "video" in it.tags }
            MemoryTab.PROJECTS -> pool.filter { it.type == MemoryType.PROJECT || "project" in it.tags }
            else -> pool
        }
    }
    val displayed = filtered

    editing?.let { record ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Correct memory") },
            text = { Column {
                Text("Replace this saved memory with your correction. This does not change the original conversation.")
                OutlinedTextField(value = correction, onValueChange = { correction = it.take(20000) }, label = { Text("Corrected memory") })
            } },
            confirmButton = { TextButton(enabled = correction.isNotBlank(), onClick = { onCorrect(record.id, correction); editing = null }) { Text("Save correction") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
    pinning?.let { record ->
        AlertDialog(onDismissRequest = { pinning = null }, title = { Text("Keep this memory?") },
            text = { Text("Pinned memories are kept during automatic memory cleanup. You can still delete them or unpin them at any time.") },
            confirmButton = { TextButton(onClick = { onPin(record.id, true); pinning = null }) { Text("Pin memory") } },
            dismissButton = { TextButton(onClick = { pinning = null }) { Text("Cancel") } })
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Memory",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(
                "${state.allMemories.size} memories stored on this device",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            // The glasses link is a fact about what is installed, not a promise:
            // when linked, captures pushed over the runtime bridge already appear
            // in the list below (tagged meta_glasses) with no sync button at all.
            Text(
                if (state.metaMemoryLinked) {
                    "MetaMemory linked — glasses captures appear here automatically"
                } else {
                    "MetaMemory app not installed"
                },
                color = if (state.metaMemoryLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            // Semantic memory sync is opt-in via a configured mem0 server URL.
            Text(
                when {
                    state.mem0Linked -> "Semantic memory synced — facts shared with Meow's brain"
                    state.mem0ServerUrl.isNotBlank() -> "Semantic memory server unreachable"
                    else -> "Semantic memory sync off (set a server in Settings)"
                },
                color = when {
                    state.mem0Linked -> MaterialTheme.colorScheme.primary
                    state.mem0ServerUrl.isNotBlank() -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs: All · Images · Videos · Projects
            val tabs = MemoryTab.entries
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(selectedTab),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 0.dp
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab; query = ""; onSearch("") },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (tab) {
                                    MemoryTab.ALL -> null
                                    MemoryTab.IMAGES -> Icons.Default.Image
                                    MemoryTab.VIDEOS -> Icons.Default.Videocam
                                    MemoryTab.PROJECTS -> Icons.Default.Folder
                                }
                                if (icon != null) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(tab.label, fontSize = 12.sp)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                placeholder = { Text("Search memories…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Controls row: pause, retention, export, clear
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Save new memories", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Text(
                        if (state.memoryPaused) "Paused — nothing is written" else "On — companion remembers",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(checked = !state.memoryPaused, onCheckedChange = { onPausedChange(!it) })
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep for:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                listOf(30 to "30d", 90 to "90d", 365 to "1y", 0 to "∞").forEach { (days, label) ->
                    val selected = state.retentionDays == days
                    TextButton(
                        onClick = { onRetentionChange(days) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val json = onExport()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_SUBJECT, "Meowmba memory export")
                        }
                        context.startActivity(Intent.createChooser(intent, "Export memories"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Export JSON", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear all", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Memory list
            if (displayed.isEmpty()) {
                Text(
                    if (state.memorySearchResults != null) "No memories match \"$query\"."
                    else "No memories yet. Say \"remember that …\" or just talk — checkpoints appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayed, key = { it.id }) { memory ->
                        MemoryCard(memory = memory, onDelete = { onDelete(memory.id) },
                            pinned = state.memoryPolicies[memory.id]?.pinned == true,
                            correctedAt = state.memoryPolicies[memory.id]?.correctedAt ?: 0L,
                            onCorrect = { editing = memory; correction = memory.content },
                            onPin = { if (state.memoryPolicies[memory.id]?.pinned == true) onPin(memory.id, false) else pinning = memory })
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all memories?") },
            text = { Text("This permanently deletes every stored memory and the conversation history. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

private enum class MemoryTab(val label: String) {
    ALL("All"), IMAGES("Images"), VIDEOS("Videos"), PROJECTS("Projects")
}

@Composable
private fun MemoryCard(memory: MemoryEntity, onDelete: () -> Unit, pinned: Boolean, correctedAt: Long, onCorrect: () -> Unit, onPin: () -> Unit) {
    val typeColor = when (memory.type) {
        MemoryType.SEMANTIC -> MaterialTheme.colorScheme.primary
        MemoryType.EPISODIC -> MaterialTheme.colorScheme.primary
        MemoryType.PROJECT -> MaterialTheme.colorScheme.tertiary
        MemoryType.PREFERENCE -> MaterialTheme.colorScheme.secondary
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = typeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            memory.type.name,
                            color = typeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        dateFormat.format(Date(memory.timestamp)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(memory.content, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                if (correctedAt > 0) Text("Corrected by you · ${dateFormat.format(Date(correctedAt))}", fontSize = 11.sp)
                memory.sourceMessageId?.let { Text("Conversation source #$it", fontSize = 11.sp) }
                Row {
                    TextButton(onClick = onCorrect) { Text("Correct") }
                    TextButton(onClick = onPin) { Text(if (pinned) "Unpin" else "Pin") }
                }
                if (memory.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        memory.tags.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete memory",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
