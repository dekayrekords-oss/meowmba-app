package com.example.meowmba.ui.desk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.meowmba.ui.desk.DeskPainter.INK
import com.example.meowmba.ui.desk.DeskPainter.line
import com.example.meowmba.ui.desk.DeskPainter.rrect
import com.example.meowmba.ui.desk.DeskPainter.text
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/** What the office monitor shows besides its usual lines. */
internal enum class DeskMonitor { NORMAL, BROWSER, KEYS }

/** A plane (or, very rarely, a UFO) crossing one of the windows. */
internal data class DeskFlyer(val ufo: Boolean, val bedroom: Boolean, val start: Float, val leftToRight: Boolean, val lane: Float) {
    val duration: Float get() = if (ufo) 9f else 11f
}

/**
 * The room's own slow life, apart from the cat: drifting clouds and weather, a blinking cursor,
 * the sticky note lifting in a draught, the lamp swinging when the cat bats it, a game on the old
 * TV, a bird on the sill, and the rare plane or UFO. [t] (seconds) advances only while the Desk
 * is on screen and the phone is cool, at a low frame rate; otherwise everything holds still.
 */
internal class DeskAmbient {
    var t by mutableFloatStateOf(0f)
    var flyer by mutableStateOf<DeskFlyer?>(null)
    var lampPushAt by mutableFloatStateOf(-100f)
    var toyPushAt by mutableFloatStateOf(-100f)
    var mothSwatAt by mutableFloatStateOf(-100f)
    var flapAt by mutableFloatStateOf(-100f)
    var birdUntil by mutableFloatStateOf(-1f)
    var monitor by mutableStateOf(DeskMonitor.NORMAL)
    var monitorSince by mutableFloatStateOf(0f)

    fun lampSwing(): Float = swing(t - lampPushAt, 12f)
    fun toySwing(): Float = swing(t - toyPushAt, 28f)

    /** A damped swing after a push [dt] seconds ago, in degrees. */
    private fun swing(dt: Float, amplitude: Float): Float =
        if (dt < 0f || dt > 6f) 0f else amplitude * exp(-dt / 1.5f) * sin(dt * 5.2f)

    /** 0…1…0 over the sticky note's lift. */
    fun flap(): Float { val dt = t - flapAt; return if (dt < 0f || dt > 1.6f) 0f else sin(dt / 1.6f * PI.toFloat()) }

    fun spawnFlyer(ufo: Boolean, bedroom: Boolean, leftToRight: Boolean, lane: Float) {
        flyer = DeskFlyer(ufo, bedroom, t, leftToRight, lane)
    }

    companion object {
        /** Frames per second for the room's own motion: low, for heat. */
        const val FPS = 8
        /** Average minutes between planes while the Desk is on screen ("very very very rarely"). */
        const val PLANE_EVERY_MIN = 25f
        /** One flight in this many is a UFO. */
        const val UFO_ONE_IN = 30
    }
}

internal enum class DeskWeather { CLEAR, PARTLY, CLOUDY, RAIN, STORM, SNOW, FOG }

/** The sky follows the real time of day and the weather line (e.g. "79° cloudy"). */
internal object DeskSky {
    fun weather(line: String?): DeskWeather {
        val w = line?.lowercase().orEmpty()
        return when {
            "thunder" in w || "storm" in w -> DeskWeather.STORM
            "snow" in w || "sleet" in w || "flurr" in w -> DeskWeather.SNOW
            "rain" in w || "drizzle" in w || "shower" in w -> DeskWeather.RAIN
            "fog" in w || "mist" in w || "haze" in w -> DeskWeather.FOG
            "overcast" in w || "cloudy" in w && "partly" !in w -> DeskWeather.CLOUDY
            "partly" in w || "cloud" in w -> DeskWeather.PARTLY
            else -> DeskWeather.CLEAR
        }
    }

    private fun hours(s: DeskState) = s.clockHour + s.clockMinute / 60f

