package com.example.meowmba.core.car

/** The vision prompt has a typed prefix; words in its explanation are not a verdict. */
object CrashSceneVerdict {
    fun parse(description: String?): Boolean? = when {
        description?.trim()?.startsWith("SAFE:", ignoreCase = true) == true -> false
        description?.trim()?.startsWith("DANGER:", ignoreCase = true) == true -> true
        else -> null
    }
}
