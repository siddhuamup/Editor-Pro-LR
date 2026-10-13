package com.cleanlr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SpectrumBlue,
    secondary = SpectrumBlueHover,
    background = SpectrumCanvas,
    surface = SpectrumSurface,
    onPrimary = SpectrumTextPrimary,
    onSecondary = SpectrumTextPrimary,
    onBackground = SpectrumTextPrimary,
    onSurface = SpectrumTextPrimary
)

@Composable
fun CleanLightroomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
