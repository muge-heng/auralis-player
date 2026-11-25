package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AuralisDarkColorScheme = darkColorScheme(
    primary = RubyAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3E121B),
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = ChampagneGold,
    onSecondary = Color(0xFF281800),
    secondaryContainer = Color(0xFF362817),
    onSecondaryContainer = Color(0xFFFFE5C4),
    tertiary = AuraViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF251E4A),
    onTertiaryContainer = Color(0xFFE3DCFF),
    background = ObsidianVoid,
    onBackground = InkPrimary,
    surface = ObsidianSurface,
    onSurface = InkPrimary,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianElevated,
    surfaceContainerHigh = ObsidianCard,
    surfaceContainerHighest = Color(0xFF262636),
    outline = Color(0xFF8C8CA6),
    outlineVariant = Color(0xFF28283A),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2D0005)
)

private val AuralisLightColorScheme = lightColorScheme(
    primary = RubyAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E8),
    onPrimaryContainer = Color(0xFF5A0013),
    secondary = Color(0xFF9A6830),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD6),
    onSecondaryContainer = Color(0xFF3A2200),
    tertiary = Color(0xFF5A42D4),
    onTertiary = Color.White,
    background = AlabasterCanvas,
    onBackground = LightInkPrimary,
    surface = AlabasterSurface,
    onSurface = LightInkPrimary,
    surfaceVariant = AlabasterElevated,
    onSurfaceVariant = LightInkSecondary,
    surfaceContainerLowest = AlabasterSurface,
    surfaceContainerLow = AlabasterCanvas,
    surfaceContainer = AlabasterElevated,
    surfaceContainerHigh = Color(0xFFE6E6F0),
    surfaceContainerHighest = Color(0xFFE6E6F2),
    outline = Color(0xFF727286),
    outlineVariant = Color(0xFFD8D8E6)
)

val AuralisShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AuralisTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AuralisDarkColorScheme else AuralisLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuralisTypography,
        shapes = AuralisShapes,
        content = content
    )
}
