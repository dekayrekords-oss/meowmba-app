package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

internal enum class CartoonGagKind(val description: String) {
    JELLY("Spring jelly toy: tap to play"),
    SOCK("Runaway sock toy: tap to play"),
    PANCAKE("Pancake launcher: tap to play"),
    PORTAL("Pocket portal: tap to spin a tiny planet"),
    MOONFLOWER("Sleepy moonflower: tap to wake and play"),
    PERISCOPE("Bubble periscope: tap to blow alien bubbles"),
    ROCKET("Pocket rocket: tap to launch and play");

    companion object {
        fun forBackground(id: String): CartoonGagKind? = when (id) {
            "cartoon_snack_lab" -> JELLY
            "cartoon_laundromat" -> SOCK
            "cartoon_diner" -> PANCAKE
            "cartoon_portal_arcade" -> PORTAL
            "cartoon_moon_garden" -> MOONFLOWER
            "cartoon_alien_aquarium" -> PERISCOPE
            "cartoon_meteor_garage" -> ROCKET
            else -> null
        }
    }
}

internal data class CartoonGagLayout(val center: Offset, val unit: Float) {
    fun contains(point: Offset): Boolean = point.x.isFinite() && point.y.isFinite() &&
        abs(point.x - center.x) <= unit * 1.22f &&
        point.y in (center.y - unit * 1.42f)..(center.y + unit * 1.60f)
}

/** Reserve the lower side of the room, clear of the central avatar and table props.
 * Both dimensions use the short edge so the toy stays round on the Fold in landscape.
 */
internal fun cartoonGagLayout(width: Float, height: Float): CartoonGagLayout? {
    if (!width.isFinite() || !height.isFinite() || width < 1f || height < 1f) return null
    val unit = minOf(width, height) * .064f
    return CartoonGagLayout(Offset(maxOf(width * .125f, unit * 1.8f), height * .67f), unit)
}

internal data class CartoonGagPose(
    val lift: Float = 0f,
    val squash: Float = 1f,
    val lean: Float = 0f,
    val burst: Float = 0f,
)

/** One bounded response from the caller's active clock. No timer, random stream,
 * command queue or physics objects: repeated taps replace the current 1.8-second gag.
 * The existing avatar clock freezes under overlays/backgrounding/thermal pauses.
 */
internal class CartoonHabitatGag(private val kind: CartoonGagKind) {
    private var startedAt: Float? = null
    private val path by lazy { Path() }
    private val ink = Color(0xFF22363E)
    private val cream = Color(0xFFFFF0BC)

    fun tap(point: Offset, width: Float, height: Float, clock: Float,
        active: Boolean, avatarOwnsTap: Boolean, propOwnsTap: Boolean): Boolean {
        if (!active || avatarOwnsTap || propOwnsTap || !clock.isFinite()) return false
        if (cartoonGagLayout(width, height)?.contains(point) != true) return false
        startedAt = clock
        return true
    }

    fun pose(clock: Float, reducedMotion: Boolean = false): CartoonGagPose {
        val start = startedAt ?: return CartoonGagPose()
        if (reducedMotion || !clock.isFinite()) return CartoonGagPose()
        val age = clock - start
        if (age < 0f || age >= DURATION) return CartoonGagPose()
        if (age < .16f) return CartoonGagPose(squash = .72f + .28f * (age / .16f))
        val t = (age - .16f) / (DURATION - .16f)
        val envelope = (1f - t) * (1f - t)
        val bounce = abs(sin(t * PI.toFloat() * 2.6f))
        return CartoonGagPose(lift = bounce * envelope * 2.1f,
            squash = 1f + sin(t * PI.toFloat() * 5.2f) * envelope * .13f,
            lean = sin(t * PI.toFloat() * 3.6f) * envelope * 13f,
            burst = ((1f - t * 3f).coerceAtLeast(0f) * bounce).coerceIn(0f, 1f))
    }

