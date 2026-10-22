package com.example.meowmba.ui.flow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.flow.FlowComments
import com.example.meowmba.core.flow.Intention
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun FlowCommentSheet(item: Intention, busy: Boolean, result: String?,
    onSubmit: (String) -> Unit, onSetup: () -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable(item.id) { mutableStateOf("") }
    val cancel = FlowComments.requestsCancellation(text)
    val target = FlowComments.target(item)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(24.dp)
        .testTag("flow_comment_sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Comment on this Flow", style = MaterialTheme.typography.titleLarge)
        Text(item.text, style = MaterialTheme.typography.titleMedium)
        target?.let { Text(DateTimeFormatter.ofPattern("EEE, MMM d yyyy · h:mm a z")
            .format(Instant.ofEpochMilli(it.begin).atZone(ZoneId.systemDefault()))) }
        Text("A comment is for Meow, not a reply sent to the person. Ordinary comments are saved on this Flow. For a calendar event, say ‘this event is canceled’ to cancel that occurrence.")
        item.facts[FlowComments.TEXT]?.let { Text("Last comment: $it") }
        OutlinedTextField(text, { text = it.take(1000) }, Modifier.fillMaxWidth().testTag("flow_comment_input"),
            enabled = !busy, label = { Text("Your comment") }, minLines = 2, maxLines = 5)
        if (cancel && target != null) Text("This will cancel only the occurrence shown above in its calendar, then remove its Flow reminder after verification. It will not cancel the whole series.")
        if (cancel && target == null) Text("This Flow does not have an exact calendar event attached. Nothing will be canceled; open the event's current calendar Flow first.")
        result?.let { Text(it, Modifier.testTag("flow_comment_result")) }
        Button(onClick = { onSubmit(text.trim()) }, enabled = text.isNotBlank() && !busy && (!cancel || target != null),
            modifier = Modifier.testTag("flow_comment_submit")) {
            Text(if (busy) "Working…" else if (cancel) "Cancel this occurrence" else "Save comment")
        }
        TextButton(onClick = onSetup, enabled = !busy) { Text("Calendar & tool access guide") }
        TextButton(onClick = onClose, enabled = !busy) { Text("Close") }
    }
}