    /** Top and bottom of the sky: dawn, morning, day, golden hour, sunset, dusk or night, dulled by weather. */
    fun colors(s: DeskState): Pair<Color, Color> {
        val h = hours(s)
        val base = when {
            s.night -> if (h in 18f..21.5f) Color(0xFF1C2650) to Color(0xFF5E4A78) else Color(0xFF0D2130) to Color(0xFF2A4E61)
            h < 6.5f -> Color(0xFF34406E) to Color(0xFFF2B8B5)
            h < 9f -> Color(0xFF7FB2D0) to Color(0xFFFFE7C4)
            h < 16f -> Color(0xFF5E9FC8) to Color(0xFFCFE4EC)
            h < 18.5f -> Color(0xFF6A98C2) to Color(0xFFFFD49A)
            else -> Color(0xFF4A3F7A) to Color(0xFFFF9E7A)
        }
        val dull = when (weather(s.weatherLine)) { DeskWeather.CLOUDY, DeskWeather.SNOW -> .5f; DeskWeather.RAIN, DeskWeather.FOG -> .6f; DeskWeather.STORM -> .72f; else -> 0f }
        if (dull == 0f) return base
        val grey = if (s.night) Color(0xFF1E2830) to Color(0xFF34404A) else Color(0xFF8C9AA6) to Color(0xFFC3CBD1)
        return lerp(base.first, grey.first, dull) to lerp(base.second, grey.second, dull)
    }

    /** Where the sun (day) or moon (night) is in the office window, rising and setting through the day. */
    fun bodyIn(s: DeskState): Offset {
        val h = hours(s)
        val f = if (s.night) (((h - 19f + 24f) % 24f) / 11f).coerceIn(0f, 1f) else ((h - 6f) / 13f).coerceIn(0f, 1f)
        return Offset(254f + f * 116f, 172f - sin(f * PI.toFloat()) * 92f)
    }

    /** Centre of the sunbeam on the bedroom floor, or null when there's no sun to make one. */
    fun sunbeamX(s: DeskState): Float? {
        if (s.night || weather(s.weatherLine) !in setOf(DeskWeather.CLEAR, DeskWeather.PARTLY)) return null
        return (632f + (12.5f - hours(s)) * 16f).coerceIn(480f, 790f)
    }
}

internal object DeskAmbientPainter {
    private val SANS = FontFamily.SansSerif
    private val MONO = FontFamily.Monospace

    /** Everything that moves in the room layer, drawn above the objects and below the cat. */
    fun DrawScope.drawAmbient(s: DeskState, a: DeskAmbient, m: TextMeasurer, layout: DeskLayout, gamePlaying: Boolean) {
        val weather = DeskSky.weather(s.weatherLine)
        // Office window, then the bedroom's big window.
        window(a, s, weather, bedroom = false, Offset(238f, 66f), Size(148f, 176f), 3f, 312f, 154f, scale = .72f)
        window(a, s, weather, bedroom = true, Offset(522f, 90f), Size(220f, 290f), 3.5f, 632f, 236f, scale = 1f)
        if (a.t < a.birdUntil && !s.night) bird(700f, 380f, a.t)
        monitor(s, a, m)
        sticky(s, m, a.flap())
        lamp(s, a)
        toy(layout, a.toySwing())
        arcade(a.t, m, gamePlaying)
    }

    // ---- windows ------------------------------------------------------------------------------

