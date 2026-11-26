package com.example.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AuraPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure visual parameters computed for translucent Liquid Glass surfaces across
 * standard and GPU-enhanced visual modes.
 */
data class GlassVisualProfile(
    val blurRadiusDp: Float,
    val veilTopAlpha: Float,
    val veilBottomAlpha: Float,
    val accentTintAlpha: Float,
    val refractionPx: Float,
    val useAgslRefraction: Boolean,
    val useBackdropBlur: Boolean
)

fun computeGlassVisualProfile(
    darkTheme: Boolean,
    enhanced: Boolean,
    supportsHardwareBlur: Boolean,
    supportsAgslShader: Boolean
): GlassVisualProfile {
    val activeBlur = supportsHardwareBlur
    val activeAgsl = enhanced && supportsHardwareBlur && supportsAgslShader
    return when {
        activeBlur && enhanced -> GlassVisualProfile(
            blurRadiusDp = if (darkTheme) 26f else 24f,
            veilTopAlpha = if (darkTheme) 0.20f else 0.44f,
            veilBottomAlpha = if (darkTheme) 0.28f else 0.34f,
            accentTintAlpha = if (darkTheme) 0.10f else 0.05f,
            refractionPx = if (darkTheme) 15f else 12f,
            useAgslRefraction = activeAgsl,
            useBackdropBlur = true
        )
        activeBlur -> GlassVisualProfile(
            blurRadiusDp = 18f,
            veilTopAlpha = if (darkTheme) 0.28f else 0.48f,
            veilBottomAlpha = if (darkTheme) 0.36f else 0.40f,
            accentTintAlpha = if (darkTheme) 0.06f else 0.03f,
            refractionPx = 0f,
            useAgslRefraction = false,
            useBackdropBlur = true
        )
        enhanced -> GlassVisualProfile(
            blurRadiusDp = 0f,
            veilTopAlpha = if (darkTheme) 0.46f else 0.68f,
            veilBottomAlpha = if (darkTheme) 0.54f else 0.60f,
            accentTintAlpha = if (darkTheme) 0.12f else 0.06f,
            refractionPx = 0f,
            useAgslRefraction = false,
            useBackdropBlur = false
        )
        else -> GlassVisualProfile(
            blurRadiusDp = 0f,
            veilTopAlpha = if (darkTheme) 0.52f else 0.74f,
            veilBottomAlpha = if (darkTheme) 0.60f else 0.66f,
            accentTintAlpha = if (darkTheme) 0.06f else 0.03f,
            refractionPx = 0f,
            useAgslRefraction = false,
            useBackdropBlur = false
        )
    }
}

/**
 * Holds a live offscreen GPU GraphicsLayer recorded from the scrollable screen content
 * so sibling floating chrome (MiniPlayerBar, LiquidGlassNavBar) can render true real-time
 * translucent frosted glass and optical edge refraction without opaque black/white backing panels.
 */
@Stable
class LiquidGlassBackdropState(
    val contentLayer: GraphicsLayer
) {
    var sourceOriginInRoot by mutableStateOf(Offset.Zero)
    var isRecorded by mutableStateOf(false)
}

@Composable
fun rememberLiquidGlassBackdropState(): LiquidGlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidGlassBackdropState(layer) }
}

/**
 * Attaches to the scrollable screen container to record its live frames into [state]
 * for real-time backdrop sampling by sibling floating Liquid Glass bars.
 */
fun Modifier.liquidGlassBackdropSource(
    state: LiquidGlassBackdropState,
    enabled: Boolean
): Modifier {
    if (!enabled) return this
    return this
        .onGloballyPositioned { coords ->
            state.sourceOriginInRoot = coords.positionInRoot()
        }
        .drawWithContent {
            if (size.width >= 1f && size.height >= 1f && !state.contentLayer.isReleased) {
                state.contentLayer.record {
                    this@drawWithContent.drawContent()
                }
                if (!state.isRecorded) {
                    state.isRecorded = true
                }
                drawLayer(state.contentLayer)
            } else {
                drawContent()
            }
        }
}

/**
 * True Apple iOS 26-inspired optical Liquid Glass surface:
 * - Zero solid black/white opaque backing plate
 * - Real-time frosted backdrop blur (Android 12+)
 * - Optional GPU AGSL rounded-lens optical refraction + RGB chromatic rim dispersion + specular Fresnel sheen
 *   when [visualEffectsEnhanced] is enabled on Android 13+.
 */
