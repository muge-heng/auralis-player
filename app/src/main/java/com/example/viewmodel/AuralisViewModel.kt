package com.example.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiApiException
import com.example.data.ai.AiTaskType
import com.example.data.ai.DeepSeekChatClient
import com.example.data.local.AuralisDatabase
import com.example.data.local.ParsedSettingsBackup
import com.example.data.local.PreferencesRepository
import com.example.data.local.SettingsBackupCodec
import com.example.data.lyrics.AudioLyricsTagWriter
import com.example.data.lyrics.AudioTagWriteOutcome
import com.example.data.lyrics.LyricParser
import com.example.data.lyrics.OnlineLyricClient
import com.example.data.scanner.DuplicateTrackGroup
import com.example.data.scanner.ImportConflictDecision
import com.example.data.scanner.ImportQualityConflict
import com.example.data.scanner.LocalMusicScanner
import com.example.data.scanner.MetadataExtractor
import com.example.data.scanner.ScanProgress
import com.example.data.scanner.TrackDeduplicator
import com.example.model.AiAttachmentBadgeCodec
import com.example.model.AiCatalogAttachmentPrefs
import com.example.model.AiChatMessageEntity
import com.example.model.AiChatSessionEntity
import com.example.model.AiConfig
import com.example.model.AiErrorReport
import com.example.model.AiSessionRuntimeState
import com.example.model.AiSuggestedPlaylist
import com.example.model.AudioBookmark
import com.example.model.AudioTransitionConfig
import com.example.model.AuraPalette
import com.example.model.CustomLyricEntity
import com.example.model.DangerZoneAction
import com.example.model.DeletionGuardMode
import com.example.model.EqPreset
import com.example.model.EqState
import com.example.model.HapticProfile
import com.example.model.LibraryFilterFormat
import com.example.model.LibrarySortOption
import com.example.model.LyricDocument
import com.example.model.LyricFormat
import com.example.model.LyricSettings
import com.example.model.LyricSource
import com.example.model.NavBarStyle
import com.example.model.OnlineLyricCandidate
import com.example.model.PlaylistEntity
import com.example.model.PlaylistTrackCrossRef
import com.example.model.PlaylistWithCount
import com.example.model.ReasoningEffortLevel
import coil.imageLoader
import com.example.model.ScanFilterConfig
import com.example.model.SmartLibrarySorter
import com.example.model.Track
import com.example.model.TrackMetadataOverride
import com.example.model.normalizeEqBands
import com.example.playback.AcousticDspEngine
import com.example.playback.HapticEngine
import com.example.playback.PaletteExtractor
import com.example.playback.PlaybackController
import com.example.playback.PlaybackState
import com.example.ui.components.buildTrackArtworkRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.ui.components.PendingConfirmDeletion
import com.example.ui.components.StatusToastLevel
import com.example.ui.components.StatusToastState
import com.example.ui.components.UndoBannerState
import com.example.ui.components.inferStatusToastLevel

private const val TAG = "AuralisViewModel"

data class OnlineLyricSearchState(
    val isSearching: Boolean = false,
    val candidates: List<OnlineLyricCandidate> = emptyList(),
    val sourceSummaries: Map<LyricSource, String> = emptyMap(),
    val error: String? = null
)

data class PendingLyricExportRequest(
    val track: Track,
    val suggestedFileName: String,
    val mimeType: String,
    val rawContent: String
)

data class PendingBurnConfirmRequest(
    val track: Track,
    val sourceLabel: String,
    val lyricsText: String
)

data class PendingAudioWritePermissionRequest(
    val track: Track,
    val lyricsText: String,
    val outcome: AudioTagWriteOutcome,
    val intentSender: IntentSender
)

data class PendingAudioSaveCopyRequest(
    val track: Track,
    val lyricsText: String,
    val suggestedFileName: String,
    val mimeType: String,
    val patchedBytes: ByteArray
)

data class PendingSettingsBackupExportRequest(
    val suggestedFileName: String,
    val includeApiKey: Boolean
)

data class PendingSettingsBackupImportPreview(
    val fileName: String,
    val parsed: ParsedSettingsBackup
)

class AuralisViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AuralisDatabase.getInstance(context)
    private val trackDao = db.trackDao()
    private val playlistDao = db.playlistDao()
    private val customLyricDao = db.customLyricDao()
    private val aiChatDao = db.aiChatDao()

    private val prefs = PreferencesRepository(context)
    private val metadataExtractor = MetadataExtractor(context)
    private val scanner = LocalMusicScanner(context, metadataExtractor)
    private val onlineLyricClient = OnlineLyricClient()
    private val aiChatClient = DeepSeekChatClient()

    val haptics = HapticEngine(context)

    val playbackController = PlaybackController(
        context = context,
        scope = viewModelScope,
        onTrackChanged = { track -> handleTrackActivated(track) },
        onPlaybackError = { msg -> showBanner(msg) },
        resolveTrackLyrics = { track -> resolveLocalLyricsForBridge(track) }
    )

    val playbackState: StateFlow<PlaybackState> = playbackController.state

    val darkTheme: StateFlow<Boolean> = prefs.darkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hapticProfile: StateFlow<HapticProfile> = prefs.hapticProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HapticProfile.STUDIO)

    val navBarStyle: StateFlow<NavBarStyle> = prefs.navBarStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavBarStyle.FLOATING_GLASS)

    val visualEffectsEnhanced: StateFlow<Boolean> = prefs.visualEffectsEnhancedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showLibraryHero: StateFlow<Boolean> = prefs.showLibraryHeroFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showGeneralAudioNotice: StateFlow<Boolean> = prefs.showGeneralAudioNoticeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enableGeneralAudioMode: StateFlow<Boolean> = prefs.enableGeneralAudioModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val deletionGuardMode: StateFlow<DeletionGuardMode> = prefs.deletionGuardModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeletionGuardMode.CONFIRM_DIALOG)

    val scanFilter: StateFlow<ScanFilterConfig> = prefs.scanFilterFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanFilterConfig())

    val lyricSettings: StateFlow<LyricSettings> = prefs.lyricSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricSettings())

    val audioTransition: StateFlow<AudioTransitionConfig> = prefs.audioTransitionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioTransitionConfig())

    val eqState: StateFlow<EqState> = prefs.eqStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EqState())

    val customEqPresets: StateFlow<List<EqPreset>> = prefs.customEqPresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiConfig: StateFlow<AiConfig> = prefs.aiConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiConfig())

    val aiCatalogAttachmentPrefs: StateFlow<AiCatalogAttachmentPrefs> = prefs.aiCatalogAttachmentPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiCatalogAttachmentPrefs())

    val playlistCoverConfigs: StateFlow<Map<String, com.example.model.PlaylistCoverConfig>> = prefs.playlistCoverConfigsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val audioBookmarks: StateFlow<Map<String, List<AudioBookmark>>> = prefs.audioBookmarksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val trackMetadataOverrides: StateFlow<Map<String, TrackMetadataOverride>> = prefs.trackMetadataOverridesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isBootDataReady = MutableStateFlow(false)
    val isBootDataReady: StateFlow<Boolean> = _isBootDataReady.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow<Boolean?>(null)
    val hasCompletedOnboarding: StateFlow<Boolean?> = _hasCompletedOnboarding.asStateFlow()

    val allTracks: StateFlow<List<Track>> = trackDao.observeAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val musicTracks: StateFlow<List<Track>> = allTracks
        .map { list -> list.filter { !it.isGeneralAudio } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generalAudioTracks: StateFlow<List<Track>> = allTracks
        .map { list -> list.filter { it.isGeneralAudio } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val duplicateGroups: StateFlow<List<DuplicateTrackGroup>> = allTracks
        .map { TrackDeduplicator.detectLibraryDuplicates(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pendingImportConflicts = MutableStateFlow<List<ImportQualityConflict>>(emptyList())
    val pendingImportConflicts: StateFlow<List<ImportQualityConflict>> = _pendingImportConflicts.asStateFlow()

    private val _pendingImportSourceLabel = MutableStateFlow("")
    val pendingImportSourceLabel: StateFlow<String> = _pendingImportSourceLabel.asStateFlow()

    val favoriteTracks: StateFlow<List<Track>> = trackDao.observeFavoriteTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = trackDao.observeRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<Track>> = trackDao.observeMostPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistWithCount>> = combine(
        playlistDao.observePlaylists(),
        playlistDao.observeAllRefs(),
        musicTracks
    ) { list, refs, tracks ->
        val tracksById = tracks.associateBy { it.id }
        val refsByPlaylist = refs.groupBy { it.playlistId }
        list.map { pl ->
            val plRefs = refsByPlaylist[pl.id].orEmpty().filter { tracksById.containsKey(it.trackId) }
            val firstCover = plRefs.firstNotNullOfOrNull { tracksById[it.trackId]?.artworkPath ?: tracksById[it.trackId]?.albumArtUri }
            PlaylistWithCount(pl, plRefs.size, firstCover)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val playlistRefsFlow = playlistDao.observeAllRefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistTracksMap: StateFlow<Map<Long, List<Track>>> = combine(
        playlistRefsFlow,
        musicTracks
    ) { refs, tracks ->
        val trackMap = tracks.associateBy { it.id }
        refs.groupBy { it.playlistId }.mapValues { (_, plRefs) ->
            plRefs.mapNotNull { trackMap[it.trackId] }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Library search, format filter, sort, folder/album/artist filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _formatFilter = MutableStateFlow(LibraryFilterFormat.ALL)
    val formatFilter: StateFlow<LibraryFilterFormat> = _formatFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(LibrarySortOption.RECENT)
    val sortOption: StateFlow<LibrarySortOption> = _sortOption.asStateFlow()

    private val _smartSortSeed = MutableStateFlow(System.nanoTime())

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<String?>(null)
    val selectedAlbum: StateFlow<String?> = _selectedAlbum.asStateFlow()

    private val _selectedArtist = MutableStateFlow<String?>(null)
    val selectedArtist: StateFlow<String?> = _selectedArtist.asStateFlow()

    private data class LibraryFilterCriteria(
        val query: String,
        val fmt: LibraryFilterFormat,
        val sort: LibrarySortOption,
        val smartSeed: Long,
        val folder: String?,
        val album: String?,
        val artist: String?
    )

    private val libraryCriteriaFlow = combine(
        combine(_searchQuery, _formatFilter, _sortOption, _smartSortSeed) { q, f, s, seed ->
            LibraryFilterCriteria(q, f, s, seed, null, null, null)
        },
        combine(_selectedFolder, _selectedAlbum, _selectedArtist) { folder, album, artist ->
            Triple(folder, album, artist)
        }
    ) { base, (folder, album, artist) ->
        base.copy(folder = folder, album = album, artist = artist)
    }

    val filteredTracks: StateFlow<List<Track>> = combine(
        musicTracks,
        libraryCriteriaFlow
    ) { tracks, criteria ->
        applyLibraryFilter(tracks, criteria)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredGeneralAudioTracks: StateFlow<List<Track>> = combine(
        generalAudioTracks,
        libraryCriteriaFlow
    ) { tracks, criteria ->
        applyLibraryFilter(tracks, criteria.copy(album = null, artist = null))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun applyLibraryFilter(
        tracks: List<Track>,
        criteria: LibraryFilterCriteria
    ): List<Track> {
        val q = criteria.query.trim().lowercase()
        return tracks.filter { t ->
            val matchesQuery = q.isEmpty() ||
                t.title.lowercase().contains(q) ||
                t.artist.lowercase().contains(q) ||
                t.album.lowercase().contains(q) ||
                t.codecLabel.lowercase().contains(q) ||
                t.fileName.lowercase().contains(q)
            val matchesFmt = when (criteria.fmt) {
                LibraryFilterFormat.ALL -> true
                LibraryFilterFormat.LOSSLESS -> t.isLossless
                LibraryFilterFormat.HIRES -> t.isHiRes
                LibraryFilterFormat.LOSSY -> !t.isLossless
            }
            val matchesFolder = criteria.folder == null || t.folderPath == criteria.folder
            val matchesAlbum = criteria.album == null ||
                t.album.ifBlank { "未知专辑" } == criteria.album
            val matchesArtist = criteria.artist == null ||
                t.artist.ifBlank { "未知艺术家" } == criteria.artist
            matchesQuery && matchesFmt && matchesFolder && matchesAlbum && matchesArtist
        }.let { list ->
            when (criteria.sort) {
                LibrarySortOption.SMART -> SmartLibrarySorter.sort(list, sessionSeed = criteria.smartSeed)
                LibrarySortOption.RECENT -> list.sortedByDescending { it.dateAddedSec }
                LibrarySortOption.TITLE -> list.sortedBy { it.title.lowercase() }
                LibrarySortOption.ARTIST -> list.sortedBy { it.artist.lowercase() }
                LibrarySortOption.DURATION -> list.sortedByDescending { it.durationMs }
                LibrarySortOption.PLAY_COUNT -> list.sortedByDescending { it.playCount }
            }
        }
    }

    // Dynamic UI states
    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _auraPalette = MutableStateFlow(AuraPalette())
    val auraPalette: StateFlow<AuraPalette> = _auraPalette.asStateFlow()

    private val _currentLyric = MutableStateFlow(LyricDocument(LyricFormat.NONE, emptyList()))
    val currentLyric: StateFlow<LyricDocument> = _currentLyric.asStateFlow()

    private val _onlineLyricSearch = MutableStateFlow(OnlineLyricSearchState())
    val onlineLyricSearch: StateFlow<OnlineLyricSearchState> = _onlineLyricSearch.asStateFlow()

    // Multi-session independent AI conversation states
    val aiSessions: StateFlow<List<AiChatSessionEntity>> = aiChatDao.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeAiSessionId = MutableStateFlow(0L)
    val activeAiSessionId: StateFlow<Long> = _activeAiSessionId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeAiMessages: StateFlow<List<AiChatMessageEntity>> = _activeAiSessionId
        .flatMapLatest { sessionId ->
            if (sessionId > 0L) aiChatDao.observeMessagesForSession(sessionId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiSessionRuntimeStates = MutableStateFlow<Map<Long, AiSessionRuntimeState>>(emptyMap())
    val aiSessionRuntimeStates: StateFlow<Map<Long, AiSessionRuntimeState>> = _aiSessionRuntimeStates.asStateFlow()

    private val aiSessionJobs = mutableMapOf<Long, Job>()

    private val _statusBanner = MutableStateFlow<StatusToastState?>(null)
    val statusBanner: StateFlow<StatusToastState?> = _statusBanner.asStateFlow()

    private val _undoBanner = MutableStateFlow<UndoBannerState?>(null)
    val undoBanner: StateFlow<UndoBannerState?> = _undoBanner.asStateFlow()

    private val _pendingConfirmDeletion = MutableStateFlow<PendingConfirmDeletion?>(null)
    val pendingConfirmDeletion: StateFlow<PendingConfirmDeletion?> = _pendingConfirmDeletion.asStateFlow()

    private val _pendingLyricExport = MutableStateFlow<PendingLyricExportRequest?>(null)
    val pendingLyricExport: StateFlow<PendingLyricExportRequest?> = _pendingLyricExport.asStateFlow()

    private val _pendingBurnConfirm = MutableStateFlow<PendingBurnConfirmRequest?>(null)
    val pendingBurnConfirm: StateFlow<PendingBurnConfirmRequest?> = _pendingBurnConfirm.asStateFlow()

    private val _pendingAudioWritePermission = MutableStateFlow<PendingAudioWritePermissionRequest?>(null)
    val pendingAudioWritePermission: StateFlow<PendingAudioWritePermissionRequest?> = _pendingAudioWritePermission.asStateFlow()

    private val _pendingAudioSaveCopy = MutableStateFlow<PendingAudioSaveCopyRequest?>(null)
    val pendingAudioSaveCopy: StateFlow<PendingAudioSaveCopyRequest?> = _pendingAudioSaveCopy.asStateFlow()

    private val _pendingSettingsBackupExport = MutableStateFlow<PendingSettingsBackupExportRequest?>(null)
    val pendingSettingsBackupExport: StateFlow<PendingSettingsBackupExportRequest?> = _pendingSettingsBackupExport.asStateFlow()

    private val _pendingSettingsBackupImport = MutableStateFlow<PendingSettingsBackupImportPreview?>(null)
    val pendingSettingsBackupImport: StateFlow<PendingSettingsBackupImportPreview?> = _pendingSettingsBackupImport.asStateFlow()

    private val _openNowPlayingTrigger = MutableStateFlow(false)
    val openNowPlayingTrigger: StateFlow<Boolean> = _openNowPlayingTrigger.asStateFlow()

    private var lyricLoadJob: Job? = null
    private var paletteJob: Job? = null
    private var lastActivatedTrackId: String? = null
    private var lastActivatedAtMs: Long = 0L

    init {
        playbackController.connect()
        // Cold-start hydration: wait for preferences, initial Room tracks snapshot, AND pre-warm
        // the top visible album covers in Coil memory cache before lifting our custom splash curtain
        // so there is NEVER an empty-library flash or uncached-cover frame.
        viewModelScope.launch {
            try {
                withTimeoutOrNull(900L) {
                    val onboardedDeferred = async(Dispatchers.IO) {
                        runCatching { prefs.hasCompletedOnboardingFlow.first() }.getOrDefault(false)
                    }
                    val tracksDeferred = async(Dispatchers.IO) {
                        runCatching { trackDao.observeAllTracks().first() }.getOrDefault(emptyList())
                    }
                    val onboarded = onboardedDeferred.await()
                    val initialTracks = tracksDeferred.await()
                    _hasCompletedOnboarding.value = onboarded

                    // Pre-warm top 16 covers into Coil's memory cache so Frame 1 renders real covers instantly
                    val topCovers = initialTracks.filter { !it.isGeneralAudio }.take(16)
                    if (topCovers.isNotEmpty()) {
                        val loader = context.imageLoader
                        topCovers.map { track ->
                            async(Dispatchers.IO) {
                                buildTrackArtworkRequest(
                                    context = context,
                                    artworkPath = track.artworkPath,
                                    albumArtUri = track.albumArtUri,
                                    decodeSizePx = 256
                                )?.let { req ->
                                    runCatching { loader.execute(req) }
                                }
                            }
                        }.awaitAll()
                    }
                }
            } finally {
                if (_hasCompletedOnboarding.value == null) {
                    _hasCompletedOnboarding.value = runCatching {
                        prefs.hasCompletedOnboardingFlow.first()
                    }.getOrDefault(false)
                }
                _isBootDataReady.value = true
            }
        }
        viewModelScope.launch {
            prefs.hasCompletedOnboardingFlow.collect { completed ->
                if (_isBootDataReady.value) {
                    _hasCompletedOnboarding.value = completed
                }
            }
        }
        viewModelScope.launch {
            val existing = runCatching { aiChatDao.getSessionsSnapshot() }.getOrDefault(emptyList())
            if (existing.isEmpty()) {
                val cfg = prefs.aiConfigFlow.first()
                val createdId = aiChatDao.insertSession(
                    AiChatSessionEntity(
                        title = "声学与音乐对话",
                        modelId = cfg.deepSeekModelId,
                        reasoningEffort = cfg.reasoningEffort.apiValue
                    )
                )
                _activeAiSessionId.value = createdId
            } else {
                _activeAiSessionId.value = existing.first().id
            }
        }
        viewModelScope.launch {
            prefs.hapticProfileFlow.collect { profile ->
                haptics.profile = profile
            }
        }
        viewModelScope.launch {
            prefs.eqStateFlow.collect { state ->
                AcousticDspEngine.applyState(state)
            }
        }
        viewModelScope.launch {
            prefs.audioTransitionFlow.collect { config ->
                playbackController.transitionConfig = config
            }
        }
        viewModelScope.launch {
            // ONLY re-parse active lyric when enableSimulatedWordByWord actually flips!
            // Prevents lag when adjusting fontScale / fontWeight / blur.
            prefs.lyricSettingsFlow
                .map { it.enableSimulatedWordByWord }
                .distinctUntilChanged()
                .collect { enableSim ->
                    val currentDoc = _currentLyric.value
                    if (currentDoc.rawContent.isNotBlank()) {
                        _currentLyric.value = LyricParser.parse(
                            rawInput = currentDoc.rawContent,
                            sourceLabel = currentDoc.sourceLabel,
                            userOffsetMs = currentDoc.globalOffsetMs,
                            enableSimulatedWordByWord = enableSim
                        )
                    }
                }
        }
    }

    fun consumeOpenNowPlayingTrigger() {
        _openNowPlayingTrigger.value = false
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String, level: StatusToastLevel = inferStatusToastLevel(msg)) {
        if (msg.isBlank()) return
        _statusBanner.value = StatusToastState(message = msg, level = level)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFormatFilter(format: LibraryFilterFormat) {
        haptics.tickLight()
        _formatFilter.value = format
    }

    fun completeOnboarding() {
        _hasCompletedOnboarding.value = true
        viewModelScope.launch {
            prefs.setOnboardingCompleted(true)
        }
    }

    fun updateSortOption(option: LibrarySortOption) {
        haptics.tickLight()
        if (option == LibrarySortOption.SMART) {
            // Re-selecting or entering Smart Sort refreshes the dynamic session seed
            _smartSortSeed.value = System.nanoTime()
        }
        _sortOption.value = option
    }

    fun selectFolder(folder: String?) {
        haptics.tickLight()
        _selectedFolder.value = folder
        if (folder != null) {
            _selectedAlbum.value = null
            _selectedArtist.value = null
        }
    }

    fun selectAlbum(album: String?) {
        haptics.tickLight()
        _selectedAlbum.value = album
        if (album != null) {
            _selectedFolder.value = null
            _selectedArtist.value = null
        }
    }

    fun selectArtist(artist: String?) {
        haptics.tickLight()
        _selectedArtist.value = artist
        if (artist != null) {
            _selectedFolder.value = null
            _selectedAlbum.value = null
        }
    }

    fun clearGroupedDrilldownFilters() {
        _selectedFolder.value = null
        _selectedAlbum.value = null
        _selectedArtist.value = null
    }

    fun scanDeviceLibraryWithConfig(config: ScanFilterConfig) {
        viewModelScope.launch {
            prefs.updateScanFilter(config)
            scanDeviceLibrary()
        }
    }

    fun scanDeviceLibrary() {
        if (_scanProgress.value.isScanning) return
        viewModelScope.launch {
            _scanProgress.value = ScanProgress(isScanning = true)
            try {
                val filter = prefs.scanFilterFlow.first().normalized()
                val existingList = trackDao.getAllTracksSnapshot()
                val existingById = existingList.associateBy { it.id }
                val overrides = prefs.getTrackMetadataOverridesSnapshot()
                val customCoverTrackIds = overrides.values
                    .filter { MetadataExtractor.hasVerifiedArtworkFile(it.customArtworkPath) }
                    .map { it.trackId }
                    .toSet()

                val scanned = scanner.scanDeviceMediaStore(
                    filter = filter,
                    existingTracksById = existingById,
                    customCoverTrackIds = customCoverTrackIds,
                    onProgress = { _scanProgress.value = it }
                )
                val scannedIds = scanned.mapTo(HashSet(scanned.size)) { it.id }

                val analysis = withContext(Dispatchers.Default) {
                    TrackDeduplicator.analyzeImportCandidates(
                        incoming = scanned,
                        existing = existingList,
                        syncGeneralAudioZone = true
                    )
                }
                val toUpsert = enrichTracksWithArchivedHistory(analysis.updatedExistingTracks + analysis.newTracks)
                if (toUpsert.isNotEmpty()) {
                    trackDao.upsertAll(toUpsert)
                }

                // Prune previously MediaStore-scanned tracks that no longer satisfy the active scan rules
                // (e.g. coverless tracks when "仅筛选含有效内嵌封面" is checked and general-audio fallback is off,
                // or tracks outside duration bounds / inside newly excluded folders).
                val filteredOutTracks = existingList.filter { existing ->
                    existing.id.startsWith("ms_") &&
                        existing.id !in scannedIds &&
                        existing.id !in customCoverTrackIds
                }
                if (filteredOutTracks.isNotEmpty()) {
                    prefs.archiveTrackRecords(filteredOutTracks)
                    trackDao.deleteTracksByIds(filteredOutTracks.map { it.id })
                }

                val demotedToGeneralCount = analysis.updatedExistingTracks.count { updated ->
                    updated.isGeneralAudio && existingById[updated.id]?.isGeneralAudio == false
                }

                if (analysis.qualityConflicts.isNotEmpty()) {
                    _pendingImportSourceLabel.value = "智能扫描"
                    _pendingImportConflicts.value = analysis.qualityConflicts
                }
                val newMusicCount = analysis.newTracks.count { !it.isGeneralAudio }
                val newGeneralCount = analysis.newTracks.count { it.isGeneralAudio }
                haptics.harmonicChime()
                showBanner(
                    buildImportSummaryBanner(
                        prefix = "智能扫描完成",
                        newMusicCount = newMusicCount,
                        newGeneralCount = newGeneralCount,
                        updatedCount = (analysis.updatedExistingTracks.size - demotedToGeneralCount).coerceAtLeast(0),
                        skippedCount = analysis.autoSkippedCount,
                        conflictCount = analysis.qualityConflicts.size,
                        demotedCount = demotedToGeneralCount,
                        removedCount = filteredOutTracks.size
                    )
                )
            } catch (e: SecurityException) {
                showBanner("尚未获得本地音频访问权限，请在系统弹窗授权或使用「导入音频」")
            } catch (e: Exception) {
                Log.e(TAG, "Scan failed", e)
                showBanner("扫描异常：${e.localizedMessage ?: "未知错误"}")
            } finally {
                _scanProgress.update { it.copy(isScanning = false) }
            }
        }
    }

    fun importAudioFiles(uris: List<Uri>, playImmediately: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _scanProgress.value = ScanProgress(isScanning = true, currentFile = "正在解析元数据与查重...")
            val imported = scanner.importAudioUris(uris)
            if (imported.isNotEmpty()) {
                val existingList = trackDao.getAllTracksSnapshot()
                val analysis = withContext(Dispatchers.Default) {
                    TrackDeduplicator.analyzeImportCandidates(imported, existingList)
                }
                val toUpsert = enrichTracksWithArchivedHistory(analysis.updatedExistingTracks + analysis.newTracks)
                if (toUpsert.isNotEmpty()) {
                    trackDao.upsertAll(toUpsert)
                }
                if (analysis.qualityConflicts.isNotEmpty()) {
                    _pendingImportSourceLabel.value = "单曲导入"
                    _pendingImportConflicts.value = analysis.qualityConflicts
                }
                val newMusicCount = analysis.newTracks.count { !it.isGeneralAudio }
                val newGeneralCount = analysis.newTracks.count { it.isGeneralAudio }
                haptics.harmonicChime()
                showBanner(
                    buildImportSummaryBanner(
                        prefix = "音频导入完成",
                        newMusicCount = newMusicCount,
                        newGeneralCount = newGeneralCount,
                        updatedCount = analysis.updatedExistingTracks.size,
                        skippedCount = analysis.autoSkippedCount,
                        conflictCount = analysis.qualityConflicts.size
                    )
                )
                if (playImmediately) {
                    val targetToPlay = toUpsert.firstOrNull { t -> analysis.newTracks.any { it.id == t.id } }
                        ?: existingList.firstOrNull { TrackDeduplicator.areSameSong(it, imported.first()) }
                        ?: imported.first()
                    val queueCandidates = toUpsert.filter { t -> analysis.newTracks.any { it.id == t.id } }
                        .ifEmpty { listOf(targetToPlay) }
                    playTrack(targetToPlay, queueCandidates)
                    _openNowPlayingTrigger.value = true
                }
            } else {
                showBanner("未能解析所选音频文件")
            }
            _scanProgress.value = ScanProgress(isScanning = false)
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            _scanProgress.value = ScanProgress(isScanning = true, currentFile = "正在扫描文件夹并查重...")
            val filter = prefs.scanFilterFlow.first()
            val imported = scanner.importFolderTree(treeUri, filter) { _scanProgress.value = it }
            if (imported.isNotEmpty()) {
                val existingList = trackDao.getAllTracksSnapshot()
                val analysis = withContext(Dispatchers.Default) {
                    TrackDeduplicator.analyzeImportCandidates(imported, existingList)
                }
                val toUpsert = enrichTracksWithArchivedHistory(analysis.updatedExistingTracks + analysis.newTracks)
                if (toUpsert.isNotEmpty()) {
                    trackDao.upsertAll(toUpsert)
                }
                if (analysis.qualityConflicts.isNotEmpty()) {
                    _pendingImportSourceLabel.value = "文件夹导入"
                    _pendingImportConflicts.value = analysis.qualityConflicts
                }
                val newMusicCount = analysis.newTracks.count { !it.isGeneralAudio }
                val newGeneralCount = analysis.newTracks.count { it.isGeneralAudio }
                haptics.harmonicChime()
                showBanner(
                    buildImportSummaryBanner(
                        prefix = "文件夹导入完成",
                        newMusicCount = newMusicCount,
                        newGeneralCount = newGeneralCount,
                        updatedCount = analysis.updatedExistingTracks.size,
                        skippedCount = analysis.autoSkippedCount,
                        conflictCount = analysis.qualityConflicts.size
                    )
                )
            } else {
                showBanner("所选文件夹中没有符合筛选规则的音频")
            }
            _scanProgress.value = ScanProgress(isScanning = false)
        }
    }

    private suspend fun enrichTracksWithArchivedHistory(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return tracks
        val archived = prefs.getArchivedTrackRecords()
        val withHistory = if (archived.isEmpty()) {
            tracks
        } else {
            PreferencesRepository.applyArchivedRecordsToTracks(tracks, archived)
        }
        val overrides = prefs.getTrackMetadataOverridesSnapshot()
        return if (overrides.isEmpty()) {
            withHistory
        } else {
            PreferencesRepository.applyMetadataOverridesToTracks(withHistory, overrides)
        }
    }

    private fun buildImportSummaryBanner(
        prefix: String,
        newMusicCount: Int,
        newGeneralCount: Int,
        updatedCount: Int,
        skippedCount: Int,
        conflictCount: Int,
        demotedCount: Int = 0,
        removedCount: Int = 0
    ): String {
        val parts = mutableListOf<String>()
        if (newMusicCount > 0) parts += "新增 $newMusicCount 首音乐"
        if (newGeneralCount > 0) parts += "新增 $newGeneralCount 条普通音频"
        if (demotedCount > 0) parts += "$demotedCount 首移入普通音频区"
        if (removedCount > 0) parts += "移除 $removedCount 首未达标曲目"
        if (updatedCount > 0) parts += "更新 $updatedCount 项元数据"
        if (skippedCount > 0) parts += "自动跳过 $skippedCount 首重复"
        if (conflictCount > 0) parts += "$conflictCount 首存在音质差异待决策"
        return if (parts.isEmpty()) "$prefix：曲库已是最新状态" else "$prefix：${parts.joinToString(" · ")}"
    }

    /**
     * Handles incoming ACTION_VIEW audio intents when Auralis is launched as the system default player.
     */
    fun handleExternalAudioIntent(uri: Uri) {
        viewModelScope.launch {
            val imported = scanner.importAudioUris(listOf(uri), folderLabel = "系统调用")
            val candidate = imported.firstOrNull() ?: return@launch
            val existingList = trackDao.getAllTracksSnapshot()
            val matchedExisting = existingList.firstOrNull { TrackDeduplicator.areSameSong(it, candidate) }
            val trackToPlay = if (matchedExisting != null && TrackDeduplicator.isEquivalentQuality(matchedExisting, candidate)) {
                // Reuse existing library track directly so external playback never duplicates it
                matchedExisting
            } else {
                if (matchedExisting != null) {
                    // Different quality version: still play immediately, and surface quality conflict option
                    _pendingImportSourceLabel.value = "外部打开音频"
                    _pendingImportConflicts.value = listOf(
                        ImportQualityConflict(
                            conflictId = "${matchedExisting.id}__${candidate.id}",
                            existingTrack = matchedExisting,
                            incomingTrack = candidate,
                            existingScore = TrackDeduplicator.computeQualityScore(matchedExisting),
                            incomingScore = TrackDeduplicator.computeQualityScore(candidate)
                        )
                    )
                    candidate
                } else {
                    val enriched = enrichTracksWithArchivedHistory(listOf(candidate)).first()
                    trackDao.upsert(enriched)
                    enriched
                }
            }
            val fullList = trackDao.getAllTracksSnapshot()
            val queue = if (fullList.any { it.id == trackToPlay.id }) fullList else listOf(trackToPlay) + fullList
            val startIdx = queue.indexOfFirst { it.id == trackToPlay.id }.coerceAtLeast(0)
            playbackController.playQueue(queue, startIdx)
            _openNowPlayingTrigger.value = true
            haptics.springImpact()
        }
    }

    // =========================================================================
    // Smart Duplicate Import Conflict & Library Conflict Resolution
    // =========================================================================

    fun dismissImportConflicts() {
        _pendingImportConflicts.value = emptyList()
    }

    fun resolveImportConflicts(decisions: Map<String, ImportConflictDecision>) {
        val conflicts = _pendingImportConflicts.value
        if (conflicts.isEmpty()) return
        _pendingImportConflicts.value = emptyList()
        haptics.harmonicChime()

        viewModelScope.launch {
            var keptExisting = 0
            var replaced = 0
            var separated = 0
            var keptBoth = 0

            for (conflict in conflicts) {
                val decision = decisions[conflict.conflictId]
                    ?: if (conflict.recommendedIncoming) {
                        ImportConflictDecision.REPLACE_WITH_INCOMING
                    } else {
                        ImportConflictDecision.KEEP_EXISTING
                    }

                when (decision) {
                    ImportConflictDecision.KEEP_EXISTING -> {
                        keptExisting++
                    }

                    ImportConflictDecision.REPLACE_WITH_INCOMING -> {
                        preserveUserStateAndResolve(
                            winner = conflict.incomingTrack,
                            losers = listOf(conflict.existingTrack),
                            removeLosers = true,
                            demoteLosersToGeneral = false,
                            promoteWinnerToMusic = !conflict.existingTrack.isGeneralAudio
                        )
                        replaced++
                    }

                    ImportConflictDecision.KEEP_HIGH_IN_MUSIC_DEMOTE_OTHER -> {
                        val high = if (conflict.recommendedIncoming) conflict.incomingTrack else conflict.existingTrack
                        val low = if (conflict.recommendedIncoming) conflict.existingTrack else conflict.incomingTrack
                        preserveUserStateAndResolve(
                            winner = high,
                            losers = listOf(low),
                            removeLosers = false,
                            demoteLosersToGeneral = true,
                            promoteWinnerToMusic = true
                        )
                        separated++
                    }

                    ImportConflictDecision.KEEP_BOTH -> {
                        trackDao.upsert(conflict.incomingTrack)
                        keptBoth++
                    }
                }
            }

            val parts = mutableListOf<String>()
            if (replaced > 0) parts += "升级替换 $replaced 首"
            if (keptExisting > 0) parts += "保留原版 $keptExisting 首"
            if (separated > 0) parts += "高质留音乐/低质转普通音频 $separated 首"
            if (keptBoth > 0) parts += "同时保留两版 $keptBoth 首"
            showBanner("音质冲突处理完成：${parts.joinToString(" · ")}")
        }
    }

    fun resolveDuplicateGroupKeepOnly(group: DuplicateTrackGroup, keepTrackId: String) {
        val winner = group.tracks.firstOrNull { it.id == keepTrackId } ?: return
        val losers = group.tracks.filter { it.id != keepTrackId }
        if (losers.isEmpty()) return
        haptics.springImpact()
        viewModelScope.launch {
            preserveUserStateAndResolve(
                winner = winner,
                losers = losers,
                removeLosers = true,
                demoteLosersToGeneral = false,
                promoteWinnerToMusic = group.tracks.any { !it.isGeneralAudio }
            )
        }
    }

    fun resolveDuplicateGroupDemoteOthers(group: DuplicateTrackGroup, keepMusicTrackId: String) {
        val winner = group.tracks.firstOrNull { it.id == keepMusicTrackId } ?: return
        val others = group.tracks.filter { it.id != keepMusicTrackId }
        if (others.isEmpty()) return
        haptics.springImpact()
        viewModelScope.launch {
            preserveUserStateAndResolve(
                winner = winner,
                losers = others,
                removeLosers = false,
                demoteLosersToGeneral = true,
                promoteWinnerToMusic = true
            )
        }
    }

    fun resolveAllDuplicatesKeepHighestQuality() {
        val groups = duplicateGroups.value
        if (groups.isEmpty()) return
        haptics.harmonicChime()
        viewModelScope.launch {
            var removedTotal = 0
            for (group in groups) {
                val winner = group.bestTrack
                val losers = group.tracks.drop(1)
                if (losers.isEmpty()) continue
                preserveUserStateAndResolve(
                    winner = winner,
                    losers = losers,
                    removeLosers = true,
                    demoteLosersToGeneral = false,
                    promoteWinnerToMusic = group.tracks.any { !it.isGeneralAudio }
                )
                removedTotal += losers.size
            }
            showBanner("智能去重完成：已为 ${groups.size} 组曲目保留最高音质，移除 $removedTotal 个重复条目")
        }
    }

    fun resolveAllDuplicatesDemoteLowerToGeneral() {
        val groups = duplicateGroups.value
        if (groups.isEmpty()) return
        haptics.harmonicChime()
        viewModelScope.launch {
            var demotedTotal = 0
            for (group in groups) {
                val winner = group.bestTrack
                val others = group.tracks.drop(1)
                if (others.isEmpty()) continue
                preserveUserStateAndResolve(
                    winner = winner,
                    losers = others,
                    removeLosers = false,
                    demoteLosersToGeneral = true,
                    promoteWinnerToMusic = true
                )
                demotedTotal += others.size
            }
            showBanner("智能分流完成：${groups.size} 首最高音质保留在音乐区，$demotedTotal 个副本已转入普通音频区")
        }
    }

    fun resolveExactQualityDuplicatesOnly() {
        val groups = duplicateGroups.value
        if (groups.isEmpty()) return
        haptics.harmonicChime()
        viewModelScope.launch {
            var removedTotal = 0
            var cleanedGroups = 0
            for (group in groups) {
                val remaining = mutableListOf<Track>()
                val toRemove = mutableListOf<Track>()
                for (t in group.tracks) {
                    val equivalentKeeper = remaining.firstOrNull { TrackDeduplicator.isEquivalentQuality(it, t) }
                    if (equivalentKeeper == null) {
                        remaining.add(t)
                    } else {
                        toRemove.add(t)
                    }
                }
                if (toRemove.isNotEmpty() && remaining.isNotEmpty()) {
                    preserveUserStateAndResolve(
                        winner = remaining.first(),
                        losers = toRemove,
                        removeLosers = true,
                        demoteLosersToGeneral = false
                    )
                    removedTotal += toRemove.size
                    cleanedGroups++
                }
            }
            if (removedTotal > 0) {
                showBanner("已清理 $cleanedGroups 组共 $removedTotal 个同音质冗余副本，不同音质版本均已保留")
            } else {
                showBanner("当前所有重复组均为不同音质版本，无同音质冗余副本")
            }
        }
    }

    private suspend fun preserveUserStateAndResolve(
        winner: Track,
        losers: List<Track>,
        removeLosers: Boolean,
        demoteLosersToGeneral: Boolean,
        promoteWinnerToMusic: Boolean = false
    ) {
        val loserIds = losers.map { it.id }.filter { it != winner.id }
        val mergedWinner = winner.copy(
            isFavorite = winner.isFavorite || losers.any { it.isFavorite },
            playCount = winner.playCount + losers.sumOf { it.playCount },
            lastPlayedAt = maxOf(winner.lastPlayedAt, losers.maxOfOrNull { it.lastPlayedAt } ?: 0L),
            isGeneralAudio = if (promoteWinnerToMusic) false else winner.isGeneralAudio,
            embeddedLyrics = winner.embeddedLyrics?.takeIf { it.isNotBlank() }
                ?: losers.firstNotNullOfOrNull { it.embeddedLyrics?.takeIf { s -> s.isNotBlank() } }
        )
        trackDao.upsert(mergedWinner)

        // Inherit saved custom lyrics if winner doesn't have one
        if (customLyricDao.getCustomLyric(mergedWinner.id) == null) {
            val inherited = losers.firstNotNullOfOrNull { customLyricDao.getCustomLyric(it.id) }
            if (inherited != null) {
                customLyricDao.saveCustomLyric(inherited.copy(trackId = mergedWinner.id))
            }
        }

        // Preserve playlist memberships by re-linking loser track IDs to winner
        if (!mergedWinner.isGeneralAudio && loserIds.isNotEmpty()) {
            val migratedRefs = mutableListOf<PlaylistTrackCrossRef>()
            for (loserId in loserIds) {
                val refs = playlistDao.getRefsForTrack(loserId)
                refs.forEach { ref ->
                    migratedRefs.add(ref.copy(trackId = mergedWinner.id))
                }
            }
            if (migratedRefs.isNotEmpty()) {
                playlistDao.addTracksToPlaylist(migratedRefs)
            }
        }

        if (removeLosers && loserIds.isNotEmpty()) {
            loserIds.chunked(500).forEach { chunk ->
                playlistDao.removeTracksFromAllPlaylists(chunk)
                trackDao.deleteTracksByIds(chunk)
            }
            playbackController.removeTracksByIds(loserIds.toSet())
        } else if (demoteLosersToGeneral && loserIds.isNotEmpty()) {
            val demotedLosers = losers.filter { it.id != mergedWinner.id }.map { it.copy(isGeneralAudio = true) }
            if (demotedLosers.isNotEmpty()) {
                trackDao.upsertAll(demotedLosers)
            }
            loserIds.chunked(500).forEach { chunk ->
                playlistDao.removeTracksFromAllPlaylists(chunk)
            }
            val current = playbackState.value.currentTrack
            if (current != null && current.id in loserIds) {
                playbackController.syncTrackMetadataUpdate(current.copy(isGeneralAudio = true))
            }
        }

        val current = playbackState.value.currentTrack
        if (current != null && current.id == mergedWinner.id) {
            playbackController.syncTrackMetadataUpdate(mergedWinner)
        }
    }

    fun playTrack(track: Track, contextQueue: List<Track> = filteredTracks.value) {
        haptics.springImpact()
        val queue = if (contextQueue.any { it.id == track.id }) contextQueue else listOf(track)
        val idx = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackController.playQueue(queue, idx)
    }

    fun playAll(tracks: List<Track>, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        haptics.springImpact()
        val ordered = if (shuffle) tracks.shuffled() else tracks
        playbackController.playQueue(ordered, 0)
        if (shuffle) {
            playbackController.setShuffleEnabled(true)
        }
    }

    /**
     * Saves in-app metadata edits (title, artist, album, and optional custom cover art) without
     * modifying the underlying audio file. Stores the original snapshot for 1-tap restore.
     */
    fun saveTrackMetadataEdit(
        track: Track,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        pickedCoverUri: Uri? = null,
        clearCustomCover: Boolean = false
    ) {
        val cleanTitle = newTitle.trim().ifBlank { track.title }
        val cleanArtist = newArtist.trim().ifBlank { track.artist }
        val cleanAlbum = newAlbum.trim().ifBlank { track.album }
        viewModelScope.launch {
            val existingOverride = prefs.getTrackMetadataOverridesSnapshot()[track.id]
            val origTitle = existingOverride?.originalTitle ?: track.title
            val origArtist = existingOverride?.originalArtist ?: track.artist
            val origAlbum = existingOverride?.originalAlbum ?: track.album
            val origArtworkPath = existingOverride?.originalArtworkPath ?: track.artworkPath
            val origAlbumArtUri = existingOverride?.originalAlbumArtUri ?: track.albumArtUri

            val resolvedCustomCoverPath: String? = when {
                pickedCoverUri != null -> withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = java.io.File(context.filesDir, "custom_track_covers").apply { mkdirs() }
                        val safeName = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val outFile = java.io.File(dir, "${safeName}_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(pickedCoverUri)?.use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        existingOverride?.customArtworkPath?.let { oldPath ->
                            runCatching { java.io.File(oldPath).delete() }
                        }
                        if (outFile.exists() && outFile.length() > 0L) outFile.absolutePath else null
                    }.getOrNull()
                }
                clearCustomCover -> {
                    existingOverride?.customArtworkPath?.let { oldPath ->
                        withContext(Dispatchers.IO) { runCatching { java.io.File(oldPath).delete() } }
                    }
                    null
                }
                else -> existingOverride?.customArtworkPath
            }

            val finalArtworkPath = resolvedCustomCoverPath ?: origArtworkPath
            val updated = track.copy(
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                artworkPath = finalArtworkPath,
                albumArtUri = origAlbumArtUri
            )

            val overrideRecord = TrackMetadataOverride(
                trackId = track.id,
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                customArtworkPath = resolvedCustomCoverPath,
                originalTitle = origTitle,
                originalArtist = origArtist,
                originalAlbum = origAlbum,
                originalArtworkPath = origArtworkPath,
                originalAlbumArtUri = origAlbumArtUri
            )
            prefs.saveTrackMetadataOverride(overrideRecord)
            withContext(Dispatchers.IO) {
                trackDao.updateTrackMetadata(
                    trackId = track.id,
                    title = updated.title,
                    artist = updated.artist,
                    album = updated.album,
                    artworkPath = updated.artworkPath,
                    albumArtUri = updated.albumArtUri
                )
            }
            playbackController.syncTrackMetadataUpdate(updated)
            if (playbackState.value.currentTrack?.id == track.id) {
                _auraPalette.value = runCatching {
                    PaletteExtractor.extractFromTrackArtwork(
                        context = context,
                        artworkPath = updated.artworkPath,
                        albumArtUri = updated.albumArtUri
                    )
                }.getOrDefault(AuraPalette())
            }
            haptics.harmonicChime()
            showBanner("已保存应用内曲目信息（原音频文件未改动）", StatusToastLevel.SUCCESS)
        }
    }

    /**
     * Restores a previously edited track back to its original scanned metadata & cover art.
     */
    fun restoreTrackMetadataOriginal(trackId: String) {
        viewModelScope.launch {
            val removed = prefs.removeTrackMetadataOverride(trackId)
            if (removed == null) {
                showBanner("当前曲目已是原始信息")
                return@launch
            }
            removed.customArtworkPath?.let { oldPath ->
                withContext(Dispatchers.IO) { runCatching { java.io.File(oldPath).delete() } }
            }
            val currentDbTrack = withContext(Dispatchers.IO) { trackDao.getTrackById(trackId) }
            val restoredTitle = removed.originalTitle.ifBlank { currentDbTrack?.title ?: "" }
            val restoredArtist = removed.originalArtist.ifBlank { currentDbTrack?.artist ?: "" }
            val restoredAlbum = removed.originalAlbum.ifBlank { currentDbTrack?.album ?: "" }
            withContext(Dispatchers.IO) {
                trackDao.updateTrackMetadata(
                    trackId = trackId,
                    title = restoredTitle,
                    artist = restoredArtist,
                    album = restoredAlbum,
                    artworkPath = removed.originalArtworkPath,
                    albumArtUri = removed.originalAlbumArtUri
                )
            }
            val refreshed = withContext(Dispatchers.IO) { trackDao.getTrackById(trackId) }
            if (refreshed != null) {
                playbackController.syncTrackMetadataUpdate(refreshed)
                if (playbackState.value.currentTrack?.id == trackId) {
                    _auraPalette.value = runCatching {
                        PaletteExtractor.extractFromTrackArtwork(
                            context = context,
                            artworkPath = refreshed.artworkPath,
                            albumArtUri = refreshed.albumArtUri
                        )
                    }.getOrDefault(AuraPalette())
                }
            }
            haptics.tickLight()
            showBanner("已恢复原始曲目信息与封面", StatusToastLevel.SUCCESS)
        }
    }

    fun playNextInQueue(track: Track) {
        haptics.tickLight()
        playbackController.addTrackNextInQueue(track)
    }

    fun togglePlayPause() {
        haptics.springImpact()
        playbackController.togglePlayPause()
    }

    fun skipNext() {
        haptics.springImpact()
        playbackController.skipNext()
    }

    fun skipPrevious() {
        haptics.springImpact()
        playbackController.skipPrevious()
    }

    fun seekTo(ms: Long) {
        playbackController.seekTo(ms)
    }

    fun seekRelative(deltaMs: Long) {
        val state = playbackState.value
        val track = state.currentTrack ?: return
        val maxDur = state.durationMs.coerceAtLeast(track.durationMs).coerceAtLeast(0L)
        val target = (state.positionMs + deltaMs).coerceIn(0L, maxDur)
        haptics.tickLight()
        playbackController.seekTo(target)
    }

    fun addAudioBookmark(trackId: String, positionMs: Long, label: String) = viewModelScope.launch {
        haptics.springImpact()
        val existingCount = audioBookmarks.value[trackId].orEmpty().size
        val cleanLabel = label.trim().ifBlank { "时间标记 #${existingCount + 1}" }
        prefs.addAudioBookmark(
            trackId = trackId,
            bookmark = AudioBookmark(
                id = "bm_${System.currentTimeMillis()}_${positionMs}",
                positionMs = positionMs.coerceAtLeast(0L),
                label = cleanLabel,
                createdAtMs = System.currentTimeMillis()
            )
        )
    }

    fun removeAudioBookmark(trackId: String, bookmarkId: String) = viewModelScope.launch {
        haptics.tickLight()
        prefs.removeAudioBookmark(trackId, bookmarkId)
    }

    fun toggleFavorite(track: Track) {
        haptics.heartPulse()
        val newFav = !track.isFavorite
        viewModelScope.launch {
            trackDao.setFavorite(track.id, newFav)
            playbackController.syncTrackMetadataUpdate(track.copy(isFavorite = newFav))
        }
    }

    private fun handleTrackActivated(track: Track) {
        val now = System.currentTimeMillis()
        if (lastActivatedTrackId == track.id && now - lastActivatedAtMs < 400L) {
            return
        }
        lastActivatedTrackId = track.id
        lastActivatedAtMs = now

        viewModelScope.launch {
            runCatching { trackDao.recordPlay(track.id, now) }
        }
        // Extract fluid cover palette
        paletteJob?.cancel()
        paletteJob = viewModelScope.launch {
            _auraPalette.value = runCatching {
                PaletteExtractor.extractFromTrackArtwork(
                    context = context,
                    artworkPath = track.artworkPath,
                    albumArtUri = track.albumArtUri
                )
            }.getOrDefault(AuraPalette())
        }
        // Proactively pre-warm current & adjacent tracks' artwork in Coil memory cache
        // so auto-skipping and rapid Next/Prev transitions never flash a fallback placeholder.
        viewModelScope.launch(Dispatchers.IO) {
            val curPlayback = playbackState.value
            val targets = listOfNotNull(track, curPlayback.nextTrack, curPlayback.previousTrack).distinctBy { it.id }
            val loader = context.imageLoader
            targets.forEach { t ->
                buildTrackArtworkRequest(context, t.artworkPath, t.albumArtUri, decodeSizePx = 768)?.let {
                    loader.enqueue(it)
                }
                buildTrackArtworkRequest(context, t.artworkPath, t.albumArtUri, decodeSizePx = 256)?.let {
                    loader.enqueue(it)
                }
            }
        }
        // Skip music lyric loading when playing General Audio (recordings, podcasts, etc.)
        if (track.isGeneralAudio) {
            lyricLoadJob?.cancel()
            _currentLyric.value = LyricDocument(LyricFormat.NONE, emptyList())
        } else {
            // Load lyrics in priority order
            loadLyricsForTrack(track)
        }
    }

    private fun loadLyricsForTrack(track: Track) {
        lyricLoadJob?.cancel()
        lyricLoadJob = viewModelScope.launch {
            val settings = prefs.lyricSettingsFlow.first()
            // 1. Check user custom/saved lyric in Room
            val saved = runCatching { customLyricDao.getCustomLyric(track.id) }.getOrNull()
            if (saved != null && saved.rawContent.isNotBlank()) {
                val parsed = runCatching {
                    LyricParser.parse(
                        rawInput = saved.rawContent,
                        sourceLabel = saved.sourceLabel,
                        userOffsetMs = saved.offsetMs,
                        enableSimulatedWordByWord = settings.enableSimulatedWordByWord
                    )
                }.getOrNull()
                if (parsed != null) {
                    _currentLyric.value = parsed
                    return@launch
                }
            }

            // 2. Check embedded lyrics extracted from ID3v2 / FLAC / MP4
            if (!track.embeddedLyrics.isNullOrBlank()) {
                val parsed = runCatching {
                    LyricParser.parse(
                        rawInput = track.embeddedLyrics,
                        sourceLabel = "内嵌歌词标签",
                        enableSimulatedWordByWord = settings.enableSimulatedWordByWord
                    )
                }.getOrNull()
                if (parsed != null) {
                    _currentLyric.value = parsed
                    return@launch
                }
            }

            // 3. Check sidecar local lyric (.qrc, .yrc, .krc, .ttml, .lrc, .srt)
            val sidecar = runCatching {
                withContext(Dispatchers.IO) { scanner.findSidecarLyric(track) }
            }.getOrNull()
            if (sidecar != null) {
                val parsed = runCatching {
                    LyricParser.parse(
                        rawInput = sidecar.first,
                        sourceLabel = sidecar.second,
                        enableSimulatedWordByWord = settings.enableSimulatedWordByWord
                    )
                }.getOrNull()
                if (parsed != null) {
                    _currentLyric.value = parsed
                    return@launch
                }
            }

            // 4. Empty state ready for 1-tap smart online match or manual upload
            _currentLyric.value = LyricDocument(LyricFormat.NONE, emptyList(), "未匹配歌词")
        }
    }

    /**
     * Fast local-only lyric lookup for Lyric Bridge (人声接唱) transition planning.
     * Never hits the network; checks in-memory active lyrics first, then Room DB, embedded tags, and sidecar files.
     */
    private suspend fun resolveLocalLyricsForBridge(track: Track): LyricDocument? {
        if (track.isGeneralAudio) return null
        if (playbackState.value.currentTrack?.id == track.id) {
            val activeDoc = _currentLyric.value
            if (activeDoc.hasSyncedTimestamps) return activeDoc
        }
        return withContext(Dispatchers.IO) {
            val saved = runCatching { customLyricDao.getCustomLyric(track.id) }.getOrNull()
            if (saved != null && saved.rawContent.isNotBlank()) {
                val parsed = runCatching {
                    LyricParser.parse(
                        rawInput = saved.rawContent,
                        sourceLabel = saved.sourceLabel,
                        userOffsetMs = saved.offsetMs,
                        enableSimulatedWordByWord = false
                    )
                }.getOrNull()
                if (parsed != null && parsed.hasSyncedTimestamps) return@withContext parsed
            }

            if (!track.embeddedLyrics.isNullOrBlank()) {
                val parsed = runCatching {
                    LyricParser.parse(
                        rawInput = track.embeddedLyrics,
                        sourceLabel = "内嵌歌词标签",
                        enableSimulatedWordByWord = false
                    )
                }.getOrNull()
                if (parsed != null && parsed.hasSyncedTimestamps) return@withContext parsed
            }

            val sidecar = runCatching { scanner.findSidecarLyric(track) }.getOrNull()
            if (sidecar != null) {
                val parsed = runCatching {
                    LyricParser.parse(
                        rawInput = sidecar.first,
                        sourceLabel = sidecar.second,
                        enableSimulatedWordByWord = false
                    )
                }.getOrNull()
                if (parsed != null && parsed.hasSyncedTimestamps) return@withContext parsed
            }
            null
        }
    }

    fun smartMatchLyricsOnline(
        track: Track,
        customTitle: String = track.title,
        customArtist: String = track.artist,
        source: LyricSource = lyricSettings.value.activeOnlineSource
    ) {
        viewModelScope.launch {
            _onlineLyricSearch.value = OnlineLyricSearchState(isSearching = true)
            val result = onlineLyricClient.searchLyrics(
                title = customTitle,
                artist = customArtist,
                album = track.album,
                durationMs = track.durationMs,
                source = source
            )
            result.fold(
                onSuccess = { searchResult ->
                    _onlineLyricSearch.value = OnlineLyricSearchState(
                        isSearching = false,
                        candidates = searchResult.candidates,
                        sourceSummaries = searchResult.sourceSummaries,
                        error = if (searchResult.candidates.isEmpty()) {
                            "未在「${source.displayName}」检索到匹配歌词，可切换其他源、精简曲名或手动导入 .qrc/.krc/.lrc"
                        } else {
                            null
                        }
                    )
                    if (searchResult.candidates.isNotEmpty()) haptics.tickLight()
                },
                onFailure = { err ->
                    _onlineLyricSearch.value = OnlineLyricSearchState(
                        isSearching = false,
                        error = err.localizedMessage ?: "在线匹配失败"
                    )
                }
            )
        }
    }

    fun applyOnlineLyricCandidate(track: Track, candidate: OnlineLyricCandidate) {
        val raw = candidate.syncedLyrics ?: candidate.plainLyrics ?: return
        val fileNameHint = when {
            candidate.isWordSynced && candidate.source == LyricSource.KUGOU -> "match.krc"
            candidate.isWordSynced && candidate.source == LyricSource.NETEASE -> "match.yrc"
            else -> "match.lrc"
        }
        saveCustomLyrics(
            track = track,
            rawContent = raw,
            sourceLabel = "云端匹配 · ${candidate.source.displayName}",
            fileNameHint = fileNameHint
        )
    }

    fun importLyricFileForTrack(track: Track, uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = withContext(Dispatchers.IO) {
                    var name = uri.lastPathSegment?.substringAfterLast("/") ?: "lyrics.lrc"
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
                    }
                    name
                }
                val decoded = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        LyricParser.decodeRawBytes(stream.readBytes(), fileName)
                    }.orEmpty()
                }
                if (decoded.isBlank()) {
                    showBanner("歌词文件内容为空")
                    return@launch
                }
                saveCustomLyrics(track, decoded, sourceLabel = "手动导入 ($fileName)", fileNameHint = fileName)
            } catch (e: Exception) {
                showBanner("导入歌词失败：${e.localizedMessage}")
            }
        }
    }

    fun saveCustomLyrics(
        track: Track,
        rawContent: String,
        sourceLabel: String,
        fileNameHint: String? = null
    ) {
        viewModelScope.launch {
            val settings = prefs.lyricSettingsFlow.first()
            val parsed = LyricParser.parse(
                rawInput = rawContent,
                sourceLabel = sourceLabel,
                fileNameHint = fileNameHint,
                enableSimulatedWordByWord = settings.enableSimulatedWordByWord
            )
            customLyricDao.saveCustomLyric(
                CustomLyricEntity(
                    trackId = track.id,
                    rawContent = rawContent,
                    formatName = parsed.format.name,
                    sourceLabel = sourceLabel,
                    offsetMs = parsed.globalOffsetMs
                )
            )
            playbackController.invalidateLyricCache(track.id)
            if (playbackState.value.currentTrack?.id == track.id) {
                _currentLyric.value = parsed
            }
            haptics.harmonicChime()
        }
    }

    fun adjustLyricOffset(deltaMs: Long) {
        val track = playbackState.value.currentTrack ?: return
        val current = _currentLyric.value
        val newOffset = (current.globalOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        haptics.tickLight()
        _currentLyric.value = current.copy(globalOffsetMs = newOffset)
        playbackController.invalidateLyricCache(track.id)
        viewModelScope.launch {
            val existing = customLyricDao.getCustomLyric(track.id)
            if (existing != null) {
                customLyricDao.updateOffset(track.id, newOffset)
            } else if (current.rawContent.isNotBlank()) {
                customLyricDao.saveCustomLyric(
                    CustomLyricEntity(
                        trackId = track.id,
                        rawContent = current.rawContent,
                        formatName = current.format.name,
                        sourceLabel = current.sourceLabel,
                        offsetMs = newOffset
                    )
                )
            }
        }
    }

    // Queue & Batch List Management
    fun appendTracksToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        haptics.tickLight()
        playbackController.appendTracksToQueue(tracks)
    }

    fun removeQueueIndex(index: Int) {
        haptics.tickLight()
        playbackController.removeFromQueue(index)
    }

    fun moveQueueIndexToNext(index: Int) {
        haptics.tickLight()
        playbackController.moveQueueItemToNext(index)
    }

    fun clearUpcomingQueue() {
        haptics.tickLight()
        playbackController.clearUpcomingQueue()
    }

    fun saveQueueAsPlaylist(name: String) {
        val queueIds = playbackState.value.queue
            .filter { !it.isGeneralAudio }
            .map { it.id }
        if (queueIds.isEmpty()) {
            showBanner("普通音频队列不支持存为音乐歌单")
            return
        }
        createPlaylist(name, "来自当前待播队列 (${queueIds.size} 首)", queueIds, showToast = true)
    }

    fun batchFavoriteTracks(trackIds: List<String>, favorite: Boolean) {
        if (trackIds.isEmpty()) return
        haptics.heartPulse()
        viewModelScope.launch {
            trackIds.chunked(500).forEach { chunk ->
                trackDao.setFavoriteForIds(chunk, favorite)
            }
            val current = playbackState.value.currentTrack
            if (current != null && current.id in trackIds) {
                playbackController.syncTrackMetadataUpdate(current.copy(isFavorite = favorite))
            }
        }
    }

    fun setTracksGeneralAudio(trackIds: List<String>, isGeneralAudio: Boolean) {
        if (trackIds.isEmpty()) return
        haptics.springImpact()
        viewModelScope.launch {
            trackIds.chunked(500).forEach { chunk ->
                trackDao.setGeneralAudioForIds(chunk, isGeneralAudio)
                if (isGeneralAudio) {
                    playlistDao.removeTracksFromAllPlaylists(chunk)
                }
            }
            val current = playbackState.value.currentTrack
            if (current != null && current.id in trackIds) {
                playbackController.syncTrackMetadataUpdate(current.copy(isGeneralAudio = isGeneralAudio))
            }
        }
    }

    fun dismissUndoBanner() {
        _undoBanner.value = null
    }

    fun dismissPendingConfirmDeletion() {
        _pendingConfirmDeletion.value = null
    }

    fun confirmPendingDeletion() {
        val pending = _pendingConfirmDeletion.value ?: return
        _pendingConfirmDeletion.value = null
        pending.onConfirm()
    }

    private fun requestProtectedDeletion(
        confirmTitle: String,
        confirmMessage: String,
        confirmLabel: String = "确认删除",
        deletedSummary: String,
        restoredSummary: String,
        onExecuteConfirmed: (() -> Unit)? = null,
        performDeleteAndBuildUndo: suspend () -> (suspend () -> Unit)?
    ) {
        val executeImmediate: (showTopUndo: Boolean) -> Unit = { showTopUndo ->
            haptics.tickLight()
            viewModelScope.launch {
                val undoAction = performDeleteAndBuildUndo()
                onExecuteConfirmed?.invoke()
                if (showTopUndo && undoAction != null) {
                    _statusBanner.value = null
                    _undoBanner.value = UndoBannerState(
                        message = deletedSummary,
                        onUndo = {
                            viewModelScope.launch {
                                undoAction()
                                haptics.harmonicChime()
                            }
                        }
                    )
                }
            }
        }

        when (deletionGuardMode.value) {
            DeletionGuardMode.CONFIRM_DIALOG -> {
                haptics.tickLight()
                _pendingConfirmDeletion.value = PendingConfirmDeletion(
                    title = confirmTitle,
                    message = confirmMessage,
                    confirmLabel = confirmLabel,
                    onConfirm = { executeImmediate(false) }
                )
            }

            DeletionGuardMode.TOP_UNDO_BANNER -> {
                executeImmediate(true)
            }
        }
    }

    fun batchRemoveTracksFromLibrary(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        val uniqueIds = trackIds.distinct()
        val label = if (uniqueIds.size == 1) {
            val title = allTracks.value.firstOrNull { it.id == uniqueIds.first() }?.title ?: "该曲目"
            "「$title」"
        } else {
            "${uniqueIds.size} 首曲目"
        }
        requestProtectedDeletion(
            confirmTitle = "从曲库列表移除",
            confirmMessage = "确定从 Auralis 曲库中移除$label 吗？\n手机本地原始音频文件不会被删除，之后可随时通过智能扫描重新收录。",
            confirmLabel = "确认移出",
            deletedSummary = "已从曲库移除$label（原文件保留）",
            restoredSummary = "已撤回移除$label"
        ) {
            val tracksSnapshot = allTracks.value.filter { it.id in uniqueIds }
            val refsSnapshot = playlistRefsFlow.value.filter { it.trackId in uniqueIds }
            val lyricsSnapshot = uniqueIds.mapNotNull { id -> runCatching { customLyricDao.getCustomLyric(id) }.getOrNull() }
            uniqueIds.chunked(500).forEach { chunk ->
                playlistDao.removeTracksFromAllPlaylists(chunk)
                trackDao.deleteTracksByIds(chunk)
            }
            playbackController.removeTracksByIds(uniqueIds.toSet())
            if (tracksSnapshot.isEmpty()) null else suspend {
                trackDao.upsertAll(tracksSnapshot)
                if (refsSnapshot.isNotEmpty()) playlistDao.addTracksToPlaylist(refsSnapshot)
                lyricsSnapshot.forEach { customLyricDao.saveCustomLyric(it) }
            }
        }
    }

    fun clearAllFavorites(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        val uniqueIds = trackIds.distinct()
        requestProtectedDeletion(
            confirmTitle = "取消全部红心收藏",
            confirmMessage = "确定取消全部 ${uniqueIds.size} 首曲目的红心收藏吗？",
            confirmLabel = "确认取消",
            deletedSummary = "已取消 ${uniqueIds.size} 首红心收藏",
            restoredSummary = "${uniqueIds.size} 首红心收藏"
        ) {
            uniqueIds.chunked(500).forEach { chunk ->
                trackDao.setFavoriteForIds(chunk, false)
            }
            val current = playbackState.value.currentTrack
            if (current != null && current.id in uniqueIds) {
                playbackController.syncTrackMetadataUpdate(current.copy(isFavorite = false))
            }
            suspend {
                uniqueIds.chunked(500).forEach { chunk ->
                    trackDao.setFavoriteForIds(chunk, true)
                }
                val active = playbackState.value.currentTrack
                if (active != null && active.id in uniqueIds) {
                    playbackController.syncTrackMetadataUpdate(active.copy(isFavorite = true))
                }
            }
        }
    }

    fun clearRecentHistory() {
        val snapshot = allTracks.value.filter { it.lastPlayedAt > 0L }
        if (snapshot.isEmpty()) {
            showBanner("暂无近期聆听记录")
            return
        }
        requestProtectedDeletion(
            confirmTitle = "清空最近聆听记录",
            confirmMessage = "确定清空全部 ${snapshot.size} 条最近聆听历史记录吗？",
            confirmLabel = "确认清空",
            deletedSummary = "已清空最近聆听记录",
            restoredSummary = "最近聆听记录"
        ) {
            trackDao.clearAllRecentHistory()
            suspend {
                val latestMap = trackDao.getAllTracksSnapshot().associateBy { it.id }
                val restored = snapshot.mapNotNull { old ->
                    latestMap[old.id]?.copy(lastPlayedAt = old.lastPlayedAt)
                }
                if (restored.isNotEmpty()) trackDao.upsertAll(restored)
            }
        }
    }

    fun removeFromRecentHistory(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        val uniqueIds = trackIds.distinct()
        val snapshot = allTracks.value.filter { it.id in uniqueIds && it.lastPlayedAt > 0L }
        requestProtectedDeletion(
            confirmTitle = "移出最近聆听",
            confirmMessage = "确定从最近聆听中移出所选的 ${uniqueIds.size} 首曲目吗？",
            confirmLabel = "确认移出",
            deletedSummary = "已从最近聆听移出 ${uniqueIds.size} 首",
            restoredSummary = "最近聆听曲目"
        ) {
            uniqueIds.chunked(500).forEach { chunk ->
                trackDao.clearPlayHistoryForIds(chunk)
            }
            if (snapshot.isEmpty()) null else suspend {
                val latestMap = trackDao.getAllTracksSnapshot().associateBy { it.id }
                val restored = snapshot.mapNotNull { old ->
                    latestMap[old.id]?.copy(lastPlayedAt = old.lastPlayedAt)
                }
                if (restored.isNotEmpty()) trackDao.upsertAll(restored)
            }
        }
    }

    fun clearMostPlayedStats() {
        val snapshot = allTracks.value.filter { it.playCount > 0 }
        if (snapshot.isEmpty()) {
            showBanner("暂无高频循环统计数据")
            return
        }
        requestProtectedDeletion(
            confirmTitle = "重置高频循环统计",
            confirmMessage = "确定重置所有 ${snapshot.size} 首曲目的历史循环播放次数统计吗？",
            confirmLabel = "确认重置",
            deletedSummary = "已重置高频循环统计",
            restoredSummary = "高频循环播放统计"
        ) {
            trackDao.clearMostPlayedStats()
            suspend {
                val latestMap = trackDao.getAllTracksSnapshot().associateBy { it.id }
                val restored = snapshot.mapNotNull { old ->
                    latestMap[old.id]?.copy(playCount = old.playCount)
                }
                if (restored.isNotEmpty()) trackDao.upsertAll(restored)
            }
        }
    }

    // Playlists
    fun createPlaylist(
        name: String,
        description: String = "",
        initialTrackIds: List<String> = emptyList(),
        showToast: Boolean = false
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val id = playlistDao.insertPlaylist(PlaylistEntity(name = trimmed, description = description.trim()))
            if (initialTrackIds.isNotEmpty()) {
                val refs = initialTrackIds.distinct().mapIndexed { idx, trackId ->
                    PlaylistTrackCrossRef(id, trackId, idx)
                }
                playlistDao.addTracksToPlaylist(refs)
            }
            haptics.harmonicChime()
            if (showToast) {
                showBanner(
                    "已创建歌单「$trimmed」" + if (initialTrackIds.isNotEmpty()) "（含 ${initialTrackIds.distinct().size} 首）" else "",
                    StatusToastLevel.SUCCESS
                )
            }
        }
    }

    fun renamePlaylist(playlistId: Long, name: String, description: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            playlistDao.updatePlaylistInfo(playlistId, trimmed, description.trim())
            haptics.tickLight()
        }
    }

    fun deletePlaylist(playlistId: Long, onDeleted: (() -> Unit)? = null) {
        val plEntity = playlists.value.firstOrNull { it.playlist.id == playlistId }?.playlist ?: return
        val coverKey = com.example.model.PlaylistCoverKeys.custom(playlistId)
        requestProtectedDeletion(
            confirmTitle = "删除歌单",
            confirmMessage = "确定永久删除歌单「${plEntity.name}」吗？（手机本地歌曲文件不会被删除）",
            confirmLabel = "删除歌单",
            deletedSummary = "已删除歌单「${plEntity.name}」",
            restoredSummary = "歌单「${plEntity.name}」",
            onExecuteConfirmed = onDeleted
        ) {
            val refsSnapshot = playlistRefsFlow.value.filter { it.playlistId == playlistId }
            val coverSnapshot = playlistCoverConfigs.value[coverKey]
            playlistDao.clearPlaylistTracks(playlistId)
            playlistDao.deletePlaylist(playlistId)
            prefs.updatePlaylistCoverConfig(coverKey, null)
            suspend {
                playlistDao.insertPlaylist(plEntity)
                if (refsSnapshot.isNotEmpty()) playlistDao.addTracksToPlaylist(refsSnapshot)
                if (coverSnapshot != null) prefs.updatePlaylistCoverConfig(coverKey, coverSnapshot)
            }
        }
    }

    fun updatePlaylistCoverConfig(collectionKey: String, config: com.example.model.PlaylistCoverConfig?) {
        viewModelScope.launch {
            prefs.updatePlaylistCoverConfig(collectionKey, config)
            haptics.tickLight()
        }
    }

    fun clearPlaylist(playlistId: Long) {
        val plName = playlists.value.firstOrNull { it.playlist.id == playlistId }?.playlist?.name ?: "歌单"
        val refsSnapshot = playlistRefsFlow.value.filter { it.playlistId == playlistId }
        if (refsSnapshot.isEmpty()) return
        requestProtectedDeletion(
            confirmTitle = "清空歌单",
            confirmMessage = "确定清空「$plName」内的全部 ${refsSnapshot.size} 首曲目吗？",
            confirmLabel = "确认清空",
            deletedSummary = "已清空歌单「$plName」",
            restoredSummary = "歌单「$plName」内的 ${refsSnapshot.size} 首曲目"
        ) {
            playlistDao.clearPlaylistTracks(playlistId)
            suspend {
                playlistDao.addTracksToPlaylist(refsSnapshot)
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        batchAddTracksToPlaylist(playlistId, listOf(track.id))
    }

    fun batchAddTracksToPlaylist(playlistId: Long, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            val existingIds = playlistRefsFlow.value
                .filter { it.playlistId == playlistId }
                .map { it.trackId }
                .toSet()
            val basePos = existingIds.size
            val newRefs = trackIds.distinct()
                .filterNot { it in existingIds }
                .mapIndexed { idx, trackId ->
                    PlaylistTrackCrossRef(playlistId, trackId, basePos + idx)
                }
            if (newRefs.isNotEmpty()) {
                playlistDao.addTracksToPlaylist(newRefs)
                haptics.harmonicChime()
            } else {
                haptics.tickLight()
                showBanner("所选曲目均已在该歌单中", StatusToastLevel.INFO)
            }
        }
    }

    fun removeTracksFromPlaylist(playlistId: Long, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        val uniqueIds = trackIds.distinct()
        val plName = playlists.value.firstOrNull { it.playlist.id == playlistId }?.playlist?.name ?: "歌单"
        val refsSnapshot = playlistRefsFlow.value.filter { it.playlistId == playlistId && it.trackId in uniqueIds }
        val label = if (uniqueIds.size == 1) {
            val title = allTracks.value.firstOrNull { it.id == uniqueIds.first() }?.title ?: "该曲目"
            "「$title」"
        } else {
            "${uniqueIds.size} 首曲目"
        }
        requestProtectedDeletion(
            confirmTitle = "从歌单移出",
            confirmMessage = "确定将$label 从「$plName」中移出吗？",
            confirmLabel = "确认移出",
            deletedSummary = "已从「$plName」移出$label",
            restoredSummary = "「$plName」中的$label"
        ) {
            uniqueIds.chunked(500).forEach { chunk ->
                playlistDao.removeTracksFromPlaylist(playlistId, chunk)
            }
            if (refsSnapshot.isEmpty()) null else suspend {
                playlistDao.addTracksToPlaylist(refsSnapshot)
            }
        }
    }

    // Lyrics Export & Burn-to-Audio File
    private suspend fun resolveBestTrackLyrics(track: Track): Triple<String, String?, String>? {
        val saved = runCatching { customLyricDao.getCustomLyric(track.id) }.getOrNull()
        if (saved != null && saved.rawContent.isNotBlank()) {
            return Triple(saved.rawContent, saved.formatName, saved.sourceLabel.ifBlank { "已保存歌词" })
        }
        if (!track.embeddedLyrics.isNullOrBlank()) {
            return Triple(track.embeddedLyrics, null, "音频内嵌歌词")
        }
        val sidecar = runCatching {
            withContext(Dispatchers.IO) { scanner.findSidecarLyric(track) }
        }.getOrNull()
        if (sidecar != null && sidecar.first.isNotBlank()) {
            return Triple(sidecar.first, null, sidecar.second)
        }
        return null
    }

    fun requestExportTrackLyrics(track: Track) {
        haptics.tickLight()
        viewModelScope.launch {
            val resolved = resolveBestTrackLyrics(track)
            if (resolved == null) {
                showBanner("「${track.title}」暂未获取到歌词，请先在播放页匹配或导入歌词")
                return@launch
            }
            val (rawContent, formatName, _) = resolved
            val (fileName, mimeType) = AudioLyricsTagWriter.suggestExportFileNameAndMime(
                track = track,
                formatName = formatName,
                rawContent = rawContent
            )
            _pendingLyricExport.value = PendingLyricExportRequest(
                track = track,
                suggestedFileName = fileName,
                mimeType = mimeType,
                rawContent = rawContent
            )
        }
    }

    fun completeExportTrackLyrics(targetUri: Uri?) {
        val req = _pendingLyricExport.value ?: return
        _pendingLyricExport.value = null
        if (targetUri == null) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                        out.write(req.rawContent.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: error("无法打开目标文件输出流")
                }
            }
            result.fold(
                onSuccess = {
                    haptics.harmonicChime()
                    showBanner("已导出歌词文件：${req.suggestedFileName}")
                },
                onFailure = { err ->
                    showBanner("导出歌词失败：${err.localizedMessage ?: "无写入权限"}")
                }
            )
        }
    }

    fun requestBurnLyricsToAudio(track: Track) {
        haptics.tickLight()
        viewModelScope.launch {
            val resolved = resolveBestTrackLyrics(track)
            if (resolved == null) {
                showBanner("「${track.title}」暂未获取到歌词，请先在播放页匹配或导入歌词后再刻录")
                return@launch
            }
            val (rawContent, _, sourceLabel) = resolved
            _pendingBurnConfirm.value = PendingBurnConfirmRequest(
                track = track,
                sourceLabel = sourceLabel,
                lyricsText = rawContent
            )
        }
    }

    fun dismissBurnConfirm() {
        _pendingBurnConfirm.value = null
    }

    fun confirmBurnLyricsToAudio() {
        val req = _pendingBurnConfirm.value ?: return
        _pendingBurnConfirm.value = null
        haptics.tickLight()
        viewModelScope.launch {
            val uri = Uri.parse(req.track.contentUri)
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取原音频文件，请确认文件未被移动或删除")
                }
            }
            val originalBytes = readResult.getOrElse { err ->
                showBanner("读取音频失败：${err.localizedMessage ?: "文件不可访问"}")
                return@launch
            }

            val embedResult = withContext(Dispatchers.Default) {
                AudioLyricsTagWriter.embedLyrics(
                    originalBytes = originalBytes,
                    lyricsText = req.lyricsText,
                    fileName = req.track.fileName,
                    mimeType = req.track.mimeType
                )
            }
            val outcome = embedResult.getOrElse { err ->
                showBanner(err.localizedMessage ?: "当前格式暂不支持直接刻录歌词")
                return@launch
            }

            val directWrite = withContext(Dispatchers.IO) {
                writePatchedAudioDirect(uri, outcome.patchedBytes)
            }
            directWrite.fold(
                onSuccess = {
                    finalizeBurnSuccess(req.track, req.lyricsText, outcome)
                },
                onFailure = { err ->
                    // Handle Android 10+ RecoverableSecurityException or Android 11+ MediaStore.createWriteRequest
                    val intentSender: IntentSender? = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && err is RecoverableSecurityException ->
                            err.userAction.actionIntent.intentSender

                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.authority == MediaStore.AUTHORITY ->
                            runCatching {
                                MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
                            }.getOrNull()

                        else -> null
                    }

                    if (intentSender != null) {
                        _pendingAudioWritePermission.value = PendingAudioWritePermissionRequest(
                            track = req.track,
                            lyricsText = req.lyricsText,
                            outcome = outcome,
                            intentSender = intentSender
                        )
                    } else {
                        // Read-only SAF URI or restricted storage provider -> offer seamless Save Copy with embedded lyrics
                        _pendingAudioSaveCopy.value = PendingAudioSaveCopyRequest(
                            track = req.track,
                            lyricsText = req.lyricsText,
                            suggestedFileName = req.track.fileName.ifBlank { "${req.track.title}.${req.track.codecLabel.lowercase()}" },
                            mimeType = req.track.mimeType.ifBlank { "audio/*" },
                            patchedBytes = outcome.patchedBytes
                        )
                        showBanner("原路径为只读授权，请选择保存位置以输出已刻录歌词的完整音频")
                    }
                }
            )
        }
    }

    fun completeAuthorizedAudioWrite(granted: Boolean) {
        val req = _pendingAudioWritePermission.value ?: return
        _pendingAudioWritePermission.value = null
        if (!granted) {
            showBanner("未授予系统文件修改权限，已取消刻录")
            return
        }
        viewModelScope.launch {
            val uri = Uri.parse(req.track.contentUri)
            val result = withContext(Dispatchers.IO) {
                writePatchedAudioDirect(uri, req.outcome.patchedBytes)
            }
            result.fold(
                onSuccess = {
                    finalizeBurnSuccess(req.track, req.lyricsText, req.outcome)
                },
                onFailure = { err ->
                    showBanner("刻录写入失败：${err.localizedMessage ?: "系统拒绝写入"}")
                }
            )
        }
    }

    fun completeAudioSaveCopy(targetUri: Uri?) {
        val req = _pendingAudioSaveCopy.value ?: return
        _pendingAudioSaveCopy.value = null
        if (targetUri == null) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                writePatchedAudioDirect(targetUri, req.patchedBytes)
            }
            result.fold(
                onSuccess = {
                    haptics.harmonicChime()
                    showBanner("已另存为内嵌歌词的音频文件：${req.suggestedFileName}")
                },
                onFailure = { err ->
                    showBanner("另存音频失败：${err.localizedMessage ?: "无写入权限"}")
                }
            )
        }
    }

    private fun writePatchedAudioDirect(uri: Uri, bytes: ByteArray): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
            return@runCatching
        }
        context.contentResolver.openFileDescriptor(uri, "rwt")?.use { pfd ->
            java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                fos.channel.truncate(0)
                fos.write(bytes)
                fos.flush()
            }
            return@runCatching
        }
        error("无法获取音频文件写入流")
    }

    private suspend fun finalizeBurnSuccess(track: Track, lyricsText: String, outcome: AudioTagWriteOutcome) {
        val updatedTrack = track.copy(embeddedLyrics = lyricsText)
        trackDao.upsert(updatedTrack)
        if (playbackState.value.currentTrack?.id == track.id) {
            playbackController.syncTrackMetadataUpdate(updatedTrack)
            loadLyricsForTrack(updatedTrack)
        }
        haptics.harmonicChime()
        showBanner("已将歌词刻录到原音频「${track.title}」(${outcome.tagFieldLabel})")
    }

    fun movePlaylistTrack(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val currentList = playlistTracksMap.value[playlistId].orEmpty()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return
        haptics.tickLight()
        val reordered = currentList.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
        viewModelScope.launch {
            reordered.forEachIndexed { idx, track ->
                playlistDao.updateTrackPosition(playlistId, track.id, idx)
            }
        }
    }

    fun getTracksForPlaylist(playlistId: Long): List<Track> {
        return playlistTracksMap.value[playlistId].orEmpty()
    }

    // Settings & DSP Updates
    fun setDarkTheme(dark: Boolean) = viewModelScope.launch { prefs.setDarkTheme(dark) }
    fun setNavBarStyle(style: NavBarStyle) = viewModelScope.launch {
        haptics.tickLight()
        prefs.setNavBarStyle(style)
    }
    fun setVisualEffectsEnhanced(enabled: Boolean) = viewModelScope.launch {
        haptics.springImpact()
        prefs.setVisualEffectsEnhanced(enabled)
    }
    fun setShowLibraryHero(show: Boolean) = viewModelScope.launch {
        haptics.tickLight()
        prefs.setShowLibraryHero(show)
    }
    fun setShowGeneralAudioNotice(show: Boolean) = viewModelScope.launch {
        haptics.tickLight()
        prefs.setShowGeneralAudioNotice(show)
    }
    fun setEnableGeneralAudioMode(enabled: Boolean) = viewModelScope.launch {
        haptics.tickLight()
        prefs.setEnableGeneralAudioMode(enabled)
    }
    fun setDeletionGuardMode(mode: DeletionGuardMode) = viewModelScope.launch {
        haptics.tickLight()
        prefs.setDeletionGuardMode(mode)
    }
    fun setHapticProfile(profile: HapticProfile) = viewModelScope.launch {
        prefs.setHapticProfile(profile)
        haptics.profile = profile
        haptics.springImpact()
    }

    fun updateScanFilter(config: ScanFilterConfig) = viewModelScope.launch {
        prefs.updateScanFilter(config)
    }

    fun updateLyricSettings(settings: LyricSettings) = viewModelScope.launch {
        haptics.tickLight()
        prefs.updateLyricSettings(settings)
    }

    fun updateEqState(state: EqState) = viewModelScope.launch {
        prefs.updateEqState(state)
        AcousticDspEngine.applyState(state)
    }

    fun applyEqPreset(preset: EqPreset) {
        haptics.springImpact()
        val normalizedBands = normalizeEqBands(preset.bandLevelsDb)
        updateEqState(
            EqState(
                enabled = true,
                presetId = preset.id,
                bandLevelsDb = normalizedBands,
                bassBoostPercent = preset.bassBoostPercent,
                virtualizerPercent = preset.virtualizerPercent
            )
        )
    }

    fun saveCustomEqPreset(name: String, state: EqState) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        haptics.harmonicChime()
        val preset = EqPreset(
            id = "custom_${System.currentTimeMillis()}",
            name = cleaned,
            bandLevelsDb = normalizeEqBands(state.bandLevelsDb),
            bassBoostPercent = state.bassBoostPercent,
            virtualizerPercent = state.virtualizerPercent,
            isCustom = true
        )
        viewModelScope.launch {
            prefs.saveCustomEqPreset(preset)
            updateEqState(
                state.copy(
                    enabled = true,
                    presetId = preset.id,
                    bandLevelsDb = preset.bandLevelsDb
                )
            )
        }
    }

    fun deleteCustomEqPreset(presetId: String) {
        val snapshot = customEqPresets.value.firstOrNull { it.id == presetId } ?: return
        val previousEqState = eqState.value
        requestProtectedDeletion(
            confirmTitle = "删除自定义均衡器预设",
            confirmMessage = "确定删除自定义预设「${snapshot.name}」吗？",
            confirmLabel = "确认删除",
            deletedSummary = "已删除自定义预设「${snapshot.name}」",
            restoredSummary = "自定义预设「${snapshot.name}」"
        ) {
            prefs.deleteCustomEqPreset(presetId)
            if (eqState.value.presetId == presetId) {
                updateEqState(eqState.value.copy(presetId = "custom"))
            }
            suspend {
                prefs.saveCustomEqPreset(snapshot)
                if (previousEqState.presetId == presetId) {
                    updateEqState(previousEqState)
                }
            }
        }
    }

    fun applyAndSaveEqPreset(preset: EqPreset) {
        haptics.harmonicChime()
        val normalized = normalizeEqBands(preset.bandLevelsDb)
        val savedPreset = preset.copy(
            id = "ai_${System.currentTimeMillis()}",
            bandLevelsDb = normalized,
            isCustom = true
        )
        viewModelScope.launch {
            prefs.saveCustomEqPreset(savedPreset)
            updateEqState(
                EqState(
                    enabled = true,
                    presetId = savedPreset.id,
                    bandLevelsDb = normalized,
                    bassBoostPercent = savedPreset.bassBoostPercent,
                    virtualizerPercent = savedPreset.virtualizerPercent
                )
            )
        }
    }

    fun updateAiConfig(config: AiConfig) = viewModelScope.launch {
        prefs.updateAiConfig(config)
        haptics.tickLight()
    }

    fun toggleAiWebSearch() = viewModelScope.launch {
        val current = prefs.aiConfigFlow.first()
        val next = !current.enableWebSearch
        prefs.updateAiConfig(current.copy(enableWebSearch = next))
        haptics.tickLight()
    }

    fun updateAiCatalogAttachmentPrefs(attachment: AiCatalogAttachmentPrefs) = viewModelScope.launch {
        prefs.updateAiCatalogAttachmentPrefs(attachment)
        haptics.tickLight()
    }

    fun triggerReasoningDetentHaptic(level: ReasoningEffortLevel) {
        when (level) {
            ReasoningEffortLevel.OFF,
            ReasoningEffortLevel.LOW,
            ReasoningEffortLevel.MEDIUM -> haptics.tickLight()
            ReasoningEffortLevel.HIGH,
            ReasoningEffortLevel.XHIGH -> haptics.springImpact()
            ReasoningEffortLevel.MAX,
            ReasoningEffortLevel.ULTRA -> haptics.harmonicChime()
        }
    }

    fun updateReasoningEffort(level: ReasoningEffortLevel) {
        viewModelScope.launch {
            val current = prefs.aiConfigFlow.first()
            val updatedEnabled = if (level in current.enabledReasoningLevels) {
                current.enabledReasoningLevels
            } else {
                current.enabledReasoningLevels + level
            }
            prefs.updateAiConfig(
                current.copy(
                    reasoningEffort = level,
                    enabledReasoningLevels = updatedEnabled
                )
            )
        }
    }

    fun toggleExtendedReasoningLevel(level: ReasoningEffortLevel) {
        if (level.isDefaultEnabled) return
        haptics.tickLight()
        viewModelScope.launch {
            val current = prefs.aiConfigFlow.first()
            val exists = level in current.enabledReasoningLevels
            val updatedSet = if (exists) {
                current.enabledReasoningLevels - level
            } else {
                current.enabledReasoningLevels + level
            }
            val nextEffort = if (exists && current.reasoningEffort == level) {
                ReasoningEffortLevel.HIGH
            } else {
                current.reasoningEffort
            }
            prefs.updateAiConfig(
                current.copy(
                    enabledReasoningLevels = updatedSet,
                    reasoningEffort = nextEffort
                )
            )
        }
    }

    // Multi-Session Independent AI Chat Engine
    fun createNewAiSession() {
        haptics.springImpact()
        viewModelScope.launch {
            val cfg = prefs.aiConfigFlow.first()
            val count = aiSessions.value.size + 1
            val newId = aiChatDao.insertSession(
                AiChatSessionEntity(
                    title = "新对话 #$count",
                    modelId = cfg.deepSeekModelId,
                    reasoningEffort = cfg.reasoningEffort.apiValue,
                    boundTrackTitle = playbackState.value.currentTrack?.title
                )
            )
            _activeAiSessionId.value = newId
        }
    }

    fun selectAiSession(sessionId: Long) {
        haptics.tickLight()
        _activeAiSessionId.value = sessionId
    }

    fun renameAiSession(sessionId: Long, title: String) {
        val cleaned = title.trim()
        if (cleaned.isEmpty()) return
        haptics.tickLight()
        viewModelScope.launch {
            aiChatDao.renameSession(sessionId, cleaned)
        }
    }

    fun clearAiSession(sessionId: Long) {
        haptics.tickLight()
        stopAiGeneration(sessionId)
        viewModelScope.launch {
            aiChatDao.clearSessionMessages(sessionId)
        }
    }

    fun deleteAiSession(sessionId: Long) {
        val sessionSnapshot = aiSessions.value.firstOrNull { it.id == sessionId } ?: return
        val wasActive = _activeAiSessionId.value == sessionId
        requestProtectedDeletion(
            confirmTitle = "删除 AI 对话窗口",
            confirmMessage = "确定删除对话窗口「${sessionSnapshot.title}」及其全部历史消息吗？",
            confirmLabel = "确认删除",
            deletedSummary = "已删除 AI 对话「${sessionSnapshot.title}」",
            restoredSummary = "AI 对话「${sessionSnapshot.title}」"
        ) {
            val messagesSnapshot = aiChatDao.getMessagesSnapshot(sessionId)
            aiSessionJobs.remove(sessionId)?.cancel()
            _aiSessionRuntimeStates.update { it - sessionId }
            aiChatDao.deleteSession(sessionId)
            val remaining = aiChatDao.getSessionsSnapshot()
            var autoCreatedFallbackId: Long? = null
            if (remaining.isEmpty()) {
                val cfg = prefs.aiConfigFlow.first()
                val freshId = aiChatDao.insertSession(
                    AiChatSessionEntity(
                        title = "声学与音乐对话",
                        modelId = cfg.deepSeekModelId,
                        reasoningEffort = cfg.reasoningEffort.apiValue
                    )
                )
                autoCreatedFallbackId = freshId
                _activeAiSessionId.value = freshId
            } else if (wasActive) {
                _activeAiSessionId.value = remaining.first().id
            }
            suspend {
                aiChatDao.insertSession(sessionSnapshot)
                messagesSnapshot.forEach { msg -> aiChatDao.insertMessage(msg) }
                autoCreatedFallbackId?.let { fallbackId ->
                    val fallbackMsgs = aiChatDao.getMessagesSnapshot(fallbackId)
                    if (fallbackMsgs.isEmpty()) {
                        aiChatDao.deleteSession(fallbackId)
                    }
                }
                if (wasActive) {
                    _activeAiSessionId.value = sessionSnapshot.id
                }
            }
        }
    }

    fun stopAiGeneration(sessionId: Long) {
        val job = aiSessionJobs.remove(sessionId)
        if (job != null) {
            haptics.tickLight()
            job.cancel()
        }
    }

    fun runQuickAiTask(
        task: AiTaskType,
        attachCoverVision: Boolean,
        attachLibraryCatalog: Boolean = false
    ) {
        val currentTrack = playbackState.value.currentTrack?.takeIf { !it.isGeneralAudio }
        val cleanPrompt = aiChatClient.buildQuickTaskPrompt(
            taskType = task,
            track = currentTrack
        )
        sendAiMessage(
            userText = cleanPrompt,
            attachTrackContext = currentTrack != null,
            attachCoverVision = attachCoverVision || task == AiTaskType.TRACK_DEEP_DIVE,
            attachLibraryCatalog = attachLibraryCatalog || task == AiTaskType.MOOD_CURATION
        )
    }

    private fun resolveAttachedCatalogTracks(
        allMusic: List<Track>,
        attachmentPrefs: AiCatalogAttachmentPrefs,
        attachLibraryCatalog: Boolean
    ): List<Track> {
        if (!attachLibraryCatalog || allMusic.isEmpty()) return emptyList()
        return if (!attachmentPrefs.hasCustomSelection) {
            allMusic
        } else {
            val selected = allMusic.filter { it.id in attachmentPrefs.selectedTrackIds }
            // Fallback to full list only if user never explicitly deselected everything
            selected
        }
    }

    private suspend fun loadPlainLyricsTextForTrack(
        track: Track,
        activeTrackId: String?,
        activeLyricText: String?
    ): String? {
        if (track.id == activeTrackId && !activeLyricText.isNullOrBlank()) {
            return activeLyricText
        }
        val saved = runCatching { customLyricDao.getCustomLyric(track.id) }.getOrNull()
        if (saved != null && saved.rawContent.isNotBlank()) {
            val parsed = runCatching { LyricParser.parse(saved.rawContent, saved.sourceLabel) }.getOrNull()
            val text = parsed?.lines?.joinToString("\n") { it.text }?.trim()
            if (!text.isNullOrBlank()) return text
        }
        if (!track.embeddedLyrics.isNullOrBlank()) {
            val parsed = runCatching { LyricParser.parse(track.embeddedLyrics, "内嵌歌词") }.getOrNull()
            val text = parsed?.lines?.joinToString("\n") { it.text }?.trim()
            if (!text.isNullOrBlank()) return text
        }
        val sidecar = runCatching { withContext(Dispatchers.IO) { scanner.findSidecarLyric(track) } }.getOrNull()
        if (sidecar != null && sidecar.first.isNotBlank()) {
            val parsed = runCatching { LyricParser.parse(sidecar.first, sidecar.second) }.getOrNull()
            val text = parsed?.lines?.joinToString("\n") { it.text }?.trim()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    fun sendAiMessage(
        userText: String,
        attachTrackContext: Boolean,
        attachCoverVision: Boolean,
        attachLibraryCatalog: Boolean = false,
        targetSessionIdOverride: Long? = null,
        effortOverride: ReasoningEffortLevel? = null
    ) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return
        haptics.springImpact()

        viewModelScope.launch {
            val config = prefs.aiConfigFlow.first()
            val catalogPrefs = prefs.aiCatalogAttachmentPrefsFlow.first()
            val sessionId = targetSessionIdOverride?.takeIf { it > 0L }
                ?: _activeAiSessionId.value.takeIf { it > 0L }
                ?: aiChatDao.insertSession(
                    AiChatSessionEntity(
                        title = trimmed.take(18),
                        modelId = config.deepSeekModelId,
                        reasoningEffort = (effortOverride ?: config.reasoningEffort).apiValue
                    )
                ).also { _activeAiSessionId.value = it }

            val historyBefore = aiChatDao.getMessagesSnapshot(sessionId)
            val track = playbackState.value.currentTrack?.takeIf { !it.isGeneralAudio }
            val useTrack = attachTrackContext && track != null
            val useVision = attachCoverVision && config.enableVisionForCovers && !track?.artworkPath.isNullOrBlank()
            val attachedCatalogTracks = resolveAttachedCatalogTracks(
                allMusic = musicTracks.value,
                attachmentPrefs = catalogPrefs,
                attachLibraryCatalog = attachLibraryCatalog
            )
            val activeLyricSnippet = _currentLyric.value.lines.take(36).joinToString("\n") { it.text }
                .takeIf { it.isNotBlank() }

            // Compute how many unique tracks will actually carry lyrics (deduplicating current track!)
            val explicitLyricIds = if (attachedCatalogTracks.isNotEmpty()) {
                attachedCatalogTracks.map { it.id }.filter { it in catalogPrefs.lyricTrackIds }.toSet()
            } else {
                emptySet()
            }
            val lyricBadgeCount = buildSet {
                addAll(explicitLyricIds)
                if (useTrack && track != null && !activeLyricSnippet.isNullOrBlank()) {
                    add(track.id)
                }
            }.size

            val attachedLabel = AiAttachmentBadgeCodec.encode(
                trackTitle = if (useTrack) track?.title else null,
                catalogCount = attachedCatalogTracks.size,
                lyricCount = lyricBadgeCount,
                usedVision = useVision,
                usedWebSearch = config.enableWebSearch && config.activeProvider != com.example.model.AiProviderType.GEMINI
            )

            // Persist clean user message immediately (never dumping raw song lists/metadata into UI bubble)
            aiChatDao.insertMessage(
                AiChatMessageEntity(
                    sessionId = sessionId,
                    role = "user",
                    content = trimmed,
                    usedVision = useVision,
                    attachedTrackLabel = attachedLabel
                )
            )

            // Instant fallback title for brand new session before optional AI title completes
            val sessionEntity = aiSessions.value.firstOrNull { it.id == sessionId }
            val isFirstTurn = historyBefore.isEmpty()
            if (isFirstTurn && sessionEntity != null &&
                (sessionEntity.title == "声学与音乐对话" || sessionEntity.title.startsWith("新对话"))
            ) {
                val smartTitle = trimmed.lineSequence().firstOrNull()?.take(20)?.trim().orEmpty()
                if (smartTitle.isNotEmpty()) {
                    aiChatDao.renameSession(sessionId, smartTitle)
                }
            }

            launchSessionCompletionJob(
                sessionId = sessionId,
                config = config,
                catalogPrefs = catalogPrefs,
                historyBefore = historyBefore,
                promptText = trimmed,
                attachTrackContext = useTrack,
                attachCoverVision = useVision,
                attachLibraryCatalog = attachedCatalogTracks.isNotEmpty(),
                effortOverride = effortOverride,
                shouldAutoGenerateTitle = isFirstTurn && config.autoGenerateSessionTitle
            )
        }
    }

    fun regenerateSessionTitle(sessionId: Long, showFeedbackToast: Boolean = true) {
        viewModelScope.launch {
            val config = prefs.aiConfigFlow.first()
            val msgs = aiChatDao.getMessagesSnapshot(sessionId)
            val firstUser = msgs.firstOrNull { it.role == "user" }?.content
            if (firstUser.isNullOrBlank()) {
                if (showFeedbackToast) showBanner("该窗口还没有提问内容，无法生成标题")
                return@launch
            }
            val firstAssistant = msgs.firstOrNull { it.role == "assistant" }?.content
            if (showFeedbackToast) {
                haptics.tickLight()
            }
            val res = aiChatClient.generateSessionTitle(config, firstUser, firstAssistant)
            res.fold(
                onSuccess = { title ->
                    aiChatDao.renameSession(sessionId, title)
                    if (showFeedbackToast) {
                        haptics.harmonicChime()
                    }
                },
                onFailure = { err ->
                    if (showFeedbackToast) {
                        showBanner("AI 标题生成失败：${err.localizedMessage ?: "请检查网络或配置"}", StatusToastLevel.ERROR)
                    }
                }
            )
        }
    }

    fun sendErrorFeedbackToModel(errorMsg: AiChatMessageEntity) {
        val report = AiErrorReport(
            summary = errorMsg.errorSummary ?: errorMsg.content,
            httpStatusCode = errorMsg.httpStatusCode,
            endpoint = errorMsg.errorEndpoint.orEmpty(),
            modelId = errorMsg.modelUsed.orEmpty(),
            reasoningEffort = errorMsg.reasoningEffortUsed ?: "off",
            rawDetail = errorMsg.errorRawDetail.orEmpty()
        )
        // If the previous request failed with HTTP 400 mentioning reasoning_effort, send feedback with OFF so it succeeds
        val likelyReasoningReject = errorMsg.httpStatusCode == 400 &&
            (errorMsg.errorRawDetail?.contains("reasoning", ignoreCase = true) == true ||
                errorMsg.errorSummary?.contains("reasoning", ignoreCase = true) == true)

        sendAiMessage(
            userText = report.buildModelFeedbackPrompt(),
            attachTrackContext = false,
            attachCoverVision = false,
            attachLibraryCatalog = false,
            targetSessionIdOverride = errorMsg.sessionId,
            effortOverride = if (likelyReasoningReject) ReasoningEffortLevel.OFF else null
        )
    }

    fun retryLastAiMessage(overrideEffort: ReasoningEffortLevel? = null) {
        val sessionId = _activeAiSessionId.value
        if (sessionId <= 0L) return
        haptics.springImpact()

        viewModelScope.launch {
            val allMsgs = aiChatDao.getMessagesSnapshot(sessionId)
            val lastUserIdx = allMsgs.indexOfLast { it.role == "user" }
            if (lastUserIdx < 0) {
                showBanner("当前窗口暂无可重试的提问")
                return@launch
            }
            val lastUserMsg = allMsgs[lastUserIdx]
            val historyBefore = allMsgs.take(lastUserIdx)
            val config = prefs.aiConfigFlow.first()
            val catalogPrefs = prefs.aiCatalogAttachmentPrefsFlow.first()
            val decodedBadges = AiAttachmentBadgeCodec.decode(lastUserMsg.attachedTrackLabel, lastUserMsg.usedVision)
            val hadTrack = decodedBadges.any { it.type == com.example.model.AiAttachmentBadgeType.TRACK }
            val hadCatalog = decodedBadges.any { it.type == com.example.model.AiAttachmentBadgeType.CATALOG } ||
                lastUserMsg.attachedTrackLabel?.contains("曲库(") == true
            launchSessionCompletionJob(
                sessionId = sessionId,
                config = config,
                catalogPrefs = catalogPrefs,
                historyBefore = historyBefore,
                promptText = lastUserMsg.content,
                attachTrackContext = hadTrack,
                attachCoverVision = lastUserMsg.usedVision,
                attachLibraryCatalog = hadCatalog,
                effortOverride = overrideEffort,
                shouldAutoGenerateTitle = false
            )
        }
    }

    private fun launchSessionCompletionJob(
        sessionId: Long,
        config: AiConfig,
        catalogPrefs: AiCatalogAttachmentPrefs,
        historyBefore: List<AiChatMessageEntity>,
        promptText: String,
        attachTrackContext: Boolean,
        attachCoverVision: Boolean,
        attachLibraryCatalog: Boolean,
        effortOverride: ReasoningEffortLevel?,
        shouldAutoGenerateTitle: Boolean
    ) {
        // Cancel only any in-flight job on this exact session; other windows remain untouched!
        aiSessionJobs.remove(sessionId)?.cancel()

        val effectiveEffort = effortOverride ?: config.reasoningEffort
        val track = playbackState.value.currentTrack?.takeIf { !it.isGeneralAudio }
        val lyricSnippet = _currentLyric.value.lines.take(36).joinToString("\n") { it.text }
            .takeIf { it.isNotBlank() }
        // Only send selected music tracks (never general local audio!) to AI curation/analysis
        val selectedLibrarySnapshot = resolveAttachedCatalogTracks(
            allMusic = musicTracks.value,
            attachmentPrefs = catalogPrefs,
            attachLibraryCatalog = attachLibraryCatalog
        )
        val startMs = System.currentTimeMillis()

        _aiSessionRuntimeStates.update { map ->
            map + (
                sessionId to AiSessionRuntimeState(
                    isGenerating = true,
                    isThinkingPhase = effectiveEffort != ReasoningEffortLevel.OFF,
                    isSearchingWeb = false,
                    startedAtMs = startMs,
                    activeModelId = config.deepSeekModelId,
                    activeEffort = effectiveEffort
                )
            )
        }

        val job = viewModelScope.launch {
            try {
                // Load lyrics for selected catalog songs with lyric attachment enabled
                val catalogLyricsMap = if (attachLibraryCatalog && selectedLibrarySnapshot.isNotEmpty()) {
                    buildMap {
                        for (itemTrack in selectedLibrarySnapshot) {
                            val isPlayingInCatalog = attachTrackContext && track != null && itemTrack.id == track.id
                            val shouldIncludeLyric = itemTrack.id in catalogPrefs.lyricTrackIds || isPlayingInCatalog
                            if (shouldIncludeLyric) {
                                val text = loadPlainLyricsTextForTrack(
                                    track = itemTrack,
                                    activeTrackId = track?.id,
                                    activeLyricText = lyricSnippet
                                )
                                if (!text.isNullOrBlank()) {
                                    put(itemTrack.id, text)
                                }
                            }
                        }
                    }
                } else {
                    emptyMap()
                }

                val result = aiChatClient.streamChatCompletion(
                    config = config,
                    history = historyBefore,
                    currentUserText = promptText,
                    effortOverride = effectiveEffort,
                    currentTrack = track,
                    lyricsSnippet = lyricSnippet,
                    attachTrackContext = attachTrackContext,
                    attachCoverVision = attachCoverVision,
                    attachLibraryCatalog = attachLibraryCatalog && selectedLibrarySnapshot.isNotEmpty(),
                    librarySample = selectedLibrarySnapshot,
                    catalogLyricsByTrackId = catalogLyricsMap,
                    onWebSearchStatus = { searching, label ->
                        _aiSessionRuntimeStates.update { map ->
                            val cur = map[sessionId] ?: return@update map
                            if (cur.isSearchingWeb == searching && cur.webSearchStatusLabel == label) map
                            else map + (
                                sessionId to cur.copy(
                                    isSearchingWeb = searching,
                                    webSearchStatusLabel = if (searching) label else ""
                                )
                            )
                        }
                    },
                    onReasoningDelta = { rDelta ->
                        _aiSessionRuntimeStates.update { map ->
                            val cur = map[sessionId] ?: return@update map
                            map + (
                                sessionId to cur.copy(
                                    isThinkingPhase = true,
                                    liveReasoning = cur.liveReasoning + rDelta
                                )
                            )
                        }
                    },
                    onContentDelta = { cDelta ->
                        _aiSessionRuntimeStates.update { map ->
                            val cur = map[sessionId] ?: return@update map
                            map + (
                                sessionId to cur.copy(
                                    isThinkingPhase = false,
                                    isSearchingWeb = false,
                                    webSearchStatusLabel = "",
                                    liveContent = cur.liveContent + cDelta
                                )
                            )
                        }
                    }
                )

                result.fold(
                    onSuccess = { completion ->
                        aiChatDao.insertMessage(
                            AiChatMessageEntity(
                                sessionId = sessionId,
                                role = "assistant",
                                content = completion.content,
                                reasoningContent = completion.reasoningContent,
                                reasoningDurationMs = completion.reasoningDurationMs,
                                reasoningEffortUsed = completion.reasoningEffortUsed.apiValue,
                                modelUsed = completion.modelUsed,
                                usedVision = completion.usedVision,
                                suggestedEqJson = completion.suggestedEqJson,
                                curatedTitlesCsv = completion.curatedTrackTitles
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString("||"),
                                suggestedPlaylistJson = completion.suggestedPlaylistJson
                            )
                        )
                        aiChatDao.touchSession(
                            sessionId = sessionId,
                            updatedAt = System.currentTimeMillis(),
                            modelId = completion.modelUsed,
                            effort = completion.reasoningEffortUsed.apiValue
                        )
                        if (_activeAiSessionId.value == sessionId) {
                            haptics.harmonicChime()
                        }
                        if (shouldAutoGenerateTitle) {
                            viewModelScope.launch {
                                aiChatClient.generateSessionTitle(config, promptText, completion.content)
                                    .onSuccess { generatedTitle ->
                                        aiChatDao.renameSession(sessionId, generatedTitle)
                                    }
                            }
                        }
                    },
                    onFailure = { err ->
                        val report = (err as? AiApiException)?.report ?: AiErrorReport(
                            summary = err.localizedMessage ?: "AI 请求失败",
                            modelId = config.deepSeekModelId,
                            reasoningEffort = effectiveEffort.apiValue,
                            rawDetail = err.stackTraceToString().take(1800)
                        )
                        aiChatDao.insertMessage(
                            AiChatMessageEntity(
                                sessionId = sessionId,
                                role = "error",
                                content = report.summary,
                                reasoningEffortUsed = report.reasoningEffort,
                                modelUsed = report.modelId,
                                errorSummary = report.summary,
                                errorRawDetail = report.rawDetail,
                                httpStatusCode = report.httpStatusCode,
                                errorEndpoint = report.endpoint
                            )
                        )
                        aiChatDao.touchSession(
                            sessionId = sessionId,
                            updatedAt = System.currentTimeMillis(),
                            modelId = config.deepSeekModelId,
                            effort = effectiveEffort.apiValue
                        )
                    }
                )
            } catch (cancel: CancellationException) {
                // Save any partial response already received before user pressed Stop
                val partial = _aiSessionRuntimeStates.value[sessionId]
                if (partial != null && (partial.liveContent.isNotBlank() || partial.liveReasoning.isNotBlank())) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            aiChatDao.insertMessage(
                                AiChatMessageEntity(
                                    sessionId = sessionId,
                                    role = "assistant",
                                    content = partial.liveContent.ifBlank { "（已手动停止生成）" } +
                                        if (partial.liveContent.isNotBlank()) "\n\n（已手动停止生成）" else "",
                                    reasoningContent = partial.liveReasoning.takeIf { it.isNotBlank() },
                                    reasoningDurationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                                    reasoningEffortUsed = effectiveEffort.apiValue,
                                    modelUsed = config.deepSeekModelId
                                )
                            )
                        }
                    }
                }
                throw cancel
            } finally {
                aiSessionJobs.remove(sessionId)
                _aiSessionRuntimeStates.update { it - sessionId }
            }
        }
        aiSessionJobs[sessionId] = job
    }

    private fun normalizeTitleForMatch(raw: String): String {
        return raw
            .trim()
            .replace('（', '(')
            .replace('）', ')')
            .replace('【', '[')
            .replace('】', ']')
            .replace('　', ' ')
            .trim('《', '》', '"', '\'', '「', '」', '“', '”')
            .replace(Regex("""\s+"""), " ")
            .lowercase()
    }

    private fun stripTitleNoise(normalized: String): String {
        return normalized
            .replace(Regex("""[\(\[][^\)\]]*[\)\]]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
    }

    private suspend fun matchTracksFromLibraryByTitles(titles: List<String>): List<Track> {
        val snapshot = withContext(Dispatchers.IO) {
            val cached = musicTracks.value
            if (cached.isNotEmpty()) cached
            else {
                val all = allTracks.value.ifEmpty { trackDao.getAllTracksSnapshot() }
                all.filter { !it.isGeneralAudio }.ifEmpty { all }
            }
        }
        val result = mutableListOf<Track>()
        val usedIds = mutableSetOf<String>()
        titles.forEach { rawTitle ->
            val cleaned = normalizeTitleForMatch(rawTitle)
            if (cleaned.isEmpty()) return@forEach
            val stripped = stripTitleNoise(cleaned)

            val matched = snapshot.firstOrNull { t ->
                t.id !in usedIds && normalizeTitleForMatch(t.title) == cleaned
            } ?: snapshot.firstOrNull { t ->
                val tNorm = normalizeTitleForMatch(t.title)
                t.id !in usedIds && (tNorm.contains(cleaned) || cleaned.contains(tNorm))
            } ?: if (stripped.length >= 2) {
                snapshot.firstOrNull { t ->
                    val tStripped = stripTitleNoise(normalizeTitleForMatch(t.title))
                    t.id !in usedIds && tStripped.isNotEmpty() &&
                        (tStripped == stripped || tStripped.contains(stripped) || stripped.contains(tStripped))
                }
            } else {
                null
            }

            if (matched != null) {
                usedIds.add(matched.id)
                result.add(matched)
            }
        }
        return result
    }

    fun playCuratedTracks(titles: List<String>) {
        viewModelScope.launch {
            val matched = matchTracksFromLibraryByTitles(titles)
            if (matched.isNotEmpty()) {
                playAll(matched)
                _openNowPlayingTrigger.value = true
            } else {
                showBanner("未在本地音乐曲库匹配到对应曲目", StatusToastLevel.WARNING)
            }
        }
    }

    fun createPlaylistFromAiSuggestion(suggestion: AiSuggestedPlaylist) {
        viewModelScope.launch {
            val matched = matchTracksFromLibraryByTitles(suggestion.trackTitles)
            if (matched.isEmpty()) {
                showBanner("未能从本地音乐曲库匹配到歌单中的曲目", StatusToastLevel.WARNING)
                return@launch
            }
            val desc = suggestion.description.ifBlank { "由 AI 音乐策展生成（共 ${matched.size} 首）" }
            createPlaylist(
                name = suggestion.name,
                description = desc,
                initialTrackIds = matched.map { it.id },
                showToast = true
            )
        }
    }

    // ── Local Settings Backup Export & Import ──

    fun requestExportSettingsBackup(includeApiKey: Boolean) {
        haptics.tickLight()
        _pendingSettingsBackupExport.value = PendingSettingsBackupExportRequest(
            suggestedFileName = SettingsBackupCodec.suggestFileName(),
            includeApiKey = includeApiKey
        )
    }

    fun completeExportSettingsBackup(targetUri: Uri?) {
        val req = _pendingSettingsBackupExport.value ?: return
        _pendingSettingsBackupExport.value = null
        if (targetUri == null) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val snapshot = prefs.exportSettingsSnapshot()
                    val json = SettingsBackupCodec.encode(
                        snapshot = snapshot,
                        includeApiKey = req.includeApiKey
                    )
                    context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: error("无法写入目标备份文件")
                }
            }
            result.fold(
                onSuccess = {
                    haptics.harmonicChime()
                    val suffix = if (req.includeApiKey) "（含 AI 密钥）" else "（已脱敏）"
                    showBanner("设置备份已导出$suffix", StatusToastLevel.SUCCESS)
                },
                onFailure = { err ->
                    showBanner("导出设置备份失败：${err.localizedMessage ?: "写入被拒绝"}", StatusToastLevel.ERROR)
                }
            )
        }
    }

    fun inspectSettingsBackupFile(sourceUri: Uri) {
        haptics.tickLight()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = resolveDisplayName(sourceUri) ?: "auralis_settings.json"
                    val rawText = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("无法读取选中的备份文件")
                    val parsed = SettingsBackupCodec.decode(rawText).getOrThrow()
                    fileName to parsed
                }
            }
            result.fold(
                onSuccess = { (fileName, parsed) ->
                    _pendingSettingsBackupImport.value = PendingSettingsBackupImportPreview(
                        fileName = fileName,
                        parsed = parsed
                    )
                },
                onFailure = { err ->
                    showBanner(
                        "备份文件无效：${err.localizedMessage ?: "JSON 格式解析失败"}",
                        StatusToastLevel.ERROR
                    )
                }
            )
        }
    }

    fun confirmImportSettingsBackup(restoreApiKey: Boolean) {
        val pending = _pendingSettingsBackupImport.value ?: return
        _pendingSettingsBackupImport.value = null
        viewModelScope.launch {
            runCatching {
                prefs.importSettingsSnapshot(
                    snapshot = pending.parsed.snapshot,
                    restoreApiKey = restoreApiKey
                )
                haptics.profile = pending.parsed.snapshot.hapticProfile
                AcousticDspEngine.applyState(pending.parsed.snapshot.eqState)
                playbackController.transitionConfig = pending.parsed.snapshot.audioTransition
            }.fold(
                onSuccess = {
                    haptics.harmonicChime()
                    showBanner("已成功恢复本地设置备份", StatusToastLevel.SUCCESS)
                },
                onFailure = { err ->
                    showBanner("导入恢复失败：${err.localizedMessage ?: "未知错误"}", StatusToastLevel.ERROR)
                }
            )
        }
    }

    fun dismissImportSettingsBackup() {
        _pendingSettingsBackupImport.value = null
    }

    // ── Smart Audio Transitions ──

    fun updateAudioTransition(config: AudioTransitionConfig) {
        haptics.tickLight()
        playbackController.transitionConfig = config
        viewModelScope.launch {
            prefs.updateAudioTransition(config)
        }
    }

    // ── Danger Zone: Stratified Data Clearing (never touches local storage files) ──

    fun executeDangerZoneAction(action: DangerZoneAction) {
        haptics.springImpact()
        viewModelScope.launch {
            runCatching {
                when (action) {
                    DangerZoneAction.CLEAR_AUDIO_LIBRARY_ONLY -> {
                        val existingTracks = withContext(Dispatchers.IO) { trackDao.getAllTracksSnapshot() }
                        val archivedCount = existingTracks.count {
                            it.playCount > 0 || it.lastPlayedAt > 0L || it.isFavorite
                        }
                        if (archivedCount > 0) {
                            prefs.archiveTrackRecords(existingTracks)
                        }
                        playbackController.clearQueueAndStop()
                        _currentLyric.value = LyricDocument(LyricFormat.NONE, emptyList())
                        _auraPalette.value = AuraPalette()
                        _pendingImportConflicts.value = emptyList()
                        withContext(Dispatchers.IO) {
                            trackDao.clearAll()
                        }
                        val note = if (archivedCount > 0) {
                            "已清空播放器内全部音频索引（已封存 $archivedCount 首曲目的播放与收藏记录，重新导入后自动加载，本地文件未受影响）"
                        } else {
                            "已清空播放器内全部音频索引（未触碰本地存储文件）"
                        }
                        showBanner(note, StatusToastLevel.SUCCESS)
                    }

                    DangerZoneAction.CLEAR_RECORDS_AND_LOGS -> {
                        aiSessionJobs.values.forEach { it.cancel() }
                        aiSessionJobs.clear()
                        _aiSessionRuntimeStates.value = emptyMap()
                        _currentLyric.value = LyricDocument(LyricFormat.NONE, emptyList())
                        playbackController.invalidateLyricCache(null)
                        withContext(Dispatchers.IO) {
                            trackDao.clearAllFavoritesAndPlayStats()
                            customLyricDao.clearAllCustomLyrics()
                            playlistDao.clearAllPlaylistTracks()
                            playlistDao.clearAllPlaylists()
                            aiChatDao.clearAllMessages()
                            aiChatDao.clearAllSessions()
                        }
                        prefs.clearAllUserRecordPrefs()
                        val cfg = prefs.aiConfigFlow.first()
                        val freshSessionId = withContext(Dispatchers.IO) {
                            aiChatDao.insertSession(
                                AiChatSessionEntity(
                                    title = "声学与音乐对话",
                                    modelId = cfg.deepSeekModelId,
                                    reasoningEffort = cfg.reasoningEffort.apiValue
                                )
                            )
                        }
                        _activeAiSessionId.value = freshSessionId
                        showBanner("已清除全部播放记录、歌单、歌词缓存、自定义预设与 AI 对话记录", StatusToastLevel.SUCCESS)
                    }

                    DangerZoneAction.RESET_ALL_SETTINGS -> {
                        prefs.resetAllSettingPrefsOnly()
                        haptics.profile = HapticProfile.STUDIO
                        AcousticDspEngine.applyState(EqState())
                        playbackController.transitionConfig = AudioTransitionConfig()
                        showBanner("已将全部偏好设置项还原为出厂默认值", StatusToastLevel.SUCCESS)
                    }

                    DangerZoneAction.NUKE_ALL_DATA -> {
                        aiSessionJobs.values.forEach { it.cancel() }
                        aiSessionJobs.clear()
                        _aiSessionRuntimeStates.value = emptyMap()
                        playbackController.clearQueueAndStop()
                        playbackController.invalidateLyricCache(null)
                        _currentLyric.value = LyricDocument(LyricFormat.NONE, emptyList())
                        _auraPalette.value = AuraPalette()
                        _pendingImportConflicts.value = emptyList()
                        _searchQuery.value = ""
                        _selectedFolder.value = null
                        _selectedAlbum.value = null
                        _selectedArtist.value = null
                        _formatFilter.value = LibraryFilterFormat.ALL
                        _sortOption.value = LibrarySortOption.RECENT
                        _hasCompletedOnboarding.value = false
                        withContext(Dispatchers.IO) {
                            trackDao.clearAll()
                            customLyricDao.clearAllCustomLyrics()
                            playlistDao.clearAllPlaylistTracks()
                            playlistDao.clearAllPlaylists()
                            aiChatDao.clearAllMessages()
                            aiChatDao.clearAllSessions()
                        }
                        prefs.clearAllPreferences()
                        haptics.profile = HapticProfile.STUDIO
                        AcousticDspEngine.applyState(EqState())
                        playbackController.transitionConfig = AudioTransitionConfig()
                        val defaultCfg = AiConfig()
                        val freshSessionId = withContext(Dispatchers.IO) {
                            aiChatDao.insertSession(
                                AiChatSessionEntity(
                                    title = "声学与音乐对话",
                                    modelId = defaultCfg.deepSeekModelId,
                                    reasoningEffort = defaultCfg.reasoningEffort.apiValue
                                )
                            )
                        }
                        _activeAiSessionId.value = freshSessionId
                        showBanner("已彻底清空应用内全部数据并恢复初始状态（本地音频文件完好无损）", StatusToastLevel.SUCCESS)
                    }
                }
            }.onFailure { err ->
                Log.e(TAG, "Danger zone action failed: $action", err)
                showBanner("清除操作未完成：${err.localizedMessage ?: "发生未知错误"}", StatusToastLevel.ERROR)
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    override fun onCleared() {
        aiSessionJobs.values.forEach { it.cancel() }
        aiSessionJobs.clear()
        playbackController.release()
        super.onCleared()
    }
}
