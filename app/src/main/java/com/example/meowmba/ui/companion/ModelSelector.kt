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
            
            LazyColumn {
                items(modelProviders) { model ->
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
val modelProviders = listOf(
    "Gemma 2b (Local)",
    "Ollama (Local Network)",
    "OpenAI (Cloud)",
)