    fun draw(scope: DrawScope, clock: Float, reducedMotion: Boolean = false) = with(scope) {
        val layout = cartoonGagLayout(size.width, size.height) ?: return@with
        val pose = pose(clock, reducedMotion)
        translate(layout.center.x, layout.center.y) {
            scale(layout.unit, layout.unit, Offset.Zero) {
                // Heavy ink, flat fills and a tiny contact shadow match the new room art.
                drawOval(ink.copy(alpha = .15f), Offset(-1.18f, 1.36f), Size(2.36f, .30f))
                drawRoundRect(ink, Offset(-1.06f, .92f), Size(2.12f, .62f), CornerRadius(.19f))
                drawRoundRect(Color(0xFF70B7BD), Offset(-.98f, .98f), Size(1.96f, .47f), CornerRadius(.14f))
                drawLine(cream, Offset(-.72f, 1.1f), Offset(-.30f, 1.1f), .05f, StrokeCap.Round)
                drawCircle(ink, .16f, Offset(.70f, 1.22f))
                drawCircle(if (pose.lift > .05f) Color(0xFFFFDC67) else Color(0xFFEC737C), .105f, Offset(.70f, 1.22f))
                // A printed hand-sized button mark makes the toy discoverable without labels.
                drawCircle(cream, .21f, Offset(0f, 1.23f))
                drawCircle(ink, .21f, Offset(0f, 1.23f), style = Stroke(.05f))
                drawLine(ink, Offset(-.09f, 1.23f), Offset(.09f, 1.23f), .045f, StrokeCap.Round)
                drawLine(ink, Offset(0f, 1.14f), Offset(0f, 1.32f), .045f, StrokeCap.Round)
                if (kind == CartoonGagKind.PORTAL || kind == CartoonGagKind.PERISCOPE) {
                    // These two machines remain planted; only their portal/bubble contents
                    // respond. Moonflower and rocket use the spring/launch silhouette.
                    if (kind == CartoonGagKind.PORTAL) portal(pose) else periscope(pose)
                    return@scale
                }
                path.reset()
                path.moveTo(0f, .97f)
                repeat(6) { i ->
                    val y = .9f - (i + 1) / 6f * (.70f + pose.lift)
                    path.lineTo(if (i % 2 == 0) -.23f else .23f, y)
                }
                drawPath(path, ink, style = Stroke(.09f, cap = StrokeCap.Round))
                translate(0f, -pose.lift) {
                    rotate(pose.lean, Offset.Zero) {
                        scale(1f / pose.squash, pose.squash, Offset(0f, .32f)) {
                            when (kind) {
                                CartoonGagKind.JELLY -> jelly(pose.lift)
                                CartoonGagKind.SOCK -> sock(pose.lift)
                                CartoonGagKind.PANCAKE -> pancake(pose.lift)
                                CartoonGagKind.MOONFLOWER -> moonflower(pose)
                                CartoonGagKind.ROCKET -> rocket(pose)
                                else -> Unit
                            }
                        }
                    }
                }
                if (pose.burst > .01f) repeat(4) { i ->
                    val side = if (i < 2) -1f else 1f
                    val y = -.95f - (i % 2) * .65f - pose.lift * .4f
                    drawLine(cream.copy(alpha = pose.burst), Offset(side * 1.35f, y),
                        Offset(side * 1.72f, y - .19f), .09f, StrokeCap.Round)
                }
            }
        }
    }

    private fun DrawScope.face(y: Float, excited: Float) {
        for (side in intArrayOf(-1, 1)) {
            val eye = Offset(side * .32f, y)
            drawOval(cream, eye - Offset(.18f, .23f), Size(.36f, .46f))
            drawOval(ink, eye - Offset(.18f, .23f), Size(.36f, .46f), style = Stroke(.06f))
            drawCircle(ink, .075f, eye + Offset(.025f, -.015f))
        }
        if (excited > .08f) {
            drawOval(ink, Offset(-.15f, y + .22f), Size(.30f, .31f))
            drawOval(Color(0xFFEE8DA1), Offset(-.09f, y + .40f), Size(.18f, .09f))
        } else drawArc(ink, 12f, 156f, false, Offset(-.20f, y + .09f), Size(.40f, .34f), style = Stroke(.065f, cap = StrokeCap.Round))
    }

    private fun DrawScope.jelly(excited: Float) {
        path.reset()
        path.moveTo(-.86f, .40f)
        path.cubicTo(-1.08f, -.03f, -.72f, -1.10f, -.22f, -1.04f)
        path.cubicTo(.57f, -1.29f, .91f, -.57f, .86f, .34f)
        path.quadraticBezierTo(.72f, .57f, .50f, .41f)
        path.quadraticBezierTo(.20f, .65f, -.05f, .45f)
        path.quadraticBezierTo(-.49f, .69f, -.86f, .40f)
        path.close()
        drawPath(path, Color(0xFFA4D663)); drawPath(path, ink, style = Stroke(.085f))
        drawOval(cream.copy(alpha = .65f), Offset(-.63f, -.90f), Size(.33f, .13f))
        face(-.40f, excited)
    }

