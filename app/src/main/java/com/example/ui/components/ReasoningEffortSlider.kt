package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ReasoningEffortLevel
import com.example.ui.theme.AuraViolet
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.LocalAuralisDarkTheme
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.adaptiveAmberColor
import com.example.ui.theme.adaptiveGoldColor
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun reasoningAccentColor(level: ReasoningEffortLevel, darkTheme: Boolean = true): Color = when (level) {
    ReasoningEffortLevel.OFF -> Color(0xFF7D7D94)
    ReasoningEffortLevel.LOW -> if (darkTheme) EmeraldLossless else Color(0xFF158A52)
    ReasoningEffortLevel.MEDIUM -> if (darkTheme) Color(0xFF38BDF8) else Color(0xFF0284C7)
    ReasoningEffortLevel.HIGH -> if (darkTheme) AuraViolet else Color(0xFF5A42D4)
    ReasoningEffortLevel.XHIGH -> if (darkTheme) Color(0xFFF59E0B) else Color(0xFFB45309)
    ReasoningEffortLevel.MAX -> RubyAccent
    ReasoningEffortLevel.ULTRA -> if (darkTheme) Color(0xFFE879F9) else Color(0xFFA21CAF)
}

/**
 * Custom aesthetic reasoning_effort slider with magnetic detents, optional extended tier toggles
 * (medium / xhigh / ultra), and escalating visual effects at HIGH, XHIGH, MAX, and ULTRA tiers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReasoningEffortSliderCard(
    currentLevel: ReasoningEffortLevel,
    enabledLevels: Set<ReasoningEffortLevel>,
    onSelectLevel: (ReasoningEffortLevel) -> Unit,
    onToggleExtendedLevel: (ReasoningEffortLevel) -> Unit,
    onDetentHaptic: (ReasoningEffortLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val orderedLevels = remember(enabledLevels) {
        ReasoningEffortLevel.entries
            .filter { it in enabledLevels }
            .ifEmpty { ReasoningEffortLevel.defaultEnabledSet.toList() }
            .sortedBy { it.intensityRank }
    }
    val currentIndex = orderedLevels.indexOf(currentLevel).coerceAtLeast(0)
    val maxIndex = (orderedLevels.size - 1).coerceAtLeast(1)

    var showExtendedDrawer by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(currentIndex.toFloat() / maxIndex.toFloat()) }
    var sliderWidthPx by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

    val targetFraction = currentIndex.toFloat() / maxIndex.toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = if (isDragging) dragFraction else targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "reasoning_slider_fraction"
    )

    val shockwave = remember { Animatable(0f) }
    LaunchedEffect(currentLevel) {
        if (currentLevel.intensityRank >= ReasoningEffortLevel.HIGH.intensityRank) {
            shockwave.snapTo(0.01f)
            shockwave.animateTo(1f, tween(durationMillis = 650))
            shockwave.snapTo(0f)
        }
    }

    val isDarkTheme = LocalAuralisDarkTheme.current
    val goldAccent = adaptiveGoldColor()
    val amberAccent = adaptiveAmberColor()
    val accentColor by animateColorAsState(
        targetValue = reasoningAccentColor(currentLevel, isDarkTheme),
        animationSpec = tween(320),
        label = "reasoning_accent"
    )

    // Continuous ambient phase for HIGH / XHIGH / MAX / ULTRA visual effects
    val infinite = rememberInfiniteTransition(label = "reasoning_fx")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (currentLevel) {
                    ReasoningEffortLevel.ULTRA -> 1300
                    ReasoningEffortLevel.MAX -> 1700
                    ReasoningEffortLevel.XHIGH -> 2100
                    else -> 2800
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "fx_phase"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fx_breathe"
    )

    val isHighFx = currentLevel.intensityRank >= ReasoningEffortLevel.HIGH.intensityRank
    val cardBorderBrush = when (currentLevel) {
        ReasoningEffortLevel.ULTRA -> Brush.linearGradient(
            listOf(
                Color(0xFF38BDF8),
                Color(0xFFA855F7),
                Color(0xFFFA2D48),
                goldAccent
            )
        )
        ReasoningEffortLevel.MAX -> Brush.linearGradient(
            listOf(RubyAccent, goldAccent, RubyAccent.copy(alpha = 0.55f))
        )
        ReasoningEffortLevel.XHIGH -> Brush.linearGradient(
            listOf(amberAccent, Color(0xFFEC4899), AuraViolet)
        )
        ReasoningEffortLevel.HIGH -> Brush.linearGradient(
            listOf(AuraViolet.copy(alpha = 0.8f), Color(0xFF38BDF8).copy(alpha = 0.45f))
        )
        else -> Brush.linearGradient(
            listOf(accentColor.copy(alpha = 0.35f), MaterialTheme.colorScheme.outlineVariant)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        if (isHighFx) accentColor.copy(alpha = 0.09f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            )
            .border(
                width = if (currentLevel == ReasoningEffortLevel.ULTRA) 1.5.dp else 1.dp,
                brush = cardBorderBrush,
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header row: Active effort pill + Custom Tiers button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (currentLevel) {
                            ReasoningEffortLevel.ULTRA -> Icons.Rounded.AutoAwesome
                            ReasoningEffortLevel.MAX, ReasoningEffortLevel.XHIGH -> Icons.Rounded.Bolt
                            else -> Icons.Rounded.Psychology
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "思考强度 · ${currentLevel.shortLabel}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val fxTag = when (currentLevel) {
                            ReasoningEffortLevel.ULTRA -> "SUPERNOVA"
                            ReasoningEffortLevel.MAX -> "OVERDRIVE"
                            ReasoningEffortLevel.XHIGH -> "PLASMA"
                            ReasoningEffortLevel.HIGH -> "ORBITAL"
                            else -> null
                        }
                        if (fxTag != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.20f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = fxTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = currentLevel.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (showExtendedDrawer) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .clickable { showExtendedDrawer = !showExtendedDrawer }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("toggle_extended_reasoning_tiers")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "自定义档位",
                    tint = if (showExtendedDrawer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "扩展档位",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (showExtendedDrawer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Custom High-Craft Canvas Slider with Dynamic Tier Effects
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { sliderWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(orderedLevels) {
                    detectTapGestures { tapOffset ->
                        val pad = 22.dp.toPx()
                        val usable = (sliderWidthPx - pad * 2f).coerceAtLeast(1f)
                        val frac = ((tapOffset.x - pad) / usable).coerceIn(0f, 1f)
                        val idx = (frac * (orderedLevels.size - 1)).roundToInt()
                            .coerceIn(0, orderedLevels.lastIndex)
                        val chosen = orderedLevels[idx]
                        if (chosen != currentLevel) {
                            onDetentHaptic(chosen)
                            onSelectLevel(chosen)
                        }
                    }
                }
                .pointerInput(orderedLevels) {
                    var lastIdx = currentIndex
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            isDragging = true
                            val pad = 22.dp.toPx()
                            val usable = (sliderWidthPx - pad * 2f).coerceAtLeast(1f)
                            dragFraction = ((start.x - pad) / usable).coerceIn(0f, 1f)
                            lastIdx = (dragFraction * (orderedLevels.size - 1)).roundToInt()
                                .coerceIn(0, orderedLevels.lastIndex)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val pad = 22.dp.toPx()
                            val usable = (sliderWidthPx - pad * 2f).coerceAtLeast(1f)
                            val frac = ((change.position.x - pad) / usable).coerceIn(0f, 1f)
                            dragFraction = frac
                            val idx = (frac * (orderedLevels.size - 1)).roundToInt()
                                .coerceIn(0, orderedLevels.lastIndex)
                            if (idx != lastIdx) {
                                lastIdx = idx
                                val chosen = orderedLevels[idx]
                                onDetentHaptic(chosen)
                                onSelectLevel(chosen)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
                .testTag("reasoning_effort_slider")
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                val padX = 22.dp.toPx()
                val cy = size.height / 2f
                val trackW = (size.width - padX * 2f).coerceAtLeast(1f)
                val thumbX = padX + trackW * animatedFraction
                val trackH = if (isHighFx) 8.dp.toPx() else 6.dp.toPx()

                // Inactive base track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(padX, cy - trackH / 2f),
                    size = Size(trackW, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                )

                // Active track brush by intensity tier
                val activeW = (thumbX - padX).coerceAtLeast(0f)
                if (activeW > 1f) {
                    val activeBrush = when (currentLevel) {
                        ReasoningEffortLevel.ULTRA -> {
                            val shift = (sin(phase) * 0.15f)
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color(0xFF38BDF8),
                                    (0.32f + shift).coerceIn(0.1f, 0.5f) to AuraViolet,
                                    (0.66f + shift).coerceIn(0.45f, 0.85f) to RubyAccent,
                                    1f to goldAccent
                                ),
                                startX = padX,
                                endX = thumbX
                            )
                        }
                        ReasoningEffortLevel.MAX -> Brush.horizontalGradient(
                            listOf(AuraViolet, RubyAccent, goldAccent),
                            startX = padX,
                            endX = thumbX
                        )
                        ReasoningEffortLevel.XHIGH -> Brush.horizontalGradient(
                            listOf(Color(0xFF38BDF8), amberAccent, Color(0xFFEC4899)),
                            startX = padX,
                            endX = thumbX
                        )
                        ReasoningEffortLevel.HIGH -> Brush.horizontalGradient(
                            listOf(Color(0xFF38BDF8), AuraViolet, Color(0xFFC084FC)),
                            startX = padX,
                            endX = thumbX
                        )
                        else -> Brush.horizontalGradient(
                            listOf(accentColor.copy(alpha = 0.65f), accentColor),
                            startX = padX,
                            endX = thumbX
                        )
                    }

                    // Ambient under-glow behind active track for HIGH+
                    if (isHighFx) {
                        drawRoundRect(
                            brush = activeBrush,
                            topLeft = Offset(padX, cy - trackH * 1.15f),
                            size = Size(activeW, trackH * 2.3f),
                            cornerRadius = CornerRadius(trackH, trackH),
                            alpha = 0.24f * breathe
                        )
                    }

                    drawRoundRect(
                        brush = activeBrush,
                        topLeft = Offset(padX, cy - trackH / 2f),
                        size = Size(activeW, trackH),
                        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                    )

                    // XHIGH / MAX / ULTRA: Live animated energy plasma sine wave flowing across active track
                    if (currentLevel.intensityRank >= ReasoningEffortLevel.XHIGH.intensityRank) {
                        val wavePath = Path()
                        val amp = when (currentLevel) {
                            ReasoningEffortLevel.ULTRA -> 7.dp.toPx()
                            ReasoningEffortLevel.MAX -> 5.5.dp.toPx()
                            else -> 4.dp.toPx()
                        }
                        val freq = if (currentLevel == ReasoningEffortLevel.ULTRA) 0.065f else 0.045f
                        val steps = 36
                        for (i in 0..steps) {
                            val ratio = i / steps.toFloat()
                            val px = padX + activeW * ratio
                            val envelope = sin(ratio * PI).toFloat()
                            val py = cy + sin(px * freq - phase * 2f) * amp * envelope
                            if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
                        }
                        drawPath(
                            path = wavePath,
                            color = if (currentLevel == ReasoningEffortLevel.ULTRA) Color.White.copy(alpha = 0.72f)
                            else goldAccent.copy(alpha = 0.62f),
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Magnetic Detent Ticks
                orderedLevels.forEachIndexed { idx, lvl ->
                    val tickX = padX + trackW * (idx.toFloat() / maxIndex.toFloat())
                    val passed = idx <= currentIndex
                    drawCircle(
                        color = if (passed) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.28f),
                        radius = if (idx == currentIndex) 4.dp.toPx() else 2.5.dp.toPx(),
                        center = Offset(tickX, cy)
                    )
                    if (!lvl.isDefaultEnabled) {
                        // Tiny diamond dot above extended custom tiers
                        drawCircle(
                            color = goldAccent.copy(alpha = if (passed) 0.9f else 0.45f),
                            radius = 1.8.dp.toPx(),
                            center = Offset(tickX, cy - 11.dp.toPx())
                        )
                    }
                }

                // Shockwave ring on switching to HIGH+
                if (shockwave.value > 0f && shockwave.value < 1f) {
                    val sp = shockwave.value
                    drawCircle(
                        color = accentColor.copy(alpha = (1f - sp) * 0.7f),
                        radius = 12.dp.toPx() + 26.dp.toPx() * sp,
                        center = Offset(thumbX, cy),
                        style = Stroke(width = 2.dp.toPx() * (1f - sp))
                    )
                }

                // HIGH: Orbital halo ring
                if (currentLevel == ReasoningEffortLevel.HIGH) {
                    drawCircle(
                        color = AuraViolet.copy(alpha = 0.38f * breathe),
                        radius = 15.dp.toPx() + 2.dp.toPx() * breathe,
                        center = Offset(thumbX, cy),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }

                // MAX: Fiery orbiting overdrive embers
                if (currentLevel == ReasoningEffortLevel.MAX) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(RubyAccent.copy(alpha = 0.48f), Color.Transparent),
                            center = Offset(thumbX, cy),
                            radius = 22.dp.toPx()
                        ),
                        radius = 22.dp.toPx(),
                        center = Offset(thumbX, cy)
                    )
                    for (i in 0 until 5) {
                        val a = phase + i * (2.0 * PI / 5.0)
                        val r = 13.dp.toPx() + (sin(phase * 2f + i) * 3.dp.toPx())
                        val sx = thumbX + (cos(a) * r).toFloat()
                        val sy = cy + (sin(a) * r).toFloat()
                        drawCircle(
                            color = if (i % 2 == 0) goldAccent else RubyAccent,
                            radius = 2.4.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }

                // ULTRA: Prismatic Supernova star orbit + pulsing dual holographic rings
                if (currentLevel == ReasoningEffortLevel.ULTRA) {
                    val haloR = 24.dp.toPx() * (0.9f + 0.15f * breathe)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE879F9).copy(alpha = 0.55f),
                                Color(0xFF38BDF8).copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(thumbX, cy),
                            radius = haloR
                        ),
                        radius = haloR,
                        center = Offset(thumbX, cy)
                    )
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.65f),
                        radius = 15.dp.toPx(),
                        center = Offset(thumbX, cy),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    val starColors = listOf(Color(0xFF38BDF8), goldAccent, Color(0xFFE879F9), RubyAccent, Color.White, EmeraldLossless)
                    for (i in 0 until 6) {
                        val a = phase * 1.4f + i * (2.0 * PI / 6.0)
                        val rx = 18.dp.toPx()
                        val ry = 10.dp.toPx()
                        val sx = thumbX + (cos(a) * rx).toFloat()
                        val sy = cy + (sin(a) * ry).toFloat()
                        drawCircle(
                            color = starColors[i % starColors.size],
                            radius = 2.6.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }

                // Glowing Thumb Core
                val thumbRadius = if (isDragging) 11.dp.toPx() else 9.5.dp.toPx()
                drawCircle(
                    color = accentColor.copy(alpha = 0.45f),
                    radius = thumbRadius + 4.dp.toPx(),
                    center = Offset(thumbX, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbX, cy)
                )
                drawCircle(
                    color = accentColor,
                    radius = thumbRadius * 0.52f,
                    center = Offset(thumbX, cy)
                )
            }
        }

        // Detent labels directly beneath the slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            orderedLevels.forEach { lvl ->
                val isSelected = lvl == currentLevel
                Text(
                    text = lvl.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (lvl != currentLevel) {
                                onDetentHaptic(lvl)
                                onSelectLevel(lvl)
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("reasoning_tier_${lvl.apiValue}")
                )
            }
        }

        // Collapsible Extended Tier Selector (medium, xhigh, ultra)
        AnimatedVisibility(visible = showExtendedDrawer) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "手动开启 / 关闭扩展档位（默认开启 off · low · high · max）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReasoningEffortLevel.optionalExtendedLevels.forEach { extLevel ->
                        val active = extLevel in enabledLevels
                        val extColor = reasoningAccentColor(extLevel, isDarkTheme)
                        FilterChip(
                            selected = active,
                            onClick = { onToggleExtendedLevel(extLevel) },
                            label = { Text("${extLevel.apiValue} (${extLevel.label})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = extColor.copy(alpha = 0.22f),
                                selectedLabelColor = extColor
                            ),
                            modifier = Modifier.testTag("toggle_ext_${extLevel.apiValue}")
                        )
                    }
                }
            }
        }
    }
}
