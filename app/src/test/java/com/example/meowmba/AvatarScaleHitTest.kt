package com.example.meowmba

import androidx.compose.ui.geometry.Offset
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test

class AvatarScaleHitTest {
    @Test fun scaledLumenEyesKeepTheirInteractionZones() {
        for ((width, height) in listOf(320 to 640, 768 to 540)) {
            val frame = LumenFrame()
            val center = lumenCenter(width.toFloat(), height.toFloat(), frame)
            val unit = lumenUnit(width.toFloat(), height.toFloat(), frame)
            for (scale in listOf(.5f, 1f, 1.5f, 2f)) {
                val transform = AvatarHitTransform(center, center, scale)
                for (side in listOf(-1f, 1f)) {
                    val eye = center + Offset(side * unit * .213f, 0f)
                    val visible = center + (eye - center) * scale
                    val expected = if (side < 0) AvatarZone.EYE_LEFT else AvatarZone.EYE_RIGHT
                    assertEquals(expected, lumenZoneAt(transform.inverse(visible), width, height, frame))
                }
            }
        }
    }

    @Test fun habitatTravelAndSizeBothParticipateInInverseHitMapping() {
        val original = Offset(160f, 290f)
        val destination = Offset(85f, 170f)
        for (scale in listOf(.2f, .4f, .8f)) {
            val transform = AvatarHitTransform(original, destination, scale)
            for (local in listOf(Offset(-70f, 0f), Offset(70f, 0f), Offset(0f, -90f))) {
                val visible = destination + local * scale
                val result = transform.inverse(visible)
                assertEquals(original.x + local.x, result.x, .0001f)
                assertEquals(original.y + local.y, result.y, .0001f)
            }
        }
    }

    @Test fun identityLeavesExistingPointerCoordinatesUnchanged() {
        val point = Offset(123f, 456f)
        assertEquals(point, AvatarHitTransform().inverse(point))
    }
}
