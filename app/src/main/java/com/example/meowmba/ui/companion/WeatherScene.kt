package com.example.meowmba.ui.companion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.weather.WeatherCondition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Atmospheric weather drawn behind the companion.
 *
 * The first version of this looked cheap for concrete reasons, all of which are addressed
 * here rather than papered over:
 *
 *  - **Real blur.** Three stacked canvases at 26dp / 10dp / 0dp. minSdk is 31, so
 *    [Modifier.blur] is a GPU RenderEffect. Soft falloff is most of what makes weather
 *    look like weather; the previous version had none and every edge was hard and aliased.
 *  - **Depth.** Far elements are large, slow, dim and heavily blurred; near ones are
 *    smaller, faster, brighter and sharp. One flat layer at one speed reads as a
 *    screensaver.
 *  - **Soft particles.** Radial-gradient sprites and gradient-stroked streaks with round
 *    caps, instead of hard-edged circles and 1.8px lines.
 *  - **Real shapes.** Clouds are multi-lobe [Path]s, not three overlapping ovals.
 *  - **Wind.** A bounded lateral offset pushes particles sideways, so motion is not a dead
 *    straight line. It is added, never multiplied into the accumulating position — doing
 *    that made the rain travel UPWARDS whenever the gust fell faster than the clock rose.
 *  - **Density.** Far more particles, distributed across depth so the count reads as
 *    weather rather than as dots.
 */
@Composable
fun WeatherScene(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val clock by rememberInfiniteTransition(label = "scene").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(CYCLE_MS, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "clock",
    )
    // Independent slower clock for gusts and lightning, so neither locks to the particle
    // period and repeats visibly.
    val slow by rememberInfiniteTransition(label = "scene-slow").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(SLOW_CYCLE_MS, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "slow",
    )

    val field = remember(condition) { Field(condition) }

    Box(modifier = modifier) {
        // Atmosphere wash: unifies the palette so particles sit IN a sky rather than on
        // top of a black rectangle.
        Canvas(Modifier.fillMaxSize()) { drawAtmosphere(condition, isDay, slow) }

        // FAR — heavy blur, big, slow, dim.
        Canvas(Modifier.fillMaxSize().blur(FAR_BLUR)) {
            drawLayer(condition, isDay, field, clock, slow, Depth.FAR)
        }
        // MID — some blur.
        Canvas(Modifier.fillMaxSize().blur(MID_BLUR)) {
            drawLayer(condition, isDay, field, clock, slow, Depth.MID)
        }
        // NEAR — sharp, fast, brightest. Sharpness only reads as sharp against blurred
        // layers behind it.
        Canvas(Modifier.fillMaxSize()) {
            drawLayer(condition, isDay, field, clock, slow, Depth.NEAR)
        }
        // Flash sits above everything: lightning lights the whole scene, not one layer.
        if (condition == WeatherCondition.THUNDERSTORM) {
            Canvas(Modifier.fillMaxSize()) { drawFlash(slow) }
        }
    }
}

private enum class Depth(val scale: Float, val speed: Float, val alpha: Float) {
    FAR(1.9f, 0.45f, 0.30f),
    MID(1.25f, 0.85f, 0.55f),
    NEAR(0.8f, 1.5f, 0.95f),
}

// ── Particle field ───────────────────────────────────────────────────────────────

/** Seeded so the same condition always looks the same; a reshuffling scene reads as a bug. */
private class Field(condition: WeatherCondition) {
    private val rng = Random(condition.ordinal * 7919 + 13)
    val drops = List(150) { P(rng) }
    val flakes = List(110) { P(rng) }
    val motes = List(160) { P(rng) }
    val clouds = List(9) { P(rng) }
    val stars = List(90) { P(rng) }

    fun forDepth(list: List<P>, d: Depth): List<P> = when (d) {
        Depth.FAR -> list.filterIndexed { i, _ -> i % 3 == 0 }
        Depth.MID -> list.filterIndexed { i, _ -> i % 3 == 1 }
        Depth.NEAR -> list.filterIndexed { i, _ -> i % 3 == 2 }
    }
}

