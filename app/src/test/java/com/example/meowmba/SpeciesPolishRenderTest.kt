package com.example.meowmba

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpeciesPolishRenderTest {
    /** Render anatomy alone: any changed pixels must belong to acting anatomy,
     * not blinking lenses, camera movement, captions or an independent clock. */
    private fun anatomy(frame: LumenFrame): Bitmap {
        val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val species = LumenSpeciesParts(); val animals = LumenAnimalParts()
        val center = Offset(256f, 255f); val unit = 500f
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image.asImageBitmap()), Size(512f, 512f)) {
            species.backdrop(this, center, unit, frame, Color(0xFFB7C1C9), Color.Cyan)
            for (side in listOf(-1, 1)) translate(center.x + side * unit * .213f, center.y) {
                species.eyeSurround(this, side, unit, frame, Color(0xFFB7C1C9))
            }
            animals.muzzle(this, center, unit, frame, Color(0xFFB7C1C9), Color.Cyan)
            species.beak(this, center, unit, frame, Color(0xFFB7C1C9))
        }
        return image
    }

    @Test fun speechAndEmotionAnimateEachSpeciesFacialAnatomyWithoutDependingOnTheEyes() {
        for (animal in listOf(AvatarAnimal.AUSTRALIAN_SHEPHERD, AvatarAnimal.OWL, AvatarAnimal.DUCK)) {
            val neutral = LumenFrame(form = AvatarFormProfiles.traits(animal))
            val rest = anatomy(neutral)
            val speaking = anatomy(neutral.copy(mouthOpen = .8f, smile = .9f, earLift = 3f))
            assertFalse("facial anatomy acts for $animal", rest.sameAs(speaking))
            val repeated = anatomy(neutral)
            assertTrue("same active frame paints identically for $animal", rest.sameAs(repeated))
            rest.recycle(); speaking.recycle(); repeated.recycle()
        }
    }

    @Test fun zeroMorphChannelsLeaveNoResidualSpeciesArt() {
        val blank = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val neutral = anatomy(LumenFrame())
        assertTrue(blank.sameAs(neutral))
        blank.recycle(); neutral.recycle()
    }
}
