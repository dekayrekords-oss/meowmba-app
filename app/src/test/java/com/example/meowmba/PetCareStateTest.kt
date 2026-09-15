package com.example.meowmba

import com.example.meowmba.core.behavior.PetCareState
import org.junit.Assert.*
import org.junit.Test

class PetCareStateTest {
    private val start = 1_000L
    private val hour = 3_600_000L

    @Test fun enabledNeedsDecayAtDocumentedRates() {
        val advanced = PetCareState().withCare(true, start).advance(start + hour)
        assertEquals(.92f, advanced.food, .00001f)
        assertEquals(.96f, advanced.energy, .00001f)
        assertEquals(.95f, advanced.happiness, .00001f)
        assertEquals(.94f, advanced.affection, .00001f)
        assertEquals(.97f, advanced.hygiene, .00001f)
    }

    @Test fun absentPetNeverDiesAndCatchUpIsCapped() {
        val pet = PetCareState().withCare(true, start)
        val day = pet.advance(start + 24 * hour)
        val year = pet.advance(start + 365 * 24 * hour)
        assertEquals(day.food, year.food, 0f)
        assertEquals(day.energy, year.energy, 0f)
        assertEquals(day.happiness, year.happiness, 0f)
        assertEquals(day.affection, year.affection, 0f)
        assertEquals(day.hygiene, year.hygiene, 0f)
        assertTrue(listOf(year.food, year.energy, year.happiness, year.affection, year.hygiene).all { it >= .1f })
        assertEquals(year, year.advance(year.updatedAtMs))
    }

    @Test fun disabledCareNeverAccumulatesNeglectIncludingAcrossRestart() {
        val disabled = PetCareState().withCare(true, start).advance(start + 7 * hour).withCare(false, start + 7 * hour)
        val later = PetCareState.decode(disabled.encode()).advance(start + 600 * hour)
        assertEquals(1f, later.food, 0f)
        assertEquals(1f, later.energy, 0f)
        assertEquals(1f, later.happiness, 0f)
        assertEquals(1f, later.affection, 0f)
        assertEquals(1f, later.hygiene, 0f)
        val enabled = later.withCare(true, start + 601 * hour)
        assertEquals(1f, enabled.food, 0f)
        assertEquals(1f, enabled.energy, 0f)
        assertEquals(1f, enabled.happiness, 0f)
        assertEquals(enabled, enabled.withCare(true, enabled.updatedAtMs))
    }

    @Test fun clockReversalDoesNotDamageOrDoubleChargePet() {
        val pet = PetCareState(true, .7f, .6f, .8f, .75f, .9f, start + 10 * hour)
        assertEquals(pet, pet.advance(start))
        assertEquals(pet, pet.advance(-1))
        assertEquals(.62f, pet.advance(start + 11 * hour).food, .00001f)
        assertEquals(1f, PetCareState(true).advance(Long.MAX_VALUE).food, 0f)
    }

    @Test fun actionsAffectNeedsWithinBounds() {
        val pet = PetCareState(true, .3f, .3f, .3f, .3f, .3f, start)
        assertEquals(.65f, pet.feed(start).food, .00001f)
        assertEquals(.60f, pet.play(start).happiness, .00001f)
        assertEquals(.22f, pet.play(start).energy, .00001f)
        assertEquals(.70f, pet.rest(start).energy, .00001f)
        assertEquals(.75f, pet.clean(start).hygiene, .00001f)
        assertEquals(.58f, pet.talk(start).affection, .00001f)
        var repeated = pet
        repeat(100) { repeated = repeated.feed(start).play(start).rest(start).clean(start).talk(start) }
        assertTrue(listOf(repeated.food, repeated.energy, repeated.happiness, repeated.affection, repeated.hygiene).all { it in .1f..1f })
        assertEquals(PetCareState(updatedAtMs = start), PetCareState().feed(start).play(start).rest(start).clean(start).talk(start))
    }

    @Test fun persistencePreservesRatesAndRejectsBrokenPayloads() {
        val pet = PetCareState(true, .72f, .56f, .88f, .64f, .91f, start)
        assertEquals(pet, PetCareState.decode(pet.encode()))
        assertEquals(pet.advance(start + hour), PetCareState.decode(pet.encode()).advance(start + hour))
        listOf(null, "", "{}", "3|true|1|1|1|1|1|0", "2|yes|1|1|1|1|1|0", "2|true|NaN|1|1|1|1|0", "2|true|1|Infinity|1|1|1|0", "2|true|1|1|1|1|1|-1").forEach {
            assertEquals(PetCareState(), PetCareState.decode(it))
        }
        val bounded = PetCareState.decode("2|true|999|-99|1|1|1|1000")
        assertEquals(1f, bounded.food, 0f)
        assertEquals(.1f, bounded.energy, 0f)
    }

    @Test fun v1PayloadMigratesAffectionAndHygiene() {
        val legacy = PetCareState.decode("1|true|0.72|0.56|0.88|1000")
        assertTrue(legacy.careEnabled)
        assertEquals(.72f, legacy.food, .00001f)
        assertEquals(.56f, legacy.energy, .00001f)
        assertEquals(.88f, legacy.happiness, .00001f)
        assertEquals(.88f, legacy.affection, .00001f)
        assertEquals(1f, legacy.hygiene, 0f)
    }

    @Test fun formAwareReactionMentionsCareNeed() {
        val hungry = PetCareState(true, .2f, .9f, .9f, .9f, .9f, start)
        assertTrue(hungry.reaction("cat").contains("snack", ignoreCase = true) || hungry.reaction("cat").contains("Food", ignoreCase = true) || hungry.reaction("cat").contains("purr", ignoreCase = true))
        val off = PetCareState(false, updatedAtMs = start)
        assertTrue(off.reaction(null).contains("Self-sufficient"))
    }
}