@Composable
fun LiquidGlassSurface(
    backdropState: LiquidGlassBackdropState?,
    cornerRadius: Dp,
    darkTheme: Boolean,
    accentColor: Color,
    visualEffectsEnhanced: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(cornerRadius)
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val supportsAgsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val profile = remember(darkTheme, visualEffectsEnhanced, supportsBlur, supportsAgsl) {
        computeGlassVisualProfile(
            darkTheme = darkTheme,
            enhanced = visualEffectsEnhanced,
            supportsHardwareBlur = supportsBlur,
            supportsAgslShader = supportsAgsl
        )
    }

    val blurPx = with(density) { profile.blurRadiusDp.dp.toPx() }
    val cornerPx = with(density) { cornerRadius.toPx() }
    var targetOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    // Gentle iridescent rim light shimmer when Visual Effects Enhancement is enabled
    val shimmerPhase = if (visualEffectsEnhanced) {
        val transition = rememberInfiniteTransition(label = "liquid_glass_shimmer")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_phase"
        )
        phase
    } else {
        0f
    }

    val runtimeShader = remember(profile.useAgslRefraction) {
        if (profile.useAgslRefraction && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(LIQUID_GLASS_REFRACTION_AGSL) }.getOrNull()
        } else {
            null
        }
    }

    val staticBlurEffect = remember(profile.useBackdropBlur, blurPx) {
        if (profile.useBackdropBlur && blurPx > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                AndroidRenderEffect.createBlurEffect(
                    blurPx,
                    blurPx,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }.getOrNull()
        } else {
            null
        }
    }

    val tintVeilBrush = if (darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1B1B28).copy(alpha = profile.veilTopAlpha),
                Color(0xFF10101A).copy(alpha = profile.veilBottomAlpha)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = profile.veilTopAlpha),
                Color(0xFFF3F3FA).copy(alpha = profile.veilBottomAlpha)
            )
        )
    }

    val specularTopSheen = if (darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (visualEffectsEnhanced) 0.16f else 0.10f),
                accentColor.copy(alpha = profile.accentTintAlpha),
                Color.Transparent
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (visualEffectsEnhanced) 0.52f else 0.34f),
                accentColor.copy(alpha = profile.accentTintAlpha),
                Color.Transparent
            )
        )
    }

    val rimBorderBrush = if (visualEffectsEnhanced) {
        val drift = (sin(shimmerPhase.toDouble()).toFloat() * 0.5f + 0.5f)
        if (darkTheme) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.42f - drift * 0.08f),
                    accentColor.copy(alpha = 0.42f + drift * 0.14f),
                    Color(0xFF8BE9FD).copy(alpha = 0.24f + drift * 0.10f),
                    Color.White.copy(alpha = 0.22f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.96f),
                    accentColor.copy(alpha = 0.30f + drift * 0.12f),
                    Color(0xFF7B61FF).copy(alpha = 0.16f + drift * 0.08f),
                    Color.White.copy(alpha = 0.78f)
                )
            )
        }
    } else {
        if (darkTheme) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.30f),
                    accentColor.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.12f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.92f),
                    Color.Black.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.62f)
                )
            )
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                targetOriginInRoot = coords.positionInRoot()
            }
            .clip(shape)
    ) {
        // Layer 1: Live refracted & frosted real-time backdrop behind the floating capsule.
        // IMPORTANT: Keep this inner blurred graphicsLayer UNCLIPPED (rectangular) while the parent
        // Box performs the rounded capsule clip, and paint an opaque theme-matched base before drawing
        // contentLayer. That prevents Skia blur from sampling transparent (0,0,0,0) edge pixels that
        // would otherwise collapse into a dark/black vignette halo in light mode!
        val fallbackBgColor = if (darkTheme) Color(0xFF0B0B12) else Color(0xFFF6F6FA)
        if (backdropState != null && profile.useBackdropBlur) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = false
                        val w = size.width
                        val h = size.height
                        renderEffect = if (
                            profile.useAgslRefraction &&
                            runtimeShader != null &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            w > 8f && h > 8f
                        ) {
                            runCatching {
                                runtimeShader.setFloatUniform("resolution", w, h)
                                runtimeShader.setFloatUniform("cornerRadius", cornerPx.coerceAtMost(minOf(w, h) * 0.5f))
                                runtimeShader.setFloatUniform("refractionStrength", profile.refractionPx)
                                runtimeShader.setFloatUniform(
                                    "chromaticDispersion",
                                    if (darkTheme) 0.24f else 0.14f
                                )
                                runtimeShader.setFloatUniform("isDark", if (darkTheme) 1f else 0f)
                                runtimeShader.setFloatUniform("shimmerPhase", shimmerPhase)
                                runtimeShader.setFloatUniform(
                                    "fallbackBg",
                                    fallbackBgColor.red,
                                    fallbackBgColor.green,
                                    fallbackBgColor.blue
                                )

                                val blurNode = AndroidRenderEffect.createBlurEffect(
                                    blurPx,
                                    blurPx,
                                    Shader.TileMode.CLAMP
                                )
                                val refractNode = AndroidRenderEffect.createRuntimeShaderEffect(
                                    runtimeShader,
                                    "backdrop"
                                )
                                AndroidRenderEffect.createChainEffect(refractNode, blurNode)
                                    .asComposeRenderEffect()
                            }.getOrElse { staticBlurEffect }
                        } else {
                            staticBlurEffect
                        }
                    }
                    .drawBehind {
                        drawRect(fallbackBgColor)
                        if (backdropState.isRecorded && !backdropState.contentLayer.isReleased) {
                            val dx = backdropState.sourceOriginInRoot.x - targetOriginInRoot.x
                            val dy = backdropState.sourceOriginInRoot.y - targetOriginInRoot.y
                            translate(left = dx, top = dy) {
                                drawLayer(backdropState.contentLayer)
                            }
                        }
                    }
            )
        }

        // Layer 2: Translucent glass chemistry veil + top specular light catch
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(tintVeilBrush)
                .background(specularTopSheen)
                .border(
                    width = if (visualEffectsEnhanced) 1.1.dp else 0.9.dp,
                    brush = rimBorderBrush,
                    shape = shape
                )
        )

        // Layer 3: Foreground interactive content
        content()
    }
}

