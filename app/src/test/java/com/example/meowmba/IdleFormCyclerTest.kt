package com.example.meowmba

import com.example.meowmba.ui.companion.AvatarAnimal
import com.example.meowmba.ui.companion.IdleFormCycler
import com.example.meowmba.ui.companion.IdleFormTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdleFormCyclerTest {
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
        assertEquals(AvatarAnimal.CAT, seen[0].animal)
        assertEquals(AvatarAnimal.DUCK, seen[1].animal)
        assertEquals(AvatarAnimal.DOG, seen[2].animal)
        assertEquals(AvatarAnimal.OWL, seen[3].animal)
        assertEquals(AvatarAnimal.CAT, seen[4].animal)
    }
}
