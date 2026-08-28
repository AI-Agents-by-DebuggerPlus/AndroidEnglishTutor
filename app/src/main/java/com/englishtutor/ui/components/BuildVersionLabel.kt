package com.englishtutor.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.englishtutor.util.AppVersion

@Composable
fun BuildVersionLabel(
    modifier: Modifier = Modifier,
    prefix: String = "Сборка",
) {
    Text(
        text = "$prefix ${AppVersion.label}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
fun BuildVersionSubtitle() {
    Text(
        text = AppVersion.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
    )
}
