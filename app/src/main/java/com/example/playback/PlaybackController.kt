package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.model.AudioTransitionConfig
import com.example.model.AudioTransitionMode
import com.example.model.RepeatMode
import com.example.model.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "PlaybackController"
private const val MAX_SESSION_QUEUE_SIZE = 100
const val MIN_PLAYBACK_SPEED = 0.5f
const val MAX_PLAYBACK_SPEED = 3.0f

fun clampPlaybackSpeed(speed: Float): Float = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)

fun formatPlaybackSpeedLabel(speed: Float): String {
    val safe = clampPlaybackSpeed(speed)
    val hundredths = kotlin.math.round(safe * 100f).toInt()
    return when {
        hundredths % 10 == 0 -> String.format(Locale.US, "%.1fx", hundredths / 100f)
        else -> String.format(Locale.US, "%.2fx", hundredths / 100f)
    }
}

data class ResolvedAudioTransition(
    val mode: AudioTransitionMode,
    val durationMs: Long
)

data class TransitionGainPair(
    val outgoingGain: Float,
    val incomingGain: Float
)

/**
 * Pure heuristic deciding effective audio transition mode & duration for any track pair.
 * Handles AutoMix album continuity awareness, General Audio spoken-word overlap protection,
 * manual skip snappy durations, and short-track safety clamping.
 */
fun resolveEffectiveTransition(
    config: AudioTransitionConfig,
    outgoing: Track?,
    incoming: Track?,
    isManualSkip: Boolean
): ResolvedAudioTransition {
    if (config.mode == AudioTransitionMode.GAPLESS) {
        return ResolvedAudioTransition(AudioTransitionMode.GAPLESS, 0L)
    }
    if (isManualSkip && !config.fadeOnManualSkip) {
        return ResolvedAudioTransition(AudioTransitionMode.GAPLESS, 0L)
    }

    val spokenAudioInvolved = outgoing?.isGeneralAudio == true || incoming?.isGeneralAudio == true
    val configuredMs = config.durationMs.toLong().coerceIn(1500L, 10_000L)

    var effectiveMode = config.mode
    var effectiveMs = configuredMs

    if (spokenAudioInvolved && (effectiveMode == AudioTransitionMode.CROSSFADE || effectiveMode == AudioTransitionMode.AUTOMIX)) {
        // Spoken podcasts / recordings should never overlap voices — smoothly dip & rise instead
        effectiveMode = AudioTransitionMode.SMOOTH_FADE
        effectiveMs = configuredMs.coerceAtMost(900L)
    } else if (effectiveMode == AudioTransitionMode.AUTOMIX) {
        val outAlbum = outgoing?.album?.trim().orEmpty()
        val inAlbum = incoming?.album?.trim().orEmpty()
        val outArtist = outgoing?.artist?.trim().orEmpty()
        val inArtist = incoming?.artist?.trim().orEmpty()
        val validAlbum = outAlbum.isNotEmpty() &&
            !outAlbum.equals("未知专辑", ignoreCase = true) &&
            !outAlbum.equals("Unknown Album", ignoreCase = true)
        val sameAlbumFlow = validAlbum &&
            outAlbum.equals(inAlbum, ignoreCase = true) &&
            outArtist.equals(inArtist, ignoreCase = true)
        effectiveMs = if (sameAlbumFlow) {
            // Preserve intentional album sequencing with a subtle 1.6s kiss
            1600L
        } else {
            // Cross-album / playlist mix: lush full configured overlap
            configuredMs
        }
    } else if (effectiveMode == AudioTransitionMode.VINYL_BRAKE) {
        effectiveMs = 680L
    }

    if (isManualSkip) {
        effectiveMs = when (effectiveMode) {
            AudioTransitionMode.CROSSFADE, AudioTransitionMode.AUTOMIX -> effectiveMs.coerceIn(750L, 1450L)
            AudioTransitionMode.SMOOTH_FADE -> effectiveMs.coerceIn(420L, 860L)
            AudioTransitionMode.VINYL_BRAKE -> 620L
            AudioTransitionMode.GAPLESS -> 0L
        }
    }

    val outDuration = outgoing?.durationMs ?: 0L
    if (outDuration > 0L && effectiveMs > 0L) {
        val maxAllowed = (outDuration * 0.35f).toLong().coerceAtLeast(350L)
        effectiveMs = effectiveMs.coerceAtMost(maxAllowed)
    }

    return ResolvedAudioTransition(effectiveMode, effectiveMs.coerceAtLeast(0L))
}

