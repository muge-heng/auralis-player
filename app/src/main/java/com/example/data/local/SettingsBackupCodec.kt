package com.example.data.local

import com.example.model.AiCatalogAttachmentPrefs
import com.example.model.AiConfig
import com.example.model.AiProviderType
import com.example.model.AppVersionInfo
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
import com.example.model.normalizeEqBands
import com.example.model.snapLyricFontScale
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsBackupSnapshot(
    val darkTheme: Boolean = true,
    val hapticProfile: HapticProfile = HapticProfile.STUDIO,
    val navBarStyle: NavBarStyle = NavBarStyle.FLOATING_GLASS,
    val visualEffectsEnhanced: Boolean = false,
    val showLibraryHero: Boolean = true,
    val showGeneralAudioNotice: Boolean = true,
    val enableGeneralAudioMode: Boolean = true,
    val deletionGuardMode: DeletionGuardMode = DeletionGuardMode.CONFIRM_DIALOG,
    val scanFilter: ScanFilterConfig = ScanFilterConfig(),
    val lyricSettings: LyricSettings = LyricSettings(),
    val audioTransition: AudioTransitionConfig = AudioTransitionConfig(),
    val eqState: EqState = EqState(),
    val customEqPresets: List<EqPreset> = emptyList(),
    val aiConfig: AiConfig = AiConfig(),
    val aiCatalogAttachmentPrefs: AiCatalogAttachmentPrefs = AiCatalogAttachmentPrefs(),
    val playlistCoverConfigs: Map<String, PlaylistCoverConfig> = emptyMap(),
    val audioBookmarks: Map<String, List<AudioBookmark>> = emptyMap()
)

data class SettingsBackupPreview(
    val appVersion: String,
    val schemaVersion: Int,
    val exportedAtMs: Long,
    val includesApiKey: Boolean,
    val themeLabel: String,
    val navBarLabel: String,
    val customEqPresetCount: Int,
    val excludedFolderCount: Int,
    val playlistCoverCount: Int,
    val bookmarkCount: Int
)

data class ParsedSettingsBackup(
    val snapshot: SettingsBackupSnapshot,
    val preview: SettingsBackupPreview
)

private data class SettingsBackupDocument(
    val schemaVersion: Int = 0,
    val appName: String = "",
    val appVersion: String = "",
    val exportedAtMs: Long = 0L,
    val includesApiKey: Boolean = false,
    val appearance: AppearanceBackupDto = AppearanceBackupDto(),
    val scanFilter: ScanFilterBackupDto = ScanFilterBackupDto(),
    val lyrics: LyricSettingsBackupDto = LyricSettingsBackupDto(),
    val audioTransition: AudioTransitionBackupDto = AudioTransitionBackupDto(),
    val equalizer: EqualizerBackupDto = EqualizerBackupDto(),
    val aiConfig: AiConfigBackupDto = AiConfigBackupDto(),
    val aiCatalogPrefs: AiCatalogPrefsBackupDto = AiCatalogPrefsBackupDto(),
    val playlistCovers: Map<String, PlaylistCoverBackupDto> = emptyMap(),
    val audioBookmarks: Map<String, List<AudioBookmarkBackupDto>> = emptyMap()
)

private data class AppearanceBackupDto(
    val darkTheme: Boolean = true,
    val hapticProfile: String = HapticProfile.STUDIO.name,
    val navBarStyle: String = NavBarStyle.FLOATING_GLASS.name,
    val visualEffectsEnhanced: Boolean = false,
    val showLibraryHero: Boolean = true,
    val showGeneralAudioNotice: Boolean = true,
    val enableGeneralAudioMode: Boolean = true,
    val deletionGuardMode: String = DeletionGuardMode.CONFIRM_DIALOG.name
)

