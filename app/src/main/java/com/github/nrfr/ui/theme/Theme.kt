package com.github.nrfr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = NrfrPrimary,
    onPrimary = NrfrOnPrimary,
    primaryContainer = NrfrPrimaryContainer,
    onPrimaryContainer = NrfrOnPrimaryContainer,
    secondary = NrfrSecondary,
    onSecondary = NrfrOnSecondary,
    secondaryContainer = NrfrSecondaryContainer,
    onSecondaryContainer = NrfrOnSecondaryContainer,
    tertiary = NrfrCodeAccent,
    onTertiary = NrfrSurface2,
    tertiaryContainer = NrfrVioletSoft,
    onTertiaryContainer = NrfrCodeAccent,
    background = NrfrBackground,
    onBackground = NrfrOnSurface,
    surface = NrfrSurface2,
    onSurface = NrfrOnSurface,
    surfaceVariant = NrfrSurfaceVariant,
    onSurfaceVariant = NrfrOnSurfaceVariant,
    outline = NrfrOutline,
    outlineVariant = NrfrOutlineVariant,
    error = NrfrError,
    errorContainer = NrfrErrorContainer,
    surfaceTint = Color.Transparent
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = DarkLink,
    onTertiary = DarkBackground,
    tertiaryContainer = Color(0xFF2A2140),
    onTertiaryContainer = Color(0xFFCBB6FF),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkError,
    errorContainer = DarkErrorContainer,
    surfaceTint = Color.Transparent
)

@Composable
fun NrfrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    @Suppress("UNUSED_PARAMETER")
    val keepBinaryCompat = dynamicColor
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NrfrTypography,
        shapes = NrfrShapes,
        content = content
    )
}
