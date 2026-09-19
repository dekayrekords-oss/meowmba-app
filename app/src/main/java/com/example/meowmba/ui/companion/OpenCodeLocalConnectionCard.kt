package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.computer.OpenCodeLocalConnection
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*

@Composable
internal fun OpenCodeLocalConnectionCard() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Start the password-protected OpenCode server inside Ubuntu, then verify it here.") }
    var busy by remember { mutableStateOf(false) }
    com.example.meowmba.ui.theme.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OpenCode on this phone", style = MaterialTheme.typography.titleMedium)
            Text("${OpenCodeLocalConnection.ADDRESS} · username opencode", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(password, { password = it }, label = { Text("Private server password") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text("This is your Ubuntu server password—not an OpenRouter or Zen API key. Leave blank to retest the saved password.",
                style = MaterialTheme.typography.bodySmall)
            com.example.meowmba.ui.theme.Button(onClick = {
                busy = true
                scope.launch {
                    try {
                        val draft = password
                        val version = withContext(Dispatchers.IO) {
                            val storage = SecureStorageManager(context)
                            val secret = draft.ifBlank { storage.getApiKey(OpenCodeLocalConnection.CREDENTIAL).orEmpty() }
                            val result = OpenCodeLocalConnection.verify(secret)
                            storage.saveVerifiedApiKey(OpenCodeLocalConnection.CREDENTIAL, secret)
                            result
                        }
                        password = ""
                        status = "Connection verified and password saved securely. OpenCode $version. Task, progress and approval integration is not yet enabled."
                    } catch (e: CancellationException) { throw e }
                    catch (e: java.net.ConnectException) { status = "Server not running. Start OpenCode inside Ubuntu on port 4096, then retry." }
                    catch (e: Exception) { status = if (e is IllegalArgumentException || e is IllegalStateException) e.message.orEmpty()
                        else "Could not verify or save the connection. Check the server and try again." }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text(if (busy) "Checking…" else "Verify and save connection") }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
