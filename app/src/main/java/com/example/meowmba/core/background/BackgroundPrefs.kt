package com.example.meowmba.core.background

import com.example.meowmba.core.weather.WeatherCondition

/**
 * What is painted behind the companion.
 *
 * Kept as a (kind, value) pair rather than a sealed hierarchy because it has to round-trip
 * through two encrypted string preferences, and a pair of strings is the whole persistence
 * story. [BackgroundSelection.parse] is the only place that has to know that.
 */
enum class BackgroundKind {
    /** The flat app colour. */
    NONE,

    /** A named animation from [BackgroundAnimation]. */
    ANIMATION,

    /** A user-picked image, held as a persisted content URI. */
    IMAGE,

    /**
     * The real sky: current conditions and the current time of day, refreshed on its own.
     * Distinct from ANIMATION/`clear` because the *content* changes during the day.
     */
    LIVE_WEATHER,
}

/**
 * The animated backgrounds on offer.
 *
 * The weather ones reuse the existing scene renderer at a fixed condition — they are the
 * same code the live sky uses, pinned. That is deliberate: a separate "rain background"
 * implementation would drift away from the real rain the first time either is touched.
 */
enum class BackgroundAnimation(
    val id: String,
    val label: String,
    /** Non-null when this is the weather scene pinned to one condition. */
    val condition: WeatherCondition? = null,
    /** Room scenery with a shared tabletop at 78% of the scene height. */
    val isPetEnvironment: Boolean = false,
) {
    AURORA("aurora", "Aurora"),
    STARFIELD("starfield", "Starfield"),
    NEBULA("nebula", "Nebula"),
    EMBERS("embers", "Embers"),
    WOODLAND("woodland", "Firefly woodland"),
    LAGOON("lagoon", "Moonlit lagoon"),
    RAINY_LOFT("rainy_loft", "Rainy reading loft"),
    CLEAR_SKY("clear", "Clear sky", WeatherCondition.CLEAR),
    CLOUDS("clouds", "Clouds", WeatherCondition.CLOUDY),
    RAIN("rain", "Rain", WeatherCondition.RAIN),
    SNOW("snow", "Snow", WeatherCondition.SNOW),
    FOG("fog", "Fog", WeatherCondition.FOG),
    DUST("dust", "Dust", WeatherCondition.DUST),
    STORM("storm", "Storm", WeatherCondition.THUNDERSTORM),
    KITCHEN("kitchen", "Sunlit kitchen", isPetEnvironment = true),
    LIBRARY("library", "Candlelit library", isPetEnvironment = true),
    GREENHOUSE("greenhouse", "Glass greenhouse", isPetEnvironment = true),
    WORKSHOP("workshop", "Clockwork workshop", isPetEnvironment = true),
    POND("pond", "Lotus pond", isPetEnvironment = true),
    ORCHARD("orchard", "Peach orchard", isPetEnvironment = true),
    CAFE("cafe", "Rainy cafe", isPetEnvironment = true),
    OBSERVATORY("observatory", "Star observatory", isPetEnvironment = true),
    BEACH("beach", "Seaside deck", isPetEnvironment = true),
    BEDROOM("bedroom", "Cozy bedroom", isPetEnvironment = true),
    CARTOON_NOOK("cartoon_nook", "Cartoon sky nook", isPetEnvironment = true),
    CARTOON_GALLEY("cartoon_galley", "Cartoon space galley", isPetEnvironment = true),
    CARTOON_SNACK_LAB("cartoon_snack_lab", "Cartoon snack lab", isPetEnvironment = true),
    CARTOON_LAUNDROMAT("cartoon_laundromat", "Cosmic laundry", isPetEnvironment = true),
    CARTOON_DINER("cartoon_diner", "Oddball diner", isPetEnvironment = true);

    /** Picker grouping is independent of the stable IDs already stored on the phone. */
    val isCartoonEnvironment: Boolean
        get() = when (this) {
            CARTOON_NOOK, CARTOON_GALLEY, CARTOON_SNACK_LAB, CARTOON_LAUNDROMAT, CARTOON_DINER -> true
            else -> false
        }

    companion object {
        fun byId(id: String?): BackgroundAnimation? = entries.firstOrNull { it.id == id }
    }
}

data class BackgroundSelection(
    val kind: BackgroundKind = BackgroundKind.NONE,
    /** Animation id for [BackgroundKind.ANIMATION], content URI for [BackgroundKind.IMAGE]. */
    val value: String = "",
    /** 0..100 black scrim over the background, so the companion stays readable on top. */
    val dimPercent: Int = 35,
) {
    val animation: BackgroundAnimation?
        get() = if (kind == BackgroundKind.ANIMATION) BackgroundAnimation.byId(value) else null

    companion object {
        /**
         * Rebuilds a selection from stored strings, falling back to [NONE] on anything
         * unrecognised — a background that fails to load must not be able to blank the app.
         */
        fun parse(kind: String?, value: String?, dim: String?): BackgroundSelection {
            val parsedKind = BackgroundKind.entries.firstOrNull { it.name == kind }
                ?: BackgroundKind.NONE
            // An ANIMATION whose id no longer exists (renamed, removed) is not an animation.
            if (parsedKind == BackgroundKind.ANIMATION &&
                BackgroundAnimation.byId(value) == null
            ) {
                return BackgroundSelection()
            }
            if (parsedKind == BackgroundKind.IMAGE && value.isNullOrBlank()) {
                return BackgroundSelection()
            }
            return BackgroundSelection(
                kind = parsedKind,
                value = value.orEmpty(),
                dimPercent = dim?.toIntOrNull()?.coerceIn(0, 100) ?: 35,
            )
        }
    }
}
