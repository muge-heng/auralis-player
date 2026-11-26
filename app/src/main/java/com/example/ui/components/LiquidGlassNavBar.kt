package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuraPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class LiquidGlassNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Pure continuous slot coordinate for Apple-style follow-finger Liquid Glass tab scrubbing.
 * Centering the lens on the finger maps slot center (i + 0.5) * slotWidth -> i.0f,
 * with gentle elastic rubber-band overscroll past the outer capsule edges.
 */
fun computeLiquidNavDragSlot(
    touchX: Float,
    containerWidthPx: Float,
    itemCount: Int
): Float {
    if (itemCount <= 1 || containerWidthPx <= 0f) return 0f
    val slotWidth = containerWidthPx / itemCount.toFloat()
    val rawSlot = (touchX / slotWidth) - 0.5f
    val maxIdx = (itemCount - 1).toFloat()
    return when {
        rawSlot < 0f -> (rawSlot * 0.24f).coerceAtLeast(-0.16f)
        rawSlot > maxIdx -> maxIdx + ((rawSlot - maxIdx) * 0.24f).coerceAtMost(0.16f)
        else -> rawSlot
    }
}

/**
 * Discrete tab index under a given horizontal touch position inside the Liquid Glass capsule.
 */
fun computeLiquidNavTargetIndex(
    touchX: Float,
    containerWidthPx: Float,
    itemCount: Int
): Int {
    if (itemCount <= 1 || containerWidthPx <= 0f) return 0
    val slotWidth = containerWidthPx / itemCount.toFloat()
    return (touchX / slotWidth).toInt().coerceIn(0, itemCount - 1)
}

/**
 * Apple iOS 26-inspired Floating Transparent Liquid Glass 4-Tab Navigation Capsule.
 * - Renders a completely transparent floating pill over full-bleed scrolling content
 *   with real-time GPU backdrop blur/refraction and specular rim gleam.
 * - Supports both instant tap-to-switch AND Apple-style press-and-drag scrubbing:
 *   holding/sliding lifts & magnifies the glass droplet lens 1:1 under your thumb,
 *   highlights each tab with tactile haptic detents as you glide across, and commits
 *   the page switch cleanly on release.
 */
