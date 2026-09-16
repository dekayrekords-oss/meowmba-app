package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.runtime.CommandHeuristics
import com.example.meowmba.ui.companion.*
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class CharacterFormsTest {
    private val added = listOf(AvatarAnimal.RABBIT, AvatarAnimal.PIG, AvatarAnimal.ALIEN, AvatarAnimal.ROBOT, AvatarAnimal.PANDA)

    @Test fun newCharactersAndAliasesRouteAndResetWithoutInventingOtherSpecies() {
        for (animal in added) {
            assertEquals(animal, AvatarAnimal.resolve(animal.key))
            val request = "be ${if (animal == AvatarAnimal.ALIEN) "an" else "a"} ${animal.key}"
            assertEquals("avatar.form", AgentRouter.route(request)?.toolId)
            assertTrue(CommandHeuristics.mightBeCommand(request))
            assertEquals("none", AgentRouter.route("stop being ${if (animal == AvatarAnimal.ALIEN) "an" else "a"} ${animal.key}")?.args?.get("animal"))
            assertNotEquals("avatar.form", AgentRouter.route("don't $request")?.toolId)
        }
        assertEquals(AvatarAnimal.RABBIT, AvatarAnimal.resolve("bunny"))
        assertEquals(AvatarAnimal.PANDA, AvatarAnimal.resolve("panda bear"))
        assertNull(AvatarAnimal.resolve("red panda"))
    }

    @Test fun everyFormHasDistinctEyesAndCustomizedColorsStillWin() {
        val shapes = AvatarAnimal.entries.map { lumenEyeShape(AvatarFormProfiles.traits(it)) }
        assertEquals(AvatarAnimal.entries.size, shapes.distinct().size)
        val cat = lumenEyeShape(AvatarFormProfiles.traits(AvatarAnimal.CAT))
        val owl = lumenEyeShape(AvatarFormProfiles.traits(AvatarAnimal.OWL))
        assertTrue(cat.almond > .7f && cat.pupilAspect < .4f)
        assertTrue(owl.almond == 0f && owl.pupilAspect == 1f)
        val custom = Color(0xFFAD77EE)
        for (animal in AvatarAnimal.entries) assertEquals(custom, lumenIrisColor(AvatarFormProfiles.traits(animal), custom))
        assertNotEquals(lumenIrisColor(AvatarFormProfiles.traits(AvatarAnimal.CAT), Color(AvatarColors.DEFAULT_EYE)),
            lumenIrisColor(AvatarFormProfiles.traits(AvatarAnimal.OWL), Color(AvatarColors.DEFAULT_EYE)))
    }

    @Test fun portraitEnlargesMoreThanLandscapeAndMorphsReturnToOriginalEyes() {
        val base = LumenFrame(form = AvatarFormProfiles.traits(AvatarAnimal.CAT), cat = 1f)
        val oldPortrait = 360f * .74f * .86f
        val oldLandscape = 500f * .86f
        assertEquals(1.26f, lumenUnit(360f, 800f, base) / oldPortrait, .001f)
        assertEquals(1.08f, lumenUnit(1600f, 500f, base) / oldLandscape, .001f)
        for (animal in added) {
            val rig = LumenRig(); var frame = LumenFrame()
            repeat(180) { frame = rig.step(it / 60f, LumenInput(animal = animal)) }
            assertTrue(frame.form.costume > .999f)
            val previous = frame
            val changed = rig.step(3f, LumenInput(animal = AvatarAnimal.NONE))
            assertTrue(kotlin.math.abs(lumenEyeShape(previous.form).height - lumenEyeShape(changed.form).height) < .02f)
            repeat(240) { frame = rig.step(3f + (it + 1) / 60f, LumenInput(animal = AvatarAnimal.NONE)) }
            assertTrue(frame.form.costume < .0001f)
            assertEquals(1f, lumenEyeShape(frame.form).width, .0001f)
        }
    }
}