private data class ScanFilterBackupDto(
    val minDurationSec: Int = 30,
    val maxDurationSec: Int = 0,
    val minSizeKb: Int = 500,
    val excludeSystemAudio: Boolean = true,
    val requireMetadata: Boolean = true,
    val requireArtist: Boolean = true,
    val requireAlbumOrArt: Boolean = true,
    val classifyUnverifiedAsGeneralAudio: Boolean = true,
    val excludedFolders: List<String> = ScanFilterConfig().excludedFolders.toList()
)

private data class LyricSettingsBackupDto(
    val enableSimulatedWordByWord: Boolean = true,
    val showTranslation: Boolean = true,
    val fontScale: Float = 1.0f,
    val fontWeight: Int = 700,
    val keepScreenOnInLyrics: Boolean = true,
    val activeOnlineSource: String = LyricSource.ALL.name,
    val enableLyricBlur: Boolean = true,
    val enableImmersiveLyrics: Boolean = false,
    val enableElasticLyricWave: Boolean = false
)

private data class AudioTransitionBackupDto(
    val mode: String = AudioTransitionMode.GAPLESS.name,
    val durationMs: Int = 3500,
    val fadeOnManualSkip: Boolean = false,
    val fadeOnPlayPause: Boolean = true
)

private data class EqPresetBackupDto(
    val id: String = "",
    val name: String = "",
    val bandLevelsDb: List<Float> = emptyList(),
    val bassBoostPercent: Int = 0,
    val virtualizerPercent: Int = 0
)

private data class EqualizerBackupDto(
    val enabled: Boolean = false,
    val presetId: String = "flat",
    val bandLevelsDb: List<Float> = List(EQ_BAND_COUNT) { 0f },
    val bassBoostPercent: Int = 0,
    val virtualizerPercent: Int = 0,
    val customPresets: List<EqPresetBackupDto> = emptyList()
)

private data class AiConfigBackupDto(
    val activeProvider: String = AiProviderType.DEEPSEEK.name,
    val deepSeekApiKey: String = "",
    val deepSeekBaseUrl: String = "https://api.deepseek.com",
    val deepSeekModelId: String = "deepseek-flash",
    val enableVisionForCovers: Boolean = true,
    val enableWebSearch: Boolean = true,
    val maxOutputTokens: Int = DEFAULT_AI_MAX_OUTPUT_TOKENS,
    val reasoningEffort: String = ReasoningEffortLevel.OFF.apiValue,
    val enabledReasoningLevels: List<String> = ReasoningEffortLevel.defaultEnabledSet.map { it.name },
    val showThinkingProcess: Boolean = true,
    val enableStreaming: Boolean = true,
    val autoGenerateSessionTitle: Boolean = false,
    val customSystemPrompt: String = ""
)

private data class AiCatalogPrefsBackupDto(
    val enabled: Boolean = false,
    val hasCustomSelection: Boolean = false,
    val selectedTrackIds: List<String> = emptyList(),
    val lyricTrackIds: List<String> = emptyList()
)

private data class PlaylistCoverBackupDto(
    val mode: String = PlaylistCoverMode.AUTO_COLLAGE.name,
    val trackIds: List<String> = emptyList(),
    val randomSeed: Long = 0L
)

private data class AudioBookmarkBackupDto(
    val id: String = "",
    val positionMs: Long = 0L,
    val label: String = "",
    val createdAtMs: Long = 0L
)

private const val CURRENT_SCHEMA_VERSION = 1

object SettingsBackupCodec {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(SettingsBackupDocument::class.java).indent("  ")

    fun suggestFileName(nowMs: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        return "auralis_settings_${formatter.format(Date(nowMs))}.json"
    }

