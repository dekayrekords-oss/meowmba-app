package com.example.meowmba.ui.companion

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Collapsible chat workspace for Phase 1.
 * Opened with a swipe-up on the avatar or the chat button; sent text is routed
 * through the same companion pipeline as voice input.
 */
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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    AnimatedVisibility(
        visible = isExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF121212).copy(alpha = 0.95f))
                // Ride the keyboard and clear the gesture/nav bar: the sheet's
                // background extends into the inset area, the content (input
                // row included) is pushed above it.
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    if (onLeaveProject != null) {
                        TextButton(onClick = onLeaveProject, contentPadding = PaddingValues(0.dp)) {
                            Text("Back to inbox", color = Color(0xFF58A6FF), fontSize = 12.sp)
                        }
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
            
            // Chat History (live from Room via CompanionState)
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                items(messages) { entry ->
                    ChatMessage(
                        sender = entry.sender,
                        message = entry.text,
                        isCompanion = entry.isCompanion,
                        onDelete = { onDeleteMessage(entry.id) }
                    )
                }
            }
            
            if (callActive) {
                TextButton(
                    onClick = onCallClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Hang up",
                        tint = Color(0xFFF85149),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (listening) "On a call — listening…" else "On a call with Meow · tap to hang up",
                        color = Color(0xFF3FB950),
                        fontSize = 13.sp,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var text by remember { mutableStateOf("") }
                LaunchedEffect(listening, listenPartial) {
                    if (listening && !listenPartial.isNullOrBlank()) text = listenPartial
                }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when {
                                callActive -> "Talk — I'm listening"
                                listening -> "Listening…"
                                else -> "Ask Meow anything..."
                            },
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onMicClick) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = if (listening) "Stop listening" else "Speak",
                        tint = if (listening || callActive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.85f),
                    )
                }
                IconButton(onClick = onCallClick) {
                    Icon(
                        if (callActive) Icons.Default.CallEnd else Icons.Default.Call,
                        contentDescription = if (callActive) "Hang up" else "Start voice call",
                        tint = if (callActive) Color(0xFFF85149) else Color(0xFF3FB950),
                    )
                }
                IconButton(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            onSend(trimmed)
                            text = ""
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF448AFF))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChatMessage(sender: String, message: String, isCompanion: Boolean, onDelete: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isCompanion) Alignment.Start else Alignment.End
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isCompanion) {
                Surface(
                    color = Color(0xFF2C2C2C),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close, contentDescription = "Delete",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close, contentDescription = "Delete",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    color = Color(0xFF448AFF),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Text(
            text = sender,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
    }
}
