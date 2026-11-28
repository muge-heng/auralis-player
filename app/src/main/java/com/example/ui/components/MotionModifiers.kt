package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.example.model.AuraPalette
import com.example.model.Track
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.adaptiveGoldColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Blocks pointer hit-testing and accessibility traversal from passing through full-screen
 * modal sheets or overlays to sibling screens underneath, without consuming events that
 * belong to child buttons, sliders, scrollable lists, or drag handles inside this overlay.
 */
fun Modifier.blockUnderlyingTouches(): Modifier = this
    .semantics(mergeDescendants = false) {
        isTraversalGroup = true
    }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Final)
            }
        }
    }

/**
 * Tactile spring press-bounce modifier for primary controls and interactive cards.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.91f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncy_press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Animated favorite button with spring pop + radial ruby halo & micro-spark burst.
 */
@Composable
fun HeartBurstIconButton(
    isFavorite: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inactiveTint: Color = Color.White.copy(alpha = 0.82f),
    iconSize: Dp = 26.dp
) {
    val heartScale = remember { Animatable(1f) }
    val burstProgress = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(isFavorite) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        if (isFavorite) {
            burstProgress.snapTo(0f)
            heartScale.snapTo(0.76f)
            heartScale.animateTo(
                targetValue = 1.28f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
            heartScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            heartScale.animateTo(0.86f, tween(90))
            heartScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    LaunchedEffect(isFavorite) {
        if (initialized && isFavorite) {
            burstProgress.snapTo(0.01f)
            burstProgress.animateTo(1f, tween(durationMillis = 480))
            burstProgress.snapTo(0f)
        }
    }

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        val sparkGold = adaptiveGoldColor()
        if (burstProgress.value > 0f && burstProgress.value < 1f) {
            val p = burstProgress.value
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension * 0.48f
                val ringAlpha = (1f - p).coerceIn(0f, 1f)
                drawCircle(
                    color = RubyAccent.copy(alpha = ringAlpha * 0.65f),
                    radius = maxR * p,
                    center = center,
                    style = Stroke(width = (3f * (1f - p)).coerceAtLeast(0.8f))
                )
                val sparkCount = 6
                for (i in 0 until sparkCount) {
                    val angle = (i * (2.0 * PI / sparkCount)) - PI / 6.0
                    val dist = maxR * (0.45f + p * 0.65f)
                    val pt = Offset(
                        x = center.x + (cos(angle) * dist).toFloat(),
                        y = center.y + (sin(angle) * dist).toFloat()
                    )
                    drawCircle(
                        color = if (i % 2 == 0) RubyAccent.copy(alpha = ringAlpha) else sparkGold.copy(alpha = ringAlpha),
                        radius = (3.2f * (1f - p)).coerceAtLeast(0.5f),
                        center = pt
                    )
                }
            }
        }

        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = contentDescription,
                tint = if (isFavorite) RubyAccent else inactiveTint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = heartScale.value
                        scaleY = heartScale.value
                    }
            )
        }
    }
}

/**
 * Shared-bounds hero artwork overlay that continuously morphs position, size, corner radius,
 * and elevation shadow between MiniPlayerBar and NowPlayingSheet as the user drags or springs.
 */
@Composable
fun MorphingFloatingArtwork(
    track: Track,
    auraPalette: AuraPalette,
    expansionProgress: Float,
    miniRect: Rect,
    fullRect: Rect,
    startCornerDp: Dp = 11.dp,
    endCornerDp: Dp = 24.dp
) {
    val density = LocalDensity.current
    val t = expansionProgress.coerceIn(0f, 1f)

    val leftPx = lerp(miniRect.left, fullRect.left, t)
    val topPx = lerp(miniRect.top, fullRect.top, t)
    val widthPx = lerp(miniRect.width, fullRect.width, t).coerceAtLeast(1f)
    val heightPx = lerp(miniRect.height, fullRect.height, t).coerceAtLeast(1f)

    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val cornerDp = lerp(startCornerDp, endCornerDp, t)
    val targetElevation = if (endCornerDp >= 18.dp) 28.dp else 8.dp
    val elevationDp = lerp(6.dp, targetElevation, t)
    val accent = Color(auraPalette.primaryHex)

    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
            .size(width = widthDp, height = heightDp)
            .shadow(
                elevation = elevationDp,
                shape = RoundedCornerShape(cornerDp),
                ambientColor = accent.copy(alpha = lerp(0.22f, 0.52f, t)),
                spotColor = accent.copy(alpha = lerp(0.28f, 0.62f, t))
            )
    ) {
        TrackArtwork(
            artworkPath = track.artworkPath,
            albumArtUri = track.albumArtUri,
            contentDescription = track.title,
            cornerRadius = cornerDp,
            modifier = Modifier.fillMaxSize()
        )
    }
}
