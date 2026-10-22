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
            random = kotlin.random.Random(7), onTarget = targets::add)
        cycler.start()
        runCurrent()
        val wait = kotlin.random.Random(7).nextLong(60_000L, 240_001L)
        advanceTimeBy(10_000L)
        cycler.start()
        advanceTimeBy(wait - 10_000L + 500L)
        runCurrent()
        assertEquals(1, targets.size)
        cycler.stop()
        assertFalse(cycler.isRunning)
    }
    @Test fun visitReturnsToBaselineAndStopsWhenBusy() = kotlinx.coroutines.test.runTest {
        var idle = true
        var target: IdleFormTarget? = null
        val baseline = IdleFormTarget(animal = AvatarAnimal.CAT)
        val cycler = IdleFormCycler(backgroundScope, { idle }, baseline = { baseline },
            random = kotlin.random.Random(7), onTarget = { target = it }, onBaseline = { target = null })
        assertFalse(baseline in cycler.pool())
        cycler.start()
        runCurrent()
        val wait = kotlin.random.Random(7).nextLong(60_000L, 240_001L)
        advanceTimeBy(wait + 500L); runCurrent()
        assertTrue(target != null)
        advanceTimeBy(6_500L); runCurrent()
        assertEquals(null, target)
        idle = false
        advanceTimeBy(300_000L); runCurrent()
        assertEquals(null, target)
        cycler.stop()
    }

    @Test fun interruptedVisitImmediatelyRestoresBaseline() = kotlinx.coroutines.test.runTest {
        var idle = true
        var target: IdleFormTarget? = null
        val cycler = IdleFormCycler(backgroundScope, { idle }, random = kotlin.random.Random(7),
            onTarget = { target = it }, onBaseline = { target = null })
        cycler.start(); runCurrent()
        advanceTimeBy(kotlin.random.Random(7).nextLong(60_000L, 240_001L) + 500L); runCurrent()
        assertTrue(target != null)
        idle = false
        advanceTimeBy(500L); runCurrent()
        assertEquals(null, target)
        cycler.stop()
    }

    @Test fun visitsHaveIrregularGaps() {
        val cycler = IdleFormCycler(TestScope(), { true }, random = kotlin.random.Random(9), onTarget = {})
        val gaps = List(30) { cycler.nextWaitMs() }
        assertTrue(gaps.all { it in 60_000L..240_000L })
        assertTrue(gaps.toSet().size > 20)
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
