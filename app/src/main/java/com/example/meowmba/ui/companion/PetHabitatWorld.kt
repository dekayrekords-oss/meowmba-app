package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.meowmba.core.behavior.BehaviorBias
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

enum class HabitatAction { FEED, PLAY, REST, READ, DRINK, NUDGE, RELEASE, EXPLORE }
data class HabitatCommand(val id: Long = 0, val action: HabitatAction = HabitatAction.EXPLORE)
internal data class HabitatObjectSnapshot(val kind: String, val x: Float, val y: Float,
    val vx: Float, val vy: Float, val held: Boolean, val angle: Float)
internal data class HabitatWorldSnapshot(val elapsed: Float, val action: HabitatAction,
    val approaching: Boolean, val pose: FullFlyPose, val objects: List<HabitatObjectSnapshot>)

/** Bounded cartoon locomotion and rigid prop state, not biological flight physics.
 * One host clock controls both integration and phases; equal timestamps are frozen.
 * Props retain their position after falling and have exactly one owner while held.
 */
internal class PetHabitatWorld {
    private class Prop(val kind: String, var x: Float, var y: Float = TABLE) {
        var vx = 0f; var vy = 0f; var angle = 0f; var spin = 0f; var held = false
        var onTable = true
    }
    private val props = listOf(Prop("glass", .68f), Prop("book", .28f), Prop("ball", .83f))
    private var lastTime: Float? = null
    private var lastCommand = Long.MIN_VALUE
    private var elapsed = 0f
    private var action = HabitatAction.EXPLORE
    private var actionAge = 0f
    private var approaching = false
    private var targetKind: String? = null
    private var foodRemaining = 0f
    private var pose = FullFlyPose()
    private var targetX = .58f
    private var targetY = .42f
    private var roamAge = 0f
    private var roamIndex = 0
    private var wingPhase = 0f
    private var walkPhase = 0f
    private var approachFacing = 1f

    fun snapshot() = HabitatWorldSnapshot(elapsed, action, approaching, pose,
        props.map { HabitatObjectSnapshot(it.kind, it.x, it.y, it.vx, it.vy, it.held, it.angle) })