    fun formatExportTimestamp(timestampMs: Long): String {
        if (timestampMs <= 0L) return "未知时间"
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    fun encode(
        snapshot: SettingsBackupSnapshot,
        includeApiKey: Boolean = false,
        exportedAtMs: Long = System.currentTimeMillis()
    ): String {
        val exportedKey = if (includeApiKey) snapshot.aiConfig.deepSeekApiKey.trim() else ""
        val doc = SettingsBackupDocument(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            appName = AppVersionInfo.APP_DISPLAY_NAME,
            appVersion = AppVersionInfo.CURRENT_VERSION,
            exportedAtMs = exportedAtMs,
            includesApiKey = exportedKey.isNotEmpty(),
            appearance = AppearanceBackupDto(
                darkTheme = snapshot.darkTheme,
                hapticProfile = snapshot.hapticProfile.name,
                navBarStyle = snapshot.navBarStyle.name,
                visualEffectsEnhanced = snapshot.visualEffectsEnhanced,
                showLibraryHero = snapshot.showLibraryHero,
                showGeneralAudioNotice = snapshot.showGeneralAudioNotice,
                enableGeneralAudioMode = snapshot.enableGeneralAudioMode,
                deletionGuardMode = snapshot.deletionGuardMode.name
            ),
            scanFilter = ScanFilterBackupDto(
                minDurationSec = snapshot.scanFilter.normalized().minDurationSec,
                maxDurationSec = snapshot.scanFilter.normalized().maxDurationSec,
                minSizeKb = snapshot.scanFilter.normalized().minSizeKb,
                excludeSystemAudio = snapshot.scanFilter.excludeSystemAudio,
                requireMetadata = snapshot.scanFilter.requireMetadata,
                requireArtist = snapshot.scanFilter.requireArtist,
                requireAlbumOrArt = snapshot.scanFilter.requireAlbumOrArt,
                classifyUnverifiedAsGeneralAudio = snapshot.scanFilter.classifyUnverifiedAsGeneralAudio,
                excludedFolders = snapshot.scanFilter.excludedFolders.sorted()
            ),
            lyrics = LyricSettingsBackupDto(
                enableSimulatedWordByWord = snapshot.lyricSettings.enableSimulatedWordByWord,
                showTranslation = snapshot.lyricSettings.showTranslation,
                fontScale = snapLyricFontScale(snapshot.lyricSettings.fontScale),
                fontWeight = snapshot.lyricSettings.fontWeight.coerceIn(300, 900),
                keepScreenOnInLyrics = snapshot.lyricSettings.keepScreenOnInLyrics,
                activeOnlineSource = snapshot.lyricSettings.activeOnlineSource.name,
                enableLyricBlur = snapshot.lyricSettings.enableLyricBlur,
                enableImmersiveLyrics = snapshot.lyricSettings.enableImmersiveLyrics,
                enableElasticLyricWave = snapshot.lyricSettings.enableElasticLyricWave
            ),
            audioTransition = AudioTransitionBackupDto(
                mode = snapshot.audioTransition.mode.name,
                durationMs = snapshot.audioTransition.durationMs.coerceIn(1500, 10_000),
                fadeOnManualSkip = snapshot.audioTransition.fadeOnManualSkip,
                fadeOnPlayPause = snapshot.audioTransition.fadeOnPlayPause
            ),
            equalizer = EqualizerBackupDto(
                enabled = snapshot.eqState.enabled,
                presetId = snapshot.eqState.presetId,
                bandLevelsDb = normalizeEqBands(snapshot.eqState.bandLevelsDb, EQ_BAND_COUNT),
                bassBoostPercent = snapshot.eqState.bassBoostPercent.coerceIn(0, 100),
                virtualizerPercent = snapshot.eqState.virtualizerPercent.coerceIn(0, 100),
                customPresets = snapshot.customEqPresets.map { preset ->
                    EqPresetBackupDto(
                        id = preset.id,
                        name = preset.name,
                        bandLevelsDb = normalizeEqBands(preset.bandLevelsDb, EQ_BAND_COUNT),
                        bassBoostPercent = preset.bassBoostPercent.coerceIn(0, 100),
                        virtualizerPercent = preset.virtualizerPercent.coerceIn(0, 100)
                    )
                }
            ),
            aiConfig = AiConfigBackupDto(
                activeProvider = snapshot.aiConfig.activeProvider.name,
                deepSeekApiKey = exportedKey,
                deepSeekBaseUrl = snapshot.aiConfig.deepSeekBaseUrl.trim().ifBlank { "https://api.deepseek.com" },
                deepSeekModelId = snapshot.aiConfig.deepSeekModelId.trim().ifBlank { "deepseek-flash" },
                enableVisionForCovers = snapshot.aiConfig.enableVisionForCovers,
                enableWebSearch = snapshot.aiConfig.enableWebSearch,
                maxOutputTokens = snapshot.aiConfig.maxOutputTokens.coerceAtLeast(256),
                reasoningEffort = snapshot.aiConfig.reasoningEffort.apiValue,
                enabledReasoningLevels = snapshot.aiConfig.enabledReasoningLevels.map { it.name }.sorted(),
                showThinkingProcess = snapshot.aiConfig.showThinkingProcess,
                enableStreaming = snapshot.aiConfig.enableStreaming,
                autoGenerateSessionTitle = snapshot.aiConfig.autoGenerateSessionTitle,
                customSystemPrompt = snapshot.aiConfig.customSystemPrompt
            ),
            aiCatalogPrefs = AiCatalogPrefsBackupDto(
                enabled = snapshot.aiCatalogAttachmentPrefs.enabled,
                hasCustomSelection = snapshot.aiCatalogAttachmentPrefs.hasCustomSelection,
                selectedTrackIds = snapshot.aiCatalogAttachmentPrefs.selectedTrackIds.sorted(),
                lyricTrackIds = snapshot.aiCatalogAttachmentPrefs.lyricTrackIds.sorted()
            ),
            playlistCovers = snapshot.playlistCoverConfigs
                .filterValues { !it.isDefault }
                .mapValues { (_, cfg) ->
                    PlaylistCoverBackupDto(
                        mode = cfg.mode.name,
                        trackIds = cfg.trackIds.filter { it.isNotBlank() }.distinct().take(4),
                        randomSeed = cfg.randomSeed
                    )
                },
            audioBookmarks = snapshot.audioBookmarks
                .filterValues { it.isNotEmpty() }
                .mapValues { (_, list) ->
                    list.sortedBy { it.positionMs }.map { bm ->
                        AudioBookmarkBackupDto(
                            id = bm.id,
                            positionMs = bm.positionMs,
                            label = bm.label,
                            createdAtMs = bm.createdAtMs
                        )
                    }
                }
        )
        return adapter.toJson(doc)
    }

    fun decode(rawJson: String): Result<ParsedSettingsBackup> {
        val cleaned = rawJson.trimStart('\uFEFF').trim()
        if (cleaned.isBlank()) {
            return Result.failure(IllegalArgumentException("备份文件内容为空"))
        }
        return runCatching {
            val doc = adapter.fromJson(cleaned)
                ?: throw IllegalArgumentException("无法解析备份文件格式")
            if (!doc.appName.equals(AppVersionInfo.APP_DISPLAY_NAME, ignoreCase = true) ||
                doc.schemaVersion <= 0
            ) {
                throw IllegalArgumentException("该 JSON 文件不是有效的 Auralis 设置备份")
            }

            val haptic = runCatching { HapticProfile.valueOf(doc.appearance.hapticProfile) }
                .getOrDefault(HapticProfile.STUDIO)
            val navStyle = runCatching { NavBarStyle.valueOf(doc.appearance.navBarStyle) }
                .getOrDefault(NavBarStyle.FLOATING_GLASS)
            val deletionGuard = DeletionGuardMode.fromStored(doc.appearance.deletionGuardMode)

            val lyricSource = runCatching { LyricSource.valueOf(doc.lyrics.activeOnlineSource) }
                .getOrDefault(LyricSource.ALL)

            val aiProvider = runCatching { AiProviderType.valueOf(doc.aiConfig.activeProvider) }
                .getOrDefault(AiProviderType.DEEPSEEK)
            val enabledEfforts = doc.aiConfig.enabledReasoningLevels
                .mapNotNull { runCatching { ReasoningEffortLevel.valueOf(it) }.getOrNull() }
                .toSet()
                .ifEmpty { ReasoningEffortLevel.defaultEnabledSet }
            val effort = ReasoningEffortLevel.fromApiValue(doc.aiConfig.reasoningEffort)
                .let { if (it in enabledEfforts) it else ReasoningEffortLevel.OFF }

            val customPresets = doc.equalizer.customPresets.mapIndexed { idx, dto ->
                EqPreset(
                    id = dto.id.ifBlank { "custom_restored_$idx" },
                    name = dto.name.trim().ifBlank { "自定义预设 #${idx + 1}" },
                    bandLevelsDb = normalizeEqBands(dto.bandLevelsDb, EQ_BAND_COUNT),
                    bassBoostPercent = dto.bassBoostPercent.coerceIn(0, 100),
                    virtualizerPercent = dto.virtualizerPercent.coerceIn(0, 100),
                    isCustom = true
                )
            }

            val covers = doc.playlistCovers
                .mapNotNull { (key, dto) ->
                    val cleanKey = key.trim()
                    if (cleanKey.isEmpty()) return@mapNotNull null
                    val cfg = PlaylistCoverConfig(
                        mode = PlaylistCoverMode.fromStored(dto.mode),
                        trackIds = dto.trackIds.filter { it.isNotBlank() }.distinct().take(4),
                        randomSeed = dto.randomSeed
                    )
                    if (cfg.isDefault) null else cleanKey to cfg
                }
                .toMap()

            val bookmarks = doc.audioBookmarks
                .mapNotNull { (trackId, list) ->
                    val cleanTrackId = trackId.trim()
                    if (cleanTrackId.isEmpty()) return@mapNotNull null
                    val cleanList = list.mapIndexedNotNull { idx, dto ->
                        if (dto.positionMs < 0L) return@mapIndexedNotNull null
                        AudioBookmark(
                            id = dto.id.ifBlank { "bm_${cleanTrackId}_$idx" },
                            positionMs = dto.positionMs,
                            label = dto.label.trim().ifBlank { "标记 #${idx + 1}" },
                            createdAtMs = dto.createdAtMs
                        )
                    }.sortedBy { it.positionMs }.take(40)
                    if (cleanList.isEmpty()) null else cleanTrackId to cleanList
                }
                .toMap()

            val hasNonBlankKey = doc.aiConfig.deepSeekApiKey.trim().isNotEmpty()
            val snapshot = SettingsBackupSnapshot(
                darkTheme = doc.appearance.darkTheme,
                hapticProfile = haptic,
                navBarStyle = navStyle,
                visualEffectsEnhanced = doc.appearance.visualEffectsEnhanced,
                showLibraryHero = doc.appearance.showLibraryHero,
                showGeneralAudioNotice = doc.appearance.showGeneralAudioNotice,
                enableGeneralAudioMode = doc.appearance.enableGeneralAudioMode,
                deletionGuardMode = deletionGuard,
                scanFilter = ScanFilterConfig(
                    minDurationSec = doc.scanFilter.minDurationSec.coerceIn(0, 600),
                    maxDurationSec = doc.scanFilter.maxDurationSec.coerceIn(0, 7200),
                    minSizeKb = doc.scanFilter.minSizeKb.coerceIn(0, 10_000),
                    excludeSystemAudio = doc.scanFilter.excludeSystemAudio,
                    requireMetadata = doc.scanFilter.requireMetadata,
                    requireArtist = doc.scanFilter.requireArtist,
                    requireAlbumOrArt = doc.scanFilter.requireAlbumOrArt,
                    classifyUnverifiedAsGeneralAudio = doc.scanFilter.classifyUnverifiedAsGeneralAudio,
                    excludedFolders = doc.scanFilter.excludedFolders
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                ).normalized(),
                lyricSettings = LyricSettings(
                    enableSimulatedWordByWord = doc.lyrics.enableSimulatedWordByWord,
                    showTranslation = doc.lyrics.showTranslation,
                    fontScale = snapLyricFontScale(doc.lyrics.fontScale),
                    fontWeight = doc.lyrics.fontWeight.coerceIn(300, 900),
                    keepScreenOnInLyrics = doc.lyrics.keepScreenOnInLyrics,
                    activeOnlineSource = lyricSource,
                    enableLyricBlur = doc.lyrics.enableLyricBlur,
                    enableImmersiveLyrics = doc.lyrics.enableImmersiveLyrics,
                    enableElasticLyricWave = doc.lyrics.enableElasticLyricWave
                ),
                audioTransition = AudioTransitionConfig(
                    mode = AudioTransitionMode.fromStored(doc.audioTransition.mode),
                    durationMs = doc.audioTransition.durationMs.coerceIn(1500, 10_000),
                    fadeOnManualSkip = doc.audioTransition.fadeOnManualSkip,
                    fadeOnPlayPause = doc.audioTransition.fadeOnPlayPause
                ),
                eqState = EqState(
                    enabled = doc.equalizer.enabled,
                    presetId = doc.equalizer.presetId.ifBlank { "flat" },
                    bandLevelsDb = normalizeEqBands(doc.equalizer.bandLevelsDb, EQ_BAND_COUNT),
                    bassBoostPercent = doc.equalizer.bassBoostPercent.coerceIn(0, 100),
                    virtualizerPercent = doc.equalizer.virtualizerPercent.coerceIn(0, 100)
                ),
                customEqPresets = customPresets,
                aiConfig = AiConfig(
                    activeProvider = aiProvider,
                    deepSeekApiKey = doc.aiConfig.deepSeekApiKey.trim(),
                    deepSeekBaseUrl = doc.aiConfig.deepSeekBaseUrl.trim().ifBlank { "https://api.deepseek.com" },
                    deepSeekModelId = doc.aiConfig.deepSeekModelId.trim().ifBlank { "deepseek-flash" },
                    enableVisionForCovers = doc.aiConfig.enableVisionForCovers,
                    enableWebSearch = doc.aiConfig.enableWebSearch,
                    maxOutputTokens = doc.aiConfig.maxOutputTokens.coerceAtLeast(256),
                    reasoningEffort = effort,
                    enabledReasoningLevels = enabledEfforts,
                    showThinkingProcess = doc.aiConfig.showThinkingProcess,
                    enableStreaming = doc.aiConfig.enableStreaming,
                    autoGenerateSessionTitle = doc.aiConfig.autoGenerateSessionTitle,
                    customSystemPrompt = doc.aiConfig.customSystemPrompt
                ),
                aiCatalogAttachmentPrefs = AiCatalogAttachmentPrefs(
                    enabled = doc.aiCatalogPrefs.enabled,
                    hasCustomSelection = doc.aiCatalogPrefs.hasCustomSelection,
                    selectedTrackIds = doc.aiCatalogPrefs.selectedTrackIds.toSet(),
                    lyricTrackIds = doc.aiCatalogPrefs.lyricTrackIds.toSet()
                ),
                playlistCoverConfigs = covers,
                audioBookmarks = bookmarks
            )

            val preview = SettingsBackupPreview(
                appVersion = doc.appVersion.ifBlank { "0.9.x" },
                schemaVersion = doc.schemaVersion,
                exportedAtMs = doc.exportedAtMs,
                includesApiKey = doc.includesApiKey && hasNonBlankKey,
                themeLabel = if (snapshot.darkTheme) "曜石深色" else "云白浅色",
                navBarLabel = snapshot.navBarStyle.label,
                customEqPresetCount = snapshot.customEqPresets.size,
                excludedFolderCount = snapshot.scanFilter.excludedFolders.size,
                playlistCoverCount = snapshot.playlistCoverConfigs.size,
                bookmarkCount = snapshot.audioBookmarks.values.sumOf { it.size }
            )

            ParsedSettingsBackup(snapshot = snapshot, preview = preview)
        }
    }
}
