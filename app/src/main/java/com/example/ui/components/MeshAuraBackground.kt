package com.example.ui.components

import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.ui.graphics.ShaderBrush
import com.example.model.AuraPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple Music-inspired fluid, breathing 4-color ambient mesh gradient derived dynamically
 * from the active track's embedded album cover via Palette.
 *
 * Uses a continuous frame-driven Double clock with smoothly interpolated flow speed so there
 * is NEVER a loop-boundary discontinuity or a frame jump when toggling play/pause.
 *
 * When [enhanced] is true (Visual Effects Enhancement mode):
 * - On Android 13+ (API 33+): executes a real-time GPU AGSL domain-warped fluid silk aurora shader
 *   with harmonic ribbons and anti-banding micro-dithering.
 * - On older Android versions: renders a richer 5-orb harmonic aurora canvas with silk sheen waves.
 */
@Composable
fun MeshAuraBackground(
    palette: AuraPalette,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 0.78f,
    darkTheme: Boolean = true,
    enhanced: Boolean = false
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
        targetValue = if (isPlaying) {
            if (enhanced) 1.25f else 1.0f
        } else {
            0.24f
        },
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

    val auroraShader = remember(enhanced) {
        if (enhanced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(FLUID_AURORA_AGSL) }.getOrNull()
        } else {
            null
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = flowTime

        if (enhanced && auroraShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && w > 8f && h > 8f) {
            val renderedShader = runCatching {
                auroraShader.setFloatUniform("resolution", w, h)
                auroraShader.setFloatUniform("time", (t % 628.3185).toFloat())
                auroraShader.setFloatUniform("intensity", intensity.coerceIn(0.2f, 1.2f))
                auroraShader.setFloatUniform("isDark", if (darkTheme) 1f else 0f)
                auroraShader.setFloatUniform("cPrimary", primaryColor.red, primaryColor.green, primaryColor.blue)
                auroraShader.setFloatUniform("cSecondary", secondaryColor.red, secondaryColor.green, secondaryColor.blue)
                auroraShader.setFloatUniform("cDeep", deepColor.red, deepColor.green, deepColor.blue)
                auroraShader.setFloatUniform("cGlow", glowColor.red, glowColor.green, glowColor.blue)
                drawRect(brush = ShaderBrush(auroraShader))
                true
            }.getOrDefault(false)

            if (renderedShader) {
                return@Canvas
            }
        }

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

        val orbScale = if (darkTheme) {
            if (enhanced) 1.15f else 1.0f
        } else {
            if (enhanced) 0.58f else 0.44f
        }

        // Orb 1: Primary cover bloom — organic continuous Lissajous drift + subtle breath
        val orb1Center = Offset(
            x = w * (0.28f + 0.20f * cos(t * 0.43).toFloat()),
            y = h * (0.24f + 0.14f * sin(t * 0.34 + 0.6).toFloat())
        )
        val orb1Radius = w * (0.90f + 0.07f * sin(t * 0.27).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = (0.64f * intensity * orbScale).coerceAtMost(0.92f)),
                    primaryColor.copy(alpha = (0.16f * intensity * orbScale).coerceAtMost(0.45f)),
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
                    secondaryColor.copy(alpha = (0.58f * intensity * orbScale).coerceAtMost(0.88f)),
                    secondaryColor.copy(alpha = (0.13f * intensity * orbScale).coerceAtMost(0.40f)),
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
                    glowColor.copy(alpha = (0.38f * intensity * orbScale).coerceAtMost(0.75f)),
                    primaryColor.copy(alpha = (0.10f * intensity * orbScale).coerceAtMost(0.35f)),
                    Color.Transparent
                ),
                center = orb3Center,
                radius = orb3Radius
            ),
            radius = orb3Radius,
            center = orb3Center
        )

        if (enhanced) {
            // Enhanced fallback Orbs 4 & 5: Harmonic silk sheen & mid-stage counter-swell
            val orb4Center = Offset(
                x = w * (0.62f + 0.26f * sin(t * 0.51 + 0.4).toFloat()),
                y = h * (0.18f + 0.18f * cos(t * 0.41 + 1.8).toFloat())
            )
            val orb4Radius = w * (0.74f + 0.10f * sin(t * 0.33).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = (0.34f * intensity * orbScale).coerceAtMost(0.65f)),
                        secondaryColor.copy(alpha = (0.12f * intensity * orbScale).coerceAtMost(0.32f)),
                        Color.Transparent
                    ),
                    center = orb4Center,
                    radius = orb4Radius
                ),
                radius = orb4Radius,
                center = orb4Center
            )

            val orb5Center = Offset(
                x = w * (0.20f + 0.22f * cos(t * 0.47 + 2.7).toFloat()),
                y = h * (0.60f + 0.18f * sin(t * 0.29 + 3.5).toFloat())
            )
            val orb5Radius = w * (0.78f + 0.09f * cos(t * 0.26).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = (0.38f * intensity * orbScale).coerceAtMost(0.70f)),
                        Color.Transparent
                    ),
                    center = orb5Center,
                    radius = orb5Radius
                ),
                radius = orb5Radius,
                center = orb5Center
            )
        }

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

