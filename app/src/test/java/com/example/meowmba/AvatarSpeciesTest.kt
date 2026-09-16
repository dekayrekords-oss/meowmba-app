package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AvatarSpeciesTest {
    private val species = listOf(AvatarAnimal.AUSTRALIAN_SHEPHERD, AvatarAnimal.OWL, AvatarAnimal.DUCK)

    @Test fun persistentKeysAndWholeAliasesResolveToTheThreeDistinctForms() {
        for (animal in species) {
            assertEquals(animal, AvatarAnimal.fromKey(animal.key))
            assertEquals(animal, AvatarAnimal.resolve(animal.key))
            assertEquals(animal, AvatarAnimal.resolve(animal.label.uppercase()))
        }
        for (alias in listOf("Aussie", "aussie dog", "Australian shepherd dog", "shepherd", "shepherd dog"))
            assertEquals(alias, AvatarAnimal.AUSTRALIAN_SHEPHERD, AvatarAnimal.resolve(alias))
        assertEquals(AvatarAnimal.OWL, AvatarAnimal.resolve("owlet"))
        assertEquals(AvatarAnimal.DUCK, AvatarAnimal.resolve("duckling"))
        assertEquals(AvatarAnimal.AUSTRALIAN_SHEPHERD, AvatarAnimal.performanceFor("Australian shepherd dog"))
        assertEquals(AvatarAnimal.OWL, AvatarAnimal.performanceFor("curious owl"))
        assertEquals(AvatarAnimal.DUCK, AvatarAnimal.performanceFor("happy duckling"))
        assertNull(AvatarAnimal.resolve("shepherd a project"))
        assertNull(AvatarAnimal.resolve("duck the question"))
    }

    @Test fun eachSpeciesUsesItsOwnAnatomyInsteadOfRenamingTheExistingDog() {
        val dog = AvatarFormProfiles.traits(AvatarAnimal.DOG)
        val aussie = AvatarFormProfiles.traits(AvatarAnimal.AUSTRALIAN_SHEPHERD)
        val owl = AvatarFormProfiles.traits(AvatarAnimal.OWL)
        val duck = AvatarFormProfiles.traits(AvatarAnimal.DUCK)
        assertEquals(4, listOf(dog, aussie, owl, duck).distinct().size)
        assertEquals(1f, aussie.shepherdCoat, 0f); assertEquals(1f, aussie.longMuzzle, 0f)
        assertEquals(0f, aussie.floppyEars, 0f) // Its separate folded-ear geometry owns the silhouette.
        assertEquals(1f, owl.owlFeathers, 0f); assertEquals(1f, duck.duckFeathers, 0f)
        for (bird in listOf(owl, duck)) {
            assertEquals(0f, bird.pointedEars + bird.floppyEars + bird.roundEars + bird.longMuzzle + bird.shortMuzzle, 0f)
            assertEquals(1f, bird.costume, 0f)
        }
    }

    @Test fun speciesMorphsKeepTheSameEyesSpeechAttentionAndPausedClock() {
        for (hz in listOf(30, 60, 120)) {
            val reference = LumenRig(901); val changing = LumenRig(901)
            var previous = LumenFrame()
            repeat(hz * 10) { i ->
                val animal = species[(i / (hz / 2)) % species.size]
                val input = LumenInput(.6f, -.3f, true, animal = animal, speaking = .6f,
                    driveWeight = if (i > hz * 4) 1f else 0f)
                val f = changing.step(i.toFloat() / hz, input)
                val base = reference.step(i.toFloat() / hz, input.copy(animal = AvatarAnimal.NONE))
                assertEquals(base.gazeX, f.gazeX, 0f); assertEquals(base.gazeY, f.gazeY, 0f)
                assertEquals(base.leftOpen, f.leftOpen, 0f); assertEquals(base.rightOpen, f.rightOpen, 0f)
                assertEquals(base.mouthOpen, f.mouthOpen, 0f); assertEquals(base.driftX, f.driftX, 0f)
                for ((a, b) in listOf(previous.form.shepherdCoat to f.form.shepherdCoat,
                    previous.form.owlFeathers to f.form.owlFeathers, previous.form.duckFeathers to f.form.duckFeathers)) {
                    assertTrue("continuous species opacity at $hz Hz", abs(b - a) < .10f)
                    assertTrue(b in 0f..1f)
                }
                assertEquals(f, changing.step(i.toFloat() / hz, input.copy(animal = AvatarAnimal.CAT)))
                previous = f
            }
        }
    }

    @Test fun morphingBackToEyesClearsEverySpeciesSpecificPart() {
        for (animal in species) {
            val rig = LumenRig(); var frame = LumenFrame()
            repeat(120) { frame = rig.step(it / 60f, LumenInput(animal = animal)) }
            assertTrue(frame.form.costume > .99f)
            repeat(180) { frame = rig.step(2f + it / 60f, LumenInput(animal = AvatarAnimal.NONE)) }
            assertTrue(frame.form.shepherdCoat < .0001f)
            assertTrue(frame.form.owlFeathers < .0001f)
            assertTrue(frame.form.duckFeathers < .0001f)
            assertTrue(frame.form.costume < .0001f)
        }
    }
}
