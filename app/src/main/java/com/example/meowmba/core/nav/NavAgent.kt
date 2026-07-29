package com.example.meowmba.core.nav

/**
 * The navigation brain: turns an utterance into either a route or a question.
 *
 * Behaviour follows the two cases the user actually asks for:
 *
 *  - "take me to Circle K"     — a specific name, so route to the nearest one and say so.
 *  - "take me to get a burger" — vague, so pull the closest few and ask whether they
 *                                want the nearest or would rather pick.
 *
 * Holds one piece of state, [pending], so the follow-up ("the second one", "yes",
 * "Whataburger") can be resolved against the list we just offered.
 */
class NavAgent(
    private val repository: PlaceRepository,
) {

    sealed interface Outcome {
        /** Not a navigation request; caller should keep looking for a handler. */
        data object NotNavigation : Outcome

        /** Routing now. */
        data class Navigating(val place: Place, val say: String) : Outcome

        /** Offered a list; awaiting a pick. */
        data class Choose(val places: List<Place>, val say: String) : Outcome

        /** A saved place ("home"), which the caller resolves from its own store. */
        data class SavedPlace(val name: String) : Outcome

        data class NeedLocation(val say: String) : Outcome
        data class NoResults(val query: String, val say: String) : Outcome
        data class NoSource(val say: String) : Outcome
    }

    /** The list most recently offered to the user, if we are awaiting a choice. */
    var pending: List<Place>? = null
        private set

    fun clearPending() { pending = null }

    /**
     * Synchronous "is this mine?" check, so the caller's command dispatcher can decide
     * without launching a coroutine.
     *
     * Deliberately narrow while a list is pending: only an actual selection is claimed.
     * If the user ignores the question and says something else entirely, that message
     * must reach the normal chat path rather than vanishing into the navigator.
     */
    fun claims(text: String): Boolean {
        pending?.let { if (resolveChoice(text, it) != null) return true }
        return NavRequestParser.parse(text) != null
    }

    /**
     * @param lat/[lon] current position, or null if unavailable.
     * @param savedPlaceNames lowercased names the user has taught us.
     */
    suspend fun handle(
        text: String,
        lat: Double?,
        lon: Double?,
        savedPlaceNames: Set<String> = emptySet(),
    ): Outcome {
        // A pending list takes priority: "the second one" is only meaningful now.
        pending?.let { list ->
            resolveChoice(text, list)?.let { chosen ->
                pending = null
                return Outcome.Navigating(chosen, "Routing to ${chosen.name}")
            }
            // Not a choice — fall through and treat it as a fresh request.
        }

        val request = NavRequestParser.parse(text, savedPlaceNames)
            ?: return Outcome.NotNavigation

        if (request is NavRequest.Saved) return Outcome.SavedPlace(request.placeName)

        if (lat == null || lon == null) {
            return Outcome.NeedLocation("I need location access to find that.")
        }

        val result = repository.find(request, lat, lon)

        if (result.noSourceAvailable) {
            return Outcome.NoSource(
                "I have no place data offline and no Geoapify key set, so I can't " +
                    "search. I can open Maps for you instead.",
            )
        }

        val places = result.places
        if (places.isEmpty()) {
            val what = describe(request)
            return Outcome.NoResults(what, "I couldn't find $what anywhere nearby.")
        }

        val nearest = places.first()
        return when (request) {
            is NavRequest.Named -> {
                pending = null
                Outcome.Navigating(
                    nearest,
                    "Nearest ${request.query} is ${nearest.name}" +
                        (nearest.distanceLabel()?.let { ", $it away" } ?: "") +
                        ". Routing you there.",
                )
            }

            is NavRequest.Category -> {
                pending = places
                Outcome.Choose(
                    places,
                    "Closest is ${nearest.name}" +
                        (nearest.distanceLabel()?.let { ", $it away" } ?: "") +
                        ". Want that one, or would you rather pick?",
                )
            }

            is NavRequest.Saved -> Outcome.NotNavigation   // handled above
        }
    }

    /**
     * Interprets a reply to a [Outcome.Choose] prompt. Returns null when the reply is
     * not a selection at all, so the caller can treat it as a new request.
     */
    private fun resolveChoice(text: String, list: List<Place>): Place? {
        val t = text.lowercase().trim().trimEnd('.', '!', '?')
        if (t.isBlank()) return null

        // "yes" / "closest" / "that one" -> take the nearest.
        if (t in ACCEPT_NEAREST) return list.firstOrNull()

        // "2" / "number 2" / "the second one" / "second"
        ORDINALS.entries.firstOrNull { (word, _) ->
            t == word || t == "the $word" || t == "$word one" || t == "the $word one"
        }?.let { (_, idx) -> return list.getOrNull(idx) }

        Regex("""^(?:number\s+|option\s+|#)?(\d{1,2})$""").find(t)?.let { m ->
            val idx = m.groupValues[1].toInt() - 1
            return list.getOrNull(idx)
        }

        // A name from the list — require a reasonably specific match so a stray word
        // does not silently pick a destination.
        if (t.length >= 3) {
            list.firstOrNull { it.name.lowercase().contains(t) }?.let { return it }
        }
        return null
    }

    private fun describe(request: NavRequest): String = when (request) {
        is NavRequest.Named -> request.query
        is NavRequest.Category -> request.label
        is NavRequest.Saved -> request.placeName
    }

    private companion object {
        val ACCEPT_NEAREST = setOf(
            "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "closest", "the closest",
            "nearest", "the nearest", "that one", "that works", "go", "let's go",
            "lets go", "first", "the first", "first one", "the first one", "1",
        )
        val ORDINALS = mapOf(
            "first" to 0, "second" to 1, "third" to 2, "fourth" to 3,
            "fifth" to 4, "sixth" to 5, "seventh" to 6, "eighth" to 7,
        )
    }
}
