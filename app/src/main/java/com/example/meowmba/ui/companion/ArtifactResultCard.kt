package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.Surface

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Kept after external handoff, so a missing viewer can never swallow the result. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ArtifactResultCard(media: GeneratedMedia, message: String?, onOpen: () -> Unit,
    onFiles: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var bitmap by remember(media.filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var text by remember(media.filePath) { mutableStateOf<String?>(null) }
    LaunchedEffect(media.filePath, media.timestamp) {
        withContext(Dispatchers.IO) {
            val file = File(media.filePath)
            if (media.type == GeneratedMedia.MediaType.IMAGE) {
                bitmap = runCatching {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.path, options)
                    options.inSampleSize = 1
                    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2
                    options.inJustDecodeBounds = false
                    android.graphics.BitmapFactory.decodeFile(file.path, options)
                }.getOrNull()
            } else if (file.extension.lowercase() in setOf("txt", "md", "csv", "json", "html", "htm", "eml")) {
                text = runCatching {
                    val chars = CharArray(6000)
                    val count = file.bufferedReader().use { it.read(chars) }
                    val raw = if (count > 0) String(chars, 0, count) else ""
                    if (file.extension.lowercase() in setOf("html", "htm"))
                        android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    else raw
                }.getOrNull()
            }
        }
    }
    Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
        Column(Modifier.padding(16.dp).widthIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Text("Saved result", style = MaterialTheme.typography.titleMedium)
            Text(media.label, style = MaterialTheme.typography.bodyMedium)
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = media.label,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) }
            text?.let { Text(it, modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) }
            Text(message ?: "Your file is saved. Open it in a compatible app or find it in Files.",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text("Open result") }
                TextButton(onClick = onFiles) { Text("Files") }
                TextButton(onClick = onClose) { Text("Dismiss") }
            }
        }
    }
}