    fun step(timeSec: Float, command: HabitatCommand, bias: BehaviorBias,
        width: Float, height: Float, playPreference: Float = 0f): FullFlyPose {
        if (!timeSec.isFinite() || !width.isFinite() || !height.isFinite() || width <= 0 || height <= 0) return pose
        val prior = lastTime
        // A redraw is not a simulation tick, including commands queued during pause.
        if (prior != null && timeSec <= prior) return pose
        lastTime = timeSec
        val dt = if (prior == null) 0f else (timeSec - prior).coerceIn(0f, .05f)
        if (command.id != lastCommand) {
            lastCommand = command.id
            begin(command.action)
        }
        elapsed += dt
        actionAge += dt
        roamAge += dt
        foodRemaining = maxOf(0f, foodRemaining - dt)
        integrateProps(dt)
        val activity = bias.arousal.finite(.4f).coerceIn(0f, 1f)
        val play = playPreference.finite(0f).coerceIn(-1f, 1f)
        if (action == HabitatAction.EXPLORE && roamAge > 3.5f - activity - play * .4f) {
            roamAge = 0f; roamIndex++
            targetX = .50f + sin(roamIndex * 2.399f) * .30f
            targetY = .37f + sin(roamIndex * 1.713f) * .15f
        }
        val unit = minOf(width, height) * .32f
        val ratioY = unit / height
        val minX = maxOf(.15f, 1.05f * unit / width)
        val maxX = minOf(.85f, 1f - 1.05f * unit / width)
        val tablePerch = TABLE - .689f * ratioY
        val floorPerch = FLOOR - .689f * ratioY
        if (action == HabitatAction.REST || action == HabitatAction.FEED) targetY = tablePerch
        val target = props.firstOrNull { it.kind == targetKind }
        if (approaching && target != null) {
            targetX = (target.x - approachFacing * .39f * unit / width).coerceIn(minX, maxX)
            targetY = if (target.y > TABLE + .05f) floorPerch else tablePerch
        }
        targetX = targetX.coerceIn(minX, maxX)
        targetY = targetY.coerceIn(maxOf(.20f, .88f * ratioY), floorPerch)
        val dx = targetX - pose.x; val dy = targetY - pose.y
        val distance = hypot(dx, dy)
        val speed = .14f + activity * .11f
        val fraction = if (distance < .00001f) 0f else minOf(1f, dt * speed / distance)
        val x = (pose.x + dx * fraction).coerceIn(minX, maxX)
        val y = (pose.y + dy * fraction).coerceIn(maxOf(.20f, .88f * ratioY), floorPerch)
        val moving = distance > .015f
        val desiredFlight = if (moving && (targetY < tablePerch - .035f || abs(dy) > .035f)) 1f else 0f
        val flight = pose.flight + (desiredFlight - pose.flight) * minOf(1f, dt * 5f)
        wingPhase = (wingPhase + dt * (5f + flight * 31f)) % TAU
        if (moving) walkPhase = (walkPhase + dt * (7f + activity * 4f)) % TAU
        val desiredDrop = if (approaching && target != null && distance < .12f)
            .689f - .075f - (if (target.kind == "glass") .06f else .085f) else 0f
        val drop = pose.handDrop + (desiredDrop - pose.handDrop) * minOf(1f, dt * 8f)
        val desiredHandX = if (approaching && target != null && distance < .12f)
            (abs(target.x - x) * width / unit).coerceIn(.25f, .85f) else .39f
        val handX = pose.handX + (desiredHandX - pose.handX) * minOf(1f, dt * 8f)
        if (approaching && distance < .008f && (target == null || drop > desiredDrop - .012f && abs(handX - desiredHandX) < .012f)) {
            approaching = false; actionAge = 0f
            when (action) {
                HabitatAction.READ, HabitatAction.DRINK -> target?.let { prop ->
                    // Any previous object has already been released by begin().
                    prop.held = true; prop.vx = 0f; prop.vy = 0f; prop.spin = 0f
                }
                HabitatAction.NUDGE -> target?.let { kick(it, .95f, -.13f) }
                HabitatAction.PLAY -> target?.let { kick(it, -.35f, -.45f) }
                HabitatAction.FEED -> foodRemaining = 4f
                else -> Unit
            }
        }
        val held = props.firstOrNull { it.held }
        pose = FullFlyPose(x, y, facing = if (approaching || props.any { it.held }) approachFacing else if (abs(dx) > .01f) if (dx < 0) -1f else 1f else pose.facing,
            flight = flight, walkPhase = walkPhase, wingPhase = wingPhase,
            armReach = if (approaching && distance < .12f) .6f else if (action == HabitatAction.PLAY) .35f else 0f,
            holding = held?.kind, handDrop = drop, handX = handX)
        held?.let {
            // Keep simulation position with the visible hand; releasing begins here.
            it.x = (x + pose.facing * handX * unit / width).coerceIn(.04f, .96f)
            it.y = (y + (.075f + drop + (if (it.kind == "glass") .06f else .085f)) * unit / height).coerceIn(.04f, FLOOR)
        }
        if (!approaching && actionAge > 6f && action in listOf(HabitatAction.FEED, HabitatAction.PLAY, HabitatAction.NUDGE)) {
            action = HabitatAction.EXPLORE; roamAge = 4f
        }
        return pose
    }

    private fun begin(next: HabitatAction) {
        if (next == action && next in listOf(HabitatAction.READ, HabitatAction.DRINK) && props.any { it.held }) return
        releaseHeld()
        action = next; actionAge = 0f; approaching = false
        targetKind = when (next) {
            HabitatAction.READ -> "book"
            HabitatAction.DRINK, HabitatAction.NUDGE -> "glass"
            HabitatAction.PLAY -> "ball"
            else -> null
        }
        if (targetKind != null) {
            approaching = true
            approachFacing = if ((props.firstOrNull { it.kind == targetKind }?.x ?: .5f) >= pose.x) 1f else -1f
        }
        when (next) {
            HabitatAction.REST -> { targetX = .50f; targetY = .65f }
            HabitatAction.FEED -> { targetX = .50f; targetY = .61f; approaching = true; foodRemaining = 12f }
            HabitatAction.EXPLORE, HabitatAction.RELEASE -> { targetX = .58f; targetY = .40f; roamAge = 4f }
            else -> Unit
        }
    }

