package com.example.meowmba.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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
    secondaryContainer = Color(0xFF443C30), onSecondaryContainer = Color(0xFFF2DFC1),
    tertiary = Color(0xFFB2C7A3), onTertiary = Color(0xFF23341D),
    tertiaryContainer = Color(0xFF35472D), onTertiaryContainer = Color(0xFFD2E6C3),
    background = Color(0xFF101719), onBackground = Color(0xFFE3E9E7),
    surface = Color(0xFF151E20), onSurface = Color(0xFFE3E9E7),
    surfaceContainerLowest = Color(0xFF0C1214), surfaceContainerLow = Color(0xFF151E20),
    surfaceContainer = Color(0xFF1A2426), surfaceContainerHigh = Color(0xFF233033),
    surfaceContainerHighest = Color(0xFF2D3C3E),
    surfaceVariant = Color(0xFF233033), onSurfaceVariant = Color(0xFFB8C8C7),
    outline = Color(0xFF849795), outlineVariant = Color(0xFF374A4C),
)
private val ProfessionalLight = lightColorScheme(
    primary = Color(0xFF205D60), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEDE9), onPrimaryContainer = Color(0xFF163F41),
    secondary = Color(0xFF705E43), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E1CB), onSecondaryContainer = Color(0xFF302719),
    tertiary = Color(0xFF4B6741), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDFECD4), onTertiaryContainer = Color(0xFF203319),
    background = Color(0xFFF8F6F2), onBackground = Color(0xFF202A2B),
    surface = Color(0xFFFFFDFA), onSurface = Color(0xFF202A2B),
    surfaceContainerLowest = Color(0xFFFFFDFA), surfaceContainerLow = Color(0xFFF5F3EE),
    surfaceContainer = Color(0xFFEFEFE8), surfaceContainerHigh = Color(0xFFE7EBE5),
    surfaceContainerHighest = Color(0xFFDFE5DF),
    surfaceVariant = Color(0xFFECEEE9), onSurfaceVariant = Color(0xFF4F6263),
    outline = Color(0xFF738485), outlineVariant = Color(0xFFD4DDD8),
)

// Companion keeps its established green identity in both appearances. Container/on-
// container pairs are explicit so controls no longer inherit unrelated purple defaults.
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF72D486), onPrimary = Color(0xFF073A18),
    primaryContainer = Color(0xFF17472A), onPrimaryContainer = Color(0xFFB8F4C3),
    secondary = Color(0xFFB6CBB9), onSecondary = Color(0xFF213526),
    secondaryContainer = Color(0xFF344B3A), onSecondaryContainer = Color(0xFFD4E8D6),
    tertiary = Color(0xFFE0C48D), onTertiary = Color(0xFF3D2E0D),
    tertiaryContainer = Color(0xFF574323), onTertiaryContainer = Color(0xFFF7E0B4),
    background = Color(0xFF101713), onBackground = Color(0xFFE3EBE4),
    surface = Color(0xFF17201B), onSurface = Color(0xFFE3EBE4),
    surfaceContainerLowest = Color(0xFF0C120F), surfaceContainerLow = Color(0xFF17201B),
    surfaceContainer = Color(0xFF1D2922), surfaceContainerHigh = Color(0xFF26342B),
    surfaceContainerHighest = Color(0xFF304136),
    surfaceVariant = Color(0xFF29372E), onSurfaceVariant = Color(0xFFBCCCBF),
    outline = Color(0xFF8B9C8F), outlineVariant = Color(0xFF3D4D42),
    inverseSurface = Color(0xFFE0E9E1), inverseOnSurface = Color(0xFF27352B),
    inversePrimary = Color(0xFF27673B), surfaceTint = Color(0xFF72D486),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF27673B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD1EED6), onPrimaryContainer = Color(0xFF123D21),
    secondary = Color(0xFF4E6755), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEEBDF), onSecondaryContainer = Color(0xFF213526),
    tertiary = Color(0xFF785C29), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E3BF), onTertiaryContainer = Color(0xFF3D2E0D),
    background = Color(0xFFF5F8F2), onBackground = Color(0xFF1D2921),
    surface = Color(0xFFFDFEF9), onSurface = Color(0xFF1D2921),
    surfaceContainerLowest = Color(0xFFFDFEF9), surfaceContainerLow = Color(0xFFF0F5EC),
    surfaceContainer = Color(0xFFEAF1E6), surfaceContainerHigh = Color(0xFFE3EBDE),
    surfaceContainerHighest = Color(0xFFDAE5D6),
    surfaceVariant = Color(0xFFE3EBE0), onSurfaceVariant = Color(0xFF4C5E50),
    outline = Color(0xFF718674), outlineVariant = Color(0xFFC9D6C8),
    inverseSurface = Color(0xFF29372E), inverseOnSurface = Color(0xFFE9F0E7),
    inversePrimary = Color(0xFF8BDD9C), surfaceTint = Color(0xFF27673B),
)

private val WorkbenchShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
        shapes = WorkbenchShapes,
        typography = if (professional) Typography.copy(
            headlineLarge = Typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
        ) else Typography,
        content = content
    )
}
