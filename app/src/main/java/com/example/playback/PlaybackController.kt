package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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

private const val TAG = "PlaybackController"
private const val MAX_SESSION_QUEUE_SIZE = 100

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

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
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
                    pendingQueue?.let { (tracks, idx) ->
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
                    val dur = mc.duration.takeIf { it > 0L }
                        ?: _state.value.currentTrack?.durationMs
                        ?: 0L

                    // Handle A-B loop repeat seamlessly
                    val loopA = _state.value.abLoopStartMs
                    val loopB = _state.value.abLoopEndMs
                    if (mc.isPlaying && loopA != null && loopB != null && loopB > loopA + 400L && pos >= loopB) {
                        mc.seekTo(loopA)
                    }

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

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
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

    private fun syncWindowAndPlay(mc: MediaController, tracks: List<Track>, targetGlobalIndex: Int) {
        val safeGlobal = targetGlobalIndex.coerceIn(0, tracks.lastIndex)
        val (winStart, winEnd) = computeWindowBounds(tracks.size, safeGlobal)
        windowStartIndex = winStart
        val localIndex = safeGlobal - winStart
        val windowTracks = tracks.subList(winStart, winEnd)
        val mediaItems = windowTracks.map { buildMediaItem(it) }

        runCatching {
            isSyncingWindow = true
            mc.volume = 1.0f
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
        if (mc.isPlaying) {
            mc.pause()
        } else {
            if (mc.playbackState == Player.STATE_ENDED) {
                mc.seekTo(0L)
            }
            mc.volume = 1.0f
            mc.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        controller?.seekTo(clamped)
        _state.update { it.copy(positionMs = clamped) }
    }

    fun skipNext() {
        val mc = controller ?: return
        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

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
            syncWindowAndPlay(mc, queue, nextGlobal)
        } else if (queue.size > MAX_SESSION_QUEUE_SIZE) {
            _state.update {
                it.copy(
                    currentIndex = 0,
                    positionMs = 0L,
                    durationMs = queue[0].durationMs
                )
            }
            syncWindowAndPlay(mc, queue, 0)
        } else if (mc.mediaItemCount > 0) {
            mc.seekTo(0, 0L)
        }
    }

    fun skipPrevious() {
        val mc = controller ?: return
        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        if (mc.currentPosition > 3500L) {
            mc.seekTo(0L)
        } else if (mc.hasPreviousMediaItem()) {
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
            syncWindowAndPlay(mc, queue, prevGlobal)
        } else if (queue.size > MAX_SESSION_QUEUE_SIZE) {
            val lastIdx = queue.lastIndex
            _state.update {
                it.copy(
                    currentIndex = lastIdx,
                    positionMs = 0L,
                    durationMs = queue[lastIdx].durationMs
                )
            }
            syncWindowAndPlay(mc, queue, lastIdx)
        } else if (mc.mediaItemCount > 0) {
            mc.seekTo(mc.mediaItemCount - 1, 0L)
        }
    }

    fun jumpToQueueIndex(index: Int) {
        val mc = controller ?: return
        val queue = _state.value.queue
        if (index !in queue.indices) return
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
            controller?.let { mc ->
                mc.stop()
                mc.clearMediaItems()
            }
            windowStartIndex = 0
            _state.update { PlaybackState() }
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

    fun removeTracksByIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        val currentState = _state.value
        if (currentState.queue.isEmpty()) return
        val remaining = currentState.queue.filterNot { it.id in ids }
        if (remaining.size == currentState.queue.size) return
        if (remaining.isEmpty()) {
            controller?.let { mc ->
                mc.stop()
                mc.clearMediaItems()
            }
            windowStartIndex = 0
            _state.update { PlaybackState() }
            return
        }
        val currentId = currentState.currentTrack?.id
        val preservedIndex = remaining.indexOfFirst { it.id == currentId }
        if (preservedIndex >= 0) {
            _state.update { it.copy(queue = remaining, currentIndex = preservedIndex) }
        } else {
            val nextIdx = currentState.currentIndex.coerceIn(0, remaining.lastIndex)
            controller?.let { mc ->
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
        val safe = speed.coerceIn(0.5f, 2.0f)
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
        tickerJob?.cancel()
        sleepTimerJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}
