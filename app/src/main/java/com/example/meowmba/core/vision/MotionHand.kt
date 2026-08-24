package com.example.meowmba.core.vision

import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Close-up hand follow for a selfie / cover camera.
 *
 * Full-body pose is the wrong detector here: the Fold cover sees a face and a
 * waving hand, not a standing skeleton. Pose then invents a wrist at the
 * bottom of the frame and we "follow" that ghost. Frame-to-frame luma motion
 * outside the face is what a waved hand actually looks like.
 */
object MotionHand {

    fun sampleY(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        step: Int = 4,
    ): ByteArray {
        val nw = (width / step).coerceAtLeast(1)
        val nh = (height / step).coerceAtLeast(1)
        val out = ByteArray(nw * nh)
        val src = buffer.duplicate()
        var i = 0
        for (y in 0 until nh) {
            val row = y * step * rowStride
            for (x in 0 until nw) {
                val idx = row + x * step * pixelStride
                out[i++] = if (idx < src.limit()) src.get(idx) else 0
            }
        }
        return out
    }

    fun meanLuma(sampled: ByteArray): Int {
        if (sampled.isEmpty()) return 0
        var s = 0
        for (b in sampled) s += b.toInt() and 0xFF
        return s / sampled.size
    }

    /**
     * Point to look at in buffer 0..1, or null if not enough pixels moved.
     *
     * Uses the far third of motion (away from [origin], usually the face) so
     * we track the hand tip instead of the arm's center — the centroid lagged
     * a full wave behind the actual hand.
     */
    fun followPoint(
        prev: ByteArray?,
        curr: ByteArray,
        sampleW: Int,
        sampleH: Int,
        origin: Pair<Float, Float> = 0.5f to 0.5f,
        exclude: Pair<Float, Float>? = null,
        excludeR: Float = 0.30f,
        diffThresh: Int = 36,
        minFrac: Float = 0.05f,
    ): Pair<Float, Float>? {
        val pts = motionPixels(prev, curr, sampleW, sampleH, exclude, excludeR, diffThresh)
            ?: return null
        if (pts.size < sampleW * sampleH * minFrac) return null
        val ranked = pts.sortedByDescending { p ->
            val dx = p.first - origin.first
            val dy = p.second - origin.second
            dx * dx + dy * dy
        }
        val take = (ranked.size / 3).coerceAtLeast(1)
        var sx = 0.0
        var sy = 0.0
        for (i in 0 until take) {
            sx += ranked[i].first
            sy += ranked[i].second
        }
        return (sx / take).toFloat() to (sy / take).toFloat()
    }

    /** Average of every moving pixel. Kept for tests / fallback. */
    fun centroid(
        prev: ByteArray?,
        curr: ByteArray,
        sampleW: Int,
        sampleH: Int,
        exclude: Pair<Float, Float>? = null,
        excludeR: Float = 0.22f,
        diffThresh: Int = 28,
        minFrac: Float = 0.018f,
    ): Pair<Float, Float>? {
        val pts = motionPixels(prev, curr, sampleW, sampleH, exclude, excludeR, diffThresh)
            ?: return null
        if (pts.size < sampleW * sampleH * minFrac) return null
        var sx = 0.0
        var sy = 0.0
        for (p in pts) {
            sx += p.first
            sy += p.second
        }
        return (sx / pts.size).toFloat() to (sy / pts.size).toFloat()
    }

    private fun motionPixels(
        prev: ByteArray?,
        curr: ByteArray,
        sampleW: Int,
        sampleH: Int,
        exclude: Pair<Float, Float>?,
        excludeR: Float,
        diffThresh: Int,
    ): ArrayList<Pair<Float, Float>>? {
        if (prev == null || prev.size != curr.size || sampleW <= 0 || sampleH <= 0) return null
        val out = ArrayList<Pair<Float, Float>>(64)
        val r2 = excludeR * excludeR
        for (y in 0 until sampleH) {
            val row = y * sampleW
            for (x in 0 until sampleW) {
                val i = row + x
                val d = (curr[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF)
                if (abs(d) < diffThresh) continue
                val nx = (x + 0.5f) / sampleW
                val ny = (y + 0.5f) / sampleH
                if (exclude != null) {
                    val dx = nx - exclude.first
                    val dy = ny - exclude.second
                    if (dx * dx + dy * dy < r2) continue
                }
                out += nx to ny
            }
        }
        return out
    }
}
