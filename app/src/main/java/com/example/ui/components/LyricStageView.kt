package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LYRIC_FONT_SCALE_TIERS
import com.example.model.LyricDocument
import com.example.model.LyricFormat
import com.example.model.LyricLine
import com.example.model.LyricSettings
import com.example.model.LyricWord
import com.example.model.lyricFontScaleLabel
import com.example.model.snapLyricFontScaleIndex
import com.example.ui.theme.ChampagneGold
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure physics profile for lyric line transition cascade (Standard vs. Enhanced Elastic Wave).
 */
data class LyricWavePhysics(
    val maxDistance: Int,
    val staggerMs: Long,
    val impulseDp: Float,
    val stretchImpulseY: Float,
    val dampingRatio: Float,
    val stiffness: Float,
    val activeLiftDp: Float,
    val activeScale: Float,
    val scrollDampingRatio: Float,
    val scrollStiffness: Float
)

fun resolveLyricWavePhysics(
    enableElasticWave: Boolean,
    isActive: Boolean,
    lineIndex: Int,
    activeIndex: Int,
    distanceFromActive: Int
): LyricWavePhysics {
    return if (enableElasticWave) {
        val stagger = when {
            isActive -> 0L
            lineIndex > activeIndex -> (distanceFromActive * 46L).coerceAtMost(280L)
            else -> (distanceFromActive * 26L).coerceAtMost(160L)
        }
        val impulse = when {
            isActive -> 30f
            distanceFromActive == 1 -> 24f
            distanceFromActive == 2 -> 17.5f
            distanceFromActive == 3 -> 12f
            else -> 7.5f
        }
        val stretchY = when {
            isActive -> 1.16f
            distanceFromActive == 1 -> 1.10f
            distanceFromActive == 2 -> 1.05f
            else -> 1.02f
        }
        LyricWavePhysics(
            maxDistance = 7,
            staggerMs = stagger,
            impulseDp = impulse,
            stretchImpulseY = stretchY,
            dampingRatio = if (isActive) 0.40f else 0.48f,
            stiffness = if (isActive) 185f else 155f,
            activeLiftDp = -7.5f,
            activeScale = 1.08f,
            scrollDampingRatio = 0.54f,
            scrollStiffness = 145f
        )
    } else {
        val stagger = when {
            isActive -> 0L
            lineIndex > activeIndex -> (distanceFromActive * 30L).coerceAtMost(150L)
            else -> (distanceFromActive * 18L).coerceAtMost(90L)
        }
        val impulse = when {
            isActive -> 13f
            distanceFromActive == 1 -> 9.5f
            distanceFromActive == 2 -> 6.5f
            else -> 4f
        }
        LyricWavePhysics(
            maxDistance = 5,
            staggerMs = stagger,
            impulseDp = impulse,
            stretchImpulseY = 1.0f,
            dampingRatio = if (isActive) 0.56f else 0.64f,
            stiffness = if (isActive) 215f else 185f,
            activeLiftDp = -3f,
            activeScale = 1.04f,
            scrollDampingRatio = 0.72f,
            scrollStiffness = 165f
        )
    }
}

/**
 * Apple Music-caliber Word-by-Word Karaoke Lyric Stage.
 * Features progressive Gaussian depth-of-field blur & progressive scale-down on non-active lines,
 * auto-unblur while manually scrolling, optional Immersive Lyrics mode, adjustable font weight (300..900),
 * discrete instant font-size tiers, and full Dark/Light mode adaptation.
 */
