package com.example.meowmba.core.screen

import java.nio.ByteBuffer

/** The final row need not include its padding. Copy only actual RGBA pixels. */
object ScreenPixels {
    fun pack(source: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): ByteArray {
        require(width in 1..4096 && height in 1..4096 && pixelStride >= 4 && rowStride >= width * pixelStride)
        val input = source.duplicate()
        val start = input.position()
        require(start.toLong() + (height - 1L) * rowStride + (width - 1L) * pixelStride + 4 <= input.limit())
        val out = ByteArray(width * height * 4)
        for (y in 0 until height) {
            if (pixelStride == 4) { input.position(start + y * rowStride); input.get(out, y * width * 4, width * 4) }
            else for (x in 0 until width) { input.position(start + y * rowStride + x * pixelStride); input.get(out, (y * width + x) * 4, 4) }
        }
        return out
    }
}
