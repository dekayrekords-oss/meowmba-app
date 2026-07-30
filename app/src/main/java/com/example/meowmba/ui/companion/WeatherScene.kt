package com.example.meowmba.ui.companion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.meowmba.core.weather.WeatherCondition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ambient weather drawn behind the companion, so the cat is *in* the weather rather than
 * next to a readout of it.
 *
 * Deliberately understated — low alphas, slow speeds. The avatar is the subject; this is
 * atmosphere. Anything livelier reads as a screensaver and fights the face for attention.
 *
 * One animation clock drives everything. Particle positions are pure functions of that
 * clock plus a seeded layout, so nothing accumulates drift and recomposition never shuffles
 * the scene — a per-frame Random() would make the rain jitter sideways.
 */
@Composable
fun WeatherScene(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    // 0..1 ramp, restarting. Every effect derives its own phase from this.
    val clock by rememberInfiniteTransition(label = "weather-scene").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "clock",
    )

    // Separate slow clock: lightning must not be locked to the rain's period, or every
    // flash lands on the same raindrop.
    val slowClock by rememberInfiniteTransition(label = "weather-slow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SLOW_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "slow",
    )

    // Fixed layout per condition. Remembered so the field is stable across recomposition.
    val layout = remember(condition) { SceneLayout(condition) }

    Canvas(modifier = modifier) {
        when (condition) {
            WeatherCondition.CLEAR ->
                if (isDay) drawSun(clock) else drawStars(layout, clock)

            WeatherCondition.PARTLY_CLOUDY -> {
                if (isDay) drawSun(clock) else drawStars(layout, clock)
                drawClouds(layout, clock, alpha = 0.16f)
            }

            WeatherCondition.CLOUDY -> drawClouds(layout, clock, alpha = 0.22f)

            WeatherCondition.FOG -> drawFogBands(clock)

            WeatherCondition.DRIZZLE -> {
                drawClouds(layout, clock, alpha = 0.18f)
                drawRain(layout, clock, streak = 12f, alpha = 0.20f)
            }

            WeatherCondition.RAIN -> {
                drawClouds(layout, clock, alpha = 0.22f)
                drawRain(layout, clock, streak = 26f, alpha = 0.30f)
            }

            WeatherCondition.SNOW -> {
                drawClouds(layout, clock, alpha = 0.14f)
                drawSnow(layout, clock)
            }

            WeatherCondition.THUNDERSTORM -> {
                drawClouds(layout, clock, alpha = 0.30f)
                drawRain(layout, clock, streak = 30f, alpha = 0.28f)
                drawLightning(slowClock)
            }

            WeatherCondition.DUST -> drawDust(layout, clock)

            WeatherCondition.UNKNOWN -> Unit
        }
    }
}

/**
 * Seeded particle field.
 *
 * The seed is the condition's ordinal, so the same weather always looks the same — a scene
 * that reshuffles itself on every recomposition reads as a glitch.
 */
private class SceneLayout(condition: WeatherCondition) {
    private val rng = Random(condition.ordinal * 7919)

    /** x, phase offset, speed multiplier, size multiplier — per particle. */
    val drops = List(60) { Particle(rng) }
    val flakes = List(45) { Particle(rng) }
    val motes = List(70) { Particle(rng) }
    val clouds = List(5) { Particle(rng) }
    val stars = List(40) { Particle(rng) }
}

private class Particle(rng: Random) {
    val x = rng.nextFloat()
    val y = rng.nextFloat()
    val phase = rng.nextFloat()
    val speed = 0.6f + rng.nextFloat() * 0.9f
    val scale = 0.6f + rng.nextFloat() * 0.8f
}

/** Wraps a value into 0..1 so particles recycle instead of marching off-screen forever. */
private fun wrap(v: Float): Float = v - kotlin.math.floor(v)

// ── Effects ──────────────────────────────────────────────────────────────────────

private fun DrawScope.drawSun(clock: Float) {
    val centre = Offset(size.width * 0.80f, size.height * 0.16f)
    val glow = size.minDimension * 0.22f

    // Soft halo. Radial rather than a hard disc: a crisp circle behind a cat face looks
    // like a bug, a glow looks like light.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x33FFD98A), Color(0x00FFD98A)),
            center = centre,
            radius = glow,
        ),
        radius = glow,
        center = centre,
    )
    drawCircle(Color(0x40FFE9AE), radius = glow * 0.30f, center = centre)

    // Slow rotation, 12 rays. Breathing length keeps it from looking mechanical.
    rotate(degrees = clock * 360f, pivot = centre) {
        repeat(12) { i ->
            val a = (i / 12f) * 2f * PI.toFloat()
            val inner = glow * 0.42f
            val outer = glow * (0.62f + 0.10f * sin(clock * 2f * PI.toFloat() + i))
            drawLine(
                color = Color(0x26FFE9AE),
                start = centre + Offset(cos(a) * inner, sin(a) * inner),
                end = centre + Offset(cos(a) * outer, sin(a) * outer),
                strokeWidth = 3f,
            )
        }
    }
}

