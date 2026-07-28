package com.hemanth.vaani.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VaaniDarkScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBackground,
    secondary = TerminalGreenDim,
    background = TerminalBackground,
    surface = TerminalSurface,
    surfaceVariant = TerminalSurfaceVariant,
    onBackground = TerminalTextPrimary,
    onSurface = TerminalTextPrimary,
    onSurfaceVariant = TerminalTextSecondary,
    error = TerminalRed,
    tertiary = TerminalAmber
)

@Composable
fun VaaniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VaaniDarkScheme, // always dark -- this app is terminal-styled by design
        typography = VaaniTypography,
        content = content
    )
}
