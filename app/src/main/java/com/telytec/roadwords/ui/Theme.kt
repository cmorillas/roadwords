package com.telytec.roadwords.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonGreen = Color(0xFF00FF88)
val DeepSpace = Color(0xFF0B0E14)
val SurfaceGlass = Color(0xFF1A1F29)
val AccentBlue = Color(0xFF00E5FF)
val ErrorRed = Color(0xFFFF3366)

private val RoadWordsColorScheme = darkColorScheme(
    primary = NeonGreen,
    secondary = AccentBlue,
    background = DeepSpace,
    surface = SurfaceGlass,
    onPrimary = DeepSpace,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed
)

@Composable
fun RoadWordsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RoadWordsColorScheme,
        content = content
    )
}
