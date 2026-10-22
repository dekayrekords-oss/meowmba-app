package com.example.meowmba.flow

import com.example.meowmba.ui.desk.DeskCatActivity
import com.example.meowmba.ui.desk.DeskCatBehavior
import com.example.meowmba.ui.desk.DeskCatPose
import com.example.meowmba.ui.desk.DeskCatReplies
import com.example.meowmba.ui.desk.DeskObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DeskCatBehaviorTest {
    @Test fun restsAreIrregularButBounded() {
        val random = Random(172)
        val rests = List(32) { DeskCatBehavior.restMs(random, 20_000) }

        assertTrue(rests.all { it in 10_000L..30_000L })
        assertTrue("Rest cadence must not become a fixed loop", rests.toSet().size > 1)
        assertTrue(DeskCatBehavior.restMs(Random(1), 1) in 2_000L..6_000L)
        assertTrue(DeskCatBehavior.restMs(Random(2), 90_000) in 30_000L..90_000L)
    }

    @Test fun objectAndIdleTravelStayOnSafeDeskOrFloorSpots() {
        val objectActs = DeskObject.entries.map(DeskCatBehavior::atObject)
        assertEquals(DeskCatBehavior.computer, DeskCatBehavior.atObject(DeskObject.BUILD).spot)
        assertEquals(DeskCatPose.TYPE, DeskCatBehavior.atObject(DeskObject.BUILD).pose)
        assertEquals(DeskCatBehavior.letters, DeskCatBehavior.atObject(DeskObject.INBOX).spot)
        assertEquals(DeskCatBehavior.board, DeskCatBehavior.atObject(DeskObject.PROJECT).spot)
        // Office objects keep the cat in the office; the other rooms' objects keep it in theirs.
        DeskObject.entries.forEach { o ->
            val act = DeskCatBehavior.atObject(o)
            assertEquals("$o", o.room, com.example.meowmba.ui.desk.DeskRoom.of(act.spot.x))
            assertTrue("$o", act.spot.y in 0f..748f)
        }
        assertTrue(objectActs.any { it.spot.y == 516f }) // floor / desktop baseline
        assertTrue(objectActs.any { it.spot.y == 300f }) // corkboard perch
        assertTrue(objectActs.any { it.spot.y == 656f }) // paws meet the floor beside the drawers

        val random = Random(173)
        val acts = List(200) { DeskCatBehavior.idle(random, DeskCatPose.REST, hasMail = true, hasGoal = true) }
        assertTrue(acts.map { it.pose }.toSet().size >= 3)
        assertTrue(acts.all { it.spot.x >= 0f && it.spot.x + 110f <= 412f && it.spot.y + 90f <= 749f })
        assertTrue(acts.all { it.durationMs in 2_400L..5_200L })
    }

    @Test fun repliesDoNotReplayHistoryAndTrackLiveFinalAndConversationBoundaries() {
        val replies = DeskCatReplies(conversation = 7, sequence = 4, live = null)
        // Desk entry sees an already-stored answer but must not replay it.
        assertNull(replies.observe(7, 4, 7, "Old answer", 7, null, DeskCatActivity.IDLE))

        val live = replies.observe(7, 4, 7, "Old answer", 7, "Draft answer", DeskCatActivity.THINKING)
        assertEquals("7:5", live?.key)
        assertEquals("Draft answer", live?.text)
        val final = replies.observe(7, 5, 7, "Final answer", 7, "Draft answer", DeskCatActivity.IDLE)
        assertEquals("7:5", final?.key)
        assertEquals("Final answer", final?.text)
        // Room persistence clears the live draft; the final bubble remains.
        assertEquals(final, replies.observe(7, 5, 7, "Final answer", 7, null, DeskCatActivity.IDLE))

        val repeated = replies.observe(7, 6, 7, "Final answer", 7, null, DeskCatActivity.IDLE)
        assertEquals("7:6", repeated?.key)
        assertNotEquals(final?.key, repeated?.key)

        assertNull(replies.observe(8, 9, 7, "Wrong conversation", 7, "also wrong", DeskCatActivity.IDLE))
        assertNull(replies.observe(8, 9, 8, "Current but pre-existing", 8, null, DeskCatActivity.IDLE))
    }

    @Test fun followUpListeningKeepsTheAnswerUntilTheNextQuestionStarts() {
        val replies = DeskCatReplies(conversation = 2, sequence = 0, live = null)
        assertEquals("answer", replies.observe(2, 1, 2, "answer", 2, null, DeskCatActivity.IDLE)?.text)
        assertEquals("answer", replies.observe(2, 1, 2, "answer", 2, null, DeskCatActivity.LISTENING)?.text)
        assertEquals("next", replies.observe(2, 2, 2, "next", 2, null, DeskCatActivity.IDLE)?.text)
        assertNull(replies.observe(2, 2, 2, "next", 2, null, DeskCatActivity.THINKING))
    }
}
