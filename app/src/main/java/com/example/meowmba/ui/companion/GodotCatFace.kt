package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Full Cat Assistant face draw — port of Godot `cat_assistant.gd` `_draw_*`
 * layers: floating features on black, green almond eyes, pink ears/nose,
 * hinged jaw, whiskers, glows, motes. Driven by [MotionPose] + ambient clocks.
 */
data class GodotFaceClock(
    val t: Float,
    val blink: Float,
    val earTwitchL: Float,
    val earTwitchR: Float,
    val gazeX: Float,
    val gazeY: Float,
    val talk: Float,
    val faceAlpha: Float = 1f,
)

// Godot palette (boosted for dark void)
private val EyeGreen = Color(0.30f, 1.00f, 0.52f)
private val EarPink = Color(1.00f, 0.52f, 0.60f)
private val NosePink = Color(1.00f, 0.48f, 0.56f)
private val MouthPink = Color(0.95f, 0.70f, 0.82f)
private val WhiskCyan = Color(0.72f, 0.90f, 1.00f)
private val PawColor = Color(1.00f, 0.72f, 0.78f)
private val PadColor = Color(1.00f, 0.45f, 0.55f)

private fun Color.withA(a: Float) = copy(alpha = (alpha * a).coerceIn(0f, 1f))
private fun hdr(c: Color, boost: Float, a: Float = 1f): Color {
    // Soft HDR stand-in: brighten without fully clipping on sRGB canvas.
    val k = 0.55f + 0.45f * boost.coerceIn(0.2f, 1.4f)
    return Color(
        red = (c.red * k).coerceIn(0f, 1f),
        green = (c.green * k).coerceIn(0f, 1f),
        blue = (c.blue * k).coerceIn(0f, 1f),
        alpha = (c.alpha * a).coerceIn(0f, 1f),
    )
}

