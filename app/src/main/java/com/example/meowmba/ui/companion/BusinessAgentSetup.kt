package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.workflows.*
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.agent.ModelCatalog
import com.example.meowmba.core.agent.ModelDownloader
import com.example.meowmba.core.download.DownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File

@Composable internal fun BusinessMcpSetup(runtime: WorkflowRuntime) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var localHttp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Link a real MCP service", style = MaterialTheme.typography.titleMedium)
        Text("Use the Streamable HTTP endpoint and access token provided by your service. This version supports bearer-token linking, not browser OAuth or desktop-only stdio servers. Installing Gmail or another app does not automatically provide MCP access.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it.take(80) }, enabled = !busy, label = { Text("Service name") })
        OutlinedTextField(url, { url = it }, enabled = !busy, label = { Text("HTTPS MCP endpoint") })
        OutlinedTextField(token, { token = it }, enabled = !busy, label = { Text("Access token (if required)") }, visualTransformation = PasswordVisualTransformation())
        Row { Checkbox(localHttp, { localHttp = it }, enabled = !busy); Text("Allow unencrypted HTTP to my local-network server. Its token and data may be visible on the network.", style = MaterialTheme.typography.bodySmall) }
        Row { Checkbox(consent, { consent = it }, enabled = !busy); Text("Contact this endpoint to verify the connection and discover its tools. Do not run any tools yet.", style = MaterialTheme.typography.bodySmall) }
        Button(enabled = !busy && consent && name.isNotBlank() && url.isNotBlank(), onClick = {
            val endpoint = url.trim(); val suppliedToken = token.trim(); val label = name.trim(); val allowHttp = localHttp
            busy = true
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) {
                        WorkflowMcpClient.validateEndpoint(endpoint, allowHttp)
                        val discovered = withTimeout(60_000) { WorkflowMcpClient(endpoint, suppliedToken).listTools() }
                        check(discovered.isNotEmpty()) { "Connected, but no tools were exposed. Nothing was added." }
                        val connection = BusinessMcpConnection(name = label, endpoint = endpoint, tools = discovered)
                        SecureStorageManager(context).saveVerifiedApiKey("business_mcp_${connection.id}", suppliedToken)
                        runtime.store.change { snapshot ->
                            check(snapshot.connections.none { it.endpoint == endpoint }) { "This endpoint is already connected" }
                            snapshot.copy(connections = snapshot.connections + connection)
                        }
                        discovered.size
                    }
                    token = ""; status = "Linked: $count actual tools discovered. Edit an agent to grant selected tools. Service calls always ask you first."
                } catch (e: TimeoutCancellationException) { status = "Connection test timed out. No tools were added." }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = e.message ?: "Connection failed" }
                finally { busy = false }
            }
        }) { Text(if (busy) "Discovering tools…" else "Verify and link") }
        Text(status, style = MaterialTheme.typography.bodySmall)
    } }
}

@Composable internal fun BusinessLocalSetup(runtime: WorkflowRuntime) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(emptyList<File>()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val directory = remember { File(context.filesDir, "models") }
    fun refresh() { scope.launch { files = withContext(Dispatchers.IO) { ModelDownloader(directory).listInstalled().filter { it.extension in setOf("task", "litertlm") } } } }
    OutlinedButton(onClick = { open = !open; if (open) refresh() }, modifier = Modifier.fillMaxWidth()) { Text("Set up a local planning model") }
    if (open) Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No cloud required", style = MaterialTheme.typography.titleMedium)
        Text("Needle routes actions; a conversation model does the planning. Small local models can fail complex jobs. Each job still enforces tools, approvals and budgets.", style = MaterialTheme.typography.bodySmall)
        if (files.isEmpty()) Text("No conversation models found in this installation.")
        files.forEach { file ->
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            runtime.mutex.withLock {
                                runtime.verifyAndSelectLocal(file.absolutePath)
                            }
                        }
                        status = "${file.name} returned a structured decision. Selected for local agents. Run a sample job next."
                    } catch (e: TimeoutCancellationException) { status = "Model timed out. Try a smaller compatible model." }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status = e.message ?: "Model test failed" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Testing…" else "Test and select ${file.name}") }
        }
        val option = ModelCatalog.options.first { it.id == "gemma-4-e2b" }
        if (files.none { it.name == option.fileName }) OutlinedButton(enabled = !busy, onClick = {
            runCatching { DownloadService.start(context, option.downloadUrl, option.fileName, option.id, null, directory) }
                .onSuccess { status = "Download started (${option.approxSize}). Progress is in notifications. After it finishes, tap Refresh, then Test and select. Wi-Fi is recommended." }
                .onFailure { status = it.message ?: "Could not start download" }
        }) { Text("Download ${option.displayName} · ${option.approxSize}") }
        TextButton(enabled = !busy, onClick = { refresh() }) { Text("Refresh downloaded models") }
        Text(status, style = MaterialTheme.typography.bodySmall)
    } }
}
