package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.model.AudioTransitionConfig
import com.example.model.AudioTransitionMode
import com.example.model.LyricDocument
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
internal const val CROSSFADE_PRE_ARM_LEAD_MS = 2400L
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

data class LyricVocalWindow(
    val firstVocalStartMs: Long,
    val lastVocalEndMs: Long
)

data class LyricBridgePlan(
    val triggerPosMs: Long,
    val incomingStartPosMs: Long,
    val fadeDurationMs: Long
)

private val LYRIC_CREDIT_PREFIX_REGEX = Regex(
    """^(作词|词|作曲|曲|编曲|制作人?|监制|混音|母带|录音|和声|吉他|贝斯|鼓|弦乐|钢琴|键盘|配唱|人声|出品|发行|策划|统筹|版权|原唱|演唱|歌手|词曲|词/曲|作词/作曲|混音/母带|录音室|混音室|母带室|OP|SP|Lyricist|Lyrics|Composer|Arranger|Producer|Mixed|Mastering|Recorded|Vocal|Music|By)\s*[:：\-—/]""",
    RegexOption.IGNORE_CASE
)

private val LYRIC_NON_VOCAL_BANNER_REGEX = Regex(
    """^(纯音乐|无歌词|此歌曲为没有填词的纯音乐|Instrumental|TME享有|腾讯音乐人|网易云音乐)""",
    RegexOption.IGNORE_CASE
)

private val LYRIC_TITLE_DASH_ARTIST_REGEX = Regex("""^.{1,40}\s*[-—–]\s*.{1,40}$""")

/**
 * Identifies metadata/credit/banner lines (e.g. "作词：xxx", "Title - Artist", "纯音乐")
 * so Lyric Bridge mode accurately detects the true first and last sung vocal phrases.
 */
fun isLyricCreditOrMetadataLine(rawText: String, lineIndex: Int, startMs: Long): Boolean {
    val cleaned = rawText
        .trim()
        .trim('-', '—', '–', '~', '·', '•', '♪', '♫', '*', ' ')
    if (cleaned.isBlank()) return true
    if (LYRIC_CREDIT_PREFIX_REGEX.containsMatchIn(cleaned)) return true
    if (LYRIC_NON_VOCAL_BANNER_REGEX.containsMatchIn(cleaned)) return true
    if (lineIndex <= 1 && startMs <= 4_000L && LYRIC_TITLE_DASH_ARTIST_REGEX.matches(cleaned)) {
        return true
    }
    return false
}

/**
 * Extracts the true sung vocal boundaries [firstVocalStartMs, lastVocalEndMs] in song playback
 * coordinates (already accounting for [LyricDocument.globalOffsetMs]). Returns null if the track
 * does not have valid synced lyrics.
 */
fun extractVocalLyricWindow(doc: LyricDocument, trackDurationMs: Long): LyricVocalWindow? {
    if (!doc.hasSyncedTimestamps || doc.lines.size < 2 || trackDurationMs < 15_000L) return null

    val vocalLines = doc.lines.filterIndexed { index, line ->
        !isLyricCreditOrMetadataLine(line.text, index, line.startMs)
    }
    if (vocalLines.size < 2) return null

    val firstLine = vocalLines.first()
    val lastLine = vocalLines.last()

    val firstVocalStartMs = (firstLine.startMs - doc.globalOffsetMs)
        .coerceIn(0L, trackDurationMs)

    val rawLastEnd = if (lastLine.words.isNotEmpty()) {
        maxOf(lastLine.endMs, lastLine.words.last().endMs)
    } else {
        lastLine.endMs
    }
    val boundedLastEnd = rawLastEnd.coerceIn(lastLine.startMs + 1_200L, lastLine.startMs + 8_500L)
    val lastVocalEndMs = (boundedLastEnd - doc.globalOffsetMs)
        .coerceIn(0L, trackDurationMs)

    if (lastVocalEndMs <= firstVocalStartMs + 5_000L) return null
    if (firstVocalStartMs > (trackDurationMs * 0.65f).toLong()) return null

    return LyricVocalWindow(
        firstVocalStartMs = firstVocalStartMs,
        lastVocalEndMs = lastVocalEndMs
    )
}

/**
 * Computes the exact crossfade trigger point and incoming cue point for Lyric Bridge mode.
 * Returns null whenever either song lacks synced vocal boundaries so the engine automatically
 * degrades to AutoMix.
 */
