package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import java.lang.ref.WeakReference

private const val SERVICE_TAG = "AuralisPlaybackService"

/**
 * In-process bridge giving [PlaybackController] direct zero-IPC access to [AuralisPlaybackService]'s
 * true dual-deck A/B ExoPlayer engine (Deck A <-> Deck B hot-swap crossfade).
 */
object DualDeckServiceBridge {
    @Volatile
    private var serviceRef: WeakReference<AuralisPlaybackService>? = null

    val current: AuralisPlaybackService?
        get() = serviceRef?.get()

    internal fun attach(service: AuralisPlaybackService) {
        serviceRef = WeakReference(service)
    }

    internal fun detach(service: AuralisPlaybackService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
    }
}

/**
 * Snapshot returned when [AuralisPlaybackService] prepares a true A/B dual-deck handoff.
 * Neither track is ever seeked mid-song or copied between players:
 *  - [outgoingDeck] continues playing the current track uninterrupted from its exact sample to silence.
 *  - [incomingDeck] plays the next track cleanly from 0:00 and stays active after the fade finishes.
 */
data class DualDeckCrossfadePair(
    val outgoingDeck: ExoPlayer,
    val incomingDeck: ExoPlayer,
    val startingOutgoingVolume: Float
)

class AuralisPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var deckA: ExoPlayer
    private lateinit var deckB: ExoPlayer
    private var activeDeckIndex: Int = 0
    private var fadingOutDeck: ExoPlayer? = null
    private var preCuedTrackKey: String? = null
    private var preCuedStartPositionMs: Long = 0L
    private var sharedAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var resumeOnFocusGain: Boolean = false

    @Volatile
    var focusDuckMultiplier: Float = 1.0f
        private set

    val activeDeck: ExoPlayer
        get() = if (activeDeckIndex == 0) deckA else deckB

    private val standbyDeck: ExoPlayer
        get() = if (activeDeckIndex == 0) deckB else deckA

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                focusDuckMultiplier = 1.0f
                if (fadingOutDeck == null) {
                    activeDeck.volume = 1.0f
                }
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    activeDeck.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeOnFocusGain = false
                stopFadingOutDeck()
                activeDeck.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = activeDeck.isPlaying
                stopFadingOutDeck()
                activeDeck.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusDuckMultiplier = 0.25f
                if (fadingOutDeck == null) {
                    activeDeck.volume = 0.25f
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("auralis_playback")
            .setChannelName(R.string.notification_channel_playback)
            .build()
        setMediaNotificationProvider(notificationProvider)

        val generatedSession = runCatching { audioManager.generateAudioSessionId() }
            .getOrDefault(AudioManager.ERROR)
        if (generatedSession != AudioManager.ERROR && generatedSession > 0) {
            sharedAudioSessionId = generatedSession
        }

        deckA = buildDeckPlayer("DeckA")
        deckB = buildDeckPlayer("DeckB")

        if (sharedAudioSessionId != C.AUDIO_SESSION_ID_UNSET && sharedAudioSessionId > 0) {
            runCatching { AcousticDspEngine.attachToSession(sharedAudioSessionId) }
        }

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, deckA)
            .setSessionActivity(pendingIntent)
            .build()

        DualDeckServiceBridge.attach(this)
    }

    @OptIn(UnstableApi::class)
    private fun buildDeckPlayer(deckLabel: String): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Expanded PCM AudioTrack buffer + standard PCM output prevent AudioFlinger underrun stutter
        // when 10-band DynamicsProcessing EQ is active on Hi-Res 96/192kHz FLAC or WAV streams.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                val bufferSizeProvider = DefaultAudioTrackBufferSizeProvider.Builder()
                    .setMinPcmBufferDurationUs(500_000)
                    .setMaxPcmBufferDurationUs(1_200_000)
                    .setPcmBufferMultiplicationFactor(4)
                    .build()
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioTrackBufferSizeProvider(bufferSizeProvider)
                    .build()
            }
        }

        // handleAudioFocus = false so Deck A and Deck B NEVER steal AudioFocus from each other
        // during overlapping crossfades; service-level AudioManager focus coordinates both decks cleanly.
        val exo = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        if (sharedAudioSessionId != C.AUDIO_SESSION_ID_UNSET && sharedAudioSessionId > 0) {
            runCatching { exo.audioSessionId = sharedAudioSessionId }
                .onFailure { Log.w(SERVICE_TAG, "Unable to bind shared audioSessionId on $deckLabel", it) }
        }

        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (exo === activeDeck && audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
                    runCatching { AcousticDspEngine.attachToSession(audioSessionId) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (exo === activeDeck) {
                    if (isPlaying) {
                        requestServiceAudioFocus()
                        val sid = exo.audioSessionId
                        if (sid != C.AUDIO_SESSION_ID_UNSET && sid > 0) {
                            runCatching { AcousticDspEngine.attachToSession(sid) }
                        }
                    } else if (!exo.playWhenReady && fadingOutDeck != null) {
                        // User paused via notification / headset mid-crossfade: silence dying tail immediately
                        stopFadingOutDeck()
                        exo.volume = 1.0f * focusDuckMultiplier
                    }
                }
            }
        })
        return exo
    }

    fun requestServiceAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonServiceAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        resumeOnFocusGain = false
    }

    /**
     * Resolves the real next/previous local MediaItem index on [activeDeck], honoring shuffle order.
     */
    fun resolveAdjacentLocalIndex(direction: Int): Int {
        val deck = activeDeck
        return if (direction >= 0) deck.nextMediaItemIndex else deck.previousMediaItemIndex
    }

    /**
     * Silently pre-decodes the incoming track at [startPositionMs] on [standbyDeck] while [activeDeck]
     * continues playing the current track completely untouched.
     */
    fun preCueStandbyDeck(
        mediaItems: List<MediaItem>,
        targetLocalIndex: Int,
        speed: Float,
        repeatMode: Int,
        shuffleEnabled: Boolean,
        trackKey: String,
        startPositionMs: Long = 0L
    ) {
        if (fadingOutDeck != null) return
        val safeStartMs = startPositionMs.coerceAtLeast(0L)
        if (preCuedTrackKey == trackKey &&
            preCuedStartPositionMs == safeStartMs &&
            standbyDeck.mediaItemCount > 0
        ) return
        if (targetLocalIndex !in mediaItems.indices) return

        val standby = standbyDeck
        runCatching {
            standby.stop()
            standby.volume = 0.0f
            standby.repeatMode = repeatMode
            standby.shuffleModeEnabled = shuffleEnabled
            standby.playbackParameters = PlaybackParameters(speed, 1.0f)
            standby.setMediaItems(mediaItems, targetLocalIndex, safeStartMs)
            standby.playWhenReady = false
            standby.prepare()
            preCuedTrackKey = trackKey
            preCuedStartPositionMs = safeStartMs
        }.onFailure {
            Log.w(SERVICE_TAG, "Failed pre-cueing standby deck", it)
            preCuedTrackKey = null
            preCuedStartPositionMs = 0L
        }
    }

    /**
     * Clears any unused pre-cued standby deck (e.g., on user seek, mode change, or new queue).
     */
    fun clearStandbyPreCue() {
        if (fadingOutDeck != null) return
        if (preCuedTrackKey != null) {
            runCatching {
                standbyDeck.stop()
                standbyDeck.clearMediaItems()
                standbyDeck.volume = 1.0f
            }
            preCuedTrackKey = null
            preCuedStartPositionMs = 0L
        }
    }

    /**
     * Stops and clears any outgoing deck that is still fading out.
     */
    fun stopFadingOutDeck() {
        val dying = fadingOutDeck ?: return
        fadingOutDeck = null
        runCatching {
            dying.stop()
            dying.clearMediaItems()
            dying.volume = 1.0f
        }
    }

    /**
     * Ensures [standbyDeck] is cued at [startPositionMs] of the incoming track (reusing any pre-cued buffer
     * if [trackKey] and [startPositionMs] match) without touching [activeDeck] at all.
     */
    fun prepareIncomingDeckForCrossfade(
        mediaItems: List<MediaItem>,
        targetLocalIndex: Int,
        speed: Float,
        repeatMode: Int,
        shuffleEnabled: Boolean,
        trackKey: String,
        startPositionMs: Long = 0L
    ): DualDeckCrossfadePair? {
        if (targetLocalIndex !in mediaItems.indices) return null
        val safeStartMs = startPositionMs.coerceAtLeast(0L)

        // If a previous crossfade's dying tail was still running on standbyDeck, stop it cleanly first
        if (fadingOutDeck != null) {
            stopFadingOutDeck()
            preCuedTrackKey = null
            preCuedStartPositionMs = 0L
        }

        val outgoing = activeDeck
        val incoming = standbyDeck
        val alreadyPreCued = preCuedTrackKey == trackKey &&
            preCuedStartPositionMs == safeStartMs &&
            incoming.mediaItemCount == mediaItems.size &&
            incoming.currentMediaItemIndex == targetLocalIndex &&
            incoming.playbackState != Player.STATE_IDLE &&
            incoming.playbackState != Player.STATE_ENDED

        if (!alreadyPreCued) {
            runCatching {
                incoming.stop()
                incoming.volume = 0.0f
                incoming.repeatMode = repeatMode
                incoming.shuffleModeEnabled = shuffleEnabled
                incoming.playbackParameters = PlaybackParameters(speed, 1.0f)
                incoming.setMediaItems(mediaItems, targetLocalIndex, safeStartMs)
                incoming.playWhenReady = false
                incoming.prepare()
            }.onFailure {
                Log.e(SERVICE_TAG, "Failed preparing incoming deck for crossfade", it)
                return null
            }
        } else {
            incoming.volume = 0.0f
            incoming.repeatMode = repeatMode
            incoming.shuffleModeEnabled = shuffleEnabled
            incoming.playbackParameters = PlaybackParameters(speed, 1.0f)
        }
        preCuedTrackKey = null
        preCuedStartPositionMs = 0L

        val currentOutVol = outgoing.volume.coerceIn(0.05f, 1.0f)
        return DualDeckCrossfadePair(
            outgoingDeck = outgoing,
            incomingDeck = incoming,
            startingOutgoingVolume = currentOutVol
        )
    }

    /**
     * Atomically promotes [pair.incomingDeck] to be the primary active deck and binds [MediaSession]
     * to it while [pair.outgoingDeck] continues playing uninterrupted as [fadingOutDeck].
     */
    fun commitDualDeckSwap(pair: DualDeckCrossfadePair) {
        fadingOutDeck = pair.outgoingDeck
        activeDeckIndex = if (pair.incomingDeck === deckA) 0 else 1
        mediaSession?.setPlayer(pair.incomingDeck)
        val sid = pair.incomingDeck.audioSessionId
        if (sid != C.AUDIO_SESSION_ID_UNSET && sid > 0) {
            runCatching { AcousticDspEngine.attachToSession(sid) }
        }
    }

    /**
     * Finalizes a completed crossfade: stops & clears the faded-out deck and locks active deck gain.
     */
    fun finishDualDeckCrossfade(pair: DualDeckCrossfadePair) {
        if (fadingOutDeck === pair.outgoingDeck) {
            fadingOutDeck = null
        }
        runCatching {
            pair.outgoingDeck.stop()
            pair.outgoingDeck.clearMediaItems()
            pair.outgoingDeck.volume = 1.0f
        }
        runCatching {
            if (activeDeck === pair.incomingDeck) {
                pair.incomingDeck.volume = 1.0f * focusDuckMultiplier
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        DualDeckServiceBridge.detach(this)
        stopFadingOutDeck()
        abandonServiceAudioFocus()
        AcousticDspEngine.release()
        mediaSession?.release()
        mediaSession = null
        runCatching { deckA.release() }
        runCatching { deckB.release() }
        super.onDestroy()
    }
}
