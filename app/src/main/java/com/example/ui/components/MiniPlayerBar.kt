package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AuraPalette
import com.example.model.Track
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class MiniDragAxis {
    UNDECIDED,
    HORIZONTAL,
    VERTICAL
}

@Composable
fun MiniPlayerBar(
    track: Track,
    previousTrack: Track? = null,
    nextTrack: Track? = null,
    isPlaying: Boolean,
    progressFraction: Float,
    auraPalette: AuraPalette,
    darkTheme: Boolean = true,
    backdropState: LiquidGlassBackdropState? = null,
    visualEffectsEnhanced: Boolean = false,
    artworkAlpha: Float = 1f,
    onArtworkBoundsChanged: (Rect) -> Unit = {},
    onExpand: () -> Unit,
    onDragExpandStart: () -> Unit = {},
    onDragExpandDelta: (Float) -> Unit = {},
    onDragExpandEnd: (Float) -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 74.dp.toPx() }
    val touchSlopPx = with(density) { 8.dp.toPx() }

    val horizontalOffset = remember { Animatable(0f) }
    var lastSkipDirection by remember { mutableIntStateOf(1) }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mini_progress"
    )
    val accent = Color(auraPalette.primaryHex)
    val glowAccent = Color(auraPalette.glowHex)
    val swipeProgress = (horizontalOffset.value / swipeThresholdPx).coerceIn(-1.2f, 1.2f)
    val absSwipe = abs(swipeProgress)

    // Fade out right-side transport controls quickly during horizontal swipe so they NEVER overlap the swipe preview
    val controlsAlpha = (1f - absSwipe * 2.8f).coerceIn(0f, 1f)
    val contentRowAlpha = (1f - absSwipe * 0.85f).coerceIn(0.18f, 1f)

    // Subtle living aura breath around artwork & progress tip in Visual Effects Enhanced mode
    val auraPulse = if (visualEffectsEnhanced && isPlaying) {
        val transition = rememberInfiniteTransition(label = "mini_aura_pulse")
        val p by transition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "mini_pulse"
        )
        p
    } else {
        0.78f
    }

    val titleInk = if (darkTheme) Color.White else Color(0xFF141422)
    val subInk = if (darkTheme) Color.White.copy(alpha = 0.74f) else Color(0xFF3E3E54)
    val textShadow = if (darkTheme) {
        Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 1f), blurRadius = 4f)
    } else {
        Shadow(color = Color.White.copy(alpha = 0.72f), offset = Offset(0f, 1f), blurRadius = 4f)
    }
    val handleInk = if (darkTheme) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.22f)
    val playBtnBg = if (darkTheme) Color.White.copy(alpha = 0.16f) else accent.copy(alpha = 0.14f)
    val playBtnBorder = if (darkTheme) Color.White.copy(alpha = 0.24f) else accent.copy(alpha = 0.24f)
    val playIconTint = if (darkTheme) Color.White else Color(0xFF141422)
    val nextIconTint = if (darkTheme) Color.White.copy(alpha = 0.90f) else Color(0xFF232334)
    val trackBg = if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val swipePillBg = if (darkTheme) Color(0xFF161622).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.84f)
    val swipePillBorder = accent.copy(alpha = if (darkTheme) 0.55f else 0.40f)

    LiquidGlassSurface(
        backdropState = backdropState,
        cornerRadius = 24.dp,
        darkTheme = darkTheme,
        accentColor = accent,
        visualEffectsEnhanced = visualEffectsEnhanced,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(
                elevation = if (darkTheme) {
                    if (visualEffectsEnhanced) 22.dp else 14.dp
                } else {
                    if (visualEffectsEnhanced) 12.dp else 8.dp
                },
                shape = RoundedCornerShape(24.dp),
                ambientColor = if (darkTheme) accent.copy(alpha = if (visualEffectsEnhanced) 0.42f else 0.24f) else Color(0xFF1A1A2E).copy(alpha = 0.08f),
                spotColor = if (darkTheme) accent.copy(alpha = if (visualEffectsEnhanced) 0.52f else 0.30f) else Color(0xFF1A1A2E).copy(alpha = 0.10f)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var axis = MiniDragAxis.UNDECIDED
                    var totalDx = 0f
                    var totalDy = 0f
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    // Interrupt any active horizontal snap-back immediately
                    scope.launch { horizontalOffset.stop() }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            val vel = velocityTracker.calculateVelocity()
                            when (axis) {
                                MiniDragAxis.UNDECIDED -> {
                                    if (!change.isConsumed) {
                                        onExpand()
                                    }
                                }
                                MiniDragAxis.VERTICAL -> {
                                    onDragExpandEnd(vel.y)
                                }
                                MiniDragAxis.HORIZONTAL -> {
                                    val offset = horizontalOffset.value
                                    val triggerNext = offset < -swipeThresholdPx || vel.x < -700f
                                    val triggerPrev = offset > swipeThresholdPx || vel.x > 700f
                                    scope.launch {
                                        if (triggerNext) {
                                            lastSkipDirection = 1
                                            onSkipNext()
                                        } else if (triggerPrev) {
                                            lastSkipDirection = -1
                                            onSkipPrevious()
                                        }
                                        horizontalOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            }
                            break
                        }

                        val delta = change.positionChange()
                        if (delta.x != 0f || delta.y != 0f) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            totalDx += delta.x
                            totalDy += delta.y

                            if (axis == MiniDragAxis.UNDECIDED) {
                                if (abs(totalDy) > touchSlopPx && abs(totalDy) > abs(totalDx) * 1.15f) {
                                    axis = MiniDragAxis.VERTICAL
                                    onDragExpandStart()
                                } else if (abs(totalDx) > touchSlopPx && abs(totalDx) >= abs(totalDy)) {
                                    axis = MiniDragAxis.HORIZONTAL
                                }
                            }

                            when (axis) {
                                MiniDragAxis.VERTICAL -> {
                                    change.consume()
                                    onDragExpandDelta(delta.y)
                                }
                                MiniDragAxis.HORIZONTAL -> {
                                    change.consume()
                                    val targetX = (horizontalOffset.value + delta.x * 0.85f)
                                        .coerceIn(-swipeThresholdPx * 1.45f, swipeThresholdPx * 1.45f)
                                    scope.launch { horizontalOffset.snapTo(targetX) }
                                }
                                MiniDragAxis.UNDECIDED -> Unit
                            }
                        }
                    }
                }
            }
            .testTag("mini_player_bar")
    ) {
        Column {
            // Subtle top pill hint indicating drag-up capability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.5.dp)
                        .clip(CircleShape)
                        .background(handleInk)
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 10.dp, top = 5.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Horizontally draggable track area (artwork + metadata)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .offset { IntOffset(horizontalOffset.value.roundToInt(), 0) }
                            .graphicsLayer { alpha = contentRowAlpha },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .onGloballyPositioned { coords ->
                                    onArtworkBoundsChanged(coords.boundsInRoot())
                                }
                                .alpha(artworkAlpha),
                            contentAlignment = Alignment.Center
                        ) {
                            if (visualEffectsEnhanced) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                accent.copy(alpha = 0.50f * auraPulse),
                                                glowAccent.copy(alpha = 0.16f * auraPulse),
                                                Color.Transparent
                                            ),
                                            center = center,
                                            radius = size.maxDimension * 0.86f
                                        ),
                                        radius = size.maxDimension * 0.86f,
                                        center = center
                                    )
                                }
                            }
                            TrackArtwork(
                                artworkPath = track.artworkPath,
                                albumArtUri = track.albumArtUri,
                                contentDescription = stringResource(R.string.cd_album_art),
                                cornerRadius = 11.dp,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        AnimatedContent(
                            targetState = track,
                            transitionSpec = {
                                val dir = lastSkipDirection
                                (slideInHorizontally(
                                    initialOffsetX = { w -> dir * (w / 3) },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeIn()) togetherWith (slideOutHorizontally(
                                    targetOffsetX = { w -> -dir * (w / 3) }
                                ) + fadeOut())
                            },
                            modifier = Modifier.weight(1f),
                            label = "mini_track_meta"
                        ) { activeTrack ->
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = activeTrack.title,
                                        style = MaterialTheme.typography.titleSmall.copy(shadow = textShadow),
                                        color = titleInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (activeTrack.isLossless) {
                                        QualityBadgePill(
                                            label = activeTrack.codecLabel,
                                            isHiRes = activeTrack.isHiRes,
                                            isLossless = true
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (activeTrack.isGeneralAudio) {
                                        "普通音频 · ${activeTrack.folderPath.ifBlank { activeTrack.codecLabel }}"
                                    } else {
                                        "${activeTrack.artist} · ${activeTrack.album}"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                                    color = subInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    // Transport buttons fade cleanly during swipe so they never collide with swipe track preview
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            alpha = controlsAlpha
                            scaleX = 0.85f + 0.15f * controlsAlpha
                            scaleY = 0.85f + 0.15f * controlsAlpha
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(playBtnBg)
                                .border(0.75.dp, playBtnBorder, CircleShape)
                                .bouncyClickable(enabled = controlsAlpha > 0.4f, onClick = onTogglePlayPause)
                                .testTag("mini_play_pause"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = playIconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .bouncyClickable(enabled = controlsAlpha > 0.4f) {
                                    lastSkipDirection = 1
                                    onSkipNext()
                                }
                                .testTag("mini_skip_next"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.cd_next_track),
                                tint = nextIconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Clean unoccluded directional track preview pill on horizontal swipe
                if (absSwipe > 0.08f) {
                    val isPrev = swipeProgress > 0f
                    val targetTrack = if (isPrev) previousTrack else nextTrack
                    val unit = if (track.isGeneralAudio) "条" else "首"
                    val dirLabel = if (isPrev) "上一$unit" else "下一$unit"
                    val readyToTrigger = absSwipe >= 0.92f
                    val previewAlpha = ((absSwipe - 0.06f) * 1.45f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 14.dp),
                        contentAlignment = if (isPrev) Alignment.CenterStart else Alignment.CenterEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .alpha(previewAlpha)
                                .clip(RoundedCornerShape(14.dp))
                                .background(swipePillBg)
                                .border(
                                    width = if (readyToTrigger) 1.1.dp else 0.75.dp,
                                    color = if (readyToTrigger) accent else swipePillBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            if (isPrev) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(
                                horizontalAlignment = if (isPrev) Alignment.Start else Alignment.End,
                                modifier = Modifier.width(196.dp)
                            ) {
                                Text(
                                    text = if (readyToTrigger) "松手切换$dirLabel" else dirLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = targetTrack?.let { target ->
                                        if (target.isGeneralAudio) {
                                            "${target.title} · ${target.folderPath.ifBlank { target.codecLabel }}"
                                        } else {
                                            "${target.title} · ${target.artist}"
                                        }
                                    } ?: "当前队列仅此一$unit",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = titleInk,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!isPrev) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Ultra-fine glowing acoustic progress hairline (+ luminous tip bloom when enhanced)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (visualEffectsEnhanced) 3.dp else 2.5.dp)
                    .background(trackBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(if (visualEffectsEnhanced) 3.dp else 2.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(accent, glowAccent)
                            )
                        )
                )
                if (visualEffectsEnhanced && animatedProgress > 0.01f) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    ) {
                        val tipX = (size.width * animatedProgress).coerceIn(2f, size.width)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.92f * auraPulse),
                                    glowAccent.copy(alpha = 0.55f * auraPulse),
                                    Color.Transparent
                                ),
                                center = Offset(tipX, size.height * 0.5f),
                                radius = 16f
                            ),
                            radius = 16f,
                            center = Offset(tipX, size.height * 0.5f)
                        )
                    }
                }
            }
        }
    }
}