@Composable
fun LiquidGlassNavBar(
    items: List<LiquidGlassNavItem>,
    selectedIndex: Int,
    darkTheme: Boolean,
    accentColor: Color,
    auraPalette: AuraPalette = AuraPalette(),
    backdropState: LiquidGlassBackdropState? = null,
    visualEffectsEnhanced: Boolean = false,
    onHoverTick: () -> Unit = {},
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragSlopPx = with(density) { 5.dp.toPx() }

    val latestSelectedIndex by rememberUpdatedState(clampedIndex)
    val latestOnSelectIndex by rememberUpdatedState(onSelectIndex)
    val latestOnHoverTick by rememberUpdatedState(onHoverTick)

    val pillPosition = remember { Animatable(clampedIndex.toFloat()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubHoveredIndex by remember { mutableIntStateOf(clampedIndex) }
    var pressedTabIndex by remember { mutableIntStateOf(-1) }

    // Sync lens smoothly whenever external selectedIndex changes while not actively scrubbing
    LaunchedEffect(clampedIndex) {
        if (!isScrubbing) {
            scrubHoveredIndex = clampedIndex
            pillPosition.animateTo(
                targetValue = clampedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 380f
                )
            )
        }
    }

    // Apple-style liquid glass lens inflation while pressing & dragging
    val lensScaleX by animateFloatAsState(
        targetValue = if (isScrubbing) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 480f
        ),
        label = "liquid_lens_scale_x"
    )
    val lensScaleY by animateFloatAsState(
        targetValue = if (isScrubbing) 1.09f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 480f
        ),
        label = "liquid_lens_scale_y"
    )

    val capsuleShape = RoundedCornerShape(32.dp)
    val pillShape = RoundedCornerShape(26.dp)
    val dynamicAuraAccent = Color(auraPalette.primaryHex)
    val activeAccent = if (visualEffectsEnhanced) dynamicAuraAccent else accentColor

    val activeLensBrush = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(
                    alpha = when {
                        isScrubbing -> 0.34f
                        visualEffectsEnhanced -> 0.28f
                        else -> 0.22f
                    }
                ),
                activeAccent.copy(
                    alpha = when {
                        isScrubbing -> 0.34f
                        visualEffectsEnhanced -> 0.28f
                        else -> 0.18f
                    }
                )
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(
                    alpha = when {
                        isScrubbing -> 0.92f
                        visualEffectsEnhanced -> 0.80f
                        else -> 0.72f
                    }
                ),
                accentColor.copy(
                    alpha = when {
                        isScrubbing -> 0.20f
                        visualEffectsEnhanced -> 0.14f
                        else -> 0.10f
                    }
                )
            )
        )
    }

    val activeLensBorder = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isScrubbing || visualEffectsEnhanced) 0.56f else 0.36f),
                activeAccent.copy(alpha = if (isScrubbing) 0.44f else 0.28f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.98f),
                accentColor.copy(alpha = if (isScrubbing) 0.42f else 0.28f)
            )
        )
    }

    val activeTint = if (darkTheme) Color.White else accentColor
    val inactiveTint = if (darkTheme) Color.White.copy(alpha = 0.66f) else Color(0xFF3A3A4E)
    val labelShadow = if (darkTheme) {
        Shadow(color = Color.Black.copy(alpha = 0.42f), offset = Offset(0f, 1f), blurRadius = 3f)
    } else {
        Shadow(color = Color.White.copy(alpha = 0.82f), offset = Offset(0f, 1f), blurRadius = 3f)
    }

    val visualFocusIndex = if (isScrubbing) scrubHoveredIndex else clampedIndex

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            backdropState = backdropState,
            cornerRadius = 32.dp,
            darkTheme = darkTheme,
            accentColor = activeAccent,
            visualEffectsEnhanced = visualEffectsEnhanced,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = if (darkTheme) {
                        if (visualEffectsEnhanced) 22.dp else 15.dp
                    } else {
                        if (visualEffectsEnhanced) 12.dp else 8.dp
                    },
                    shape = capsuleShape,
                    ambientColor = if (darkTheme) Color.Black.copy(alpha = 0.45f) else Color(0xFF1A1A2E).copy(alpha = 0.08f),
                    spotColor = if (darkTheme) {
                        activeAccent.copy(alpha = 0.30f)
                    } else {
                        Color(0xFF1A1A2E).copy(alpha = 0.10f)
                    }
                )
                .testTag("liquid_glass_nav_bar")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 4.dp)
                    .pointerInput(items.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            val downX = down.position.x
                            val downIndex = computeLiquidNavTargetIndex(downX, widthPx, items.size)

                            pressedTabIndex = downIndex
                            var dragging = false
                            var lastHover = latestSelectedIndex
                            var isPointerAlive = true

                            // Press-and-hold gently lifts the liquid droplet under the finger even before horizontal travel
                            val holdJob: Job = scope.launch {
                                delay(115L)
                                if (isPointerAlive && !dragging) {
                                    dragging = true
                                    isScrubbing = true
                                    lastHover = downIndex
                                    scrubHoveredIndex = downIndex
                                    latestOnHoverTick()
                                    val targetSlot = computeLiquidNavDragSlot(downX, widthPx, items.size)
                                    pillPosition.animateTo(
                                        targetValue = targetSlot,
                                        animationSpec = spring(
                                            dampingRatio = 0.78f,
                                            stiffness = 720f
                                        )
                                    )
                                }
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        isPointerAlive = false
                                        holdJob.cancel()
                                        pressedTabIndex = -1
                                        if (dragging) {
                                            val commitIndex = lastHover.coerceIn(0, items.lastIndex)
                                            isScrubbing = false
                                            scope.launch {
                                                pillPosition.animateTo(
                                                    targetValue = commitIndex.toFloat(),
                                                    animationSpec = spring(
                                                        dampingRatio = 0.70f,
                                                        stiffness = 400f
                                                    )
                                                )
                                            }
                                            if (commitIndex != latestSelectedIndex) {
                                                latestOnSelectIndex(commitIndex)
                                            }
                                        } else if (!change.isConsumed) {
                                            // Instant crisp tap
                                            val tapIndex = computeLiquidNavTargetIndex(
                                                change.position.x,
                                                widthPx,
                                                items.size
                                            )
                                            latestOnSelectIndex(tapIndex)
                                        }
                                        break
                                    }

                                    val currX = change.position.x
                                    val dx = currX - downX
                                    if (!dragging && abs(dx) > dragSlopPx) {
                                        holdJob.cancel()
                                        dragging = true
                                        isScrubbing = true
                                        val initialHover = computeLiquidNavTargetIndex(currX, widthPx, items.size)
                                        lastHover = initialHover
                                        scrubHoveredIndex = initialHover
                                        if (initialHover != latestSelectedIndex) {
                                            latestOnHoverTick()
                                        }
                                    }

                                    if (dragging) {
                                        change.consume()
                                        val targetSlot = computeLiquidNavDragSlot(currX, widthPx, items.size)
                                        scope.launch { pillPosition.snapTo(targetSlot) }
                                        val hovered = computeLiquidNavTargetIndex(currX, widthPx, items.size)
                                        if (hovered != lastHover) {
                                            lastHover = hovered
                                            scrubHoveredIndex = hovered
                                            latestOnHoverTick()
                                        }
                                    }
                                }
                            } finally {
                                isPointerAlive = false
                                holdJob.cancel()
                                pressedTabIndex = -1
                                if (isScrubbing) {
                                    isScrubbing = false
                                    scope.launch {
                                        pillPosition.animateTo(
                                            targetValue = latestSelectedIndex.toFloat(),
                                            animationSpec = spring(
                                                dampingRatio = 0.74f,
                                                stiffness = 380f
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
            ) {
                val slotWidth = maxWidth / items.size
                val slotWidthPx = constraints.maxWidth.toFloat() / items.size.toFloat()

                // Spring-gliding & follow-finger Liquid Glass Lens behind active/hovered tab
                Box(
                    modifier = Modifier
                        .offset { IntOffset((pillPosition.value * slotWidthPx).roundToInt(), 0) }
                        .width(slotWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = lensScaleX
                            scaleY = lensScaleY
                        }
                        .clip(pillShape)
                        .background(activeLensBrush)
                        .border(
                            width = if (isScrubbing) 1.2.dp else 0.9.dp,
                            brush = activeLensBorder,
                            shape = pillShape
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { idx, item ->
                        val isFocused = idx == visualFocusIndex
                        val isPressed = !isScrubbing && idx == pressedTabIndex
                        val tint by animateColorAsState(
                            targetValue = if (isFocused) activeTint else inactiveTint,
                            animationSpec = tween(160),
                            label = "glass_tab_tint_$idx"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = when {
                                isScrubbing && isFocused -> 1.15f
                                isFocused -> 1.08f
                                isPressed -> 0.92f
                                else -> 0.96f
                            },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "glass_tab_scale_$idx"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(pillShape)
                                .semantics {
                                    role = Role.Tab
                                    selected = idx == clampedIndex
                                    onClick {
                                        onSelectIndex(idx)
                                        true
                                    }
                                }
                                .testTag(item.testTag),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier.size((21f * iconScale).dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                    shadow = labelShadow
                                ),
                                color = tint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