private class P(rng: Random) {
    val x = rng.nextFloat()
    val y = rng.nextFloat()
    val phase = rng.nextFloat()
    val speed = 0.65f + rng.nextFloat() * 0.8f
    val scale = 0.55f + rng.nextFloat() * 0.9f
    val tilt = -0.5f + rng.nextFloat()
}

private fun wrap(v: Float) = v - kotlin.math.floor(v)

/**
 * Bounded wind wobble in -1..1, to be ADDED as a small offset — never multiplied into a
 * term that accumulates with the clock.
 *
 * The first version returned a factor around 1.0 and multiplied it into `clock * speed`.
 * Because the factor itself oscillates, the product is not monotonic: when the gust falls
 * faster than the clock rises, the position DECREASES and the rain visibly travels
 * upwards. Gusts belong on velocity or on a bounded lateral offset, not on integrated
 * position.
 */
private fun wind(slow: Float, phase: Float) =
    0.7f * sin((slow + phase) * 2f * PI.toFloat()) +
        0.3f * sin((slow * 3.1f + phase) * 2f * PI.toFloat())

// ── Dispatch ─────────────────────────────────────────────────────────────────────

private fun DrawScope.drawLayer(
    condition: WeatherCondition,
    isDay: Boolean,
    f: Field,
    clock: Float,
    slow: Float,
    d: Depth,
) {
    when (condition) {
        WeatherCondition.CLEAR ->
            if (isDay) sun(clock, slow, d) else stars(f, clock, d)

        WeatherCondition.PARTLY_CLOUDY -> {
            if (isDay) sun(clock, slow, d) else stars(f, clock, d)
            clouds(f, clock, slow, d, tint = cloudWhite, density = 0.55f)
        }

        WeatherCondition.CLOUDY ->
            clouds(f, clock, slow, d, tint = cloudGrey, density = 1f)

        WeatherCondition.FOG -> fog(clock, slow, d)

        WeatherCondition.DRIZZLE -> {
            clouds(f, clock, slow, d, tint = cloudGrey, density = 0.7f)
            rain(f, clock, slow, d, length = 0.030f, width = 1.4f, alpha = 0.45f)
        }

        WeatherCondition.RAIN -> {
            clouds(f, clock, slow, d, tint = cloudStorm, density = 1f)
            rain(f, clock, slow, d, length = 0.075f, width = 2.2f, alpha = 0.70f)
        }

        WeatherCondition.SNOW -> {
            clouds(f, clock, slow, d, tint = cloudWhite, density = 0.6f)
            snow(f, clock, slow, d)
        }

        WeatherCondition.THUNDERSTORM -> {
            clouds(f, clock, slow, d, tint = cloudStorm, density = 1.2f)
            rain(f, clock, slow, d, length = 0.090f, width = 2.4f, alpha = 0.70f)
            if (d == Depth.MID) bolt(slow)
        }

        WeatherCondition.DUST -> dust(f, clock, slow, d)

        WeatherCondition.UNKNOWN -> Unit
    }
}

// ── Palette ──────────────────────────────────────────────────────────────────────

private val cloudWhite = Color(0xFFEDF3FA)
private val cloudGrey = Color(0xFFA8B4C4)
private val cloudStorm = Color(0xFF6C7787)

/**
 * Sky wash behind everything, breathing slightly.
 *
 * Without this the particles sat on flat black and looked like sprites on a background;
 * with it they sit inside a sky.
 */