    private fun DrawScope.window(a: DeskAmbient, s: DeskState, weather: DeskWeather, bedroom: Boolean, at: Offset, size: Size,
                                 frame: Float, mullionX: Float, mullionY: Float, scale: Float) {
        val t = a.t
        clipRect(at.x, at.y, at.x + size.width, at.y + size.height) {
            val count = when (weather) { DeskWeather.CLEAR -> 2; DeskWeather.PARTLY, DeskWeather.FOG -> 3; DeskWeather.SNOW -> 4; else -> 5 }
            val (body, rim) = when {
                s.night -> Color(0xFF3A5068) to Color(0xFF6C84A0)
                weather in setOf(DeskWeather.RAIN, DeskWeather.STORM) -> Color(0xFF7C8894) to null
                weather == DeskWeather.CLOUDY || weather == DeskWeather.SNOW -> Color(0xFFD6DCE0) to null
                else -> Color(0xF2FFFFFF) to null
            }
            val skyTop = at.y + 10f
            val band = if (bedroom) 110f else 70f
            for (i in 0 until count) {
                val w = (38f + (i * 23 % 34)) * scale * 1.3f
                val speed = 1.6f + (i % 3) * .9f
                val span = size.width + w * 1.4f
                val x = at.x - w * .7f + (((i * .618f + if (bedroom) .3f else .7f) % 1f) * span + t * speed) % span
                val y = skyTop + 14f + (i * 37 % band.toInt()).toFloat()
                cloud(x, y, w, body, rim)
            }
            a.flyer?.takeIf { it.bedroom == bedroom }?.let { flyer(it, t, at, size, s.night) }
            when (weather) {
                DeskWeather.RAIN, DeskWeather.STORM -> for (i in 0 until 16) {
                    val x = at.x + ((i * 53) % size.width.toInt()) + (i % 3) * 4f
                    val y = at.y + ((i * 71f + t * 190f) % (size.height + 20f)) - 20f
                    line(Color(0x99C9DCEB), x, y, x - 3f, y + 12f, 1.2f)
                }
                DeskWeather.SNOW -> for (i in 0 until 18) {
                    val x = at.x + ((i * 47) % size.width.toInt()) + sin(t * 1.3f + i) * 5f
                    val y = at.y + ((i * 61f + t * 22f) % size.height)
                    drawCircle(Color(0xE6FFFFFF), 1.6f, Offset(x, y))
                }
                DeskWeather.FOG -> drawRect(Color(0x55DDE3E6), at, size)
                else -> Unit
            }
            // A storm lights the sky now and then.
            if (weather == DeskWeather.STORM) {
                val beat = floor(t / 6f)
                if (((beat * 7919f) % 5f) < 1f && (t % 6f) < .25f) drawRect(Color(0x88FFFFFF), at, size)
            }
        }
        line(INK, mullionX, at.y, mullionX, at.y + size.height, frame)
        line(INK, at.x, mullionY, at.x + size.width, mullionY, frame)
        drawRoundRect(INK, at, size, CornerRadius(6f), style = Stroke(frame))
    }

    /** A puffy cloud [w] wide at ([x], [y]); [rim] lights its top edge (moonlight at night). */
    private fun DrawScope.cloud(x: Float, y: Float, w: Float, body: Color, rim: Color?) {
        val puffs = listOf(Triple(-.30f, .06f, .22f), Triple(-.08f, -.12f, .30f), Triple(.20f, -.04f, .24f), Triple(.36f, .08f, .16f))
        rim?.let { c -> puffs.forEach { (dx, dy, r) -> drawCircle(c, w * r + 1.5f, Offset(x + dx * w, y + dy * w - 1.5f)) } }
        puffs.forEach { (dx, dy, r) -> drawCircle(body, w * r, Offset(x + dx * w, y + dy * w)) }
        drawRoundRect(body, Offset(x - w * .5f, y), Size(w, w * .2f), CornerRadius(w * .1f))
    }