    private fun DrawScope.sock(excited: Float) {
        path.reset(); path.moveTo(-.69f, -1.05f); path.lineTo(.35f, -1.05f)
        path.lineTo(.35f, -.05f); path.cubicTo(.87f, -.17f, 1.10f, .04f, .98f, .40f)
        path.quadraticBezierTo(.75f, .71f, -.27f, .48f)
        path.quadraticBezierTo(-.65f, .42f, -.69f, .12f); path.close()
        drawPath(path, Color(0xFFDE94BC)); drawPath(path, ink, style = Stroke(.085f))
        drawRoundRect(cream, Offset(-.75f, -1.14f), Size(1.17f, .34f), CornerRadius(.07f))
        drawRoundRect(ink, Offset(-.75f, -1.14f), Size(1.17f, .34f), CornerRadius(.07f), style = Stroke(.07f))
        repeat(4) { i -> drawLine(Color(0xFFDB806D), Offset(-.56f + i * .25f, -1.11f), Offset(-.56f + i * .25f, -.83f), .06f) }
        translate(-.15f, 0f) { face(-.40f, excited) }
        drawArc(ink, -60f, 105f, false, Offset(.63f, .02f), Size(.20f, .39f), style = Stroke(.055f))
    }

    private fun DrawScope.pancake(excited: Float) {
        for (i in 2 downTo 0) {
            val y = -.57f + i * .27f
            drawRoundRect(Color(0xFFF0B763), Offset(-.99f, y), Size(1.98f, .51f), CornerRadius(.25f))
            drawRoundRect(ink, Offset(-.99f, y), Size(1.98f, .51f), CornerRadius(.25f), style = Stroke(.075f))
            drawLine(cream, Offset(-.65f, y + .11f), Offset(-.20f, y + .11f), .05f, StrokeCap.Round)
        }
        drawOval(Color(0xFFBC743F), Offset(-.78f, -.64f), Size(1.56f, .26f))
        rotate(-12f, Offset.Zero) {
            drawRoundRect(Color(0xFFFFE979), Offset(-.28f, -.92f), Size(.62f, .33f), CornerRadius(.055f))
            drawRoundRect(ink, Offset(-.28f, -.92f), Size(.62f, .33f), CornerRadius(.055f), style = Stroke(.06f))
        }
        face(-.21f, excited)
    }

    private fun DrawScope.portal(pose: CartoonGagPose) {
        drawOval(ink, Offset(-1.03f, -1.15f), Size(2.06f, 2.13f))
        drawOval(Color(0xFFA7CF95), Offset(-.91f, -1.04f), Size(1.82f, 1.90f))
        drawOval(ink, Offset(-.70f, -.84f), Size(1.40f, 1.50f))
        drawOval(Color(0xFF487D78), Offset(-.62f, -.76f), Size(1.24f, 1.34f))
        rotate(pose.lift * 125f + pose.lean, Offset(0f, -.12f)) {
            repeat(3) { i ->
                drawArc(Color(0xFFC5EB9A), i * 120f, 72f, false,
                    Offset(-.48f, -.63f), Size(.96f, 1.03f), style = Stroke(.11f, cap = StrokeCap.Round))
            }
            drawCircle(cream, .23f, Offset(0f, -.12f))
            drawOval(Color(0xFFDE9CBA), Offset(-.36f, -.18f), Size(.72f, .13f), style = Stroke(.07f))
            drawCircle(ink, .038f, Offset(-.06f, -.16f)); drawCircle(ink, .038f, Offset(.06f, -.16f))
        }
        // Three raised studs, rather than a field of particles.
        repeat(3) { i -> drawCircle(cream, .07f, Offset((i - 1) * .48f, .75f)) }
    }

    private fun DrawScope.moonflower(pose: CartoonGagPose) {
        val awake = pose.lift > .08f
        repeat(6) { i ->
            val angle = i * PI.toFloat() / 3f
            val center = Offset(cos(angle) * .62f, -.27f + sin(angle) * .62f)
            drawOval(Color(0xFFD49CBC), center - Offset(.32f, .32f), Size(.64f, .64f))
            drawOval(ink, center - Offset(.32f, .32f), Size(.64f, .64f), style = Stroke(.075f))
        }
        drawCircle(cream, .46f, Offset(0f, -.27f))
        drawCircle(ink, .46f, Offset(0f, -.27f), style = Stroke(.075f))
        for (side in intArrayOf(-1, 1)) {
            if (awake) drawOval(ink, Offset(side * .15f - .045f, -.41f), Size(.09f, .17f))
            else drawArc(ink, 5f, 170f, false, Offset(side * .15f - .10f, -.40f), Size(.20f, .11f), style = Stroke(.05f))
        }
        drawArc(ink, 7f, 166f, false, Offset(-.13f, -.22f), Size(.26f, .17f), style = Stroke(.05f))
        drawOval(Color(0xFF95C283), Offset(-.74f, .30f), Size(.60f, .25f))
        drawOval(Color(0xFF95C283), Offset(.14f, .30f), Size(.60f, .25f))
    }