private fun DrawScope.drawAtmosphere(condition: WeatherCondition, isDay: Boolean, slow: Float) {
    val breathe = 0.9f + 0.1f * sin(slow * 2f * PI.toFloat())
    val (top, bottom) = when {
        condition == WeatherCondition.DUST -> Color(0x59C08A3E) to Color(0x1F6B4A22)
        condition == WeatherCondition.THUNDERSTORM -> Color(0x593B3054) to Color(0x14161226)
        condition == WeatherCondition.RAIN ||
            condition == WeatherCondition.DRIZZLE -> Color(0x4D2E4459) to Color(0x14121C26)
        condition == WeatherCondition.SNOW -> Color(0x4D8FA9BF) to Color(0x1A2B3A47)
        condition == WeatherCondition.FOG -> Color(0x4D8A97A6) to Color(0x1A2A3138)
        condition == WeatherCondition.CLOUDY -> Color(0x40515E6E) to Color(0x14191F26)
        !isDay -> Color(0x4D16233D) to Color(0x0D0A0F1A)
        else -> Color(0x402E6BA8) to Color(0x0D123049)
    }
    drawRect(
        brush = Brush.verticalGradient(
            listOf(top.copy(alpha = top.alpha * breathe), bottom),
        ),
    )
}

// ── Effects ──────────────────────────────────────────────────────────────────────

private fun DrawScope.sun(clock: Float, slow: Float, d: Depth) {
    val centre = Offset(size.width * 0.78f, size.height * 0.15f)
    val r = size.minDimension * 0.26f * d.scale
    val pulse = 0.94f + 0.06f * sin(slow * 2f * PI.toFloat())

    when (d) {
        // Wide bloom, heavily blurred: this is the layer that makes it feel like light.
        Depth.FAR -> drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x59FFD98A), Color(0x1FFFC46B), Color(0x00FFB347)),
                center = centre, radius = r * 1.5f * pulse,
            ),
            radius = r * 1.5f * pulse, center = centre,
        )
        // Volumetric rays.
        Depth.MID -> rotate(clock * 360f, pivot = centre) {
            repeat(20) { i ->
                val a = (i / 20f) * 2f * PI.toFloat()
                val len = r * (0.85f + 0.25f * sin(clock * 6f * PI.toFloat() + i))
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(Color(0x4DFFE9AE), Color(0x00FFE9AE)),
                        start = centre + Offset(cos(a) * r * 0.35f, sin(a) * r * 0.35f),
                        end = centre + Offset(cos(a) * len, sin(a) * len),
                    ),
                    start = centre + Offset(cos(a) * r * 0.35f, sin(a) * r * 0.35f),
                    end = centre + Offset(cos(a) * len, sin(a) * len),
                    strokeWidth = 7f, cap = StrokeCap.Round,
                )
            }
        }
        // Core, with a soft edge rather than a hard disc.
        Depth.NEAR -> drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xCCFFF6DC), Color(0x99FFE3A3), Color(0x00FFD98A)),
                center = centre, radius = r * 0.55f * pulse,
            ),
            radius = r * 0.55f * pulse, center = centre,
        )
    }
}

private fun DrawScope.stars(f: Field, clock: Float, d: Depth) {
    f.forDepth(f.stars, d).forEach { p ->
        val tw = 0.25f + 0.75f * (0.5f + 0.5f * sin((clock + p.phase) * 4f * PI.toFloat()))
        val r = (1.2f + 2.2f * p.scale) * (2f - d.scale)
        val c = Offset(p.x * size.width, p.y * size.height * 0.72f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.85f * tw * d.alpha),
                    Color.White.copy(alpha = 0f),
                ),
                center = c, radius = r,
            ),
            radius = r, center = c,
        )
    }
}

/** Multi-lobe path. A cloud silhouette is what three ovals were failing to be. */
private fun cloudPath(x: Float, y: Float, w: Float, h: Float): Path = Path().apply {
    val lobes = listOf(
        Triple(0.00f, 0.42f, 0.30f),
        Triple(0.18f, 0.16f, 0.40f),
        Triple(0.44f, 0.06f, 0.46f),
        Triple(0.68f, 0.20f, 0.36f),
        Triple(0.84f, 0.44f, 0.26f),
    )
    lobes.forEach { (lx, ly, lr) ->
        addOval(
            androidx.compose.ui.geometry.Rect(
                offset = Offset(x + w * lx, y + h * ly),
                size = Size(w * lr * 1.35f, h * lr * 2.0f),
            )
        )
    }
    // Flat base: clouds have a floor, and without it they look like popcorn.
    addOval(
        androidx.compose.ui.geometry.Rect(
            offset = Offset(x + w * 0.05f, y + h * 0.46f),
            size = Size(w * 0.9f, h * 0.62f),
        )
    )
}

