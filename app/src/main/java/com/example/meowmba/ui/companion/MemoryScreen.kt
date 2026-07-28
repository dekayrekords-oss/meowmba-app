package com.example.meowmba.ui.companion

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val displayed = state.memorySearchResults ?: state.allMemories

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
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
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Text(
                "${state.allMemories.size} memories stored on this device",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                placeholder = { Text("Search memories…", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF161B22),
                    unfocusedContainerColor = Color(0xFF161B22),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
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
                    Text("Save new memories", color = Color.White, fontSize = 14.sp)
                    Text(
                        if (state.memoryPaused) "Paused — nothing is written" else "On — companion remembers",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Switch(checked = !state.memoryPaused, onCheckedChange = { onPausedChange(!it) })
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep for:", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                listOf(30 to "30d", 90 to "90d", 365 to "1y", 0 to "∞").forEach { (days, label) ->
                    val selected = state.retentionDays == days
                    TextButton(
                        onClick = { onRetentionChange(days) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (selected) Color(0xFF58A6FF) else Color.Gray
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF85149))
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
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayed, key = { it.id }) { memory ->
                        MemoryCard(memory = memory, onDelete = { onDelete(memory.id) })
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
                    Text("Delete everything", color = Color(0xFFF85149))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MemoryCard(memory: MemoryEntity, onDelete: () -> Unit) {
    val typeColor = when (memory.type) {
        MemoryType.SEMANTIC -> Color(0xFF3FB950)
        MemoryType.EPISODIC -> Color(0xFF58A6FF)
        MemoryType.PROJECT -> Color(0xFFD29922)
        MemoryType.PREFERENCE -> Color(0xFFBC8CFF)
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Surface(
        color = Color(0xFF161B22),
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
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(memory.content, color = Color.White, fontSize = 13.sp)
                if (memory.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        memory.tags.joinToString(" · "),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete memory",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
