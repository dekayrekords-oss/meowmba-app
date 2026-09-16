package com.example.meowmba.core.nav

/**
 * On-device parsing of navigation requests. No network, no LLM — this has to keep
 * working when Meowmba is standalone and meow is unreachable.
 *
 * The whole job is to split what the user said into:
 *   - a trigger ("take me to", "i need", ...) which we discard, and
 *   - a target, which is either a known category word, a saved place, or a name.
 *
 * Anything that is not a recognised category word or saved place is treated as a
 * business name. That default is deliberate: an unknown word is far more likely to
 * be a brand ("Circle K", "Buc-ee's") than a category we forgot to list, and a
 * name search that misses degrades to "no results" rather than to a wrong answer.
 */
object NavRequestParser {

    /** Phrases that mean "navigate somewhere". Longest-first so prefixes don't shadow. */
    private val TRIGGERS = listOf(
        "take me to get some", "take me to get a", "take me to get",
        "take me out for some", "take me out for a", "take me out for",
        "take me for some", "take me for a", "take me for",
        "i want to go to", "i wanna go to", "i need to get to",
        "give me directions to", "directions to", "navigate me to", "navigate to",
        "drive me to", "take me to", "let's go to", "lets go to", "go to",
        "where is the nearest", "where's the nearest", "wheres the nearest",
        "where is the closest", "where's the closest", "find me the nearest",
        "find me the closest", "find me a", "find me some", "find the nearest",
        "nearest", "closest",
        "i need some", "i need a", "i need", "i want some", "i want a",
        "let's get some", "lets get some", "let's get a", "lets get a",
        "let's get", "lets get", "i could go for a", "i could go for some",
        "i could go for", "in the mood for",
    ).sortedByDescending { it.length }

    /** Noise words to peel off the front/back of the target. */
    private val LEADING_NOISE = listOf("a ", "an ", "some ", "the ", "my ")
    private val TRAILING_NOISE = listOf(
        " please", " nearby", " near me", " around here", " close by", " somewhere",
    )

    /**
     * Category words -> OSM/Geoapify categories. Hitting one of these means the
     * request is vague and the user should get a list to choose from.
     *
     * Category ids follow Geoapify's taxonomy, which is OSM-derived, so the same
     * strings work against the offline OSM pack.
     */
    private val CATEGORIES: List<Triple<List<String>, String, List<String>>> = listOf(
        Triple(
            listOf("burger", "burgers", "hamburger", "cheeseburger"),
            "burger",
            listOf("catering.fast_food.burger", "catering.restaurant.burger"),
        ),
        Triple(
            listOf("coffee", "espresso", "latte", "cup of coffee"),
            "coffee",
            listOf("catering.cafe.coffee_shop", "catering.cafe"),
        ),
        Triple(
            listOf("gas", "gasoline", "fuel", "petrol", "fill up", "gas station"),
            "gas",
            listOf("service.vehicle.fuel", "commercial.gas"),
        ),
        Triple(
            listOf("pizza"),
            "pizza",
            listOf("catering.fast_food.pizza", "catering.restaurant.pizza"),
        ),
        Triple(
            listOf("taco", "tacos", "mexican"),
            "tacos",
            listOf("catering.restaurant.mexican", "catering.fast_food.mexican"),
        ),
        Triple(
            listOf("sushi", "japanese"),
            "sushi",
            listOf("catering.restaurant.sushi", "catering.restaurant.japanese"),
        ),
        Triple(
            listOf("chinese", "chinese food"),
            "Chinese food",
            listOf("catering.restaurant.chinese"),
        ),
        Triple(
            listOf("sandwich", "sandwiches", "sub", "subs", "deli"),
            "a sandwich",
            listOf("catering.fast_food.sandwich", "catering.restaurant.sandwich"),
        ),
        Triple(
            listOf("chicken", "fried chicken", "wings"),
            "chicken",
            listOf("catering.fast_food.chicken", "catering.restaurant.chicken"),
        ),
        Triple(
            listOf("breakfast", "brunch", "pancakes"),
            "breakfast",
            listOf("catering.restaurant.breakfast", "catering.cafe"),
        ),
        Triple(
            listOf("beer", "a drink", "drinks", "bar", "pub"),
            "a drink",
            listOf("catering.bar", "catering.pub"),
        ),
        Triple(
            listOf("food", "eat", "something to eat", "dinner", "lunch", "hungry"),
            "food",
            listOf("catering.restaurant", "catering.fast_food"),
        ),
        Triple(
            listOf("groceries", "grocery", "grocery store", "supermarket"),
            "groceries",
            listOf("commercial.supermarket"),
        ),
        Triple(
            listOf("pharmacy", "drug store", "drugstore", "prescription"),
            "a pharmacy",
            listOf("healthcare.pharmacy"),
        ),
        Triple(
            listOf("atm", "cash", "bank"),
            "an ATM",
            listOf("service.financial.atm", "service.financial.bank"),
        ),
        Triple(
            listOf("hospital", "er", "emergency room", "urgent care"),
            "a hospital",
            listOf("healthcare.hospital", "healthcare.clinic_or_praxis"),
        ),
        Triple(
            listOf("bathroom", "restroom", "toilet"),
            "a restroom",
            listOf("service.toilet"),
        ),
        Triple(
            listOf("parking", "park the car"),
            "parking",
            listOf("parking"),
        ),
        Triple(
            listOf("hotel", "motel", "somewhere to sleep"),
            "a hotel",
            listOf("accommodation.hotel", "accommodation.motel"),
        ),
        Triple(
            listOf("car wash"),
            "a car wash",
            listOf("service.vehicle.car_wash"),
        ),
        Triple(
            listOf("convenience store", "corner store"),
            "a convenience store",
            listOf("commercial.convenience"),
        ),
    )

