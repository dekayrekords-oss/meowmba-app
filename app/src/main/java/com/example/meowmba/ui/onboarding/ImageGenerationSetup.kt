package com.example.meowmba.ui.onboarding

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.OutlinedCard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.media.*
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.launch

/** Shared between first-run setup and Settings. Draft credentials never enter saved-instance state. */
@Composable
fun ImageGenerationSetup(
    storage: SecureStorageManager,
    onSelected: (ImageService) -> Unit = {},
) {
    var active by remember { mutableStateOf(ImageServiceSettings.selected(storage)) }
    var choice by remember { mutableStateOf(active) }
    var account by remember { mutableStateOf(storage.getPreference(ImageServiceSettings.CF_ACCOUNT).orEmpty()) }
    var token by remember { mutableStateOf(storage.getApiKey(ImageServiceSettings.CF_KEY).orEmpty()) }
    var hordeKey by remember { mutableStateOf(storage.getApiKey(ImageServiceSettings.HORDE_KEY).orEmpty()) }
    var sharing by remember { mutableStateOf(storage.getBooleanPreference(ImageServiceSettings.HORDE_CONSENT, false)) }
    var server by remember { mutableStateOf(storage.getPreference("image_server_url").orEmpty()) }
    var note by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val links = LocalUriHandler.current
    val anonymous = hordeKey.isBlank() || hordeKey.trim() == HordeImageGenerator.ANONYMOUS_KEY
    val valid = when (choice) {
        ImageService.CLOUDFLARE -> account.trim().matches(Regex("[a-fA-F0-9]{32}")) && token.isNotBlank()
        ImageService.HORDE -> !anonymous || sharing
        ImageService.CUSTOM -> runCatching { java.net.URI(server.trim()).let { it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null && it.query == null && it.fragment == null } }.getOrDefault(false)
        else -> true
    }
    Text("Choose where images are made", style = MaterialTheme.typography.titleLarge)
    Text("Active: ${active.title}. Your choice is saved only when you tap Use this option. Cloud options send your prompt off the phone.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    ImageService.entries.filter { it != ImageService.LEGACY || active == ImageService.LEGACY }.forEach { service ->
        OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !testing) {
            choice = service; note = ""
        }) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = choice == service, onClick = { choice = service; note = "" }, enabled = !testing)
                Column(Modifier.weight(1f)) {
                    Text(service.title, style = MaterialTheme.typography.titleMedium)
                    Text(service.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    when (choice) {
        ImageService.HORDE -> {
            Text("Anonymous use needs no login, API key or credit card. Requests run on community workers, with lower priority; generation may take several minutes.")
            OutlinedTextField(value = hordeKey, onValueChange = { hordeKey = it; note = "" },
                label = { Text("Personal AI Horde key (optional)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), enabled = !testing, modifier = Modifier.fillMaxWidth())
            if (anonymous) {
                Text("Privacy: anonymous generations are shared for dataset use. Do not include private or sensitive information.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().clickable { sharing = !sharing }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sharing, onCheckedChange = { sharing = it })
                    Text("I understand anonymous generations are shared for dataset use.", modifier = Modifier.weight(1f))
                }
            } else Text("Your prompt still goes to community workers. Meow requests no dataset sharing when using your personal key.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { links.openUri("https://aihorde.net/") }) { Text("About AI Horde & optional registration") }
        }
        ImageService.CLOUDFLARE -> {
            Text("1. Create or sign in to Cloudflare.\n2. Copy your Account ID from the dashboard.\n3. Create a token with Workers AI edit/write access for that account. Paste both below.")
            TextButton(onClick = { links.openUri("https://developers.cloudflare.com/workers-ai/get-started/rest-api/") }) { Text("Open step-by-step Cloudflare guide") }
            OutlinedTextField(value = account, onValueChange = { account = it; note = "" }, label = { Text("Account ID (32 characters)") }, singleLine = true, enabled = !testing, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = token, onValueChange = { token = it; note = "" }, label = { Text("Workers AI API token") }, singleLine = true, enabled = !testing,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Text("Free accounts have a daily allowance, not unlimited generation. Paid accounts may incur usage charges. Meow never enables a paid plan for you. Tokens are encrypted on this phone.", style = MaterialTheme.typography.bodySmall)
        }
        ImageService.CUSTOM -> {
            OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("Image server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Example: http://192.168.1.10:9100. The server must accept POST /generate with a JSON prompt and return an image. Prefer HTTPS outside your home network.", style = MaterialTheme.typography.bodySmall)
        }
        ImageService.LOCAL -> Text("Install the image model in Settings → Images. Local generation can be slow and memory-intensive; it does not silently fall back to cloud services.", style = MaterialTheme.typography.bodySmall)
        ImageService.LEGACY -> Text("Your existing local/server preferences stay unchanged. Choose a specific option to disable automatic fallback.", style = MaterialTheme.typography.bodySmall)
    }
    if (choice == ImageService.CLOUDFLARE || choice == ImageService.HORDE) {
        OutlinedButton(enabled = !testing && (choice == ImageService.HORDE || valid), modifier = Modifier.fillMaxWidth(), onClick = {
            testing = true; note = "Checking connection…"
            scope.launch {
                try {
                    val result = if (choice == ImageService.HORDE) HordeImageGenerator().checkConnection()
                        else CloudflareImageGenerator(account.trim(), token.trim()).checkConnection()
                    note = result.getOrElse { it.message ?: "Connection check failed." }
                } finally { testing = false }
            }
        }) { Text(if (testing) "Checking…" else "Check connection (no image generated)") }
    }
    Button(enabled = valid && !testing, modifier = Modifier.fillMaxWidth(), onClick = {
        when (choice) {
            ImageService.HORDE -> {
                storage.saveApiKey(ImageServiceSettings.HORDE_KEY, hordeKey.trim())
                storage.saveBooleanPreference(ImageServiceSettings.HORDE_CONSENT, sharing)
            }
            ImageService.CLOUDFLARE -> {
                storage.savePreference(ImageServiceSettings.CF_ACCOUNT, account.trim())
                storage.saveApiKey(ImageServiceSettings.CF_KEY, token.trim())
            }
            ImageService.CUSTOM -> storage.savePreference("image_server_url", server.trim().trimEnd('/'))
            else -> Unit
        }
        storage.savePreference(ImageServiceSettings.PROVIDER, choice.name)
        active = choice; note = "Saved on this phone. ${choice.title} will handle new image requests."
        onSelected(choice)
    }) { Text("Use this option") }
    if (note.isNotBlank()) Text(note, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
}
