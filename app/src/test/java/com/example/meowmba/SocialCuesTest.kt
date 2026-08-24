package com.example.meowmba

import com.example.meowmba.core.vision.FollowSource
import com.example.meowmba.core.vision.HandSample
import com.example.meowmba.core.vision.SocialCue
import com.example.meowmba.core.vision.SocialCues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialCuesTest {

    @Test
    fun `smile and wink come from face probabilities`() {
        assertEquals(SocialCue.SMILE, SocialCues.faceCue(0.9f, 0.8f, 0.8f))
        assertEquals(SocialCue.WINK, SocialCues.faceCue(0.1f, 0.1f, 0.9f))
        assertEquals(SocialCue.BLINK, SocialCues.faceCue(0.1f, 0.1f, 0.1f))
        assertEquals(SocialCue.EYES_WIDE, SocialCues.faceCue(0.1f, 0.97f, 0.97f))
        assertEquals(SocialCue.NONE, SocialCues.faceCue(-1f, -1f, -1f))
    }

    @Test
    fun `a waving raised hand is WAVE and follows the hand`() {
        val hand = HandSample(
            wristX = 0.4f, wristY = 0.25f,
            elbowX = 0.4f, elbowY = 0.55f,
        )
        assertTrue(SocialCues.handRaised(hand))
        val waveXs = listOf(0.25f, 0.55f, 0.22f, 0.58f, 0.28f)
        assertEquals(SocialCue.WAVE, SocialCues.handSign(hand, waveXs))
        assertEquals(FollowSource.HAND, SocialCues.pickFollow(handRaised = true, handMoving = true))
        assertEquals(FollowSource.HAND, SocialCues.pickFollow(handRaised = false, handMoving = true))
        assertEquals(FollowSource.HAND, SocialCues.pickFollow(false, false, motionHand = true))
        assertEquals(FollowSource.FACE, SocialCues.pickFollow(handRaised = false, handMoving = false))
    }

    @Test
    fun `thumbs up point fist and palm from fingertip geometry`() {
        val up = HandSample(
            wristX = 0.5f, wristY = 0.40f,
            elbowX = 0.5f, elbowY = 0.70f,
            thumbX = 0.50f, thumbY = 0.20f,
            indexX = 0.52f, indexY = 0.38f,
            pinkyX = 0.48f, pinkyY = 0.39f,
        )
        assertEquals(SocialCue.THUMBS_UP, SocialCues.handSign(up, emptyList()))

        val point = HandSample(
            wristX = 0.5f, wristY = 0.40f,
            elbowX = 0.5f, elbowY = 0.70f,
            thumbX = 0.51f, thumbY = 0.38f,
            indexX = 0.72f, indexY = 0.25f,
            pinkyX = 0.49f, pinkyY = 0.39f,
        )
        assertEquals(SocialCue.POINT, SocialCues.handSign(point, emptyList()))

        val fist = HandSample(
            wristX = 0.5f, wristY = 0.35f,
            elbowX = 0.5f, elbowY = 0.65f,
            thumbX = 0.51f, thumbY = 0.36f,
            indexX = 0.50f, indexY = 0.34f,
            pinkyX = 0.49f, pinkyY = 0.36f,
        )
        assertEquals(SocialCue.FIST, SocialCues.handSign(fist, emptyList()))
    }

    @Test
    fun `reactions pick a real clip or a face emotion`() {
        val (emotion, clip) = SocialCues.reaction(SocialCue.SMILE)
        assertEquals("HAPPY", emotion)
        assertEquals("happy bop", clip)
        assertEquals("ear flatten", SocialCues.reaction(SocialCue.FIST).second)
        assertEquals(null, SocialCues.reaction(SocialCue.NONE).second)
    }

    @Test
    fun `hand cue wins over a smile and a raised hand is followed`() {
        assertEquals(
            SocialCue.WAVE,
            SocialCues.pickCue(SocialCue.SMILE, SocialCue.WAVE),
        )
        assertEquals(
            SocialCue.SMILE,
            SocialCues.pickCue(SocialCue.SMILE, SocialCue.NONE),
        )
        assertEquals(
            FollowSource.HAND,
            SocialCues.pickFollow(handRaised = true, handMoving = false),
        )
        val hand = HandSample(wristX = 0.8f, wristY = 0.2f, elbowX = 0.8f, elbowY = 0.5f)
        val (x, y) = SocialCues.followOffset(hand)
        assertTrue(x > 0.4f)
        assertTrue(y < 0f)
    }

    @Test
    fun `blinks and held smiles do not retrigger`() {
        assertTrue(SocialCues.shouldReact(SocialCue.SMILE, SocialCue.NONE, 1_000L, 0L))
        assertTrue(!SocialCues.shouldReact(SocialCue.BLINK, SocialCue.NONE, 1_000L, 0L))
        assertTrue(!SocialCues.shouldReact(SocialCue.EYES_WIDE, SocialCue.NONE, 1_000L, 0L))
        assertTrue(!SocialCues.shouldReact(SocialCue.HAND_UP, SocialCue.NONE, 1_000L, 0L))
        assertTrue(!SocialCues.shouldReact(SocialCue.SMILE, SocialCue.SMILE, 3_000L, 1_000L))
        assertTrue(SocialCues.shouldReact(SocialCue.SMILE, SocialCue.SMILE, 6_000L, 1_000L))
        assertTrue(SocialCues.shouldReact(SocialCue.WAVE, SocialCue.SMILE, 3_000L, 1_000L))
    }

    @Test
    fun `reaction clips exist in the avatar catalogs`() {
        SocialCue.entries.mapNotNull { SocialCues.reaction(it).second }.forEach { name ->
            val known = com.example.meowmba.ui.companion.CatExpressions.resolve(name) != null ||
                com.example.meowmba.ui.companion.CustomAnimations.builtIn(name) != null
            assertTrue("missing clip: $name", known)
        }
    }
}
