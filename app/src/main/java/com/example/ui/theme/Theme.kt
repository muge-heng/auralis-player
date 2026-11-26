package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.LyricSource

val LocalAuralisDarkTheme = staticCompositionLocalOf { true }

/**
 * Theme-adaptive signature gold accent:
 * - Dark mode: luminous ChampagneGold (#F0C28A)
 * - Light mode: deep burnished golden-amber (#A66000) with crisp readability on light surfaces
 */
@Composable
@ReadOnlyComposable
fun adaptiveGoldColor(): Color =
    if (LocalAuralisDarkTheme.current) ChampagneGold else ChampagneGoldLight

/**
 * Theme-adaptive vivid amber accent (e.g., XHIGH reasoning tier, warm warning highlights).
 */
@Composable
@ReadOnlyComposable
fun adaptiveAmberColor(): Color =
    if (LocalAuralisDarkTheme.current) AmberAccentDark else AmberAccentLight

/**
 * Theme-adaptive lyric provider chip/badge ink so light mode never renders washed-out pale yellow/pastel labels.
 */
@Composable
@ReadOnlyComposable
fun adaptiveLyricSourceColor(source: LyricSource): Color = when {
    source == LyricSource.ALL -> adaptiveGoldColor()
    !LocalAuralisDarkTheme.current && source == LyricSource.QQ_MUSIC -> Color(0xFF158A52)
    !LocalAuralisDarkTheme.current && source == LyricSource.KUGOU -> Color(0xFF0E74D1)
    !LocalAuralisDarkTheme.current && source == LyricSource.LRCLIB -> Color(0xFF6D4AD8)
    else -> Color(source.accentHex)
}

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
    secondary = ChampagneGoldLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E2C8),
    onSecondaryContainer = Color(0xFF3E2300),
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
    CompositionLocalProvider(LocalAuralisDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AuralisTypography,
            shapes = AuralisShapes,
            content = content
        )
    }
}
