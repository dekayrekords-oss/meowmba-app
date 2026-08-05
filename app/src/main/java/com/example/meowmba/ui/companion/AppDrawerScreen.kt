package com.example.meowmba.ui.companion

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App drawer (swipe up from the face): solid black, opens instantly (the app
 * list and icons load in the background), with a Favorites row at the bottom
 * holding up to 8 pinned apps. Long-press any app to pin/unpin it. The first
 * two pins also appear on the companion face itself.
 */
data class DrawerApp(val label: String, val packageName: String)

/** Shared icon cache so reopening the drawer costs nothing. */
private val iconCache = HashMap<String, ImageBitmap>()

fun launchApp(context: Context, packageName: String) {
    runCatching {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) context.startActivity(launch)
    }
}

private fun loadApps(context: Context): List<DrawerApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .mapNotNull { info ->
            val label = info.loadLabel(pm)?.toString() ?: return@mapNotNull null
            DrawerApp(label, info.activityInfo.packageName)
        }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    onClose: () -> Unit,
    pins: List<String> = emptyList(),
    onTogglePin: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    // Load off the UI thread: the drawer composes immediately (smooth open).
    var apps by remember { mutableStateOf<List<DrawerApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
    }

    val visible = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
        Spacer(Modifier.height(12.dp))
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
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(visible) { app ->
                val pinned = app.packageName in pins
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { launchApp(context, app.packageName) },
                            onLongClick = { onTogglePin(app.packageName) },
                        )
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DrawerIcon(packageName = app.packageName, size = 48.dp, label = app.label)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (pinned) "★ " else "") + app.label,
                        color = if (pinned) Color(0xFFFFD75E) else Color.White,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Favorites: up to 8 pinned apps, drawer-only.
        if (pins.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101010))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                pins.take(8).forEach { pkg ->
                    Column(
                        modifier = Modifier.combinedClickable(
                            onClick = { launchApp(context, pkg) },
                            onLongClick = { onTogglePin(pkg) },
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DrawerIcon(packageName = pkg, size = 40.dp, label = "favorite")
                    }
                }
            }
        }
        Text(
            if (pins.isEmpty()) "Long-press an app to pin it here" else "Swipe left to go back",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(bottom = 8.dp),
        )
    }
}

/** Async icon with a neutral placeholder until decoded (keeps the grid fluid). */
@Composable
fun DrawerIcon(
    packageName: String,
    size: androidx.compose.ui.unit.Dp,
    label: String,
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(iconCache[packageName]) }
    if (bitmap == null) {
        LaunchedEffect(packageName) {
            bitmap = withContext(Dispatchers.IO) {
                iconCache.getOrPut(packageName) {
                    runCatching {
                        context.packageManager.getApplicationIcon(packageName)
                            .toBitmap().asImageBitmap()
                    }.getOrNull() ?: return@withContext null
                }
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = label,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF222222))
        )
    }
}