/**
 * Pure acoustic gain curve evaluator for each transition mode at normalized progress t in [0, 1].
 */
fun computeTransitionGains(mode: AudioTransitionMode, progress: Float): TransitionGainPair {
    val t = progress.coerceIn(0f, 1f)
    return when (mode) {
        AudioTransitionMode.CROSSFADE -> {
            val angle = t * (PI / 2.0)
            TransitionGainPair(
                outgoingGain = cos(angle).toFloat().coerceIn(0f, 1f),
                incomingGain = sin(angle).toFloat().coerceIn(0f, 1f)
            )
        }
        AudioTransitionMode.AUTOMIX -> {
            // S-curve smoothstep equal-power blend: holds outgoing body longer, blooms incoming seamlessly
            val smooth = t * t * (3f - 2f * t)
            val angle = smooth * (PI / 2.0)
            TransitionGainPair(
                outgoingGain = cos(angle).toFloat().coerceIn(0f, 1f),
                incomingGain = sin(angle).toFloat().coerceIn(0f, 1f)
            )
        }
        AudioTransitionMode.SMOOTH_FADE -> {
            if (t < 0.5f) {
                val p = t * 2f
                TransitionGainPair(outgoingGain = (1f - p).coerceIn(0f, 1f), incomingGain = 0f)
            } else {
                val p = (t - 0.5f) * 2f
                TransitionGainPair(outgoingGain = 0f, incomingGain = p.coerceIn(0f, 1f))
            }
        }
        AudioTransitionMode.VINYL_BRAKE -> {
            TransitionGainPair(
                outgoingGain = (1f - t * t * 0.92f).coerceIn(0f, 1f),
                incomingGain = if (t >= 0.99f) 1f else 0f
            )
        }
        AudioTransitionMode.GAPLESS -> TransitionGainPair(1f - t, t)
    }
}

data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerRemainingMs: Long = 0L,
    val abLoopStartMs: Long? = null,
    val abLoopEndMs: Long? = null
) {
    val currentTrack: Track?
        get() = queue.getOrNull(currentIndex)

    val previousTrack: Track?
        get() {
            if (queue.isEmpty() || currentIndex !in queue.indices) return null
            if (queue.size == 1) return queue[0]
            return if (currentIndex > 0) {
                queue[currentIndex - 1]
            } else {
                queue.last()
            }
        }

    val nextTrack: Track?
        get() {
            if (queue.isEmpty() || currentIndex !in queue.indices) return null
            if (queue.size == 1) return queue[0]
            return if (currentIndex < queue.lastIndex) {
                queue[currentIndex + 1]
            } else {
                queue.first()
            }
        }
}

class PlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onTrackChanged: (Track) -> Unit,
    private val onPlaybackError: (String) -> Unit = {}
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    @Volatile
    var transitionConfig: AudioTransitionConfig = AudioTransitionConfig()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var transitionJob: Job? = null
    private var playPauseFadeJob: Job? = null
    private var tailPlayer: ExoPlayer? = null
    private var autoTransitionTriggeredKey: String? = null
    private var pendingQueue: Pair<List<Track>, Int>? = null
    private var windowStartIndex: Int = 0
    private var isSyncingWindow: Boolean = false

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, AuralisPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val mc = future.get()
                    controller = mc
                    attachListener(mc)
                    pendingQueue?.let { (tracks, idx) ->
                        pendingQueue = null
                        playQueue(tracks, idx)
                    }
                    startPositionTicker()
                }.onFailure {
                    Log.e(TAG, "Failed connecting MediaController", it)
                    controllerFuture = null
                    pendingQueue?.let {
                        pendingQueue = null
                        onPlaybackError("音频播放服务连接失败，请重试")
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun attachListener(mc: MediaController) {
        mc.repeatMode = when (_state.value.repeatMode) {
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
        mc.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isSyncingWindow) return
                val globalIdx = windowStartIndex + mc.currentMediaItemIndex
                val queue = _state.value.queue
                autoTransitionTriggeredKey = null
                if (globalIdx in queue.indices) {
                    _state.update {
                        it.copy(
                            currentIndex = globalIdx,
                            positionMs = 0L,
                            durationMs = mc.duration.takeIf { d -> d > 0L } ?: queue[globalIdx].durationMs,
                            abLoopStartMs = null,
                            abLoopEndMs = null
                        )
                    }
                    onTrackChanged(queue[globalIdx])
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = mc.duration.coerceAtLeast(0L)
                if (dur > 0L) {
                    _state.update { it.copy(durationMs = dur) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
                cancelActiveTransition(restoreVolumeAndSpeed = true)
                val title = _state.value.currentTrack?.title ?: "当前音频"
                onPlaybackError("无法播放「$title」：${error.localizedMessage ?: "文件不可读取或格式不受支持"}")
            }
        })
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val mc = controller
                if (mc != null && mc.mediaItemCount > 0) {
                    val pos = mc.currentPosition.coerceAtLeast(0L)
                    val curState = _state.value
                    val dur = mc.duration.takeIf { it > 0L }
                        ?: curState.currentTrack?.durationMs
                        ?: 0L

                    // Handle A-B loop repeat seamlessly
                    val loopA = curState.abLoopStartMs
                    val loopB = curState.abLoopEndMs
                    if (mc.isPlaying && loopA != null && loopB != null && loopB > loopA + 400L && pos >= loopB) {
                        mc.seekTo(loopA)
                    }

                    // Smart auto-transition trigger near end of track
                    maybeTriggerAutoTransition(mc, curState, pos, dur)

                    _state.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = dur,
                            isPlaying = mc.isPlaying
                        )
                    }
                }
                // 33ms (~30fps) during active playback gives silky word-by-word karaoke sweep
                delay(if (_state.value.isPlaying) 33L else 250L)
            }
        }
    }

    private fun maybeTriggerAutoTransition(
        mc: MediaController,
        curState: PlaybackState,
        posMs: Long,
        durMs: Long
    ) {
        if (!mc.isPlaying || transitionJob != null) return
        if (curState.abLoopStartMs != null || curState.repeatMode == RepeatMode.ONE) return
        if (durMs < 5_000L || curState.queue.size <= 1) return

        val outgoing = curState.currentTrack ?: return
        val canAdvanceAuto = mc.hasNextMediaItem() ||
            curState.currentIndex + 1 in curState.queue.indices ||
            curState.repeatMode == RepeatMode.ALL
        if (!canAdvanceAuto) return

        val incoming = curState.nextTrack
        val resolved = resolveEffectiveTransition(
            config = transitionConfig,
            outgoing = outgoing,
            incoming = incoming,
            isManualSkip = false
        )
        if (resolved.mode == AudioTransitionMode.GAPLESS || resolved.durationMs <= 0L) return

        val triggerWindowMs = when (resolved.mode) {
            AudioTransitionMode.SMOOTH_FADE -> (resolved.durationMs / 2L).coerceAtLeast(350L)
            AudioTransitionMode.CROSSFADE, AudioTransitionMode.AUTOMIX -> resolved.durationMs
            AudioTransitionMode.VINYL_BRAKE -> resolved.durationMs
            AudioTransitionMode.GAPLESS -> 0L
        }
        val remainingMs = durMs - posMs
        val trackKey = "${outgoing.id}_${curState.currentIndex}"
        if (remainingMs in 140L..triggerWindowMs && autoTransitionTriggeredKey != trackKey) {
            autoTransitionTriggeredKey = trackKey
            launchTransitionSkip(
                direction = 1,
                resolved = resolved,
                outgoing = outgoing,
                outgoingPosMs = posMs
            )
        }
    }

    private fun releaseTailPlayer() {
        runCatching {
            tailPlayer?.stop()
            tailPlayer?.release()
        }
        tailPlayer = null
    }

    private fun cancelActiveTransition(restoreVolumeAndSpeed: Boolean = true) {
        transitionJob?.cancel()
        transitionJob = null
        playPauseFadeJob?.cancel()
        playPauseFadeJob = null
        releaseTailPlayer()
        if (restoreVolumeAndSpeed) {
            controller?.let { mc ->
                runCatching {
                    mc.volume = 1.0f
                    val speed = clampPlaybackSpeed(_state.value.playbackSpeed)
                    if (mc.playbackParameters.speed != speed || mc.playbackParameters.pitch != 1.0f) {
                        mc.playbackParameters = PlaybackParameters(speed, 1.0f)
                    }
                }
            }
        }
    }

    private fun launchTransitionSkip(
        direction: Int,
        resolved: ResolvedAudioTransition,
        outgoing: Track?,
        outgoingPosMs: Long
    ) {
        val mc = controller ?: return
        cancelActiveTransition(restoreVolumeAndSpeed = false)
        val baseSpeed = clampPlaybackSpeed(_state.value.playbackSpeed)
        val durationMs = resolved.durationMs.coerceAtLeast(250L)

        transitionJob = scope.launch {
            try {
                when (resolved.mode) {
                    AudioTransitionMode.CROSSFADE, AudioTransitionMode.AUTOMIX -> {
                        // Spawn transient non-audio-focus outgoing tail player so incoming track can blend live
                        val tail = if (outgoing != null) {
                            runCatching {
                                val attrs = AudioAttributes.Builder()
                                    .setUsage(C.USAGE_MEDIA)
                                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                    .build()
                                ExoPlayer.Builder(context)
                                    .setAudioAttributes(attrs, /* handleAudioFocus = */ false)
                                    .build()
                                    .apply {
                                        setMediaItem(buildMediaItem(outgoing))
                                        playbackParameters = PlaybackParameters(baseSpeed, 1.0f)
                                        volume = 1.0f
                                        prepare()
                                        seekTo(outgoingPosMs.coerceAtLeast(0L))
                                        play()
                                    }
                            }.getOrNull()
                        } else null
                        tailPlayer = tail

                        // Brief buffer settle so handoff is click-free
                        if (tail != null) delay(45L)

                        mc.volume = 0.0f
                        performImmediateStep(mc, direction)
                        if (!mc.isPlaying) mc.play()

                        val startTime = System.currentTimeMillis()
                        while (isActive) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            val gains = computeTransitionGains(resolved.mode, progress)
                            tail?.volume = gains.outgoingGain
                            mc.volume = gains.incomingGain
                            if (progress >= 1f) break
                            delay(24L)
                        }
                        releaseTailPlayer()
                        mc.volume = 1.0f
                    }

                    AudioTransitionMode.SMOOTH_FADE -> {
                        val halfMs = (durationMs / 2L).coerceAtLeast(180L)
                        // Phase 1: Fade out current track
                        val outStart = System.currentTimeMillis()
                        while (isActive) {
                            val p = ((System.currentTimeMillis() - outStart).toFloat() / halfMs.toFloat()).coerceIn(0f, 1f)
                            mc.volume = (1f - p * p).coerceIn(0f, 1f)
                            if (p >= 1f) break
                            delay(22L)
                        }
                        mc.volume = 0.0f
                        performImmediateStep(mc, direction)
                        if (!mc.isPlaying) mc.play()

                        // Phase 2: Fade in incoming track
                        val inStart = System.currentTimeMillis()
                        while (isActive) {
                            val p = ((System.currentTimeMillis() - inStart).toFloat() / halfMs.toFloat()).coerceIn(0f, 1f)
                            val eased = 1f - (1f - p) * (1f - p)
                            mc.volume = eased.coerceIn(0f, 1f)
                            if (p >= 1f) break
                            delay(22L)
                        }
                        mc.volume = 1.0f
                    }

                    AudioTransitionMode.VINYL_BRAKE -> {
                        val startTime = System.currentTimeMillis()
                        while (isActive) {
                            val p = ((System.currentTimeMillis() - startTime).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            val eased = p * p
                            val curSpd = (baseSpeed * (1f - eased * 0.66f)).coerceAtLeast(0.30f)
                            val curPitch = (1f - eased * 0.62f).coerceAtLeast(0.35f)
                            mc.playbackParameters = PlaybackParameters(curSpd, curPitch)
                            mc.volume = (1f - eased * 0.92f).coerceIn(0.05f, 1f)
                            if (p >= 1f) break
                            delay(24L)
                        }
                        mc.playbackParameters = PlaybackParameters(baseSpeed, 1.0f)
                        mc.volume = 0.15f
                        performImmediateStep(mc, direction)
                        if (!mc.isPlaying) mc.play()
                        // Crisp 120ms rise on new groove
                        val riseStart = System.currentTimeMillis()
                        while (isActive) {
                            val p = ((System.currentTimeMillis() - riseStart).toFloat() / 120f).coerceIn(0f, 1f)
                            mc.volume = (0.15f + 0.85f * p).coerceIn(0f, 1f)
                            if (p >= 1f) break
                            delay(20L)
                        }
                        mc.volume = 1.0f
                    }

                    AudioTransitionMode.GAPLESS -> {
                        performImmediateStep(mc, direction)
                    }
                }
            } finally {
                releaseTailPlayer()
                runCatching {
                    mc.volume = 1.0f
                    mc.playbackParameters = PlaybackParameters(clampPlaybackSpeed(_state.value.playbackSpeed), 1.0f)
                }
                transitionJob = null
            }
        }
    }

    private fun performImmediateStep(mc: MediaController, direction: Int) {
        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        if (direction >= 0) {
            if (mc.hasNextMediaItem()) {
                mc.seekToNextMediaItem()
            } else if (currentState.currentIndex + 1 in queue.indices) {
                val nextGlobal = currentState.currentIndex + 1
                _state.update {
                    it.copy(
                        currentIndex = nextGlobal,
                        positionMs = 0L,
                        durationMs = queue[nextGlobal].durationMs
                    )
                }
                syncWindowAndPlay(mc, queue, nextGlobal, preserveCurrentVolume = true)
            } else if (queue.size > MAX_SESSION_QUEUE_SIZE) {
                _state.update {
                    it.copy(
                        currentIndex = 0,
                        positionMs = 0L,
                        durationMs = queue[0].durationMs
                    )
                }
                syncWindowAndPlay(mc, queue, 0, preserveCurrentVolume = true)
            } else if (mc.mediaItemCount > 0) {
                mc.seekTo(0, 0L)
            }
        } else {
            if (mc.hasPreviousMediaItem()) {
                mc.seekToPreviousMediaItem()
            } else if (currentState.currentIndex - 1 in queue.indices) {
                val prevGlobal = currentState.currentIndex - 1
                _state.update {
                    it.copy(
                        currentIndex = prevGlobal,
                        positionMs = 0L,
                        durationMs = queue[prevGlobal].durationMs
                    )
                }
                syncWindowAndPlay(mc, queue, prevGlobal, preserveCurrentVolume = true)
            } else if (queue.size > MAX_SESSION_QUEUE_SIZE) {
                val lastIdx = queue.lastIndex
                _state.update {
                    it.copy(
                        currentIndex = lastIdx,
                        positionMs = 0L,
                        durationMs = queue[lastIdx].durationMs
                    )
                }
                syncWindowAndPlay(mc, queue, lastIdx, preserveCurrentVolume = true)
            } else if (mc.mediaItemCount > 0) {
                mc.seekTo(mc.mediaItemCount - 1, 0L)
            }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        cancelActiveTransition(restoreVolumeAndSpeed = true)
        autoTransitionTriggeredKey = null
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val mc = controller
        _state.update {
            it.copy(
                queue = tracks,
                currentIndex = safeIndex,
                positionMs = 0L,
                durationMs = tracks[safeIndex].durationMs,
                abLoopStartMs = null,
                abLoopEndMs = null
            )
        }
        if (mc == null) {
            pendingQueue = tracks to safeIndex
            connect()
            onTrackChanged(tracks[safeIndex])
            return
        }

        syncWindowAndPlay(mc, tracks, safeIndex)
    }

    private fun syncWindowAndPlay(
        mc: MediaController,
        tracks: List<Track>,
        targetGlobalIndex: Int,
        preserveCurrentVolume: Boolean = false
    ) {
        val safeGlobal = targetGlobalIndex.coerceIn(0, tracks.lastIndex)
        val (winStart, winEnd) = computeWindowBounds(tracks.size, safeGlobal)
        windowStartIndex = winStart
        val localIndex = safeGlobal - winStart
        val windowTracks = tracks.subList(winStart, winEnd)
        val mediaItems = windowTracks.map { buildMediaItem(it) }

        runCatching {
            isSyncingWindow = true
            if (!preserveCurrentVolume) {
                mc.volume = 1.0f
            }
            mc.setMediaItems(mediaItems, localIndex, 0L)
            mc.prepare()
            mc.play()
        }.onFailure { err ->
            Log.e(TAG, "Failed starting playback", err)
            onPlaybackError("播放启动失败：${err.localizedMessage ?: "请检查音频文件权限"}")
        }.also {
            isSyncingWindow = false
        }
        onTrackChanged(tracks[safeGlobal])
    }

    private fun computeWindowBounds(totalSize: Int, centerIndex: Int): Pair<Int, Int> {
        if (totalSize <= MAX_SESSION_QUEUE_SIZE) return 0 to totalSize
        val half = MAX_SESSION_QUEUE_SIZE / 2
        val start = (centerIndex - half).coerceIn(0, totalSize - MAX_SESSION_QUEUE_SIZE)
        val end = (start + MAX_SESSION_QUEUE_SIZE).coerceAtMost(totalSize)
        return start to end
    }

    private fun buildMediaItem(track: Track): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
        val artUri = when {
            !track.artworkPath.isNullOrBlank() -> Uri.fromFile(File(track.artworkPath))
            !track.albumArtUri.isNullOrBlank() -> Uri.parse(track.albumArtUri)
            else -> null
        }
        if (artUri != null) {
            metadataBuilder.setArtworkUri(artUri)
        }
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.contentUri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun togglePlayPause() {
        val mc = controller ?: return
        cancelActiveTransition(restoreVolumeAndSpeed = false)
        val softFade = transitionConfig.fadeOnPlayPause
        if (mc.isPlaying) {
            if (!softFade) {
                mc.volume = 1.0f
                mc.pause()
            } else {
                playPauseFadeJob = scope.launch {
                    val startVol = mc.volume.coerceIn(0.1f, 1.0f)
                    val startTime = System.currentTimeMillis()
                    while (isActive) {
                        val p = ((System.currentTimeMillis() - startTime) / 160f).coerceIn(0f, 1f)
                        mc.volume = (startVol * (1f - p)).coerceIn(0f, 1f)
                        if (p >= 1f) break
                        delay(18L)
                    }
                    mc.pause()
                    mc.volume = 1.0f
                    playPauseFadeJob = null
                }
            }
        } else {
            if (mc.playbackState == Player.STATE_ENDED) {
                mc.seekTo(0L)
            }
            if (!softFade) {
                mc.volume = 1.0f
                mc.play()
            } else {
                mc.volume = 0.0f
                mc.play()
                playPauseFadeJob = scope.launch {
                    val startTime = System.currentTimeMillis()
                    while (isActive) {
                        val p = ((System.currentTimeMillis() - startTime) / 170f).coerceIn(0f, 1f)
                        mc.volume = p
                        if (p >= 1f) break
                        delay(18L)
                    }
                    mc.volume = 1.0f
                    playPauseFadeJob = null
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        cancelActiveTransition(restoreVolumeAndSpeed = true)
        autoTransitionTriggeredKey = null
        val clamped = positionMs.coerceAtLeast(0L)
        controller?.seekTo(clamped)
        _state.update { it.copy(positionMs = clamped) }
    }

    fun skipNext() {
        val mc = controller ?: return
        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        val resolved = resolveEffectiveTransition(
            config = transitionConfig,
            outgoing = currentState.currentTrack,
            incoming = currentState.nextTrack,
            isManualSkip = true
        )
        if (mc.isPlaying && resolved.mode != AudioTransitionMode.GAPLESS && resolved.durationMs > 0L) {
            launchTransitionSkip(
                direction = 1,
                resolved = resolved,
                outgoing = currentState.currentTrack,
                outgoingPosMs = mc.currentPosition.coerceAtLeast(0L)
            )
            return
        }

        cancelActiveTransition(restoreVolumeAndSpeed = true)
        performImmediateStep(mc, direction = 1)
    }

    fun skipPrevious() {
        val mc = controller ?: return
        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        if (mc.currentPosition > 3500L) {
            cancelActiveTransition(restoreVolumeAndSpeed = true)
            mc.seekTo(0L)
            return
        }

        val resolved = resolveEffectiveTransition(
            config = transitionConfig,
            outgoing = currentState.currentTrack,
            incoming = currentState.previousTrack,
            isManualSkip = true
        )
        if (mc.isPlaying && resolved.mode != AudioTransitionMode.GAPLESS && resolved.durationMs > 0L) {
            launchTransitionSkip(
                direction = -1,
                resolved = resolved,
                outgoing = currentState.currentTrack,
                outgoingPosMs = mc.currentPosition.coerceAtLeast(0L)
            )
            return
        }

        cancelActiveTransition(restoreVolumeAndSpeed = true)
        performImmediateStep(mc, direction = -1)
    }

    fun jumpToQueueIndex(index: Int) {
        val mc = controller ?: return
        val queue = _state.value.queue
        if (index !in queue.indices) return
        cancelActiveTransition(restoreVolumeAndSpeed = true)
        autoTransitionTriggeredKey = null
        val localIdx = index - windowStartIndex
        if (localIdx in 0 until mc.mediaItemCount) {
            mc.seekTo(localIdx, 0L)
            mc.play()
        } else {
            _state.update {
                it.copy(
                    currentIndex = index,
                    positionMs = 0L,
                    durationMs = queue[index].durationMs
                )
            }
            syncWindowAndPlay(mc, queue, index)
        }
    }

    fun addTrackNextInQueue(track: Track) {
        val currentQueue = _state.value.queue.toMutableList()
        if (currentQueue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val insertIdx = (_state.value.currentIndex + 1).coerceIn(0, currentQueue.size)
        currentQueue.add(insertIdx, track)
        _state.update { it.copy(queue = currentQueue) }
        controller?.let { mc ->
            val localInsert = (insertIdx - windowStartIndex).coerceIn(0, mc.mediaItemCount)
            mc.addMediaItem(localInsert, buildMediaItem(track))
        }
    }

    fun appendTracksToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val currentQueue = _state.value.queue.toMutableList()
        if (currentQueue.isEmpty()) {
            playQueue(tracks, 0)
            return
        }
        val oldSize = currentQueue.size
        currentQueue.addAll(tracks)
        _state.update { it.copy(queue = currentQueue) }
        controller?.let { mc ->
            if (windowStartIndex + mc.mediaItemCount == oldSize && mc.mediaItemCount < MAX_SESSION_QUEUE_SIZE) {
                val room = MAX_SESSION_QUEUE_SIZE - mc.mediaItemCount
                val itemsToAppend = tracks.take(room).map { buildMediaItem(it) }
                if (itemsToAppend.isNotEmpty()) {
                    mc.addMediaItems(itemsToAppend)
                }
            }
        }
    }

    fun removeFromQueue(globalIndex: Int) {
        val currentState = _state.value
        val mutable = currentState.queue.toMutableList()
        if (globalIndex !in mutable.indices) return

        if (mutable.size == 1) {
            clearQueueAndStop()
            return
        }

        val wasCurrent = globalIndex == currentState.currentIndex
        mutable.removeAt(globalIndex)
        val newCurrentIdx = when {
            wasCurrent -> globalIndex.coerceAtMost(mutable.lastIndex)
            globalIndex < currentState.currentIndex -> currentState.currentIndex - 1
            else -> currentState.currentIndex
        }

        _state.update {
            it.copy(
                queue = mutable,
                currentIndex = newCurrentIdx,
                durationMs = mutable.getOrNull(newCurrentIdx)?.durationMs ?: it.durationMs
            )
        }

        val mc = controller ?: return
        if (wasCurrent) {
            cancelActiveTransition(restoreVolumeAndSpeed = true)
            syncWindowAndPlay(mc, mutable, newCurrentIdx)
        } else {
            val localIdx = globalIndex - windowStartIndex
            if (localIdx in 0 until mc.mediaItemCount) {
                mc.removeMediaItem(localIdx)
            } else if (globalIndex < windowStartIndex) {
                windowStartIndex = (windowStartIndex - 1).coerceAtLeast(0)
            }
        }
    }

    fun moveQueueItemToNext(fromGlobalIndex: Int) {
        val currentState = _state.value
        val curIdx = currentState.currentIndex
        val mutable = currentState.queue.toMutableList()
        if (fromGlobalIndex !in mutable.indices || curIdx !in mutable.indices) return
        if (fromGlobalIndex == curIdx || fromGlobalIndex == curIdx + 1) return

        val track = mutable.removeAt(fromGlobalIndex)
        val adjustedCurrent = if (fromGlobalIndex < curIdx) curIdx - 1 else curIdx
        val targetIdx = (adjustedCurrent + 1).coerceIn(0, mutable.size)
        mutable.add(targetIdx, track)

        _state.update { it.copy(queue = mutable, currentIndex = adjustedCurrent) }
        val mc = controller ?: return
        val localFrom = fromGlobalIndex - windowStartIndex
        val localTo = targetIdx - windowStartIndex
        if (localFrom in 0 until mc.mediaItemCount && localTo in 0 until mc.mediaItemCount) {
            mc.moveMediaItem(localFrom, localTo)
        } else {
            // Re-sync window around current track seamlessly without interrupting playback
            val pos = mc.currentPosition
            val wasPlaying = mc.isPlaying
            val (winStart, winEnd) = computeWindowBounds(mutable.size, adjustedCurrent)
            windowStartIndex = winStart
            val localCurrent = adjustedCurrent - winStart
            val items = mutable.subList(winStart, winEnd).map { buildMediaItem(it) }
            isSyncingWindow = true
            mc.setMediaItems(items, localCurrent, pos)
            mc.prepare()
            if (wasPlaying) mc.play()
            isSyncingWindow = false
        }
    }

    fun clearUpcomingQueue() {
        val currentState = _state.value
        val current = currentState.currentTrack ?: return
        _state.update {
            it.copy(
                queue = listOf(current),
                currentIndex = 0
            )
        }
        val mc = controller ?: return
        val localCur = mc.currentMediaItemIndex
        if (mc.mediaItemCount > 1 && localCur in 0 until mc.mediaItemCount) {
            if (localCur + 1 < mc.mediaItemCount) {
                mc.removeMediaItems(localCur + 1, mc.mediaItemCount)
            }
            if (localCur > 0) {
                mc.removeMediaItems(0, localCur)
            }
        }
        windowStartIndex = 0
    }

    /**
     * Stops playback completely, cancels any active transition or sleep timer, and empties the active queue.
     */
    fun clearQueueAndStop() {
        cancelActiveTransition(restoreVolumeAndSpeed = true)
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        autoTransitionTriggeredKey = null
        pendingQueue = null
        controller?.let { mc ->
            runCatching {
                mc.stop()
                mc.clearMediaItems()
                mc.volume = 1.0f
                mc.playbackParameters = PlaybackParameters(1.0f, 1.0f)
            }
        }
        windowStartIndex = 0
        _state.update { PlaybackState() }
    }

    fun removeTracksByIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        val currentState = _state.value
        if (currentState.queue.isEmpty()) return
        val remaining = currentState.queue.filterNot { it.id in ids }
        if (remaining.size == currentState.queue.size) return
        if (remaining.isEmpty()) {
            clearQueueAndStop()
            return
        }
        val currentId = currentState.currentTrack?.id
        val preservedIndex = remaining.indexOfFirst { it.id == currentId }
        if (preservedIndex >= 0) {
            _state.update { it.copy(queue = remaining, currentIndex = preservedIndex) }
        } else {
            val nextIdx = currentState.currentIndex.coerceIn(0, remaining.lastIndex)
            controller?.let { mc ->
                cancelActiveTransition(restoreVolumeAndSpeed = true)
                _state.update {
                    it.copy(
                        queue = remaining,
                        currentIndex = nextIdx,
                        positionMs = 0L,
                        durationMs = remaining[nextIdx].durationMs
                    )
                }
                syncWindowAndPlay(mc, remaining, nextIdx)
            }
        }
    }

    fun toggleShuffle() {
        val next = !_state.value.shuffleEnabled
        controller?.shuffleModeEnabled = next
        _state.update { it.copy(shuffleEnabled = next) }
    }

    fun cycleRepeatMode() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
            RepeatMode.OFF -> RepeatMode.ALL
        }
        controller?.repeatMode = when (next) {
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
        _state.update { it.copy(repeatMode = next) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safe = clampPlaybackSpeed(speed)
        controller?.playbackParameters = PlaybackParameters(safe, 1.0f)
        _state.update { it.copy(playbackSpeed = safe) }
    }

    fun toggleAbLoopPoint() {
        val current = _state.value
        val pos = current.positionMs
        when {
            current.abLoopStartMs == null -> {
                _state.update { it.copy(abLoopStartMs = pos, abLoopEndMs = null) }
            }
            current.abLoopEndMs == null -> {
                val end = pos.coerceAtLeast(current.abLoopStartMs + 1000L)
                _state.update { it.copy(abLoopEndMs = end) }
            }
            else -> {
                _state.update { it.copy(abLoopStartMs = null, abLoopEndMs = null) }
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            controller?.volume = 1.0f
            _state.update { it.copy(sleepTimerRemainingMs = 0L) }
            return
        }
        val totalMs = minutes * 60_000L
        _state.update { it.copy(sleepTimerRemainingMs = totalMs) }
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0L && isActive) {
                delay(1000L)
                remaining = (remaining - 1000L).coerceAtLeast(0L)
                _state.update { it.copy(sleepTimerRemainingMs = remaining) }
                // Gentle 10-second acoustic fade out before pausing
                if (remaining in 1L..10_000L) {
                    controller?.volume = (remaining / 10_000f).coerceIn(0.05f, 1.0f)
                }
            }
            controller?.pause()
            controller?.volume = 1.0f
            _state.update { it.copy(sleepTimerRemainingMs = 0L) }
        }
    }

    fun syncTrackMetadataUpdate(updated: Track) {
        _state.update { st ->
            st.copy(queue = st.queue.map { if (it.id == updated.id) updated else it })
        }
    }

    fun release() {
        cancelActiveTransition(restoreVolumeAndSpeed = false)
        tickerJob?.cancel()
        sleepTimerJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}
