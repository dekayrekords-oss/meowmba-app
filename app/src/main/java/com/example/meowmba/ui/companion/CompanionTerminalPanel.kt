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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import com.example.meowmba.ui.theme.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Interactive shell session UI for the companion chat Terminal tab.
 *
 * Scrollback is ViewModel-held ([scrollback]); this panel does not own the session.
 * Connect opens a persistent PTY/shell; Send writes a line + newline to stdin.
 */
@Composable
fun CompanionTerminalPanel(
    targets: List<CompanionTerminalTarget>,
    status: CompanionTerminalStatus,
    statusDetail: String?,
    scrollback: String,
    connectedTargetId: String?,
    onConnect: (targetId: String) -> Unit,
    onDisconnect: () -> Unit,
    onSendLine: (line: String) -> Unit,
    onInterrupt: () -> Unit,
    onClear: () -> Unit,
    onResize: (cols: Int, rows: Int) -> Unit = { _, _ -> },
    computerPower: com.example.meowmba.core.config.ComputerPowerState =
        com.example.meowmba.core.config.ComputerPowerState.Idle,
    onComputerPower: (com.example.meowmba.core.config.ComputerPowerState) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    var draft by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable {
        mutableStateOf(targets.firstOrNull()?.id ?: CompanionTerminalTarget.LOCAL_ID)
    }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }
    val density = LocalDensity.current

    // Keep picker in sync with an already-connected session.
    LaunchedEffect(connectedTargetId) {
        val id = connectedTargetId
        if (!id.isNullOrBlank()) selectedId = id
    }

    val selected = targets.firstOrNull { it.id == selectedId } ?: CompanionTerminalTarget.Local()
    val promptHost = when (selected) {
        is CompanionTerminalTarget.Local -> "computer"
        is CompanionTerminalTarget.AgenticProfile -> selected.profile.host
        is CompanionTerminalTarget.LegacyHost -> selected.name
    }
    val promptUser = when (selected) {
        is CompanionTerminalTarget.Local -> "meow"
        is CompanionTerminalTarget.AgenticProfile -> selected.profile.username
        is CompanionTerminalTarget.LegacyHost -> selected.name
    }

    val connected = status == CompanionTerminalStatus.Connected
    val connecting = status == CompanionTerminalStatus.Connecting
    val computerSelected = selected is CompanionTerminalTarget.Local
    val statusLabel = when {
        computerSelected && computerPower == com.example.meowmba.core.config.ComputerPowerState.Off &&
            !connected && !connecting -> "off"
        computerSelected && computerPower == com.example.meowmba.core.config.ComputerPowerState.Idle &&
            !connected && !connecting -> "idle"
        status == CompanionTerminalStatus.Disconnected -> "disconnected"
        status == CompanionTerminalStatus.Connecting -> "connecting…"
        status == CompanionTerminalStatus.Connected -> "connected"
        status == CompanionTerminalStatus.Error -> "error"
        else -> "disconnected"
    }
    val statusColor = when (status) {
        CompanionTerminalStatus.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
        CompanionTerminalStatus.Connecting -> MaterialTheme.colorScheme.tertiary
        CompanionTerminalStatus.Connected -> MaterialTheme.colorScheme.primary
        CompanionTerminalStatus.Error -> MaterialTheme.colorScheme.error
    }

    LaunchedEffect(scrollback) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    fun submit() {
        val cmd = draft
        if (cmd.isEmpty() && !connected) return
        if (!connected) return
        val line = cmd.trimEnd('\n')
        draft = ""
        historyIndex = -1
        if (line.isNotEmpty() && history.lastOrNull() != line) history.add(line)
        onSendLine(line)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { menuOpen = true },
                enabled = !connected && !connecting,
            ) {
                Text(selected.label, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                targets.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.label) },
                        onClick = {
                            selectedId = t.id
                            menuOpen = false
                        },
                    )
                }
            }
            Text(
                statusLabel,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = statusColor),
                modifier = Modifier.padding(start = 4.dp),
            )
            if (!statusDetail.isNullOrBlank() && status == CompanionTerminalStatus.Error) {
                Text(
                    " · ${statusDetail.take(48)}",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.error),
                    maxLines = 1,
                )
            }
            if (computerSelected) {
                val onColor = if (computerPower == com.example.meowmba.core.config.ComputerPowerState.On) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val offColor = if (computerPower == com.example.meowmba.core.config.ComputerPowerState.Off) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                TextButton(
                    onClick = { onComputerPower(com.example.meowmba.core.config.ComputerPowerState.On) },
                    enabled = computerPower != com.example.meowmba.core.config.ComputerPowerState.On || !connected,
                ) {
                    Text("On", fontSize = 12.sp, color = onColor)
                }
                TextButton(
                    onClick = { onComputerPower(com.example.meowmba.core.config.ComputerPowerState.Off) },
                    enabled = computerPower != com.example.meowmba.core.config.ComputerPowerState.Off,
                ) {
                    Text("Off", fontSize = 12.sp, color = offColor)
                }
            }
            Spacer(Modifier.weight(1f))
            if (connected || connecting) {
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = { onConnect(selected.id) }) {
                    Text(
                        if (computerSelected &&
                            computerPower != com.example.meowmba.core.config.ComputerPowerState.On
                        ) {
                            "Wake"
                        } else {
                            "Connect"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            TextButton(onClick = onClear) {
                Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SelectionContainer {
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
                    .onSizeChanged { size ->
                        val cols = with(density) {
                            (size.width.toDp().value / 7.2f).toInt().coerceIn(40, 200)
                        }
                        val rows = with(density) {
                            (size.height.toDp().value / 14f).toInt().coerceIn(10, 80)
                        }
                        onResize(cols, rows)
                    },
            ) {
                // Render as a single monospace block so mid-line PTY chunks stay intact.
                Text(
                    scrollback.ifEmpty { "Meow terminal — pick Computer or SSH, then Connect.\n" + com.example.meowmba.core.config.ComputerPowerStore.COPY + "\n" },
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                    ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Visual hint only — real PS1 streams from the shell when connected.
            if (!connected) {
                Text(
                    "$promptUser@$promptHost$",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 28.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                singleLine = true,
                enabled = connected,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            TextButton(
                onClick = onInterrupt,
                enabled = connected,
            ) {
                Text(
                    "Ctrl-C",
                    fontSize = 11.sp,
                    color = if (connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                )
            }
            IconButton(enabled = connected && draft.isNotEmpty(), onClick = { submit() }) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send to stdin",
                    tint = if (connected && draft.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        if (history.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp)) {
                TextButton(
                    onClick = {
                        if (history.isEmpty()) return@TextButton
                        historyIndex = (historyIndex + 1).coerceAtMost(history.lastIndex)
                        val idx = history.lastIndex - historyIndex
                        if (idx in history.indices) draft = history[idx]
                    },
                ) { Text("Prev", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                ) { Text("Next", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
