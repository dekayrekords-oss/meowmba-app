package com.example.meowmba.ui.flow

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.screen.ScreenHelpState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHelpSheet(state: ScreenHelpState, onAsk: (String, Boolean) -> Unit, onClose: () -> Unit) {
    var question by remember(state.observedAt) { mutableStateOf("") }
    var configuredAi by remember(state.observedAt) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 740.dp).verticalScroll(rememberScrollState()).imePadding().padding(24.dp).testTag("screen_help")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ask Meow about this screen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text("One shared picture. Meow isn't watching or controlling the other app.", style = MaterialTheme.typography.bodySmall)
            val bitmap = remember(state.image) { state.image?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }
            bitmap?.let { Image(it.asImageBitmap(), "The screen shared with Meow", Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(vertical = 12.dp)) }
            if (state.reading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            if (state.text.isNotBlank()) {
                Text("Read from your screen", style = MaterialTheme.typography.titleSmall)
                Text(state.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()))
                OutlinedTextField(question, { question = it.take(1000) }, label = { Text("What would you like help with?") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("screen_help_question"), minLines = 2)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(configuredAi, { configuredAi = it })
                    Text("Use my configured AI for this question", Modifier.weight(1f))
                }
                Text(if (configuredAi) "The screen text and your question may be sent to your configured AI provider. The picture stays on the phone."
                    else "Uses an installed local chat model. Reading the picture itself stays on this phone.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onAsk(question.trim().ifBlank { "Explain what this screen is showing and what I can do next." }, configuredAi) },
                    enabled = !state.reading && !state.busy, modifier = Modifier.padding(top = 12.dp).testTag("screen_help_ask")) { Text("Ask Meow") }
            }
            state.answer?.let { Text(it, Modifier.padding(top = 18.dp), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
