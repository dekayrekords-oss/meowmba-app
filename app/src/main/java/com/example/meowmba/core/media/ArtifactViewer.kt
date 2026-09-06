package com.example.meowmba.core.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Opens real artifacts in external apps, with a temporary read grant. */
object ArtifactViewer {
    fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "html", "htm" -> "text/html"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "txt", "md", "kt", "css", "js", "py" -> "text/plain"
        else -> "application/octet-stream"
    }

    fun viewIntent(uri: Uri, mime: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        clipData = ClipData.newRawUri("Generated artifact", uri)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun open(context: Context, file: File): Result<String> = runCatching {
        require(file.isFile && file.length() > 0) { "The saved file is missing or empty: ${file.name}" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = mimeFor(file)
        val intent = viewIntent(uri, mime)
        // Media belongs in a compatible player/gallery. Do not force video and
        // audio content URIs into Chrome, which may accept the intent but not play it.
        if (mime == "text/html") {
            for (pkg in listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev")) {
                if (runCatching { context.startActivity(Intent(intent).setPackage(pkg)) }.isSuccess) {
                    return@runCatching "Opened ${file.name} in Chrome."
                }
            }
        }
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            throw IllegalStateException("No app can open ${file.name} ($mime). Install a compatible viewer.", e)
        }
        "Opened ${file.name} in your viewer."
    }

    fun latest(roots: List<File>): File? = roots.asSequence().flatMap { root ->
        root.walkTopDown().maxDepth(5).take(5000)
            .filter { it.isFile && it.length() > 0 && mimeFor(it) != "application/octet-stream" }
    }.maxByOrNull { it.lastModified() }
}
