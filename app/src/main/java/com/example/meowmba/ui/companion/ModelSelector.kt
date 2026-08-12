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
 * removed: they were never wired up and trapped users in "no model is ready".
 * "Gemini Nano (On-device)" was removed on 2026-08-11: it needed AICore, which
 * this device reports as unavailable, stranding every reply on a provider that
 * could never generate.)
 */

/**
 * Omniroute — the router, running inside the app.
 *
 * Not a server call: the app holds the route table and picks between the backends that
 * are already configured on this phone. That is what lets it be the default (it always
 * has the on-device model as a fallback, so it works with no network) and it is why it
 * needs no address of its own.
 */
const val MODEL_OMNIROUTE = "Omniroute (Local router)"



val modelProviders = listOf(
    // First because it is the default: the router decides which model answers, so it is
    // the one entry that does not commit the phone to a particular engine.
    MODEL_OMNIROUTE,
    "On-device (Local)",
    "OpenRouter (Cloud)",
    "OpenAI (Cloud)",
)

fun availableModelProviders(context: android.content.Context?): List<String> = modelProviders