    private fun DrawScope.flyer(f: DeskFlyer, t: Float, at: Offset, size: Size, night: Boolean) {
        val p = ((t - f.start) / f.duration).coerceIn(0f, 1f)
        val y = at.y + 18f + f.lane * (size.height * .35f)
        if (!f.ufo) {
            val x = if (f.leftToRight) at.x - 30f + p * (size.width + 60f) else at.x + size.width + 30f - p * (size.width + 60f)
            val dir = if (f.leftToRight) 1f else -1f
            if (!night) line(Color(0x88FFFFFF), x - dir * 70f, y + 1f, x - dir * 10f, y + 1f, 1.6f)   // contrail
            withTransform({ if (!f.leftToRight) scale(-1f, 1f, Offset(x, y)) }) {
                val c = if (night) Color(0xFF1A2530) else Color(0xFFEEF2F5)
                drawRoundRect(c, Offset(x - 11f, y - 2f), Size(22f, 4.5f), CornerRadius(2.2f))
                drawPath(Path().apply { moveTo(x - 2f, y); lineTo(x - 8f, y + 7f); lineTo(x - 4f, y + 7f); lineTo(x + 3f, y); close() }, c)
                drawPath(Path().apply { moveTo(x - 9f, y - 1f); lineTo(x - 13f, y - 6f); lineTo(x - 10f, y - 6f); lineTo(x - 6f, y - 1f); close() }, c)
            }
            if (night && (t * 2f).toInt() % 2 == 0) { drawCircle(Color(0xFFFF4D4D), 1.4f, Offset(x - 4f, y + 6f)); drawCircle(Color(0xFF7BE06A), 1.2f, Offset(x + 9f, y)) }
            return
        }
        // UFO: glides in, hovers with a tractor beam, then zips away up and out.
        val cx = at.x + size.width / 2
        val start = if (f.leftToRight) at.x - 30f else at.x + size.width + 30f
        val (x, yy) = when {
            p < .3f -> (start + (cx - start) * (p / .3f)) to y + sin(p * 20f) * 2f
            p < .75f -> (cx + sin(t * 2.2f) * 6f) to y + sin(t * 3f) * 3f
            else -> { val q = (p - .75f) / .25f; (cx + (if (f.leftToRight) 1f else -1f) * q * q * (size.width)) to y - q * q * 60f }
        }
        if (p in .38f..0.7f) {
            val beam = Path().apply { moveTo(x - 5f, yy + 3f); lineTo(x + 5f, yy + 3f); lineTo(x + 22f, at.y + size.height); lineTo(x - 22f, at.y + size.height); close() }
            drawPath(beam, Brush.verticalGradient(listOf(Color(0x807BE06A), Color(0x107BE06A)), yy, at.y + size.height))
        }
        drawOval(Color(0xFF8FE8FF), Offset(x - 6f, yy - 7f), Size(12f, 9f)); drawOval(INK, Offset(x - 6f, yy - 7f), Size(12f, 9f), style = Stroke(1f))
        drawOval(Color(0xFFB8C4CC), Offset(x - 15f, yy - 2.5f), Size(30f, 8f)); drawOval(INK, Offset(x - 15f, yy - 2.5f), Size(30f, 8f), style = Stroke(1.2f))
        val lights = listOf(Color(0xFFFFE27A), Color(0xFFFF7B8F), Color(0xFF7BE06A))
        for (k in 0..3) drawCircle(lights[((t * 6f).toInt() + k) % 3], 1.3f, Offset(x - 9f + k * 6f, yy + 1.5f))
    }

    private fun DrawScope.bird(x: Float, sill: Float, t: Float) {
        val hop = if ((t % 1.3f) < .18f) -3f else 0f
        val y = sill - 6f + hop
        line(Color(0xFF6B4A2B), x - 1f, y + 4f, x - 1f, sill, 1f); line(Color(0xFF6B4A2B), x + 2f, y + 4f, x + 2f, sill, 1f)
        drawOval(Color(0xFF5E7A9C), Offset(x - 6f, y - 4f), Size(13f, 9f)); drawOval(INK, Offset(x - 6f, y - 4f), Size(13f, 9f), style = Stroke(1f))
        drawOval(Color(0xFFE08C84), Offset(x - 3f, y - 1f), Size(6f, 5f))                          // red breast
        drawCircle(Color(0xFF5E7A9C), 3.6f, Offset(x - 6f, y - 5f)); drawCircle(INK, 3.6f, Offset(x - 6f, y - 5f), style = Stroke(1f))
        drawCircle(INK, .8f, Offset(x - 7f, y - 6f))
        drawPath(Path().apply { moveTo(x - 9.5f, y - 5.5f); lineTo(x - 13f, y - 4.5f); lineTo(x - 9.5f, y - 3.5f); close() }, Color(0xFFE0B66A))
        line(Color(0xFF3F5A78), x + 6f, y, x + 11f, y - 3f, 2f)                                   // tail
    }

