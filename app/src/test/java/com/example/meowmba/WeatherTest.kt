package com.example.meowmba

import com.example.meowmba.core.weather.WeatherCondition
import com.example.meowmba.core.weather.WeatherNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WMO code mapping and the derived presentation.
 *
 * A wrong bucket is silently wrong: the card shows a sunny gradient during a thunderstorm
 * and the cat looks pleased about it. Cheap to pin, so pinned.
 */
class WeatherTest {

    @Test
    fun `clear sky`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmoCode(0))
    }

    @Test
    fun `cloud gradations`() {
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromWmoCode(1))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromWmoCode(2))
        assertEquals(WeatherCondition.CLOUDY, WeatherCondition.fromWmoCode(3))
    }

    @Test
    fun `fog codes`() {
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmoCode(45))
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmoCode(48))
    }

    @Test
    fun `drizzle is distinct from rain`() {
        // Different card colour and a different cat mood, so they must not collapse.
        listOf(51, 53, 55, 56, 57).forEach {
            assertEquals("code $it", WeatherCondition.DRIZZLE, WeatherCondition.fromWmoCode(it))
        }
        assertNotEquals(WeatherCondition.DRIZZLE, WeatherCondition.fromWmoCode(61))
    }

    @Test
    fun `rain includes showers and freezing rain`() {
        listOf(61, 63, 65, 66, 67, 80, 81, 82).forEach {
            assertEquals("code $it", WeatherCondition.RAIN, WeatherCondition.fromWmoCode(it))
        }
    }

    @Test
    fun `snow includes grains and snow showers`() {
        listOf(71, 73, 75, 77, 85, 86).forEach {
            assertEquals("code $it", WeatherCondition.SNOW, WeatherCondition.fromWmoCode(it))
        }
    }

    @Test
    fun `thunderstorm includes hail variants`() {
        listOf(95, 96, 99).forEach {
            assertEquals("code $it", WeatherCondition.THUNDERSTORM, WeatherCondition.fromWmoCode(it))
        }
    }

    @Test
    fun `unrecognised codes do not masquerade as clear`() {
        // Defaulting to CLEAR would show sunshine for data we did not understand.
        listOf(-1, 4, 30, 100, 999).forEach {
            assertEquals("code $it", WeatherCondition.UNKNOWN, WeatherCondition.fromWmoCode(it))
        }
    }

    // ── Presentation ─────────────────────────────────────────────────────────────

    private fun weather(
        condition: WeatherCondition,
        temp: Int = 70,
        feels: Int = 70,
        isDay: Boolean = true,
    ) = WeatherNow(
        condition = condition, temperatureF = temp, feelsLikeF = feels,
        highF = 80, lowF = 60, humidityPct = 30, windMph = 5, isDay = isDay,
    )

    @Test
    fun `spoken line mentions feels-like only when it differs meaningfully`() {
        assertTrue(weather(WeatherCondition.CLEAR, 99, 110).spoken().contains("feels like"))
        // A 1-degree difference is noise; saying it aloud is just clutter.
        assertTrue(!weather(WeatherCondition.CLEAR, 99, 100).spoken().contains("feels like"))
    }

    @Test
    fun `spoken line carries temperature and condition`() {
        val s = weather(WeatherCondition.RAIN, 55).spoken()
        assertTrue(s, s.contains("55"))
        assertTrue(s, s.contains("rain"))
        assertTrue(s, s.contains("High of 80"))
    }

    @Test
    fun `cat reacts differently to sun, storm and night`() {
        assertEquals("HAPPY", weather(WeatherCondition.CLEAR, isDay = true).catEmotionName())
        assertEquals("SLEEPY", weather(WeatherCondition.CLEAR, isDay = false).catEmotionName())
        assertEquals("ALERT", weather(WeatherCondition.THUNDERSTORM).catEmotionName())
        assertEquals("SLEEPY", weather(WeatherCondition.RAIN).catEmotionName())
        assertEquals("CONFUSED", weather(WeatherCondition.UNKNOWN).catEmotionName())
    }

    @Test
    fun `every condition maps to a real CompanionEmotion`() {
        // catEmotionName() returns a String, so a typo would silently fall back at runtime.
        val valid = com.example.meowmba.ui.companion.CompanionEmotion.entries.map { it.name }
        WeatherCondition.entries.forEach { c ->
            val name = weather(c).catEmotionName()
            assertTrue("$c -> $name is not a CompanionEmotion", name in valid)
        }
    }

    @Test
    fun `every condition has an emoji and a label`() {
        WeatherCondition.entries.forEach {
            assertTrue(it.name, it.emoji.isNotBlank())
            assertTrue(it.name, it.label.isNotBlank())
        }
    }
}