    /** Saved-place words handled by [com.example.memba2.ai.AiManager.setKnownPlace]. */
    private val SAVED_HINTS = listOf("home", "work", "the office", "office")

    /**
     * Returns null when [text] is not a navigation request at all, so the caller can
     * fall through to its other command handlers.
     *
     * @param savedPlaceNames names the user has previously saved, lowercased.
     */
    fun parse(text: String, savedPlaceNames: Set<String> = emptySet()): NavRequest? {
        val t = text.lowercase().replace('’','\'').trim().trimEnd('.', '!', '?')
            .replace(Regex("^(?:(?:ok|okay|please),?\\s+)+"), "")
        if (t.isBlank()) return null

        val trigger = TRIGGERS.firstOrNull { t == it || t.startsWith("$it ") } ?: return null
        var target = t.removePrefix(trigger).trim()

        // "take me home" / "navigate home" leave nothing after the trigger only when
        // the trigger itself swallowed the target, e.g. "nearest" alone is useless.
        if (target.isBlank()) return null

        LEADING_NOISE.forEach { if (target.startsWith(it)) target = target.removePrefix(it) }
        TRAILING_NOISE.forEach { if (target.endsWith(it)) target = target.removeSuffix(it) }
        target = target.trim().trimEnd(',').trim('"','“','”','\'')
        if (target.isBlank()) return null

        // 1. Saved places win — the user explicitly taught us these.
        if (target in savedPlaceNames || target in SAVED_HINTS) {
            return NavRequest.Saved(normalizeSaved(target))
        }

        // 2. Category words mean "vague", which means show a list.
        //    Match whole words so "burgerville" is not read as "burger".
        val words = target.split(' ', '-').filter { it.isNotBlank() }.toSet()
        CATEGORIES.forEach { (keys, label, cats) ->
            val hit = keys.any { key ->
                if (key.contains(' ')) target.contains(key) else key in words
            }
            if (hit) return NavRequest.Category(label, cats, target)
        }

        // 3. Anything else is a business name.
        return NavRequest.Named(target)
    }

    private fun normalizeSaved(target: String) = when (target) {
        "the office", "office" -> "work"
        else -> target
    }

    /** True if the phrase would be routed to navigation. Handy for the voice layer. */
    fun looksLikeNavigation(text: String): Boolean = parse(text) != null
}