    // ---- office ------------------------------------------------------------------------------

    /** The monitor's blinking cursor, or the "research" browser, or a screen full of paw-typed f's. */
    private fun DrawScope.monitor(s: DeskState, a: DeskAmbient, m: TextMeasurer) {
        when (a.monitor) {
            DeskMonitor.NORMAL -> {
                // Blinks about once a second; holds steady (visible) when the room is still.
                if (floor(a.t * 1.6f).toInt() % 2 == 0) drawRect(Color(0xFFC6EB78), Offset(44f, 444f + s.monitorLines.size * 18f - 8f), Size(7f, 11f))
            }
            DeskMonitor.BROWSER -> {
                drawRoundRect(Color(0xFFF4F1E8), Offset(36f, 422f), Size(148f, 112f), CornerRadius(8f))
                drawRect(Color(0xFFD9D2C0), Offset(40f, 426f), Size(140f, 12f))
                listOf(Color(0xFFE5484D), Color(0xFFE0B66A), Color(0xFF7BE06A)).forEachIndexed { i, c -> drawCircle(c, 2.2f, Offset(46f + i * 7f, 432f)) }
                rrect(Color.White, 42f, 442f, 136f, 15f, 7f, 1f)
                val query = "ideas for " + s.projectTitle.ifBlank { "you" }
                val typed = ((a.t - a.monitorSince) * 7f).toInt().coerceIn(0, query.length)
                text(m, "⌕ " + query.take(typed).take(24), 47f, 453f, 8.5f, INK, family = SANS)
                // Results scroll by as the cat reads.
                val scroll = ((a.t - a.monitorSince) * 6f) % 40f
                clipRect(38f, 460f, 182f, 532f) {
                    for (r in 0..3) {
                        val y = 466f + r * 20f - scroll
                        line(Color(0xFF1F4FBF), 44f, y, 44f + 70f + (r * 29 % 50), y, 3f)
                        line(Color(0xFFB8B2A2), 44f, y + 6f, 170f, y + 6f, 2f); line(Color(0xFFB8B2A2), 44f, y + 11f, 44f + 60f + (r * 41 % 60), y + 11f, 2f)
                    }
                }
            }
            DeskMonitor.KEYS -> {
                drawRoundRect(Color(0xFF0C1A1F), Offset(36f, 422f), Size(148f, 112f), CornerRadius(8f))
                val n = ((a.t - a.monitorSince) * 9f).toInt().coerceIn(0, 90)
                val keys = "fffffffffffjjjjjj;;;;;;;;ggggggggggggggggggggg7777777777777777ffffffffffff;;;;;;;;;;;;;;;;;;;;;;;;;"
                keys.take(n).chunked(18).take(5).forEachIndexed { i, l -> text(m, l, 44f, 444f + i * 18f, 11f, Color(0xFFC6EB78), family = MONO) }
            }
        }
    }

    /** The sticky note (next calendar item). Now and then its bottom lifts in a draught. */
    private fun DrawScope.sticky(s: DeskState, m: TextMeasurer, flap: Float) {
        withTransform({
            rotate(5f + flap * 3f, Offset(234f, 418f))
            scale(1f + flap * .02f, 1f - flap * .22f, Offset(234f, 418f))
        }) {
            if (flap > 0f) drawRect(Color(0x33000000), Offset(209f, 424f), Size(58f, 56f * (1f + flap * .2f)))
            drawRect(Color(0xFFFFE27A), Offset(206f, 418f), Size(58f, 56f)); drawRect(INK, Offset(206f, 418f), Size(58f, 56f), style = Stroke(2.5f))
            // The lifted edge catches a lighter, curled strip.
            if (flap > 0f) drawRect(Color(0xFFFFF0A8), Offset(206f, 474f - 8f * flap), Size(58f, 8f * flap))
            s.sticky.ifEmpty { listOf("Next up", "Tap for tasks") }.forEachIndexed { i, l -> text(m, l.take(9), 211f, 437f + i * 15f, if (i == 0) 15f else 12.5f, INK) }
        }
    }