    private fun releaseHeld() {
        props.filter { it.held }.forEach {
            it.held = false; it.onTable = it.x in TABLE_LEFT..TABLE_RIGHT && it.y <= TABLE
            it.vx = pose.facing * .07f; it.vy = 0f; it.spin = pose.facing * 60f
        }
    }

    private fun kick(prop: Prop, vx: Float, vy: Float) {
        prop.held = false; prop.vx = vx; prop.vy = vy; prop.spin = vx * 220f
    }

    private fun integrateProps(dt: Float) {
        if (dt <= 0) return
        for (p in props) {
            if (p.held) continue
            val oldY = p.y
            p.x += p.vx * dt
            p.vy += .85f * dt; p.y += p.vy * dt
            if (p.x < .035f || p.x > .965f) { p.x = p.x.coerceIn(.035f, .965f); p.vx *= -.45f }
            if (p.x !in TABLE_LEFT..TABLE_RIGHT) p.onTable = false
            val surface = if (p.onTable && oldY <= TABLE + .001f) TABLE else FLOOR
            if (p.y >= surface && p.vy >= 0f) {
                p.y = surface
                p.vy = if (p.kind == "ball" && p.vy > .09f) -p.vy * .54f else 0f
                p.vx *= maxOf(0f, 1f - dt * if (p.kind == "ball") 1.5f else 3.2f)
                p.spin *= maxOf(0f, 1f - dt * 4f)
                if (abs(p.vx) < .004f) p.vx = 0f
                if (abs(p.spin) < .5f) p.spin = 0f
            }
            p.angle = (p.angle + p.spin * dt) % 360f
        }
    }

    fun actionAt(x: Float, y: Float, width: Float, height: Float): HabitatAction? {
        if (!x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite() || width <= 0 || height <= 0) return null
        val nx = x / width; val ny = y / height
        // Use a finger-sized hit target in physical canvas pixels, not aspect-distorted distance.
        val hit = maxOf(42f, minOf(width, height) * .105f)
        val prop = props.filterNot { it.held }.minByOrNull { hypot((nx - it.x) * width, (ny - it.y) * height) }
        if (prop == null || hypot((nx - prop.x) * width, (ny - prop.y) * height) > hit) return null
        return when (prop.kind) { "book" -> HabitatAction.READ; "glass" -> HabitatAction.DRINK; else -> HabitatAction.PLAY }
    }

