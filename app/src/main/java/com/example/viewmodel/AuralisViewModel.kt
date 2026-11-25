package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiApiException
import com.example.data.ai.AiTaskType
import com.example.data.ai.DeepSeekChatClient
import com.example.data.local.AuralisDatabase
import com.example.data.local.PreferencesRepository
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
import com.example.model.AuraPalette
import com.example.model.CustomLyricEntity
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
import com.example.model.ScanFilterConfig
import com.example.model.Track
import com.example.model.normalizeEqBands
import com.example.playback.AcousticDspEngine
import com.example.playback.HapticEngine
import com.example.playback.PaletteExtractor
import com.example.playback.PlaybackController
import com.example.playback.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private const val TAG = "AuralisViewModel"

data class OnlineLyricSearchState(
    val isSearching: Boolean = false,
    val candidates: List<OnlineLyricCandidate> = emptyList(),
    val sourceSummaries: Map<LyricSource, String> = emptyMap(),
    val error: String? = null
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
        onPlaybackError = { msg -> showBanner(msg) }
    )

    val playbackState: StateFlow<PlaybackState> = playbackController.state

    val darkTheme: StateFlow<Boolean> = prefs.darkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hapticProfile: StateFlow<HapticProfile> = prefs.hapticProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HapticProfile.STUDIO)

    val navBarStyle: StateFlow<NavBarStyle> = prefs.navBarStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavBarStyle.CLASSIC)

    val showLibraryHero: StateFlow<Boolean> = prefs.showLibraryHeroFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enableGeneralAudioMode: StateFlow<Boolean> = prefs.enableGeneralAudioModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val scanFilter: StateFlow<ScanFilterConfig> = prefs.scanFilterFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanFilterConfig())

    val lyricSettings: StateFlow<LyricSettings> = prefs.lyricSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricSettings())

    val eqState: StateFlow<EqState> = prefs.eqStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EqState())

    val customEqPresets: StateFlow<List<EqPreset>> = prefs.customEqPresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiConfig: StateFlow<AiConfig> = prefs.aiConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiConfig())

    val aiCatalogAttachmentPrefs: StateFlow<AiCatalogAttachmentPrefs> = prefs.aiCatalogAttachmentPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiCatalogAttachmentPrefs())

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

    // Library search, format filter, sort, folder filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _formatFilter = MutableStateFlow(LibraryFilterFormat.ALL)
    val formatFilter: StateFlow<LibraryFilterFormat> = _formatFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(LibrarySortOption.RECENT)
    val sortOption: StateFlow<LibrarySortOption> = _sortOption.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    val filteredTracks: StateFlow<List<Track>> = combine(
        musicTracks,
        _searchQuery,
        _formatFilter,
        _sortOption,
        _selectedFolder
    ) { tracks, query, fmt, sort, folder ->
        applyLibraryFilter(tracks, query, fmt, sort, folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredGeneralAudioTracks: StateFlow<List<Track>> = combine(
        generalAudioTracks,
        _searchQuery,
        _formatFilter,
        _sortOption,
        _selectedFolder
    ) { tracks, query, fmt, sort, folder ->
        applyLibraryFilter(tracks, query, fmt, sort, folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun applyLibraryFilter(
        tracks: List<Track>,
        query: String,
        fmt: LibraryFilterFormat,
        sort: LibrarySortOption,
        folder: String?
    ): List<Track> {
        val q = query.trim().lowercase()
        return tracks.filter { t ->
            val matchesQuery = q.isEmpty() ||
                t.title.lowercase().contains(q) ||
                t.artist.lowercase().contains(q) ||
                t.album.lowercase().contains(q) ||
                t.codecLabel.lowercase().contains(q) ||
                t.fileName.lowercase().contains(q)
            val matchesFmt = when (fmt) {
                LibraryFilterFormat.ALL -> true
                LibraryFilterFormat.LOSSLESS -> t.isLossless
                LibraryFilterFormat.HIRES -> t.isHiRes
                LibraryFilterFormat.LOSSY -> !t.isLossless
            }
            val matchesFolder = folder == null || t.folderPath == folder
            matchesQuery && matchesFmt && matchesFolder
        }.let { list ->
            when (sort) {
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

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _openNowPlayingTrigger = MutableStateFlow(false)
    val openNowPlayingTrigger: StateFlow<Boolean> = _openNowPlayingTrigger.asStateFlow()

    private var lyricLoadJob: Job? = null
    private var paletteJob: Job? = null
    private var lastActivatedTrackId: String? = null
    private var lastActivatedAtMs: Long = 0L

    init {
        playbackController.connect()
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

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFormatFilter(format: LibraryFilterFormat) {
        haptics.tickLight()
        _formatFilter.value = format
    }

    fun updateSortOption(option: LibrarySortOption) {
        haptics.tickLight()
        _sortOption.value = option
    }

    fun selectFolder(folder: String?) {
        haptics.tickLight()
        _selectedFolder.value = folder
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
                val filter = prefs.scanFilterFlow.first()
                val existingList = trackDao.getAllTracksSnapshot()
                val existingById = existingList.associateBy { it.id }
                val scanned = scanner.scanDeviceMediaStore(
                    filter = filter,
                    existingTracksById = existingById,
                    onProgress = { _scanProgress.value = it }
                )
                val analysis = withContext(Dispatchers.Default) {
                    TrackDeduplicator.analyzeImportCandidates(scanned, existingList)
                }
                val toUpsert = analysis.updatedExistingTracks + analysis.newTracks
                if (toUpsert.isNotEmpty()) {
                    trackDao.upsertAll(toUpsert)
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
                        updatedCount = analysis.updatedExistingTracks.size,
                        skippedCount = analysis.autoSkippedCount,
                        conflictCount = analysis.qualityConflicts.size
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
                val toUpsert = analysis.updatedExistingTracks + analysis.newTracks
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
                    val targetToPlay = analysis.newTracks.firstOrNull()
                        ?: existingList.firstOrNull { TrackDeduplicator.areSameSong(it, imported.first()) }
                        ?: imported.first()
                    playTrack(targetToPlay, analysis.newTracks.ifEmpty { listOf(targetToPlay) })
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
                val toUpsert = analysis.updatedExistingTracks + analysis.newTracks
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

    private fun buildImportSummaryBanner(
        prefix: String,
        newMusicCount: Int,
        newGeneralCount: Int,
        updatedCount: Int,
        skippedCount: Int,
        conflictCount: Int
    ): String {
        val parts = mutableListOf<String>()
        if (newMusicCount > 0) parts += "新增 $newMusicCount 首音乐"
        if (newGeneralCount > 0) parts += "新增 $newGeneralCount 条普通音频"
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
                    trackDao.upsert(candidate)
                    candidate
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
        val count = _pendingImportConflicts.value.size
        _pendingImportConflicts.value = emptyList()
        if (count > 0) {
            showBanner("已跳过 $count 首音质差异曲目，保留曲库原有版本")
        }
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
            showBanner("「${group.displayTitle}」已保留所选版本并移出 ${losers.size} 个重复项（本机文件未删）")
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
            showBanner("「${group.displayTitle}」已精选主版留音乐区，其余 ${others.size} 个归入普通音频区")
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
    }

    fun playNextInQueue(track: Track) {
        haptics.tickLight()
        playbackController.addTrackNextInQueue(track)
        showBanner("已将「${track.title}」加入下一首播放")
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
        // Load lyrics in priority order
        loadLyricsForTrack(track)
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
            if (playbackState.value.currentTrack?.id == track.id) {
                _currentLyric.value = parsed
            }
            haptics.harmonicChime()
            showBanner("已绑定 ${parsed.format.label} 歌词（共 ${parsed.lines.size} 行）")
        }
    }

    fun adjustLyricOffset(deltaMs: Long) {
        val track = playbackState.value.currentTrack ?: return
        val current = _currentLyric.value
        val newOffset = (current.globalOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        haptics.tickLight()
        _currentLyric.value = current.copy(globalOffsetMs = newOffset)
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
        showBanner("已将 ${tracks.size} 首曲目加入播放队列")
    }

    fun removeQueueIndex(index: Int) {
        haptics.tickLight()
        playbackController.removeFromQueue(index)
    }

    fun moveQueueIndexToNext(index: Int) {
        haptics.tickLight()
        playbackController.moveQueueItemToNext(index)
        showBanner("已调至下一首播放")
    }

    fun clearUpcomingQueue() {
        haptics.tickLight()
        playbackController.clearUpcomingQueue()
        showBanner("已清空后续待播队列")
    }

    fun saveQueueAsPlaylist(name: String) {
        val queueIds = playbackState.value.queue.map { it.id }
        if (queueIds.isEmpty()) return
        createPlaylist(name, "来自当前待播队列 (${queueIds.size} 首)", queueIds)
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
            showBanner(if (favorite) "已将 ${trackIds.size} 首设为红心挚爱" else "已取消 ${trackIds.size} 首红心")
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
            showBanner(
                if (isGeneralAudio) "已将 ${trackIds.size} 项归入「普通音频区」（不混入音乐歌单与 AI 分析）"
                else "已将 ${trackIds.size} 项标记为「音乐曲库」"
            )
        }
    }

    fun batchRemoveTracksFromLibrary(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        haptics.tickLight()
        viewModelScope.launch {
            trackIds.chunked(500).forEach { chunk ->
                playlistDao.removeTracksFromAllPlaylists(chunk)
                trackDao.deleteTracksByIds(chunk)
            }
            playbackController.removeTracksByIds(trackIds.toSet())
            showBanner("已从曲库列表移除 ${trackIds.size} 首（本机原文件保留）")
        }
    }

    fun clearRecentHistory() {
        haptics.tickLight()
        viewModelScope.launch {
            trackDao.clearAllRecentHistory()
            showBanner("已清空最近聆听记录")
        }
    }

    fun removeFromRecentHistory(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        haptics.tickLight()
        viewModelScope.launch {
            trackIds.chunked(500).forEach { chunk ->
                trackDao.clearPlayHistoryForIds(chunk)
            }
            showBanner("已从最近聆听移出 ${trackIds.size} 首")
        }
    }

    fun clearMostPlayedStats() {
        haptics.tickLight()
        viewModelScope.launch {
            trackDao.clearMostPlayedStats()
            showBanner("已重置高频循环统计")
        }
    }

    // Playlists
    fun createPlaylist(name: String, description: String = "", initialTrackIds: List<String> = emptyList()) {
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
            showBanner("已创建歌单「$trimmed」" + if (initialTrackIds.isNotEmpty()) "（含 ${initialTrackIds.distinct().size} 首）" else "")
        }
    }

    fun renamePlaylist(playlistId: Long, name: String, description: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            playlistDao.updatePlaylistInfo(playlistId, trimmed, description.trim())
            haptics.tickLight()
            showBanner("已更新歌单信息")
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.clearPlaylistTracks(playlistId)
            playlistDao.deletePlaylist(playlistId)
            haptics.tickLight()
            showBanner("已删除歌单")
        }
    }

    fun clearPlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.clearPlaylistTracks(playlistId)
            haptics.tickLight()
            showBanner("已清空歌单内全部曲目")
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
                showBanner("已加入歌单（新增 ${newRefs.size} 首）")
            } else {
                haptics.tickLight()
                showBanner("所选曲目均已在该歌单中")
            }
        }
    }

    fun removeTracksFromPlaylist(playlistId: Long, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        haptics.tickLight()
        viewModelScope.launch {
            trackIds.chunked(500).forEach { chunk ->
                playlistDao.removeTracksFromPlaylist(playlistId, chunk)
            }
            showBanner("已从歌单移出 ${trackIds.size} 首")
        }
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
    fun setShowLibraryHero(show: Boolean) = viewModelScope.launch {
        haptics.tickLight()
        prefs.setShowLibraryHero(show)
    }
    fun setEnableGeneralAudioMode(enabled: Boolean) = viewModelScope.launch {
        haptics.tickLight()
        prefs.setEnableGeneralAudioMode(enabled)
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
        showBanner("已应用 10 段声学预设：${preset.name}")
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
            showBanner("已保存自定义预设：${preset.name}")
        }
    }

    fun deleteCustomEqPreset(presetId: String) {
        haptics.tickLight()
        viewModelScope.launch {
            prefs.deleteCustomEqPreset(presetId)
            if (eqState.value.presetId == presetId) {
                updateEqState(eqState.value.copy(presetId = "custom"))
            }
            showBanner("已删除自定义预设")
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
            showBanner("已应用并保存 AI 声学预设：${savedPreset.name}")
        }
    }

    fun updateAiConfig(config: AiConfig) = viewModelScope.launch {
        prefs.updateAiConfig(config)
        haptics.tickLight()
        showBanner("AI 模型配置已保存在本地")
    }

    fun toggleAiWebSearch() = viewModelScope.launch {
        val current = prefs.aiConfigFlow.first()
        val next = !current.enableWebSearch
        prefs.updateAiConfig(current.copy(enableWebSearch = next))
        haptics.tickLight()
        showBanner(if (next) "已开启 DeepSeek 原生联网搜索（AI 按需自主检索）" else "已关闭联网搜索")
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
            showBanner(
                if (exists) "已隐藏扩展思考档位 ${level.apiValue}"
                else "已开启扩展思考档位 ${level.apiValue} (${level.label})"
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
            showBanner("已新建独立对话窗口 #$count")
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
            showBanner("已清空当前对话消息")
        }
    }

    fun deleteAiSession(sessionId: Long) {
        haptics.tickLight()
        aiSessionJobs.remove(sessionId)?.cancel()
        _aiSessionRuntimeStates.update { it - sessionId }
        viewModelScope.launch {
            aiChatDao.deleteSession(sessionId)
            val remaining = aiChatDao.getSessionsSnapshot()
            if (remaining.isEmpty()) {
                val cfg = prefs.aiConfigFlow.first()
                val freshId = aiChatDao.insertSession(
                    AiChatSessionEntity(
                        title = "声学与音乐对话",
                        modelId = cfg.deepSeekModelId,
                        reasoningEffort = cfg.reasoningEffort.apiValue
                    )
                )
                _activeAiSessionId.value = freshId
            } else if (_activeAiSessionId.value == sessionId) {
                _activeAiSessionId.value = remaining.first().id
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
                showBanner("正在用 AI 提炼对话标题...")
            }
            val res = aiChatClient.generateSessionTitle(config, firstUser, firstAssistant)
            res.fold(
                onSuccess = { title ->
                    aiChatDao.renameSession(sessionId, title)
                    if (showFeedbackToast) {
                        haptics.harmonicChime()
                        showBanner("已更新对话标题：「$title」")
                    }
                },
                onFailure = { err ->
                    if (showFeedbackToast) {
                        showBanner("AI 标题生成失败：${err.localizedMessage ?: "请检查网络或配置"}")
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
                showBanner("正在连播 ${matched.size} 首匹配曲目")
            } else {
                showBanner("未在本地音乐曲库匹配到对应曲目")
            }
        }
    }

    fun createPlaylistFromAiSuggestion(suggestion: AiSuggestedPlaylist) {
        viewModelScope.launch {
            val matched = matchTracksFromLibraryByTitles(suggestion.trackTitles)
            if (matched.isEmpty()) {
                showBanner("未能从本地音乐曲库匹配到歌单中的曲目")
                return@launch
            }
            val desc = suggestion.description.ifBlank { "由 AI 音乐策展生成（共 ${matched.size} 首）" }
            createPlaylist(
                name = suggestion.name,
                description = desc,
                initialTrackIds = matched.map { it.id }
            )
        }
    }

    override fun onCleared() {
        aiSessionJobs.values.forEach { it.cancel() }
        aiSessionJobs.clear()
        playbackController.release()
        super.onCleared()
    }
}
