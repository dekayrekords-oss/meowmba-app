package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Mock Model Provider selection UI.
 */
@Composable
fun ModelSelector(
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = Color(0xFF121212)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Select Model Provider",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Hoisted: LocalContext cannot be read inside the LazyListScope lambda.
            val offered = availableModelProviders(androidx.compose.ui.platform.LocalContext.current)

            LazyColumn {
                items(offered) { model ->
                    ModelItem(
                        name = model,
                        isSelected = model == selectedModel,
                        onClick = {
                            onModelSelected(model)
                            onClose()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ModelItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF448AFF).copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = name, color = Color.White)
    }
}

/**
 * Provider picker entries. Honesty contract: only providers that
 * [CompanionViewModel.updateProvider] can actually build are listed here —
 * no stub entries that would silently produce a null provider.
 * ("Phi-3 Mini (Local)", "Anthropic (Cloud)", "Memba Custom (Remote)" were
 * removed: they were never wired up and trapped users in "no model is ready".)
 */
/** Label for the AICore-backed provider. Also the default when AICore is present. */
const val MODEL_GEMINI_NANO = "Gemini Nano (On-device)"

/**
 * Omniroute — the route manager on meow (port 20128).
 *
 * Spoken to as an OpenAI-compatible endpoint, which is what routers of this kind expose;
 * the address and model are editable in Settings, so pointing it somewhere else (or at a
 * different port) needs no code change. If the endpoint turns out to speak a different
 * dialect, "Test connection" on the Hardware page says so rather than failing silently.
 */
const val MODEL_OMNIROUTE = "Omniroute (Router)"

/** Where Omniroute lives unless Settings says otherwise. */
const val DEFAULT_OMNIROUTE_ENDPOINT = "http://192.168.1.69:20128"

val modelProviders = listOf(
    MODEL_GEMINI_NANO,
    "On-device (Local)",
    "Ollama (Local Network)",
    MODEL_OMNIROUTE,
    "OpenAI (Cloud)",
)

/**
 * The picker entries that can actually build a provider on THIS device.
 *
 * Gemini Nano needs AICore, which not every device has. Offering it where it is
 * absent would recreate exactly the "No model is ready" trap that [modelProviders]
 * was pruned to avoid, so it is filtered out rather than shown and broken.
 */
fun availableModelProviders(context: android.content.Context?): List<String> =
    modelProviders.filter { label ->
        label != MODEL_GEMINI_NANO ||
            (context != null && com.example.meowmba.core.agent.GeminiNanoProvider.isAvailable(context))
    }