    private fun DrawScope.periscope(pose: CartoonGagPose) {
        path.reset(); path.moveTo(-.18f, .92f); path.lineTo(-.18f, -.49f)
        path.quadraticBezierTo(-.18f, -.80f, .15f, -.80f); path.lineTo(.66f, -.80f)
        drawPath(path, ink, style = Stroke(.57f, cap = StrokeCap.Round))
        drawPath(path, Color(0xFF91C8C2), style = Stroke(.41f, cap = StrokeCap.Round))
        drawOval(cream, Offset(.39f, -1.13f), Size(.51f, .66f))
        drawOval(ink, Offset(.39f, -1.13f), Size(.51f, .66f), style = Stroke(.08f))
        drawOval(Color(0xFF417C95), Offset(.49f, -1.01f), Size(.28f, .42f))
        drawCircle(cream, .08f, Offset(.59f, -.90f))
        drawRoundRect(Color(0xFFE8BC89), Offset(-.62f, .40f), Size(.86f, .30f), CornerRadius(.08f))
        drawRoundRect(ink, Offset(-.62f, .40f), Size(.86f, .30f), CornerRadius(.08f), style = Stroke(.06f))
        if (pose.lift > .04f) repeat(3) { i ->
            val rise = pose.lift * (.43f + i * .18f)
            val center = Offset(.57f - i * .36f, -1.24f - rise)
            val radius = .13f + i * .028f
            drawCircle(Color(0xFFBCECDC).copy(alpha = .75f), radius, center)
            drawCircle(ink, radius, center, style = Stroke(.042f))
            drawCircle(cream, radius * .25f, center + Offset(-.04f, -.05f))
        }
    }

    private fun DrawScope.rocket(pose: CartoonGagPose) {
        if (pose.lift > .05f) {
            path.reset(); path.moveTo(-.23f, .36f); path.lineTo(0f, .83f + pose.burst * .42f)
            path.lineTo(.23f, .36f); path.close()
            drawPath(path, Color(0xFFFFCD6F)); drawPath(path, ink, style = Stroke(.06f))
        }
        path.reset(); path.moveTo(-.36f, -.18f); path.lineTo(-.83f, .45f)
        path.lineTo(-.27f, .30f); path.lineTo(.27f, .30f); path.lineTo(.83f, .45f)
        path.lineTo(.36f, -.18f); path.close()
        drawPath(path, Color(0xFFC48FAD)); drawPath(path, ink, style = Stroke(.075f))
        drawOval(Color(0xFFE6CBA0), Offset(-.40f, -1.15f), Size(.80f, 1.56f))
        drawOval(ink, Offset(-.40f, -1.15f), Size(.80f, 1.56f), style = Stroke(.075f))
        drawCircle(ink, .28f, Offset(0f, -.43f))
        drawCircle(Color(0xFF92D1C2), .21f, Offset(0f, -.43f))
        drawCircle(cream, .065f, Offset(-.055f, -.49f))
        drawLine(ink, Offset(-.26f, .25f), Offset(.26f, .25f), .09f)
    }

    companion object { const val DURATION = 1.8f }
}

/** Conservative foreground exclusion for the GPU avatar. Matches the habitat camera
 * in Fly3DRenderer; a generous body/wing envelope prevents a background toy stealing
 * an avatar touch when the character flies in front of it. This is not ray picking.
 */
internal fun cartoonFlyOwnsPoint(point: Offset, width: Float, height: Float, frame: Fly3DFrame): Boolean {
    val scale = frame.avatarScale.takeIf { it.isFinite() }?.coerceIn(.5f, 3f) ?: 2f
    val gameScale = habitatFlyScale(scale)
    val unit = .50f * gameScale
    val groundedOffset = .545f * (gameScale - 1f) * (1f - frame.flight.coerceIn(0f, 1f))
    val rootY = frame.y + groundedOffset
    val center = cartoonProjectHabitatPoint(frame.x, rootY + unit * .12f, frame.z, width, height) ?: return true
    val front = cartoonProjectHabitatPoint(frame.x + unit * 1.6f, rootY + unit * .12f,
        (frame.z + unit * .35f).coerceAtMost(3f), width, height) ?: return true
    val radius = maxOf(minOf(width, height) * .05f, abs(front.x - center.x))
    return hypot(point.x - center.x, point.y - center.y) <= radius
}

/** Same camera basis/frustum as the GPU habitat, expressed without Android Matrix
 * so picking guards can be checked on the JVM as well as the real display.
 */
internal fun cartoonProjectHabitatPoint(x: Float, y: Float, z: Float, width: Float, height: Float): Offset? {
    if (!x.isFinite() || !y.isFinite() || !z.isFinite() || !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
    val upY = .9922779f; val upZ = -.12403473f
    val depth = (2.5f - y) * .12403473f + (8f - z) * .9922779f
    if (depth <= .1f) return null
    val halfWidth = maxOf(3.25f, 3f * width / height)
    val pixels = width * 4f / halfWidth / depth
    return Offset(width * .5f + x * pixels, height * .5f - ((y - 2.5f) * upY + (z - 8f) * upZ) * pixels)
}
