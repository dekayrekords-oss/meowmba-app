package com.example.meowmba.core.robot

/** Robot identity is a boundary, not a choice based on which connection happens to be live. */
object RobotCommandTarget {
    private val cozmoName = Regex("\\b(?:cozmo|cosmo)\\b", RegexOption.IGNORE_CASE)
    private val looiName = Regex("\\blooi\\b", RegexOption.IGNORE_CASE)

    /** Keep legacy unnamed body commands on LOOI; Cozmo always needs an explicit name. */
    fun permits(toolId: String, request: String): Boolean = when (toolId) {
        "robot.move" -> !cozmoName.containsMatchIn(request)
        "cozmo.control" -> cozmoName.containsMatchIn(request) && !looiName.containsMatchIn(request)
        // Avatar performances also feed LOOI's body scheduler; they are not a named-robot fallback.
        "avatar.animate" -> !cozmoName.containsMatchIn(request) && !looiName.containsMatchIn(request)
        else -> true
    }
}