/**
 * Global ambient album-color atmospheric veil rendered behind primary app screens
 * when Visual Effects Enhancement is enabled, harmonizing the entire workspace with
 * the active track's extracted cover palette and feeding rich color into the floating glass bars.
 */
@Composable
fun AmbientAppAuraOverlay(
    palette: AuraPalette,
    isPlaying: Boolean,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor by animateColorAsState(
        targetValue = Color(palette.primaryHex),
        animationSpec = tween(1100),
        label = "app_aura_primary"
    )
    val secondaryColor by animateColorAsState(
        targetValue = Color(palette.secondaryHex),
        animationSpec = tween(1100),
        label = "app_aura_secondary"
    )
    val glowColor by animateColorAsState(
        targetValue = Color(palette.glowHex),
        animationSpec = tween(1100),
        label = "app_aura_glow"
    )

    val animatedSpeed by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.25f,
        animationSpec = tween(800),
        label = "app_aura_speed"
    )
    val latestSpeed by rememberUpdatedState(animatedSpeed)

    var flowTime by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val dtSec = ((nowNanos - lastFrameNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000.0
            lastFrameNanos = nowNanos
            flowTime += dtSec * latestSpeed.toDouble()
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = flowTime
        val scale = if (darkTheme) 1.0f else 0.44f

        // In light mode, gently lift extracted album colors toward airy pastels so dark covers
        // never cast muddy grey/black smudges underneath translucent light glass capsules.
        fun pastelForLight(c: Color): Color = if (darkTheme) c else Color(
            red = (c.red * 0.58f + 0.42f).coerceIn(0f, 1f),
            green = (c.green * 0.58f + 0.42f).coerceIn(0f, 1f),
            blue = (c.blue * 0.58f + 0.42f).coerceIn(0f, 1f),
            alpha = 1f
        )
        val effPrimary = pastelForLight(primaryColor)
        val effSecondary = pastelForLight(secondaryColor)
        val effGlow = pastelForLight(glowColor)

        // Upper-left slow ambient bloom
        val c1 = Offset(
            x = w * (0.20f + 0.16f * cos(t * 0.28).toFloat()),
            y = h * (0.14f + 0.08f * sin(t * 0.22 + 0.4).toFloat())
        )
        val r1 = w * 0.92f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    effPrimary.copy(alpha = 0.22f * scale),
                    effPrimary.copy(alpha = 0.06f * scale),
                    Color.Transparent
                ),
                center = c1,
                radius = r1
            ),
            radius = r1,
            center = c1
        )

        // Lower-right atmospheric glow right behind the floating glass dock!
        val c2 = Offset(
            x = w * (0.78f + 0.14f * sin(t * 0.25 + 1.2).toFloat()),
            y = h * (0.86f + 0.07f * cos(t * 0.21 + 0.8).toFloat())
        )
        val r2 = w * 0.96f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    effSecondary.copy(alpha = 0.24f * scale),
                    effGlow.copy(alpha = 0.08f * scale),
                    Color.Transparent
                ),
                center = c2,
                radius = r2
            ),
            radius = r2,
            center = c2
        )

        // Lower-left counter-glow so both sides of the floating mini-player & nav bar refract rich color
        val c3 = Offset(
            x = w * (0.22f + 0.15f * cos(t * 0.23 + 2.4).toFloat()),
            y = h * (0.82f + 0.08f * sin(t * 0.27 + 1.7).toFloat())
        )
        val r3 = w * 0.78f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    effPrimary.copy(alpha = 0.16f * scale),
                    Color.Transparent
                ),
                center = c3,
                radius = r3
            ),
            radius = r3,
            center = c3
        )
    }
}

