package com.example.meowmba.ui.companion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.weather.WeatherCondition
import com.example.meowmba.core.weather.WeatherNow

/**
 * The weather card.
 *
 * Colour comes from the condition, not a fixed palette, so a stormy day reads as stormy at
 * a glance before any text is parsed. The big emoji breathes gently — enough to feel alive,
 * slow enough not to pull attention off the companion itself.
 */
@Composable
fun WeatherCard(
    weather: WeatherNow,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = paletteFor(weather.condition, weather.isDay)

    // 4s cycle: a slow breath. Anything faster competes with the avatar for attention.
    val pulse by rememberInfiniteTransition(label = "weather-pulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Translucent, not opaque: the animated weather behind it should read THROUGH
            // the card. The condition's hue survives as a tint so a storm still looks like a
            // storm, and a thin border keeps the edge legible against a busy scene.
            .background(
                brush = Brush.verticalGradient(palette.gradient.map { it.copy(alpha = CARD_ALPHA) }),
                shape = RoundedCornerShape(24.dp),
            )
            .border(
                width = 1.dp,
                color = palette.onSurface.copy(alpha = 0.18f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = weather.condition.emoji,
                    fontSize = (44 * pulse).sp,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${weather.temperatureF}",
                            color = palette.onSurface,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            text = "°",
                            color = palette.onSurface,
                            fontSize = 28.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        text = weather.condition.label,
                        color = palette.onSurface,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("✕", color = palette.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("High", "${weather.highF}°", palette.onSurface)
                Stat("Low", "${weather.lowF}°", palette.onSurface)
                // Only worth the space when it disagrees with the actual temperature.
                if (kotlin.math.abs(weather.feelsLikeF - weather.temperatureF) >= 3) {
                    Stat("Feels", "${weather.feelsLikeF}°", palette.onSurface)
                }
                Stat("Humidity", "${weather.humidityPct}%", palette.onSurface)
                Stat("Wind", "${weather.windMph}", palette.onSurface)
            }

            weather.place?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    color = palette.onSurface.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    modifier = Modifier.alpha(0.9f),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, onSurface: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(
            label.uppercase(),
            color = onSurface.copy(alpha = 0.6f),
            fontSize = 9.sp,
        )
    }
}

/**
 * Card translucency. High enough that white text stays readable over rain and lightning,
 * low enough that the scene is clearly visible through it.
 */
private const val CARD_ALPHA = 0.55f

private data class WeatherPalette(
    val gradient: List<Color>,
    val onSurface: Color,
)

/**
 * Night is a separate case rather than a darkening filter: a clear night should feel
 * different from an overcast day, and simply dimming the sunny palette makes both look
 * muddy.
 */
private fun paletteFor(condition: WeatherCondition, isDay: Boolean): WeatherPalette {
    if (!isDay && (condition == WeatherCondition.CLEAR ||
            condition == WeatherCondition.PARTLY_CLOUDY)
    ) {
        return WeatherPalette(
            gradient = listOf(Color(0xFF1B2A4A), Color(0xFF0D1424)),
            onSurface = Color(0xFFE8ECF5),
        )
    }
    return when (condition) {
        WeatherCondition.CLEAR -> WeatherPalette(
            listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)), Color(0xFFF8FAFF)
        )
        WeatherCondition.PARTLY_CLOUDY -> WeatherPalette(
            listOf(Color(0xFF60A5FA), Color(0xFF3B6FA8)), Color(0xFFF8FAFF)
        )
        WeatherCondition.CLOUDY, WeatherCondition.FOG -> WeatherPalette(
            listOf(Color(0xFF64748B), Color(0xFF3F4A5A)), Color(0xFFF1F5F9)
        )
        WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> WeatherPalette(
            listOf(Color(0xFF3E5C76), Color(0xFF1F3247)), Color(0xFFE6F0F8)
        )
        WeatherCondition.SNOW -> WeatherPalette(
            listOf(Color(0xFFA5C7DE), Color(0xFF6B8CA6)), Color(0xFF14202B)
        )
        WeatherCondition.THUNDERSTORM -> WeatherPalette(
            listOf(Color(0xFF4C3B6E), Color(0xFF241B38)), Color(0xFFEDE6FA)
        )
        WeatherCondition.DUST -> WeatherPalette(
            listOf(Color(0xFF8A6A3B), Color(0xFF4A3720)), Color(0xFFFBF3E4)
        )
        WeatherCondition.UNKNOWN -> WeatherPalette(
            listOf(Color(0xFF475569), Color(0xFF29323F)), Color(0xFFF1F5F9)
        )
    }
}
