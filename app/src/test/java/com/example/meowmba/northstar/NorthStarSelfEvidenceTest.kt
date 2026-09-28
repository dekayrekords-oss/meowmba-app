package com.example.meowmba.northstar

import com.example.meowmba.core.northstar.NorthStar
import com.example.meowmba.core.northstar.NorthStarPolicy
import com.example.meowmba.core.northstar.StarEvidence
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Found recording the Android demo (2026-09-27): the sentence that created a goal was listed as its first connection. */
class NorthStarSelfEvidenceTest {
    private val star = NorthStar("s1", "play in Europe with my band", "", "Someday",
        "[]", createdAt = 1_000L, updatedAt = 1_000L, pinnedAt = 0)

    @Test fun theGoalStatementIsNotEvidenceForItself() {
        assertNull(NorthStarPolicy.match(star, StarEvidence("speech:1", "My dream is to play in Europe with my band.", 1_000L)))
    }

    @Test fun realActivityStillConnects() {
        assertNotNull(NorthStarPolicy.match(star, StarEvidence("cal:1", "Band rehearsal for the Europe tour", 2_000L)))
    }
}
