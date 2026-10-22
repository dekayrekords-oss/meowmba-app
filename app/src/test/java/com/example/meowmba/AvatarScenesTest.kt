package com.example.meowmba

import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test

class AvatarScenesTest {
    @Test fun requestedScenesReachTheAvatarWithoutGeneratingMedia() {
        for (phrase in listOf("chase a butterfly", "Meow, play detective", "play with yarn", "play with a ball",
            "play with an acorn", "hide a snack", "curl up in a nest", "could you watch the rain?")) {
            val intent = AgentRouter.route(phrase)
            assertEquals(phrase, "avatar.animate", intent?.toolId)
            assertNotNull(phrase, AvatarPerformanceCatalog.resolve(intent!!.args.getValue("name")))
            assertTrue(phrase, CommandHeuristics.mightBeCommand(phrase))
            assertFalse(phrase, CompanionViewModel.looksLikeDirectQuestion(phrase))
        }
        for (phrase in listOf("a butterfly landed", "don't chase a butterfly", "play with yarn and open Gmail",
            "make a video of a butterfly chase", "be a detective", "I like watching the rain")) {
            assertNull(phrase, AvatarSceneRequest.parse(phrase))
        }
    }

    @Test fun sceneGazeWorksWithCameraOffAndReleasesBackToAttention() {
        for (entry in AvatarPerformanceCatalog.entries.filter { it.category == "Scenes" }) {
            val rig = LumenRig(); var last = LumenFrame(); var totalMovement = 0f
            for (i in 0..360) {
                val p = i / 360f
                val input = lumenPerformanceInput(LumenInput(cameraEnabled = false, performing = true), entry, p)
                assertFalse(input.cameraEnabled)
                assertTrue(input.sceneGazeWeight in 0f..1f)
                val f = rig.step(i / 60f, input)
                totalMovement += kotlin.math.abs(f.gazeX - last.gazeX) + kotlin.math.abs(f.gazeY - last.gazeY)
                assertTrue(entry.id, kotlin.math.abs(f.gazeX - last.gazeX) < .15f)
                last = f
            }
            assertTrue(entry.id, totalMovement > .1f)
            assertEquals(0f, lumenPerformanceInput(LumenInput(), entry, 1f).sceneGazeWeight, 0f)
        }
    }

    @Test fun animalPlayUsesTemporaryAppropriateForms() {
        assertEquals(AvatarAnimal.CAT, AvatarAnimal.performanceFor("Yarn Play"))
        assertEquals(AvatarAnimal.DOG, AvatarAnimal.performanceFor("Ball Play"))
        assertEquals(AvatarAnimal.SQUIRREL, AvatarAnimal.performanceFor("Acorn Play"))
        assertEquals(AvatarAnimal.RACCOON, AvatarAnimal.performanceFor("Mischievous Raccoon"))
        assertNull(AvatarAnimal.performanceFor("Butterfly Chase"))
    }
}
