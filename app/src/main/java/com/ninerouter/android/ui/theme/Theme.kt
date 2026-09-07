package com.ninerouter.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = mdPrimary,
    onPrimary = mdOnPrimary,
    primaryContainer = mdPrimaryContainer,
    onPrimaryContainer = mdOnPrimaryContainer,
    secondary = mdSecondary,
    onSecondary = mdOnSecondary,
    background = mdBackground,
    onBackground = mdOnBackground,
    surface = mdSurface,
    onSurface = mdOnSurface,
    surfaceVariant = mdSurfaceVariant,
    onSurfaceVariant = mdOnSurfaceVariant,
    outline = mdOutline,
    error = mdError,
)

@Composable
fun NineRouterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
