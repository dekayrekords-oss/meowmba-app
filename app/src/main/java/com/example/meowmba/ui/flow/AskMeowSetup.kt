package com.example.meowmba.ui.flow

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.screen.AskMeowTileService

@Composable
fun AskMeowSetup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Ask Meow in another app", style = MaterialTheme.typography.titleMedium)
        Text("Open Quick Settings and tap Ask Meow. Choose the app to share in Android's prompt, then tap the captured-screen notification to ask your question. Sharing stops after one picture.")
        Text("Or share a screenshot or selected text directly with Meow.", Modifier.padding(top = 8.dp))
        if (Build.VERSION.SDK_INT >= 33) Button(onClick = {
            runCatching {
                context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(context, AskMeowTileService::class.java), "Ask Meow",
                    Icon.createWithResource(context, android.R.drawable.ic_menu_view), context.mainExecutor) { code ->
                    result = when (code) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Ask Meow was added to Quick Settings."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Ask Meow is already in Quick Settings."
                        else -> "You can also add Ask Meow using the pencil / Edit button in Quick Settings."
                    }
                }
            }.onFailure { result = "Open Quick Settings, tap Edit, and drag Ask Meow into your tiles." }
        }) { Text("Add Ask Meow tile") }
        else Text("Open Quick Settings, tap Edit, and drag Ask Meow into your tiles.")
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
