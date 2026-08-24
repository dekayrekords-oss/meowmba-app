package com.example.meowmba.ui.companion

import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.audio.WakePhraseMatcher

/**
 * What the app-drawer search bar should do with a submitted line.
 *
 * The bar filters apps while you type. Enter / the search key then picks
 * one of: open the app, ask Meow, run a phone task, or search Google.
 */
object DrawerQuery {

    enum class Kind { FIND_APP, ANSWER, PHONE_TASK, WEB_SEARCH }

    sealed class Action {
        data class Launch(val name: String) : Action()
        data object Ask : Action()
        data object Task : Action()
        data class Web(val query: String) : Action()
        data object Stay : Action()
    }

    private val PHONE_CUES = listOf(
        "open ", "launch ", "start ", "set ", "turn ", "call ", "text ",
        "message ", "send ", "email ", "remind ", "schedule ", "play ",
        "pause ", "skip ", "volume ", "flashlight", "timer", "alarm",
        "navigate ", "directions ", "brightness", "do not disturb",
        "take a photo", "take a picture", "take a selfie",
        "clean ", "cleanup", "tidy ", "organize ", "organise ", "declutter",
        "free up space",
    )

    fun classify(raw: String): Kind {
        val t = SpeechIntents.stripAddress(WakePhraseMatcher.normalize(raw.trim()))
        if (t.isBlank()) return Kind.FIND_APP
        SpeechIntents.extractSearchQuery(t)?.let { return Kind.WEB_SEARCH }
        if (SpeechIntents.looksLikeDirectQuestion(t)) return Kind.ANSWER
        if (SpeechIntents.isMultiStepPhoneGoal(t)) return Kind.PHONE_TASK
        val routed = AgentRouter.route(raw)
        if (routed != null) {
            return if (routed.toolId == "web.search") Kind.WEB_SEARCH else Kind.PHONE_TASK
        }
        if (PHONE_CUES.any { t.contains(it) || t == it.trim() }) return Kind.PHONE_TASK
        return Kind.FIND_APP
    }

    fun webQuery(raw: String): String {
        val t = SpeechIntents.stripAddress(WakePhraseMatcher.normalize(raw.trim()))
        return SpeechIntents.extractSearchQuery(t) ?: raw.trim()
    }

    /**
     * [matchCount] / [singleLabel] are the live app-filter results for [raw].
     * A unique or exact-name match opens the app; zero matches go to Google.
     */
    fun resolve(raw: String, matchCount: Int, singleLabel: String?): Action {
        val q = raw.trim()
        if (q.isBlank()) return Action.Stay
        return when (classify(q)) {
            Kind.ANSWER -> Action.Ask
            Kind.PHONE_TASK -> Action.Task
            Kind.WEB_SEARCH -> Action.Web(webQuery(q))
            Kind.FIND_APP -> when {
                !singleLabel.isNullOrBlank() &&
                    (matchCount == 1 || singleLabel.equals(q, ignoreCase = true)) ->
                    Action.Launch(singleLabel)
                matchCount == 0 -> Action.Web(q)
                else -> Action.Stay
            }
        }
    }

    fun hint(raw: String, matchCount: Int, singleLabel: String?): String? {
        val q = raw.trim()
        if (q.isBlank()) return null
        return when (val action = resolve(q, matchCount, singleLabel)) {
            is Action.Launch -> "Open ${action.name}"
            Action.Ask -> "Ask Meow"
            Action.Task -> "Do this on the phone"
            is Action.Web -> "Search Google"
            Action.Stay -> "$matchCount apps"
        }
    }
}
