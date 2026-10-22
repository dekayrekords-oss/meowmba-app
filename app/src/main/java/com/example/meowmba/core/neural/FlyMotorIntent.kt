package com.example.meowmba.core.neural

/** Bounded cartoon retarget of measured populations, never a physical actuator command. */
data class FlyMotorIntent(
    val active: Boolean = false,
    val turn: Float = 0f,
    val forward: Float = 0f,
    val backward: Float = 0f,
    val halt: Float = 0f,
    val landing: Float = 0f,
    val takeoff: Float = 0f,
    val jump: Float = 0f,
    val escape: Float = 0f,
    val grooming: Float = 0f,
) {
    fun bounded(): FlyMotorIntent {
        if (!active || !listOf(turn, forward, backward, halt, landing, takeoff, jump, escape, grooming).all { it.isFinite() }) return NONE
        return copy(turn = turn.coerceIn(-1f, 1f), forward = forward.coerceIn(0f, 1f),
            backward = backward.coerceIn(0f, 1f), halt = halt.coerceIn(0f, 1f),
            landing = landing.coerceIn(0f, 1f), takeoff = takeoff.coerceIn(0f, 1f),
            jump = jump.coerceIn(0f, 1f), escape = escape.coerceIn(0f, 1f), grooming = grooming.coerceIn(0f, 1f))
    }
    companion object { val NONE = FlyMotorIntent() }
}
