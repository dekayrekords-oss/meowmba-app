package com.example.meowmba.core.agent

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Omniroute, running inside the app.
 *
 * The routing decision — which model should answer this — does not need a server to make
 * it. Doing it here means the companion keeps choosing sensibly with no network at all,
 * and it means the phone is not required to call out to a box on the network to find out
 * which box to call. That is the point: **no request leaves the device to decide where a
 * request should go.**
 *
 * A route is a provider plus what it is good for. Selection is:
 *
 *  1. classify the prompt into one [RouteTag];
 *  2. take the routes carrying that tag, best first;
 *  3. try them in order, falling through on failure to the next.
 *
 * Fallback is the part that matters in practice. A phone moves between networks all day;
 * a router that picks the LAN model and then reports failure when you leave the house is
 * worse than no router. The on-device provider is always last in the chain, so the answer
 * degrades in quality rather than disappearing.
 */
class OmniRoute(
    private val routes: List<Route>,
    /**
     * Where the routing decision is written.
     *
     * Injectable because android.util.Log is not mocked in JVM unit tests, and a bare
     * Log call in here fails every test that lets a route win — the same trap
     * WakeWordEngineFactory documents. Tests pass a no-op.
     */
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) : ModelProvider {

    /** What a prompt needs, coarse enough to decide with no model in the loop. */
    enum class RouteTag { FAST, CHAT, CODING, REASONING, VISION }

    /**
     * One backend the router may choose.
     *
     * [offline] is not a preference, it is a fact about the route, and it is what makes
     * the last-resort ordering correct when the network is gone.
     */
    data class Route(
        val id: String,
        val label: String,
        val provider: ModelProvider,
        val tags: Set<RouteTag>,
        /** Lower goes first within a tag. */
        val priority: Int = 50,
        val offline: Boolean = false,
    )

    /**
     * The user's actual words for the next call, when the caller knows them.
     *
     * What reaches [generateStreaming] is an assembled prompt: persona, recalled
     * memories, the tool list, recent turns, and only then the question. Classifying that
     * is meaningless — the boilerplate alone contains braces and newlines, so measured on
     * device "what time is it" came out as CODING. The caller sets this to the raw turn
     * and the routing decision is made on the thing the user actually said.
     */
    @Volatile
    private var userTextHint: String? = null

    /** Tell the router what the user actually asked, before generating. */
    fun hintUserText(text: String?) {
        userTextHint = text?.takeIf { it.isNotBlank() }
    }

    private val _lastRoute = MutableStateFlow<String?>(null)

    /** Label of the route that most recently answered — shown on the LLM Manager. */
    val lastRoute: StateFlow<String?> = _lastRoute.asStateFlow()

    override val name: String
        get() = "Omniroute (${routes.size} routes)"

    /**
     * Which kind of model this prompt wants.
     *
     * Deliberately keyword-and-shape based rather than model-assisted: asking a model
     * which model to ask costs a round trip before any work starts, and gets it wrong in
     * exactly the cases where latency matters most.
     */
    fun classify(prompt: String): RouteTag {
        val text = (userTextHint ?: lastUserTurnOf(prompt)).trim()
        val lower = text.lowercase()

        if (CODE_MARKERS.containsMatchIn(text) ||
            CODING_PHRASES.any { lower.contains(it) }
        ) return RouteTag.CODING

        if (VISION_PHRASES.any { lower.contains(it) }) return RouteTag.VISION

        if (REASONING_PHRASES.any { lower.contains(it) } || text.length > LONG_PROMPT) {
            return RouteTag.REASONING
        }

        val words = lower.split(Regex("\\s+")).count { it.isNotBlank() }
        if (words <= SHORT_PROMPT_WORDS) return RouteTag.FAST

        return RouteTag.CHAT
    }

    /**
     * Best-effort recovery of the last user turn from an assembled prompt.
     *
     * Used when no hint was set. Falls back to the whole prompt rather than guessing
     * wrongly — a bad guess routes badly, and routing on everything at least routes
     * consistently.
     */
    private fun lastUserTurnOf(prompt: String): String {
        for (marker in USER_MARKERS) {
            val idx = prompt.lastIndexOf(marker, ignoreCase = true)
            if (idx >= 0) {
                val tail = prompt.substring(idx + marker.length)
                    .substringBefore("<|im_end|>")
                    .substringBefore("<end_of_turn>")
                    .trim()
                if (tail.isNotEmpty()) return tail
            }
        }
        return prompt
    }

    /**
     * The order this prompt should be attempted in, best first, fallbacks after.
     *
     * [preferRouteId] pins one route to the front regardless of classification —
     * used for direct question answering, where word-count routing kept handing
     * short questions to the small on-device model and it answered with another
     * question.
     */
    fun plan(prompt: String, preferRouteId: String? = null): List<Route> {
        val tag = classify(prompt)
        val tagged = routes.filter { tag in it.tags }.sortedBy { it.priority }
        val rest = routes.filterNot { it in tagged }
            // Offline last: it is the safety net, not the preference.
            .sortedWith(compareBy({ it.offline }, { it.priority }))
        val planned = tagged + rest
        val pinned = preferRouteId?.let { id -> planned.firstOrNull { it.id == id } }
        return if (pinned != null) listOf(pinned) + (planned - pinned) else planned
    }

    override suspend fun generate(prompt: String): ModelResponse {
        val plan = plan(prompt)
        if (plan.isEmpty()) {
            return ModelResponse.Error("No models are configured for Omniroute to route to.")
        }
        var lastError: String? = null
        for (route in plan) {
            val response = runCatching { route.provider.generate(prompt) }
                .getOrElse { ModelResponse.Error(it.message ?: it.javaClass.simpleName) }
            when (response) {
                is ModelResponse.Error -> lastError = "${route.label}: ${response.message}"
                else -> {
                    win(route, prompt)
                    return response
                }
            }
        }
        return ModelResponse.Error(
            "Every route failed. Last was ${lastError ?: "unknown"}."
        )
    }

    /**
     * Streaming with fallback.
     *
     * A route only counts as failed if it fails *before producing anything*. Once text has
     * been emitted the user is reading it, and silently restarting on a different model
     * mid-sentence would rewrite what they are looking at.
     */
    override fun generateStreaming(prompt: String): Flow<ModelResponse> =
        generateStreaming(prompt, preferRouteId = null)

    /** Streaming variant with a pinned first-choice route. See [plan]. */
    fun generateStreaming(prompt: String, preferRouteId: String?): Flow<ModelResponse> = flow {
        val plan = plan(prompt, preferRouteId)
        if (plan.isEmpty()) {
            emit(ModelResponse.Error("No models are configured for Omniroute to route to."))
            return@flow
        }
        var lastError: String? = null
        for (route in plan) {
            var produced = false
            val failure = runCatching {
                route.provider.generateStreaming(prompt).collect { response ->
                    when (response) {
                        is ModelResponse.Error ->
                            if (produced) {
                                emit(response)
                            } else {
                                lastError = "${route.label}: ${response.message}"
                            }
                        is ModelResponse.Partial -> {
                            if (!produced) {
                                produced = true
                                win(route, prompt)
                            }
                            emit(response)
                        }
                        is ModelResponse.Complete -> {
                            if (!produced) {
                                produced = true
                                win(route, prompt)
                            }
                            emit(response)
                        }
                    }
                }
            }.exceptionOrNull()

            if (produced) return@flow
            if (failure != null) {
                lastError = "${route.label}: ${failure.message ?: failure.javaClass.simpleName}"
            }
        }
        emit(ModelResponse.Error("Every route failed. Last was ${lastError ?: "unknown"}."))
    }

    /**
     * Records which route answered.
     *
     * Logged because "why did that answer come from there" is otherwise unanswerable
     * from outside: the routing decision leaves no other trace.
     */
    private fun win(route: Route, prompt: String) {
        _lastRoute.value = route.label
        log("routed ${classify(prompt)} -> ${route.id} (${route.label})")
    }

    private companion object {
        const val TAG = "OmniRoute"

        /** How the prompt builders and chat templates in this app mark a user turn. */
        val USER_MARKERS = listOf(
            "<|im_start|>user\n", "<start_of_turn>user\n", "\nUser:", "\nuser:",
        )

        /** Backticks, braces, obvious declarations — anything that reads as source. */
        val CODE_MARKERS = Regex("```|\\bfun \\w|\\bdef \\w|\\bclass \\w|#include|=>|\\{\\s*\\n")

        val CODING_PHRASES = listOf(
            "write a function", "write code", "refactor", "debug", "stack trace",
            "compile", "unit test", "regex", "sql query", "kotlin", "python",
            "javascript", "typescript", "why does this code", "fix this code",
        )

        val VISION_PHRASES = listOf(
            "what do you see", "look at this", "describe this photo",
            "describe the picture", "read this label", "what is in this image",
        )

        val REASONING_PHRASES = listOf(
            "explain why", "compare", "trade-off", "tradeoff", "step by step",
            "plan for", "strategy", "pros and cons", "analyse", "analyze",
            "work out", "figure out why",
        )

        /** Past this, treat it as thinking work regardless of wording. */
        const val LONG_PROMPT = 320

        /** "what time is it", "turn the lights off" — no need to wake a big model. */
        const val SHORT_PROMPT_WORDS = 6
    }
}
