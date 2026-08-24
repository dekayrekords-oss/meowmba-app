package com.example.meowmba.core.vision

import kotlin.math.hypot

/**
 * Named social cues from face classification + a few pose landmarks.
 * Pure so the mapping is unit-tested without CameraX or ML Kit.
 */
enum class SocialCue {
    NONE,
    SMILE,
    WINK,
    BLINK,
    EYES_WIDE,
    WAVE,
    PALM,
    FIST,
    THUMBS_UP,
    POINT,
    HAND_UP,
}

enum class FollowSource { FACE, HAND }

data class HandSample(
    val wristX: Float,
    val wristY: Float,
    val elbowX: Float,
    val elbowY: Float,
    val thumbX: Float? = null,
    val thumbY: Float? = null,
    val indexX: Float? = null,
    val indexY: Float? = null,
    val pinkyX: Float? = null,
    val pinkyY: Float? = null,
)

object SocialCues {

    fun faceCue(smile: Float, leftEyeOpen: Float, rightEyeOpen: Float): SocialCue {
        if (smile < 0f && leftEyeOpen < 0f) return SocialCue.NONE
        val winkLeft = leftEyeOpen in 0f..0.25f && rightEyeOpen > 0.65f
        val winkRight = rightEyeOpen in 0f..0.25f && leftEyeOpen > 0.65f
        if (winkLeft || winkRight) return SocialCue.WINK
        if (leftEyeOpen in 0f..0.22f && rightEyeOpen in 0f..0.22f) return SocialCue.BLINK
        if (leftEyeOpen > 0.92f && rightEyeOpen > 0.92f && smile < 0.35f) return SocialCue.EYES_WIDE
        if (smile > 0.65f) return SocialCue.SMILE
        return SocialCue.NONE
    }

    fun handRaised(hand: HandSample): Boolean {
        val aboveElbow = hand.wristY < hand.elbowY - 0.04f
        // Close-up cover camera often has no usable elbow. A wrist in the
        // upper half of the frame is still a hand the user is showing us.
        val highInFrame = hand.wristY < 0.58f
        return (aboveElbow || highInFrame) && hand.wristY < 0.80f
    }

    fun handSign(hand: HandSample, recentWristX: List<Float>): SocialCue {
        if (!handRaised(hand)) return SocialCue.NONE
        if (recentWristX.size >= 5) {
            val span = (recentWristX.maxOrNull() ?: 0f) - (recentWristX.minOrNull() ?: 0f)
            if (span > 0.22f) return SocialCue.WAVE
        }
        val wrist = hand.wristX to hand.wristY
        val thumb = hand.thumbX?.let { it to hand.thumbY!! }
        val index = hand.indexX?.let { it to hand.indexY!! }
        val pinky = hand.pinkyX?.let { it to hand.pinkyY!! }
        if (thumb != null && index != null && pinky != null) {
            val dThumb = dist(wrist, thumb)
            val dIndex = dist(wrist, index)
            val dPinky = dist(wrist, pinky)
            val curled = 0.12f
            val extended = 0.18f
            if (dIndex > extended && dThumb < curled && dPinky < curled) return SocialCue.POINT
            if (thumb.second < hand.wristY - 0.06f && dIndex < curled && dPinky < curled) {
                return SocialCue.THUMBS_UP
            }
            if (dThumb < curled && dIndex < curled && dPinky < curled) return SocialCue.FIST
            if (dThumb > extended && dIndex > extended && dPinky > extended) return SocialCue.PALM
        }
        return SocialCue.HAND_UP
    }

    /** Follow a raised, moving, or motion-blob hand — "watch this". */
    fun pickFollow(
        handRaised: Boolean,
        handMoving: Boolean,
        motionHand: Boolean = false,
    ): FollowSource =
        if (handRaised || handMoving || motionHand) FollowSource.HAND else FollowSource.FACE

    fun pickCue(face: SocialCue, hand: SocialCue): SocialCue =
        if (hand != SocialCue.NONE) hand else face

    fun followOffset(hand: HandSample): Pair<Float, Float> =
        (hand.wristX * 2f - 1f).coerceIn(-1f, 1f) to
            (hand.wristY * 2f - 1f).coerceIn(-1f, 1f)

    /**
     * Whether this frame should fire a face/clip reaction.
     *
     * Natural blinks are ignored — they would replay "slow blink" every few
     * seconds. Same cue is locked out longer than a new one so a held smile
     * does not spam happy-bop.
     */
    fun shouldReact(
        cue: SocialCue,
        lastFired: SocialCue,
        nowMs: Long,
        lastFiredAtMs: Long,
        minGapMs: Long = 1_800L,
        sameCueGapMs: Long = 4_500L,
    ): Boolean {
        if (cue == SocialCue.NONE || cue == SocialCue.BLINK) return false
        // A locked face looking at the camera is "eyes wide" every frame,
        // and a close-up wrist reads as HAND_UP. Those clips made the
        // avatar jump while the person was just standing there.
        if (cue == SocialCue.EYES_WIDE || cue == SocialCue.HAND_UP) return false
        if (lastFiredAtMs <= 0L) return true
        val dt = nowMs - lastFiredAtMs
        if (dt < minGapMs) return false
        if (cue == lastFired && dt < sameCueGapMs) return false
        return true
    }

    fun reaction(cue: SocialCue): Pair<String, String?> = when (cue) {
        SocialCue.SMILE -> "HAPPY" to "happy bop"
        SocialCue.WINK, SocialCue.BLINK -> "CURIOUS" to "slow blink"
        SocialCue.EYES_WIDE -> "ALERT" to "curious tilt"
        SocialCue.WAVE -> "HAPPY" to "ear wiggle"
        SocialCue.PALM, SocialCue.HAND_UP, SocialCue.POINT -> "CURIOUS" to "curious tilt"
        SocialCue.THUMBS_UP -> "PROUD" to "happy bop"
        SocialCue.FIST -> "ALERT" to "ear flatten"
        SocialCue.NONE -> "IDLE" to null
    }

    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
        hypot(a.first - b.first, a.second - b.second)
}