@Composable
fun LyricStageView(
    document: LyricDocument,
    positionMs: Long,
    settings: LyricSettings,
    isPlaying: Boolean = true,
    isImmersive: Boolean = false,
    darkTheme: Boolean = true,
    onSeekTo: (Long) -> Unit,
    onToggleSimulatedWordByWord: (Boolean) -> Unit,
    onUpdateLyricSettings: (LyricSettings) -> Unit,
    onOpenLyricManager: () -> Unit,
    onQuickOnlineMatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectivePosMs = positionMs + document.globalOffsetMs
    val listState = rememberLazyListState()
    var showTypographyControls by remember { mutableStateOf(false) }

    val ink = if (darkTheme) Color.White else Color(0xFF151521)
    val accentInk = if (darkTheme) ChampagneGold else Color(0xFFD92344)
    val pillBg = if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    val pillBorder = if (darkTheme) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.12f)
    val drawerBg = if (darkTheme) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.88f)
    val drawerBorder = if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)

    // Distinguish genuine manual user drag from programmatic auto-scroll:
    // collectIsDraggedAsState() is ONLY true when the user's finger drags the list,
    // so programmatic auto-scrolling NEVER falsely clears progressive depth-of-field blur.
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var isUserBrowsing by remember { mutableStateOf(false) }

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            isUserBrowsing = true
        }
    }

    LaunchedEffect(isUserBrowsing, isUserDragging, listState.isScrollInProgress) {
        if (isUserBrowsing && !isUserDragging && !listState.isScrollInProgress) {
            delay(2200L)
            isUserBrowsing = false
        }
    }

    val activeIndex = remember(document.lines, effectivePosMs, document.hasSyncedTimestamps) {
        if (!document.hasSyncedTimestamps || document.lines.isEmpty()) -1
        else {
            var idx = -1
            for (i in document.lines.indices) {
                if (effectivePosMs >= document.lines[i].startMs) {
                    idx = i
                } else {
                    break
                }
            }
            idx
        }
    }

    val activeInterlude = remember(document.lines, effectivePosMs, document.hasSyncedTimestamps) {
        detectActiveLyricInterlude(
            lines = document.lines,
            effectivePosMs = effectivePosMs,
            hasSyncedTimestamps = document.hasSyncedTimestamps
        )
    }

    val effectiveActiveLineIndex = if (activeInterlude != null) -1 else activeIndex

    // Compute step direction synchronously when active lyric line changes so every LyricLineRow
    // receives the exact transition direction on the very first frame (including 0 -> 1 & backward seeks).
    val stepTracker = remember(document.rawContent) { IntArray(1) { -1 } }
    val stepDirection = remember(document.rawContent, effectiveActiveLineIndex) {
        val prev = stepTracker[0]
        val dir = when {
            effectiveActiveLineIndex < 0 || prev < 0 -> 0
            effectiveActiveLineIndex > prev -> 1
            effectiveActiveLineIndex < prev -> -1
            else -> 0
        }
        if (effectiveActiveLineIndex >= 0) {
            stepTracker[0] = effectiveActiveLineIndex
        }
        dir
    }

    // Target LazyColumn item index (slot 0 = dynamic intro breathing dots; slot i+1 = lyric line i + trailing interlude slot)
    val focalListIndex = when {
        activeInterlude != null && activeInterlude.afterLineIndex == -1 -> 0
        activeInterlude != null -> activeInterlude.afterLineIndex + 1
        effectiveActiveLineIndex >= 0 -> effectiveActiveLineIndex + 1
        else -> -1
    }
    val focalIsMidInterlude = activeInterlude != null && activeInterlude.afterLineIndex >= 0

    // Elastic spring auto-scroll to focal anchor (never overrides manual user drag)
    LaunchedEffect(focalListIndex, focalIsMidInterlude, isImmersive, isUserBrowsing) {
        if (!isUserBrowsing && focalListIndex >= 0 && focalListIndex <= document.lines.size) {
            // Brief frame settle when expanding a mid-interlude so item height includes the dots
            if (focalIsMidInterlude) delay(60L)
            val layoutInfo = listState.layoutInfo
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
            val targetAnchorOffset = (viewportHeight * if (isImmersive) 0.30f else 0.25f).toInt()
            val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == focalListIndex }

            if (visibleItem != null) {
                val itemFocalY = if (focalIsMidInterlude) {
                    visibleItem.offset + (visibleItem.size * 0.58f).toInt()
                } else {
                    visibleItem.offset
                }
                val scrollDelta = (itemFocalY - targetAnchorOffset).toFloat()
                if (abs(scrollDelta) > 1.5f) {
                    val scrollPhysics = resolveLyricWavePhysics(
                        enableElasticWave = settings.enableElasticLyricWave,
                        isActive = true,
                        lineIndex = focalListIndex,
                        activeIndex = focalListIndex,
                        distanceFromActive = 0
                    )
                    listState.animateScrollBy(
                        value = scrollDelta,
                        animationSpec = spring(
                            dampingRatio = scrollPhysics.scrollDampingRatio,
                            stiffness = scrollPhysics.scrollStiffness
                        )
                    )
                }
            } else {
                listState.animateScrollToItem(
                    index = focalListIndex,
                    scrollOffset = -targetAnchorOffset
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top HUD bar (smoothly hidden during Immersive Lyrics mode)
        AnimatedVisibility(
            visible = !isImmersive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isToggleableLineSync = document.hasSyncedTimestamps && !document.lines.any { it.isWordSyncedNative }
                    val shortFormatTag = when (document.format) {
                        LyricFormat.LRC -> "LRC"
                        LyricFormat.SRT -> "SRT"
                        LyricFormat.QRC -> "QRC"
                        LyricFormat.YRC -> "YRC"
                        LyricFormat.KRC -> "KRC"
                        LyricFormat.ENHANCED_LRC -> "LRC+"
                        LyricFormat.TTML -> "TTML"
                        LyricFormat.PLAIN -> "纯文本"
                        LyricFormat.NONE -> "无歌词"
                    }
                    val badgeText = when {
                        document.isEmpty -> "无歌词"
                        document.isSimulatedWordSync -> "$shortFormatTag·模拟"
                        isToggleableLineSync -> shortFormatTag
                        else -> document.format.label
                    }

                    // Left scrollable group of compact status & mode pills (prevents cramping on narrow widths)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillBg)
                                .border(
                                    0.75.dp,
                                    if (document.isSimulatedWordSync) accentInk.copy(alpha = 0.36f) else pillBorder,
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("lyric_format_badge")
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = ink.copy(alpha = 0.92f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isToggleableLineSync) {
                            val simActive = settings.enableSimulatedWordByWord
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (simActive) accentInk.copy(alpha = if (darkTheme) 0.24f else 0.14f)
                                        else pillBg
                                    )
                                    .border(
                                        0.75.dp,
                                        if (simActive) accentInk.copy(alpha = 0.55f)
                                        else pillBorder,
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { onToggleSimulatedWordByWord(!simActive) }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("toggle_simulated_word_lyrics")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (simActive) accentInk else ink.copy(alpha = 0.65f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (simActive) "逐字" else "逐行",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (simActive) accentInk else ink.copy(alpha = 0.72f),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Immersive Lyrics Toggle Pill
                        if (!document.isEmpty) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (settings.enableImmersiveLyrics) accentInk.copy(alpha = if (darkTheme) 0.28f else 0.15f)
                                        else pillBg
                                    )
                                    .clickable {
                                        showTypographyControls = false
                                        onUpdateLyricSettings(settings.copy(enableImmersiveLyrics = !settings.enableImmersiveLyrics))
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("toggle_immersive_lyrics")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fullscreen,
                                        contentDescription = "开启沉浸歌词",
                                        tint = if (settings.enableImmersiveLyrics) accentInk else ink,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "沉浸",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (settings.enableImmersiveLyrics) accentInk else ink,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Inline Typography / Weight / Blur Drawer Toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (showTypographyControls) accentInk.copy(alpha = if (darkTheme) 0.28f else 0.15f)
                                    else pillBg
                                )
                                .border(
                                    0.75.dp,
                                    if (showTypographyControls) accentInk.copy(alpha = 0.55f)
                                    else Color.Transparent,
                                    RoundedCornerShape(50)
                                )
                                .clickable { showTypographyControls = !showTypographyControls }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("toggle_lyric_font_panel")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FormatSize,
                                    contentDescription = "调节歌词字重与字号",
                                    tint = if (showTypographyControls) accentInk else ink,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "字重",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (showTypographyControls) accentInk else ink,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Pinned right-hand Lyric Manager / Multi-source Search Pill (guaranteed single-line)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillBg)
                            .border(0.75.dp, pillBorder, RoundedCornerShape(50))
                            .clickable(onClick = onOpenLyricManager)
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("open_lyric_manager_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "歌词匹配与校准",
                                tint = ink,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (document.globalOffsetMs != 0L) {
                                    "${if (document.globalOffsetMs > 0) "+" else ""}${document.globalOffsetMs / 1000f}s"
                                } else {
                                    "匹配/源"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = ink,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Inline Translucent Live Font Weight, Discrete Tier Scale & Blur Panel
                AnimatedVisibility(
                    visible = showTypographyControls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val currentScaleIdx = snapLyricFontScaleIndex(settings.fontScale)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(drawerBg)
                            .border(0.75.dp, drawerBorder, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "歌词字体粗细",
                                style = MaterialTheme.typography.labelLarge,
                                color = ink
                            )
                            Text(
                                text = settings.fontWeightLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = accentInk
                            )
                        }
                        Slider(
                            value = settings.fontWeight.toFloat(),
                            onValueChange = { raw ->
                                val snapped = ((raw / 100f).roundToInt() * 100).coerceIn(300, 900)
                                if (snapped != settings.fontWeight) {
                                    onUpdateLyricSettings(settings.copy(fontWeight = snapped))
                                }
                            },
                            valueRange = 300f..900f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = accentInk,
                                activeTrackColor = accentInk,
                                inactiveTrackColor = ink.copy(alpha = 0.22f)
                            ),
                            modifier = Modifier.testTag("lyric_stage_weight_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "歌词字号档位",
                                style = MaterialTheme.typography.labelLarge,
                                color = ink
                            )
                            Text(
                                text = lyricFontScaleLabel(settings.fontScale),
                                style = MaterialTheme.typography.labelMedium,
                                color = accentInk
                            )
                        }
                        // Instant discrete 6-tier selector chips (zero slider lag!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LYRIC_FONT_SCALE_TIERS.forEachIndexed { idx, (tierScale, tierLabel) ->
                                val selected = idx == currentScaleIdx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) accentInk.copy(alpha = if (darkTheme) 0.28f else 0.16f)
                                            else pillBg
                                        )
                                        .border(
                                            0.75.dp,
                                            if (selected) accentInk else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            if (!selected) {
                                                onUpdateLyricSettings(settings.copy(fontScale = tierScale))
                                            }
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tierLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) accentInk else ink.copy(alpha = 0.78f),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BlurOn,
                                    contentDescription = null,
                                    tint = accentInk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "非本行渐进景深模糊",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ink
                                )
                            }
                            Switch(
                                checked = settings.enableLyricBlur,
                                onCheckedChange = {
                                    onUpdateLyricSettings(settings.copy(enableLyricBlur = it))
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Waves,
                                    contentDescription = null,
                                    tint = accentInk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "灵动弹性波浪",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ink
                                )
                            }
                            Switch(
                                checked = settings.enableElasticLyricWave,
                                onCheckedChange = {
                                    onUpdateLyricSettings(settings.copy(enableElasticLyricWave = it))
                                },
                                modifier = Modifier.testTag("lyric_stage_elastic_wave_switch")
                            )
                        }
                    }
                }
            }
        }

        if (document.isEmpty) {
            EmptyLyricsStage(
                darkTheme = darkTheme,
                onQuickOnlineMatch = onQuickOnlineMatch,
                onOpenLyricManager = onOpenLyricManager,
                modifier = Modifier.weight(1f)
            )
        } else {
            // Edge-faded Apple Music depth-of-field lyric scroller
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.11f to Color.Black,
                                0.84f to Color.Black,
                                1.0f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = if (isImmersive) 128.dp else 72.dp,
                        bottom = if (isImmersive) 260.dp else 185.dp,
                        start = 24.dp,
                        end = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isImmersive) 28.dp else 22.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Dynamic Instrumental Intro Slot (visible ONLY while playing before first synced lyric)
                    item(key = "intro_interlude_slot") {
                        val isIntroActive = activeInterlude != null && activeInterlude.afterLineIndex == -1
                        InstrumentalBreathingDots(
                            visible = isIntroActive,
                            interludeProgress = if (isIntroActive) activeInterlude!!.progressAt(effectivePosMs) else 0f,
                            isPlaying = isPlaying,
                            darkTheme = darkTheme,
                            slotTag = "intro"
                        )
                    }

                    itemsIndexed(document.lines, key = { idx, line -> "${idx}_${line.startMs}" }) { index, line ->
                        val isActive = index == effectiveActiveLineIndex
                        val distance = when {
                            activeInterlude != null -> {
                                val afterIdx = activeInterlude.afterLineIndex
                                if (afterIdx < 0) {
                                    index + 1
                                } else if (index <= afterIdx) {
                                    (afterIdx - index) + 1
                                } else {
                                    index - afterIdx
                                }
                            }
                            effectiveActiveLineIndex >= 0 -> abs(index - effectiveActiveLineIndex)
                            else -> 0
                        }
                        val isFollowingInterludeActive = activeInterlude != null && activeInterlude.afterLineIndex == index

                        Column(modifier = Modifier.fillMaxWidth()) {
                            LyricLineRow(
                                line = line,
                                lineIndex = index,
                                activeIndex = effectiveActiveLineIndex,
                                stepDirection = stepDirection,
                                isActive = isActive,
                                distanceFromActive = distance,
                                effectivePosMs = effectivePosMs,
                                fontScale = settings.fontScale,
                                fontWeightValue = settings.fontWeight,
                                showTranslation = settings.showTranslation,
                                enableProgressiveBlur = settings.enableLyricBlur && !isUserBrowsing,
                                enableElasticWave = settings.enableElasticLyricWave,
                                isUserBrowsing = isUserBrowsing,
                                darkTheme = darkTheme,
                                enableWordAnimation = line.words.isNotEmpty() &&
                                    (line.isWordSyncedNative || settings.enableSimulatedWordByWord),
                                onClick = {
                                    if (document.hasSyncedTimestamps) {
                                        isUserBrowsing = false
                                        onSeekTo((line.startMs - document.globalOffsetMs).coerceAtLeast(0L))
                                    }
                                }
                            )

                            // Dynamic Interlude Slot (expands ONLY when playback reaches this instrumental gap)
                            InstrumentalBreathingDots(
                                visible = isFollowingInterludeActive,
                                interludeProgress = if (isFollowingInterludeActive) {
                                    activeInterlude!!.progressAt(effectivePosMs)
                                } else {
                                    0f
                                },
                                isPlaying = isPlaying,
                                darkTheme = darkTheme,
                                slotTag = "after_$index"
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineRow(
    line: LyricLine,
    lineIndex: Int,
    activeIndex: Int,
    stepDirection: Int,
    isActive: Boolean,
    distanceFromActive: Int,
    effectivePosMs: Long,
    fontScale: Float,
    fontWeightValue: Int,
    showTranslation: Boolean,
    enableProgressiveBlur: Boolean,
    enableElasticWave: Boolean,
    isUserBrowsing: Boolean,
    darkTheme: Boolean,
    enableWordAnimation: Boolean,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val wavePhysics = remember(enableElasticWave, isActive, lineIndex, activeIndex, distanceFromActive) {
        resolveLyricWavePhysics(
            enableElasticWave = enableElasticWave,
            isActive = isActive,
            lineIndex = lineIndex,
            activeIndex = activeIndex,
            distanceFromActive = distanceFromActive
        )
    }

    // Staggered Apple Music cascade bounce & elastic vertical stretch when active lyric line advances
    val waveBouncePx = remember { Animatable(0f) }
    val waveStretchY = remember { Animatable(1f) }
    LaunchedEffect(activeIndex, enableElasticWave) {
        if (activeIndex >= 0 && !isUserBrowsing && stepDirection != 0 && distanceFromActive <= wavePhysics.maxDistance) {
            if (wavePhysics.staggerMs > 0L) delay(wavePhysics.staggerMs)
            val impulsePx = with(density) { (wavePhysics.impulseDp * stepDirection).dp.toPx() }
            waveBouncePx.snapTo(impulsePx)
            if (wavePhysics.stretchImpulseY > 1.001f) {
                waveStretchY.snapTo(wavePhysics.stretchImpulseY)
            } else {
                waveStretchY.snapTo(1f)
            }
            coroutineScope {
                launch {
                    waveBouncePx.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = wavePhysics.dampingRatio,
                            stiffness = wavePhysics.stiffness
                        )
                    )
                }
                if (wavePhysics.stretchImpulseY > 1.001f) {
                    launch {
                        waveStretchY.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = (wavePhysics.dampingRatio + 0.04f).coerceAtMost(0.65f),
                                stiffness = wavePhysics.stiffness * 1.08f
                            )
                        )
                    }
                }
            }
        } else {
            waveBouncePx.snapTo(0f)
            waveStretchY.snapTo(1f)
        }
    }
    LaunchedEffect(isUserBrowsing) {
        if (isUserBrowsing) {
            coroutineScope {
                if (waveBouncePx.value != 0f) {
                    launch { waveBouncePx.animateTo(0f, tween(150)) }
                }
                if (waveStretchY.value != 1f) {
                    launch { waveStretchY.animateTo(1f, tween(150)) }
                }
            }
        }
    }

    // Buoyancy lift for the currently active line (more expressive in Elastic Wave mode)
    val activeLiftDp by animateDpAsState(
        targetValue = if (isActive) wavePhysics.activeLiftDp.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = if (enableElasticWave) 0.48f else 0.62f,
            stiffness = if (enableElasticWave) 200f else 230f
        ),
        label = "lyric_active_lift"
    )
    val activeLiftPx = with(density) { activeLiftDp.toPx() }
    // Progressive Apple Music opacity falloff (brightens while manually browsing)
    val targetAlpha = when {
        isActive -> 1.0f
        isUserBrowsing -> 0.80f
        distanceFromActive == 1 -> 0.56f
        distanceFromActive == 2 -> 0.40f
        distanceFromActive == 3 -> 0.28f
        else -> 0.19f
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 420),
        label = "lyric_alpha"
    )

    // Progressive scale-down on non-active lines (anchored left so alignment stays editorial & crisp)
    val targetScale = when {
        isActive -> wavePhysics.activeScale
        isUserBrowsing -> 0.90f
        distanceFromActive == 1 -> 0.85f
        distanceFromActive == 2 -> 0.82f
        distanceFromActive == 3 -> 0.79f
        else -> 0.76f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = if (enableElasticWave) 0.48f else 0.64f,
            stiffness = if (enableElasticWave) 205f else 230f
        ),
        label = "lyric_scale"
    )

    // Progressive Apple Music depth-of-field Gaussian blur (Android 12+ / API 31+)
    val targetBlurDp = if (!enableProgressiveBlur || isActive) {
        0.dp
    } else {
        when (distanceFromActive) {
            1 -> 1.8.dp
            2 -> 3.8.dp
            3 -> 5.8.dp
            else -> 7.6.dp
        }
    }
    val animatedBlurDp by animateDpAsState(
        targetValue = targetBlurDp,
        animationSpec = tween(durationMillis = 460),
        label = "lyric_blur"
    )

    val fontSizeSp = (27f * fontScale).sp
    val lineHeightSp = (36f * fontScale).sp
    val activeFontWeight = FontWeight(fontWeightValue.coerceIn(300, 900))
    val inactiveFontWeight = FontWeight((fontWeightValue - 100).coerceIn(300, 900))

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && animatedBlurDp > 0.1.dp) {
        Modifier.blur(radius = animatedBlurDp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier
    }

    val baseInk = if (darkTheme) Color.White else Color(0xFF151521)
    val activeShadow = if (isActive) {
        Shadow(
            color = if (darkTheme) Color.White.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.10f),
            offset = Offset.Zero,
            blurRadius = if (darkTheme) 18f else 8f
        )
    } else {
        Shadow.None
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .then(blurModifier)
            .graphicsLayer {
                translationY = waveBouncePx.value + activeLiftPx
                scaleX = animatedScale
                scaleY = animatedScale * waveStretchY.value
                transformOrigin = TransformOrigin(0f, 0.5f)
                alpha = animatedAlpha
            }
    ) {
        if (isActive && enableWordAnimation && line.words.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                line.words.forEach { word ->
                    KaraokeWordToken(
                        word = word,
                        effectivePosMs = effectivePosMs,
                        fontSizeSp = fontSizeSp.value,
                        fontWeight = activeFontWeight,
                        baseInk = baseInk,
                        glowShadow = activeShadow
                    )
                }
            }
        } else {
            val textColor by animateColorAsState(
                targetValue = if (isActive) baseInk else baseInk.copy(alpha = 0.88f),
                animationSpec = tween(280),
                label = "line_color"
            )
            Text(
                text = line.text,
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (isActive) activeFontWeight else inactiveFontWeight,
                fontSize = fontSizeSp,
                lineHeight = lineHeightSp,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (isActive) activeFontWeight else inactiveFontWeight,
                    shadow = activeShadow
                ),
                color = textColor
            )
        }

        if (showTranslation && !line.translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = line.translation,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight((fontWeightValue - 200).coerceIn(300, 700)),
                fontSize = (15f * fontScale).sp,
                lineHeight = (22f * fontScale).sp,
                color = baseInk.copy(alpha = if (isActive) 0.80f else 0.52f)
            )
        }
    }
}

