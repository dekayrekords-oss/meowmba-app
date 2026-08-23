package com.example.meowmba.ui.companion

/**
 * Drag offset for the live camera PiP. Offset is from the window's resting
 * BottomCenter slot, in pixels.
 */
object PreviewWindow {
    fun clamp(
        x: Float,
        y: Float,
        winW: Float,
        winH: Float,
        parentW: Float,
        parentH: Float,
    ): Pair<Float, Float> {
        val maxX = (parentW - winW).coerceAtLeast(0f)
        val maxY = (parentH - winH).coerceAtLeast(0f)
        return x.coerceIn(0f, maxX) to y.coerceIn(0f, maxY)
    }
}
