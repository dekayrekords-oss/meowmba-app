package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.OutlinedButton

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.example.meowmba.BuildConfig
import com.example.meowmba.core.update.PreviewSignerKind
import com.example.meowmba.core.update.PreviewUpdateChecker
import com.example.meowmba.core.update.PreviewUpdateResult
import com.example.meowmba.core.update.PreviewUpdateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Help & feedback: manual "Check for update" plus apk-serve base URL.
 */
@Composable
fun PreviewUpdateSection() {
    val context = LocalContext.current
    val store = remember { PreviewUpdateStore(context) }
    val checker = remember { PreviewUpdateChecker(context, store) }
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf(store.baseUrl) }
    var lanUrl by remember { mutableStateOf(store.lanFallbackUrl) }
    var useLan by remember { mutableStateOf(store.useLanFallback) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<PreviewUpdateResult.Available?>(null) }
    val signer = remember { checker.installedSigner() }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            Toast.makeText(context, "No browser available to open the download page.", Toast.LENGTH_LONG).show()
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Preview updates", style = MaterialTheme.typography.titleMedium)
            Text(
                "Installed: ${BuildConfig.VERSION_NAME} (vc${BuildConfig.VERSION_CODE}) · signer " +
                    when (signer) {
                        PreviewSignerKind.PROG -> "prog (get-preview)"
                        PreviewSignerKind.CLAUDE -> "Claude / Fold (get-preview-alt)"
                        PreviewSignerKind.UNKNOWN -> "unknown"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("apk-serve base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = lanUrl,
                onValueChange = { lanUrl = it },
                label = { Text("LAN fallback URL") },
                singleLine = true,
                enabled = useLan,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Try LAN if NetBird fails", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = useLan,
                    onCheckedChange = {
                        useLan = it
                        store.useLanFallback = it
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        store.baseUrl = baseUrl
                        store.lanFallbackUrl = lanUrl
                        store.useLanFallback = useLan
                        baseUrl = store.baseUrl
                        lanUrl = store.lanFallbackUrl
                        Toast.makeText(context, "Saved apk-serve URLs", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Save URLs") }
                TextButton(
                    onClick = {
                        baseUrl = PreviewUpdateStore.DEFAULT_BASE_URL
                        lanUrl = PreviewUpdateStore.DEFAULT_LAN_URL
                        store.baseUrl = baseUrl
                        store.lanFallbackUrl = lanUrl
                    },
                ) { Text("Reset defaults") }
            }
            Button(
                onClick = {
                    if (busy) return@Button
                    store.baseUrl = baseUrl
                    store.lanFallbackUrl = lanUrl
                    store.useLanFallback = useLan
                    busy = true
                    status = "Checking…"
                    available = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            checker.check(force = true)
                        }
                        busy = false
                        when (result) {
                            is PreviewUpdateResult.UpToDate -> {
                                status = "You are on the latest Preview" +
                                    (result.feed?.let { " (feed vc${it.versionCode})" } ?: "")
                            }
                            is PreviewUpdateResult.Available -> {
                                status = "Update available: vc${result.feed.versionCode}"
                                available = result
                            }
                            is PreviewUpdateResult.Error -> {
                                status = "Check failed: ${result.message}"
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Checking…" else "Check for update") }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Automatic checks run about once a day on resume / WorkManager and notify when a newer versionCode is published. Notifications are not repeated for the same version.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    available?.let { avail ->
        AlertDialog(
            onDismissRequest = { available = null },
            title = { Text("Preview vc${avail.feed.versionCode} available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Installed vc${avail.installedCode} → feed ${avail.feed.versionName} (vc${avail.feed.versionCode})",
                    )
                    avail.feed.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    if (avail.feed.size > 0) {
                        Text("Size ≈ ${avail.feed.size / (1024 * 1024)} MB · ${avail.feed.apk}")
                    }
                    Text(
                        "Prefer ${avail.preferredPage} for your installer signature. Use the alt page only if you intentionally switch keys (may require uninstall).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        openUrl(store.getPreviewUrl(alt = avail.preferredPage.contains("alt")))
                        available = null
                    },
                ) { Text("Download / Open get-preview") }
            },
            dismissButton = {
                Row {
                    avail.altPage?.let { alt ->
                        TextButton(
                            onClick = {
                                openUrl(store.getPreviewUrl(alt = alt.contains("alt")))
                                available = null
                            },
                        ) { Text("Open alt") }
                    }
                    TextButton(onClick = { available = null }) { Text("Later") }
                }
            },
        )
    }
}