fun computeLyricBridgePlan(
    outgoingWindow: LyricVocalWindow?,
    incomingWindow: LyricVocalWindow?,
    outgoingDurationMs: Long,
    incomingDurationMs: Long,
    configuredDurationMs: Long
): LyricBridgePlan? {
    if (outgoingWindow == null || incomingWindow == null) return null
    if (outgoingDurationMs < 15_000L || incomingDurationMs < 15_000L) return null

    val maxFadeByDuration = minOf(
        (outgoingDurationMs * 0.35f).toLong().coerceAtLeast(1_500L),
        (incomingDurationMs * 0.35f).toLong().coerceAtLeast(1_500L)
    )
    val targetFadeMs = configuredDurationMs
        .coerceIn(1_500L, 10_000L)
        .coerceAtMost(maxFadeByDuration)

    // Begin fading right after the outgoing track's final sung phrase finishes (+350ms breath tail)
    val triggerPosMs = (outgoingWindow.lastVocalEndMs + 350L)
        .coerceAtMost((outgoingDurationMs - 420L).coerceAtLeast(0L))
    val remainingAfterTrigger = (outgoingDurationMs - triggerPosMs).coerceAtLeast(0L)
    if (remainingAfterTrigger < 320L) return null

    val effectiveFadeMs = minOf(targetFadeMs, (remainingAfterTrigger - 60L).coerceAtLeast(350L))

    // Cue incoming track slightly before its first lyric line so vocals emerge cleanly near fade peak
    val leadInMs = (effectiveFadeMs * 0.78f).toLong().coerceIn(700L, 6_500L)
    val maxIncomingStart = (incomingDurationMs - effectiveFadeMs - 1_000L).coerceAtLeast(0L)
    val incomingStartPosMs = (incomingWindow.firstVocalStartMs - leadInMs)
        .coerceIn(0L, maxIncomingStart)

    return LyricBridgePlan(
        triggerPosMs = triggerPosMs,
        incomingStartPosMs = incomingStartPosMs,
        fadeDurationMs = effectiveFadeMs
    )
}

/**
 * Pure heuristic deciding effective audio transition mode & duration for any track pair.
 * Handles AutoMix album continuity awareness, Lyric Bridge vocal continuity, General Audio
 * spoken-word overlap protection, manual skip snappy durations, and short-track safety clamping.
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

    if (spokenAudioInvolved && (
            effectiveMode == AudioTransitionMode.CROSSFADE ||
                effectiveMode == AudioTransitionMode.AUTOMIX ||
                effectiveMode == AudioTransitionMode.LYRIC_BRIDGE
            )
    ) {
        // Spoken podcasts / recordings should never overlap voices — smoothly dip & rise instead
        effectiveMode = AudioTransitionMode.SMOOTH_FADE
        effectiveMs = configuredMs.coerceAtMost(900L)
    } else if (effectiveMode == AudioTransitionMode.LYRIC_BRIDGE && isManualSkip) {
        // Manual skip degrades Lyric Bridge into snappy AutoMix without jumping mid-song
        effectiveMode = AudioTransitionMode.AUTOMIX
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
            AudioTransitionMode.CROSSFADE,
            AudioTransitionMode.AUTOMIX,
            AudioTransitionMode.LYRIC_BRIDGE -> effectiveMs.coerceIn(750L, 1450L)
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
        AudioTransitionMode.AUTOMIX, AudioTransitionMode.LYRIC_BRIDGE -> {
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

/**
 * Pure helper determining whether the standby deck should silently pre-cue the upcoming track
 * so the A/B dual-deck crossfade begins with zero decode latency.
 */
fun shouldPreArmCrossfadeTail(
    mode: AudioTransitionMode,
    remainingMs: Long,
    triggerWindowMs: Long,
    trackDurationMs: Long
): Boolean {
    if (mode != AudioTransitionMode.CROSSFADE &&
        mode != AudioTransitionMode.AUTOMIX &&
        mode != AudioTransitionMode.LYRIC_BRIDGE
    ) return false
    if (triggerWindowMs <= 0L || trackDurationMs < 5_000L) return false
    val preArmOuterMs = (triggerWindowMs + CROSSFADE_PRE_ARM_LEAD_MS)
        .coerceAtMost((trackDurationMs * 0.75f).toLong())
    return remainingMs in (triggerWindowMs + 120L)..preArmOuterMs
}

/**
 * Clamps the live A/B crossfade duration so the outgoing deck finishes fading cleanly to 0.0
 * right before hitting end-of-file, avoiding any abrupt cutoff.
 */