private fun DrawScope.drawStars(layout: SceneLayout, clock: Float) {
    layout.stars.forEach { p ->
        // Twinkle: each star has its own phase, so they never blink in unison.
        val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin((clock + p.phase) * 2f * PI.toFloat() * 2f))
        drawCircle(
            color = Color.White.copy(alpha = 0.30f * tw),
            radius = 1.6f * p.scale,
            center = Offset(p.x * size.width, p.y * size.height * 0.6f),
        )
    }
}

private fun DrawScope.drawClouds(layout: SceneLayout, clock: Float, alpha: Float) {
    layout.clouds.forEachIndexed { i, p ->
        // Drift right, wrapping. Extra width on both sides so clouds enter and leave
        // off-screen instead of popping into existence.
        val x = (wrap(p.x + clock * 0.12f * p.speed) * 1.4f - 0.2f) * size.width
        val y = size.height * (0.06f + 0.16f * p.y)
        val w = size.width * (0.30f + 0.18f * p.scale)
        val h = w * 0.34f
        val c = Color.White.copy(alpha = alpha * (0.7f + 0.3f * p.scale))

        // Three overlapping ovals read as a cloud; one oval reads as a pill.
        drawOval(color = c, topLeft = Offset(x, y), size = Size(w, h))
        drawOval(
            color = c,
            topLeft = Offset(x + w * 0.18f, y - h * 0.34f),
            size = Size(w * 0.52f, h * 1.05f),
        )
        drawOval(
            color = c,
            topLeft = Offset(x + w * 0.48f, y - h * 0.18f),
            size = Size(w * 0.42f, h * 0.88f),
        )
    }
}

private fun DrawScope.drawRain(layout: SceneLayout, clock: Float, streak: Float, alpha: Float) {
    layout.drops.forEach { p ->
        val y = wrap(p.y + clock * 2.2f * p.speed) * size.height
        // Slight lean: vertical rain looks like static, angled rain reads as falling.
        val x = wrap(p.x + clock * 0.06f) * size.width
        drawLine(
            color = Color(0xFFBFD8EE).copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x - streak * 0.28f, y + streak * p.scale),
            strokeWidth = 1.8f,
        )
    }
}

private fun DrawScope.drawSnow(layout: SceneLayout, clock: Float) {
    layout.flakes.forEach { p ->
        val y = wrap(p.y + clock * 0.55f * p.speed) * size.height
        // Sway: flakes drift sideways as they fall, each on its own phase.
        val sway = sin((clock + p.phase) * 2f * PI.toFloat() * 1.5f) * size.width * 0.03f
        val x = wrap(p.x) * size.width + sway
        drawCircle(
            color = Color.White.copy(alpha = 0.42f * p.scale),
            radius = 2.6f * p.scale,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawDust(layout: SceneLayout, clock: Float) {
    // Ochre wash: a dust storm changes the colour of the whole sky, not just the air in it.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x33C79A4B), Color(0x147A5A2E)),
        ),
    )
    layout.motes.forEach { p ->
        // Mostly horizontal, fast: wind-driven rather than falling.
        val x = wrap(p.x + clock * 1.6f * p.speed) * size.width
        val y = (p.y + sin((clock + p.phase) * 2f * PI.toFloat()) * 0.015f) * size.height
        drawCircle(
            color = Color(0xFFE0C089).copy(alpha = 0.30f * p.scale),
            radius = 2.2f * p.scale,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawFogBands(clock: Float) {
    // Broad soft bands at different speeds; parallax is what makes fog feel deep.
    repeat(4) { i ->
        val speed = 0.05f + i * 0.03f
        val y = size.height * (0.25f + i * 0.18f)
        val x = (wrap(i * 0.25f + clock * speed) * 1.6f - 0.3f) * size.width
        drawOval(
            brush = Brush.horizontalGradient(
                listOf(Color(0x00CBD5E1), Color(0x22CBD5E1), Color(0x00CBD5E1)),
                startX = x,
                endX = x + size.width * 0.9f,
            ),
            topLeft = Offset(x, y),
            size = Size(size.width * 0.9f, size.height * 0.10f),
        )
    }
}

private fun DrawScope.drawLightning(slowClock: Float) {
    // Two flashes per slow cycle, at fixed points so they feel occasional rather than
    // rhythmic. Each is a fast decay — a flash that fades slowly looks like a fade, not a
    // strike.
    val flash = flashIntensity(slowClock, at = 0.18f) + flashIntensity(slowClock, at = 0.62f)
    if (flash <= 0.01f) return
    drawRect(color = Color.White.copy(alpha = 0.16f * flash.coerceAtMost(1f)))
}

/** 1.0 at [at], decaying to 0 within [FLASH_WIDTH]. */
private fun flashIntensity(clock: Float, at: Float): Float {
    val d = kotlin.math.abs(clock - at)
    if (d > FLASH_WIDTH) return 0f
    return 1f - (d / FLASH_WIDTH)
}

private const val CYCLE_MS = 12_000
private const val SLOW_CYCLE_MS = 9_000
private const val FLASH_WIDTH = 0.012f
