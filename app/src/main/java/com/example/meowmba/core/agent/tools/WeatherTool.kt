package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.weather.WeatherRepository

/**
 * `weather.get` — current conditions where the user is.
 *
 * SAFE: a read-only lookup against a keyless public API.
 *
 * Fetching also publishes to [WeatherRepository.latest], which the UI observes, so calling
 * this tool makes the weather card appear as a side effect. That is deliberate — the
 * companion should show the card whenever it talks about the weather, without the model
 * having to know a card exists or ask for one.
 */
class WeatherTool(private val repository: WeatherRepository) : Tool {

    override val definition = ToolDefinition(
        id = "weather.get",
        displayName = "Check the weather",
        description = "Current weather where the user is: temperature, what it feels like, " +
            "conditions, today's high and low, humidity and wind. Use for any question " +
            "about the weather, temperature, or whether to take a coat. Showing the user a " +
            "weather card happens automatically.",
        inputSchema = emptyMap(),
        requiredPermissions = setOf("android.permission.ACCESS_FINE_LOCATION"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 15_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        when (val r = repository.fetch()) {
            is WeatherRepository.Result.Ok -> {
                val w = r.weather
                ToolResult.Success(
                    buildString {
                        append("${w.condition.label}, ${w.temperatureF}°F")
                        append(" (feels like ${w.feelsLikeF}°F)")
                        append(". High ${w.highF}°, low ${w.lowF}°")
                        append(". Humidity ${w.humidityPct}%, wind ${w.windMph} mph")
                        w.place?.let { append(", in $it") }
                        append(".")
                    }
                )
            }
            is WeatherRepository.Result.Failed -> ToolResult.Failure(r.reason)
        }
}