private fun DrawScope.clouds(
    f: Field, clock: Float, slow: Float, d: Depth, tint: Color, density: Float,
) {
    val list = f.forDepth(f.clouds, d)
    list.forEachIndexed { i, p ->
        if (i.toFloat() / list.size > density.coerceAtMost(1f)) return@forEachIndexed
        val drift = clock * 0.10f * p.speed * d.speed
        val x = (wrap(p.x + drift) * 1.5f - 0.25f) * size.width +
            wind(slow, p.phase) * size.width * 0.015f
        val y = size.height * (0.02f + 0.22f * p.y)
        val w = size.width * (0.42f + 0.28f * p.scale) * d.scale * 0.75f
        val h = w * 0.42f
        val a = (0.34f * d.alpha * (0.75f + 0.25f * p.scale)).coerceAtMost(0.5f)
        drawPath(
            path = cloudPath(x, y, w, h),
            brush = Brush.verticalGradient(
                listOf(tint.copy(alpha = a), tint.copy(alpha = a * 0.45f)),
                startY = y, endY = y + h * 1.1f,
            ),
        )
    }
}

private fun DrawScope.rain(
    f: Field, clock: Float, slow: Float, d: Depth,
    length: Float, width: Float, alpha: Float,
) {
    val w = wind(slow, 0.2f)
    f.forDepth(f.drops, d).forEach { p ->
        // Monotonic: no wind term here, or the drops reverse direction.
        val fall = clock * 2.4f * p.speed * d.speed
        val y = wrap(p.y + fall) * (size.height * 1.2f) - size.height * 0.1f
        // Wind is a bounded lateral OFFSET, so gusting never rewinds the fall.
        val x = wrap(p.x + clock * 0.05f) * size.width + w * size.width * 0.02f
        val len = size.height * length * p.scale * d.scale
        val lean = len * (0.26f + p.tilt * 0.10f + w * 0.12f)
        val start = Offset(x, y)
        val end = Offset(x - lean, y + len)
        // Gradient along the streak + round cap: a raindrop is a smear of light, not a
        // rectangle.
        drawLine(
            brush = Brush.linearGradient(
                listOf(
                    Color(0x00CFE4F5),
                    Color(0xFFCFE4F5).copy(alpha = alpha * d.alpha),
                    Color(0x00CFE4F5),
                ),
                start = start, end = end,
            ),
            start = start, end = end,
            strokeWidth = width * d.scale * p.scale,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.snow(f: Field, clock: Float, slow: Float, d: Depth) {
    val w = wind(slow, 0.6f)
    f.forDepth(f.flakes, d).forEach { p ->
        val y = wrap(p.y + clock * 0.6f * p.speed * d.speed) * size.height
        val sway = (sin((clock + p.phase) * 3f * PI.toFloat()) + w * 0.6f) *
            size.width * 0.04f
        val x = wrap(p.x) * size.width + sway
        val r = (2.2f + 2.6f * p.scale) * d.scale
        val c = Offset(x, y)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.9f * d.alpha),
                    Color.White.copy(alpha = 0.35f * d.alpha),
                    Color.White.copy(alpha = 0f),
                ),
                center = c, radius = r,
            ),
            radius = r, center = c,
        )
    }
}

