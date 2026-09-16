package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AvatarFormRequest
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test

class FruitFlyAvatarTest {
    @Test fun explicitRequestsResolveAndUnrelatedFlightDoesNot() {
        for (name in listOf("fruit fly", "fruit-fly", "fruitfly", "drosophila")) {
            assertEquals(AvatarAnimal.FRUIT_FLY, AvatarAnimal.resolve(name))
            assertEquals(name, AvatarFormRequest.parse("become a $name"))
        }
        assertEquals(AvatarAnimal.FRUIT_FLY, AvatarAnimal.fromKey("fruit_fly"))
        assertEquals("none", AvatarFormRequest.parse("stop being a fruit fly"))
        assertNull(AvatarFormRequest.parse("fly to Denver"))
        assertNull(AvatarFormRequest.parse("become a fruit fly and send a message"))
    }

    @Test fun flyMorphKeepsSpeechGazeAndBlinkOnTheExistingClockAndClearsOnReset() {
        val fly = LumenRig(22); val baseline = LumenRig(22)
        var frame = LumenFrame()
        repeat(240) { i ->
            val input = LumenInput(.5f, -.3f, true, animal = AvatarAnimal.FRUIT_FLY, speaking = .7f)
            frame = fly.step(i / 60f, input)
            val other = baseline.step(i / 60f, input.copy(animal = AvatarAnimal.NONE))
            assertEquals(other.gazeX, frame.gazeX, 0f)
            assertEquals(other.leftOpen, frame.leftOpen, 0f)
            assertEquals(other.mouthOpen, frame.mouthOpen, 0f)
            assertEquals(frame, fly.step(i / 60f, input))
        }
        assertTrue(frame.form.fruitFly > .99f)
        assertEquals(0f, frame.form.shortMuzzle + frame.form.longMuzzle, 0f)
        repeat(240) { frame = fly.step(4f + it / 60f, LumenInput(animal = AvatarAnimal.NONE)) }
        assertTrue(frame.form.fruitFly < .0001f)
        assertTrue(frame.form.costume < .0001f)
    }
}
