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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayInputStream

/** User-operated developer terminal. Not exposed to companion tool routing. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DevelopmentStudioPanel() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var terminal by remember { mutableStateOf<OpenCodeTerminal?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready to connect to your existing Ubuntu Computer.") }
    var showPreview by remember { mutableStateOf(false) }
    var confirmConnect by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var receiptText by remember { mutableStateOf("") }
    var preparedApk by remember { mutableStateOf<DevelopmentApk.Prepared?>(null) }
    var fetchingApk by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val events = remember { Channel<String>(64, BufferOverflow.DROP_OLDEST) }
    val scroll = rememberScrollState()
    // Output is push-driven and capped; no status polling or startup worker.
    LaunchedEffect(Unit) { for (text in events) {
        // Batch bursts instead of recomposing once per terminal frame. The delay
        // runs only after output arrives, never as an idle/background polling loop.
        delay(32)
        val batch = StringBuilder(text)
        while (batch.length < 128_000) {
            val next = events.tryReceive().getOrNull() ?: break
            batch.append(next)
        }
        output = DevelopmentStudioPolicy.appendOutput(output, batch.toString().takeLast(64_000))
        // The Desk computer lists Studio projects from these same status lines (kept on this phone).
        DevStudioProjects.get(context).record(batch.toString())
        DevelopmentApkReceipt.latest(output)?.let {
            if (receiptText != it.line) { receiptText = it.line; preparedApk = null }
        }
    } }
    DisposableEffect(terminal) {
        val owned = terminal
        onDispose {
            // A disposed Compose scope cannot perform the blocking PTY cleanup.
            if (owned != null) CoroutineScope(Dispatchers.IO).launch { owned.close() }
        }
    }
    DisposableEffect(Unit) { onDispose { events.close() } }

    fun send(text: String) {
        try { terminal?.send(text.toByteArray(Charsets.UTF_8)) }
        catch (error: Exception) { status = error.message ?: "Terminal disconnected."; connected = false }
    }
    fun connect() {
        connecting = true
        scope.launch {
            try {
                val worker = withContext(Dispatchers.IO) {
                    val storage = SecureStorageManager(context)
                    OpenCodeLocalConnection.restore(storage)
                    val key = storage.getApiKey(OpenCodeLocalConnection.CREDENTIAL).orEmpty()
                    check(key.isNotBlank()) { "Finish Computer setup first." }
                    OpenCodeTerminal(key)
                }
                terminal = worker
                val program = withContext(Dispatchers.IO) {
                    context.assets.open("computer/development_studio.py").bufferedReader().use { it.readText() }
                }
                val closed = java.util.concurrent.atomic.AtomicBoolean(false)
                worker.connectDevelopment(
                    onOutput = { events.trySend(it) },
                    onClosed = { closed.set(true); scope.launch { if (terminal === worker) { connected = false; status = "Session closed. Review terminal output before retrying." } } },
                    developmentProgram = program,
                )
                connected = !closed.get()
                status = if (connected) "Connected. Answer the project-name prompt below, then choose numbered actions."
                    else "Session exited. Check terminal output; Python 3 must be installed in Ubuntu."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                status = error.message ?: "Development Studio could not start."
                terminal = null
            } finally { connecting = false }
        }
    }

    Text("Development Studio", style = MaterialTheme.typography.titleLarge)
    Text("Build websites and Android projects with Claude Code or OpenCode in your existing Ubuntu. This is a manual developer terminal, separate from companion chat and both robots.")
    Text("Requires Python 3 in Ubuntu. Claude needs your own account; provider charges may apply. Optional tools are downloaded only after you agree in the terminal. Ubuntu/PRoot is not a security sandbox.", style = MaterialTheme.typography.bodySmall)
    Text("Leaving this page closes this terminal and can stop its agent/build/preview. Existing OpenCode tasks and other Termux sessions are not stopped.", color = MaterialTheme.colorScheme.tertiary)
    Text(status)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { confirmConnect = true }, enabled = !connected && !connecting) { Text(if (connecting) "Connecting…" else "Open development terminal") }
        OutlinedButton(onClick = { confirmClose = true }, enabled = terminal != null) { Text("Close session") }
        OutlinedButton(onClick = { showPreview = !showPreview }) { Text(if (showPreview) "Hide preview" else "Open preview") }
    }
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
        TextButton(onClick = { output = "" }) { Text("Clear display") }
    }
    Text("Android builds require an existing Gradle app project, JDK, Android SDK and phone-compatible ARM64 aapt2. The menu reports failures and exports only the exact successful build output. Automatic Android SDK provisioning is not yet available. If needed, configure MEOW_AAPT2 in Ubuntu before starting the Computer server.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = receiptText, onValueChange = { receiptText = it.take(180); preparedApk = null },
        label = { Text("Built APK receipt (filled from terminal)") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        val receipt = DevelopmentApkReceipt.parse(receiptText) ?: return@Button
        fetchingApk = true
        preparedApk = null
        scope.launch {
            try {
                val downloaded = DevelopmentApk.fetch(context, receipt)
                if (receiptText == receipt.line) {
                    preparedApk = downloaded
                    status = "APK downloaded and hash verified. Review the package below; nothing installed."
                } else status = "The build receipt changed during transfer. Fetch the current build before installing."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status = error.message ?: "APK transfer failed." }
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
                .onFailure { status = "Could not open Android’s install permission settings." }
        }) { Text("Allow developer-app installs in Android settings") }
        Button(onClick = { scope.launch {
            try { DevelopmentApk.requestInstall(context, apk); status = "Sent to Android’s installer. Follow its confirmation; installation is not yet verified." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status = error.message ?: "Could not open Android installer." }
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
            scope.launch { status = "Removed ${DevelopmentApk.discardDownloads(context)} temporary APK download(s). Fetch again from the build to recover them." }
        }) { Text("Remove downloads") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } })
    if (showPreview) DevelopmentWebsitePreview()
    if (confirmConnect) AlertDialog(onDismissRequest = { confirmConnect = false },
        title = { Text("Start developer session?") },
        text = { Text("This opens a manual terminal in your existing Ubuntu. Projects, approved build scripts and coding agents can access that environment. Only work with trusted projects. Nothing will be downloaded or sent to a model just by connecting.") },
        confirmButton = { TextButton(onClick = { confirmConnect = false; connect() }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = { confirmConnect = false }) { Text("Cancel") } })
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false },
        title = { Text("Close developer session?") }, text = { Text("This may interrupt the active agent or build. Checkpoints remain available when you reconnect to the same project.") },
        confirmButton = { TextButton(onClick = { confirmClose = false; terminal = null; connected = false; status = "Session closed." }) { Text("Close") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep open") } })
}

@Composable
private fun DevelopmentWebsitePreview() {
    var view by remember { mutableStateOf<WebView?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    Text("Local website preview", style = MaterialTheme.typography.titleMedium)
    Text("Start action 8 in the terminal first. Only the local preview port is allowed; external APIs/fonts/CDNs are blocked. Preview uses no Computer credentials.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { view?.reload() }) { Text("Reload preview") }
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
