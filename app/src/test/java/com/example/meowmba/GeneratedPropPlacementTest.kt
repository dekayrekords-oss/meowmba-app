package com.example.meowmba

import com.example.meowmba.ui.companion.GeneratedAvatarMotion
import com.example.meowmba.ui.companion.GeneratedAvatarPart
import com.example.meowmba.ui.companion.GeneratedAvatarShape
import com.example.meowmba.ui.companion.GeneratedPropCategory
import com.example.meowmba.ui.companion.GeneratedPropForm
import com.example.meowmba.ui.companion.GeneratedPropForms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedPropPlacementTest {
    private fun draft(
        name: String,
        category: GeneratedPropCategory,
        description: String = "",
        anchorY: Float = .22f,
    ): GeneratedPropForm {
        val form = GeneratedPropForm(
            id = "",
            name = name,
            description = description,
            category = category,
            useHint = "test",
            parts = listOf(
                GeneratedAvatarPart(
                    GeneratedAvatarShape.ELLIPSE, 0f, 0f, .3f, .12f, 0xFF88AACC,
                    motion = GeneratedAvatarMotion.STILL,
                ),
            ),
            anchorX = 0f,
            anchorY = anchorY,
        )
        return GeneratedPropForms.parse(GeneratedPropForms.toJson(form))
    }

    @Test fun glassesDefaultNearEyes() {
        val glasses = GeneratedPropForms.withSmartDefaults(
            draft("Blue sunglasses", GeneratedPropCategory.WEARABLE, "round sunglasses"),
        )
        assertEquals(-.12f, glasses.anchorY, 0.001f)
        assertTrue(glasses.attachScale in .3f..2.5f)
    }

    @Test fun placementRoundTripPersistsScaleAndRotation() {
        val base = draft("Paper umbrella", GeneratedPropCategory.HELD)
        val moved = GeneratedPropForms.withPlacement(base, .1f, -.05f, 1.4f, 25f)
        val again = GeneratedPropForms.parse(GeneratedPropForms.toJson(moved))
        assertEquals(.1f, again.anchorX, 0.001f)
        assertEquals(-.05f, again.anchorY, 0.001f)
        assertEquals(1.4f, again.attachScale, 0.001f)
        assertEquals(25f, again.attachRotation, 0.001f)
        assertEquals(moved.id, again.id)
    }

    @Test fun heldDefaultNearHands() {
        val (x, y) = GeneratedPropForms.defaultAttach(GeneratedPropCategory.HELD, "umbrella")
        assertEquals(.22f, x, 0.001f)
        assertEquals(.22f, y, 0.001f)
    }
}
