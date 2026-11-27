package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.LyricLine
import kotlin.math.PI
import kotlin.math.sin

const val MIN_INTERLUDE_GAP_MS: Long = 3_500L
private const val LRC_INFERRED_INTERLUDE_THRESHOLD_MS: Long = 8_500L

/**
 * Represents an active instrumental intro (`afterLineIndex == -1`) or interlude
 * (`afterLineIndex == i`, sitting between `lines[i]` and `lines[i + 1]`).
 */
data class ActiveLyricInterlude(
    val afterLineIndex: Int,
    val startMs: Long,
    val endMs: Long
) {
    fun progressAt(posMs: Long): Float {
        val span = (endMs - startMs).coerceAtLeast(1L)
        return ((posMs - startMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }
}

data class BreathingDotVisual(
    val alpha: Float,
    val scale: Float,
    val isLocked: Boolean
)

/**
 * Detects whether playback is currently inside an instrumental intro or interlude
 * that has no active lyric line. Returns `null` whenever vocals are active or gaps are short.
 */
fun detectActiveLyricInterlude(
    lines: List<LyricLine>,
    effectivePosMs: Long,
    hasSyncedTimestamps: Boolean
): ActiveLyricInterlude? {
    if (!hasSyncedTimestamps || lines.isEmpty() || effectivePosMs < 0L) return null

    // 1. Instrumental Intro before the first lyric line
    val firstLine = lines.first()
    if (firstLine.startMs >= MIN_INTERLUDE_GAP_MS && effectivePosMs < firstLine.startMs) {
        return ActiveLyricInterlude(
            afterLineIndex = -1,
            startMs = 0L,
            endMs = firstLine.startMs
        )
    }

    // 2. Find the latest line whose startMs <= effectivePosMs
    var currentIdx = -1
    for (i in lines.indices) {
        if (effectivePosMs >= lines[i].startMs) {
            currentIdx = i
        } else {
            break
        }
    }
    if (currentIdx < 0 || currentIdx >= lines.lastIndex) return null

    val current = lines[currentIdx]
    val next = lines[currentIdx + 1]
    val totalSpanMs = next.startMs - current.startMs
    if (totalSpanMs < MIN_INTERLUDE_GAP_MS + 600L) return null

    val vocalEndMs = resolveLineVocalEndMs(current, next.startMs)
    val gapMs = next.startMs - vocalEndMs
    if (gapMs < MIN_INTERLUDE_GAP_MS) return null

    return if (effectivePosMs in vocalEndMs until next.startMs) {
        ActiveLyricInterlude(
            afterLineIndex = currentIdx,
            startMs = vocalEndMs,
            endMs = next.startMs
        )
    } else {
        null
    }
}

internal fun resolveLineVocalEndMs(line: LyricLine, nextStartMs: Long): Long {
    if (line.isWordSyncedNative) {
        val rawEnd = line.words.lastOrNull()?.endMs ?: line.endMs
        return rawEnd.coerceIn(line.startMs + 400L, nextStartMs)
    }

    // Line-synced format with explicit short endMs (e.g. SRT or LRC with explicit blank interlude marker)
    val explicitDuration = line.endMs - line.startMs
    val hasExplicitGap = line.endMs < nextStartMs &&
        (nextStartMs - line.endMs) >= MIN_INTERLUDE_GAP_MS &&
        explicitDuration in 500L..11_500L
    if (hasExplicitGap) {
        return line.endMs
    }

    // Standard LRC without explicit blank markers: infer vocal finish on long instrumental breaks
    if (nextStartMs - line.startMs >= LRC_INFERRED_INTERLUDE_THRESHOLD_MS) {
        val estimatedVocalMs = estimateVocalDurationMs(line.text)
        val candidateEnd = (line.startMs + estimatedVocalMs).coerceAtMost(line.endMs)
        if (nextStartMs - candidateEnd >= MIN_INTERLUDE_GAP_MS) {
            return candidateEnd
        }
    }

    return nextStartMs
}

internal fun estimateVocalDurationMs(text: String): Long {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 2_500L
    var cjkCount = 0
    var latinWords = 0
    var inLatinWord = false
    for (ch in trimmed) {
        val code = ch.code
        val isCjk = (code in 0x4E00..0x9FFF) || (code in 0x3040..0x30FF) || (code in 0xAC00..0xD7AF)
        if (isCjk) {
            cjkCount++
            inLatinWord = false
        } else if (ch.isLetterOrDigit()) {
            if (!inLatinWord) {
                latinWords++
                inLatinWord = true
            }
        } else {
            inLatinWord = false
        }
    }
    val rawMs = 1_300L + cjkCount * 350L + latinWords * 420L
    return rawMs.coerceIn(2_800L, 6_800L)
}

/**
 * Pure visual physics for each of the 3 instrumental breathing dots (`dotIndex` in `0..2`):
 * - Sweep transitions whole-dot opacity smoothly from translucent (`0.28f`) to full opaque (`1.0f`),
 *   rather than using a left-to-right horizontal wipe mask.
 * - Unswept dots breathe slowly between small (`0.72f`) and target full size (`1.0f`).
 * - Once a dot is swept to full opacity (`dotProgress >= 1f`), it locks cleanly at target full
 *   size (`1.0f`) and stops breathing, while remaining unswept dots keep breathing.
 */
fun computeBreathingDotVisuals(
    interludeProgress: Float,
    breathPhaseRad: Float,
    dotIndex: Int
): BreathingDotVisual {
    val clampedProgress = interludeProgress.coerceIn(0f, 1f)
    val segStart = dotIndex.coerceIn(0, 2) / 3f
    val dotProgress = ((clampedProgress - segStart) * 3f).coerceIn(0f, 1f)

    // Smoothstep uniform opacity transition (base translucent -> full opaque)
    val easedOpacity = dotProgress * dotProgress * (3f - 2f * dotProgress)
    val alpha = 0.28f + (1.0f - 0.28f) * easedOpacity

    if (dotProgress >= 0.999f) {
        return BreathingDotVisual(
            alpha = 1.0f,
            scale = 1.0f,
            isLocked = true
        )
    }

    // Slow organic breathing cycle (small -> target full size -> small) with subtle wave stagger
    val breathWave = (0.5 + 0.5 * sin(breathPhaseRad.toDouble() - dotIndex * 0.78)).toFloat()
    val breathingScale = 0.72f + (1.0f - 0.72f) * breathWave

    // Gently settle breathing into locked target size 1.0f as the dot completes its opacity sweep
    val settleRaw = ((dotProgress - 0.52f) / 0.48f).coerceIn(0f, 1f)
    val settleBlend = settleRaw * settleRaw * (3f - 2f * settleRaw)
    val scale = breathingScale + (1.0f - breathingScale) * settleBlend

    return BreathingDotVisual(
        alpha = alpha,
        scale = scale,
        isLocked = false
    )
}

/**
 * Dynamic Apple Music-inspired 3-dot breathing indicator for instrumental intros & interludes.
 * Expands in place ONLY when playback is inside that exact intro/interlude slot, and collapses
 * away smoothly when vocals resume (never shown as static placeholders while manually scrolling).
 */
@Composable
fun InstrumentalBreathingDots(
    visible: Boolean,
    interludeProgress: Float,
    isPlaying: Boolean,
    darkTheme: Boolean,
    slotTag: String,
    modifier: Modifier = Modifier
) {
    // Remember last non-zero progress so exit fade stays visually coherent instead of snapping to 0
    var heldProgress by remember { mutableFloatStateOf(0f) }
    if (visible) {
        heldProgress = interludeProgress.coerceIn(0f, 1f)
    }

    val smoothProgress by animateFloatAsState(
        targetValue = heldProgress,
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "interlude_progress_$slotTag"
    )

    var breathPhaseRad by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible, isPlaying) {
        if (!visible || !isPlaying) return@LaunchedEffect
        var lastFrameNs = 0L
        val radiansPerSecond = (2.0 * PI / 2.55).toFloat() // ~2.55s slow calm breathing cycle
        while (true) {
            withFrameNanos { frameNs ->
                if (lastFrameNs != 0L) {
                    val dtSec = ((frameNs - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.08f)
                    breathPhaseRad = (breathPhaseRad + dtSec * radiansPerSecond) % (2f * PI.toFloat())
                }
                lastFrameNs = frameNs
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = tween(durationMillis = 420),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = tween(durationMillis = 380)),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 340),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(durationMillis = 260)),
        modifier = modifier
    ) {
        val baseInk = if (darkTheme) Color.White else Color(0xFF151521)
        val isIntroSlot = slotTag == "intro"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 4.dp,
                    top = if (isIntroSlot) 6.dp else 22.dp,
                    bottom = if (isIntroSlot) 6.dp else 2.dp
                )
                .testTag("lyric_breathing_dots_$slotTag"),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .width(74.dp)
                    .height(22.dp)
            ) {
                val targetRadius = 6.5.dp.toPx()
                val centerStep = 23.dp.toPx()
                val startCenterX = targetRadius + 2.dp.toPx()
                val centerY = size.height / 2f

                for (dotIdx in 0 until 3) {
                    val visual = computeBreathingDotVisuals(
                        interludeProgress = smoothProgress,
                        breathPhaseRad = breathPhaseRad,
                        dotIndex = dotIdx
                    )
                    val cx = startCenterX + dotIdx * centerStep
                    val radius = targetRadius * visual.scale

                    // Subtle pearlescent bloom behind illuminated dots in dark mode
                    if (darkTheme && visual.alpha > 0.55f) {
                        val glowStrength = ((visual.alpha - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        val glowRadius = radius * 1.95f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    baseInk.copy(alpha = 0.24f * glowStrength),
                                    Color.Transparent
                                ),
                                center = Offset(cx, centerY),
                                radius = glowRadius
                            ),
                            radius = glowRadius,
                            center = Offset(cx, centerY)
                        )
                    }

                    // Crisp main dot with whole-dot uniform alpha transition
                    drawCircle(
                        color = baseInk.copy(alpha = visual.alpha),
                        radius = radius,
                        center = Offset(cx, centerY)
                    )
                }
            }
        }
    }
}