fun computeDualDeckFadeDurationMs(
    requestedMs: Long,
    outgoingRemainingMs: Long?
): Long {
    val safeRequested = requestedMs.coerceAtLeast(250L)
    if (outgoingRemainingMs == null || outgoingRemainingMs <= 0L) return safeRequested
    return minOf(safeRequested, (outgoingRemainingMs - 60L).coerceAtLeast(280L))
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
    private val onPlaybackError: (String) -> Unit = {},
    private val resolveTrackLyrics: suspend (Track) -> LyricDocument? = { null }
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
    private var autoTransitionTriggeredKey: String? = null
    private var pendingQueue: Pair<List<Track>, Int>? = null
    private var windowStartIndex: Int = 0
    private var isSyncingWindow: Boolean = false
    private val vocalWindowCache = HashMap<String, LyricVocalWindow?>()
    private val vocalWindowInFlight = HashSet<String>()

    fun invalidateLyricCache(trackId: String? = null) {
        if (trackId == null) {
            vocalWindowCache.clear()
            vocalWindowInFlight.clear()
        } else {
            vocalWindowCache.remove(trackId)
            vocalWindowInFlight.remove(trackId)
        }
    }

    private fun ensureVocalWindowPrefetched(track: Track, fallbackDurationMs: Long = track.durationMs) {
        if (track.isGeneralAudio) {
            vocalWindowCache[track.id] = null
            return
        }
        if (vocalWindowCache.containsKey(track.id) || vocalWindowInFlight.contains(track.id)) return
        vocalWindowInFlight.add(track.id)
        scope.launch {
            try {
                val doc = runCatching { resolveTrackLyrics(track) }.getOrNull()
                val dur = fallbackDurationMs.takeIf { it > 0L } ?: track.durationMs
                vocalWindowCache[track.id] = if (doc != null) {
                    extractVocalLyricWindow(doc, dur)
                } else {
                    null
                }
            } finally {
                vocalWindowInFlight.remove(track.id)
            }
        }
    }

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
                if (globalIdx !in queue.indices) return
                // Idempotent guard: when Dual-Deck A/B swap already promoted this exact track,
                // ignore the delayed MediaController IPC echo so we never double-fire onTrackChanged.
                if (globalIdx == _state.value.currentIndex &&
                    mediaItem?.mediaId == queue[globalIdx].id
                ) {
                    return
                }
                autoTransitionTriggeredKey = null
                DualDeckServiceBridge.current?.clearStandbyPreCue()
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

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = (DualDeckServiceBridge.current?.activeDeck?.duration ?: mc.duration).coerceAtLeast(0L)
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
                val livePlayer: Player? = DualDeckServiceBridge.current?.activeDeck ?: mc
                if (mc != null && livePlayer != null && livePlayer.mediaItemCount > 0) {
                    val pos = livePlayer.currentPosition.coerceAtLeast(0L)
                    val curState = _state.value
                    val dur = livePlayer.duration.takeIf { it > 0L }
                        ?: curState.currentTrack?.durationMs
                        ?: 0L

                    // Handle A-B loop repeat seamlessly
                    val loopA = curState.abLoopStartMs
                    val loopB = curState.abLoopEndMs
                    if (livePlayer.isPlaying && loopA != null && loopB != null && loopB > loopA + 400L && pos >= loopB) {
                        livePlayer.seekTo(loopA)
                    }

                    // Smart auto-transition trigger near end of track
                    maybeTriggerAutoTransition(mc, curState, pos, dur)

                    _state.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = dur,
                            isPlaying = livePlayer.isPlaying
                        )
                    }
                }
                // 33ms (~30fps) during active playback gives silky word-by-word karaoke sweep
                delay(if (_state.value.isPlaying) 33L else 250L)
            }
        }
    }

    private fun resolveTargetGlobalIndex(curState: PlaybackState, direction: Int): Int? {
        val queue = curState.queue
        if (queue.isEmpty() || curState.currentIndex !in queue.indices) return null

        // Prefer activeDeck's real next/previous MediaItem index so Shuffle Mode order is respected 100%
        val service = DualDeckServiceBridge.current
        if (service != null) {
            val adjLocal = service.resolveAdjacentLocalIndex(direction)
            if (adjLocal != C.INDEX_UNSET) {
                val adjGlobal = windowStartIndex + adjLocal
                if (adjGlobal in queue.indices) return adjGlobal
            }
        }

        return if (direction >= 0) {
            when {
                curState.currentIndex + 1 in queue.indices -> curState.currentIndex + 1
                curState.repeatMode == RepeatMode.ALL -> 0
                else -> null
            }
        } else {
            when {
                curState.currentIndex - 1 in queue.indices -> curState.currentIndex - 1
                curState.repeatMode == RepeatMode.ALL -> queue.lastIndex
                else -> null
            }
        }
    }

    private fun toPlayerRepeatMode(mode: RepeatMode): Int = when (mode) {
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }

    private fun maybeTriggerAutoTransition(
        mc: MediaController,
        curState: PlaybackState,
        posMs: Long,
        durMs: Long
    ) {
        val livePlaying = DualDeckServiceBridge.current?.activeDeck?.isPlaying ?: mc.isPlaying
        if (!livePlaying || transitionJob != null) return
        if (curState.abLoopStartMs != null || curState.repeatMode == RepeatMode.ONE) return
        if (durMs < 5_000L || curState.queue.size <= 1) return

        val outgoing = curState.currentTrack ?: return
        val nextGlobalIdx = resolveTargetGlobalIndex(curState, direction = 1) ?: return
        val incoming = curState.queue.getOrNull(nextGlobalIdx) ?: return

        var resolved = resolveEffectiveTransition(
            config = transitionConfig,
            outgoing = outgoing,
            incoming = incoming,
            isManualSkip = false
        )
        if (resolved.mode == AudioTransitionMode.GAPLESS || resolved.durationMs <= 0L) {
            DualDeckServiceBridge.current?.clearStandbyPreCue()
            return
        }

        val trackKey = "${outgoing.id}_${curState.currentIndex}"

        // Lyric Bridge mode: skip outgoing outro & incoming intro when BOTH tracks have synced lyrics;
        // otherwise seamlessly degrade to AutoMix at the end of the track.
        if (resolved.mode == AudioTransitionMode.LYRIC_BRIDGE) {
            ensureVocalWindowPrefetched(outgoing, durMs)
            ensureVocalWindowPrefetched(incoming, incoming.durationMs)
            val bothResolved = vocalWindowCache.containsKey(outgoing.id) &&
                vocalWindowCache.containsKey(incoming.id)
            val outWin = vocalWindowCache[outgoing.id]
            val inWin = vocalWindowCache[incoming.id]

            if (bothResolved && outWin != null && inWin != null) {
                val plan = computeLyricBridgePlan(
                    outgoingWindow = outWin,
                    incomingWindow = inWin,
                    outgoingDurationMs = durMs,
                    incomingDurationMs = incoming.durationMs,
                    configuredDurationMs = transitionConfig.durationMs.toLong()
                )
                if (plan != null && posMs <= plan.triggerPosMs + 1_500L) {
                    val timeUntilTrigger = plan.triggerPosMs - posMs
                    if (timeUntilTrigger in 120L..CROSSFADE_PRE_ARM_LEAD_MS &&
                        autoTransitionTriggeredKey != trackKey
                    ) {
                        val service = DualDeckServiceBridge.current
                        if (service != null) {
                            val (winStart, winEnd) = computeWindowBounds(curState.queue.size, nextGlobalIdx)
                            val targetLocal = nextGlobalIdx - winStart
                            val windowItems = curState.queue.subList(winStart, winEnd).map { buildMediaItem(it) }
                            service.preCueStandbyDeck(
                                mediaItems = windowItems,
                                targetLocalIndex = targetLocal,
                                speed = clampPlaybackSpeed(curState.playbackSpeed),
                                repeatMode = toPlayerRepeatMode(curState.repeatMode),
                                shuffleEnabled = curState.shuffleEnabled,
                                trackKey = "${incoming.id}_$nextGlobalIdx",
                                startPositionMs = plan.incomingStartPosMs
                            )
                        }
                    }
                    if (posMs >= plan.triggerPosMs &&
                        posMs < durMs - 140L &&
                        autoTransitionTriggeredKey != trackKey
                    ) {
                        autoTransitionTriggeredKey = trackKey
                        launchTransitionSkip(
                            direction = 1,
                            resolved = resolved.copy(durationMs = plan.fadeDurationMs),
                            targetGlobalIndex = nextGlobalIdx,
                            incomingStartPosMs = plan.incomingStartPosMs
                        )
                    }
                    return
                }
            }

            // Missing lyrics on either track (or user manually scrubbed past the vocal outro):
            // gracefully degrade to AutoMix so the track finishes smoothly without dead silence.
            resolved = resolveEffectiveTransition(
                config = transitionConfig.copy(mode = AudioTransitionMode.AUTOMIX),
                outgoing = outgoing,
                incoming = incoming,
                isManualSkip = false
            )
            if (resolved.mode == AudioTransitionMode.GAPLESS || resolved.durationMs <= 0L) {
                DualDeckServiceBridge.current?.clearStandbyPreCue()
                return
            }
        }

        val triggerWindowMs = when (resolved.mode) {
            AudioTransitionMode.SMOOTH_FADE -> (resolved.durationMs / 2L).coerceAtLeast(350L)
            AudioTransitionMode.CROSSFADE,
            AudioTransitionMode.AUTOMIX,
            AudioTransitionMode.LYRIC_BRIDGE -> resolved.durationMs
            AudioTransitionMode.VINYL_BRAKE -> resolved.durationMs
            AudioTransitionMode.GAPLESS -> 0L
        }
        val remainingMs = durMs - posMs

        // Silently pre-cue the INCOMING track at 0:00.000 on the standby deck ~2.4s early.
        // The outgoing track on the active deck is NEVER seeked, copied, or interrupted.
        if (shouldPreArmCrossfadeTail(resolved.mode, remainingMs, triggerWindowMs, durMs) &&
            autoTransitionTriggeredKey != trackKey
        ) {
            val service = DualDeckServiceBridge.current
            if (service != null) {
                val (winStart, winEnd) = computeWindowBounds(curState.queue.size, nextGlobalIdx)
                val targetLocal = nextGlobalIdx - winStart
                val windowItems = curState.queue.subList(winStart, winEnd).map { buildMediaItem(it) }
                service.preCueStandbyDeck(
                    mediaItems = windowItems,
                    targetLocalIndex = targetLocal,
                    speed = clampPlaybackSpeed(curState.playbackSpeed),
                    repeatMode = toPlayerRepeatMode(curState.repeatMode),
                    shuffleEnabled = curState.shuffleEnabled,
                    trackKey = "${incoming.id}_$nextGlobalIdx",
                    startPositionMs = 0L
                )
            }
        } else if (resolved.mode != AudioTransitionMode.CROSSFADE &&
            resolved.mode != AudioTransitionMode.AUTOMIX &&
            resolved.mode != AudioTransitionMode.LYRIC_BRIDGE
        ) {
            DualDeckServiceBridge.current?.clearStandbyPreCue()
        }

        if (remainingMs in 140L..triggerWindowMs && autoTransitionTriggeredKey != trackKey) {
            autoTransitionTriggeredKey = trackKey
            launchTransitionSkip(
                direction = 1,
                resolved = resolved,
                targetGlobalIndex = nextGlobalIdx,
                incomingStartPosMs = 0L
            )
        }
    }

    private fun cancelActiveTransition(restoreVolumeAndSpeed: Boolean = true) {
        transitionJob?.cancel()
        transitionJob = null
        playPauseFadeJob?.cancel()
        playPauseFadeJob = null
        val service = DualDeckServiceBridge.current
        service?.stopFadingOutDeck()
        service?.clearStandbyPreCue()
        if (restoreVolumeAndSpeed) {
            val duck = service?.focusDuckMultiplier ?: 1.0f
            val targetPlayer: Player? = service?.activeDeck ?: controller
            targetPlayer?.let { p ->
                runCatching {
                    p.volume = 1.0f * duck
                    val speed = clampPlaybackSpeed(_state.value.playbackSpeed)
                    if (p.playbackParameters.speed != speed || p.playbackParameters.pitch != 1.0f) {
                        p.playbackParameters = PlaybackParameters(speed, 1.0f)
                    }
                }
            }
        }
    }

    private fun launchTransitionSkip(
        direction: Int,
        resolved: ResolvedAudioTransition,
        targetGlobalIndex: Int?,
        incomingStartPosMs: Long = 0L
    ) {
        val mc = controller ?: return
        val curState = _state.value
        val queue = curState.queue
        val resolvedTargetIdx = targetGlobalIndex ?: resolveTargetGlobalIndex(curState, direction)

        // Cancel any previous fade job cleanly; note we preserve standby pre-cue for dual-deck CROSSFADE/AUTOMIX/LYRIC_BRIDGE
        transitionJob?.cancel()
        transitionJob = null
        playPauseFadeJob?.cancel()
        playPauseFadeJob = null

        val baseSpeed = clampPlaybackSpeed(curState.playbackSpeed)
        val durationMs = resolved.durationMs.coerceAtLeast(250L)

        transitionJob = scope.launch {
            var activeCrossfadePair: DualDeckCrossfadePair? = null
            val service = DualDeckServiceBridge.current
            try {
                when (resolved.mode) {
                    AudioTransitionMode.CROSSFADE,
                    AudioTransitionMode.AUTOMIX,
                    AudioTransitionMode.LYRIC_BRIDGE -> {
                        if (service == null || resolvedTargetIdx == null || resolvedTargetIdx !in queue.indices) {
                            performImmediateStep(mc, direction)
                            return@launch
                        }

                        val incomingTrack = queue[resolvedTargetIdx]
                        val (winStart, winEnd) = computeWindowBounds(queue.size, resolvedTargetIdx)
                        val targetLocal = resolvedTargetIdx - winStart
                        val windowItems = queue.subList(winStart, winEnd).map { buildMediaItem(it) }
                        val targetKey = "${incomingTrack.id}_$resolvedTargetIdx"
                        val safeIncomingStartMs = incomingStartPosMs.coerceAtLeast(0L)

                        // TRUE A/B DUAL-DECK SWAP:
                        //  - outgoingDeck keeps playing Song A continuously (ZERO seek, ZERO copy, ZERO stutter!)
                        //  - incomingDeck decodes Song B cleanly from safeIncomingStartMs and stays active after the fade!
                        val pair = service.prepareIncomingDeckForCrossfade(
                            mediaItems = windowItems,
                            targetLocalIndex = targetLocal,
                            speed = baseSpeed,
                            repeatMode = toPlayerRepeatMode(curState.repeatMode),
                            shuffleEnabled = curState.shuffleEnabled,
                            trackKey = targetKey,
                            startPositionMs = safeIncomingStartMs
                        )
                        if (pair == null) {
                            performImmediateStep(mc, direction)
                            return@launch
                        }
                        activeCrossfadePair = pair

                        // Wait until incomingDeck is genuinely decoded at target cue (0ms if pre-cued!)
                        // while outgoingDeck continues playing Song A untouched at full volume.
                        val readyDeadline = System.currentTimeMillis() + 650L
                        while (isActive &&
                            pair.incomingDeck.playbackState != Player.STATE_READY &&
                            System.currentTimeMillis() < readyDeadline
                        ) {
                            delay(12L)
                        }
                        if (!isActive) return@launch

                        // Start incomingDeck cleanly at 0.0 volume alongside uninterrupted outgoingDeck
                        service.requestServiceAudioFocus()
                        pair.incomingDeck.volume = 0.0f
                        pair.incomingDeck.play()

                        // Wait up to 120ms for incomingDeck AudioTrack to begin active playback
                        val playDeadline = System.currentTimeMillis() + 120L
                        while (isActive &&
                            !pair.incomingDeck.isPlaying &&
                            System.currentTimeMillis() < playDeadline
                        ) {
                            delay(10L)
                        }
                        if (!isActive) return@launch

                        // Atomically promote incomingDeck to activeDeck + MediaSession while outgoingDeck fades out
                        isSyncingWindow = true
                        windowStartIndex = winStart
                        autoTransitionTriggeredKey = null
                        _state.update {
                            it.copy(
                                currentIndex = resolvedTargetIdx,
                                positionMs = pair.incomingDeck.currentPosition.coerceAtLeast(safeIncomingStartMs),
                                durationMs = pair.incomingDeck.duration.takeIf { d -> d > 0L } ?: incomingTrack.durationMs,
                                isPlaying = true,
                                abLoopStartMs = null,
                                abLoopEndMs = null
                            )
                        }
                        service.commitDualDeckSwap(pair)
                        isSyncingWindow = false
                        onTrackChanged(incomingTrack)

                        val outgoingRemainingMs = pair.outgoingDeck.let { out ->
                            val d = out.duration
                            if (d > 0L) (d - out.currentPosition).coerceAtLeast(0L) else null
                        }
                        val effectiveFadeMs = computeDualDeckFadeDurationMs(
                            requestedMs = durationMs,
                            outgoingRemainingMs = outgoingRemainingMs
                        )

                        // Pure 60Hz equal-power / AutoMix crossfade — zero discontinuity on either stream
                        val startTime = System.currentTimeMillis()
                        while (isActive) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val progress = (elapsed.toFloat() / effectiveFadeMs.toFloat()).coerceIn(0f, 1f)
                            val gains = computeTransitionGains(resolved.mode, progress)
                            val duck = service.focusDuckMultiplier
                            pair.outgoingDeck.volume = (pair.startingOutgoingVolume * gains.outgoingGain * duck).coerceIn(0f, 1f)
                            pair.incomingDeck.volume = (gains.incomingGain * duck).coerceIn(0f, 1f)
                            if (progress >= 1f) break
                            delay(16L)
                        }
                        service.finishDualDeckCrossfade(pair)
                        activeCrossfadePair = null
                    }

                    AudioTransitionMode.SMOOTH_FADE -> {
                        service?.stopFadingOutDeck()
                        service?.clearStandbyPreCue()
                        val halfMs = (durationMs / 2L).coerceAtLeast(180L)
                        val activePlayer: Player = service?.activeDeck ?: mc
                        val startVol = activePlayer.volume.coerceIn(0.05f, 1.0f)
                        val outStart = System.currentTimeMillis()
                        while (isActive) {
                            val p = ((System.currentTimeMillis() - outStart).toFloat() / halfMs.toFloat()).coerceIn(0f, 1f)
                            val duck = service?.focusDuckMultiplier ?: 1.0f
                            activePlayer.volume = (startVol * (1f - p * p) * duck).coerceIn(0f, 1f)
                            if (p >= 1f) break
                            delay(18L)
                        }
                        activePlayer.volume = 0.0f
                        performImmediateStep(mc, direction)
                        if (!activePlayer.isPlaying) activePlayer.play()

                        val inStart = System.currentTimeMillis()
                        while (isActive) {
                            val p = ((System.currentTimeMillis() - inStart).toFloat() / halfMs.toFloat()).coerceIn(0f, 1f)
                            val eased = 1f - (1f - p) * (1f - p)
                            val duck = service?.focusDuckMultiplier ?: 1.0f
                            activePlayer.volume = (eased * duck).coerceIn(0f, 1f)
                            if (p >= 1f) break
                            delay(18L)
                        }
                        activePlayer.volume = 1.0f * (service?.focusDuckMultiplier ?: 1.0f)
                    }

                    AudioTransitionMode.VINYL_BRAKE -> {
                        service?.stopFadingOutDeck()
                        service?.clearStandbyPreCue()
                        val activePlayer: Player = service?.activeDeck ?: mc
                        val startTime = System.currentTimeMillis()
                        while (isActive) {
                            val p = ((System.currentTimeMillis() - startTime).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            val eased = p * p
                            val curSpd = (baseSpeed * (1f - eased * 0.66f)).coerceAtLeast(0.30f)
                            val curPitch = (1f - eased * 0.62f).coerceAtLeast(0.35f)
                            val duck = service?.focusDuckMultiplier ?: 1.0f
                            activePlayer.playbackParameters = PlaybackParameters(curSpd, curPitch)
                            activePlayer.volume = ((1f - eased * 0.92f) * duck).coerceIn(0.05f, 1f)
                            if (p >= 1f) break
                            delay(20L)
                        }
                        activePlayer.playbackParameters = PlaybackParameters(baseSpeed, 1.0f)
                        activePlayer.volume = 0.15f * (service?.focusDuckMultiplier ?: 1.0f)
                        performImmediateStep(mc, direction)
                        if (!activePlayer.isPlaying) activePlayer.play()
                        val riseStart = System.currentTimeMillis()
                        while (isActive) {
                            val p = ((System.currentTimeMillis() - riseStart).toFloat() / 120f).coerceIn(0f, 1f)
                            val duck = service?.focusDuckMultiplier ?: 1.0f
                            activePlayer.volume = ((0.15f + 0.85f * p) * duck).coerceIn(0f, 1f)
                            if (p >= 1f) break
                            delay(18L)
                        }
                        activePlayer.volume = 1.0f * (service?.focusDuckMultiplier ?: 1.0f)
                    }

                    AudioTransitionMode.GAPLESS -> {
                        performImmediateStep(mc, direction)
                    }
                }
            } finally {
                isSyncingWindow = false
                if (activeCrossfadePair != null && service != null) {
                    service.finishDualDeckCrossfade(activeCrossfadePair)
                }
                val duck = service?.focusDuckMultiplier ?: 1.0f
                val activePlayer: Player = service?.activeDeck ?: mc
                runCatching {
                    activePlayer.volume = 1.0f * duck
                    activePlayer.playbackParameters = PlaybackParameters(clampPlaybackSpeed(_state.value.playbackSpeed), 1.0f)
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
        val service = DualDeckServiceBridge.current
        val player: Player = service?.activeDeck ?: mc
        val duck = service?.focusDuckMultiplier ?: 1.0f
        cancelActiveTransition(restoreVolumeAndSpeed = false)
        val softFade = transitionConfig.fadeOnPlayPause
        if (player.isPlaying) {
            if (!softFade) {
                player.volume = 1.0f * duck
                player.pause()
            } else {
                playPauseFadeJob = scope.launch {
                    val startVol = player.volume.coerceIn(0.1f, 1.0f)
                    val startTime = System.currentTimeMillis()
                    while (isActive) {
                        val p = ((System.currentTimeMillis() - startTime) / 160f).coerceIn(0f, 1f)
                        player.volume = (startVol * (1f - p)).coerceIn(0f, 1f)
                        if (p >= 1f) break
                        delay(18L)
                    }
                    player.pause()
                    player.volume = 1.0f * (DualDeckServiceBridge.current?.focusDuckMultiplier ?: 1.0f)
                    playPauseFadeJob = null
                }
            }
        } else {
            service?.requestServiceAudioFocus()
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            if (!softFade) {
                player.volume = 1.0f * duck
                player.play()
            } else {
                player.volume = 0.0f
                player.play()
                playPauseFadeJob = scope.launch {
                    val startTime = System.currentTimeMillis()
                    while (isActive) {
                        val p = ((System.currentTimeMillis() - startTime) / 170f).coerceIn(0f, 1f)
                        val curDuck = DualDeckServiceBridge.current?.focusDuckMultiplier ?: 1.0f
                        player.volume = (p * curDuck).coerceIn(0f, 1f)
                        if (p >= 1f) break
                        delay(18L)
                    }
                    player.volume = 1.0f * (DualDeckServiceBridge.current?.focusDuckMultiplier ?: 1.0f)
                    playPauseFadeJob = null
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        cancelActiveTransition(restoreVolumeAndSpeed = true)
        autoTransitionTriggeredKey = null
        val clamped = positionMs.coerceAtLeast(0L)
        (DualDeckServiceBridge.current?.activeDeck ?: controller)?.seekTo(clamped)
        _state.update { it.copy(positionMs = clamped) }
    }

    fun skipNext() {
        val mc = controller ?: return
        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        autoTransitionTriggeredKey = null
        val targetIdx = resolveTargetGlobalIndex(currentState, direction = 1)
        val incoming = targetIdx?.let { queue.getOrNull(it) } ?: currentState.nextTrack
        val resolved = resolveEffectiveTransition(
            config = transitionConfig,
            outgoing = currentState.currentTrack,
            incoming = incoming,
            isManualSkip = true
        )
        val livePlaying = DualDeckServiceBridge.current?.activeDeck?.isPlaying ?: mc.isPlaying
        if (livePlaying && resolved.mode != AudioTransitionMode.GAPLESS && resolved.durationMs > 0L) {
            launchTransitionSkip(
                direction = 1,
                resolved = resolved,
                targetGlobalIndex = targetIdx
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

        autoTransitionTriggeredKey = null
        if (queue.size <= 1) {
            cancelActiveTransition(restoreVolumeAndSpeed = true)
            (DualDeckServiceBridge.current?.activeDeck ?: mc).seekTo(0L)
            _state.update { it.copy(positionMs = 0L) }
            return
        }

        val targetIdx = resolveTargetGlobalIndex(currentState, direction = -1)
        val incoming = targetIdx?.let { queue.getOrNull(it) } ?: currentState.previousTrack
        val resolved = resolveEffectiveTransition(
            config = transitionConfig,
            outgoing = currentState.currentTrack,
            incoming = incoming,
            isManualSkip = true
        )
        val livePlaying = DualDeckServiceBridge.current?.activeDeck?.isPlaying ?: mc.isPlaying
        if (livePlaying && resolved.mode != AudioTransitionMode.GAPLESS && resolved.durationMs > 0L) {
            launchTransitionSkip(
                direction = -1,
                resolved = resolved,
                targetGlobalIndex = targetIdx
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
        setShuffleEnabled(!_state.value.shuffleEnabled)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        if (_state.value.shuffleEnabled == enabled && controller?.shuffleModeEnabled == enabled) return
        DualDeckServiceBridge.current?.clearStandbyPreCue()
        controller?.shuffleModeEnabled = enabled
        DualDeckServiceBridge.current?.activeDeck?.shuffleModeEnabled = enabled
        _state.update { it.copy(shuffleEnabled = enabled) }
    }

    fun cycleRepeatMode() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
            RepeatMode.OFF -> RepeatMode.ALL
        }
        DualDeckServiceBridge.current?.clearStandbyPreCue()
        controller?.repeatMode = toPlayerRepeatMode(next)
        _state.update { it.copy(repeatMode = next) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safe = clampPlaybackSpeed(speed)
        DualDeckServiceBridge.current?.clearStandbyPreCue()
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
