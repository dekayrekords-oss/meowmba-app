package com.example.meowmba

import com.example.meowmba.ui.companion.AvatarAnimal
import com.example.meowmba.ui.companion.IdleFormCycler
import com.example.meowmba.ui.companion.IdleFormTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdleFormCyclerTest {
    @Test fun repeatedStartDoesNotPostponeTheNextForm() = kotlinx.coroutines.test.runTest {
        val targets = mutableListOf<IdleFormTarget>()
        val cycler = IdleFormCycler(backgroundScope, { true },
            intervalMs = { 20_000L }, onTarget = targets::add)
        cycler.start()
        runCurrent()
        advanceTimeBy(10_000L)
        cycler.start()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(1, targets.size)
        cycler.stop()
        assertFalse(cycler.isRunning)
    }
    @Test
    fun poolIncludesEveryAuthoredAnimalExceptEyesOnly() {
        val cycler = IdleFormCycler(
            scope = TestScope(),
            canMorph = { true },
            onTarget = {},
        )
        val animals = cycler.pool().mapNotNull { it.animal }
        assertFalse(AvatarAnimal.NONE in animals)
        for (a in AvatarAnimal.entries.filter { it != AvatarAnimal.NONE }) {
            assertTrue("$a missing from idle pool", a in animals)
        }
        assertTrue(animals.size >= 15)
    }

    @Test
    fun poolAppendsGeneratedIdsWhenEnabled() {
        val cycler = IdleFormCycler(
            scope = TestScope(),
            canMorph = { true },
            generatedFormIds = { listOf("gen-a", "gen-b") },
            includeGenerated = { true },
            onTarget = {},
        )
        val ids = cycler.pool().mapNotNull { it.generatedId }
        assertEquals(listOf("gen-a", "gen-b"), ids)
    }

    @Test
    fun nextTargetRotatesThroughEntirePool() {
        val seen = mutableListOf<IdleFormTarget>()
        val cycler = IdleFormCycler(
            scope = TestScope(),
            canMorph = { true },
            authoredForms = { listOf(AvatarAnimal.CAT, AvatarAnimal.DUCK, AvatarAnimal.DOG, AvatarAnimal.OWL) },
            generatedFormIds = { emptyList() },
            onTarget = {},
        )
        repeat(8) { seen += cycler.nextTarget()!! }
        val expected = setOf(AvatarAnimal.CAT, AvatarAnimal.DUCK, AvatarAnimal.DOG, AvatarAnimal.OWL)
        assertEquals(expected, seen.take(4).map { it.animal }.toSet())
        assertEquals(expected, seen.drop(4).map { it.animal }.toSet())
        assertTrue(seen.zipWithNext().all { (a, b) -> a != b })
    }
}
