package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test

class AvatarAutoSizeTest {
    @Test fun fittedAnimalEnvelopeLeavesRoomForControlsOnCoverInnerAndLandscapeScreens() {
        for ((w, h) in listOf(320f to 720f, 412f to 915f, 768f to 432f, 768f to 900f)) {
            for (animal in AvatarAnimal.entries) {
                val scale = autoAvatarScale(w, h, AvatarStyle.LUMEN, animal)
                val frame = LumenFrame(cat = if (animal == AvatarAnimal.CAT) 1f else 0f,
                    dog = if (animal == AvatarAnimal.DOG) 1f else 0f, form = AvatarFormProfiles.traits(animal))
                val e = lumenEnvelope(frame); val u = lumenUnit(w, h, frame) * scale
                assertTrue("$animal horizontal on $w × $h", e.x * u <= w * .401f)
                assertTrue("$animal top on $w × $h", e.top * u <= h * .341f)
                assertTrue("$animal bottom on $w × $h", e.bottom * u <= h * .271f)
            }
        }
    }
    @Test fun allStylesAndBadDimensionsProduceUsableFiniteScales() {
        for (style in AvatarStyle.entries) {
            assertTrue(autoAvatarScale(412f, 915f, style, AvatarAnimal.NONE) in .5f..2.5f)
            assertEquals(1f, autoAvatarScale(Float.NaN, 0f, style, AvatarAnimal.NONE))
        }
    }
}
