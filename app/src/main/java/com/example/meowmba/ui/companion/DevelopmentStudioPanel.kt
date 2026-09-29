package com.example.meowmba.ui.companion

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.meowmba.core.computer.*
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream

/** User-operated developer terminal. Not exposed to companion tool routing. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DevelopmentStudioPanel() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val session = DevStudioSession
    var showPreview by remember { mutableStateOf(false) }
    var confirmConnect by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var preparedApk by remember { mutableStateOf<DevelopmentApk.Prepared?>(null) }
    var fetchingApk by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var hasComputer by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        hasComputer = withContext(Dispatchers.IO) {
            runCatching { SecureStorageManager(context).getApiKey(OpenCodeLocalConnection.CREDENTIAL).orEmpty().isNotBlank() }.getOrDefault(false)
        }
    }
    val scroll = rememberScrollState()
    val output = session.output
    val connected = session.connected
    val receiptText = session.receiptText
    LaunchedEffect(receiptText) { preparedApk = null }
    val status = session.status ?: when (hasComputer) {
        false -> "Set up Computer first: Computer → Set up Computer."
        true -> "Ready to connect to your Ubuntu Computer."
        null -> ""
    }
    fun send(text: String) = session.send(text)

    Text("Development Studio", style = MaterialTheme.typography.titleLarge)
    Text("Build websites and Android projects with Claude Code or OpenCode in your existing Ubuntu. This is a manual developer terminal, separate from companion chat and both robots.")
    Text("Requires Python 3 in Ubuntu. Claude needs your own account; provider charges may apply. Optional tools are downloaded only after you agree in the terminal. Ubuntu/PRoot is not a security sandbox.", style = MaterialTheme.typography.bodySmall)
    Text("Your session keeps running if you leave this page. Close session ends it and stops its agent, build or preview.", color = MaterialTheme.colorScheme.tertiary)
    Text(status)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { confirmConnect = true }, enabled = !connected && !session.connecting) { Text(if (session.connecting) "Connecting…" else "Open development terminal") }
        OutlinedButton(onClick = { confirmClose = true }, enabled = session.hasTerminal) { Text("Close session") }
        OutlinedButton(onClick = { showPreview = !showPreview }) { Text(if (showPreview) "Hide preview" else "Open preview") }
    }
    // Right under its button: at the bottom of the page it looked like the button did nothing.
    if (showPreview) DevelopmentWebsitePreview()
    Text("Checkpoint before editing → run an agent → review each file → Keep or Undo. Builds/dependencies and secret files are excluded. Stop other editors before Undo. The menu retains discarded file content for recovery.", style = MaterialTheme.typography.bodySmall)
    SelectionContainer {
        Text(output.ifBlank { "No session output yet." },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).verticalScroll(scroll),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    }
    OutlinedTextField(value = input, onValueChange = { input = it.take(8000) },
        label = { Text("Terminal response or coding request") }, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("Use Claude’s own login flow. Don’t paste API keys into this transcript.") })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { send(input + "\n"); input = "" }, enabled = connected) { Text("Send / Enter") }
        OutlinedButton(onClick = { send("\u0003") }, enabled = connected) { Text("Ctrl-C") }
        TextButton(onClick = { send("\u001b") }, enabled = connected) { Text("Esc") }
        TextButton(onClick = { send("\u001b[A") }, enabled = connected) { Text("Up") }
        TextButton(onClick = { send("\u001b[B") }, enabled = connected) { Text("Down") }
        TextButton(onClick = { session.output = "" }) { Text("Clear display") }
    }
    Text("Android builds require an existing Gradle app project, JDK, Android SDK and phone-compatible ARM64 aapt2. The menu reports failures and exports only the exact successful build output. Automatic Android SDK provisioning is not yet available. If needed, configure MEOW_AAPT2 in Ubuntu before starting the Computer server.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = receiptText, onValueChange = { session.receiptText = it.take(180); preparedApk = null },
        label = { Text("Built APK receipt (filled from terminal)") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        val receipt = DevelopmentApkReceipt.parse(receiptText) ?: return@Button
        fetchingApk = true
        preparedApk = null
        scope.launch {
            try {
                val downloaded = DevelopmentApk.fetch(context, receipt)
                if (session.receiptText == receipt.line) {
                    preparedApk = downloaded
                    session.status = "APK downloaded and hash verified. Review the package below; nothing installed."
                } else session.status = "The build receipt changed during transfer. Fetch the current build before installing."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { session.status = error.message ?: "APK transfer failed." }
            finally { fetchingApk = false }
        }
    }, enabled = !fetchingApk && DevelopmentApkReceipt.parse(receiptText) != null) {
        Text(if (fetchingApk) "Verifying APK…" else "Fetch built APK")
    }
    preparedApk?.let { apk ->
        Text("Package: ${apk.packageName}\nVersion: ${apk.version}\nBytes: ${apk.bytes}\nSHA-256: ${apk.sha256}", style = MaterialTheme.typography.bodySmall)
        Text("Only install apps whose source you trust. Hash verification proves transfer integrity, not app safety.")
        if (context.packageName == "com.example.meowmba") {
        TextButton(onClick = {
            runCatching { context.startActivity(DevelopmentApk.allowInstallIntent(context)) }
                .onFailure { session.status = "Could not open Android’s install permission settings." }
        }) { Text("Allow developer-app installs in Android settings") }
        Button(onClick = { scope.launch {
            try { DevelopmentApk.requestInstall(context, apk); session.status = "Sent to Android’s installer. Follow its confirmation; installation is not yet verified." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { session.status = error.message ?: "Could not open Android installer." }
        } }) { Text("Review installation in Android") }
        } else Text("Developer APK installation is available in the preview edition only.")
    }
    TextButton(onClick = { confirmDiscard = true }, enabled = !fetchingApk) { Text("Remove developer APK downloads") }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Remove downloaded developer APKs?") },
        text = { Text("Only Development Studio’s temporary phone copies are removed. Project files, installed apps and Ubuntu build outputs are kept. Wait until Android’s installer finishes first.") },
        confirmButton = { TextButton(onClick = {
            confirmDiscard = false
            preparedApk = null
            scope.launch { session.status = "Removed ${DevelopmentApk.discardDownloads(context)} temporary APK download(s). Fetch again from the build to recover them." }
        }) { Text("Remove downloads") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } })
    if (confirmConnect) AlertDialog(onDismissRequest = { confirmConnect = false },
        title = { Text("Start developer session?") },
        text = { Text("This opens a manual terminal in your existing Ubuntu. Projects, approved build scripts and coding agents can access that environment. Only work with trusted projects. Nothing will be downloaded or sent to a model just by connecting.") },
        confirmButton = { TextButton(onClick = { confirmConnect = false; session.connect(context) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = { confirmConnect = false }) { Text("Cancel") } })
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false },
        title = { Text("Close developer session?") }, text = { Text("This may interrupt the active agent or build. Checkpoints remain available when you reconnect to the same project.") },
        confirmButton = { TextButton(onClick = { confirmClose = false; session.close() }) { Text("Close") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep open") } })
}

@Composable
private fun DevelopmentWebsitePreview() {
    var view by remember { mutableStateOf<WebView?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    Text("Local website preview", style = MaterialTheme.typography.titleMedium)
    Text("Start action 8 in the terminal first. Only the local preview port is allowed; external APIs/fonts/CDNs are blocked. Preview uses no Computer credentials.", style = MaterialTheme.typography.bodySmall)
    var notRunning by remember { mutableStateOf(false) }
    TextButton(onClick = { notRunning = false; view?.reload() }) { Text("Reload preview") }
    if (notRunning) Text("Nothing is running on the preview port yet. In the terminal choose 8 Preview, then tap Reload preview.",
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    AndroidView(modifier = Modifier.fillMaxWidth().height(420.dp), factory = { context ->
        WebView(context).apply {
            view = this
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) notRunning = true
                }
                override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean =
                    !DevelopmentStudioPolicy.previewAllowed(request?.url.toString())
                override fun shouldInterceptRequest(v: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                    if (DevelopmentStudioPolicy.previewAllowed(request?.url.toString())) null
                    else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
            loadUrl(DevelopmentStudioPolicy.PREVIEW)
        }
    })
    DisposableEffect(Unit) { onDispose { view?.stopLoading(); view?.destroy(); view = null } }
    DisposableEffect(lifecycle, view) {
        val current = view
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) current?.onPause()
            if (event == Lifecycle.Event.ON_START) current?.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
