package com.example.meowmba.core.robot

/**
 * How much the base is allowed to move.
 *
 * [DESK] is the fail-safe default and the value used whenever anything is
 * uncertain — an unparsed sensor frame, a dropped link, a fresh start. The
 * only difference that matters is that DESK never translates, so it cannot
 * drive off an edge.
 */
enum class BodyProfile { DESK, FLOOR }

/** One physical action the LOOI base can perform. */
sealed class BodyVerb {

    /** Neck pitch: -1 fully up, 0 center, +1 down. Safety-clamped in the link. */
    data class Neck(val pitch: Float) : BodyVerb()

    /**
     * In-place rotation for [ticks] (~20ms each) at [speedPercent].
     *
     * [sign] (-1 left, +1 right) records the direction the *face* implied. It
     * is deliberately carried but not yet honoured: LooiBleProtocol.rotateFrame
     * exposes no direction parameter, so the base turns whichever way the
     * verified packet turns. See BodyMotionMap's note on turn direction.
     */
    data class Turn(val speedPercent: Int, val ticks: Int, val sign: Int) : BodyVerb()

    /** Forward drive, 0..1. The base has no reverse channel. FLOOR only. */
    data class Drive(val speed: Float) : BodyVerb()

    data class Light(val on: Boolean) : BodyVerb()

    /** Hardware brake plus motor halt. */
    object Brake : BodyVerb()
}

/** A verb scheduled at a moment on the clip's own timeline. */
data class BodyCue(val atMs: Int, val verb: BodyVerb)