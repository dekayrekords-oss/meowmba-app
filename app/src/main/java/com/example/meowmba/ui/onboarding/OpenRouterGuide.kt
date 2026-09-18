package com.example.meowmba.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agent.OpenRouterModels
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.theme.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guided OpenRouter setup for onboarding (reusable from Settings).
 *
 * Create/select key → consent → paste securely → pick model → test connection.
 * Optional advanced field clarifies OpenRouter cloud vs local OpenAI-compatible route.
 */
@Composable
fun OpenRouterGuidePage(
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val storage = remember { SecureStorageManager(context) }
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var model by remember {
        mutableStateOf(storage.getModelName("openrouter") ?: "openrouter/free")
    }
    var endpoint by remember {
        mutableStateOf(storage.getEndpoint("openrouter") ?: "https://openrouter.ai/api")
    }
    var showAdvanced by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            if (!storage.getApiKey("openrouter").isNullOrBlank()) {
                "A key is already saved. Paste a new one to replace it, or test the saved key."
            } else {
                ""
            },
        )
    }
    var catalogHint by remember { mutableStateOf("") }

    fun openKeys() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/settings/keys")),
            )
        }.onFailure {
            status = "Open openrouter.ai/settings/keys in a browser, then return here."
        }
    }

    fun testAndSave() {
        if (busy) return
        busy = true
        status = "Testing connection…"
        val supplied = key.trim().ifBlank { storage.getApiKey("openrouter").orEmpty() }
        val base = endpoint.trim().ifBlank { "https://openrouter.ai/api" }
        val modelId = model.trim().ifBlank { "openrouter/free" }
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    require(consent) { "Allow cloud (or local endpoint) use before connecting." }
                    require(supplied.isNotBlank()) { "Paste your OpenRouter key first." }
                    val models = OpenRouterModels.fetch(supplied).getOrThrow()
                    storage.saveVerifiedApiKey("openrouter", supplied)
                    storage.saveModelName("openrouter", modelId)
                    storage.saveVerifiedEndpoint("openrouter", base)
                    storage.savePreference("brain_provider", "openrouter")
                    storage.savePreference("default_brain", "openrouter")
                    Triple(models.size, models.take(4).joinToString { it.id }, modelId)
                }
            }
            busy = false
            outcome.fold(
                onSuccess = { (count, sample, usedModel) ->
                    status = "Connected · $count models. Using $usedModel."
                    catalogHint = if (sample.isNotBlank()) "Examples: $sample" else ""
                },
                onFailure = { err ->
                    status = err.message ?: "Connection failed"
                    catalogHint = ""
                },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "OpenRouter",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Cloud AI by default. Optional: point the same settings at a local OpenAI-compatible server.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = ::openKeys, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("1. Create or select a key ↗", style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !busy)
            Text(
                "Allow OpenRouter (or my custom endpoint) to process setup tests and chats. The key stays encrypted on this phone.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            enabled = !busy,
            label = { Text("2. Paste OpenRouter key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            enabled = !busy,
            label = { Text("3. Model id (e.g. openrouter/free)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { showAdvanced = !showAdvanced }, enabled = !busy) {
            Text(if (showAdvanced) "Hide local / custom route" else "Optional: local or custom endpoint")
        }
        if (showAdvanced) {
            Text(
                "Default https://openrouter.ai/api is OpenRouter cloud. " +
                    "For a local OpenAI-compatible server, paste its base URL (no /chat/completions suffix).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                enabled = !busy,
                label = { Text("API base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = ::testAndSave,
            enabled = !busy && consent &&
                (key.isNotBlank() || !storage.getApiKey("openrouter").isNullOrBlank()),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                if (busy) "Testing…" else "4. Save key & test connection",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (catalogHint.isNotBlank()) {
            Text(catalogHint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Continue", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onSkip) { Text("Skip for now") }
    }
}
