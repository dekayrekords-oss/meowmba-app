package com.example.meowmba.ui.desk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.roundToInt

/**
 * The living room's framed picture. When the user chooses a photo, Meow keeps a small copy in its
 * own app storage on this phone; the original photo is never changed or uploaded. Call from a
 * background thread.
 */
internal object DeskPicture {
    /** Longest edge of the kept copy: sharp in the biggest frame, small on disk and in memory. */
    private const val MAX_EDGE = 720

    private fun file(context: Context) = File(context.filesDir, "desk/picture.jpg")

    fun load(context: Context): ImageBitmap? = file(context).takeIf { it.isFile }?.let { f ->
        runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
    }

    /** Copies the photo at [uri] (scaled down, upright); null when it can't be read. */
    fun save(context: Context, uri: Uri): ImageBitmap? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = MAX_EDGE.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val target = file(context)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "picture.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
        bitmap.asImageBitmap()
    }.getOrNull()

    /** Back to the painting: removes Meow's copy (the user's original photo is untouched). */
    fun reset(context: Context) { file(context).delete() }
}
