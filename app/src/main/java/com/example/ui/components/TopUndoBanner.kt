package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.RubyAccent
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class UndoBannerState(
    val id: Long = System.nanoTime(),
    val message: String,
    val detail: String = "支持一键撤销，或左右/上滑关闭",
    val actionLabel: String = "撤销",
    val durationMs: Long = 6000L,
    val onUndo: () -> Unit
)

data class PendingConfirmDeletion(
    val id: Long = System.nanoTime(),
    val title: String,
    val message: String,
    val confirmLabel: String = "确认删除",
    val onConfirm: () -> Unit
)

/**
 * Top-pinned floating deletion notice with an instant Undo pill, countdown progress hairline,
 * and smooth left/right/upward swipe-to-dismiss gesture.
 */
@Composable
fun TopUndoBanner(
    state: UndoBannerState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdX = with(density) { 96.dp.toPx() }
    val dismissThresholdY = with(density) { 46.dp.toPx() }

    AnimatedVisibility(
        visible = state != null,
        enter = slideInVertically(
            initialOffsetY = { -it - 32 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { -it - 32 },
            animationSpec = tween(200)
        ) + fadeOut(tween(160)),
        modifier = modifier
    ) {
        state?.let { banner ->
            val offsetX = remember(banner.id) { Animatable(0f) }
            val offsetY = remember(banner.id) { Animatable(0f) }
            val progress = remember(banner.id) { Animatable(1f) }
            var isDragging by remember(banner.id) { mutableStateOf(false) }

            LaunchedEffect(banner.id, isDragging) {
                if (!isDragging) {
                    val remainingMs = (banner.durationMs * progress.value).toLong().coerceAtLeast(150L)
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = remainingMs.toInt(), easing = LinearEasing)
                    )
                    if (progress.value <= 0.001f) {
                        onDismiss()
                    }
                }
            }

            val dragAlpha = remember(offsetX.value, offsetY.value) {
                val xRatio = (abs(offsetX.value) / (dismissThresholdX * 1.9f)).coerceIn(0f, 0.7f)
                val yRatio = ((-offsetY.value).coerceAtLeast(0f) / (dismissThresholdY * 1.8f)).coerceIn(0f, 0.7f)
                (1f - maxOf(xRatio, yRatio)).coerceIn(0.25f, 1f)
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 10.dp,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .graphicsLayer { alpha = dragAlpha }
                    .border(
                        width = 1.dp,
                        color = RubyAccent.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .pointerInput(banner.id) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragCancel = {
                                isDragging = false
                                scope.launch {
                                    launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                }
                            },
                            onDragEnd = {
                                val shouldDismiss = abs(offsetX.value) >= dismissThresholdX ||
                                    offsetY.value <= -dismissThresholdY
                                if (shouldDismiss) {
                                    onDismiss()
                                } else {
                                    isDragging = false
                                    scope.launch {
                                        launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                        launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                    val nextY = (offsetY.value + dragAmount.y).coerceAtMost(18f)
                                    offsetY.snapTo(nextY)
                                }
                            }
                        )
                    }
                    .testTag("top_undo_banner")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(RubyAccent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = RubyAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = banner.message,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = banner.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ChampagneGold.copy(alpha = 0.22f))
                                .border(0.75.dp, ChampagneGold.copy(alpha = 0.6f), RoundedCornerShape(50))
                                .clickable {
                                    banner.onUndo()
                                    onDismiss()
                                }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                                .testTag("top_undo_banner_action")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = banner.actionLabel,
                                tint = ChampagneGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = banner.actionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ChampagneGold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("top_undo_banner_close")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭提示",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Slim animated countdown hairline along the bottom edge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                                .height(2.5.dp)
                                .background(
                                    if (isDragging) Color.Transparent else ChampagneGold
                                )
                        )
                    }
                }
            }
        }
    }
}
