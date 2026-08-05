package com.example.meowmba.ui.companion

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * App drawer (swipe right from the face): every launchable app on the phone,
 * alphabetical, with a live search bar on top. MEOWMBA excludes itself.
 */
data class DrawerApp(val label: String, val packageName: String)

@Composable
fun AppDrawerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val label = info.loadLabel(pm)?.toString() ?: return@mapNotNull null
                DrawerApp(label, info.activityInfo.packageName)
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    val visible = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Search apps…", color = Color.White.copy(alpha = 0.6f)) },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 84.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(visible) { app ->
                    Column(
                        modifier = Modifier
                            .clickable {
                                runCatching {
                                    val launch = context.packageManager
                                        .getLaunchIntentForPackage(app.packageName)
                                    if (launch != null) context.startActivity(launch)
                                }
                            }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val icon = remember(app) {
                            runCatching {
                                context.packageManager.getApplicationIcon(app.packageName)
                            }.getOrNull()
                        }
                        if (icon != null) {
                            androidx.compose.foundation.Image(
                                bitmap = icon.toBitmap().asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            app.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Text(
            "Swipe left to go back",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}
