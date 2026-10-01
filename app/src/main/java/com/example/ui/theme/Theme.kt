package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SanwolfColorScheme = darkColorScheme(
    primary = SanwolfGold,
    onPrimary = Color.Black,
    primaryContainer = SanwolfGoldDim,
    onPrimaryContainer = Color.White,
    secondary = SanwolfCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D5A),
    onSecondaryContainer = Color.White,
    tertiary = SanwolfLime,
    onTertiary = Color.Black,
    background = SanwolfBlack,
    onBackground = SanwolfTextPrimary,
    surface = SanwolfPanel,
    onSurface = SanwolfTextPrimary,
    surfaceVariant = SanwolfPanelElevated,
    onSurfaceVariant = SanwolfTextSecondary,
    outline = SanwolfPanelBorder,
    error = SanwolfMagenta,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SanwolfColorScheme,
        typography = Typography,
        content = content
    )
}
