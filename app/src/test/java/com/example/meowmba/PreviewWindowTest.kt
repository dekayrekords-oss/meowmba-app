package com.example.meowmba

import com.example.meowmba.ui.companion.PreviewWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewWindowTest {

    @Test
    fun `keeps the pip fully on screen`() {
        val o = PreviewWindow.clamp(
            x = 900f, y = -40f,
            winW = 160f, winH = 120f,
            parentW = 400f, parentH = 800f,
        )
        assertEquals(240f, o.first, 0.1f)
        assertEquals(0f, o.second, 0.1f)
    }

    @Test
    fun `allows the pip to sit in a corner`() {
        val o = PreviewWindow.clamp(
            x = -20f, y = 700f,
            winW = 160f, winH = 120f,
            parentW = 400f, parentH = 800f,
        )
        assertEquals(0f, o.first, 0.1f)
        assertEquals(680f, o.second, 0.1f)
    }
}
