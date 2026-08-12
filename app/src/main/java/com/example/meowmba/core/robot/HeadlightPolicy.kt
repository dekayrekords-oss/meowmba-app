package com.example.meowmba.core.robot

/**
 * Headlight stays off unless the user asked or the camera cannot see.
 *
 * Moods, expressions, and glow clips must not flip fed2. Auto uses a
 * Schmitt trigger on mean luma so a lamp flicker does not strobe the LED.
 */
object HeadlightPolicy {
    const val DARK_ON = 34
    const val BRIGHT_OFF = 56

    enum class Mode { AUTO, FORCE_ON, FORCE_OFF }

    fun shouldBeOn(meanLuma: Int, mode: Mode, currentlyOn: Boolean): Boolean = when (mode) {
        Mode.FORCE_ON -> true
        Mode.FORCE_OFF -> false
        Mode.AUTO -> when {
            meanLuma <= DARK_ON -> true
            meanLuma >= BRIGHT_OFF -> false
            else -> currentlyOn
        }
    }
}