@Composable
private fun KaraokeWordToken(
    word: LyricWord,
    effectivePosMs: Long,
    fontSizeSp: Float,
    fontWeight: FontWeight,
    baseInk: Color,
    glowShadow: Shadow
) {
    val progress = when {
        effectivePosMs <= word.startMs -> 0f
        effectivePosMs >= word.endMs -> 1f
        else -> (effectivePosMs - word.startMs).toFloat() / word.durationMs.coerceAtLeast(1L).toFloat()
    }.coerceIn(0f, 1f)

    // Buoyant upward float while a syllable/word is actively sung
    val floatLift by animateDpAsState(
        targetValue = if (progress in 0.02f..0.98f) (-2.8).dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "word_lift"
    )

    val wordBrush = if (progress <= 0f) {
        Brush.horizontalGradient(
            listOf(baseInk.copy(alpha = 0.36f), baseInk.copy(alpha = 0.36f))
        )
    } else if (progress >= 1f) {
        Brush.horizontalGradient(listOf(baseInk, baseInk))
    } else {
        val feather = (progress + 0.16f).coerceAtMost(1f)
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to baseInk,
                progress to baseInk,
                feather to baseInk.copy(alpha = 0.36f),
                1f to baseInk.copy(alpha = 0.36f)
            )
        )
    }

    Text(
        text = word.text,
        fontFamily = FontFamily.SansSerif,
        fontWeight = fontWeight,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.35f).sp,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = fontWeight,
            brush = wordBrush,
            shadow = if (progress > 0.05f) glowShadow else Shadow.None
        ),
        modifier = Modifier.offset(y = floatLift)
    )
}

@Composable
private fun EmptyLyricsStage(
    darkTheme: Boolean,
    onQuickOnlineMatch: () -> Unit,
    onOpenLyricManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = if (darkTheme) Color.White else Color(0xFF151521)
    val subInk = if (darkTheme) Color.White.copy(alpha = 0.68f) else Color(0xFF56566C)
    val primaryBtnBg = if (darkTheme) Color.White else Color(0xFF151521)
    val primaryBtnInk = if (darkTheme) Color(0xFF111118) else Color.White

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ink.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lyrics,
                contentDescription = null,
                tint = ink.copy(alpha = 0.8f),
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "暂未发现内嵌或本地同名歌词",
            style = MaterialTheme.typography.titleLarge,
            color = ink,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "支持网易云 / QQ音乐 / 酷狗 / LRCLIB 多源一键匹配，或导入 .qrc / .yrc / .krc / .lrc / .ttml 逐字歌词",
            style = MaterialTheme.typography.bodyMedium,
            color = subInk,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onQuickOnlineMatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryBtnBg,
                    contentColor = primaryBtnInk
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("empty_lyrics_smart_match")
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("多源智能匹配", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onOpenLyricManager,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("empty_lyrics_import")
            ) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("导入歌词")
            }
        }
    }
}
