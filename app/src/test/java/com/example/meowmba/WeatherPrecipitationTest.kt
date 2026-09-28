package com.example.meowmba

import com.example.meowmba.core.weather.WeatherCondition
import com.example.meowmba.ui.desk.DeskSky
import com.example.meowmba.ui.desk.DeskWeather
import org.junit.Assert.assertEquals
import org.junit.Test

/** Owner report 2026-09-28: it was raining; Meow (and the Desk windows) said cloudy. */
class WeatherPrecipitationTest {
    private fun c(code: WeatherCondition, rain: Double = 0.0, snow: Double = 0.0, total: Double = rain) =
        WeatherCondition.withPrecipitation(code, rain, snow, total)

    @Test fun measuredRainOutranksADryCode() {
        assertEquals(WeatherCondition.RAIN, c(WeatherCondition.CLOUDY, rain = 1.2))
        assertEquals(WeatherCondition.DRIZZLE, c(WeatherCondition.CLOUDY, rain = 0.2))
        assertEquals(WeatherCondition.DRIZZLE, c(WeatherCondition.PARTLY_CLOUDY, total = 0.3))
        assertEquals(WeatherCondition.SNOW, c(WeatherCondition.CLOUDY, snow = 0.4))
    }

    @Test fun dryOrAlreadyWetCodesAreKept() {
        assertEquals(WeatherCondition.CLOUDY, c(WeatherCondition.CLOUDY))
        assertEquals(WeatherCondition.CLOUDY, c(WeatherCondition.CLOUDY, rain = 0.05))
        assertEquals(WeatherCondition.THUNDERSTORM, c(WeatherCondition.THUNDERSTORM, rain = 3.0))
        assertEquals(WeatherCondition.SNOW, c(WeatherCondition.SNOW, rain = 1.0))
    }

    @Test fun theDeskShowsRainForTheWeatherLine() {
        // The Desk reads the same "69° rain" line the app builds from the condition label.
        assertEquals(DeskWeather.RAIN, DeskSky.weather("69° " + WeatherCondition.RAIN.label.lowercase()))
        assertEquals(DeskWeather.RAIN, DeskSky.weather("69° " + WeatherCondition.DRIZZLE.label.lowercase()))
    }
}
