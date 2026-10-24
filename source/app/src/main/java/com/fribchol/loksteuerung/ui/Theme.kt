package com.fribchol.loksteuerung.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Farbpalette: dunkler Führerstand mit Messing-Akzenten. */
object LokColors {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF171B22)
    val SurfaceHigh = Color(0xFF222935)
    val Outline = Color(0xFF2E3644)
    val Brass = Color(0xFFF2A93B)
    val OnBrass = Color(0xFF1C1405)
    val Forward = Color(0xFF3DDC97)
    val Backward = Color(0xFFFF8A3D)
    val Stop = Color(0xFFE5484D)
    val LightOn = Color(0xFFFFD866)
    val Text = Color(0xFFE8EAED)
    val Muted = Color(0xFF8B93A1)
    val Knob = Color(0xFFF4F5F7)
    val KnobText = Color(0xFF14181F)
}

private val LokColorScheme = darkColorScheme(
    primary = LokColors.Brass,
    onPrimary = LokColors.OnBrass,
    secondary = LokColors.Forward,
    background = LokColors.Background,
    onBackground = LokColors.Text,
    surface = LokColors.Surface,
    onSurface = LokColors.Text,
    surfaceVariant = LokColors.SurfaceHigh,
    onSurfaceVariant = LokColors.Muted,
    surfaceContainerHigh = LokColors.SurfaceHigh,
    error = LokColors.Stop,
    outline = LokColors.Outline,
)

@Composable
fun LokTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LokColorScheme, content = content)
}
