package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry helpers ported from the Godot Cat Assistant `cat_draw.gd`.
 * Compose is also Y-down, so the math matches without flipping.
 */
object GodotCatDraw {

    fun ellipse(
        center: Offset,
        rx: Float,
        ry: Float,
        segments: Int = 48,
        startAngle: Float = 0f,
        endAngle: Float = (PI * 2).toFloat(),
    ): Path {
        val segs = max(3, segments)
        val span = endAngle - startAngle
        return Path().apply {
            for (i in 0..segs) {
                val t = i.toFloat() / segs
                val a = startAngle + span * t
                val p = Offset(center.x + cos(a) * rx, center.y + sin(a) * ry)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
    }

    fun ellipsePoints(
        center: Offset,
        rx: Float,
        ry: Float,
        segments: Int = 48,
        startAngle: Float = 0f,
        endAngle: Float = (PI * 2).toFloat(),
    ): List<Offset> {
        val segs = max(3, segments)
        val span = endAngle - startAngle
        return (0..segs).map { i ->
            val t = i.toFloat() / segs
            val a = startAngle + span * t
            Offset(center.x + cos(a) * rx, center.y + sin(a) * ry)
        }
    }

    /** Rounded isosceles triangle — ear shell. Apex points along -Y (up). */
    fun roundedTriangle(
        center: Offset,
        halfWidth: Float,
        height: Float,
        roundAmount: Float = 0.34f,
        segments: Int = 26,
    ): Path {
        val segs = max(3, segments)
        return Path().apply {
            for (i in 0..segs) {
                val t = i.toFloat() / segs
                val linear = 1f - t
                val dome = sqrt(max(0f, 1f - t * t))
                val w = halfWidth * (linear * (1f - roundAmount) + dome * roundAmount)
                val p = Offset(center.x - w, center.y - height * t)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            for (i in segs downTo 0) {
                val t = i.toFloat() / segs
                val linear = 1f - t
                val dome = sqrt(max(0f, 1f - t * t))
                val w = halfWidth * (linear * (1f - roundAmount) + dome * roundAmount)
                lineTo(center.x + w, center.y - height * t)
            }
            close()
        }
    }

    /**
     * Angular feline eye aperture — from Godot `CatDraw.almond`.
     * Linear |x| falloff so edges can be knife-sharp.
     */
    fun almond(
        center: Offset,
        halfW: Float,
        upH: Float,
        lowH: Float,
        upPower: Float = 0.92f,
        lowPower: Float = 0.55f,
        peakShift: Float = 0f,
        segments: Int = 48,
        rotation: Float = 0f,
        edgeNUp: Float = 6f,
        edgeNLow: Float = 1.9f,
    ): Path {
        val nUp = max(1.05f, edgeNUp)
        val nLow = max(1.05f, edgeNLow)
        val segs = max(8, segments)
        val raw = ArrayList<Offset>(segs * 2)
        for (i in 0..segs) {
            val x = -halfW + 2f * halfW * i / segs
            val u = (1f - abs(x / halfW).pow(nUp)).coerceIn(0f, 1f)
            val skew = 1f + peakShift * (x / halfW)
            raw.add(Offset(x, -upH * u.pow(upPower) * skew))
        }
        for (i in (segs - 1) downTo 1) {
            val x = -halfW + 2f * halfW * i / segs
            val u = (1f - abs(x / halfW).pow(nLow)).coerceIn(0f, 1f)
            raw.add(Offset(x, lowH * u.pow(lowPower)))
        }
        val cosR = cos(rotation)
        val sinR = sin(rotation)
        return Path().apply {
            raw.forEachIndexed { idx, p ->
                val q = Offset(
                    center.x + p.x * cosR - p.y * sinR,
                    center.y + p.x * sinR + p.y * cosR,
                )
                if (idx == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
            }
            close()
        }
    }

    fun place(path: Path, origin: Offset, rot: Float): Path {
        // Path transform via manual point rebuild is awkward; callers build at origin.
        // Keep helper for lists.
        return path
    }

    fun placePoints(points: List<Offset>, origin: Offset, rot: Float): List<Offset> {
        val cosR = cos(rot)
        val sinR = sin(rot)
        return points.map { p ->
            Offset(
                origin.x + p.x * cosR - p.y * sinR,
                origin.y + p.x * sinR + p.y * cosR,
            )
        }
    }

    fun pathFrom(points: List<Offset>, close: Boolean = true): Path = Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        if (close) close()
    }

    fun smoothPath(control: List<Offset>, count: Int): List<Offset> {
        val n = control.size
        if (n == 0 || count < 2) return emptyList()
        if (n == 1) return List(count) { control[0] }
        val out = ArrayList<Offset>(count)
        for (i in 0 until count) {
            val t = i.toFloat() / (count - 1) * (n - 1)
            val seg = min(n - 2, max(0, floor(t).toInt()))
            val f = t - seg
            val p0 = control[max(0, seg - 1)]
            val p1 = control[seg]
            val p2 = control[seg + 1]
            val p3 = control[min(n - 1, seg + 2)]
            val f2 = f * f
            val f3 = f2 * f
            out.add(
                Offset(
                    0.5f * ((2f * p1.x) + (-p0.x + p2.x) * f +
                        (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * f2 +
                        (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * f3),
                    0.5f * ((2f * p1.y) + (-p0.y + p2.y) * f +
                        (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * f2 +
                        (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * f3),
                ),
            )
        }
        return out
    }

    fun smoothstep01(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun deg(d: Float): Float = (d * PI / 180.0).toFloat()
}
