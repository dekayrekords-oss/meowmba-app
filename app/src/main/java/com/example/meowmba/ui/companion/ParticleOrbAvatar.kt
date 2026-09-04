package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Jarvis particle orb — the Meow OS avatar, ported from the server.
 *
 * Source: `jarvis_mode.js` on the MEOW server
 * (`~/memba-server/meow_os/jarvis_mode.js`, "JARVIS mode — black-field
 * parameter-particle orb"): a floating sphere where each particle is a model
 * parameter. Fibonacci-sphere seeds in 7 shells, per-particle tumble rates,
 * depth-sorted cyan→violet dots, pulse rings, a bright nucleus, lat/long
 * wireframe, and — while thinking — particles eject with trails toward the
 * reply ("◈ INFERENCE" caption; "N PARAMETERS" at rest).
 *
 * This file mirrors that behavior on the phone's draw clock. The pure parts
 * (seeds, rotation, caption) live in [ParticleOrbContent] and are JVM
 * unit-tested; [ParticleOrbSim] holds the per-frame sim state (slot order,
 * flyers) allocation-free across frames, exactly like the JS slots array.
 * Text labels use a passed-in [TextMeasurer] — a handful per frame.
 */
object ParticleOrbContent {

    /** Parameter names, one per particle (mirrors PARAM_NAMES in jarvis_mode.js). */
    val PARAM_NAMES: List<String> = listOf(
        "attn.q", "attn.k", "attn.v", "attn.o", "mlp.up", "mlp.down", "mlp.gate",
        "ln1.w", "ln2.w", "emb.tok", "emb.pos", "rope.th", "head.proj", "kv.cache",
        "temp", "top_p", "ctx", "layer", "resid", "bias", "scale", "norm",
        "latent", "logit", "softmax", "gelu", "silu", "dropout", "mask", "score",
        "memory", "skill", "tool", "route", "plan", "act", "obs", "reward",
        "Wq", "Wk", "Wv", "Wo", "ffn", "h0", "h1", "lora.a", "lora.b",
    )

    fun paramName(i: Int): String =
        PARAM_NAMES[i % PARAM_NAMES.size] + "." + (i % 97)

    /** Caption under the sphere. Offline is stated, never faked as active. */
    fun captionFor(particleCount: Int, thinking: Boolean, offline: Boolean): String = when {
        offline -> "OFFLINE"
        thinking -> "◈ INFERENCE"
        else -> "$particleCount PARAMETERS"
    }

    data class ParticleSpec(
        val dx: Float, val dy: Float, val dz: Float,
        val shell: Int,
        /** Shell radius fraction 0.42..0.97. */
        val radiusFrac: Float,
        val spinY: Float, val spinX: Float, val spinZ: Float,
        val size: Float,
        /** 156..268 — cyan into blue/violet. */
        val hue: Float,
        val phase: Float,
    )

    /** Deterministic seeds (fixed seed → stable face, no per-frame allocation). */
    fun seedParticles(n: Int, seed: Long = 0x5EEDL): List<ParticleSpec> {
        val rnd = Random(seed)
        val golden = PI * (3 - sqrt(5.0))
        return List(n) { i ->
            val shell = 1 + (i % 7)
            val y = 1 - (i / maxOf(1, n - 1).toDouble()) * 2
            val rad = sqrt(maxOf(0.0, 1 - y * y))
            val theta = golden * i
            ParticleSpec(
                dx = (cos(theta) * rad).toFloat(),
                dy = y.toFloat(),
                dz = (sin(theta) * rad).toFloat(),
                shell = shell,
                radiusFrac = (0.42 + (shell / 7.0) * 0.55 + (rnd.nextFloat() - 0.5) * 0.03).toFloat(),
                spinY = 0.28f + rnd.nextFloat() * 0.5f,
                spinX = 0.10f + rnd.nextFloat() * 0.32f,
                spinZ = 0.06f + rnd.nextFloat() * 0.22f,
                size = 1.4f + rnd.nextFloat() * 2.6f + (if (shell <= 2) 1f else 0f),
                hue = 170f + shell * 14f + (i % 28) - 14f,
                phase = rnd.nextFloat() * (2 * PI).toFloat(),
            )
        }
    }

    /**
     * Tumble a unit direction (mirrors rotateVec in jarvis_mode.js):
     * Y spin, then X spin, then a slower Z roll.
     */
    fun rotateDir(
        dx: Float, dy: Float, dz: Float,
        spinY: Float, spinX: Float, spinZ: Float, t: Float,
    ): Triple<Float, Float, Float> {
        val sy = (t * spinY).toDouble()
        val x1 = dx * cos(sy) + dz * sin(sy)
        val z1 = -dx * sin(sy) + dz * cos(sy)
        val sx = (t * spinX).toDouble()
        val y2 = dy * cos(sx) - z1 * sin(sx)
        val z2 = dy * sin(sx) + z1 * cos(sx)
        val sz = (t * spinZ * 0.6).toDouble()
        return Triple(
            (x1 * cos(sz) - y2 * sin(sz)).toFloat(),
            (x1 * sin(sz) + y2 * cos(sz)).toFloat(),
            z2.toFloat(),
        )
    }
}

/** Ejected think-particle with a fading trail (mirrors the JS flyers). */
private data class Flyer(
    var x: Float, var y: Float, var tx: Float, var ty: Float,
    var vx: Float, var vy: Float, var life: Float,
    var size: Float, var hue: Float, var label: String?,
    val trail: ArrayDeque<Offset> = ArrayDeque(),
)

/**
 * Persistent sim state for one orb. Created with `remember` beside the canvas
 * and drawn inside the draw lambda — no composition reads, no allocation in
 * the hot loop beyond a few trail points.
 */
class ParticleOrbSim(val n: Int = 650) {

    private val specs = ParticleOrbContent.seedParticles(n)
    private val order = IntArray(n) { it }
    private val px = FloatArray(n)
    private val py = FloatArray(n)
    private val pz = FloatArray(n)
    private val flyers = ArrayList<Flyer>()
    private var lastT = Float.NaN

    fun draw(
        scope: DrawScope,
        measurer: TextMeasurer,
        center: Offset,
        domeRadius: Float,
        thinking: Boolean,
        level: Float,
        clockSec: Float,
        ringColor: Color,
        alpha: Float,
        offline: Boolean,
    ) {
        with(scope) {
            val dt = if (lastT.isNaN()) 0.016f else (clockSec - lastT).coerceIn(0f, 0.05f)
            lastT = clockSec
            val lvl = level.coerceIn(0f, 1f)
            val think = if (thinking) 1f else 0f
            val baseR = domeRadius * 0.80f
            // Reference sizes are px at baseR ≈ 0.38·minWH; rescale to our dome.
            val k = domeRadius / 300f
            val a = alpha * if (offline) 0.6f else 1f
            val cyan = if (offline) Color(0xFFF85149) else ringColor
            val violet = if (offline) Color(0xFFF85149) else Color(0xFFA78BFA)

            // ── Volumetric halo (concentric washes, no per-frame shader) ──
            drawCircle(cyan.copy(alpha = 0.05f * a), baseR * 1.15f, center)
            drawCircle(cyan.copy(alpha = 0.07f * a), baseR * 0.80f, center)
            drawCircle(cyan.copy(alpha = 0.08f * a), baseR * 0.50f, center)

            // ── Outer pulse rings ──
            for (i in 0 until 4) {
                val pulseR = baseR * (0.55f + i * 0.22f) +
                    sin(clockSec * 1.1f + i) * (domeRadius * 0.02f + lvl * domeRadius * 0.06f)
                drawCircle(
                    cyan.copy(alpha = (0.05f + (1 - i / 4f) * 0.07f + lvl * 0.06f) * a),
                    pulseR.coerceAtLeast(8f), center,
                    style = Stroke(width = 1.4f * k.coerceAtLeast(0.8f)),
                )
            }

            // ── Bright nucleus (grows with voice + thought) ──
            val nr = baseR * 0.16f * (1 + lvl * 0.12f + think * 0.18f)
            drawCircle(Color.White.copy(alpha = 0.95f * a), nr * 0.45f, center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (thinking) violet else cyan).copy(alpha = 0.85f * a),
                        Color.Transparent,
                    ),
                    center = center, radius = nr * 2.2f,
                ),
                radius = nr * 2.2f, center = center,
            )

            // ── Lat/long wireframe for volume ──
            for (s in 1..5) {
                val rr = baseR * (0.35f + s * 0.13f)
                rotate(clockSec * 2.8f * s.toFloat(), pivot = center) {
                    drawOval(
                        cyan.copy(alpha = (0.07f + think * 0.05f) * a),
                        topLeft = Offset(center.x - rr * (1 + lvl * 0.04f), center.y - rr * 0.72f),
                        size = Size(rr * 2 * (1 + lvl * 0.04f), rr * 2 * 0.72f),
                        style = Stroke(width = 1f),
                    )
                }
            }
            for (m in 0 until 6) {
                val ang = clockSec * 0.15f + m * PI.toFloat() / 3
                rotate(ang * 57.3f, pivot = center) {
                    val wHalf = baseR * 0.95f * kotlin.math.abs(cos(ang * 0.5f)) + baseR * 0.2f
                    drawOval(
                        Color(0xFF78B4FF).copy(alpha = (0.04f + think * 0.04f) * a),
                        topLeft = Offset(center.x - wHalf, center.y - baseR * 0.95f),
                        size = Size(wHalf * 2, baseR * 2 * 0.95f),
                        style = Stroke(width = 1f),
                    )
                }
            }

            // ── Particles: rotate, project, depth-sort back→front ──
            val t = clockSec
            for (idx in 0 until n) {
                val p = specs[idx]
                val (x, y, z) = ParticleOrbContent.rotateDir(
                    p.dx, p.dy, p.dz, p.spinY, p.spinX, p.spinZ, t + p.phase,
                )
                val rr = baseR * p.radiusFrac * (1 + lvl * 0.06f + think * 0.08f)
                px[idx] = center.x + x * rr
                py[idx] = center.y + y * rr * 0.9f
                pz[idx] = z
            }
            // Insertion sort on the persistent order: z changes little per
            // frame, so this runs ~O(n) on nearly-sorted data (as in the JS).
            for (i in 1 until n) {
                val s = order[i]
                var j = i - 1
                while (j >= 0 && pz[order[j]] > pz[s]) {
                    order[j + 1] = order[j]
                    j--
                }
                order[j + 1] = s
            }
            val labelStyle = TextStyle(
                color = Color(0xFFB4E6FF).copy(alpha = 0.8f * a),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            )
            // Labels cluster along the line of sight through the sphere's
            // middle, so a minimum on-screen distance keeps them readable
            // instead of printing over each other.
            var lastLabelX = Float.NaN
            var lastLabelY = Float.NaN
            val minLabelDist = domeRadius * 0.20f
            for (rank in 0 until n) {
                val idx = order[rank]
                val p = specs[idx]
                val depth = 0.35f + 0.65f * ((pz[idx] + 1f) * 0.5f)
                val sz = (p.size * 1.55f * depth * (1 + think * 0.35f + lvl * 0.25f) * k)
                    .coerceAtLeast(1.2f)
                val light = 45 + ((depth * 20).toInt() / 20f) * 35
                drawCircle(
                    Color.hsv(p.hue, 0.95f, (light / 100f).coerceIn(0f, 1f), (0.25f + depth * 0.7f) * a),
                    sz, Offset(px[idx], py[idx]),
                )
                if (depth > 0.65f) {
                    drawCircle(
                        Color.White.copy(alpha = 0.5f * a),
                        sz * 0.4f, Offset(px[idx] - sz * 0.25f, py[idx] - sz * 0.25f),
                    )
                }
                if (depth > 0.72f && rank % 28 == 0 &&
                    (lastLabelX.isNaN() || kotlin.math.hypot(
                        px[idx] - lastLabelX,
                        py[idx] - lastLabelY,
                    ) > minLabelDist)
                ) {
                    lastLabelX = px[idx]
                    lastLabelY = py[idx]
                    val name = ParticleOrbContent.paramName(idx)
                    val measured = measurer.measure(name, labelStyle)
                    drawText(
                        measured, Color(0xFFB4E6FF).copy(alpha = (0.45f + depth * 0.4f) * a),
                        topLeft = Offset(px[idx] - measured.size.width / 2f, py[idx] - sz - 5 - measured.size.height),
                    )
                }
                // Thinking ejects particles toward the reply line.
                if (thinking && !offline && flyers.size < 40 && Math.random() < 0.004) {
                    val tgtX = center.x + (Math.random() - 0.5).toFloat() * domeRadius * 0.6f
                    val tgtY = center.y + baseR * 1.45f + (Math.random() - 0.5).toFloat() * domeRadius * 0.2f
                    flyers.add(
                        Flyer(
                            x = px[idx], y = py[idx], tx = tgtX, ty = tgtY,
                            vx = (Math.random() - 0.5).toFloat() * 8,
                            vy = (-3 - Math.random() * 5).toFloat(),
                            life = 1f, size = sz + 1.2f, hue = p.hue,
                            label = if (Math.random() < 0.22f) p.let { ParticleOrbContent.paramName(idx) } else null,
                        ),
                    )
                }
            }

            // ── Flyers: spring toward target with fading trails ──
            val steps = (dt * 60).toInt().coerceIn(1, 3)
            val flyerStyle = TextStyle(
                color = Color(0xFFD2F0FF).copy(alpha = 0.9f * a),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            )
            for (i in flyers.size - 1 downTo 0) {
                val f = flyers[i]
                repeat(steps) {
                    f.trail.addLast(Offset(f.x, f.y))
                    if (f.trail.size > 8) f.trail.removeFirst()
                    f.vx += (f.tx - f.x) * 0.045f
                    f.vy += (f.ty - f.y) * 0.045f
                    f.vx *= 0.88f
                    f.vy *= 0.88f
                    f.x += f.vx
                    f.y += f.vy
                    f.life -= 0.013f
                }
                for (j in 1 until f.trail.size) {
                    val p0 = f.trail[j - 1]
                    val p1 = f.trail[j]
                    drawLine(
                        Color.hsv(f.hue, 0.95f, 0.72f, (0.08f * j * f.life) * a),
                        p0, p1, strokeWidth = 1.6f,
                    )
                }
                drawCircle(
                    Color.hsv(f.hue, 1f, 0.78f, f.life.coerceIn(0f, 1f) * a),
                    f.size * (0.9f + f.life * 0.5f), Offset(f.x, f.y),
                )
                if (f.label != null && f.life > 0.35f) {
                    val measured = measurer.measure(f.label!!, flyerStyle)
                    drawText(
                        measured, Color(0xFFD2F0FF).copy(alpha = f.life * a),
                        topLeft = Offset(f.x - measured.size.width / 2f, f.y - 12 - measured.size.height),
                    )
                }
                if (min(
                        kotlin.math.abs(f.tx - f.x),
                        kotlin.math.abs(f.ty - f.y),
                    ) < 10f
                ) f.life -= 0.06f * steps
                if (f.life <= 0f) flyers.removeAt(i)
            }

            // ── Caption under the sphere ──
            val caption = ParticleOrbContent.captionFor(
                n, thinking,
                offline,
            )
            val captionStyle = TextStyle(
                color = (if (thinking) violet else Color(0xFF96E6FF)).copy(alpha = 0.85f * a),
                fontSize = 13.sp, fontFamily = FontFamily.Monospace,
            )
            val measured = measurer.measure(caption, captionStyle)
            drawText(
                measured, captionStyle.color,
                topLeft = Offset(
                    center.x - measured.size.width / 2f,
                    center.y + baseR * 1.08f,
                ),
            )
        }
    }
}
