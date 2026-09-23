package com.example.meowmba.ui.companion

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.backup.BackupUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupUpdatesPanel() {
    val context = LocalContext.current
    val manager = remember(context) { BackupUpdateManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    // Never save passwords in SavedState, preferences or logs.
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(manager.hasSelectedApk()) }
    var confirmed by remember { mutableStateOf(false) }
    var needsRestart by remember { mutableStateOf(false) }
    var openPlayAfterBackup by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val other = remember(refresh) { manager.otherEdition() }
    val cleanupProblem = remember(refresh, confirmed) { manager.cleanupProblem(confirmed) }
    val canSave = password.length >= 12 && password == repeatPassword && !busy

    fun persist(uri: Uri) {
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: SecurityException) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { /* Readability is checked before use. */ }
        }
    }
    fun runAction(failure: String, action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { message = action() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = failure }
            finally { busy = false; refresh++ }
        }
    }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            persist(uri)
            val secret = password.toCharArray(); password = ""; repeatPassword = ""
            runAction("Backup was not verified. Keep your keys and the old app. Check the save location, available space and password, then try again.") {
                try {
                    val summary = withContext(Dispatchers.IO) { manager.exportBackup(uri, secret) }
                    if (openPlayAfterBackup) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.meow.meowmba")))
                    "Backup saved and verified: ${summary.preferences} settings, ${summary.rows} records and ${summary.files} files. Keep the file and password somewhere safe."
                } finally { secret.fill('\u0000'); openPlayAfterBackup = false }
            }
        } else openPlayAfterBackup = false
    }
    val restoreDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persist(uri)
            val secret = password.toCharArray(); password = ""; repeatPassword = ""; confirmed = false
            runAction("Restore could not finish. Your existing data has not been replaced. Check the password and backup, then retry. The other edition cannot be removed here until a complete restore succeeds.") {
                try {
                    val result = withContext(Dispatchers.IO) { manager.importBackup(uri, secret) }
                    needsRestart = result.restored > 0
                    if (result.complete) "Restore verified: ${result.restored} items added, ${result.unchanged} already present. Restart Meow, then check your keys and data."
                    else "Restore kept your existing data: ${result.restored} items added, ${result.conflicts.size} conflicts need attention. Differing items were kept in the backup; the other edition stays installed."
                } finally { secret.fill('\u0000') }
            }
        }
    }
    val selectApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runAction("This APK cannot update this edition. Choose a newer, matching Meow APK. Keep the existing app installed; a different edition or signing key needs a separate migration.") {
            val result = withContext(Dispatchers.IO) { manager.stageUpdate(uri) }
            selected = true
            result
        }
    }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { manager.clearFinishedInstaller() } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Keep your keys and creations safe", style = MaterialTheme.typography.titleLarge)
        Text("A normal update replaces this edition and keeps its data. Do not uninstall first: removing an app deletes its private data and can make saved keys unrecoverable.")
        Text("Save an encrypted backup before updating. It includes saved API keys, app settings, chats, memory, tasks and supported user-created files. Downloaded models, caches, Android permissions and files held by other apps (including Termux) are excluded.", style = MaterialTheme.typography.bodyMedium)
        Text("Use this page before installing. Meow cannot pause an update started by Play Store, Files or another installer. Keep a separate copy of important keys.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Backup password (at least 12 characters)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = repeatPassword, onValueChange = { repeatPassword = it }, label = { Text("Repeat password to save a backup") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Text("Store the password separately. Meow cannot recover it. Choose a location outside Meow, such as Documents or your own cloud drive.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { openPlayAfterBackup = false; saveDocument.launch("Meow-backup-${System.currentTimeMillis()}.meowbackup") }, enabled = canSave) { Text("Save encrypted backup") }
        OutlinedButton(onClick = { restoreDocument.launch(arrayOf("*/*")) }, enabled = !busy && password.length >= 12) { Text("Restore encrypted backup") }
        Text("Restore adds missing items and keeps identical ones. If any item has different content, no items are imported and removal of the old edition is blocked. Keep both backups until you resolve the differences.", style = MaterialTheme.typography.bodySmall)
        if (needsRestart) OutlinedButton(onClick = {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        }, enabled = !busy) { Text("Reload Meow after restore") }
        HorizontalDivider()
        Text("Update this edition", style = MaterialTheme.typography.titleMedium)
        if (manager.allowsApkUpdates) {
            OutlinedButton(onClick = { selectApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }, enabled = !busy) { Text("Choose update APK") }
            if (selected) {
                Text("Save a fresh backup above, then install the selected APK. The backup must be less than 15 minutes old. Android will ask you to approve the update.")
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }, enabled = !busy) { Text("Allow updates in Android settings") }
                Button(onClick = { runAction("Installation is blocked. Save a fresh verified backup, allow updates in Android settings and choose a matching newer APK. No app was removed.") {
                    val intent = withContext(Dispatchers.IO) { manager.prepareInstall() }
                    context.startActivity(intent)
                    "Android's installer is open. Your backup stays saved whether you install or cancel."
                } }, enabled = !busy) { Text("Verify backup & install update") }
            }
        } else {
            Button(onClick = { openPlayAfterBackup = true; saveDocument.launch("Meow-backup-${System.currentTimeMillis()}.meowbackup") }, enabled = canSave) { Text("Back up & open Play Store") }
        }
        HorizontalDivider()
        Text("Move from another Meow edition", style = MaterialTheme.typography.titleMedium)
        if (other == null) Text("No other Meow edition is installed. Android already replaces older versions of this edition during normal updates.")
        else {
            Text("${manager.label(other.packageName)} is also installed. Its data is separate. Open it, save a current encrypted backup, then restore that backup here. If it has no backup option, keep it installed until it receives one.")
            OutlinedButton(onClick = { context.packageManager.getLaunchIntentForPackage(other.packageName)?.let(context::startActivity) }, enabled = !busy) { Text("Open other edition") }
            Row {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }, enabled = !busy)
                Text("I have checked that my keys and data work here, saved anything excluded from the backup, and made no new changes in the old edition.", modifier = Modifier.weight(1f))
            }
            if (cleanupProblem != null) Text(cleanupProblem, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { runAction("Removal is blocked because the imported backup could not be verified. Keep the other edition and restore a fresh backup first.") {
                val intent = withContext(Dispatchers.IO) { manager.prepareCleanup(confirmed) }
                context.startActivity(intent)
                "Android will ask you to confirm removal of the other edition. The encrypted backup will be kept."
            } }, enabled = !busy && cleanupProblem == null) { Text("Ask Android to remove other edition") }
        }
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working securely on this phone… Keep this page open.") }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
    }
}

/** Shown once when separate editions coexist, before a user mistakes migration for an update. */
@Composable
fun BackupSafetyNotice(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val state = remember { context.getSharedPreferences("backup_update_state", 0) }
    var visible by remember { mutableStateOf(!state.getBoolean("edition_warning_seen", false) && BackupUpdateManager(context).otherEdition() != null) }
    fun acknowledge() { state.edit().putBoolean("edition_warning_seen", true).apply(); visible = false }
    if (visible) AlertDialog(
        onDismissRequest = { acknowledge() },
        title = { Text("Keep your keys before removing an app") },
        text = { Text("You have two Meow editions installed. Each has its own saved keys and data. Keep both until you have saved a backup from the old edition, restored it here and checked everything works. Open Settings → Backup & updates to get started.") },
        confirmButton = { TextButton(onClick = { acknowledge(); onOpenSettings() }) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = { acknowledge() }) { Text("Keep both for now") } },
    )
}