    fun drawProps(scope: DrawScope, fullFly: Boolean) = with(scope) {
        val u = size.minDimension
        val timber = Color(0xFF76523C)
        val edge = Color(0xFF243B41)
        // The tabletop is intentionally broader than the thin old rail: it gives
        // the larger glass/book/ball a believable surface in portrait and landscape.
        drawRoundRect(timber, Offset(size.width * TABLE_LEFT, size.height * TABLE - u * .004f),
            Size(size.width * (TABLE_RIGHT - TABLE_LEFT), u * .055f), CornerRadius(u * .014f))
        drawRoundRect(edge.copy(alpha = .55f), Offset(size.width * TABLE_LEFT, size.height * TABLE + u * .040f),
            Size(size.width * (TABLE_RIGHT - TABLE_LEFT), u * .010f), CornerRadius(u * .004f))
        drawRoundRect(timber.copy(alpha = .92f), Offset(size.width * .16f, size.height * TABLE + u * .046f), Size(u * .040f, size.height * .19f), CornerRadius(u * .009f))
        drawRoundRect(timber.copy(alpha = .92f), Offset(size.width * .80f, size.height * TABLE + u * .046f), Size(u * .040f, size.height * .19f), CornerRadius(u * .009f))
        // A solid ink contour and broad bevel add depth without moving the contact plane.
        drawRoundRect(edge, Offset(size.width * TABLE_LEFT, size.height * TABLE - u * .004f),
            Size(size.width * (TABLE_RIGHT - TABLE_LEFT), u * .055f), CornerRadius(u * .014f), style = Stroke(u * .004f))
        drawLine(Color(0xFFC6A47E), Offset(size.width * TABLE_LEFT + u * .016f, size.height * TABLE + u * .006f),
            Offset(size.width * TABLE_RIGHT - u * .016f, size.height * TABLE + u * .006f), u * .006f)
        for (p in props) {
            if (p.held && fullFly) continue
            val at = Offset(p.x * size.width, p.y * size.height)
            if (!p.held) drawOval(edge.copy(alpha = .17f), at - Offset(u * .040f, u * .007f), Size(u * .080f, u * .014f))
            rotate(p.angle, at) {
                when (p.kind) {
                    "book" -> {
                        val book = at - Offset(u * .064f, u * .030f)
                        drawRoundRect(edge, book - Offset(u * .004f, u * .004f), Size(u * .136f, u * .044f), CornerRadius(u * .008f))
                        drawRoundRect(Color(0xFF2C777A), book, Size(u * .128f, u * .036f), CornerRadius(u * .007f))
                        drawRect(Color(0xFFF5DEC0), book + Offset(u * .011f, u * .010f), Size(u * .106f, u * .016f))
                        drawLine(Color(0xFFF9F1D8), book + Offset(u * .016f, u * .016f), book + Offset(u * .108f, u * .016f), u * .002f)
                        drawCircle(Color(0xFFF0B75B), u * .006f, book + Offset(u * .103f, u * .018f))
                        drawRoundRect(Color(0xFF214F59), book + Offset(u * .002f, u * .001f), Size(u * .013f, u * .034f), CornerRadius(u * .003f))
                        drawLine(Color(0xFFB0A68D), book + Offset(u * .017f, u * .023f), book + Offset(u * .097f, u * .023f), u * .0015f)
                        drawRect(Color(0xFFDA8FA7), book + Offset(u * .083f, u * .023f), Size(u * .013f, u * .017f))
                    }
                    "glass" -> {
                        val glass = at - Offset(u * .031f, u * .115f)
                        drawRoundRect(Color(0x442C6F86), glass + Offset(u * .004f, u * .006f), Size(u * .062f, u * .115f), CornerRadius(u * .010f))
                        drawRoundRect(Color(0x779BE7F2), glass, Size(u * .062f, u * .115f), CornerRadius(u * .010f))
                        drawRect(Color(0xAA4DA8D0), glass + Offset(u * .006f, u * .059f), Size(u * .050f, u * .046f))
                        drawLine(Color(0xFFE9FFFF), glass + Offset(u * .012f, u * .012f), glass + Offset(u * .012f, u * .095f), u * .004f)
                        drawRoundRect(edge, glass, Size(u * .062f, u * .115f), CornerRadius(u * .010f), style = Stroke(u * .004f))
                        drawOval(Color(0xFF97DFE2), glass + Offset(u * .006f, u * .053f), Size(u * .050f, u * .012f))
                        drawOval(Color(0xFFE9F8E5), glass - Offset(0f, u * .004f), Size(u * .062f, u * .013f))
                        drawOval(edge, glass - Offset(0f, u * .004f), Size(u * .062f, u * .013f), style = Stroke(u * .003f))
                        drawLine(Color(0xFFD7F2E1), glass + Offset(u * .048f, u * .078f), glass + Offset(u * .048f, u * .096f), u * .003f)
                    }
                    else -> {
                        val ballAt = at - Offset(0f, u * .040f)
                        drawCircle(edge, u * .039f, ballAt)
                        drawCircle(Color(0xFFCC754F), u * .035f, ballAt)
                        drawCircle(Color(0xFFF2A263), u * .030f, ballAt - Offset(u * .003f, u * .004f))
                        drawArc(Color(0xFFFFD78B), -70f, 128f, false, ballAt - Offset(u * .027f, u * .027f), Size(u * .054f, u * .054f), style = Stroke(u * .005f))
                        drawArc(Color(0xFF4B8E9B), 112f, 116f, false, ballAt - Offset(u * .031f, u * .031f), Size(u * .062f, u * .062f), style = Stroke(u * .005f))
                    }
                }
            }
        }
        if (foodRemaining > 0f) {
            val at = Offset(size.width * .5f, size.height * TABLE)
            drawOval(Color(0xFFBFD0B4), at - Offset(u * .06f, u * .012f), Size(u * .12f, u * .025f))
            for (i in 0..2) drawCircle(Color(0xFFEFA661), u * .015f,
                at + Offset((i - 1) * u * .029f, -u * .012f - if (i == 1) u * .009f else 0f))
        }
    }

    private fun Float.finite(fallback: Float) = if (isFinite()) this else fallback
    private companion object {
        const val TABLE = .78f; const val FLOOR = .94f
        const val TABLE_LEFT = .10f; const val TABLE_RIGHT = .90f
        const val TAU = 6.2831855f
    }
}
