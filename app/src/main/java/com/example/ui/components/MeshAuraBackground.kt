package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.model.AuraPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple Music-inspired fluid, breathing 4-color ambient mesh gradient derived dynamically
 * from the active track's embedded album cover via Palette.
 *
 * Uses a continuous frame-driven Double clock with smoothly interpolated flow speed so there
 * is NEVER a loop-boundary discontinuity or a frame jump when toggling play/pause.
 */
@Composable
fun MeshAuraBackground(
    palette: AuraPalette,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 0.78f,
    darkTheme: Boolean = true
) {
    val primaryColor by animateColorAsState(
        targetValue = Color(palette.primaryHex),
        animationSpec = tween(durationMillis = 1200),
        label = "aura_primary"
    )
    val secondaryColor by animateColorAsState(
        targetValue = Color(palette.secondaryHex),
        animationSpec = tween(durationMillis = 1200),
        label = "aura_secondary"
    )
    val deepColor by animateColorAsState(
        targetValue = Color(palette.deepHex),
        animationSpec = tween(durationMillis = 1200),
        label = "aura_deep"
    )
    val glowColor by animateColorAsState(
        targetValue = Color(palette.glowHex),
        animationSpec = tween(durationMillis = 1200),
        label = "aura_glow"
    )

    // Smoothly glide flow velocity on play/pause without ever resetting orbital phase
    val animatedSpeed by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.24f,
        animationSpec = tween(durationMillis = 900),
        label = "aura_flow_speed"
    )
    val latestSpeed by rememberUpdatedState(animatedSpeed)

    // Continuous, never-resetting Double-precision flow clock (seconds * angular scale)
    var flowTime by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            // Clamp dt to 50ms so returning from background never leaps
            val dtSec = ((nowNanos - lastFrameNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000.0
            lastFrameNanos = nowNanos
            flowTime += dtSec * latestSpeed.toDouble()
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = flowTime

        if (darkTheme) {
            // Base deep obsidian studio backdrop
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        deepColor.copy(alpha = 0.95f),
                        Color(0xFF0A0A10)
                    )
                )
            )
        } else {
            // Luminous warm-ivory studio backdrop for Light Mode
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF9F8FC),
                        Color(0xFFF1EFF7),
                        Color(0xFFEBE8F4)
                    )
                )
            )
        }

        val orbScale = if (darkTheme) 1.0f else 0.44f

        // Orb 1: Primary cover bloom — organic continuous Lissajous drift + subtle breath
        val orb1Center = Offset(
            x = w * (0.28f + 0.20f * cos(t * 0.43).toFloat()),
            y = h * (0.24f + 0.14f * sin(t * 0.34 + 0.6).toFloat())
        )
        val orb1Radius = w * (0.90f + 0.07f * sin(t * 0.27).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.64f * intensity * orbScale),
                    primaryColor.copy(alpha = 0.16f * intensity * orbScale),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = orb1Radius
            ),
            radius = orb1Radius,
            center = orb1Center
        )

        // Orb 2: Secondary counter-orbiting fluid wave
        val orb2Center = Offset(
            x = w * (0.76f + 0.18f * sin(t * 0.39 + 1.4).toFloat()),
            y = h * (0.38f + 0.16f * cos(t * 0.31 + 0.9).toFloat())
        )
        val orb2Radius = w * (0.86f + 0.08f * cos(t * 0.23 + 1.2).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.58f * intensity * orbScale),
                    secondaryColor.copy(alpha = 0.13f * intensity * orbScale),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = orb2Radius
            ),
            radius = orb2Radius,
            center = orb2Center
        )

        // Orb 3: Lower luminous highlight swell
        val orb3Center = Offset(
            x = w * (0.48f + 0.24f * cos(t * 0.29 + 3.1).toFloat()),
            y = h * (0.76f + 0.14f * sin(t * 0.37 + 2.2).toFloat())
        )
        val orb3Radius = w * (0.84f + 0.06f * sin(t * 0.19 + 2.5).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.38f * intensity * orbScale),
                    primaryColor.copy(alpha = 0.10f * intensity * orbScale),
                    Color.Transparent
                ),
                center = orb3Center,
                radius = orb3Radius
            ),
            radius = orb3Radius,
            center = orb3Center
        )

        if (darkTheme) {
            // Subtle dark scrim for effortless white text contrast
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.18f),
                        Color.Black.copy(alpha = 0.26f),
                        Color.Black.copy(alpha = 0.56f)
                    )
                )
            )
        } else {
            // Subtle luminous frost veil for crisp dark-ink legibility
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.36f)
                    )
                )
            )
        }
    }
}