private fun DrawScope.dust(f: Field, clock: Float, slow: Float, d: Depth) {
    val w = wind(slow, 0.35f)
    // Rolling haze sheets: a dust storm is a wall of air, not just particles in it.
    repeat(3) { i ->
        val x = (wrap(i * 0.33f + clock * 0.22f * d.speed) * 1.6f - 0.3f) * size.width +
            w * size.width * 0.02f
        val y = size.height * (0.10f + 0.30f * i)
        drawOval(
            brush = Brush.radialGradient(
                listOf(Color(0x47D2A75C), Color(0x00D2A75C)),
                center = Offset(x + size.width * 0.35f, y + size.height * 0.14f),
                radius = size.width * 0.5f,
            ),
            topLeft = Offset(x, y),
            size = Size(size.width * 0.7f, size.height * 0.28f),
        )
    }
    f.forDepth(f.motes, d).forEach { p ->
        val x = wrap(p.x + clock * 1.5f * p.speed * d.speed) * size.width +
            w * size.width * 0.015f
        val y = (p.y + 0.02f * sin((clock + p.phase) * 2f * PI.toFloat())) * size.height
        val r = (1.8f + 2.4f * p.scale) * d.scale
        val c = Offset(x, y)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color(0xFFE8CB96).copy(alpha = 0.75f * d.alpha),
                    Color(0x00E8CB96),
                ),
                center = c, radius = r,
            ),
            radius = r, center = c,
        )
    }
}

private fun DrawScope.fog(clock: Float, slow: Float, d: Depth) {
    val w = wind(slow, 0.8f)
    repeat(4) { i ->
        val speed = (0.05f + i * 0.035f) * d.speed
        val y = size.height * (0.12f + i * 0.21f)
        val x = (wrap(i * 0.27f + clock * speed) * 1.7f - 0.35f) * size.width +
            w * size.width * 0.01f
        val h = size.height * 0.22f * d.scale
        drawOval(
            brush = Brush.radialGradient(
                listOf(
                    Color(0xFFD6DEE8).copy(alpha = 0.22f * d.alpha),
                    Color(0x00D6DEE8),
                ),
                center = Offset(x + size.width * 0.45f, y + h * 0.5f),
                radius = size.width * 0.5f,
            ),
            topLeft = Offset(x, y),
            size = Size(size.width * 0.9f, h),
        )
    }
}

/** Branching bolt, drawn only while a flash is active. */
private fun DrawScope.bolt(slow: Float) {
    val intensity = flash(slow)
    if (intensity < 0.35f) return
    val originX = size.width * 0.62f
    val path = Path().apply {
        moveTo(originX, size.height * 0.10f)
        lineTo(originX - size.width * 0.05f, size.height * 0.24f)
        lineTo(originX + size.width * 0.03f, size.height * 0.26f)
        lineTo(originX - size.width * 0.07f, size.height * 0.44f)
        lineTo(originX + size.width * 0.01f, size.height * 0.42f)
        lineTo(originX - size.width * 0.02f, size.height * 0.58f)
    }
    // Glow pass then core: a bolt without a glow looks like a scribble.
    drawPath(path, color = Color(0xFFBFA8FF).copy(alpha = 0.35f * intensity), style =
        androidx.compose.ui.graphics.drawscope.Stroke(width = 16f, cap = StrokeCap.Round))
    drawPath(path, color = Color.White.copy(alpha = 0.9f * intensity), style =
        androidx.compose.ui.graphics.drawscope.Stroke(width = 4f, cap = StrokeCap.Round))
}

private fun DrawScope.drawFlash(slow: Float) {
    val i = flash(slow)
    if (i <= 0.01f) return
    // Brightest at the top, where the cloud is: a uniform white wash looks like a bug.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.30f * i),
                Color.White.copy(alpha = 0.04f * i),
            ),
        ),
    )
}

/** Two strikes per slow cycle with fast decay, at irregular points so it is not rhythmic. */
private fun flash(slow: Float): Float {
    fun at(t: Float, w: Float): Float {
        val d = kotlin.math.abs(slow - t)
        return if (d > w) 0f else (1f - d / w).let { it * it }
    }
    return (at(0.17f, 0.030f) + at(0.21f, 0.016f) + at(0.68f, 0.026f)).coerceAtMost(1f)
}

private const val CYCLE_MS = 14_000
private const val SLOW_CYCLE_MS = 11_000
private val FAR_BLUR = 26.dp
private val MID_BLUR = 10.dp
