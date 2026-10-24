package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Dedicated Navy Blue & White Color Scheme
private val DarkColorScheme = darkColorScheme(
    primary = WhitePure,
    onPrimary = NavyDeep,
    primaryContainer = NavySurfaceVariant,
    onPrimaryContainer = WhitePure,
    secondary = NavyAccent,
    onSecondary = NavyDeep,
    secondaryContainer = NavyCard,
    onSecondaryContainer = WhiteOff,
    tertiary = NavyAccent,
    background = NavyDeep,
    onBackground = WhitePure,
    surface = NavySurface,
    onSurface = WhitePure,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextMutedDark,
    outline = NavyBorder,
    outlineVariant = NavyCard
)

private val LightColorScheme = lightColorScheme(
    primary = LightNavyPrimary,
    onPrimary = WhitePure,
    primaryContainer = LightNavySurfaceVariant,
    onPrimaryContainer = LightNavyPrimary,
    secondary = NavyBrand,
    onSecondary = WhitePure,
    secondaryContainer = LightNavySurface,
    onSecondaryContainer = LightNavyPrimary,
    tertiary = NavyAccent,
    background = LightNavyBackground,
    onBackground = TextNavyDark,
    surface = LightNavySurface,
    onSurface = TextNavyDark,
    surfaceVariant = LightNavySurfaceVariant,
    onSurfaceVariant = TextMutedLight,
    outline = LightNavyBorder,
    outlineVariant = LightNavySurfaceVariant
)

@Composable
fun SentinelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SentinelTheme(darkTheme = darkTheme, content = content)
}
