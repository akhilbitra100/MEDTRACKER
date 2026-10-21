package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MedTrackerLightColorScheme = lightColorScheme(
    primary = MedicalBlue,
    onPrimary = Color.White,
    primaryContainer = MedicalBlueLight,
    onPrimaryContainer = MedicalBlueDark,

    secondary = HealthTeal,
    onSecondary = Color.White,
    secondaryContainer = HealthTealLight,
    onSecondaryContainer = HealthTealDark,

    tertiary = AccentMint,
    onTertiary = Color.White,
    tertiaryContainer = AccentMintLight,
    onTertiaryContainer = Color(0xFF00201B),

    background = BackgroundWhite,
    onBackground = TextPrimaryDark,

    surface = SurfaceWhite,
    onSurface = TextPrimaryDark,

    surfaceVariant = SurfaceVariantClean,
    onSurfaceVariant = TextSecondaryMuted,

    outline = OutlineBorder,
    outlineVariant = DividerColor,

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val MedTrackerDarkColorScheme = darkColorScheme(
    primary = DarkMedicalBlue,
    onPrimary = Color(0xFF003549),
    primaryContainer = DarkMedicalBlueContainer,
    onPrimaryContainer = Color(0xFFC2E8FF),

    secondary = DarkHealthTeal,
    onSecondary = Color(0xFF003732),
    secondaryContainer = DarkHealthTealContainer,
    onSecondaryContainer = Color(0xFFB2DFDB),

    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF00332C),
    tertiaryContainer = Color(0xFF004D40),
    onTertiaryContainer = Color(0xFFE0F2F1),

    background = DarkBackground,
    onBackground = DarkTextPrimary,

    surface = DarkSurface,
    onSurface = DarkTextPrimary,

    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,

    outline = DarkOutline,
    outlineVariant = Color(0xFF334155),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MedTrackerTheme(
    themeMode: String = "LIGHT",
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Light mode only: consistent light backgrounds, readable text, and accessible contrast throughout
    MaterialTheme(
        colorScheme = MedTrackerLightColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun DoseBuddyTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) = MedTrackerTheme(themeMode = "LIGHT", content = content)

