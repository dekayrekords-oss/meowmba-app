package com.example.meowmba.ui.companion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.meowmba.core.background.BackgroundAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Original painted room plus bounded native atmosphere. Clock suppliers are read only in
 * Canvas; the established background clock still owns pause, thermal and reduced motion.
 * Decoding is asynchronous; an absent illustration shows its complete vector room.
 */
@Composable
internal fun IllustratedEnvironmentBackground(
    animation: BackgroundAnimation,
    modifier: Modifier = Modifier,
    clock: () -> Float,
    slow: () -> Float,
    thumbnail: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(animation, thumbnail) { mutableStateOf<Bitmap?>(null) }
    val fallback = remember { PetEnvironmentBackgroundPainter() }
    val atmosphere = remember { PetEnvironmentAtmosphere() }
    LaunchedEffect(context, animation, thumbnail) {
        bitmap = IllustratedRoomCache.load(context, animation, if (thumbnail) THUMBNAIL_EDGE else ROOM_EDGE)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Canvas(modifier) {
        val t = roomPhase(clock())
        val s = roomPhase(slow())
        if (image == null) {
            fallback.draw(this, animation, t, s)
        } else {
            val placement = illustratedRoomPlacement(image.width, image.height, size.width, size.height, illustratedSourceTableAnchor(animation))
            // Anchored cover maps each measured image ledge to the world's .78 plane.
            // Center-crop would silently move the contact plane on a landscape Fold.
            clipRect {
                drawImage(image, dstOffset = IntOffset(placement.x.roundToInt(), placement.y.roundToInt()),
                    dstSize = IntSize(placement.width.roundToInt().coerceAtLeast(1), placement.height.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High)
                clipRect(bottom = size.height * PET_ENVIRONMENT_TABLE_Y) {
                    atmosphere.drawLight(this, animation, s)
                    atmosphere.drawDetails(this, animation, t, s)
                }
            }
        }
    }
}

internal data class IllustratedRoomPlacement(val x: Float, val y: Float, val width: Float, val height: Float)

/** Source contact anchors are measured from the final assets, never inferred from prompts. */
internal fun illustratedSourceTableAnchor(animation: BackgroundAnimation): Float = when (animation) {
    BackgroundAnimation.KITCHEN, BackgroundAnimation.LIBRARY -> .689f
    BackgroundAnimation.GREENHOUSE -> 994f / 1536f
    BackgroundAnimation.ORCHARD -> 941f / 1536f
    BackgroundAnimation.WORKSHOP -> 908f / 1536f
    BackgroundAnimation.POND -> 974f / 1536f
    BackgroundAnimation.CAFE -> 945f / 1536f
    BackgroundAnimation.OBSERVATORY -> 905f / 1536f
    BackgroundAnimation.BEACH -> 982f / 1536f
    BackgroundAnimation.BEDROOM -> 887f / 1536f
    else -> .69f
}

/** Cover without stretching; align the measured source ledge to the viewport's .78. */
internal fun illustratedRoomPlacement(imageWidth: Int, imageHeight: Int, width: Float, height: Float, sourceAnchorFraction: Float = .69f): IllustratedRoomPlacement {
    if (imageWidth <= 0 || imageHeight <= 0 || !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f)
        return IllustratedRoomPlacement(0f, 0f, 1f, 1f)
    val sourceAnchor = if (sourceAnchorFraction.isFinite()) sourceAnchorFraction.coerceIn(.05f, .95f) else .69f
    val sourceY = imageHeight * sourceAnchor
    val targetY = height * PET_ENVIRONMENT_TABLE_Y
    val scale = maxOf(width / imageWidth, targetY / sourceY, (height - targetY) / (imageHeight - sourceY))
    val drawnWidth = imageWidth * scale
    val drawnHeight = imageHeight * scale
    return IllustratedRoomPlacement((width - drawnWidth) * .5f,
        targetY - sourceY * scale, drawnWidth, drawnHeight)
}

/** Bound both dimensions before allocation, and never enlarge a small image. */
internal fun illustratedDecodeSize(width: Int, height: Int, requestedEdge: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 1 to 1
    val edge = requestedEdge.coerceIn(64, ROOM_EDGE)
    val ratio = minOf(1.0, edge.toDouble() / max(width, height))
    return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
}

private fun roomPhase(value: Float) = if (value.isFinite()) value - kotlin.math.floor(value) else 0f
private const val ROOM_EDGE = 1536
private const val THUMBNAIL_EDGE = 320

/**
 * Serialized decode limits transient allocations when a picker composes all ten rooms.
 * LRU retains <=16MiB of pixels; evictions drop references rather than recycling bitmaps
 * still referenced by an in-flight Compose draw. Active consumers keep their own bitmap.
 */
private object IllustratedRoomCache {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val lock = Mutex()
    private val entries = LinkedHashMap<String, Bitmap>(16, .75f, true)
    private var bytes = 0L

    suspend fun load(context: Context, animation: BackgroundAnimation, edge: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!animation.isPetEnvironment) return@withLock null
            val key = "${animation.id}:$edge"
            entries[key]?.let { return@withLock it }
            val decoded = try {
                val source = ImageDecoder.createSource(context.assets, "backgrounds/painted/${animation.id}.png")
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val (width, height) = illustratedDecodeSize(info.size.width, info.size.height, edge)
                    decoder.setTargetSize(width, height)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                }
            } catch (_: java.io.IOException) {
                null
            } catch (_: RuntimeException) {
                null
            } ?: return@withLock null
            val cost = decoded.allocationByteCount.toLong()
            while (bytes + cost > MAX_BYTES && entries.isNotEmpty()) {
                val eldest = entries.entries.iterator()
                val removed = eldest.next().value
                bytes -= removed.allocationByteCount.toLong()
                eldest.remove()
            }
            if (cost <= MAX_BYTES) {
                entries[key] = decoded
                bytes += cost
            }
            decoded
        }
    }
}
