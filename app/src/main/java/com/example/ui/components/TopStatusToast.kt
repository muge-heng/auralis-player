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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
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
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class StatusToastLevel(val defaultDurationMs: Long) {
    INFO(2200L),
    SUCCESS(2400L),
    WARNING(3200L),
    ERROR(3800L)
}

data class StatusToastState(
    val id: Long = System.nanoTime(),
    val message: String,
    val level: StatusToastLevel = StatusToastLevel.INFO,
    val durationMs: Long = level.defaultDurationMs
)

fun inferStatusToastLevel(message: String): StatusToastLevel {
    return when {
        message.contains("失败") || message.contains("异常") || message.contains("错误") ||
            message.contains("无法") || message.contains("拒绝") -> StatusToastLevel.ERROR

        message.contains("未授予") || message.contains("尚未获得") || message.contains("暂未") ||
            message.contains("暂不支持") || message.contains("未能") || message.contains("没有") ||
            message.contains("为空") || message.contains("暂无") || message.contains("只读") -> StatusToastLevel.WARNING

        message.contains("完成") || message.contains("已导出") || message.contains("已刻录") ||
            message.contains("已将歌词刻录") || message.contains("已另存") || message.contains("已创建") ||
            message.contains("已清理") || message.contains("已复制") -> StatusToastLevel.SUCCESS

        else -> StatusToastLevel.INFO
    }
}

/**
 * Compact, theme-matched, top-floating toast that never covers bottom navigation, mini-player,
 * or chat input controls. Supports smooth left/right/upward swipe-to-dismiss and auto-dismiss.
 */
@Composable
fun TopStatusToast(
    state: StatusToastState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdX = with(density) { 84.dp.toPx() }
    val dismissThresholdY = with(density) { 40.dp.toPx() }

    AnimatedVisibility(
        visible = state != null,
        enter = slideInVertically(
            initialOffsetY = { -it - 24 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(tween(160)),
        exit = slideOutVertically(
            targetOffsetY = { -it - 24 },
            animationSpec = tween(180)
        ) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        state?.let { toast ->
            val offsetX = remember(toast.id) { Animatable(0f) }
            val offsetY = remember(toast.id) { Animatable(0f) }
            val progress = remember(toast.id) { Animatable(1f) }
            var isDragging by remember(toast.id) { mutableStateOf(false) }

            LaunchedEffect(toast.id, isDragging) {
                if (!isDragging) {
                    val remainingMs = (toast.durationMs * progress.value).toLong().coerceAtLeast(150L)
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = remainingMs.toInt(), easing = LinearEasing)
                    )
                    if (progress.value <= 0.001f) {
                        onDismiss()
                    }
                }
            }

            val accent = when (toast.level) {
                StatusToastLevel.SUCCESS -> EmeraldLossless
                StatusToastLevel.WARNING -> ChampagneGold
                StatusToastLevel.ERROR -> RubyAccent
                StatusToastLevel.INFO -> MaterialTheme.colorScheme.primary
            }
            val icon = when (toast.level) {
                StatusToastLevel.SUCCESS -> Icons.Rounded.CheckCircle
                StatusToastLevel.WARNING -> Icons.Rounded.WarningAmber
                StatusToastLevel.ERROR -> Icons.Rounded.ErrorOutline
                StatusToastLevel.INFO -> Icons.Rounded.Info
            }

            val dragAlpha = remember(offsetX.value, offsetY.value) {
                val xRatio = (abs(offsetX.value) / (dismissThresholdX * 1.8f)).coerceIn(0f, 0.72f)
                val yRatio = ((-offsetY.value).coerceAtLeast(0f) / (dismissThresholdY * 1.8f)).coerceIn(0f, 0.72f)
                (1f - maxOf(xRatio, yRatio)).coerceIn(0.25f, 1f)
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .graphicsLayer { alpha = dragAlpha }
                    .border(
                        width = 0.9.dp,
                        color = accent.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .pointerInput(toast.id) {
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
                                    val nextY = (offsetY.value + dragAmount.y).coerceAtMost(14f)
                                    offsetY.snapTo(nextY)
                                }
                            }
                        )
                    }
                    .testTag("top_status_toast")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = toast.message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("top_status_toast_close")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭提示",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(if (isDragging) Color.Transparent else accent.copy(alpha = 0.85f))
                        )
                    }
                }
            }
        }
    }
}
