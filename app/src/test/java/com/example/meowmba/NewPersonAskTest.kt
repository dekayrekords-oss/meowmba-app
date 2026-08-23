package com.example.meowmba

import com.example.meowmba.core.vision.NewPersonAsk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPersonAskTest {

    @Test
    fun `a lone face is the owner, not a guest`() {
        assertNull(
            NewPersonAsk.pickFaceToAsk(
                faceIds = listOf(1),
                namedIds = emptySet(),
                askedIds = emptySet(),
                firstSeenAt = mapOf(1 to 0L),
                nowMs = 5_000,
                lastAskAtMs = 0,
                busy = false,
                alreadyAwaiting = false,
            ),
        )
    }

    @Test
    fun `asks the newer unnamed face after they settle`() {
        val id = NewPersonAsk.pickFaceToAsk(
            faceIds = listOf(1, 2),
            namedIds = setOf(1),
            askedIds = emptySet(),
            firstSeenAt = mapOf(1 to 0L, 2 to 1_000L),
            nowMs = 1_000L + NewPersonAsk.SETTLE_MS,
            lastAskAtMs = 0,
            busy = false,
            alreadyAwaiting = false,
        )
        assertEquals(2, id)
    }

    @Test
    fun `does not ask a face that just appeared`() {
        assertNull(
            NewPersonAsk.pickFaceToAsk(
                faceIds = listOf(1, 2),
                namedIds = setOf(1),
                askedIds = emptySet(),
                firstSeenAt = mapOf(1 to 0L, 2 to 4_000L),
                nowMs = 4_200L,
                lastAskAtMs = 0,
                busy = false,
                alreadyAwaiting = false,
            ),
        )
    }

    @Test
    fun `does not nag after asking or while speaking`() {
        val seen = mapOf(1 to 0L, 2 to 0L)
        assertNull(
            NewPersonAsk.pickFaceToAsk(
                listOf(1, 2), namedIds = setOf(1), askedIds = setOf(2),
                seen, nowMs = 5_000, lastAskAtMs = 0, busy = false, alreadyAwaiting = false,
            ),
        )
        assertNull(
            NewPersonAsk.pickFaceToAsk(
                listOf(1, 2), emptySet(), emptySet(),
                seen, nowMs = 5_000, lastAskAtMs = 0, busy = true, alreadyAwaiting = false,
            ),
        )
        assertNull(
            NewPersonAsk.pickFaceToAsk(
                listOf(1, 2), emptySet(), emptySet(),
                seen, nowMs = 5_000, lastAskAtMs = 0, busy = false, alreadyAwaiting = true,
            ),
        )
    }

    @Test
    fun `no later and skip decline the intro`() {
        assertTrue(NewPersonAsk.declined("no thanks"))
        assertTrue(NewPersonAsk.declined("Later"))
        assertTrue(NewPersonAsk.declined("skip"))
    }

    @Test
    fun `a tiny second box is a ghost, not a guest`() {
        assertNull(
            NewPersonAsk.pickFaceToAsk(
                faceIds = listOf(1, 2),
                namedIds = emptySet(),
                askedIds = emptySet(),
                firstSeenAt = mapOf(1 to 0L, 2 to 0L),
                nowMs = 5_000,
                lastAskAtMs = 0,
                busy = false,
                alreadyAwaiting = false,
                faceAreas = mapOf(1 to 10_000f, 2 to 400f),
            ),
        )
    }

    @Test
    fun `tracking id reset is not a new person when we already know someone`() {
        assertNull(
            NewPersonAsk.pickFaceToAsk(
                faceIds = listOf(40, 41),
                namedIds = emptySet(),
                askedIds = emptySet(),
                firstSeenAt = mapOf(40 to 0L, 41 to 0L),
                nowMs = 5_000,
                lastAskAtMs = 0,
                busy = false,
                alreadyAwaiting = false,
                hasKnownPeople = true,
            ),
        )
    }

    @Test
    fun `does not re-ask after an unanswered intro even when cooldown has passed`() {
        assertNull(
            NewPersonAsk.pickFaceToAsk(
                faceIds = listOf(1, 2),
                namedIds = emptySet(),
                askedIds = emptySet(),
                firstSeenAt = mapOf(1 to 0L, 2 to 0L),
                nowMs = 1_000L + NewPersonAsk.ASK_COOLDOWN_MS + 5_000L,
                lastAskAtMs = 1_000L,
                busy = false,
                alreadyAwaiting = false,
            ),
        )
    }

    @Test
    fun `still asks a settled guest once the owner is bound to a face`() {
        val id = NewPersonAsk.pickFaceToAsk(
            faceIds = listOf(1, 2),
            namedIds = setOf(1),
            askedIds = emptySet(),
            firstSeenAt = mapOf(1 to 0L, 2 to 1_000L),
            nowMs = 1_000L + NewPersonAsk.SETTLE_MS,
            lastAskAtMs = 0,
            busy = false,
            alreadyAwaiting = false,
            hasKnownPeople = true,
            faceAreas = mapOf(1 to 8_000f, 2 to 6_000f),
        )
        assertEquals(2, id)
    }

    @Test
    fun `rebinds the owner to the largest new face after tracking ids churn`() {
        val rebound = NewPersonAsk.rebindOwner(
            faceIds = listOf(40, 41),
            namedIds = emptySet(),
            ownerName = "bob",
            faceAreas = mapOf(40 to 9_000f, 41 to 1_200f),
        )
        assertEquals(40 to "bob", rebound)
    }

    @Test
    fun `does not steal a face that already has a name`() {
        assertNull(
            NewPersonAsk.rebindOwner(
                faceIds = listOf(1, 2),
                namedIds = setOf(1),
                ownerName = "bob",
                faceAreas = mapOf(1 to 9_000f, 2 to 8_000f),
            ),
        )
    }
}
