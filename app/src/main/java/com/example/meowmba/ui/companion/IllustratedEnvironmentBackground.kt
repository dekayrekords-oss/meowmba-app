package com.example.meowmba.ui.companion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    val objects = remember { HabitatObjectAnimations() }
    LaunchedEffect(context, animation, thumbnail) {
        bitmap = IllustratedRoomCache.load(context, animation, if (thumbnail) THUMBNAIL_EDGE else ROOM_EDGE)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image == null) {
        Canvas(modifier) {
            fallback.draw(this, animation, roomPhase(clock()), roomPhase(slow()))
        }
    } else {
        IllustratedRoomLayers(image, animation, objects, clock, modifier)
    }
}

/** Retain the stationary image's display list independently of the moving details.
 * No offscreen bitmap, lower resolution, changed phase or reduced frame rate.
 */
@Composable
internal fun IllustratedRoomLayers(image: ImageBitmap, animation: BackgroundAnimation,
    objects: HabitatObjectAnimations, clock: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize().graphicsLayer()) {
            drawIllustratedRoomImage(image, roomPlacement(image, animation))
        }
        Canvas(Modifier.matchParentSize().graphicsLayer()) {
            drawIllustratedRoomObjects(animation, objects, roomPlacement(image, animation), roomPhase(clock()))
        }
    }
}

private fun DrawScope.roomPlacement(image: ImageBitmap, animation: BackgroundAnimation) =
    illustratedRoomPlacement(image.width, image.height, size.width, size.height,
        illustratedSourceTableAnchor(animation), illustratedSourceFocusX(animation))

/** Shared native paint path used by the screen and pixel-level stationary-room checks. */
internal fun DrawScope.drawIllustratedRoom(image: ImageBitmap, animation: BackgroundAnimation,
    objects: HabitatObjectAnimations, phase: Float) {
    val placement = roomPlacement(image, animation)
    drawIllustratedRoomImage(image, placement)
    drawIllustratedRoomObjects(animation, objects, placement, phase)
}

private fun DrawScope.drawIllustratedRoomImage(image: ImageBitmap, placement: IllustratedRoomPlacement) {
    // Anchored cover maps each measured image ledge to the world's .78 plane.
    // Center-crop would silently move the contact plane on a landscape Fold.
    clipRect {
        drawImage(
            image,
            dstOffset = IntOffset(placement.x.roundToInt(), placement.y.roundToInt()),
            dstSize = IntSize(placement.width.roundToInt().coerceAtLeast(1), placement.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )
    }
}

private fun DrawScope.drawIllustratedRoomObjects(animation: BackgroundAnimation,
    objects: HabitatObjectAnimations, placement: IllustratedRoomPlacement, phase: Float) {
    clipRect {
        clipRect(bottom = size.height * PET_ENVIRONMENT_TABLE_Y) {
            objects.draw(this, animation, placement, phase)
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
    // New landscape art carries a deliberately broad, nearly level counter.
    BackgroundAnimation.CARTOON_NOOK -> 612f / 941f
    BackgroundAnimation.CARTOON_GALLEY -> 604f / 941f
    BackgroundAnimation.CARTOON_SNACK_LAB -> 679f / 1024f
    BackgroundAnimation.CARTOON_LAUNDROMAT -> 685f / 1024f
    BackgroundAnimation.CARTOON_DINER -> 664f / 1024f
    // Build 117 source artwork is square; the final measured contact anchors are
    // verified against the shipped PNGs by the integration build.
    BackgroundAnimation.CARTOON_PORTAL_ARCADE -> 758f / 1254f
    BackgroundAnimation.CARTOON_MOON_GARDEN -> 833f / 1254f
    BackgroundAnimation.CARTOON_ALIEN_AQUARIUM -> 753f / 1254f
    BackgroundAnimation.CARTOON_METEOR_GARAGE -> 850f / 1254f
    else -> .69f
}

/** Keep the illustrated joke in portrait's narrow crop; other rooms retain center framing. */
internal fun illustratedSourceFocusX(animation: BackgroundAnimation): Float = when (animation) {
    BackgroundAnimation.CARTOON_SNACK_LAB -> .23f
    BackgroundAnimation.CARTOON_DINER -> .18f
    else -> .5f
}

/** Cover without stretching; align the ledge vertically and bound horizontal focal framing. */
internal fun illustratedRoomPlacement(imageWidth: Int, imageHeight: Int, width: Float, height: Float,
    sourceAnchorFraction: Float = .69f, sourceFocusX: Float = .5f): IllustratedRoomPlacement {
    if (imageWidth <= 0 || imageHeight <= 0 || !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f)
        return IllustratedRoomPlacement(0f, 0f, 1f, 1f)
    val sourceAnchor = if (sourceAnchorFraction.isFinite()) sourceAnchorFraction.coerceIn(.05f, .95f) else .69f
    val sourceY = imageHeight * sourceAnchor
    val targetY = height * PET_ENVIRONMENT_TABLE_Y
    // Anchor each image to the shared interaction plane while still covering
    // every edge. Landscape rooms now have dedicated wide source art; reducing
    // cover below this value would expose blank bands on an unfolded screen.
    val scale = maxOf(width / imageWidth, targetY / sourceY, (height - targetY) / (imageHeight - sourceY))
    val drawnWidth = imageWidth * scale
    val drawnHeight = imageHeight * scale
    val focus = if (sourceFocusX.isFinite()) sourceFocusX.coerceIn(0f, 1f) else .5f
    val left = (width * .5f - drawnWidth * focus).coerceIn(minOf(0f, width - drawnWidth), 0f)
    return IllustratedRoomPlacement(left,
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
