package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.computer.OpenCodeLocalConnection
import com.example.meowmba.core.computer.PhoneComputerSetup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*

@Composable
internal fun OpenCodeLocalConnectionCard() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Setup reuses Ubuntu and OpenCode when present, installs missing components and checks the private connection. Downloads may take several minutes.") }
    var busy by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Permission granted. Tap Setup computer to continue." else "Termux command permission is needed. You can allow it in Android Settings → Meow → Permissions."
    }
    com.example.meowmba.ui.theme.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OpenCode on this phone", style = MaterialTheme.typography.titleMedium)
            Text("${OpenCodeLocalConnection.ADDRESS} · username opencode", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(password, { password = it }, label = { Text("Private server password") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text("Choose a private connection password (at least 12 characters), not your Ubuntu login or API key. Leave blank to reuse your saved password. No existing Ubuntu installation will be removed.",
                style = MaterialTheme.typography.bodySmall)
            Text("One-time Termux requirement: enable allow-external-apps in Termux settings. Android requires your approval; Meow cannot change another app’s private settings.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { permission.launch(PhoneComputerSetup.PERMISSION) }, enabled = !busy) { Text("Allow Meow to connect to Termux") }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent#allow-external-apps-property-mandatory")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Show Termux connection instructions") }
            com.example.meowmba.ui.theme.Button(onClick = setup@{
                if (context.checkSelfPermission(PhoneComputerSetup.PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permission.launch(PhoneComputerSetup.PERMISSION)
                    return@setup
                }
                busy = true
                status = "Preparing Ubuntu and OpenCode… Keep this screen open. Existing files are preserved."
                scope.launch {
                    try {
                        val draft = password
                        val version = withContext(Dispatchers.IO) {
                            val storage = SecureStorageManager(context)
                            val secret = draft.ifBlank { storage.getApiKey(OpenCodeLocalConnection.CREDENTIAL)
                                ?.takeIf { it.isNotBlank() }
                                ?: storage.getApiKey(OpenCodeLocalConnection.CREDENTIAL + "_pending").orEmpty() }
                            require(secret.length in 12..256 && secret.none { it == '\n' || it == '\r' || it == '\u0000' }) {
                                "Enter a private password of 12–256 characters on one line."
                            }
                            // Retain a separate encrypted draft if Android closes this screen during
                            // provisioning. Never mark it as the verified connection credential.
                            storage.saveVerifiedApiKey(OpenCodeLocalConnection.CREDENTIAL + "_pending", secret)
                            val result = PhoneComputerSetup.start(context, secret)
                            storage.saveVerifiedApiKey(OpenCodeLocalConnection.CREDENTIAL, secret)
                            storage.saveApiKey(OpenCodeLocalConnection.CREDENTIAL + "_pending", "")
                            result
                        }
                        password = ""
                        status = "Connection verified and password saved securely. OpenCode $version. Task, progress and approval integration is not yet enabled."
                    } catch (e: TimeoutCancellationException) { status = "Setup is taking longer than expected. Check Termux and retry; downloads may still be running."
                    } catch (e: CancellationException) { throw e }
                    catch (e: java.net.ConnectException) { status = "Server is not ready yet. Check Termux, then retry setup." }
                    catch (e: Exception) { status = if (e is IllegalArgumentException || e is IllegalStateException) e.message.orEmpty()
                        else "Could not verify or save the connection. Check the server and try again." }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text(if (busy) "Setting up…" else "Setup computer") }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
