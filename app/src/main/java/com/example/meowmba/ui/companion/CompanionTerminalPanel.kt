package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Interactive shell session UI for the companion chat Terminal tab.
 * Monospace scrollback + prompt + history; runs via [onRunCommand].
 */
@Composable
fun CompanionTerminalPanel(
    targets: List<CompanionTerminalTarget>,
    onRunCommand: suspend (targetId: String, command: String) -> String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val lines = remember { mutableStateListOf("Meow terminal ready. Pick a target, type a command, Enter.") }
    var draft by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable {
        mutableStateOf(targets.firstOrNull()?.id ?: CompanionTerminalTarget.LOCAL_ID)
    }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }

    val selected = targets.firstOrNull { it.id == selectedId } ?: CompanionTerminalTarget.Local
    val promptHost = when (selected) {
        is CompanionTerminalTarget.Local -> "local"
        is CompanionTerminalTarget.AgenticProfile -> selected.profile.host
        is CompanionTerminalTarget.LegacyHost -> selected.name
    }
    val promptUser = when (selected) {
        is CompanionTerminalTarget.Local -> "meow"
        is CompanionTerminalTarget.AgenticProfile -> selected.profile.username
        is CompanionTerminalTarget.LegacyHost -> selected.name
    }

    fun appendLine(text: String) {
        text.split('\n').forEach { lines.add(it) }
        while (lines.size > 2_000) lines.removeAt(0)
    }

    fun submit() {
        val cmd = draft.trim()
        if (cmd.isEmpty() || busy) return
        appendLine("$promptUser@$promptHost$ $cmd")
        draft = ""
        historyIndex = -1
        if (history.lastOrNull() != cmd) history.add(cmd)
        busy = true
        scope.launch {
            val result = runCatching { onRunCommand(selected.id, cmd) }
                .getOrElse { e -> "error: ${e.message ?: e.javaClass.simpleName}" }
            appendLine(result.ifBlank { "(no output)" })
            busy = false
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1117))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { menuOpen = true }) {
                Text(selected.label, color = Color(0xFF58A6FF), fontSize = 12.sp)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                targets.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.label) },
                        onClick = {
                            selectedId = t.id
                            menuOpen = false
                            appendLine("[switched to ${t.label}]")
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { lines.clear(); lines.add("scrollback cleared") }) {
                Text("Clear", fontSize = 12.sp, color = Color(0xFF8B949E))
            }
        }

        SelectionContainer {
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                lines.forEach { line ->
                    Text(
                        line,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFC9D1D9),
                            lineHeight = 16.sp,
                        ),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "$promptUser@$promptHost$",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF3FB950),
                ),
            )
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 28.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE6EDF3),
                ),
                cursorBrush = SolidColor(Color(0xFF58A6FF)),
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            IconButton(enabled = !busy && draft.isNotBlank(), onClick = { submit() }) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Run command",
                    tint = if (!busy && draft.isNotBlank()) Color(0xFF58A6FF) else Color(0xFF484F58),
                )
            }
        }
        if (busy) {
            Text(
                "running…",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8B949E),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // Up-arrow history via long-press on prompt would be nice; expose last cmd buttons.
        if (history.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp)) {
                TextButton(
                    onClick = {
                        if (history.isEmpty()) return@TextButton
                        historyIndex = (historyIndex + 1).coerceAtMost(history.lastIndex)
                        val idx = history.lastIndex - historyIndex
                        if (idx in history.indices) draft = history[idx]
                    },
                ) { Text("Prev", fontSize = 11.sp, color = Color(0xFF8B949E)) }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = {
                        if (historyIndex <= 0) {
                            historyIndex = -1
                            draft = ""
                        } else {
                            historyIndex -= 1
                            val idx = history.lastIndex - historyIndex
                            if (idx in history.indices) draft = history[idx]
                        }
                    },
                ) { Text("Next", fontSize = 11.sp, color = Color(0xFF8B949E)) }
            }
        }
    }
}
