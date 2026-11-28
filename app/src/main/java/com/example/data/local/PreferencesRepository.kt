package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AiCatalogAttachmentPrefs
import com.example.model.AiConfig
import com.example.model.AiProviderType
import com.example.model.ArchivedTrackRecord
import com.example.model.AudioBookmark
import com.example.model.AudioTransitionConfig
import com.example.model.AudioTransitionMode
import com.example.model.DEFAULT_AI_MAX_OUTPUT_TOKENS
import com.example.model.DeletionGuardMode
import com.example.model.EQ_BAND_COUNT
import com.example.model.EqPreset
import com.example.model.EqState
import com.example.model.HapticProfile
import com.example.model.LyricSettings
import com.example.model.LyricSource
import com.example.model.NavBarStyle
import com.example.model.PlaylistCoverConfig
import com.example.model.PlaylistCoverMode
import com.example.model.ReasoningEffortLevel
import com.example.model.ScanFilterConfig
import com.example.model.Track
import com.example.model.TrackMetadataOverride
import com.example.model.normalizeEqBands
import com.example.model.snapLyricFontScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.auralisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auralis_preferences"
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val HAPTIC_PROFILE = stringPreferencesKey("haptic_profile")
        val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
        val VISUAL_EFFECTS_ENHANCED = booleanPreferencesKey("visual_effects_enhanced")
        val SHOW_LIBRARY_HERO = booleanPreferencesKey("show_library_hero")
        val SHOW_GENERAL_AUDIO_NOTICE = booleanPreferencesKey("show_general_audio_notice")
        val ENABLE_GENERAL_AUDIO_MODE = booleanPreferencesKey("enable_general_audio_mode")
        val DELETION_GUARD_MODE = stringPreferencesKey("deletion_guard_mode")

        // Smart Scan Filter
        val MIN_DURATION_SEC = intPreferencesKey("scan_min_duration_sec")
        val MAX_DURATION_SEC = intPreferencesKey("scan_max_duration_sec")
        val MIN_SIZE_KB = intPreferencesKey("scan_min_size_kb")
        val EXCLUDE_SYSTEM_AUDIO = booleanPreferencesKey("scan_exclude_system")
        val SCAN_REQUIRE_METADATA = booleanPreferencesKey("scan_require_metadata")
        val SCAN_REQUIRE_ARTIST = booleanPreferencesKey("scan_require_artist")
        val SCAN_REQUIRE_ALBUM_OR_ART = booleanPreferencesKey("scan_require_album_or_art")
        val SCAN_CLASSIFY_GENERAL_AUDIO = booleanPreferencesKey("scan_classify_general_audio")
        val EXCLUDED_FOLDERS = stringSetPreferencesKey("scan_excluded_folders")

        // Lyrics
        val SIMULATED_WORD_BY_WORD = booleanPreferencesKey("lyric_simulated_word_by_word")
        val SHOW_TRANSLATION = booleanPreferencesKey("lyric_show_translation")
        val LYRIC_FONT_SCALE = floatPreferencesKey("lyric_font_scale")
        val LYRIC_FONT_WEIGHT = intPreferencesKey("lyric_font_weight")
        val KEEP_SCREEN_ON_LYRICS = booleanPreferencesKey("lyric_keep_screen_on")
        val LYRIC_ACTIVE_SOURCE = stringPreferencesKey("lyric_active_source")
        val LYRIC_ENABLE_BLUR = booleanPreferencesKey("lyric_enable_blur")
        val LYRIC_ENABLE_IMMERSIVE = booleanPreferencesKey("lyric_enable_immersive")
        val LYRIC_ENABLE_ELASTIC_WAVE = booleanPreferencesKey("lyric_enable_elastic_wave")

        // Smart Audio Transitions
        val TRANSITION_MODE = stringPreferencesKey("audio_transition_mode")
        val TRANSITION_DURATION_MS = intPreferencesKey("audio_transition_duration_ms")
        val TRANSITION_MANUAL_SKIP = booleanPreferencesKey("audio_transition_manual_skip")
        val TRANSITION_PLAY_PAUSE = booleanPreferencesKey("audio_transition_play_pause")

        // Equalizer (10-Band + Custom Presets)
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET_ID = stringPreferencesKey("eq_preset_id")
        val EQ_BANDS_CSV = stringPreferencesKey("eq_bands_csv")
        val EQ_BASS_BOOST = intPreferencesKey("eq_bass_boost")
        val EQ_VIRTUALIZER = intPreferencesKey("eq_virtualizer")
        val EQ_CUSTOM_PRESETS_JSON = stringPreferencesKey("eq_custom_presets_json")

        // AI Provider & Reasoning
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        val DEEPSEEK_BASE_URL = stringPreferencesKey("deepseek_base_url")
        val DEEPSEEK_MODEL_ID = stringPreferencesKey("deepseek_model_id")
        val AI_ENABLE_VISION = booleanPreferencesKey("ai_enable_vision")
        val AI_ENABLE_WEB_SEARCH = booleanPreferencesKey("ai_enable_web_search")
        val AI_MAX_OUTPUT_TOKENS = intPreferencesKey("ai_max_output_tokens")
        val AI_REASONING_EFFORT = stringPreferencesKey("ai_reasoning_effort")
        val AI_ENABLED_EFFORTS = stringSetPreferencesKey("ai_enabled_efforts")
        val AI_SHOW_THINKING = booleanPreferencesKey("ai_show_thinking")
        val AI_ENABLE_STREAMING = booleanPreferencesKey("ai_enable_streaming")
        val AI_AUTO_TITLE = booleanPreferencesKey("ai_auto_title")
        val AI_SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")

        // Remembered AI Library Catalog Attachment Selection
        val AI_ATTACH_CATALOG_ENABLED = booleanPreferencesKey("ai_attach_catalog_enabled")
        val AI_ATTACH_CATALOG_CUSTOMIZED = booleanPreferencesKey("ai_attach_catalog_customized")
        val AI_ATTACH_CATALOG_TRACK_IDS = stringSetPreferencesKey("ai_attach_catalog_track_ids")
        val AI_ATTACH_CATALOG_LYRIC_IDS = stringSetPreferencesKey("ai_attach_catalog_lyric_ids")

        // Playlist Cover Customization (Custom, Artist Auto, and Smart Collections)
        val PLAYLIST_COVER_CONFIGS_JSON = stringPreferencesKey("playlist_cover_configs_json")

        // Per-Track Timestamp Bookmarks (General Audio Mode)
        val AUDIO_BOOKMARKS_JSON = stringPreferencesKey("audio_bookmarks_json")

        // Archived Play History & Favorites (preserved when clearing audio library only)
        val TRACK_HISTORY_ARCHIVE_RAW = stringPreferencesKey("track_history_archive_raw")

        // First-launch onboarding completion state
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")

        // In-app track metadata & cover art overrides (does not modify source files)
        val TRACK_METADATA_OVERRIDES_JSON = stringPreferencesKey("track_metadata_overrides_json")
    }

    val darkThemeFlow: Flow<Boolean> = context.auralisDataStore.data.map { prefs ->
        prefs[Keys.DARK_THEME] ?: true
    }

    val hapticProfileFlow: Flow<HapticProfile> = context.auralisDataStore.data.map { prefs ->
        val raw = prefs[Keys.HAPTIC_PROFILE] ?: HapticProfile.STUDIO.name
        runCatching { HapticProfile.valueOf(raw) }.getOrDefault(HapticProfile.STUDIO)
    }

    val navBarStyleFlow: Flow<NavBarStyle> = context.auralisDataStore.data.map { prefs ->
        val raw = prefs[Keys.NAV_BAR_STYLE] ?: NavBarStyle.FLOATING_GLASS.name
        runCatching { NavBarStyle.valueOf(raw) }.getOrDefault(NavBarStyle.FLOATING_GLASS)
    }

    val visualEffectsEnhancedFlow: Flow<Boolean> = context.auralisDataStore.data.map { prefs ->
        prefs[Keys.VISUAL_EFFECTS_ENHANCED] ?: true
    }

    val showLibraryHeroFlow: Flow<Boolean> = context.auralisDataStore.data.map { prefs ->
        prefs[Keys.SHOW_LIBRARY_HERO] ?: true
    }

    val showGeneralAudioNoticeFlow: Flow<Boolean> = context.auralisDataStore.data.map { prefs ->
        prefs[Keys.SHOW_GENERAL_AUDIO_NOTICE] ?: true
    }

    val enableGeneralAudioModeFlow: Flow<Boolean> = context.auralisDataStore.data.map { prefs ->
        prefs[Keys.ENABLE_GENERAL_AUDIO_MODE] ?: true
    }

    val deletionGuardModeFlow: Flow<DeletionGuardMode> = context.auralisDataStore.data.map { prefs ->
        DeletionGuardMode.fromStored(prefs[Keys.DELETION_GUARD_MODE])
    }

    val scanFilterFlow: Flow<ScanFilterConfig> = context.auralisDataStore.data.map { prefs ->
        val default = ScanFilterConfig()
        ScanFilterConfig(
            minDurationSec = prefs[Keys.MIN_DURATION_SEC] ?: default.minDurationSec,
            maxDurationSec = prefs[Keys.MAX_DURATION_SEC] ?: default.maxDurationSec,
            minSizeKb = prefs[Keys.MIN_SIZE_KB] ?: default.minSizeKb,
            excludeSystemAudio = prefs[Keys.EXCLUDE_SYSTEM_AUDIO] ?: default.excludeSystemAudio,
            requireMetadata = prefs[Keys.SCAN_REQUIRE_METADATA] ?: default.requireMetadata,
            requireArtist = prefs[Keys.SCAN_REQUIRE_ARTIST] ?: default.requireArtist,
            requireAlbumOrArt = prefs[Keys.SCAN_REQUIRE_ALBUM_OR_ART] ?: default.requireAlbumOrArt,
            classifyUnverifiedAsGeneralAudio = prefs[Keys.SCAN_CLASSIFY_GENERAL_AUDIO] ?: default.classifyUnverifiedAsGeneralAudio,
            excludedFolders = prefs[Keys.EXCLUDED_FOLDERS] ?: default.excludedFolders
        ).normalized()
    }

    val lyricSettingsFlow: Flow<LyricSettings> = context.auralisDataStore.data.map { prefs ->
        val srcRaw = prefs[Keys.LYRIC_ACTIVE_SOURCE] ?: LyricSource.ALL.name
        val src = runCatching { LyricSource.valueOf(srcRaw) }.getOrDefault(LyricSource.ALL)
        LyricSettings(
            enableSimulatedWordByWord = prefs[Keys.SIMULATED_WORD_BY_WORD] ?: true,
            showTranslation = prefs[Keys.SHOW_TRANSLATION] ?: true,
            fontScale = snapLyricFontScale(prefs[Keys.LYRIC_FONT_SCALE] ?: 1.0f),
            fontWeight = (prefs[Keys.LYRIC_FONT_WEIGHT] ?: 700).coerceIn(300, 900),
            keepScreenOnInLyrics = prefs[Keys.KEEP_SCREEN_ON_LYRICS] ?: true,
            activeOnlineSource = src,
            enableLyricBlur = prefs[Keys.LYRIC_ENABLE_BLUR] ?: true,
            enableImmersiveLyrics = prefs[Keys.LYRIC_ENABLE_IMMERSIVE] ?: false,
            enableElasticLyricWave = prefs[Keys.LYRIC_ENABLE_ELASTIC_WAVE] ?: true
        )
    }

    val audioTransitionFlow: Flow<AudioTransitionConfig> = context.auralisDataStore.data.map { prefs ->
        AudioTransitionConfig(
            mode = AudioTransitionMode.fromStored(prefs[Keys.TRANSITION_MODE]),
            durationMs = (prefs[Keys.TRANSITION_DURATION_MS] ?: 5000).coerceIn(1500, 10_000),
            fadeOnManualSkip = prefs[Keys.TRANSITION_MANUAL_SKIP] ?: false,
            fadeOnPlayPause = prefs[Keys.TRANSITION_PLAY_PAUSE] ?: true
        )
    }

    val eqStateFlow: Flow<EqState> = context.auralisDataStore.data.map { prefs ->
        val csv = prefs[Keys.EQ_BANDS_CSV].orEmpty()
        val rawBands = csv.split(",").mapNotNull { it.trim().toFloatOrNull() }
        val parsedBands = normalizeEqBands(rawBands, EQ_BAND_COUNT)
        EqState(
            enabled = prefs[Keys.EQ_ENABLED] ?: false,
            presetId = prefs[Keys.EQ_PRESET_ID] ?: "flat",
            bandLevelsDb = parsedBands,
            bassBoostPercent = prefs[Keys.EQ_BASS_BOOST] ?: 0,
            virtualizerPercent = prefs[Keys.EQ_VIRTUALIZER] ?: 0
        )
    }

    val customEqPresetsFlow: Flow<List<EqPreset>> = context.auralisDataStore.data.map { prefs ->
        parseCustomPresetsJson(prefs[Keys.EQ_CUSTOM_PRESETS_JSON])
    }

    val aiConfigFlow: Flow<AiConfig> = context.auralisDataStore.data.map { prefs ->
        val providerRaw = prefs[Keys.AI_PROVIDER] ?: AiProviderType.DEEPSEEK.name
        val provider = runCatching { AiProviderType.valueOf(providerRaw) }
            .getOrDefault(AiProviderType.DEEPSEEK)
        val enabledRaw = prefs[Keys.AI_ENABLED_EFFORTS]
        val enabledSet = if (enabledRaw.isNullOrEmpty()) {
            ReasoningEffortLevel.defaultEnabledSet
        } else {
            enabledRaw.mapNotNull { runCatching { ReasoningEffortLevel.valueOf(it) }.getOrNull() }
                .toSet()
                .ifEmpty { ReasoningEffortLevel.defaultEnabledSet }
        }
        val effort = ReasoningEffortLevel.fromApiValue(prefs[Keys.AI_REASONING_EFFORT])
            .let { if (it in enabledSet) it else ReasoningEffortLevel.OFF }

        val storedModel = prefs[Keys.DEEPSEEK_MODEL_ID]?.trim().orEmpty()
        val sanitizedModel = when {
            storedModel.isBlank() ||
                storedModel.equals("deepseek-chat", ignoreCase = true) ||
                storedModel.equals("deepseek-reasoner", ignoreCase = true) -> "deepseek-flash"
            else -> storedModel
        }

        AiConfig(
            activeProvider = provider,
            deepSeekApiKey = prefs[Keys.DEEPSEEK_API_KEY] ?: "",
            deepSeekBaseUrl = prefs[Keys.DEEPSEEK_BASE_URL] ?: "https://api.deepseek.com",
            deepSeekModelId = sanitizedModel,
            enableVisionForCovers = prefs[Keys.AI_ENABLE_VISION] ?: true,
            enableWebSearch = prefs[Keys.AI_ENABLE_WEB_SEARCH] ?: true,
            maxOutputTokens = prefs[Keys.AI_MAX_OUTPUT_TOKENS]
                ?.let { if (it == 524_288) DEFAULT_AI_MAX_OUTPUT_TOKENS else it }
                ?.coerceAtLeast(256)
                ?: DEFAULT_AI_MAX_OUTPUT_TOKENS,
            reasoningEffort = effort,
            enabledReasoningLevels = enabledSet,
            showThinkingProcess = prefs[Keys.AI_SHOW_THINKING] ?: true,
            enableStreaming = prefs[Keys.AI_ENABLE_STREAMING] ?: true,
            autoGenerateSessionTitle = prefs[Keys.AI_AUTO_TITLE] ?: false,
            customSystemPrompt = prefs[Keys.AI_SYSTEM_PROMPT] ?: ""
        )
    }

    val aiCatalogAttachmentPrefsFlow: Flow<AiCatalogAttachmentPrefs> = context.auralisDataStore.data.map { prefs ->
        AiCatalogAttachmentPrefs(
            enabled = prefs[Keys.AI_ATTACH_CATALOG_ENABLED] ?: false,
            hasCustomSelection = prefs[Keys.AI_ATTACH_CATALOG_CUSTOMIZED] ?: false,
            selectedTrackIds = prefs[Keys.AI_ATTACH_CATALOG_TRACK_IDS] ?: emptySet(),
            lyricTrackIds = prefs[Keys.AI_ATTACH_CATALOG_LYRIC_IDS] ?: emptySet()
        )
    }

    val playlistCoverConfigsFlow: Flow<Map<String, PlaylistCoverConfig>> = context.auralisDataStore.data.map { prefs ->
        parsePlaylistCoverConfigsJson(prefs[Keys.PLAYLIST_COVER_CONFIGS_JSON])
    }

    val audioBookmarksFlow: Flow<Map<String, List<AudioBookmark>>> = context.auralisDataStore.data.map { prefs ->
        parseAudioBookmarksJson(prefs[Keys.AUDIO_BOOKMARKS_JSON])
    }

    val hasCompletedOnboardingFlow: Flow<Boolean> = context.auralisDataStore.data.map { prefs ->
        prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false
    }

    val trackMetadataOverridesFlow: Flow<Map<String, TrackMetadataOverride>> = context.auralisDataStore.data.map { prefs ->
        parseTrackMetadataOverridesJson(prefs[Keys.TRACK_METADATA_OVERRIDES_JSON])
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.auralisDataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    suspend fun getTrackMetadataOverridesSnapshot(): Map<String, TrackMetadataOverride> {
        val raw = context.auralisDataStore.data.first()[Keys.TRACK_METADATA_OVERRIDES_JSON]
        return parseTrackMetadataOverridesJson(raw)
    }

    suspend fun saveTrackMetadataOverride(override: TrackMetadataOverride) {
        context.auralisDataStore.edit { prefs ->
            val current = parseTrackMetadataOverridesJson(prefs[Keys.TRACK_METADATA_OVERRIDES_JSON]).toMutableMap()
            current[override.trackId] = override
            prefs[Keys.TRACK_METADATA_OVERRIDES_JSON] = serializeTrackMetadataOverridesJson(current)
        }
    }

    suspend fun removeTrackMetadataOverride(trackId: String): TrackMetadataOverride? {
        var removed: TrackMetadataOverride? = null
        context.auralisDataStore.edit { prefs ->
            val current = parseTrackMetadataOverridesJson(prefs[Keys.TRACK_METADATA_OVERRIDES_JSON]).toMutableMap()
            removed = current.remove(trackId)
            prefs[Keys.TRACK_METADATA_OVERRIDES_JSON] = serializeTrackMetadataOverridesJson(current)
        }
        return removed
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.auralisDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setHapticProfile(profile: HapticProfile) {
        context.auralisDataStore.edit { it[Keys.HAPTIC_PROFILE] = profile.name }
    }

    suspend fun setNavBarStyle(style: NavBarStyle) {
        context.auralisDataStore.edit { it[Keys.NAV_BAR_STYLE] = style.name }
    }

    suspend fun setVisualEffectsEnhanced(enabled: Boolean) {
        context.auralisDataStore.edit { it[Keys.VISUAL_EFFECTS_ENHANCED] = enabled }
    }

    suspend fun setShowLibraryHero(show: Boolean) {
        context.auralisDataStore.edit { it[Keys.SHOW_LIBRARY_HERO] = show }
    }

    suspend fun setShowGeneralAudioNotice(show: Boolean) {
        context.auralisDataStore.edit { it[Keys.SHOW_GENERAL_AUDIO_NOTICE] = show }
    }

    suspend fun setEnableGeneralAudioMode(enabled: Boolean) {
        context.auralisDataStore.edit { it[Keys.ENABLE_GENERAL_AUDIO_MODE] = enabled }
    }

    suspend fun setDeletionGuardMode(mode: DeletionGuardMode) {
        context.auralisDataStore.edit { it[Keys.DELETION_GUARD_MODE] = mode.name }
    }

    suspend fun updateScanFilter(config: ScanFilterConfig) {
        val norm = config.normalized()
        context.auralisDataStore.edit { prefs ->
            prefs[Keys.MIN_DURATION_SEC] = norm.minDurationSec
            prefs[Keys.MAX_DURATION_SEC] = norm.maxDurationSec
            prefs[Keys.MIN_SIZE_KB] = norm.minSizeKb
            prefs[Keys.EXCLUDE_SYSTEM_AUDIO] = norm.excludeSystemAudio
            prefs[Keys.SCAN_REQUIRE_METADATA] = norm.requireMetadata
            prefs[Keys.SCAN_REQUIRE_ARTIST] = norm.requireArtist
            prefs[Keys.SCAN_REQUIRE_ALBUM_OR_ART] = norm.requireAlbumOrArt
            prefs[Keys.SCAN_CLASSIFY_GENERAL_AUDIO] = norm.classifyUnverifiedAsGeneralAudio
            prefs[Keys.EXCLUDED_FOLDERS] = norm.excludedFolders
        }
    }

    suspend fun updateLyricSettings(settings: LyricSettings) {
        context.auralisDataStore.edit { prefs ->
            prefs[Keys.SIMULATED_WORD_BY_WORD] = settings.enableSimulatedWordByWord
            prefs[Keys.SHOW_TRANSLATION] = settings.showTranslation
            prefs[Keys.LYRIC_FONT_SCALE] = snapLyricFontScale(settings.fontScale)
            prefs[Keys.LYRIC_FONT_WEIGHT] = settings.fontWeight.coerceIn(300, 900)
            prefs[Keys.KEEP_SCREEN_ON_LYRICS] = settings.keepScreenOnInLyrics
            prefs[Keys.LYRIC_ACTIVE_SOURCE] = settings.activeOnlineSource.name
            prefs[Keys.LYRIC_ENABLE_BLUR] = settings.enableLyricBlur
            prefs[Keys.LYRIC_ENABLE_IMMERSIVE] = settings.enableImmersiveLyrics
            prefs[Keys.LYRIC_ENABLE_ELASTIC_WAVE] = settings.enableElasticLyricWave
        }
    }

    suspend fun updateAudioTransition(config: AudioTransitionConfig) {
        context.auralisDataStore.edit { prefs ->
            prefs[Keys.TRANSITION_MODE] = config.mode.name
            prefs[Keys.TRANSITION_DURATION_MS] = config.durationMs.coerceIn(1500, 10_000)
            prefs[Keys.TRANSITION_MANUAL_SKIP] = config.fadeOnManualSkip
            prefs[Keys.TRANSITION_PLAY_PAUSE] = config.fadeOnPlayPause
        }
    }

    suspend fun updateEqState(state: EqState) {
        val normalized = normalizeEqBands(state.bandLevelsDb, EQ_BAND_COUNT)
        context.auralisDataStore.edit { prefs ->
            prefs[Keys.EQ_ENABLED] = state.enabled
            prefs[Keys.EQ_PRESET_ID] = state.presetId
            prefs[Keys.EQ_BANDS_CSV] = normalized.joinToString(",")
            prefs[Keys.EQ_BASS_BOOST] = state.bassBoostPercent
            prefs[Keys.EQ_VIRTUALIZER] = state.virtualizerPercent
        }
    }

    suspend fun saveCustomEqPreset(preset: EqPreset) {
        val cleanPreset = preset.copy(
            id = preset.id.ifBlank { "custom_${System.currentTimeMillis()}" },
            name = preset.name.trim().ifBlank { "自定义声场" },
            bandLevelsDb = normalizeEqBands(preset.bandLevelsDb, EQ_BAND_COUNT),
            bassBoostPercent = preset.bassBoostPercent.coerceIn(0, 100),
            virtualizerPercent = preset.virtualizerPercent.coerceIn(0, 100),
            isCustom = true
        )
        context.auralisDataStore.edit { prefs ->
            val current = parseCustomPresetsJson(prefs[Keys.EQ_CUSTOM_PRESETS_JSON]).toMutableList()
            val existingIdx = current.indexOfFirst { it.id == cleanPreset.id || it.name == cleanPreset.name }
            if (existingIdx >= 0) {
                current[existingIdx] = cleanPreset
            } else {
                current.add(0, cleanPreset)
            }
            prefs[Keys.EQ_CUSTOM_PRESETS_JSON] = serializeCustomPresetsJson(current)
        }
    }

    suspend fun deleteCustomEqPreset(presetId: String) {
        context.auralisDataStore.edit { prefs ->
            val current = parseCustomPresetsJson(prefs[Keys.EQ_CUSTOM_PRESETS_JSON])
                .filterNot { it.id == presetId }
            prefs[Keys.EQ_CUSTOM_PRESETS_JSON] = serializeCustomPresetsJson(current)
        }
    }

    suspend fun updateAiConfig(config: AiConfig) {
        context.auralisDataStore.edit { prefs ->
            prefs[Keys.AI_PROVIDER] = config.activeProvider.name
            prefs[Keys.DEEPSEEK_API_KEY] = config.deepSeekApiKey.trim()
            prefs[Keys.DEEPSEEK_BASE_URL] = config.deepSeekBaseUrl.trim().ifBlank { "https://api.deepseek.com" }
            prefs[Keys.DEEPSEEK_MODEL_ID] = config.deepSeekModelId.trim().ifBlank { "deepseek-flash" }
            prefs[Keys.AI_ENABLE_VISION] = config.enableVisionForCovers
            prefs[Keys.AI_ENABLE_WEB_SEARCH] = config.enableWebSearch
            prefs[Keys.AI_MAX_OUTPUT_TOKENS] = config.maxOutputTokens.coerceAtLeast(256)
            prefs[Keys.AI_REASONING_EFFORT] = config.reasoningEffort.apiValue
            prefs[Keys.AI_ENABLED_EFFORTS] = config.enabledReasoningLevels.map { it.name }.toSet()
            prefs[Keys.AI_SHOW_THINKING] = config.showThinkingProcess
            prefs[Keys.AI_ENABLE_STREAMING] = config.enableStreaming
            prefs[Keys.AI_AUTO_TITLE] = config.autoGenerateSessionTitle
            prefs[Keys.AI_SYSTEM_PROMPT] = config.customSystemPrompt
        }
    }

    suspend fun updateAiCatalogAttachmentPrefs(attachment: AiCatalogAttachmentPrefs) {
        context.auralisDataStore.edit { prefs ->
            prefs[Keys.AI_ATTACH_CATALOG_ENABLED] = attachment.enabled
            prefs[Keys.AI_ATTACH_CATALOG_CUSTOMIZED] = attachment.hasCustomSelection
            prefs[Keys.AI_ATTACH_CATALOG_TRACK_IDS] = attachment.selectedTrackIds
            prefs[Keys.AI_ATTACH_CATALOG_LYRIC_IDS] = attachment.lyricTrackIds
        }
    }

    suspend fun updatePlaylistCoverConfig(collectionKey: String, config: PlaylistCoverConfig?) {
        val normalizedKey = collectionKey.trim()
        if (normalizedKey.isEmpty()) return
        context.auralisDataStore.edit { prefs ->
            val current = parsePlaylistCoverConfigsJson(prefs[Keys.PLAYLIST_COVER_CONFIGS_JSON]).toMutableMap()
            if (config == null || config.isDefault) {
                current.remove(normalizedKey)
            } else {
                current[normalizedKey] = config.copy(
                    trackIds = config.trackIds.filter { it.isNotBlank() }.distinct().take(4)
                )
            }
            prefs[Keys.PLAYLIST_COVER_CONFIGS_JSON] = serializePlaylistCoverConfigsJson(current)
        }
    }

    suspend fun addAudioBookmark(trackId: String, bookmark: AudioBookmark) {
        val cleanTrackId = trackId.trim()
        if (cleanTrackId.isEmpty()) return
        context.auralisDataStore.edit { prefs ->
            val map = parseAudioBookmarksJson(prefs[Keys.AUDIO_BOOKMARKS_JSON]).toMutableMap()
            val list = map[cleanTrackId].orEmpty().toMutableList()
            list.add(bookmark)
            map[cleanTrackId] = list.sortedBy { it.positionMs }.take(40)
            prefs[Keys.AUDIO_BOOKMARKS_JSON] = serializeAudioBookmarksJson(map)
        }
    }

    suspend fun removeAudioBookmark(trackId: String, bookmarkId: String) {
        val cleanTrackId = trackId.trim()
        if (cleanTrackId.isEmpty()) return
        context.auralisDataStore.edit { prefs ->
            val map = parseAudioBookmarksJson(prefs[Keys.AUDIO_BOOKMARKS_JSON]).toMutableMap()
            val updated = map[cleanTrackId].orEmpty().filterNot { it.id == bookmarkId }
            if (updated.isEmpty()) {
                map.remove(cleanTrackId)
            } else {
                map[cleanTrackId] = updated
            }
            prefs[Keys.AUDIO_BOOKMARKS_JSON] = serializeAudioBookmarksJson(map)
        }
    }

    suspend fun exportSettingsSnapshot(): SettingsBackupSnapshot {
        return SettingsBackupSnapshot(
            darkTheme = darkThemeFlow.first(),
            hapticProfile = hapticProfileFlow.first(),
            navBarStyle = navBarStyleFlow.first(),
            visualEffectsEnhanced = visualEffectsEnhancedFlow.first(),
            showLibraryHero = showLibraryHeroFlow.first(),
            showGeneralAudioNotice = showGeneralAudioNoticeFlow.first(),
            enableGeneralAudioMode = enableGeneralAudioModeFlow.first(),
            deletionGuardMode = deletionGuardModeFlow.first(),
            scanFilter = scanFilterFlow.first(),
            lyricSettings = lyricSettingsFlow.first(),
            audioTransition = audioTransitionFlow.first(),
            eqState = eqStateFlow.first(),
            customEqPresets = customEqPresetsFlow.first(),
            aiConfig = aiConfigFlow.first(),
            aiCatalogAttachmentPrefs = aiCatalogAttachmentPrefsFlow.first(),
            playlistCoverConfigs = playlistCoverConfigsFlow.first(),
            audioBookmarks = audioBookmarksFlow.first()
        )
    }

    suspend fun importSettingsSnapshot(
        snapshot: SettingsBackupSnapshot,
        restoreApiKey: Boolean
    ) {
        val normalizedEqBands = normalizeEqBands(snapshot.eqState.bandLevelsDb, EQ_BAND_COUNT)
        val cleanCustomPresets = snapshot.customEqPresets.map { preset ->
            preset.copy(
                bandLevelsDb = normalizeEqBands(preset.bandLevelsDb, EQ_BAND_COUNT),
                bassBoostPercent = preset.bassBoostPercent.coerceIn(0, 100),
                virtualizerPercent = preset.virtualizerPercent.coerceIn(0, 100),
                isCustom = true
            )
        }
        context.auralisDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = snapshot.darkTheme
            prefs[Keys.HAPTIC_PROFILE] = snapshot.hapticProfile.name
            prefs[Keys.NAV_BAR_STYLE] = snapshot.navBarStyle.name
            prefs[Keys.VISUAL_EFFECTS_ENHANCED] = snapshot.visualEffectsEnhanced
            prefs[Keys.SHOW_LIBRARY_HERO] = snapshot.showLibraryHero
            prefs[Keys.SHOW_GENERAL_AUDIO_NOTICE] = snapshot.showGeneralAudioNotice
            prefs[Keys.ENABLE_GENERAL_AUDIO_MODE] = snapshot.enableGeneralAudioMode
            prefs[Keys.DELETION_GUARD_MODE] = snapshot.deletionGuardMode.name

            // Scan Filter
            val normScan = snapshot.scanFilter.normalized()
            prefs[Keys.MIN_DURATION_SEC] = normScan.minDurationSec
            prefs[Keys.MAX_DURATION_SEC] = normScan.maxDurationSec
            prefs[Keys.MIN_SIZE_KB] = normScan.minSizeKb
            prefs[Keys.EXCLUDE_SYSTEM_AUDIO] = normScan.excludeSystemAudio
            prefs[Keys.SCAN_REQUIRE_METADATA] = normScan.requireMetadata
            prefs[Keys.SCAN_REQUIRE_ARTIST] = normScan.requireArtist
            prefs[Keys.SCAN_REQUIRE_ALBUM_OR_ART] = normScan.requireAlbumOrArt
            prefs[Keys.SCAN_CLASSIFY_GENERAL_AUDIO] = normScan.classifyUnverifiedAsGeneralAudio
            prefs[Keys.EXCLUDED_FOLDERS] = normScan.excludedFolders

            // Lyrics
            prefs[Keys.SIMULATED_WORD_BY_WORD] = snapshot.lyricSettings.enableSimulatedWordByWord
            prefs[Keys.SHOW_TRANSLATION] = snapshot.lyricSettings.showTranslation
            prefs[Keys.LYRIC_FONT_SCALE] = snapLyricFontScale(snapshot.lyricSettings.fontScale)
            prefs[Keys.LYRIC_FONT_WEIGHT] = snapshot.lyricSettings.fontWeight.coerceIn(300, 900)
            prefs[Keys.KEEP_SCREEN_ON_LYRICS] = snapshot.lyricSettings.keepScreenOnInLyrics
            prefs[Keys.LYRIC_ACTIVE_SOURCE] = snapshot.lyricSettings.activeOnlineSource.name
            prefs[Keys.LYRIC_ENABLE_BLUR] = snapshot.lyricSettings.enableLyricBlur
            prefs[Keys.LYRIC_ENABLE_IMMERSIVE] = snapshot.lyricSettings.enableImmersiveLyrics
            prefs[Keys.LYRIC_ENABLE_ELASTIC_WAVE] = snapshot.lyricSettings.enableElasticLyricWave

            // Smart Audio Transition
            prefs[Keys.TRANSITION_MODE] = snapshot.audioTransition.mode.name
            prefs[Keys.TRANSITION_DURATION_MS] = snapshot.audioTransition.durationMs.coerceIn(1500, 10_000)
            prefs[Keys.TRANSITION_MANUAL_SKIP] = snapshot.audioTransition.fadeOnManualSkip
            prefs[Keys.TRANSITION_PLAY_PAUSE] = snapshot.audioTransition.fadeOnPlayPause

            // Equalizer & Custom Presets
            prefs[Keys.EQ_ENABLED] = snapshot.eqState.enabled
            prefs[Keys.EQ_PRESET_ID] = snapshot.eqState.presetId
            prefs[Keys.EQ_BANDS_CSV] = normalizedEqBands.joinToString(",")
            prefs[Keys.EQ_BASS_BOOST] = snapshot.eqState.bassBoostPercent.coerceIn(0, 100)
            prefs[Keys.EQ_VIRTUALIZER] = snapshot.eqState.virtualizerPercent.coerceIn(0, 100)
            prefs[Keys.EQ_CUSTOM_PRESETS_JSON] = serializeCustomPresetsJson(cleanCustomPresets)

            // AI Config (preserve local API key unless explicitly restored with a non-blank key)
            prefs[Keys.AI_PROVIDER] = snapshot.aiConfig.activeProvider.name
            if (restoreApiKey && snapshot.aiConfig.deepSeekApiKey.isNotBlank()) {
                prefs[Keys.DEEPSEEK_API_KEY] = snapshot.aiConfig.deepSeekApiKey.trim()
            }
            prefs[Keys.DEEPSEEK_BASE_URL] = snapshot.aiConfig.deepSeekBaseUrl.trim().ifBlank { "https://api.deepseek.com" }
            prefs[Keys.DEEPSEEK_MODEL_ID] = snapshot.aiConfig.deepSeekModelId.trim().ifBlank { "deepseek-flash" }
            prefs[Keys.AI_ENABLE_VISION] = snapshot.aiConfig.enableVisionForCovers
            prefs[Keys.AI_ENABLE_WEB_SEARCH] = snapshot.aiConfig.enableWebSearch
            prefs[Keys.AI_MAX_OUTPUT_TOKENS] = snapshot.aiConfig.maxOutputTokens.coerceAtLeast(256)
            prefs[Keys.AI_REASONING_EFFORT] = snapshot.aiConfig.reasoningEffort.apiValue
            prefs[Keys.AI_ENABLED_EFFORTS] = snapshot.aiConfig.enabledReasoningLevels.map { it.name }.toSet()
            prefs[Keys.AI_SHOW_THINKING] = snapshot.aiConfig.showThinkingProcess
            prefs[Keys.AI_ENABLE_STREAMING] = snapshot.aiConfig.enableStreaming
            prefs[Keys.AI_AUTO_TITLE] = snapshot.aiConfig.autoGenerateSessionTitle
            prefs[Keys.AI_SYSTEM_PROMPT] = snapshot.aiConfig.customSystemPrompt

            // AI Catalog Prefs
            prefs[Keys.AI_ATTACH_CATALOG_ENABLED] = snapshot.aiCatalogAttachmentPrefs.enabled
            prefs[Keys.AI_ATTACH_CATALOG_CUSTOMIZED] = snapshot.aiCatalogAttachmentPrefs.hasCustomSelection
            prefs[Keys.AI_ATTACH_CATALOG_TRACK_IDS] = snapshot.aiCatalogAttachmentPrefs.selectedTrackIds
            prefs[Keys.AI_ATTACH_CATALOG_LYRIC_IDS] = snapshot.aiCatalogAttachmentPrefs.lyricTrackIds

            // Playlist Cover Configs & Bookmarks
            prefs[Keys.PLAYLIST_COVER_CONFIGS_JSON] = serializePlaylistCoverConfigsJson(snapshot.playlistCoverConfigs)
            prefs[Keys.AUDIO_BOOKMARKS_JSON] = serializeAudioBookmarksJson(snapshot.audioBookmarks)
        }
    }

    /**
     * Archives play counts, last played timestamps, and favorite states before clearing
     * library audio, so re-importing the same tracks automatically restores their records.
     */
    suspend fun archiveTrackRecords(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        context.auralisDataStore.edit { prefs ->
            val existing = parseArchivedTrackRecords(prefs[Keys.TRACK_HISTORY_ARCHIVE_RAW])
            val merged = mergeArchivedTrackRecords(existing, tracks)
            prefs[Keys.TRACK_HISTORY_ARCHIVE_RAW] = serializeArchivedTrackRecords(merged)
        }
    }

    suspend fun getArchivedTrackRecords(): List<ArchivedTrackRecord> {
        val raw = context.auralisDataStore.data.first()[Keys.TRACK_HISTORY_ARCHIVE_RAW]
        return parseArchivedTrackRecords(raw)
    }

    suspend fun clearAllUserRecordPrefs() {
        context.auralisDataStore.edit { prefs ->
            prefs.remove(Keys.EQ_CUSTOM_PRESETS_JSON)
            prefs.remove(Keys.PLAYLIST_COVER_CONFIGS_JSON)
            prefs.remove(Keys.AUDIO_BOOKMARKS_JSON)
            prefs.remove(Keys.TRACK_HISTORY_ARCHIVE_RAW)
            prefs.remove(Keys.TRACK_METADATA_OVERRIDES_JSON)
            prefs.remove(Keys.AI_ATTACH_CATALOG_ENABLED)
            prefs.remove(Keys.AI_ATTACH_CATALOG_CUSTOMIZED)
            prefs.remove(Keys.AI_ATTACH_CATALOG_TRACK_IDS)
            prefs.remove(Keys.AI_ATTACH_CATALOG_LYRIC_IDS)
        }
    }

    suspend fun resetAllSettingPrefsOnly() {
        context.auralisDataStore.edit { prefs ->
            // Preserve user-created records/archives while resetting all preference settings
            val keptCustomEq = prefs[Keys.EQ_CUSTOM_PRESETS_JSON]
            val keptCovers = prefs[Keys.PLAYLIST_COVER_CONFIGS_JSON]
            val keptBookmarks = prefs[Keys.AUDIO_BOOKMARKS_JSON]
            val keptArchive = prefs[Keys.TRACK_HISTORY_ARCHIVE_RAW]
            val keptOverrides = prefs[Keys.TRACK_METADATA_OVERRIDES_JSON]
            val keptOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING]
            prefs.clear()
            if (keptCustomEq != null) prefs[Keys.EQ_CUSTOM_PRESETS_JSON] = keptCustomEq
            if (keptCovers != null) prefs[Keys.PLAYLIST_COVER_CONFIGS_JSON] = keptCovers
            if (keptBookmarks != null) prefs[Keys.AUDIO_BOOKMARKS_JSON] = keptBookmarks
            if (keptArchive != null) prefs[Keys.TRACK_HISTORY_ARCHIVE_RAW] = keptArchive
            if (keptOverrides != null) prefs[Keys.TRACK_METADATA_OVERRIDES_JSON] = keptOverrides
            if (keptOnboarding != null) prefs[Keys.HAS_COMPLETED_ONBOARDING] = keptOnboarding
        }
    }

    suspend fun clearAllPreferences() {
        context.auralisDataStore.edit { it.clear() }
    }

    companion object {
        fun parseCustomPresetsJson(raw: String?): List<EqPreset> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                val result = mutableListOf<EqPreset>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val bandsArr = obj.optJSONArray("bands") ?: continue
                    val rawBands = (0 until bandsArr.length()).map { idx ->
                        bandsArr.optDouble(idx, 0.0).toFloat()
                    }
                    result.add(
                        EqPreset(
                            id = obj.optString("id", "custom_$i"),
                            name = obj.optString("name", "自定义预设"),
                            bandLevelsDb = normalizeEqBands(rawBands, EQ_BAND_COUNT),
                            bassBoostPercent = obj.optInt("bass", 0).coerceIn(0, 100),
                            virtualizerPercent = obj.optInt("virtualizer", 0).coerceIn(0, 100),
                            isCustom = true
                        )
                    )
                }
                result
            }.getOrDefault(emptyList())
        }

        fun serializeCustomPresetsJson(presets: List<EqPreset>): String {
            val arr = JSONArray()
            presets.forEach { preset ->
                val bandsArr = JSONArray()
                normalizeEqBands(preset.bandLevelsDb, EQ_BAND_COUNT).forEach { bandsArr.put(it.toDouble()) }
                arr.put(
                    JSONObject()
                        .put("id", preset.id)
                        .put("name", preset.name)
                        .put("bands", bandsArr)
                        .put("bass", preset.bassBoostPercent)
                        .put("virtualizer", preset.virtualizerPercent)
                )
            }
            return arr.toString()
        }

        fun parsePlaylistCoverConfigsJson(raw: String?): Map<String, PlaylistCoverConfig> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val root = JSONObject(raw)
                val result = linkedMapOf<String, PlaylistCoverConfig>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = root.optJSONObject(key) ?: continue
                    val mode = PlaylistCoverMode.fromStored(obj.optString("mode", ""))
                    val idsArr = obj.optJSONArray("trackIds")
                    val ids = if (idsArr != null) {
                        (0 until idsArr.length())
                            .mapNotNull { idx -> idsArr.optString(idx, "").takeIf { it.isNotBlank() } }
                            .distinct()
                            .take(4)
                    } else {
                        emptyList()
                    }
                    val seed = obj.optLong("randomSeed", 0L)
                    val cfg = PlaylistCoverConfig(mode = mode, trackIds = ids, randomSeed = seed)
                    if (!cfg.isDefault) {
                        result[key] = cfg
                    }
                }
                result
            }.getOrDefault(emptyMap())
        }

        fun serializePlaylistCoverConfigsJson(configs: Map<String, PlaylistCoverConfig>): String {
            val root = JSONObject()
            configs.forEach { (key, config) ->
                if (key.isNotBlank() && !config.isDefault) {
                    val idsArr = JSONArray()
                    config.trackIds.filter { it.isNotBlank() }.distinct().take(4).forEach { idsArr.put(it) }
                    root.put(
                        key,
                        JSONObject()
                            .put("mode", config.mode.name)
                            .put("trackIds", idsArr)
                            .put("randomSeed", config.randomSeed)
                    )
                }
            }
            return root.toString()
        }

        private fun escapeBookmarkField(raw: String): String {
            return raw
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "")
        }

        private fun unescapeBookmarkField(raw: String): String {
            val sb = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (ch == '\\' && i + 1 < raw.length) {
                    when (raw[i + 1]) {
                        't' -> sb.append('\t')
                        'n' -> sb.append('\n')
                        '\\' -> sb.append('\\')
                        else -> sb.append(raw[i + 1])
                    }
                    i += 2
                } else {
                    sb.append(ch)
                    i += 1
                }
            }
            return sb.toString()
        }

        fun parseAudioBookmarksJson(raw: String?): Map<String, List<AudioBookmark>> {
            if (raw.isNullOrBlank()) return emptyMap()
            val trimmed = raw.trim()
            if (trimmed.startsWith("BM1\n") || trimmed == "BM1") {
                val grouped = linkedMapOf<String, MutableList<AudioBookmark>>()
                trimmed.lineSequence().drop(1).forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split('\t')
                    if (parts.size < 5) return@forEach
                    val trackId = unescapeBookmarkField(parts[0])
                    val id = unescapeBookmarkField(parts[1])
                    val pos = parts[2].toLongOrNull() ?: return@forEach
                    val created = parts[3].toLongOrNull() ?: 0L
                    val label = unescapeBookmarkField(parts[4])
                    if (trackId.isNotBlank() && pos >= 0L) {
                        grouped.getOrPut(trackId) { mutableListOf() }.add(
                            AudioBookmark(
                                id = id.ifBlank { "bm_${trackId}_$pos" },
                                positionMs = pos,
                                label = label,
                                createdAtMs = created
                            )
                        )
                    }
                }
                return grouped.mapValues { (_, list) -> list.sortedBy { it.positionMs } }
            }
            return runCatching {
                val root = JSONObject(trimmed)
                val result = linkedMapOf<String, List<AudioBookmark>>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val trackId = keys.next()
                    val arr = root.optJSONArray(trackId) ?: continue
                    val list = mutableListOf<AudioBookmark>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val pos = obj.optLong("pos", -1L)
                        if (pos < 0L) continue
                        list.add(
                            AudioBookmark(
                                id = obj.optString("id", "bm_${trackId}_$i"),
                                positionMs = pos,
                                label = obj.optString("label", "标记 #${i + 1}"),
                                createdAtMs = obj.optLong("ts", 0L)
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        result[trackId] = list.sortedBy { it.positionMs }
                    }
                }
                result
            }.getOrDefault(emptyMap())
        }

        fun serializeAudioBookmarksJson(map: Map<String, List<AudioBookmark>>): String {
            val sb = StringBuilder("BM1")
            map.forEach { (trackId, bookmarks) ->
                if (trackId.isNotBlank() && bookmarks.isNotEmpty()) {
                    val escTrack = escapeBookmarkField(trackId)
                    bookmarks.sortedBy { it.positionMs }.forEach { bm ->
                        sb.append('\n')
                            .append(escTrack).append('\t')
                            .append(escapeBookmarkField(bm.id)).append('\t')
                            .append(bm.positionMs).append('\t')
                            .append(bm.createdAtMs).append('\t')
                            .append(escapeBookmarkField(bm.label))
                    }
                }
            }
            return sb.toString()
        }

        fun buildTrackHistoryFingerprint(title: String, artist: String, fileName: String): String {
            val cleanTitle = title.trim().lowercase().replace(Regex("\\s+"), " ")
            val cleanArtist = artist.trim().lowercase().replace(Regex("\\s+"), " ")
            val cleanFile = fileName.trim().lowercase()
            return "$cleanTitle|$cleanArtist|$cleanFile"
        }

        fun mergeArchivedTrackRecords(
            existing: List<ArchivedTrackRecord>,
            tracks: List<Track>
        ): List<ArchivedTrackRecord> {
            val byKey = linkedMapOf<String, ArchivedTrackRecord>()
            existing.forEach { rec ->
                val key = rec.trackId.ifBlank { rec.fingerprint }
                if (key.isNotBlank()) byKey[key] = rec
            }
            tracks.forEach { t ->
                if (t.playCount > 0 || t.lastPlayedAt > 0L || t.isFavorite) {
                    val fp = buildTrackHistoryFingerprint(t.title, t.artist, t.fileName)
                    val prev = byKey[t.id] ?: byKey.values.firstOrNull { it.fingerprint == fp }
                    byKey[t.id] = ArchivedTrackRecord(
                        trackId = t.id,
                        fingerprint = fp,
                        playCount = maxOf(t.playCount, prev?.playCount ?: 0),
                        lastPlayedAt = maxOf(t.lastPlayedAt, prev?.lastPlayedAt ?: 0L),
                        isFavorite = t.isFavorite || (prev?.isFavorite ?: false),
                        isGeneralAudio = t.isGeneralAudio
                    )
                }
            }
            return byKey.values.toList()
        }

        fun applyArchivedRecordsToTracks(
            incoming: List<Track>,
            archived: List<ArchivedTrackRecord>
        ): List<Track> {
            if (incoming.isEmpty() || archived.isEmpty()) return incoming
            val byId = archived.associateBy { it.trackId }
            val byFingerprint = archived.associateBy { it.fingerprint }
            return incoming.map { t ->
                val fp = buildTrackHistoryFingerprint(t.title, t.artist, t.fileName)
                val match = byId[t.id] ?: byFingerprint[fp]
                if (match != null) {
                    t.copy(
                        playCount = maxOf(t.playCount, match.playCount),
                        lastPlayedAt = maxOf(t.lastPlayedAt, match.lastPlayedAt),
                        isFavorite = t.isFavorite || match.isFavorite
                    )
                } else {
                    t
                }
            }
        }

        fun serializeArchivedTrackRecords(records: List<ArchivedTrackRecord>): String {
            val sb = StringBuilder("AR1")
            records.forEach { rec ->
                sb.append('\n')
                    .append(escapeBookmarkField(rec.trackId)).append('\t')
                    .append(escapeBookmarkField(rec.fingerprint)).append('\t')
                    .append(rec.playCount).append('\t')
                    .append(rec.lastPlayedAt).append('\t')
                    .append(if (rec.isFavorite) "1" else "0").append('\t')
                    .append(if (rec.isGeneralAudio) "1" else "0")
            }
            return sb.toString()
        }

        fun parseArchivedTrackRecords(raw: String?): List<ArchivedTrackRecord> {
            if (raw.isNullOrBlank()) return emptyList()
            val trimmed = raw.trim()
            if (!trimmed.startsWith("AR1")) return emptyList()
            val result = mutableListOf<ArchivedTrackRecord>()
            trimmed.lineSequence().drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('\t')
                if (parts.size < 6) return@forEach
                val id = unescapeBookmarkField(parts[0])
                val fp = unescapeBookmarkField(parts[1])
                val playCount = parts[2].toIntOrNull() ?: 0
                val lastPlayed = parts[3].toLongOrNull() ?: 0L
                val fav = parts[4] == "1"
                val isGen = parts[5] == "1"
                if (id.isNotBlank() || fp.isNotBlank()) {
                    result.add(
                        ArchivedTrackRecord(
                            trackId = id,
                            fingerprint = fp,
                            playCount = playCount,
                            lastPlayedAt = lastPlayed,
                            isFavorite = fav,
                            isGeneralAudio = isGen
                        )
                    )
                }
            }
            return result
        }

        fun serializeTrackMetadataOverridesJson(map: Map<String, TrackMetadataOverride>): String {
            val sb = StringBuilder("MO1")
            map.values.forEach { ov ->
                sb.append('\n')
                    .append(escapeBookmarkField(ov.trackId)).append('\t')
                    .append(escapeBookmarkField(ov.title)).append('\t')
                    .append(escapeBookmarkField(ov.artist)).append('\t')
                    .append(escapeBookmarkField(ov.album)).append('\t')
                    .append(escapeBookmarkField(ov.customArtworkPath ?: "")).append('\t')
                    .append(escapeBookmarkField(ov.originalTitle)).append('\t')
                    .append(escapeBookmarkField(ov.originalArtist)).append('\t')
                    .append(escapeBookmarkField(ov.originalAlbum)).append('\t')
                    .append(escapeBookmarkField(ov.originalArtworkPath ?: "")).append('\t')
                    .append(escapeBookmarkField(ov.originalAlbumArtUri ?: ""))
            }
            return sb.toString()
        }

        fun parseTrackMetadataOverridesJson(raw: String?): Map<String, TrackMetadataOverride> {
            if (raw.isNullOrBlank()) return emptyMap()
            val trimmed = raw.trim()
            if (trimmed.startsWith("MO1")) {
                val map = linkedMapOf<String, TrackMetadataOverride>()
                trimmed.lineSequence().drop(1).forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split('\t')
                    if (parts.size < 10) return@forEach
                    val id = unescapeBookmarkField(parts[0])
                    if (id.isBlank()) return@forEach
                    map[id] = TrackMetadataOverride(
                        trackId = id,
                        title = unescapeBookmarkField(parts[1]),
                        artist = unescapeBookmarkField(parts[2]),
                        album = unescapeBookmarkField(parts[3]),
                        customArtworkPath = unescapeBookmarkField(parts[4]).ifBlank { null },
                        originalTitle = unescapeBookmarkField(parts[5]),
                        originalArtist = unescapeBookmarkField(parts[6]),
                        originalAlbum = unescapeBookmarkField(parts[7]),
                        originalArtworkPath = unescapeBookmarkField(parts[8]).ifBlank { null },
                        originalAlbumArtUri = unescapeBookmarkField(parts[9]).ifBlank { null }
                    )
                }
                return map
            }
            // Legacy JSON array fallback
            return runCatching {
                val arr = JSONArray(trimmed)
                val map = linkedMapOf<String, TrackMetadataOverride>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("trackId", "")
                    if (id.isBlank()) continue
                    map[id] = TrackMetadataOverride(
                        trackId = id,
                        title = obj.optString("title", ""),
                        artist = obj.optString("artist", ""),
                        album = obj.optString("album", ""),
                        customArtworkPath = obj.optString("customArtworkPath", "").ifBlank { null },
                        originalTitle = obj.optString("originalTitle", ""),
                        originalArtist = obj.optString("originalArtist", ""),
                        originalAlbum = obj.optString("originalAlbum", ""),
                        originalArtworkPath = obj.optString("originalArtworkPath", "").ifBlank { null },
                        originalAlbumArtUri = obj.optString("originalAlbumArtUri", "").ifBlank { null }
                    )
                }
                map
            }.getOrDefault(emptyMap())
        }

        fun applyMetadataOverridesToTracks(
            tracks: List<Track>,
            overrides: Map<String, TrackMetadataOverride>
        ): List<Track> {
            if (tracks.isEmpty() || overrides.isEmpty()) return tracks
            return tracks.map { track ->
                val ov = overrides[track.id] ?: return@map track
                val hasCustomCover = !ov.customArtworkPath.isNullOrBlank()
                track.copy(
                    title = ov.title.ifBlank { track.title },
                    artist = ov.artist.ifBlank { track.artist },
                    album = ov.album.ifBlank { track.album },
                    artworkPath = if (hasCustomCover) ov.customArtworkPath else track.artworkPath,
                    albumArtUri = if (hasCustomCover) null else track.albumArtUri
                )
            }
        }
    }
}
