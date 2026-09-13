package com.example.meowmba.ui.companion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundKind
import com.example.meowmba.core.background.BackgroundSelection
import com.example.meowmba.core.weather.WeatherNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whatever the user chose to have behind the companion.
 *
 * Drawn as the first child of the companion screen, so it is genuinely *behind* the avatar
 * rather than a translucent sheet over it — that distinction is the difference between a
 * wallpaper and a filter.
 *
 * A dim scrim always sits on top. Backgrounds compete with the face for attention, and a
 * bright photo makes the eyes unreadable; the strength is the user's to choose, but the
 * mechanism is not optional.
 */
@Composable
fun AppBackground(
    selection: BackgroundSelection,
    /** Current sky, for [BackgroundKind.LIVE_WEATHER]. Null falls back to a plain sky. */
    weather: WeatherNow?,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (selection.kind) {
            BackgroundKind.NONE -> Unit

            BackgroundKind.IMAGE -> ImageBackground(selection.value)

            BackgroundKind.ANIMATION -> {
                val animation = selection.animation
                val condition = animation?.condition
                when {
                    condition != null -> {
                        // Pinned condition, live clock: a "clear sky" wallpaper that stays
                        // noon-blue at 11pm looks broken. No reconciliation here — there is
                        // no real reading to reconcile against, so the clock is the truth.
                        val tod = rememberClockTimeOfDay()
                        WeatherScene(
                            condition = condition,
                            isDay = tod.isLight,
                            timeOfDay = tod,
                            modifier = Modifier.fillMaxSize(),
                            paused = paused,
                        )
                    }
                    animation != null -> AnimatedBackground(animation, Modifier.fillMaxSize(), paused)
                    else -> Unit
                }
            }

            BackgroundKind.LIVE_WEATHER -> weather?.let { w ->
                WeatherScene(
                    condition = w.condition,
                    isDay = w.isDay,
                    modifier = Modifier.fillMaxSize(),
                    paused = paused,
                )
            } ?: AnimatedBackground(BackgroundAnimation.STARFIELD, Modifier.fillMaxSize(), paused)
        }

        if (selection.kind != BackgroundKind.NONE && selection.dimPercent > 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = selection.dimPercent / 100f))
            )
        }
    }
}

/**
 * A user-picked image, decoded once per URI and downsampled to something a phone screen can
 * actually use.
 *
 * Decoding at full resolution is how a 12-megapixel photo turns into an OOM on a background
 * that nobody is even looking at directly, so [ImageDecoder] is capped at [MAX_EDGE].
 * A failed decode leaves the background empty rather than crashing — a wallpaper is never
 * worth taking the app down for.
 */
@Composable
private fun ImageBackground(uriString: String) {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uriString) {
        bitmap = withContext(Dispatchers.IO) { decodeBackground(context, uriString) }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun decodeBackground(context: Context, uriString: String): Bitmap? = runCatching {
    val source = if (uriString == "asset:///backgrounds/conservatory.png")
        ImageDecoder.createSource(context.assets, "backgrounds/conservatory.png")
    else ImageDecoder.createSource(context.contentResolver, Uri.parse(uriString))
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > MAX_EDGE) {
            val factor = MAX_EDGE.toFloat() / longest
            decoder.setTargetSize(
                (info.size.width * factor).toInt().coerceAtLeast(1),
                (info.size.height * factor).toInt().coerceAtLeast(1),
            )
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
    }
}.getOrNull()

private const val MAX_EDGE = 2_048
