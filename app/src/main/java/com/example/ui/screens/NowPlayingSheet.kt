package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.example.R
import com.example.model.AuraPalette
import com.example.model.LyricDocument
import com.example.model.LyricSettings
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.model.formatDuration
import com.example.playback.PlaybackState
import com.example.ui.components.HeartBurstIconButton
import com.example.ui.components.LiveEqualizerBars
import com.example.ui.components.LyricStageView
import com.example.ui.components.MeshAuraBackground
import com.example.ui.components.QualityBadgePill
import com.example.ui.components.TrackArtwork
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChampagneGold
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NowPlayingStage {
    COVER,
    LYRICS,
    QUEUE
}

private enum class CoverDragAxis {
    UNDECIDED,
    HORIZONTAL,
    VERTICAL
}

@Composable
fun NowPlayingSheet(
    playbackState: PlaybackState,
    auraPalette: AuraPalette,
    lyricDocument: LyricDocument,
    lyricSettings: LyricSettings,
    eqEnabled: Boolean,
    darkTheme: Boolean = true,
    artworkAlpha: Float = 1f,
    onActiveArtworkBoundsChanged: (Rect, Dp) -> Unit = { _, _ -> },
    onStageChanged: (NowPlayingStage) -> Unit = {},
    onDragCollapseStart: () -> Unit = {},
    onDragCollapseDelta: (Float) -> Unit = {},
    onDragCollapseEnd: (Float) -> Unit = {},
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleAbLoop: () -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onJumpQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onMoveQueueIndexToNext: (Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onToggleSimulatedWordByWord: (Boolean) -> Unit,
    onUpdateLyricSettings: (LyricSettings) -> Unit,
    onOpenLyricManager: () -> Unit,
    onQuickOnlineLyricMatch: (Track) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onScrubHaptic: () -> Unit
) {
    val track = playbackState.currentTrack ?: return
    var stage by remember { mutableStateOf(NowPlayingStage.COVER) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var lastSkipDirection by remember { mutableIntStateOf(1) }

    val skipPrevKick = remember { Animatable(0f) }
    val skipNextKick = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Continuous interruptible Cover <-> Compact Header (Lyrics/Queue) artwork morph state
    val compactArtProgress = remember { Animatable(0f) }
    var sheetRootOffset by remember { mutableStateOf(Offset.Zero) }
    var coverArtRectRoot by remember { mutableStateOf(Rect.Zero) }
    var headerMiniArtRectRoot by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(stage) {
        onStageChanged(stage)
        compactArtProgress.animateTo(
            targetValue = if (stage == NowPlayingStage.COVER) 0f else 1f,
            animationSpec = spring(
                dampingRatio = 0.80f,
                stiffness = 320f
            )
        )
    }

    val morphT = compactArtProgress.value.coerceIn(0f, 1f)
    val isInternalMorphing = morphT in 0.005f..0.995f &&
        !coverArtRectRoot.isEmpty &&
        !headerMiniArtRectRoot.isEmpty

    // Report exact active artwork rect (Cover large rect, Lyrics/Queue small header rect, or live mid-morph rect)
    LaunchedEffect(morphT, coverArtRectRoot, headerMiniArtRectRoot, stage) {
        val hasCover = !coverArtRectRoot.isEmpty
        val hasHeader = !headerMiniArtRectRoot.isEmpty
        when {
            morphT <= 0.005f && hasCover -> onActiveArtworkBoundsChanged(coverArtRectRoot, 24.dp)
            morphT >= 0.995f && hasHeader -> onActiveArtworkBoundsChanged(headerMiniArtRectRoot, 9.dp)
            hasCover && hasHeader -> {
                val r = Rect(
                    left = lerp(coverArtRectRoot.left, headerMiniArtRectRoot.left, morphT),
                    top = lerp(coverArtRectRoot.top, headerMiniArtRectRoot.top, morphT),
                    right = lerp(coverArtRectRoot.right, headerMiniArtRectRoot.right, morphT),
                    bottom = lerp(coverArtRectRoot.bottom, headerMiniArtRectRoot.bottom, morphT)
                )
                onActiveArtworkBoundsChanged(r, lerp(24.dp, 9.dp, morphT))
            }
            hasCover -> onActiveArtworkBoundsChanged(coverArtRectRoot, 24.dp)
            hasHeader -> onActiveArtworkBoundsChanged(headerMiniArtRectRoot, 9.dp)
        }
    }

    val isImmersiveLyrics = stage == NowPlayingStage.LYRICS &&
        lyricSettings.enableImmersiveLyrics &&
        !lyricDocument.isEmpty

    // Full Dark & Light Mode adaptive ink palette
    val backdropColor = if (darkTheme) Color(0xFF09090D) else Color(0xFFF6F5FA)
    val ink = if (darkTheme) Color.White else Color(0xFF151521)
    val subInk = if (darkTheme) Color.White.copy(alpha = 0.68f) else Color(0xFF55556A)
    val mutedInk = if (darkTheme) Color.White.copy(alpha = 0.44f) else Color(0xFF84849A)
    val pillBg = if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    val pillActiveBg = if (darkTheme) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.12f)
    val pillBorder = if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)
    val accentGold = if (darkTheme) ChampagneGold else Color(0xFFC27B1A)
    val playBtnBg = if (darkTheme) Color.White else Color(0xFF151521)
    val playBtnIconTint = if (darkTheme) Color(0xFF0E0E14) else Color.White

    BackHandler {
        when {
            isImmersiveLyrics -> onUpdateLyricSettings(lyricSettings.copy(enableImmersiveLyrics = false))
            stage != NowPlayingStage.COVER -> stage = NowPlayingStage.COVER
            else -> onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .onGloballyPositioned { coords ->
                sheetRootOffset = coords.positionInRoot()
            }
    ) {
        // Living 4-color cover-driven fluid mesh aura (adaptive for Light & Dark mode)
        MeshAuraBackground(
            palette = auraPalette,
            isPlaying = playbackState.isPlaying,
            darkTheme = darkTheme,
            intensity = when {
                isImmersiveLyrics -> 0.94f
                stage == NowPlayingStage.LYRICS -> 0.85f
                else -> 0.78f
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(bottom = if (isImmersiveLyrics) 0.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Drag Handle + Adaptive Header (hidden cleanly in Immersive Lyrics mode)
            AnimatedVisibility(
                visible = !isImmersiveLyrics,
                enter = expandVertically() + fadeIn(tween(220)),
                exit = shrinkVertically() + fadeOut(tween(180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var dragging = false
                                var accumY = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (dragging) {
                                            onDragCollapseEnd(tracker.calculateVelocity().y)
                                        }
                                        break
                                    }
                                    val d = change.positionChange()
                                    if (d.y != 0f) {
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        accumY += d.y
                                        if (!dragging && abs(accumY) > 10f) {
                                            dragging = true
                                            onDragCollapseStart()
                                        }
                                        if (dragging) {
                                            change.consume()
                                            onDragCollapseDelta(d.y)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(44.dp)
                            .height(4.5.dp)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = if (darkTheme) 0.36f else 0.22f))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_now_playing")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.cd_close_player),
                                tint = ink.copy(alpha = 0.9f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Always keep the compact header artwork anchor laid out so Cover -> Lyrics morph has exact target rect from frame 1!
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Compact Lyrics/Queue header row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = stage != NowPlayingStage.COVER) {
                                        stage = NowPlayingStage.COVER
                                    }
                            ) {
                                val showRestingMiniArt = stage != NowPlayingStage.COVER && !isInternalMorphing
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .onGloballyPositioned { coords ->
                                            val r = coords.boundsInRoot()
                                            if (!r.isEmpty) headerMiniArtRectRoot = r
                                        }
                                        .alpha(if (showRestingMiniArt) artworkAlpha else 0f)
                                ) {
                                    TrackArtwork(
                                        artworkPath = track.artworkPath,
                                        albumArtUri = track.albumArtUri,
                                        contentDescription = stringResource(R.string.cd_album_art),
                                        cornerRadius = 9.dp,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .graphicsLayer { alpha = morphT }
                                ) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = subInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Cover stage centered album/spec header
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { alpha = (1f - morphT * 1.4f).coerceIn(0f, 1f) }
                            ) {
                                Text(
                                    text = track.album.ifBlank { "正在播放" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ink.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.technicalSpecLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subInk,
                                    maxLines = 1
                                )
                            }
                        }

                        // AI Studio quick button in top right
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(pillBg)
                                .bouncyClickable(onClick = onOpenAiStudio)
                                .testTag("now_playing_ai_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(R.string.cd_open_ai_studio),
                                tint = accentGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Main Animated Stage: Cover / Word-by-Word Lyrics / Up Next Queue
            AnimatedContent(
                targetState = stage,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "now_playing_stage"
            ) { activeStage ->
                when (activeStage) {
                    NowPlayingStage.COVER -> CoverArtworkStage(
                        track = track,
                        previousTrack = playbackState.previousTrack,
                        nextTrack = playbackState.nextTrack,
                        isPlaying = playbackState.isPlaying,
                        auraPalette = auraPalette,
                        lyricDocument = lyricDocument,
                        positionMs = playbackState.positionMs,
                        darkTheme = darkTheme,
                        artworkAlpha = if (isInternalMorphing) 0f else artworkAlpha,
                        skipDirection = lastSkipDirection,
                        onFullArtworkBoundsChanged = { rect ->
                            if (!rect.isEmpty) coverArtRectRoot = rect
                        },
                        onDragCollapseStart = onDragCollapseStart,
                        onDragCollapseDelta = onDragCollapseDelta,
                        onDragCollapseEnd = onDragCollapseEnd,
                        onSwipeNext = {
                            lastSkipDirection = 1
                            onSkipNext()
                        },
                        onSwipePrevious = {
                            lastSkipDirection = -1
                            onSkipPrevious()
                        },
                        onTapLyricsPreview = { stage = NowPlayingStage.LYRICS }
                    )

                    NowPlayingStage.LYRICS -> LyricStageView(
                        document = lyricDocument,
                        positionMs = playbackState.positionMs,
                        settings = lyricSettings,
                        isImmersive = isImmersiveLyrics,
                        darkTheme = darkTheme,
                        onSeekTo = onSeekTo,
                        onToggleSimulatedWordByWord = onToggleSimulatedWordByWord,
                        onUpdateLyricSettings = onUpdateLyricSettings,
                        onOpenLyricManager = onOpenLyricManager,
                        onQuickOnlineMatch = { onQuickOnlineLyricMatch(track) }
                    )

                    NowPlayingStage.QUEUE -> QueueStageView(
                        queue = playbackState.queue,
                        currentIndex = playbackState.currentIndex,
                        isPlaying = playbackState.isPlaying,
                        darkTheme = darkTheme,
                        onSelectIndex = onJumpQueueIndex,
                        onRemoveIndex = onRemoveQueueIndex,
                        onMoveToNext = onMoveQueueIndexToNext,
                        onClearUpcoming = onClearUpcomingQueue,
                        onSaveAsPlaylist = onSaveQueueAsPlaylist
                    )
                }
            }

            // Track Info + Favorite + Quick Studio Pills (smoothly collapses during Lyrics/Queue)
            AnimatedVisibility(
                visible = stage == NowPlayingStage.COVER,
                enter = expandVertically(tween(240)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = track,
                            transitionSpec = {
                                val dir = lastSkipDirection
                                (slideInHorizontally { w -> dir * (w / 4) } + fadeIn()) togetherWith
                                    (slideOutHorizontally { w -> -dir * (w / 4) } + fadeOut())
                            },
                            modifier = Modifier.weight(1f),
                            label = "now_playing_title_anim"
                        ) { t ->
                            Column {
                                Text(
                                    text = t.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = t.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = subInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HeartBurstIconButton(
                            isFavorite = track.isFavorite,
                            contentDescription = stringResource(R.string.cd_favorite),
                            inactiveTint = ink.copy(alpha = 0.78f),
                            onClick = { onToggleFavorite(track) },
                            modifier = Modifier.testTag("now_playing_favorite")
                        )
                    }

                    // Pro Audiophile Utility Pills: Speed, A-B Loop, Sleep Timer, Quality Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QualityBadgePill(
                            label = track.qualityBadge,
                            isHiRes = track.isHiRes,
                            isLossless = track.isLossless
                        )

                        Box {
                            MiniStudioPill(
                                text = if (playbackState.playbackSpeed == 1.0f) "1.0x" else "${playbackState.playbackSpeed}x",
                                active = playbackState.playbackSpeed != 1.0f,
                                darkTheme = darkTheme,
                                onClick = { showSpeedMenu = true }
                            )
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                listOf(0.75f, 0.9f, 1.0f, 1.15f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                    DropdownMenuItem(
                                        text = { Text("${spd}x 速率") },
                                        onClick = {
                                            onSetSpeed(spd)
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        val abText = when {
                            playbackState.abLoopStartMs != null && playbackState.abLoopEndMs != null -> "A-B 循环中"
                            playbackState.abLoopStartMs != null -> "已设 A 点"
                            else -> "A-B 复读"
                        }
                        MiniStudioPill(
                            text = abText,
                            active = playbackState.abLoopStartMs != null,
                            darkTheme = darkTheme,
                            onClick = onToggleAbLoop
                        )

                        Box {
                            val sleepLabel = if (playbackState.sleepTimerRemainingMs > 0L) {
                                "定时 ${playbackState.sleepTimerRemainingMs.formatDuration()}"
                            } else {
                                "定时"
                            }
                            MiniStudioPill(
                                text = sleepLabel,
                                active = playbackState.sleepTimerRemainingMs > 0L,
                                darkTheme = darkTheme,
                                onClick = { showSleepMenu = true }
                            )
                            DropdownMenu(
                                expanded = showSleepMenu,
                                onDismissRequest = { showSleepMenu = false }
                            ) {
                                listOf(0 to "关闭定时", 15 to "15 分钟后淡出", 30 to "30 分钟后淡出", 45 to "45 分钟后淡出", 60 to "60 分钟后淡出")
                                    .forEach { (mins, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                onStartSleepTimer(mins)
                                                showSleepMenu = false
                                            }
                                        )
                                    }
                            }
                        }
                    }
                }
            }

            // Bottom controls (hidden cleanly during Immersive Lyrics mode)
            AnimatedVisibility(
                visible = !isImmersiveLyrics,
                enter = expandVertically() + fadeIn(tween(220)),
                exit = shrinkVertically() + fadeOut(tween(180))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ElasticScrubber(
                        positionMs = playbackState.positionMs,
                        durationMs = playbackState.durationMs.coerceAtLeast(track.durationMs),
                        darkTheme = darkTheme,
                        onSeekTo = onSeekTo,
                        onScrubHaptic = onScrubHaptic,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .bouncyClickable(onClick = onToggleShuffle)
                                .testTag("transport_shuffle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = stringResource(R.string.cd_shuffle),
                                tint = if (playbackState.shuffleEnabled) ink else mutedInk,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .bouncyClickable(pressedScale = 0.86f) {
                                    lastSkipDirection = -1
                                    onSkipPrevious()
                                    scope.launch {
                                        skipPrevKick.snapTo(-10f)
                                        skipPrevKick.animateTo(
                                            0f,
                                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                }
                                .testTag("transport_previous"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.cd_previous_track),
                                tint = ink,
                                modifier = Modifier
                                    .size(36.dp)
                                    .offset { IntOffset(skipPrevKick.value.roundToInt(), 0) }
                            )
                        }

                        val playButtonScale by animateFloatAsState(
                            targetValue = if (playbackState.isPlaying) 1.0f else 0.93f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "play_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .scale(playButtonScale)
                                .shadow(18.dp, CircleShape, spotColor = Color(auraPalette.primaryHex).copy(alpha = 0.36f))
                                .clip(CircleShape)
                                .background(playBtnBg)
                                .bouncyClickable(pressedScale = 0.88f, onClick = onTogglePlayPause)
                                .testTag("transport_play_pause"),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = playbackState.isPlaying,
                                transitionSpec = {
                                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                                        (scaleOut(tween(120)) + fadeOut(tween(120)))
                                },
                                label = "play_pause_morph"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play_pause),
                                    tint = playBtnIconTint,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .bouncyClickable(pressedScale = 0.86f) {
                                    lastSkipDirection = 1
                                    onSkipNext()
                                    scope.launch {
                                        skipNextKick.snapTo(10f)
                                        skipNextKick.animateTo(
                                            0f,
                                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                }
                                .testTag("transport_next"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.cd_next_track),
                                tint = ink,
                                modifier = Modifier
                                    .size(36.dp)
                                    .offset { IntOffset(skipNextKick.value.roundToInt(), 0) }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .bouncyClickable(onClick = onCycleRepeat)
                                .testTag("transport_repeat"),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = if (playbackState.repeatMode == RepeatMode.ONE) {
                                Icons.Rounded.RepeatOne
                            } else {
                                Icons.Rounded.Repeat
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = stringResource(R.string.cd_repeat),
                                tint = if (playbackState.repeatMode != RepeatMode.OFF) ink else mutedInk,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Bottom Apple Music-style Utility Action Deck
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DeckToggleIcon(
                            icon = Icons.Rounded.Lyrics,
                            contentDescription = stringResource(R.string.cd_toggle_lyrics),
                            selected = stage == NowPlayingStage.LYRICS,
                            darkTheme = darkTheme,
                            onClick = {
                                stage = if (stage == NowPlayingStage.LYRICS) NowPlayingStage.COVER else NowPlayingStage.LYRICS
                            },
                            testTag = "deck_lyrics_toggle"
                        )
                        DeckToggleIcon(
                            icon = Icons.Rounded.Equalizer,
                            contentDescription = stringResource(R.string.cd_open_equalizer),
                            selected = eqEnabled,
                            darkTheme = darkTheme,
                            onClick = onOpenEqualizer,
                            testTag = "deck_eq_button"
                        )
                        DeckToggleIcon(
                            icon = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_open_ai_studio),
                            selected = false,
                            darkTheme = darkTheme,
                            tintOverride = accentGold,
                            onClick = onOpenAiStudio,
                            testTag = "deck_ai_button"
                        )
                        DeckToggleIcon(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.cd_toggle_queue),
                            selected = stage == NowPlayingStage.QUEUE,
                            darkTheme = darkTheme,
                            onClick = {
                                stage = if (stage == NowPlayingStage.QUEUE) NowPlayingStage.COVER else NowPlayingStage.QUEUE
                            },
                            testTag = "deck_queue_toggle"
                        )
                    }
                }
            }
        }

        // Continuous interruptible Cover <-> Top Small Cover floating transition overlay!
        if (isInternalMorphing && artworkAlpha > 0.01f) {
            val localLeft = lerp(coverArtRectRoot.left, headerMiniArtRectRoot.left, morphT) - sheetRootOffset.x
            val localTop = lerp(coverArtRectRoot.top, headerMiniArtRectRoot.top, morphT) - sheetRootOffset.y
            val curWidth = lerp(coverArtRectRoot.width, headerMiniArtRectRoot.width, morphT).coerceAtLeast(1f)
            val curHeight = lerp(coverArtRectRoot.height, headerMiniArtRectRoot.height, morphT).coerceAtLeast(1f)
            val cornerDp = lerp(24.dp, 9.dp, morphT)
            val elevDp = lerp(26.dp, 6.dp, morphT)

            Box(
                modifier = Modifier
                    .offset { IntOffset(localLeft.roundToInt(), localTop.roundToInt()) }
                    .size(
                        width = with(density) { curWidth.toDp() },
                        height = with(density) { curHeight.toDp() }
                    )
                    .alpha(artworkAlpha)
                    .shadow(
                        elevation = elevDp,
                        shape = RoundedCornerShape(cornerDp),
                        ambientColor = Color(auraPalette.primaryHex).copy(alpha = lerp(0.45f, 0.18f, morphT)),
                        spotColor = Color(auraPalette.primaryHex).copy(alpha = lerp(0.55f, 0.22f, morphT))
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

        // Low-profile floating Play/Pause + Exit Immersive pill when Immersive Lyrics is active
        AnimatedVisibility(
            visible = isImmersiveLyrics,
            enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (darkTheme) Color.Black.copy(alpha = 0.44f) else Color.White.copy(alpha = 0.88f))
                    .border(0.7.dp, pillBorder, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(pillActiveBg)
                        .bouncyClickable(pressedScale = 0.88f, onClick = onTogglePlayPause)
                        .testTag("immersive_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentGold.copy(alpha = if (darkTheme) 0.22f else 0.14f))
                        .bouncyClickable(pressedScale = 0.92f) {
                            onUpdateLyricSettings(lyricSettings.copy(enableImmersiveLyrics = false))
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("immersive_exit_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FullscreenExit,
                        contentDescription = "退出沉浸歌词",
                        tint = accentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "退出沉浸",
                        style = MaterialTheme.typography.labelMedium,
                        color = ink
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverArtworkStage(
    track: Track,
    previousTrack: Track?,
    nextTrack: Track?,
    isPlaying: Boolean,
    auraPalette: AuraPalette,
    lyricDocument: LyricDocument,
    positionMs: Long,
    darkTheme: Boolean,
    artworkAlpha: Float,
    skipDirection: Int,
    onFullArtworkBoundsChanged: (Rect) -> Unit,
    onDragCollapseStart: () -> Unit,
    onDragCollapseDelta: (Float) -> Unit,
    onDragCollapseEnd: (Float) -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    onTapLyricsPreview: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeSkipThresholdPx = with(density) { 84.dp.toPx() }
    val touchSlopPx = with(density) { 10.dp.toPx() }
    val coverDragX = remember { Animatable(0f) }

    val ink = if (darkTheme) Color.White else Color(0xFF151521)
    val pillBg = if (darkTheme) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)
    val pillBorder = if (darkTheme) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    val swipePreviewBg = if (darkTheme) Color(0xFF14141F).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.94f)
    val accent = Color(auraPalette.primaryHex)

    // Apple Music breathing album cover scale: smaller when paused, lush full-size with bloom when playing
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cover_breath_scale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 10.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cover_shadow"
    )

    val activeLyricSnippet = remember(lyricDocument.lines, positionMs, lyricDocument.globalOffsetMs) {
        if (!lyricDocument.hasSyncedTimestamps) null
        else {
            val pos = positionMs + lyricDocument.globalOffsetMs
            lyricDocument.lines.lastOrNull { pos >= it.startMs }?.text
        }
    }

    val dragFraction = (coverDragX.value / swipeSkipThresholdPx).coerceIn(-1.4f, 1.4f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var axis = CoverDragAxis.UNDECIDED
                    var totalDx = 0f
                    var totalDy = 0f
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    scope.launch { coverDragX.stop() }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            val vel = tracker.calculateVelocity()
                            when (axis) {
                                CoverDragAxis.VERTICAL -> onDragCollapseEnd(vel.y)
                                CoverDragAxis.HORIZONTAL -> {
                                    val dx = coverDragX.value
                                    val goNext = dx < -swipeSkipThresholdPx || vel.x < -700f
                                    val goPrev = dx > swipeSkipThresholdPx || vel.x > 700f
                                    scope.launch {
                                        if (goNext) {
                                            onSwipeNext()
                                        } else if (goPrev) {
                                            onSwipePrevious()
                                        }
                                        coverDragX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                                CoverDragAxis.UNDECIDED -> Unit
                            }
                            break
                        }

                        val delta = change.positionChange()
                        if (delta.x != 0f || delta.y != 0f) {
                            tracker.addPosition(change.uptimeMillis, change.position)
                            totalDx += delta.x
                            totalDy += delta.y

                            if (axis == CoverDragAxis.UNDECIDED) {
                                if (abs(totalDy) > touchSlopPx && abs(totalDy) > abs(totalDx) * 1.15f) {
                                    axis = CoverDragAxis.VERTICAL
                                    onDragCollapseStart()
                                } else if (abs(totalDx) > touchSlopPx && abs(totalDx) >= abs(totalDy)) {
                                    axis = CoverDragAxis.HORIZONTAL
                                }
                            }

                            when (axis) {
                                CoverDragAxis.VERTICAL -> {
                                    change.consume()
                                    onDragCollapseDelta(delta.y)
                                }
                                CoverDragAxis.HORIZONTAL -> {
                                    change.consume()
                                    val nextX = (coverDragX.value + delta.x * 0.9f)
                                        .coerceIn(-swipeSkipThresholdPx * 1.6f, swipeSkipThresholdPx * 1.6f)
                                    scope.launch { coverDragX.snapTo(nextX) }
                                }
                                CoverDragAxis.UNDECIDED -> Unit
                            }
                        }
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    val dir = skipDirection
                    (slideInHorizontally(
                        initialOffsetX = { w -> dir * (w / 2) },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + scaleIn(initialScale = 0.88f) + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { w -> -dir * (w / 2) },
                            animationSpec = tween(220)
                        ) + scaleOut(targetScale = 0.88f) + fadeOut(tween(180)))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(artworkAlpha),
                label = "cover_track_carousel"
            ) { activeTrack ->
                val interactiveScale = coverScale * (1f - abs(dragFraction) * 0.06f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(coverDragX.value.roundToInt(), 0) }
                        .graphicsLayer {
                            scaleX = interactiveScale
                            scaleY = interactiveScale
                            rotationZ = dragFraction * 4.5f
                        }
                        // Measure the ACTUAL scaled visual bounds so expand/collapse morph lands pixel-exact with ZERO jump!
                        .onGloballyPositioned { coords ->
                            val r = coords.boundsInRoot()
                            if (!r.isEmpty && abs(coverDragX.value) < 1f) {
                                onFullArtworkBoundsChanged(r)
                            }
                        }
                        .shadow(
                            elevation = shadowElevation,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = Color(auraPalette.primaryHex).copy(alpha = if (darkTheme) 0.50f else 0.25f),
                            spotColor = Color(auraPalette.primaryHex).copy(alpha = if (darkTheme) 0.60f else 0.30f)
                        )
                ) {
                    TrackArtwork(
                        artworkPath = activeTrack.artworkPath,
                        albumArtUri = activeTrack.albumArtUri,
                        contentDescription = stringResource(R.string.cd_album_art),
                        cornerRadius = 24.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Directional swipe preview pill displaying the actual previous/next song title & artist
            if (abs(dragFraction) > 0.14f) {
                val isPrev = dragFraction > 0f
                val targetTrack = if (isPrev) previousTrack else nextTrack
                val ready = abs(dragFraction) >= 0.92f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(if (isPrev) Alignment.CenterStart else Alignment.CenterEnd)
                        .alpha(((abs(dragFraction) - 0.10f) * 1.4f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(18.dp))
                        .background(swipePreviewBg)
                        .border(
                            width = if (ready) 1.1.dp else 0.75.dp,
                            color = if (ready) accent else pillBorder,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (isPrev) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(
                        horizontalAlignment = if (isPrev) Alignment.Start else Alignment.End,
                        modifier = Modifier.widthIn(max = 176.dp)
                    ) {
                        Text(
                            text = if (isPrev) "上一首" else "下一首",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = targetTrack?.title ?: "当前曲目",
                            style = MaterialTheme.typography.labelLarge,
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (targetTrack != null) {
                            Text(
                                text = targetTrack.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = ink.copy(alpha = 0.62f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (!isPrev) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Live 1-line translucent lyric preview pill — tap to dive into full word-by-word stage
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(pillBg)
                .border(0.6.dp, pillBorder, RoundedCornerShape(50))
                .bouncyClickable(pressedScale = 0.95f, onClick = onTapLyricsPreview)
                .padding(horizontal = 16.dp, vertical = 7.dp)
                .testTag("cover_lyric_preview_pill")
        ) {
            Text(
                text = activeLyricSnippet ?: "左右滑封面切歌 · 轻触开启逐字歌词舞台",
                style = MaterialTheme.typography.bodyMedium,
                color = ink.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueueStageView(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    darkTheme: Boolean,
    onSelectIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onMoveToNext: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onSaveAsPlaylist: (String) -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var savePlaylistName by remember { mutableStateOf("") }

    val ink = if (darkTheme) Color.White else Color(0xFF151521)
    val subInk = if (darkTheme) Color.White.copy(alpha = 0.58f) else Color(0xFF5A5A70)
    val pillBg = if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    val rowBg = if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.56f)
    val rowActiveBg = if (darkTheme) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.92f)
    val accentGold = if (darkTheme) ChampagneGold else Color(0xFFC27B1A)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "待播队列 · ${queue.size} 首",
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = 0.92f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (queue.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillBg)
                            .clickable {
                                val firstTitle = queue.getOrNull(currentIndex)?.title ?: "队列精选"
                                savePlaylistName = "队列 · $firstTitle"
                                showSaveDialog = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("queue_save_playlist")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "存为歌单",
                            tint = accentGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "存为歌单",
                            style = MaterialTheme.typography.labelSmall,
                            color = ink
                        )
                    }
                }
                if (queue.size > 1 && currentIndex < queue.lastIndex) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillBg)
                            .clickable(onClick = onClearUpcoming)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("queue_clear_upcoming")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "清空待播",
                            tint = ink.copy(alpha = 0.82f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "清空待播",
                            style = MaterialTheme.typography.labelSmall,
                            color = ink.copy(alpha = 0.82f)
                        )
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(queue, key = { idx, t -> "${idx}_${t.id}" }) { index, item ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCurrent) rowActiveBg else rowBg)
                        .clickable { onSelectIndex(index) }
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(
                        artworkPath = item.artworkPath,
                        albumArtUri = item.albumArtUri,
                        contentDescription = item.title,
                        cornerRadius = 10.dp,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isCurrent) ink else ink.copy(alpha = 0.88f),
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.artist} · ${item.durationMs.formatDuration()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = subInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isCurrent) {
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            LiveEqualizerBars(isPlaying = isPlaying, color = ink)
                        }
                    } else if (index != currentIndex + 1) {
                        IconButton(
                            onClick = { onMoveToNext(index) },
                            modifier = Modifier.testTag("queue_move_next_$index")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = stringResource(R.string.cd_move_to_next),
                                tint = accentGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onRemoveIndex(index) },
                        modifier = Modifier.testTag("queue_remove_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_remove_from_queue),
                            tint = subInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("将当前待播队列存为歌单") },
            text = {
                OutlinedTextField(
                    value = savePlaylistName,
                    onValueChange = { savePlaylistName = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (savePlaylistName.isNotBlank()) {
                            onSaveAsPlaylist(savePlaylistName)
                            showSaveDialog = false
                        }
                    },
                    enabled = savePlaylistName.isNotBlank()
                ) {
                    Text("保存歌单")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ElasticScrubber(
    positionMs: Long,
    durationMs: Long,
    darkTheme: Boolean,
    onSeekTo: (Long) -> Unit,
    onScrubHaptic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var lastHapticBucket by remember { mutableStateOf(-1) }

    val ink = if (darkTheme) Color.White else Color(0xFF151521)
    val accentGold = if (darkTheme) ChampagneGold else Color(0xFFD92344)

    val displayFraction = if (isDragging) {
        dragFraction
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }
    val displayPosMs = if (isDragging) (dragFraction * safeDuration).toLong() else positionMs
    val scrubberScaleY by animateFloatAsState(
        targetValue = if (isDragging) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scrubber_scale"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ink.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${displayPosMs.formatDuration()} / ${safeDuration.formatDuration()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentGold
                )
            }
        }
        Slider(
            value = displayFraction,
            onValueChange = { value ->
                isDragging = true
                dragFraction = value
                val bucket = (value * 24).toInt()
                if (bucket != lastHapticBucket) {
                    lastHapticBucket = bucket
                    onScrubHaptic()
                }
            },
            onValueChangeFinished = {
                onSeekTo((dragFraction * safeDuration).toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = ink,
                activeTrackColor = ink,
                inactiveTrackColor = ink.copy(alpha = 0.20f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleY = scrubberScaleY }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayPosMs.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDragging) accentGold else ink.copy(alpha = 0.72f)
            )
            Text(
                text = "-${(safeDuration - displayPosMs).coerceAtLeast(0L).formatDuration()}",
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.56f)
            )
        }
    }
}

@Composable
private fun MiniStudioPill(
    text: String,
    active: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit
) {
    val ink = if (darkTheme) Color.White else Color(0xFF151521)
    val bg = when {
        active && darkTheme -> Color.White.copy(alpha = 0.22f)
        active -> Color.Black.copy(alpha = 0.12f)
        darkTheme -> Color.White.copy(alpha = 0.09f)
        else -> Color.Black.copy(alpha = 0.05f)
    }
    val borderColor = when {
        active -> ink.copy(alpha = 0.45f)
        else -> ink.copy(alpha = 0.14f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(0.75.dp, borderColor, RoundedCornerShape(50))
            .bouncyClickable(pressedScale = 0.93f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) ink else ink.copy(alpha = 0.76f)
        )
    }
}

@Composable
private fun DeckToggleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit,
    testTag: String,
    tintOverride: Color? = null
) {
    val ink = if (darkTheme) Color.White else Color(0xFF151521)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ink.copy(alpha = if (darkTheme) 0.22f else 0.12f) else Color.Transparent)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintOverride ?: if (selected) ink else ink.copy(alpha = 0.60f),
            modifier = Modifier.size(22.dp)
        )
    }
}
