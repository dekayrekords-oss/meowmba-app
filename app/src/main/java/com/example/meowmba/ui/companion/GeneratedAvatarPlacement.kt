package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** A single measured root for generated paint and generous pet hit targets. */
internal data class GeneratedAvatarPlacement(val center: Offset, val unit: Float) {
    fun owns(point: Offset): Boolean = point.x.isFinite() && point.y.isFinite() &&
        abs(point.x - center.x) <= unit * .85f && abs(point.y - center.y) <= unit * .85f
}

internal fun generatedAvatarPlacement(width: Float, height: Float, frame: LumenFrame,
    scale: Float, habitat: FullFlyPose? = null): GeneratedAvatarPlacement {
    val w = width.coerceAtLeast(1f)
    val h = height.coerceAtLeast(1f)
    val size = minOf(w, h)
    val zoom = scale.takeIf { it.isFinite() }?.coerceIn(.5f, 3f) ?: 2f
    val unit = size * .42f * zoom * if (habitat == null) 1f else .55f
    val intended = habitat?.let { Offset(it.x * w, it.y * h) } ?: lumenCenter(w, h, frame)
    val margin = minOf(unit * .55f, size * .47f)
    return GeneratedAvatarPlacement(Offset(intended.x.coerceIn(margin, w - margin),
        intended.y.coerceIn(margin, h - margin)), unit)
}