private const val FLUID_AURORA_AGSL = """
    uniform float2 resolution;
    uniform float time;
    uniform float intensity;
    uniform float isDark;
    uniform float3 cPrimary;
    uniform float3 cSecondary;
    uniform float3 cDeep;
    uniform float3 cGlow;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / max(resolution, float2(1.0));
        float aspect = resolution.x / max(resolution.y, 1.0);
        float2 p = (uv - 0.5) * float2(aspect, 1.0);

        // Two-pass organic domain warping for silky Apple Music / VisionOS aurora folds
        float t = time * 0.42;
        float2 q = p;
        q.x += 0.26 * sin(p.y * 2.4 + t * 0.92) + 0.14 * cos(p.x * 1.9 - t * 0.65);
        q.y += 0.24 * cos(p.x * 2.1 + t * 0.78) + 0.12 * sin(p.y * 2.6 + t * 0.54);

        float2 r = q;
        r.x += 0.18 * sin(q.y * 3.1 - t * 1.15 + 1.2);
        r.y += 0.18 * cos(q.x * 2.8 + t * 0.88 + 2.4);

        float w1 = 0.5 + 0.5 * sin(r.x * 2.7 + r.y * 1.6 + t * 0.8);
        float w2 = 0.5 + 0.5 * cos(r.y * 2.9 - r.x * 2.1 - t * 0.65 + 1.5);
        float w3 = smoothstep(0.18, 0.92, 0.5 + 0.5 * sin((q.x + q.y) * 3.2 - t * 0.95));

        // Ribbon highlight crest
        float ribbon = pow(smoothstep(0.35, 0.98, w1 * w2 + 0.22 * w3), 1.65);

        float3 baseDark = mix(cDeep * 0.55, float3(0.035, 0.035, 0.058), smoothstep(0.0, 1.0, uv.y));
        float3 baseLight = mix(float3(0.976, 0.972, 0.988), float3(0.922, 0.914, 0.956), uv.y);

        float3 auroraColor = mix(cPrimary, cSecondary, w1);
        auroraColor = mix(auroraColor, cGlow, ribbon * 0.62);

        float darkMix = clamp((0.36 + 0.48 * w2 + 0.26 * ribbon) * intensity, 0.0, 0.86);
        float lightMix = clamp((0.14 + 0.22 * w2 + 0.16 * ribbon) * intensity, 0.0, 0.44);

        float3 colDark = mix(baseDark, auroraColor, darkMix);
        // Bottom legibility vignette in dark mode
        colDark *= mix(0.88, 0.46, smoothstep(0.55, 1.0, uv.y));

        float3 colLight = mix(baseLight, mix(auroraColor, float3(1.0), 0.34), lightMix);

        float3 color = mix(colLight, colDark, isDark);

        // Subtle film-grain anti-banding dither (eliminates 8-bit gradient rings on OLED screens)
        float grain = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453) - 0.5;
        color = clamp(color + grain * 0.008, 0.0, 1.0);

        return half4(color, 1.0);
    }
"""
