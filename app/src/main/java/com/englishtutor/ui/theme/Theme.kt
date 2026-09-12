package com.englishtutor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BluePrimary = Color(0xFF90CAF9)
private val BlueSecondary = Color(0xFF64B5F6)
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val LightText = Color(0xFFF5F5F5)
private val LightTextMuted = Color(0xFFBDBDBD)

private val ColorScheme = darkColorScheme(
    primary = BluePrimary,
    onPrimary = Color(0xFF0D47A1),
    secondary = BlueSecondary,
    onSecondary = Color(0xFF0D47A1),
    background = DarkBackground,
    onBackground = LightText,
    surface = DarkSurface,
    onSurface = LightText,
    onSurfaceVariant = LightTextMuted,
    primaryContainer = Color(0xFF1A237E),
    onPrimaryContainer = LightText,
    secondaryContainer = Color(0xFF263238),
    onSecondaryContainer = LightText,
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF4A0000),
)

@Composable
fun EnglishTutorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
