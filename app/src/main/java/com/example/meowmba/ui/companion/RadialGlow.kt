package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

/** Analytic gradient, not a bitmap: retains full resolution at every scale.
 * Changing position/radius/opacity no longer allocates a new native shader.
 * Brush cache is bounded and UI-thread confined, like the DrawScopes using it.
 */
internal object RadialGlow {
    private val brushes = object : LinkedHashMap<Color, Brush>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Color, Brush>?) = size > 16
    }

    fun brush(color: Color): Brush = brushes.getOrPut(color.copy(alpha = 1f)) {
        Brush.radialGradient(
            listOf(color.copy(alpha = 1f), color.copy(alpha = 0f)),
            center = Offset.Zero, radius = 1f,
        )
    }
}

internal fun DrawScope.drawRadialGlow(
    center: Offset, radius: Float, brush: Brush, alpha: Float,
) {
    if (radius <= 0f || alpha <= 0f) return
    withTransform({
        translate(center.x, center.y)
        scale(radius, radius, Offset.Zero)
    }) {
        drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = alpha.coerceIn(0f, 1f))
    }
}
