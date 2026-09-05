package com.example.meowmba.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val ProfessionalDark = darkColorScheme(
    primary = Color(0xFF8AD2CB), onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF174F4D), onPrimaryContainer = Color(0xFFC1F2EB),
    secondary = Color(0xFFD9C7AC), onSecondary = Color(0xFF302A20),
    background = Color(0xFF101719), onBackground = Color(0xFFE3E9E7),
    surface = Color(0xFF151E20), onSurface = Color(0xFFE3E9E7),
    surfaceVariant = Color(0xFF233033), onSurfaceVariant = Color(0xFFB8C8C7),
    outline = Color(0xFF849795), outlineVariant = Color(0xFF374A4C),
)
private val ProfessionalLight = lightColorScheme(
    primary = Color(0xFF205D60), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEDE9), onPrimaryContainer = Color(0xFF163F41),
    secondary = Color(0xFF705E43), onSecondary = Color.White,
    background = Color(0xFFF8F6F2), onBackground = Color(0xFF202A2B),
    surface = Color(0xFFFFFDFA), onSurface = Color(0xFF202A2B),
    surfaceVariant = Color(0xFFECEEE9), onSurfaceVariant = Color(0xFF4F6263),
    outline = Color(0xFF738485), outlineVariant = Color(0xFFD4DDD8),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3FB950),
    onPrimary = Color(0xFF05210B),
    primaryContainer = Color(0xFF14251C),
    background = Color(0xFF0D1117), onBackground = Color.White,
    surface = Color(0xFF161B22), onSurface = Color.White,
    surfaceVariant = Color(0xFF21262D), onSurfaceVariant = Color(0xFFABB6C3),
    outlineVariant = Color(0xFF30363D),
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun MeowmbaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    professional: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        professional -> if (darkTheme) ProfessionalDark else ProfessionalLight
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (professional) Typography.copy(
            headlineLarge = Typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
        ) else Typography,
        content = content
    )
}