    /**
     * The swing-arm wall lamp. It's on at night, and glows by day when Meow is keeping an idea for
     * you (with a little spark). It swings when the cat bats it; a moth visits it at night.
     */
    private fun DrawScope.lamp(s: DeskState, a: DeskAmbient) {
        rrect(Color(0xFF5E7A76), 374f, 318f, 16f, 26f, 3f, 2f)
        val on = s.night || s.lampOn
        rotate(a.lampSwing(), Offset(382f, 331f)) {
            if (on) {
                val glow = Path().apply { moveTo(306f, 470f); lineTo(206f, 598f); lineTo(368f, 598f); lineTo(346f, 452f); close() }
                drawPath(glow, Brush.radialGradient(listOf(Color(if (s.night) 0x8CFFD98A else 0x73FFD98A), Color(0x00FFD98A)), Offset(326f, 464f), 160f))
            }
            val arm = Path().apply { moveTo(382f, 331f); lineTo(360f, 384f); lineTo(334f, 436f) }
            drawPath(arm, INK, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(arm, Color(0xFF5E7A76), style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Color(0xFF5E7A76), 4f, Offset(360f, 384f)); drawCircle(INK, 4f, Offset(360f, 384f), style = Stroke(2f))
            val shade = Path().apply { moveTo(302f, 468f); quadraticTo(318f, 430f, 354f, 440f); lineTo(352f, 450f); lineTo(310f, 472f); close() }
            drawPath(shade, Color(0xFFC6EB78)); drawPath(shade, INK, style = Stroke(2.5f, join = StrokeJoin.Round))
            drawOval(if (on) Color(0xFFFFF3C4) else Color(0xFFD5CDB4), Offset(318f, 458f), Size(18f, 8f)); drawOval(INK, Offset(318f, 458f), Size(18f, 8f), style = Stroke(1.5f))
            if (s.lampOn) {
                val tw = .7f + .3f * sin(a.t * 3f)
                spark(Offset(300f, 430f), 5f * tw)
            }
            if (s.night) moth(a)
        }
    }

    private fun DrawScope.spark(c: Offset, r: Float) {
        val p = Path().apply {
            moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
            quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
        }
        drawPath(p, Color(0xFFFFE27A))
    }

    private fun DrawScope.moth(a: DeskAmbient) {
        val swat = a.t - a.mothSwatAt
        val dart = if (swat in 0f..1.2f) sin(swat / 1.2f * PI.toFloat()) * 40f else 0f
        val x = 327f + cos(a.t * 2.3f) * 20f + dart
        val y = 474f + sin(a.t * 3.1f) * 12f - dart * .6f
        val flap = abs(sin(a.t * 17f))
        drawOval(Color(0xFFE9E1C8), Offset(x - 5f, y - 2f - flap * 2f), Size(5f, 3f + flap * 3f))
        drawOval(Color(0xFFE9E1C8), Offset(x, y - 2f - flap * 2f), Size(5f, 3f + flap * 3f))
        drawOval(Color(0xFF8C7A5A), Offset(x - 1.2f, y - 1.5f), Size(2.4f, 4f))
    }

    // ---- living room -------------------------------------------------------------------------

    /** The mouse toy dangling from the living-room tower's top perch; it swings when batted. */
    private fun DrawScope.toy(layout: DeskLayout, swing: Float) {
        val t = layout.living.towerX
        rotate(swing, Offset(t - 36f, 330f)) {
            line(INK, t - 36f, 330f, t - 36f, 364f, 1f)
            drawOval(Color(0xFF9B9B9B), Offset(t - 42f, 362f), Size(12f, 8f)); line(Color(0xFFF2B8C8), t - 42f, 366f, t - 48f, 370f, 1.2f)
        }
    }

    // ---- bedroom -----------------------------------------------------------------------------

    /**
     * The old TV plays a little space game: invaders march, the ship dodges and fires. When the
     * cat has the controller the score counts up; otherwise "INSERT COIN" blinks.
     */
    private fun DrawScope.arcade(t: Float, m: TextMeasurer, playing: Boolean) {
        drawRoundRect(Color(0xFF10161A), Offset(446f, 538f), Size(70f, 60f), CornerRadius(10f))
        clipRect(448f, 540f, 514f, 596f) {
            val px = Color(0xFF7BE06A)
            val march = (t * 6f) % 16f
            val dx = if (march < 8f) march else 16f - march
            val frame = (t * 3f).toInt() % 2
            val sprite = if (frame == 0) listOf("01100110", "11111111", "10111101", "01000010") else listOf("01100110", "11111111", "10111101", "10000001")
            for (row in 0..1) for (col in 0..3) {
                if (playing && row == 0 && col == ((t / 3f).toInt() % 4)) continue      // one just got shot
                val ox = 452f + col * 14f + dx; val oy = 546f + row * 10f
                sprite.forEachIndexed { r, bits -> bits.forEachIndexed { c, b -> if (b == '1') drawRect(if (row == 0) px else Color(0xFFFFE27A), Offset(ox + c * 1.2f, oy + r * 1.4f), Size(1.2f, 1.4f)) } }
            }
            val ship = 481f + sin(t * 1.7f) * 22f
            drawRect(Color(0xFF81D8CE), Offset(ship - 4f, 586f), Size(8f, 3f)); drawRect(Color(0xFF81D8CE), Offset(ship - 1f, 583f), Size(2f, 3f))
            val shot = (t * 1.4f) % 1f
            drawRect(Color.White, Offset(ship - .5f, 582f - shot * 34f), Size(1f, 3f))
        }
        if (playing) text(m, "1UP " + (100 + (t * 10f).toInt() * 10).toString().padStart(5, '0'), 481f, 544f, 5.5f, Color(0xFFFFE27A), family = MONO, weight = FontWeight.Bold, center = true)
        else if (floor(t * 1.2f).toInt() % 2 == 0) text(m, "INSERT COIN", 481f, 594f, 6.5f, Color(0xFFFFE27A), family = MONO, weight = FontWeight.Bold, center = true)
    }

    // ---- props the cat plays with (drawn with the cat) ------------------------------------------

    fun DrawScope.pen(x: Float, y: Float, rot: Float) = rotate(rot, Offset(x + 17f, y)) {
        drawRoundRect(Color(0xFFFFC56F), Offset(x, y - 2.5f), Size(30f, 5f), CornerRadius(1f))
        drawRoundRect(Color(0xFFF2B8C8), Offset(x - 5f, y - 2.5f), Size(6f, 5f), CornerRadius(1.5f))
        drawPath(Path().apply { moveTo(x + 30f, y - 2.5f); lineTo(x + 36f, y); lineTo(x + 30f, y + 2.5f); close() }, Color(0xFFE3D3A8))
        drawCircle(INK, .9f, Offset(x + 35.4f, y))
        drawRoundRect(INK, Offset(x - 5f, y - 2.5f), Size(41f, 5f), CornerRadius(1.5f), style = Stroke(1f))
    }

    fun DrawScope.yarn(x: Float, home: Float, rot: Float) {
        val y = 731f
        if (abs(x - home) > 4f) {
            val trail = Path().apply { moveTo(x, y + 8f); quadraticTo((x + home) / 2, y + 12f, home, y + 8f) }
            drawPath(trail, Color(0xFFE5484D), style = Stroke(1.3f))
        }
        drawCircle(Color(0xFFE5484D), 9f, Offset(x, y)); drawCircle(INK, 9f, Offset(x, y), style = Stroke(1.8f))
        rotate(rot, Offset(x, y)) {
            for (k in -1..1) drawArc(Color(0xFFB2323A), -60f, 120f, false, Offset(x - 7f + k * 3f, y - 7f), Size(14f, 14f), style = Stroke(1.1f))
        }
    }

    fun DrawScope.toyMouse(x: Float, facingLeft: Boolean) = withTransform({ if (facingLeft) scale(-1f, 1f, Offset(x, 734f)) }) {
        line(Color(0xFFF2B8C8), x - 7f, 736f, x - 16f, 733f, 1.2f)
        drawOval(Color(0xFF9B9B9B), Offset(x - 8f, 728f), Size(15f, 10f)); drawOval(INK, Offset(x - 8f, 728f), Size(15f, 10f), style = Stroke(1.2f))
        drawCircle(Color(0xFFF2B8C8), 2.6f, Offset(x + 1f, 728.5f))
        drawCircle(INK, .8f, Offset(x + 4f, 731.5f)); drawCircle(Color(0xFFF2B8C8), .9f, Offset(x + 7f, 733f))
    }

    /** The keyboard the cat sits on (monitor fills with f's). */
    fun DrawScope.keyboard(x: Float, y: Float) {
        rrect(Color(0xFF527482), x, y, 84f, 14f, 3f, 1.5f)
        for (row in 0..1) for (i in 0..9) rrect(PAPER_KEY, x + 4f + i * 8f, y + 3f + row * 6f, 5f, 3f, 1f)
    }
    private val PAPER_KEY = Color(0xFFF4EEDB)

    /** The cardboard box's front flap, drawn over the cat while it hides inside. */
    fun DrawScope.boxFront() {
        rrect(Color(0xFFC49A62), 296f, 704f, 80f, 36f, 2f, 2.5f)
        line(Color(0xFFA67C48), 300f, 716f, 372f, 716f, 1.2f)
        drawPath(Path().apply { moveTo(296f, 704f); lineTo(288f, 690f); lineTo(330f, 692f); lineTo(336f, 704f); close() }, Color(0xFFD2A870))
        drawPath(Path().apply { moveTo(296f, 704f); lineTo(288f, 690f); lineTo(330f, 692f); lineTo(336f, 704f); close() }, INK, style = Stroke(1.8f, join = StrokeJoin.Round))
    }
}

/** The cardboard box on the office floor ("if it fits, it sits"); drawn once with the room. */
internal fun DrawScope.drawOfficeBox() {
    drawPath(Path().apply { moveTo(300f, 700f); lineTo(306f, 684f); lineTo(372f, 684f); lineTo(376f, 700f); close() }, Color(0xFF9E7640))
    rrect(Color(0xFFC49A62), 296f, 700f, 80f, 40f, 2f, 2.5f)
    line(Color(0xFFA67C48), 300f, 712f, 372f, 712f, 1.2f)
    drawPath(Path().apply { moveTo(376f, 700f); lineTo(388f, 686f); lineTo(372f, 684f); close() }, Color(0xFFD2A870))
    drawPath(Path().apply { moveTo(376f, 700f); lineTo(388f, 686f); lineTo(372f, 684f); close() }, INK, style = Stroke(1.8f, join = StrokeJoin.Round))
    drawPath(Path().apply { moveTo(296f, 700f); lineTo(288f, 688f); lineTo(330f, 690f); lineTo(336f, 700f); close() }, Color(0xFFD2A870))
    drawPath(Path().apply { moveTo(296f, 700f); lineTo(288f, 688f); lineTo(330f, 690f); lineTo(336f, 700f); close() }, INK, style = Stroke(1.8f, join = StrokeJoin.Round))
    drawRect(Color(0x22000000), Offset(298f, 740f), Size(80f, 3f))
}