fun DrawScope.softGlow(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (alpha <= 0.001f || radius <= 0.5f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * Draw the Godot Cat Assistant face into [this] scope.
 * [R] is head radius in px; [origin] is face center after bob/sway/tilt is applied by caller.
 */
fun DrawScope.drawGodotCatFace(
    origin: Offset,
    R: Float,
    pose: MotionPose,
    clock: GodotFaceClock,
    showTail: Boolean = false,
    eyeColor: Color = EyeGreen,
    glowColor: Color = EyeGreen,
    glowStrength: Float = 0.28f,
    pearl: Boolean = false,
    featureColor: Color = Color(0xFFE8D5B9),
) {
    val a = clock.faceAlpha
    val t = clock.t
    // Sleek hairline: scale gently with R but never go fat on large faces.
    val lineW = (R * 0.010f).coerceIn(1.4f, 4.2f)

    // Soft halo behind the features — user-tinted, never a solid head fill.
    val gs = (glowStrength * a).coerceIn(0f, 0.55f)
    if (gs > 0.01f) {
        softGlow(origin + Offset(0f, R * 0.04f), R * 2.8f, glowColor, gs * 0.55f)
        softGlow(origin + Offset(0f, R * 0.04f), R * 1.55f, glowColor, gs * 0.85f)
        softGlow(origin + Offset(0f, R * 0.02f), R * 0.95f, glowColor, gs * 0.45f)
    }

    // Optional tail behind
    if (showTail) {
        drawGodotTail(origin, R, pose, t, a)
    }

    // Ears
    drawGodotEar(origin, R, -1f, pose, clock, lineW, if (pearl) featureColor else EarPink, pearl)
    drawGodotEar(origin, R, 1f, pose, clock, lineW, if (pearl) featureColor else EarPink, pearl)

    // Eyes
    drawGodotEye(origin, R, -1f, pose, clock, lineW, eyeColor, pearl, featureColor)
    drawGodotEye(origin, R, 1f, pose, clock, lineW, eyeColor, pearl, featureColor)

    // Nose + mouth + tongue
    drawGodotNoseMouth(origin, R, pose, clock, lineW,
        if (pearl) featureColor else NosePink, if (pearl) featureColor else MouthPink)

    // Whiskers
    drawGodotWhiskers(origin, R, -1f, pose, clock, if (pearl) featureColor else WhiskCyan, pearl)
    drawGodotWhiskers(origin, R, 1f, pose, clock, if (pearl) featureColor else WhiskCyan, pearl)

    // Paws (from jaw/lick or head tip)
    val pawOn = when {
        pose.jawOpen > 0.35f && pose.lipCurl < 0.2f -> (pose.jawOpen * 0.7f).coerceIn(0f, 1f)
        abs(pose.headTiltDeg) > 6f -> ((abs(pose.headTiltDeg) - 4f) / 12f).coerceIn(0f, 1f)
        else -> 0f
    }
    if (!pearl && pawOn > 0.02f) {
        val side = if (pose.headTiltDeg >= 0f) 1f else -1f
        drawGodotPaw(
            origin + Offset(side * R * 0.42f, R * 0.55f + pose.offsetY * 0.1f),
            R,
            pawOn,
            rotDeg = side * 25f + pose.headTiltDeg * 0.5f,
            a,
        )
    }
}

private fun DrawScope.drawMotes(origin: Offset, R: Float, t: Float, a: Float) {
    val ext = R * 3.0f
    for (i in 0 until 16) {
        val depth = 0.25f + (i % 5) * 0.14f
        val speed = 12f + (i % 7) * 3f
        val phase = i * 0.9f
        val x = origin.x + sin(t * 0.35f + i) * ext * depth * 0.85f
        val y = origin.y + ((i * 47f + t * speed) % (ext * 2f)) - ext
        val twinkle = 0.40f + 0.60f * sin(t * 1.5f + phase)
        softGlow(Offset(x, y), 5.5f * depth, WhiskCyan, 0.09f * twinkle * depth * a)
    }
}

private fun DrawScope.drawGodotEar(
    origin: Offset,
    R: Float,
    side: Float,
    pose: MotionPose,
    clock: GodotFaceClock,
    lineW: Float,
    EarPink: Color = com.example.meowmba.ui.companion.EarPink,
    pearl: Boolean = false,
) {
    val a = clock.faceAlpha
    val idx = if (side < 0f) 0 else 1
    val twitch = if (idx == 0) clock.earTwitchL else clock.earTwitchR
    val base = origin + Offset(side * R * 0.58f, -R * 0.72f)
    val wobble = sin((1f - twitch) * PI.toFloat() * 5f) * twitch * 7f
    val idle = sin(clock.t * 1.15f + idx * 2.4f) * 2.4f
    val split = pose.earSplitDeg * (if (idx == 0) 1f else -1f)
    // Yaw lag is symmetric in body space, so it takes `side` like splay does —
    // both ears drag the same way when the head moves sideways.
    val yawLag = side * pose.earYawLagDeg
    val rot = GodotCatDraw.deg(
        side * (pose.earSplayDeg - pose.earPitchDeg * 0.5f) + wobble + idle + split + yawLag
    )
    val hw = R * 0.30f
    val hh = R * 0.62f

    softGlow(base + Offset(0f, -hh * 0.35f), hh * 0.85f, EarPink, 0.06f * a)

    // Nested triangles at local origin, rotated into place via manual rebuild
    fun tri(cx: Float, cy: Float, hW: Float, hH: Float, round: Float, segs: Int): Path {
        val local = ArrayList<Offset>()
        // Build rounded triangle points then rotate+translate
        val path = GodotCatDraw.roundedTriangle(Offset(cx, cy), hW, hH, round, segs)
        // Extract is hard from Path; rebuild points same as GodotCatDraw
        val pts = ArrayList<Offset>()
        val n = segs
        for (i in 0..n) {
            val tt = i.toFloat() / n
            val linear = 1f - tt
            val dome = kotlin.math.sqrt(max(0f, 1f - tt * tt))
            val w = hW * (linear * (1f - round) + dome * round)
            pts.add(Offset(cx - w, cy - hH * tt))
        }
        for (i in n downTo 0) {
            val tt = i.toFloat() / n
            val linear = 1f - tt
            val dome = kotlin.math.sqrt(max(0f, 1f - tt * tt))
            val w = hW * (linear * (1f - round) + dome * round)
            pts.add(Offset(cx + w, cy - hH * tt))
        }
        val placed = GodotCatDraw.placePoints(pts, base, rot)
        return GodotCatDraw.pathFrom(placed)
    }

    val pOut = tri(0f, 0f, hw, hh, 0.30f, 28)
    val pMid = tri(0f, -hh * 0.05f, hw * 0.80f, hh * 0.82f, 0.32f, 24)
    val pIn = tri(0f, -hh * 0.09f, hw * 0.60f, hh * 0.66f, 0.34f, 22)
    val pCore = tri(0f, -hh * 0.13f, hw * 0.38f, hh * 0.46f, 0.36f, 18)

    if (pearl) {
        drawPath(pOut, Brush.linearGradient(
            listOf(lerp(EarPink, Color.White, 0.6f).withA(a), EarPink.withA(a),
                lerp(EarPink, Color.Black, 0.55f).withA(a)),
            start = base + Offset(-hw, -hh), end = base + Offset(hw, hh * 0.2f)))
        drawPath(pIn, Brush.linearGradient(
            listOf(lerp(EarPink, Color.Black, 0.55f).withA(a), EarPink.withA(a)),
            start = base + Offset(0f, -hh), end = base))
        drawPath(pOut, lerp(EarPink, Color.White, 0.5f).withA(a),
            style = Stroke(width = lineW * 1.2f, cap = StrokeCap.Round))
        return
    }

    // Lighter fills — outline carries the shape, not thick bands.
    drawPath(pOut, Color(EarPink.red * 0.10f, EarPink.green * 0.05f, EarPink.blue * 0.06f, 0.50f * a))
    drawPath(pMid, EarPink.copy(alpha = 0.09f * a))
    drawPath(pIn, EarPink.copy(alpha = 0.16f * a))
    drawPath(pCore, hdr(EarPink, 0.28f, 0.50f * a))
    drawPath(pIn, hdr(EarPink, 0.50f, a), style = Stroke(width = lineW * 0.40f, cap = StrokeCap.Round))
    drawPath(pOut, hdr(EarPink, 0.90f, a), style = Stroke(width = lineW * 0.72f, cap = StrokeCap.Round))
}

private fun DrawScope.drawGodotEye(
    origin: Offset,
    R: Float,
    side: Float,
    pose: MotionPose,
    clock: GodotFaceClock,
    lineW: Float,
    eyeColor: Color = EyeGreen,
    pearl: Boolean = false,
    featureColor: Color = Color(0xFFE8D5B9),
) {
    val a = clock.faceAlpha
    val eye = origin + Offset(side * R * 0.48f, -R * 0.07f)
    val breath = 1f + 0.025f * sin(clock.t * 1.7f + side)
    val blink = clock.blink.coerceIn(0.05f, 1.2f)
    // Cap openness — phone canvas makes Godot's full scale look oversized.
    val openness = (blink * pose.eyeOpen.coerceIn(0.12f, 1.15f)).coerceIn(0.05f, 1.2f)
    val ew = pose.eyeWidthScale.coerceIn(0.85f, 1.12f)
    val eh = pose.eyeHeightScale.coerceIn(0.70f, 1.12f)

    // Was R*0.40 (Godot desktop). A bit smaller for phone: ~R*0.33.
    val hw = R * 0.33f * ew * breath *
        (0.74f + 0.26f * openness.coerceIn(0f, 1f))

    var upBase = R * 0.20f * eh
    var loBase = R * 0.32f * eh
    val closing = 1f - openness.coerceIn(0f, 1f)
    val mid = (upBase + loBase) * 0.5f
    upBase = upBase + (mid - upBase) * closing
    loBase = loBase + (mid - loBase) * closing

    // NOT `* pose.eyeHeightScale` again: `eh` above is already that value, and
    // already clamped to 0.70..1.12. Multiplying the raw field in a second time
    // both squared the channel and bypassed its own clamp — an eyeHeightScale
    // of 1.40 rendered at 1.12 × 1.40 ≈ 1.57, which is the bug-eyed look the
    // user reported. Every tall eye (WIDE, ALERT) was inflated by it, not just
    // the pose that has since been removed.
    val up = upBase * max(0.075f, openness) * (1f - pose.lowerLid * 0.18f)
    val lo = loBase * max(0.075f, openness) * (1f - pose.lowerLid * 0.85f)

    val slantK = 0.30f + 0.70f * openness.coerceIn(0f, 1f)
    // Default slant ~5–16° outer high; mood tilts via pose.eyeTiltDeg
    val baseSlant = 8f
    val rot = GodotCatDraw.deg(-side * (baseSlant * slantK + pose.eyeTiltDeg))

    val aperture = GodotCatDraw.almond(
        center = eye,
        halfW = hw,
        upH = up,
        lowH = lo,
        upPower = 0.92f,
        lowPower = 0.50f.coerceAtLeast(0.35f),
        peakShift = side * 0.06f,
        segments = 52,
        rotation = rot,
        edgeNUp = 5.5f,
        edgeNLow = 1.9f,
    )

    softGlow(eye, hw * 1.6f, eyeColor, 0.05f * a)
    if (pearl) {
        drawPath(aperture, featureColor.withA(a), style = Stroke(R * 0.055f))
        drawPath(aperture, Brush.verticalGradient(
            listOf(Color(0xFF726A61).withA(a), Color(0xFFF4EEE4).withA(a)),
            startY = eye.y - up, endY = eye.y + lo))
    } else drawPath(aperture, Color(0.030f, 0.070f, 0.055f, a))

    if (openness > 0.06f) {
        val iris = eye + Offset(clock.gazeX * hw * 0.58f, clock.gazeY * (up + lo) * 0.42f)
        // Pearl lids close over the iris; the iris must not shrink during a blink.
        val ir = if (pearl) R * 0.23f else min(hw * 0.66f, (up + lo) * 0.60f)
        val fx = 1f - 0.30f * abs(clock.gazeX)
        val fy = 1f - 0.14f * abs(clock.gazeY)

        clipPath(aperture) {
            if (pearl) {
                drawCircle(Brush.radialGradient(
                    listOf(Color(0xFFD0AA66).withA(a), eyeColor.withA(a),
                        lerp(eyeColor, Color.Black, 0.72f).withA(a)),
                    center = iris, radius = ir), ir, iris)
            }
            // Iris rings
            for ((scale, alpha) in listOf(
                1.00f to 0.22f, 0.86f to 0.36f, 0.66f to 0.52f, 0.44f to 0.70f,
            )) {
                drawOval(
                    color = hdr(eyeColor, 0.4f + alpha, alpha * a),
                    topLeft = Offset(iris.x - ir * scale * fx, iris.y - ir * scale * fy),
                    size = androidx.compose.ui.geometry.Size(ir * scale * fx * 2f, ir * scale * fy * 2f),
                )
            }
            // Fibres
            val fibreCount = if (pearl) 72 else 22
            for (i in 0 until fibreCount) {
                val ang = i.toFloat() / fibreCount * PI.toFloat() * 2f + 0.11f
                val d = Offset(cos(ang) * fx, sin(ang) * fy)
                drawLine(
                    color = eyeColor.copy(alpha = 0.14f * a),
                    start = iris + d * (ir * 0.32f),
                    end = iris + d * (ir * 0.95f),
                    strokeWidth = (lineW * 0.22f).coerceAtLeast(0.8f),
                    cap = StrokeCap.Round,
                )
            }
            // Bounce light
            drawOval(
                color = eyeColor.copy(alpha = 0.28f * a),
                topLeft = Offset(iris.x - ir * 0.62f * fx, iris.y + ir * 0.28f * fy),
                size = androidx.compose.ui.geometry.Size(ir * 1.24f * fx, ir * 0.40f * fy),
            )
            // Slit pupil
            val pw = ir * (0.10f + 0.46f * pose.pupilSize.coerceIn(0f, 1f))
            val pTop = eye.y - up * 1.10f
            val pBot = eye.y + lo * 1.10f
            val steps = 24
            val slit = ArrayList<Offset>()
            for (i in 0..steps) {
                val tt = i.toFloat() / steps
                val w = pw * (1f - abs(tt * 2f - 1f).pow(1.7f)).pow(0.48f)
                slit.add(Offset(iris.x + w * fx, pTop + (pBot - pTop) * tt))
            }
            for (i in (steps - 1) downTo 1) {
                val tt = i.toFloat() / steps
                val w = pw * (1f - abs(tt * 2f - 1f).pow(1.7f)).pow(0.48f)
                slit.add(Offset(iris.x - w * fx, pTop + (pBot - pTop) * tt))
            }
            if (pearl) {
                drawOval(Color(0xFF081315).withA(a),
                    topLeft = iris - Offset(pw * 1.35f, ir * 0.76f),
                    size = androidx.compose.ui.geometry.Size(pw * 2.7f, ir * 1.52f))
            } else drawPath(GodotCatDraw.pathFrom(slit), Color.Black.copy(alpha = a))
            // Lid shadow
            if (pearl) {
                drawRect(Brush.verticalGradient(
                    listOf(Color.Black.withA(a * 0.58f), Color.Transparent),
                    startY = eye.y - up, endY = eye.y + lo * 0.25f),
                    topLeft = eye - Offset(hw, up),
                    size = androidx.compose.ui.geometry.Size(hw * 2f, up + lo))
            } else drawOval(
                color = Color.Black.copy(alpha = 0.38f * a),
                topLeft = Offset(eye.x - hw * 1.35f, eye.y - up * 1.02f - up * 0.92f),
                size = androidx.compose.ui.geometry.Size(hw * 2.7f, up * 1.84f),
            )
            // Catchlight
            val cl = iris + Offset(side * ir * 0.26f, -ir * 0.34f)
            softGlow(cl, ir * 0.9f, Color.White, 0.20f * a)
            drawOval(
                color = Color.White.copy(alpha = 0.92f * a),
                topLeft = Offset(cl.x - ir * 0.18f, cl.y - ir * 0.12f),
                size = androidx.compose.ui.geometry.Size(ir * 0.36f, ir * 0.24f),
            )
            // Secondary bounce
            drawCircle(
                color = Color(0.8f, 0.9f, 1f, 0.35f * a),
                radius = ir * 0.10f,
                center = iris + Offset(-side * ir * 0.34f, ir * 0.38f),
            )
        }
    }

    drawPath(aperture, hdr(if (pearl) featureColor else eyeColor, 0.55f, a),
        style = Stroke(width = lineW * (if (pearl) 1.1f else 0.42f), cap = StrokeCap.Round))
    softGlow(eye, hw * 1.4f, eyeColor, 0.06f * a * (openness + 0.3f).coerceIn(0f, 1f))
}

private fun DrawScope.drawGodotNoseMouth(
    origin: Offset,
    R: Float,
    pose: MotionPose,
    clock: GodotFaceClock,
    lineW: Float,
    NosePink: Color = com.example.meowmba.ui.companion.NosePink,
    MouthPink: Color = com.example.meowmba.ui.companion.MouthPink,
) {
    val a = clock.faceAlpha
    // Godot: nose/mouth share the R*0.52 face-local anchor.
    val anchor = origin + Offset(0f, R * 0.52f)
    val flare = 1f + pose.lipCurl * 0.08f
    val nw = R * 0.105f * flare
    val nh = R * 0.115f * flare
    // Nose sits slightly ABOVE the anchor; curled lip lifts it further.
    val nc = anchor + Offset(0f, -R * (0.05f + 0.035f * pose.lipCurl))

    softGlow(nc, nw * 2.6f, NosePink, 0.08f * a)
    val nosePts = ArrayList<Offset>()
    val segs = 24
    for (i in 0..segs) {
        val tt = i.toFloat() / segs
        val linear = 1f - tt
        val dome = kotlin.math.sqrt(max(0f, 1f - tt * tt))
        val w = nw * (linear * 0.56f + dome * 0.44f)
        nosePts.add(Offset(-w, -nh * tt))
    }
    for (i in segs downTo 0) {
        val tt = i.toFloat() / segs
        val linear = 1f - tt
        val dome = kotlin.math.sqrt(max(0f, 1f - tt * tt))
        val w = nw * (linear * 0.56f + dome * 0.44f)
        nosePts.add(Offset(w, -nh * tt))
    }
    val nosePath = GodotCatDraw.pathFrom(GodotCatDraw.placePoints(nosePts, nc, PI.toFloat()))
    drawPath(nosePath, hdr(NosePink, 0.55f, a))
    drawPath(nosePath, hdr(NosePink, 1.0f, a), style = Stroke(width = lineW * 0.45f))
    drawOval(
        color = Color.White.copy(alpha = 0.45f * a),
        topLeft = Offset(nc.x - nw * 0.45f, nc.y - nh * 0.35f),
        size = androidx.compose.ui.geometry.Size(nw * 0.35f, nh * 0.22f),
    )

    // --- mouth (Godot hinged jaw) ------------------------------------------
    // Upper lip fixed to the skull; lower lip swings away on open.
    val jaw = pose.jawOpen.coerceIn(0f, 1f)
    val curl = pose.lipCurl.coerceIn(0f, 1f)
    val mw = pose.mouthWidth.coerceIn(0.55f, 1.6f)
    // Soft idle smile bias (~Godot _mouth_curve 0.30).
    val lift = R * 0.105f * 0.30f
    softGlow(anchor + Offset(0f, R * 0.12f), R * 0.22f, MouthPink, 0.05f * a)

    // Apex below nose tip (Godot: a + R*(0.105 - 0.030*curl)).
    val apex = anchor + Offset(0f, R * (0.105f - 0.030f * curl))

    // Upper-lip "W" — two sides. Control + corner relative to anchor, like Godot.
    data class LipSide(val ctrl: Offset, val corner: Offset)
    val lips = listOf(-1f, 1f).map { s ->
        val cornerLift = (lift + R * 0.060f * curl).coerceIn(-R * 0.105f, R * 0.115f)
        LipSide(
            ctrl = anchor + Offset(s * R * 0.078f * mw, R * 0.190f),
            corner = anchor + Offset(s * R * 0.180f * mw, R * 0.156f - cornerLift),
        )
    }

    // Approximate lip_bottom as the lowest Y on the upper-lip curves.
    var lipBottom = apex.y
    for (lip in lips) {
        lipBottom = max(lipBottom, max(lip.ctrl.y, lip.corner.y))
        // Sample quadratic apex→ctrl→corner for true max.
        for (i in 0..8) {
            val tt = i / 8f
            val omt = 1f - tt
            val y = omt * omt * apex.y + 2f * omt * tt * lip.ctrl.y + tt * tt * lip.corner.y
            if (y > lipBottom) lipBottom = y
        }
    }

    // Draw upper lip strokes.
    for (lip in lips) {
        val path = Path().apply {
            moveTo(apex.x, apex.y)
            quadraticTo(lip.ctrl.x, lip.ctrl.y, lip.corner.x, lip.corner.y)
        }
        drawPath(path, hdr(MouthPink, 0.9f, a), style = Stroke(width = lineW * 0.65f, cap = StrokeCap.Round))
    }

    if (jaw > 0.02f) {
        val drop = R * (0.03f + 0.34f * jaw)
        val narrow = 1f - 0.14f * jaw
        val floorY = lipBottom + drop
        val left = lips[0]
        val right = lips[1]

        // Cavity: follow upper lip, then lower-jaw arc (Godot poly).
        val open = Path().apply {
            moveTo(left.corner.x, left.corner.y)
            // reverse left lip toward apex
            quadraticTo(left.ctrl.x, left.ctrl.y, apex.x, apex.y)
            // right lip out
            quadraticTo(right.ctrl.x, right.ctrl.y, right.corner.x, right.corner.y)
            // lower jaw arc: corner → floor → corner
            quadraticTo(
                anchor.x + R * 0.125f * mw * narrow,
                lipBottom + drop * 0.72f,
                anchor.x,
                floorY,
            )
            quadraticTo(
                anchor.x - R * 0.125f * mw * narrow,
                lipBottom + drop * 0.72f,
                left.corner.x,
                left.corner.y,
            )
            close()
        }
        drawPath(open, Color(0.115f, 0.012f, 0.045f, 0.97f * a))

        // Tongue: center at floor_y - th*1.10 so lower edge stays INSIDE the jaw
        // (old code used floorY - th*1.6 top-left + height 2th → hung below lip).
        if (jaw > 0.18f) {
            val th = R * (0.034f + 0.062f * jaw)
            val tw = R * (0.058f + 0.048f * jaw) * mw
            val tongueCy = floorY - th * 1.10f
            drawOval(
                color = hdr(Color(1f, 0.44f, 0.54f), 0.60f, a),
                topLeft = Offset(anchor.x - tw, tongueCy - th),
                size = androidx.compose.ui.geometry.Size(tw * 2f, th * 2f),
            )
        }

        // Lower lip edge so the jaw reads as a lip, not a void.
        drawPath(
            Path().apply {
                moveTo(right.corner.x, right.corner.y)
                quadraticTo(
                    anchor.x + R * 0.125f * mw * narrow,
                    lipBottom + drop * 0.72f,
                    anchor.x,
                    floorY,
                )
                quadraticTo(
                    anchor.x - R * 0.125f * mw * narrow,
                    lipBottom + drop * 0.72f,
                    left.corner.x,
                    left.corner.y,
                )
            },
            hdr(MouthPink, 0.55f, a),
            style = Stroke(width = lineW * 0.5f, cap = StrokeCap.Round),
        )
    }

    // Fangs hang from upper lip on curl only.
    if (curl > 0.05f) {
        val fangLen = R * 0.115f * curl
        for (s in listOf(-1f, 1f)) {
            val root = anchor + Offset(s * R * 0.072f * mw, R * 0.150f - lift * 0.5f)
            val fang = Path().apply {
                moveTo(root.x - R * 0.032f, root.y)
                lineTo(root.x + R * 0.032f, root.y)
                lineTo(root.x + s * R * 0.008f, root.y + fangLen)
                close()
            }
            softGlow(root + Offset(0f, fangLen * 0.4f), R * 0.10f, Color.White, 0.16f * curl * a)
            drawPath(fang, Color(1f, 0.98f, 0.95f).copy(alpha = a))
        }
    }
}

private fun DrawScope.drawGodotWhiskers(
    origin: Offset,
    R: Float,
    side: Float,
    pose: MotionPose,
    clock: GodotFaceClock,
    WhiskCyan: Color = com.example.meowmba.ui.companion.WhiskCyan,
    pearl: Boolean = false,
) {
    val a = clock.faceAlpha
    val base = origin + Offset(0f, R * 0.48f)
    val talking = pose.jawOpen > 0.12f
    val amp = R * (if (talking) 0.016f else 0.009f)
    for (i in -1..1) {
        val y0 = base.y + i * R * 0.028f
        // Overlapping action: each whisker lags the one above it, so the group
        // ripples instead of translating. Sampling ONE shared `energy` and
        // merely scaling it per whisker staggers amplitude but not time — all
        // three then cross zero on the same frame and read as a rigid comb.
        val lag = (i + 1) * 0.055f // 0 / 55 / 110 ms
        val tw = clock.t - lag
        val energy = if (talking) {
            // Rebuild the talk triangle at the lagged time; clock.talk is a
            // single instantaneous sample and cannot be shifted.
            val ph = ((tw / 0.16f) % 1f + 1f) % 1f
            val tri = if (ph < 0.5f) ph * 2f else 2f - ph * 2f
            smoothStep01(tri) * 2f - 1f
        } else {
            sin(tw * 1.4f)
        }
        val phase = energy * (1f - 0.18f * (i + 1))
        if (pearl) {
            val start = Offset(origin.x + side * R * 0.22f, y0)
            val end = Offset(origin.x + side * R * 0.94f,
                y0 + i * R * 0.18f + phase * amp * side)
            val whisker = Path().apply {
                moveTo(start.x, start.y)
                quadraticBezierTo(origin.x + side * R * 0.55f,
                    y0 - R * 0.10f + i * R * 0.10f, end.x, end.y)
            }
            drawPath(whisker, WhiskCyan.withA(a * 0.85f),
                style = Stroke(R * 0.009f, cap = StrokeCap.Round))
            continue
        }
        drawLine(
            color = WhiskCyan.copy(alpha = 0.45f * a),
            start = Offset(origin.x + side * R * 0.12f, y0),
            end = Offset(
                origin.x + side * (R * 0.48f + phase * R * 0.004f),
                y0 + i * R * 0.035f + phase * amp * side,
            ),
            strokeWidth = R * 0.006f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawGodotPaw(
    origin: Offset,
    R: Float,
    on: Float,
    rotDeg: Float,
    a: Float,
) {
    val s = R * 0.195f * (0.80f + 0.20f * on)
    val o = origin + Offset(0f, R * 0.10f * (1f - on))
    softGlow(o, s * 2.4f, PawColor, 0.10f * on * a)
    drawOval(
        color = PawColor.copy(alpha = 0.85f * on * a),
        topLeft = Offset(o.x - s * 0.7f, o.y - s * 0.4f),
        size = androidx.compose.ui.geometry.Size(s * 1.4f, s * 0.85f),
    )
    for (i in -1..1) {
        drawCircle(
            color = PadColor.copy(alpha = 0.75f * on * a),
            radius = s * 0.26f,
            center = Offset(o.x + i * s * 0.48f, o.y - s * 0.65f),
        )
    }
    drawCircle(
        color = PadColor.copy(alpha = 0.7f * on * a),
        radius = s * 0.32f,
        center = o + Offset(0f, s * 0.08f),
    )
}

private fun DrawScope.drawGodotTail(
    origin: Offset,
    R: Float,
    pose: MotionPose,
    t: Float,
    a: Float,
) {
    val base = origin + Offset(R * 0.55f, R * 0.15f)
    val wag = sin(t * 3.2f) * 0.5f + pose.offsetX / (R * 0.5f)
    val path = Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(
            base.x + R * 0.35f,
            base.y + R * 0.25f + wag * R * 0.15f,
            base.x + R * 0.15f + wag * R * 0.2f,
            base.y + R * 0.55f,
        )
    }
    drawPath(
        path,
        EyeGreen.copy(alpha = 0.55f * a),
        style = Stroke(width = R * 0.04f, cap = StrokeCap.Round),
    )
}

// ── Mood → channel mapping for continuous Godot idle ─────────────────────

/** Map companion interaction/emotion into Godot channel layers for continuous motion. */
fun godotLayersFor(state: CompanionState): ComposedExpression {
    val interaction = state.interactionState
    val emotion = state.emotion
    val eye = when {
        !state.isCameraEnabled -> EyeL.SLEEPY
        emotion == CompanionEmotion.SLEEPY -> EyeL.SLEEPY
        interaction == InteractionState.LISTENING -> EyeL.ALERT
        interaction == InteractionState.THINKING -> EyeL.HARD
        emotion == CompanionEmotion.HAPPY || emotion == CompanionEmotion.PROUD -> EyeL.SOFT
        emotion == CompanionEmotion.CURIOUS -> EyeL.WIDE
        emotion == CompanionEmotion.ALERT -> EyeL.ALERT
        else -> EyeL.NEUTRAL
    }
    val ear = when {
        interaction == InteractionState.LISTENING -> EarL.FORWARD
        interaction == InteractionState.THINKING -> EarL.BACK
        emotion == CompanionEmotion.SLEEPY -> EarL.DROOP
        emotion == CompanionEmotion.HAPPY -> EarL.FORWARD
        emotion == CompanionEmotion.CURIOUS -> EarL.PERK
        emotion == CompanionEmotion.ALERT -> EarL.ALERT
        else -> EarL.NEUTRAL
    }
    val jaw = when {
        interaction == InteractionState.SPEAKING -> JawL.PULSE
        emotion == CompanionEmotion.HAPPY -> JawL.PARTED
        else -> JawL.SHUT
    }
    val body = when {
        interaction == InteractionState.LISTENING -> Body.LEAN_IN
        interaction == InteractionState.THINKING -> Body.TIP
        emotion == CompanionEmotion.HAPPY -> Body.BOB
        emotion == CompanionEmotion.CURIOUS -> Body.TIP
        else -> Body.DRIFT
    }
    return ComposedExpression(eye, ear, jaw, body, durationSeconds = 9999f, bodyAmp = 0.55f)
}
