package com.example.meowmba.core.behavior

import kotlin.math.exp

/** NaN/Inf are treated as "no signal" rather than propagating into the drives. */
private fun Float.clamp01(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f

/** Who or what the companion is oriented toward. The reasoning layer writes this. */
enum class SocialTarget { NONE, USER, UNKNOWN_PERSON, SOUND, MOTION, SELF }

/**
 * Continuous drives, every value in 0..1.
 *
 * This is the small always-running state that makes the character feel alive: the
 * reasoning layer (LLM) never animates anything directly, it writes these few
 * scalars, and the simulator turns them into smooth micro-behaviour every frame.
 * Deliberately not a mood enum: moods are discrete and event-driven
 * (`core.personality.MoodEngine` already does that), while drives are continuous
 * and can be blended, decayed and read at frame rate.
 */
data class DriveVector(
    /** Desire to close distance on the social target. */
    val approach: Float = 0.30f,
    /** Desire to engage with the social target. */
    val social: Float = 0.35f,
    /** Desire to investigate novelty. Drives look-around and inspect. */
    val curiosity: Float = 0.40f,
    /** Desire to withdraw or hold still. Suppresses large motion. */
    val caution: Float = 0.05f,
    /** Capacity for expressive motion. Drains with arousal, recovers at rest. */
    val energy: Float = 1.0f,
) {
    fun clamped(): DriveVector = DriveVector(
        approach = approach.clamp01(),
        social = social.clamp01(),
        curiosity = curiosity.clamp01(),
        caution = caution.clamp01(),
        energy = energy.clamp01(),
    )

    fun isFinite(): Boolean =
        approach.isFinite() && social.isFinite() && curiosity.isFinite() &&
            caution.isFinite() && energy.isFinite()
}

/**
 * What the motor/animation layer actually reads. Every value is a gain or a
 * weight, never an absolute pose, so the existing springs and authored
 * performances stay in control of the final shape.
 */
data class BehaviorBias(
    /** Amplitude multiplier on idle glances. */
    val glanceGain: Float,
    /** How strongly to track the social target. */
    val followGain: Float,
    /** Relative weights over the idle glance families; they need not sum to 1. */
    val restWeight: Float,
    val inspectWeight: Float,
    val verticalWeight: Float,
    /** Head/ear expressiveness. */
    val earGain: Float,
    /** Multiplier on blink rate (1 = nominal). */
    val blinkRateScale: Float,
    /** 0..1 calm..activated. */
    val arousal: Float,
    /** -1..1 negative..positive. */
    val valence: Float,
)

/** Things the senses can report. Magnitudes are 0..1. */
enum class Stimulus {
    FACE_SEEN, FACE_LOST, LOUD_SOUND, MOTION, TOUCH,
    SPOKEN_TO, PRAISE, NOVELTY, THREAT, TASK_DONE,
}

/**
 * A drive integrator: cheap, pure, and frame-rate independent.
 *
 * Two things write into it. Stimuli arrive from the senses ([onStimulus]) as
 * impulses. Intent arrives from the reasoning layer ([setIntent]) as *targets*
 * the drives ease toward, which is what stops the LLM from needing to animate
 * anything: it says "pay attention to the user, and want to approach", and the
 * simulator handles the timing, easing and recovery on its own.
 *
 * Pure Kotlin and free of Android imports so it can be unit-tested on the host
 * JVM without Robolectric.
 */
class NervousSystem(
    initial: DriveVector = DriveVector(),
    /** Where the drives relax back to. Deliberately NOT defaulted to [initial]:
     * defaulting it to the starting state would make that state the resting
     * state, and nothing would ever move. */
    private val baseline: DriveVector = DriveVector(),
    private val tau: Tau = Tau(),
    private val energyDrainMs: Float = 90_000f,
    /**
     * Recovery is faster than drain on purpose. Energy settles where
     * drain == recover, i.e. at arousal = drainMs / (drainMs + recoverMs)
     * = 0.6. The baseline drives sit near 0.39, so a calm character genuinely
     * recovers instead of slowly winding down to zero.
     */
    private val energyRecoverMs: Float = 60_000f,
) {

    /** Per-drive relaxation time constants, in milliseconds. */
    data class Tau(
        val approach: Float = 4_000f,
        val social: Float = 6_000f,
        val curiosity: Float = 3_000f,
        val caution: Float = 2_500f,
    )

    /**
     * Written by the drive tick (a background dispatcher) and read by the render
     * loop (main thread) every frame, so it must be published, not just cached
     * per-thread. Reads take the immutable snapshot; a torn value is impossible
     * because every write replaces the whole vector.
     */
    @Volatile
    private var drives: DriveVector = initial.clamped()
    private var intentApproach: Float? = null

    @Volatile
    private var intentValence: Float = 0f

    /** Current orientation, written by the reasoning layer. */
    var socialTarget: SocialTarget = SocialTarget.NONE
        private set

    /**
     * The reasoning layer's half of the loop: high-level state, not animation.
     * [approach] null means "no instruction" and the drive relaxes to baseline.
     */
    @Synchronized
    fun setIntent(
        target: SocialTarget,
        approach: Float? = null,
        valenceBias: Float = 0f,
    ) {
        socialTarget = target
        intentApproach = approach?.takeIf { it.isFinite() }?.clamp01()
        intentValence = valenceBias.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
    }

    /** Drop the LLM's instruction; the character falls back to its own baseline. */
    @Synchronized
    fun clearIntent() {
        socialTarget = SocialTarget.NONE
        intentApproach = null
        intentValence = 0f
    }

    /**
     * A sense reports something. Impulses are additive and then clamped.
     * Synchronized: stimuli arrive on the main thread while [tick] runs on a
     * background dispatcher, and both are read-modify-write on [drives].
     */
    @Synchronized
    fun onStimulus(kind: Stimulus, magnitude: Float = 1f) {
        if (!magnitude.isFinite() || magnitude <= 0f) return
        val m = magnitude.clamp01()
        drives = when (kind) {
            Stimulus.FACE_SEEN -> drives.copy(
                social = drives.social + 0.35f * m,
                curiosity = drives.curiosity + 0.15f * m,
            )
            Stimulus.FACE_LOST -> drives.copy(
                social = drives.social - 0.30f * m,
                curiosity = drives.curiosity + 0.20f * m,
            )
            Stimulus.SPOKEN_TO -> drives.copy(
                social = drives.social + 0.40f * m,
                approach = drives.approach + 0.15f * m,
            )
            Stimulus.PRAISE -> drives.copy(
                social = drives.social + 0.30f * m,
                caution = drives.caution - 0.25f * m,
            )
            Stimulus.NOVELTY, Stimulus.MOTION -> drives.copy(
                curiosity = drives.curiosity + 0.45f * m,
            )
            Stimulus.LOUD_SOUND -> drives.copy(
                caution = drives.caution + 0.45f * m,
                curiosity = drives.curiosity + 0.20f * m,
            )
            Stimulus.THREAT -> drives.copy(
                caution = drives.caution + 0.60f * m,
                approach = drives.approach - 0.40f * m,
            )
            Stimulus.TOUCH -> drives.copy(
                social = drives.social + 0.25f * m,
                caution = drives.caution + 0.10f * m,
            )
            Stimulus.TASK_DONE -> drives.copy(
                caution = drives.caution - 0.20f * m,
                social = drives.social + 0.15f * m,
            )
        }.clamped()
    }

    /**
     * Advance [dtMs] of simulated time.
     *
     * Exponential relaxation toward the target, so the result depends on elapsed
     * time rather than on how many frames it was chopped into: one 1000 ms step
     * and a hundred 10 ms steps land in the same place. That matters because the
     * avatar clock is cadence-throttled and may skip frames.
     */
    @Synchronized
    fun tick(dtMs: Long) {
        if (dtMs <= 0L) return
        val dt = dtMs.toFloat()
        val approachTarget = intentApproach ?: baseline.approach
        drives = DriveVector(
            approach = relax(drives.approach, approachTarget, tau.approach, dt),
            social = relax(drives.social, baseline.social, tau.social, dt),
            curiosity = relax(drives.curiosity, baseline.curiosity, tau.curiosity, dt),
            caution = relax(drives.caution, baseline.caution, tau.caution, dt),
            // Energy has no relaxation target; it is integrated against arousal below.
            energy = drives.energy,
        )
        // Energy: arousal spends it, calm restores it. Integrated, not relaxed,
        // so a long excited stretch genuinely tires the character out.
        val arousal = rawArousal(drives)
        val drain = dt / energyDrainMs * arousal
        val recover = dt / energyRecoverMs * (1f - arousal)
        drives = drives.copy(energy = (drives.energy - drain + recover).clamp01())
    }

    /** Current drives. Safe to read every frame; returns a snapshot. */
    fun snapshot(): DriveVector = drives

    /** The strongest drive, for logging and debug overlays. */
    fun dominant(): String = with(drives) {
        listOf(
            "approach" to approach, "social" to social, "curiosity" to curiosity,
            "caution" to caution, "tired" to (1f - energy),
        ).maxBy { it.second }.first
    }

    /** Gains and weights for the motor layer. */
    fun bias(): BehaviorBias {
        val d = drives
        val arousal = rawArousal(d)
        val valence = ((d.social - 0.5f) * 1.2f + (0.5f - d.caution) * 0.8f + intentValence)
            .coerceIn(-1f, 1f)
        // Caution and low energy both quiet the character down.
        val vitality = ((1f - d.caution) * 0.6f + d.energy * 0.4f).coerceIn(0f, 1f)
        val inspection = (d.curiosity * (1f - d.caution)).coerceIn(0f, 1f)
        return BehaviorBias(
            glanceGain = (0.55f + 0.45f * d.curiosity) * vitality,
            followGain = (0.35f + 0.65f * d.social) * vitality,
            restWeight = (1f - inspection) * (1.2f - d.curiosity) + 0.05f,
            inspectWeight = inspection + 0.05f,
            verticalWeight = inspection * 0.7f + 0.05f,
            earGain = (0.4f + 0.6f * arousal) * vitality,
            blinkRateScale = (1f + d.caution * 0.8f - inspection * 0.25f).coerceIn(0.5f, 2f),
            arousal = arousal,
            valence = valence,
        )
    }

    private fun rawArousal(d: DriveVector): Float =
        ((d.approach * 0.25f) + (d.curiosity * 0.35f) + (d.caution * 0.40f) +
            ((1f - d.energy) * 0.15f)).coerceIn(0f, 1f)

    private fun relax(current: Float, target: Float, tauMs: Float, dtMs: Float): Float {
        if (tauMs <= 0f) return target
        return target + (current - target) * exp(-dtMs / tauMs)
    }

    companion object {
        /**
         * The bias of a character at rest: every drive at its baseline, full
         * energy, no intent.
         *
         * The motor layer divides by this rather than using the raw bias, so a
         * calm, rested character animates exactly as it was hand-tuned and only
         * a genuine departure from rest — curiosity, caution, tiredness — moves
         * it. Without this the character would sit permanently quieter than the
         * animations were authored to be.
         */
        val REST_BIAS: BehaviorBias = NervousSystem().bias()
    }
}
