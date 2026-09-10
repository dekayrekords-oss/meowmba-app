package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LumenActingTest {
    @Test fun sixteenExpressionsHaveDistinctEyeAndPostureSignatures() {
        val poses = CompanionEmotion.entries.map(LumenActingPoses::forEmotion)
        assertEquals(16, poses.size)
        assertEquals(poses.size, poses.toSet().size)
        for (pose in poses) {
            assertTrue(pose.openLeft in .25f..1.3f && pose.openRight in .25f..1.3f)
            assertTrue(pose.pupil in .2f.. .85f)
            assertTrue(abs(pose.roll) <= 8f)
        }
        val concern = LumenActingPoses.forEmotion(CompanionEmotion.CONCERNED)
        val resolve = LumenActingPoses.forEmotion(CompanionEmotion.DETERMINED)
        assertTrue(concern.innerLift > 0f && resolve.innerLift < 0f)
        assertTrue(LumenActingPoses.forEmotion(CompanionEmotion.HAPPY).lowerLeft > .25f)
    }

    @Test fun listeningRespondsLocallyAndRetargetsWithoutPositionOrLidSnaps() {
        val rig = LumenRig()
        var frame = LumenFrame()
        repeat(120) { frame = rig.step(it / 120f, LumenInput(emotion = CompanionEmotion.SLEEPY)) }
        val before = frame
        repeat(18) { index ->
            val next = rig.step(1f + index / 120f, LumenInput(interaction = InteractionState.LISTENING,
                emotion = CompanionEmotion.SLEEPY))
            assertTrue(abs(next.leftOpen - frame.leftOpen) < .045f)
            assertTrue(abs(next.roll - frame.roll) < .4f)
            frame = next
        }
        assertTrue("Listening should visibly acquire within 150ms", frame.leftOpen - before.leftOpen > .20f)
        assertTrue("Listening is attentive despite the old sleepy mood", frame.leftOpen > .6f)
    }

    @Test fun aTenMinuteConversationVariesBeatsWithoutRestartingOnIdenticalUpdates() {
        val director = LumenActingDirector(75)
        val input = LumenInput(interaction = InteractionState.LISTENING)
        repeat(60 * 10 * 60) { director.step(1f / 60f, input) }
        assertEquals(1, director.beatCount)
        val signatures = mutableSetOf<List<Float>>()
        repeat(12) {
            director.step(1f / 60f, input.copy(interaction = InteractionState.SPEAKING))
            var sample = LumenActingPose()
            repeat(30) { sample = director.step(1f / 60f, input) }
            signatures += listOf(sample.roll, sample.lean, sample.earSplit)
        }
        assertTrue(signatures.size >= 6)
        assertEquals("yield", director.lastBeat)
    }

    @Test fun actorHasNoRandomFrameNoiseAndDoesNotFightAuthoredScenes() {
        val a = LumenActingDirector(41); val b = LumenActingDirector(41)
        repeat(600) {
            val input = LumenInput(interaction = if (it < 240) InteractionState.THINKING else InteractionState.SPEAKING,
                emotion = CompanionEmotion.APOLOGETIC)
            assertEquals(a.step(1f / 60f, input), b.step(1f / 60f, input))
        }
        val authored = a.step(1f / 60f, LumenInput(performing = true, emotion = CompanionEmotion.HAPPY))
        assertEquals(LumenActingPose(), authored)
    }

    @Test fun eyesRemainStableAcrossFrameRatesAndFrozenFramesDoNotAdvance() {
        fun run(hz: Int): LumenFrame {
            val rig = LumenRig(99)
            var frame = LumenFrame()
            repeat(hz * 2 + 1) { i -> frame = rig.step(i.toFloat() / hz,
                LumenInput(emotion = CompanionEmotion.CONCERNED, cameraEnabled = true, gazeX = .6f, gazeY = -.2f)) }
            assertEquals(frame, rig.step(2f, LumenInput(emotion = CompanionEmotion.HAPPY)))
            assertEquals(frame, rig.step(Float.NaN, LumenInput()))
            return frame
        }
        val a = run(30); val b = run(60); val c = run(120)
        for (f in listOf(a,b)) {
            assertEquals(c.gazeX, f.gazeX, .012f)
            assertEquals(c.innerLift, f.innerLift, .012f)
            assertEquals(c.leftOpen, f.leftOpen, .02f)
            assertEquals(c.roll, f.roll, .12f)
        }
    }

    @Test fun physicalAttentionIsNotPredictedFromEmotionAndInputCannotEscapeBounds() {
        val rig = LumenRig()
        var f = LumenFrame()
        repeat(121) { f = rig.step(it / 60f, LumenInput(gazeX = Float.NaN, gazeY = Float.POSITIVE_INFINITY,
            cameraEnabled = true, emotion = CompanionEmotion.DETERMINED, lowerLid = Float.NaN,
            earAsymmetry = Float.POSITIVE_INFINITY, performanceJaw = Float.NaN, performanceLean = Float.NaN)) }
        assertTrue(f.gazeX.isFinite() && f.gazeY.isFinite())
        assertTrue(f.driftX in -1f..1f && f.driftY in -1f..1f)
        assertTrue(f.lowerLidLeft in 0f.. .65f && f.earAsymmetry in -12f..12f)
        assertEquals(0f, f.mouthOpen, 0f)
    }
}
