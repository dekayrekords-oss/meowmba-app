package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.weather.MoonPhase
import com.example.meowmba.core.weather.TimeOfDay
import com.example.meowmba.core.weather.WeatherCondition
import com.example.meowmba.core.weather.WeatherNow

/**
 * The Weather page in the ecosystem grid.
 *
 * The weather *card* is a glance — it appears when the companion talks about the sky and it
 * goes away again. Anything you configure needs somewhere that stays put, which is here:
 * the current reading, the moon, the ambient switch, and the controls for looking at a sky
 * that is not the one outside right now.
 */
@Composable
fun WeatherScreen(
    weather: WeatherNow?,
    ambientEnabled: Boolean,
    onAmbientChange: (Boolean) -> Unit,
    previewTimeOfDay: TimeOfDay?,
    onPreviewTimeOfDay: (TimeOfDay?) -> Unit,
    onCycleCondition: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val shown = weather ?: PLACEHOLDER
    val clockTimeOfDay = rememberTimeOfDay(shown.isDay)
    val effectiveTimeOfDay = previewTimeOfDay ?: clockTimeOfDay
    val moon = rememberMoonPhase()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Weather",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF58A6FF))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }

            // Live scene strip: the same renderer the companion screen uses, at full
            // strength. Tapping it steps the condition, which is how you audit an animation
            // without waiting for that weather to happen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0B0F14))
                    .clickable { onCycleCondition() },
            ) {
                WeatherScene(
                    condition = shown.condition,
                    isDay = effectiveTimeOfDay.isLight,
                    timeOfDay = effectiveTimeOfDay,
                    moonPhase = moon,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(modifier = Modifier.align(Alignment.CenterStart).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "${shown.temperatureF}",
                            color = Color.White,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Light,
                        )
                        Text("°", color = Color.White, fontSize = 22.sp)
                    }
                    Text(
                        "${shown.condition.emoji}  ${shown.condition.label}",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                    shown.place?.let {
                        Text(it, color = Color(0xFFB9C4CE), fontSize = 12.sp)
                    }
                }
                Text(
                    text = "tap to preview conditions",
                    color = Color(0x99FFFFFF),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("High", "${shown.highF}°")
                Stat("Low", "${shown.lowF}°")
                Stat("Feels", "${shown.feelsLikeF}°")
                Stat("Humidity", "${shown.humidityPct}%")
                Stat("Wind", "${shown.windMph} mph")
            }

            if (weather == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "No reading yet — tap refresh. Needs location access and a network.",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Tonight")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161B22))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(MoonPhase.emoji(moon), fontSize = 30.sp)
                Spacer(Modifier.padding(horizontal = 8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(MoonPhase.label(moon), color = Color.White, fontSize = 15.sp)
                    Text(
                        "${(MoonPhase.illumination(moon) * 100).toInt()}% lit · " +
                            "day ${(moon * 29.53f).toInt()} of 29",
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Settings")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161B22))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Always show weather",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Keeps the animation playing all the time, matching the real " +
                            "conditions and the time of day. Refreshes every 15 minutes.",
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = ambientEnabled,
                    onCheckedChange = onAmbientChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF2F81F7),
                    ),
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Preview")
            Text(
                "Look at this weather at another hour. Auto follows the real clock.",
                color = Color(0xFF6E7681),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeChip("Auto", previewTimeOfDay == null) { onPreviewTimeOfDay(null) }
                TimeOfDay.entries.forEach { tod ->
                    TimeChip(
                        label = tod.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = previewTimeOfDay == tod,
                    ) { onPreviewTimeOfDay(tod) }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFF8B949E),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(label.uppercase(), color = Color(0xFF8B949E), fontSize = 9.sp)
    }
}

@Composable
private fun TimeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else Color(0xFF8B949E),
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF1F6FEB) else Color(0xFF161B22))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF58A6FF) else Color(0xFF30363D),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Shown before the first fetch lands, so the page is never an empty rectangle. */
private val PLACEHOLDER = WeatherNow(
    condition = WeatherCondition.CLEAR,
    temperatureF = 72, feelsLikeF = 72, highF = 84, lowF = 61,
    humidityPct = 38, windMph = 7, isDay = true, place = null,
)
