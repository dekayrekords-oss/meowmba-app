package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.computer.OpenCodeLocalConnection
import com.example.meowmba.core.computer.PhoneComputerSetup
import com.example.meowmba.core.computer.TermuxSetupSupport
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*

@Composable
internal fun OpenCodeLocalConnectionCard(onVerified: () -> Unit = {}, supportOverride: TermuxSetupSupport? = null) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var needsExternalApps by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf("Setup reuses Ubuntu and OpenCode when present, installs missing components and checks the private connection. Downloads may take several minutes.") }
    var busy by remember { mutableStateOf(false) }
    fun copyExternalAppsFixAndOpenTermux() {
        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("Termux: allow Meow", PhoneComputerSetup.ALLOW_EXTERNAL_APPS_COMMAND))
        val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
        status = if (launch == null) "Command copied. Open Termux from your apps, paste it at the prompt and press Enter, then come back and tap Setup computer."
            else "Command copied. In Termux, long-press → Paste, press Enter, then come back and tap Setup computer. If you are inside Ubuntu, type exit first."
        launch?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
    val pendingAddress = remember { runCatching { SecureStorageManager(context).getEndpoint(OpenCodeLocalConnection.CREDENTIAL + "_pending") }.getOrNull() }
    var separate by rememberSaveable { mutableStateOf(pendingAddress == OpenCodeLocalConnection.RECOVERY_ADDRESS) }
    var confirmSeparate by remember { mutableStateOf(false) }
    var support by remember { mutableStateOf(supportOverride ?: TermuxSetupSupport.detect(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, supportOverride) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) support = supportOverride ?: TermuxSetupSupport.detect(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val targetAddress = if (separate) OpenCodeLocalConnection.RECOVERY_ADDRESS else OpenCodeLocalConnection.ADDRESS
    val guided = support == TermuxSetupSupport.GUIDED
    fun secretFor(storage: SecureStorageManager, draft: String): String {
        val pending = OpenCodeLocalConnection.CREDENTIAL + "_pending"
        return draft.ifBlank {
            if (storage.getEndpoint(pending) == targetAddress) storage.getApiKey(pending).orEmpty()
            else if (!separate) storage.getApiKey(OpenCodeLocalConnection.CREDENTIAL).orEmpty() else ""
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Permission granted. Tap Setup computer to continue." else "Termux command permission is needed. You can allow it in Android Settings → Meow → Permissions."
    }
    com.example.meowmba.ui.theme.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OpenCode on this phone", style = MaterialTheme.typography.titleMedium)
            val profile = remember { com.example.meowmba.core.setup.SetupDeviceScan.profile(context) }
            if (!profile.recommendPhoneComputer) Text(profile.computerReason, style = MaterialTheme.typography.bodySmall)
            Text("${if (separate) OpenCodeLocalConnection.RECOVERY_ADDRESS else OpenCodeLocalConnection.ADDRESS} · username opencode", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(password, { password = it }, label = { Text(if (separate) "Choose a NEW connection password" else "Existing server password (or new setup password)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), singleLine = true,
                trailingIcon = { PasswordVisibilityToggle(showPassword) { showPassword = !showPassword } },
                enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("opencode_password"))
            Text(if (separate) "Choose a new password of 12–256 characters. This creates a separate server on port 4097. The old server, Ubuntu files and active tasks are left untouched."
                else "If a server already runs, enter THAT server's password—not a new password, Ubuntu login or API key. Leave blank to reuse the saved password. If none is running, this password is used to create one.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = { confirmSeparate = true }) { Text("Forgot password? Create a separate Meow connection") }
            if (support == TermuxSetupSupport.AUTOMATIC) {
                Text("Automatic setup supported. Two one-time steps:\n1. Let Termux accept commands from Meow (allow-external-apps). Termux has no switch for this; tap below to copy a one-line command, paste it into Termux and press Enter.\n2. Approve Meow's Termux command permission.", style = MaterialTheme.typography.bodySmall)
                if (needsExternalApps) Text("Termux blocked setup: allow-external-apps is off. Do step 1, then tap Setup computer again.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                com.example.meowmba.ui.theme.OutlinedButton(onClick = { copyExternalAppsFixAndOpenTermux() }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("termux_allow_external_apps")) { Text("1. Copy Termux fix & open Termux") }
                TextButton(onClick = { permission.launch(PhoneComputerSetup.PERMISSION) }, enabled = !busy) { Text("2. Allow Meow to connect to Termux") }
                TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open permission settings") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent#allow-external-apps-property-mandatory")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Show Termux connection instructions") }
            } else if (guided) {
                Text("Guided setup for this Termux edition", style = MaterialTheme.typography.titleSmall)
                Text("This edition has no command permission to grant. Keep Termux and Ubuntu installed.\n1. Enter your new password above.\n2. Tap Copy setup & open Termux. At a Termux prompt, long-press → Paste, then press Enter. If already inside Ubuntu, type exit first.\n3. Enter that same password twice when asked (typing is hidden).\n4. Return here and tap Verify connection.")
                Text("The copied command contains no password. Setup reuses Ubuntu, downloads missing components, and starts only the selected local server. It never stops another server. Use Forgot password first if the current server has an unknown password.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy, onClick = {
                    busy = true
                    val draft = password
                    scope.launch {
                        try {
                            val command = withContext(Dispatchers.IO) {
                                val storage = SecureStorageManager(context)
                                val secret = secretFor(storage, draft)
                                require(secret.length in 12..256 && secret.none { it == '\n' || it == '\r' || it == '\u0000' }) { "Enter a new password of 12–256 characters first." }
                                storage.saveVerifiedConnection(OpenCodeLocalConnection.CREDENTIAL + "_pending", secret, targetAddress)
                                PhoneComputerSetup.guidedCommand(context, targetAddress)
                            }
                            val launch = context.packageManager.getLaunchIntentForPackage("com.termux") ?: error("Open Termux from your apps, then return here.")
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .setPrimaryClip(android.content.ClipData.newPlainText("Meow setup command — no password", command))
                            password = ""
                            status = "Setup copied. Paste into the Termux prompt, press Enter, and enter your password there. Return here to verify."
                            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { status = if (e is IllegalArgumentException) e.message.orEmpty() else "Could not prepare guided setup. Open Termux once and retry." }
                        finally { busy = false }
                    }
                }) { Text("Copy setup & open Termux") }
            } else Text("Install Termux from the recommended F-Droid link in the previous step, then return here. An already running server can still be verified.")
            com.example.meowmba.ui.theme.Button(onClick = setup@{
                busy = true
                status = if (guided) "Verifying the server started in Termux…" else "Preparing Ubuntu and OpenCode… Keep this screen open. Existing files are preserved."
                scope.launch {
                    try {
                        val draft = password
                        val version = withContext(Dispatchers.IO) {
                            val storage = SecureStorageManager(context)
                            val secret = secretFor(storage, draft)
                            require(secret.length in 12..256 && secret.none { it == '\n' || it == '\r' || it == '\u0000' }) {
                                "Enter a private password of 12–256 characters on one line."
                            }
                            // Retain a separate encrypted draft if Android closes this screen during
                            // provisioning. Never mark it as the verified connection credential.
                            storage.saveVerifiedConnection(OpenCodeLocalConnection.CREDENTIAL + "_pending", secret, targetAddress)
                            val result = if (guided) OpenCodeLocalConnection.verify(secret, targetAddress) else PhoneComputerSetup.start(context, secret, targetAddress)
                            storage.saveVerifiedConnection(OpenCodeLocalConnection.CREDENTIAL, secret, targetAddress)
                            if (separate) storage.savePreference(com.example.meowmba.core.computer.OpenCodeComputer.SESSION_KEY, "")
                            OpenCodeLocalConnection.restore(storage)
                            storage.saveApiKey(OpenCodeLocalConnection.CREDENTIAL + "_pending", "")
                            storage.saveEndpoint(OpenCodeLocalConnection.CREDENTIAL + "_pending", "")
                            result
                        }
                        password = ""
                        separate = false
                        needsExternalApps = false
                        status = "Connection verified and password saved securely. OpenCode $version. Open Computer → Tasks & approvals or the Ubuntu Terminal."
                        onVerified()
                    } catch (_: PhoneComputerSetup.PermissionRequired) {
                        status = "Approve Termux access to install or start your Computer."
                        permission.launch(PhoneComputerSetup.PERMISSION)
                    } catch (e: PhoneComputerSetup.ExternalAppsRequired) {
                        needsExternalApps = true
                        status = "Termux is blocking Meow (allow-external-apps is off). Tap “1. Copy Termux fix & open Termux”, paste it at the Termux prompt, press Enter, then come back and tap Setup computer. Your Ubuntu files are untouched."
                    } catch (e: PhoneComputerSetup.GuidedSetupRequired) {
                        support = TermuxSetupSupport.GUIDED; status = e.message.orEmpty()
                    } catch (e: TimeoutCancellationException) { status = "Setup is taking longer than expected. Check Termux and retry; downloads may still be running."
                    } catch (e: CancellationException) { throw e }
                    catch (e: java.net.ConnectException) { status = "Server is not ready yet. Check Termux, then retry setup." }
                    catch (e: Exception) { status = if (e is IllegalArgumentException || e is IllegalStateException) e.message.orEmpty()
                        else "Could not verify or save the connection. Check the server and try again." }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text(if (busy) "Setting up…" else if (guided) "Verify connection" else "Setup computer") }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmSeparate) AlertDialog(onDismissRequest = { confirmSeparate = false },
        title = { Text("Create a separate private connection?") },
        text = { Text("This does not reset or stop the server whose password you forgot. It starts a second OpenCode server inside the same Ubuntu installation on port 4097, with a new password. Existing work remains untouched. If that port is also occupied, setup stops safely. Another server uses additional memory.") },
        confirmButton = { TextButton(onClick = { separate = true; password = ""; confirmSeparate = false }) { Text("Choose new password") } },
        dismissButton = { TextButton(onClick = { confirmSeparate = false }) { Text("Cancel") } })
}
