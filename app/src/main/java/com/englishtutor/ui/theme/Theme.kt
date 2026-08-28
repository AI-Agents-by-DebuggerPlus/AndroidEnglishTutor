package com.englishtutor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BluePrimary = Color(0xFF1565C0)
private val BlueSecondary = Color(0xFF42A5F5)

private val ColorScheme = lightColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
)

@Composable
fun EnglishTutorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