private const val LIQUID_GLASS_REFRACTION_AGSL = """
    uniform shader backdrop;
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float refractionStrength;
    uniform float chromaticDispersion;
    uniform float isDark;
    uniform float shimmerPhase;
    uniform half3 fallbackBg;

    float sdRoundBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - (b - float2(r));
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    half3 compositeOverBg(half4 s, half3 bg) {
        // Skia shader samples are premultiplied (rgb*a, a). Composite over opaque theme bg
        // so low-alpha / boundary blur samples NEVER darken to black!
        return clamp(s.rgb + bg * (1.0 - clamp(s.a, 0.0, 1.0)), 0.0, 1.0);
    }

    half4 main(float2 fragCoord) {
        float2 halfSize = resolution * 0.5;
        float2 p = fragCoord - halfSize;
        float r = min(cornerRadius, min(halfSize.x, halfSize.y));

        float d = sdRoundBox(p, halfSize, r);
        float bevelWidth = max(18.0, min(halfSize.y * 0.58, 30.0));
        float edgeDepth = clamp(-d, 0.0, bevelWidth);
        float rim = smoothstep(bevelWidth, 0.0, edgeDepth);

        // Smooth finite-difference inward normal for curved glass lensing
        float eps = 1.5;
        float gx = sdRoundBox(p + float2(eps, 0.0), halfSize, r) - sdRoundBox(p - float2(eps, 0.0), halfSize, r);
        float gy = sdRoundBox(p + float2(0.0, eps), halfSize, r) - sdRoundBox(p - float2(0.0, eps), halfSize, r);
        float2 grad = float2(gx, gy);
        float gradLen = max(length(grad), 0.0001);
        float2 normal = grad / gradLen;

        // Curved edge optical refraction + subtle RGB prismatic dispersion along the glass rim
        float lensPower = rim * rim * refractionStrength;
        float2 offsetG = -normal * lensPower;
        float2 offsetR = offsetG * (1.0 + chromaticDispersion);
        float2 offsetB = offsetG * (1.0 - chromaticDispersion);

        float2 maxCoord = max(resolution - float2(1.0), float2(1.0));
        float2 uvR = clamp(fragCoord + offsetR, float2(1.0), maxCoord);
        float2 uvG = clamp(fragCoord + offsetG, float2(1.0), maxCoord);
        float2 uvB = clamp(fragCoord + offsetB, float2(1.0), maxCoord);

        half3 rgbR = compositeOverBg(backdrop.eval(uvR), fallbackBg);
        half3 rgbG = compositeOverBg(backdrop.eval(uvG), fallbackBg);
        half3 rgbB = compositeOverBg(backdrop.eval(uvB), fallbackBg);

        half3 refracted = half3(rgbR.r, rgbG.g, rgbB.b);
        // Gentle icy crystal brightness lift in light mode so glass reads clean & airy
        half lightLift = half((1.0 - isDark) * 0.08);
        refracted = mix(refracted, half3(1.0), lightLift);

        // Fresnel top-edge specular light reflection & subtle moving caustic glint
        float topHighlight = clamp(-normal.y, 0.0, 1.0) * pow(rim, 1.45);
        float bottomHighlight = clamp(normal.y, 0.0, 1.0) * pow(rim, 2.2) * 0.42;
        float wave = 0.5 + 0.5 * sin(fragCoord.x * 0.018 + shimmerPhase);
        float caustic = pow(rim, 2.4) * (0.26 + 0.34 * wave);

        float specularAmount = (topHighlight * 0.24 + bottomHighlight * 0.10 + caustic * 0.12) * mix(0.85, 0.65, isDark);
        half3 finalRgb = clamp(refracted + half3(specularAmount), 0.0, 1.0);

        return half4(finalRgb, 1.0);
    }
"""
