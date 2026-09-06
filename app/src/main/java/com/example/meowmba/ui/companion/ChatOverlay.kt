package com.example.meowmba.ui.companion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

/** Reply first; full history only when requested. Draft lives outside visibility. */
@Composable
fun ChatOverlay(
    isExpanded: Boolean,
    onClose: () -> Unit,
    onSend: (String) -> Unit = {},
    onDeleteMessage: (Long) -> Unit = {},
    messages: List<ChatUiMessage> = emptyList(),
    title: String = "Conversation",
    onLeaveProject: (() -> Unit)? = null,
    listening: Boolean = false,
    listenPartial: String? = null,
    onMicClick: () -> Unit = {},
    callActive: Boolean = false,
    onCallClick: () -> Unit = {},
    liveReply: String? = null,
    thinking: Boolean = false,
    conversationKey: String = "inbox",
    modifier: Modifier = Modifier,
) {
    // Retain separate drafts for projects as well as across panel collapse.
    val drafts = rememberSaveable(saver = androidx.compose.runtime.saveable.mapSaver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>>(
        save = { it.toMap() }, restore = { saved -> saved.map { it.key to it.value as String }.toMutableStateMap() }
    )) { mutableStateMapOf<String, String>() }
    val draft = drafts[conversationKey].orEmpty()
    var history by rememberSaveable { mutableStateOf(false) }
    val historyState = rememberLazyListState()
    val replyState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun collapse() { keyboard?.hide(); focus.clearFocus(); history = false; onClose() }
    BackHandler(isExpanded) {
        when {
            imeVisible -> { keyboard?.hide(); focus.clearFocus() }
            history -> history = false
            else -> collapse()
        }
    }
    val following by remember { derivedStateOf { historyState.firstVisibleItemIndex == 0 } }
    LaunchedEffect(conversationKey) { historyState.scrollToItem(0); replyState.scrollToItem(0) }
    LaunchedEffect(messages.lastOrNull { !it.isCompanion }?.id) { replyState.scrollToItem(0) }
    LaunchedEffect(messages.lastOrNull()?.id, messages.lastOrNull()?.text) {
        if (following && !historyState.isScrollInProgress) historyState.scrollToItem(0)
    }
    AnimatedVisibility(isExpanded, enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(), modifier = modifier) {
        // Subtract keyboard/navigation space before sizing the panel.
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            val limit = if (history) maxHeight * .82f else minOf(maxHeight, 340.dp)
            Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = limit).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { history = !history }) {
                            Icon(if (history) Icons.Default.UnfoldLess else Icons.Default.History,
                                if (history) "Show compact reply" else "Show full conversation")
                        }
                        IconButton(onClick = onCallClick) {
                            Icon(if (callActive) Icons.Default.CallEnd else Icons.Default.Call,
                                if (callActive) "End voice call" else "Start voice call",
                                tint = if (callActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { collapse() }) { Icon(Icons.Default.KeyboardArrowDown, "Collapse chat") }
                    }
                    if (onLeaveProject != null) TextButton(onClick = onLeaveProject) { Text("Back to inbox") }
                    if (callActive) Text("Voice call active", style = MaterialTheme.typography.labelMedium)
                    if (history) {
                        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), state = historyState, reverseLayout = true) {
                            if (!liveReply.isNullOrBlank() && messages.lastOrNull()?.text != liveReply) item("live") {
                                ChatMessage("Meow · responding", liveReply, true)
                            }
                            items(messages.asReversed(), key = { it.id }) { entry ->
                                ChatMessage(entry.sender, entry.text, entry.isCompanion) { onDeleteMessage(entry.id) }
                            }
                        }
                    } else {
                        val reply = liveReply?.takeIf { it.isNotBlank() } ?: messages.lastOrNull { it.isCompanion }?.text
                        if (!reply.isNullOrBlank()) {
                            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), state = replyState) {
                                item { SelectionContainer { Text(reply, Modifier.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge) } }
                            }
                        }
                        if (thinking) Text("Meow is thinking…", style = MaterialTheme.typography.labelMedium)
                    }
                    if (listening && !listenPartial.isNullOrBlank()) Text(listenPartial,
                        maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = draft, onValueChange = { drafts[conversationKey] = it },
                            modifier = Modifier.weight(1f).heightIn(max = 112.dp),
                            placeholder = { Text("Message Meow") }, minLines = 1, maxLines = 4,
                            shape = RoundedCornerShape(22.dp))
                        IconButton(onClick = onMicClick) {
                            Icon(Icons.Default.Mic, if (listening) "Stop listening" else "Speak",
                                tint = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(enabled = draft.isNotBlank(), onClick = {
                            val text = draft.trim()
                            if (text.isNotEmpty()) {
                                onSend(text); drafts[conversationKey] = ""
                                keyboard?.hide(); focus.clearFocus()
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessage(sender: String, message: String, isCompanion: Boolean, onDelete: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sender, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, "Delete message", modifier = Modifier.size(16.dp))
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = if (isCompanion)
            MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer) {
            SelectionContainer { Text(message, Modifier.fillMaxWidth().padding(12.dp),
                color = if (isCompanion) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}
