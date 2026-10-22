package com.example.meowmba.core.robot.cozmo

/** Original finite gestures, not Anki animation assets. No autonomous wheel movement. */
data class CozmoReactionStep(val atMs: Long, val head: Float? = null,
    val lift: Float? = null, val expression: String? = null)

object CozmoReactions {
    val names = listOf("nod", "greet", "celebrate", "peek", "sleep", "wake")
    fun steps(name: String): List<CozmoReactionStep> = when (name) {
        "nod" -> listOf(CozmoReactionStep(0, head = 20f, expression = "happy"),
            CozmoReactionStep(600, head = -15f), CozmoReactionStep(1200, head = 20f), CozmoReactionStep(1800, head = 0f))
        "greet" -> listOf(CozmoReactionStep(0, head = 30f, lift = 75f, expression = "happy"),
            CozmoReactionStep(800, lift = 45f), CozmoReactionStep(1600, lift = 75f), CozmoReactionStep(2400, head = 0f, lift = 32f))
        "celebrate" -> listOf(CozmoReactionStep(0, head = 35f, lift = 92f, expression = "happy"),
            CozmoReactionStep(900, lift = 50f), CozmoReactionStep(1800, lift = 92f), CozmoReactionStep(2700, head = 0f, lift = 32f))
        "peek" -> listOf(CozmoReactionStep(0, head = -20f, expression = "curious"),
            CozmoReactionStep(900, head = 40f, expression = "surprised"), CozmoReactionStep(1800, head = 0f))
        "sleep" -> listOf(CozmoReactionStep(0, head = -25f, lift = 32f, expression = "sleepy"))
        "wake" -> listOf(CozmoReactionStep(0, head = 35f, expression = "surprised"),
            CozmoReactionStep(1000, head = 0f, expression = "neutral"))
        else -> emptyList()
    }
}
