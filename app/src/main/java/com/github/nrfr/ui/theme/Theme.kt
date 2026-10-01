package com.github.nrfr.ui.theme

import android.app.Activity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NrfrShapes
    ) {
        CompositionLocalProvider(LocalIndication provides NoIndication) {
            content()
        }
    }
}
