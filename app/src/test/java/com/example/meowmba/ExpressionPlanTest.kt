package com.example.meowmba

import com.example.meowmba.core.audio.VoiceExpression
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExpressionPlanTest {
    @Test fun explicitCorrectionsRequestAttentiveRepairWithoutTreatingOrdinaryNarrationAsCorrection() {
        for (text in listOf("No, I said turn left", "No! I meant tomorrow", "That's not what I said",
            "That’s not what I meant", "You misheard me", "You misunderstood", "Please listen",
            "Stop interrupting", "Let me finish", "Let me speak")) {
            val planner = ConversationExpressionPlanner { 1_000L }
            val plan = planner.user(text, ExpressionIntensity.NATURAL)
            assertEquals(text, CompanionEmotion.ATTENTIVE, plan.primary)
            assertEquals(text, CompanionEmotion.APOLOGETIC, plan.secondary)
            assertTrue(text, plan.repairing)
            assertFalse(text, planner.allowsPlay())
        }
        for (text in listOf("I said hello to my friend", "I meant to ask about dinner",
            "You understood that perfectly", "Send a thank you email")) {
            val planner = ConversationExpressionPlanner { 1_000L }
            val plan = planner.user(text, ExpressionIntensity.NATURAL)
            assertFalse(text, plan.repairing)
            assertEquals(text, CompanionEmotion.CURIOUS, plan.primary)
            assertTrue(text, planner.allowsPlay())
        }
    }

    @Test fun seriousContextUsesInjectedTimeAndAnOrdinaryFollowUpDoesNotReleaseItEarly() {
        var now = 10_000L
        val planner = ConversationExpressionPlanner { now }
        val plan = planner.user("I'm worried about tomorrow", ExpressionIntensity.SUBTLE)
        assertEquals(CompanionEmotion.CONCERNED, plan.primary)
        assertFalse(plan.repairing)
        assertEquals(ExpressionIntensity.SUBTLE.strength, plan.intensity, 0f)
        assertFalse(planner.allowsPlay())
        now += 40_000L
        val followUp = planner.user("What should I do next?", ExpressionIntensity.NATURAL)
        assertEquals(CompanionEmotion.ATTENTIVE, followUp.primary)
        assertFalse(planner.allowsPlay())
        now = 99_999L
        assertFalse(planner.allowsPlay())
        now++
        assertTrue(planner.allowsPlay())
        assertEquals(CompanionEmotion.CURIOUS,
            planner.user("What should I do next?", ExpressionIntensity.NATURAL).primary)
    }

    @Test fun repairDeliveryExpiresBeforeTheQuietContextAndInterruptionCannotShortenEitherHold() {
        var now = 1_000L
        val planner = ConversationExpressionPlanner { now }
        val correction = planner.user("No, I said tomorrow", ExpressionIntensity.NATURAL)
        now = 30_999L
        val during = planner.reply("Understood, tomorrow.", correction.primary, ExpressionIntensity.NATURAL)
        assertTrue(during.repairing)
        assertEquals(CompanionEmotion.UNDERSTANDING, during.primary)
        now = 31_000L
        assertFalse(planner.reply("Understood, tomorrow.", during.primary, ExpressionIntensity.NATURAL).repairing)
        val interruption = planner.interruption(ExpressionIntensity.SUBTLE)
        assertEquals(CompanionEmotion.ATTENTIVE, interruption.primary)
        assertFalse(planner.allowsPlay())
        now = 90_999L
        assertFalse(planner.allowsPlay())
        now++
        assertTrue(planner.allowsPlay())
    }

    @Test fun failureExtendsAQuietHoldWithoutTurningAnOrdinaryResponseIntoForcedCelebration() {
        var now = 1_000L
        val planner = ConversationExpressionPlanner { now }
        val neutral = planner.reply("The answer is forty-two.", CompanionEmotion.CURIOUS, ExpressionIntensity.NATURAL)
        assertEquals(CompanionEmotion.UNDERSTANDING, neutral.primary)
        assertEquals(VoiceExpression(), neutral.voice())
        assertTrue(planner.allowsPlay())

        now = 2_000L
        planner.user("This is serious", ExpressionIntensity.NATURAL)
        now = 62_000L
        val failed = planner.reply("I'm sorry, I couldn't open that file.", CompanionEmotion.PROUD, ExpressionIntensity.NATURAL)
        assertEquals(CompanionEmotion.APOLOGETIC, failed.primary)
        now = 106_999L
        assertFalse(planner.allowsPlay())
        now++
        assertTrue(planner.allowsPlay())
    }

    @Test fun affectionIsExplicitAndUsesTheSelectedStrengthForGentleDelivery() {
        val planner = ConversationExpressionPlanner { 20_000L }
        val affectionate = planner.user("I love you!", ExpressionIntensity.EXPRESSIVE)
        assertEquals(CompanionEmotion.AFFECTIONATE, affectionate.primary)
        assertFalse(affectionate.repairing)
        assertTrue(planner.allowsPlay())
        assertEquals(1f, affectionate.intensity, 0f)
        assertTrue(affectionate.voice().rate < 1f)
        assertEquals(CompanionEmotion.CURIOUS,
            planner.user("Write I love you in a card", ExpressionIntensity.NATURAL).primary)
    }

    @Test fun allVoiceProposalsAndInvalidIntensityStayFiniteAndWithinSmallDeliveryAdjustments() {
        for (emotion in CompanionEmotion.entries) {
            for (intensity in listOf(-100f, 0f, .55f, .82f, 1f, 100f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                val voice = ExpressionPlan(emotion, intensity = intensity).voice()
                assertTrue("$emotion $intensity pitch", voice.pitch.isFinite() && voice.pitch in .97f..1.07f)
                assertTrue("$emotion $intensity rate", voice.rate.isFinite() && voice.rate in .95f..1.05f)
                if (intensity.isFinite() && intensity <= 0f) assertEquals(VoiceExpression(), voice)
            }
        }
        assertEquals(VoiceExpression(), VoiceExpression(Float.NaN, Float.POSITIVE_INFINITY).bounded())
        val extremes = VoiceExpression(-100f, 100f).bounded()
        assertEquals(.9f, extremes.pitch, 0f)
        assertEquals(1.1f, extremes.rate, 0f)
        assertEquals(ExpressionIntensity.NATURAL, ExpressionIntensity.fromKey("future-value"))
        assertEquals(Playfulness.BALANCED, Playfulness.fromKey(null))
    }

    @Test fun blendEndpointsAndInvalidWeightsCannotOvershootTheAuthoredEyeAndBodyChannels() {
        val a = LumenActingPoses.forEmotion(CompanionEmotion.CONCERNED)
        val b = LumenActingPoses.forEmotion(CompanionEmotion.PLAYFUL)
        for (weight in listOf(-2f, 0f, Float.NaN, Float.POSITIVE_INFINITY)) assertEquals(a, a.blend(b, weight))
        assertPoseNear(b, a.blend(b, 1f))
        assertPoseNear(b, a.blend(b, 2f))
        val mid = channels(a.blend(b, .35f))
        channels(a).zip(channels(b)).forEachIndexed { i, (left, right) ->
            assertTrue("channel $i", mid[i] >= minOf(left, right) - .00001f && mid[i] <= maxOf(left, right) + .00001f)
        }
    }

    @Test fun strongerPlansConvergeToStrongerExpressionsAndRestEventuallyReturnsToNeutral() {
        val emotion = CompanionEmotion.CONCERNED
        val authored = LumenActingPoses.forEmotion(emotion)
        val neutral = LumenActingPoses.forEmotion(CompanionEmotion.IDLE)
        fun input(strength: Float, interaction: InteractionState = InteractionState.SPEAKING) = LumenInput(
            emotion = emotion, interaction = interaction,
            expressionPlan = ExpressionPlan(emotion, secondaryWeight = 0f, intensity = strength))
        val none = sample(input(0f))
        val half = sample(input(.5f))
        val full = sample(input(1f))
        assertPoseNear(neutral, none)
        assertPoseNear(authored, full)
        assertTrue(half.concern > none.concern && half.concern < full.concern)
        assertTrue(half.innerLift > none.innerLift && half.innerLift < full.innerLift)
        assertPoseNear(neutral.blend(authored, .5f), half)
        assertPoseNear(neutral, sample(input(1f, InteractionState.IDLE), seconds = 18))
    }

    @Test fun listeningAndExplicitPerformancesKeepPriorityOverBlendIntensityAndPreviousMood() {
        val plan = ExpressionPlan(CompanionEmotion.SLEEPY, CompanionEmotion.APOLOGETIC, .9f, 0f)
        val listening = LumenInput(emotion = CompanionEmotion.SLEEPY, interaction = InteractionState.LISTENING,
            expressionPlan = plan)
        assertPoseNear(LumenActingPoses.forEmotion(CompanionEmotion.ATTENTIVE), sample(listening))
        val thinking = listening.copy(interaction = InteractionState.THINKING)
        assertPoseNear(sample(thinking.copy(expressionPlan = null)), sample(thinking))
        assertEquals(LumenActingPose(), sample(listening.copy(performing = true)))
        val affectionate = listening.copy(interaction = InteractionState.IDLE, affection = true)
        assertPoseNear(LumenActingPoses.forEmotion(CompanionEmotion.AFFECTIONATE), sample(affectionate))
    }

    private fun sample(input: LumenInput, seconds: Int = 3): LumenActingPose {
        val director = LumenActingDirector(914)
        var result = LumenActingPose()
        repeat(seconds * 50) { result = director.step(.02f, input) }
        return result
    }

    private fun assertPoseNear(expected: LumenActingPose, actual: LumenActingPose) {
        channels(expected).zip(channels(actual)).forEachIndexed { i, (a, b) ->
            assertTrue("channel $i expected=$a actual=$b", abs(a - b) < .00002f)
        }
    }

    private fun channels(p: LumenActingPose) = listOf(p.openLeft, p.openRight, p.lowerLeft, p.lowerRight,
        p.innerLift, p.tension, p.pupil, p.smile, p.concern, p.roll, p.lift, p.lean, p.earLift, p.earSplit,
        p.gazeX, p.gazeY, p.squash, p.warmth)
}
