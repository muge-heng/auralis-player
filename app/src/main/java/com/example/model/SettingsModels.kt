package com.example.model

enum class HapticProfile(val label: String, val subtitle: String) {
    STUDIO("触觉工坊", "播放、切歌、歌词跳转与拨盘均带阻尼震动"),
    SUBTLE("轻盈微振", "仅在关键操作时触发细腻轻触"),
    OFF("关闭震动", "完全静音触觉反馈")
}

enum class RepeatMode(val label: String) {
    ALL("列表循环"),
    ONE("单曲循环"),
    OFF("顺序播放")
}

enum class LibraryFilterFormat(val label: String) {
    ALL("全部格式"),
    LOSSLESS("无损"),
    HIRES("Hi-Res"),
    LOSSY("流行压缩")
}

enum class LibrarySortOption(val label: String) {
    SMART("智能排序"),
    RECENT("最近添加"),
    TITLE("曲名 A-Z"),
    ARTIST("艺术家"),
    DURATION("时长"),
    PLAY_COUNT("常听热度")
}

enum class AiProviderType(val displayName: String, val badge: String) {
    DEEPSEEK("DeepSeek / 兼容 API", "已就绪 · 多轮/思考/视觉"),
    GEMINI("Google Gemini", "接口预留 · 暂未启用")
}

/**
 * Reasoning effort levels passed conditionally to reasoning-capable endpoints.
 * Default enabled tiers: OFF, LOW, HIGH, MAX.
 * Optional manually toggleable tiers: MEDIUM, XHIGH, ULTRA.
 */
enum class ReasoningEffortLevel(
    val apiValue: String,
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val isDefaultEnabled: Boolean,
    val intensityRank: Int
) {
    OFF("off", "关闭思考", "OFF", "直出响应，不发送 reasoning_effort 参数", true, 0),
    LOW("low", "轻度思考", "LOW", "低延迟轻推理，适合快速问答与译注", true, 1),
    MEDIUM("medium", "中度思考", "MED", "平衡推演深度与响应速度（可选扩展）", false, 2),
    HIGH("high", "深度思考", "HIGH", "多步逻辑推理，细腻乐评与声学剖析", true, 3),
    XHIGH("xhigh", "极深思考", "XHIGH", "高能深层推演，适合复杂编曲与排错（可选扩展）", false, 4),
    MAX("max", "满载推理", "MAX", "释放全量思维链算力，深度洞察", true, 5),
    ULTRA("ultra", "超频穹顶", "ULTRA", "超限思维风暴，极致深度推理（可选扩展）", false, 6);

    companion object {
        val defaultEnabledSet: Set<ReasoningEffortLevel> = entries.filter { it.isDefaultEnabled }.toSet()
        val optionalExtendedLevels: List<ReasoningEffortLevel> = entries.filter { !it.isDefaultEnabled }

        fun fromApiValue(raw: String?): ReasoningEffortLevel {
            if (raw.isNullOrBlank()) return OFF
            return entries.firstOrNull {
                it.apiValue.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
            } ?: OFF
        }
    }
}

enum class NavBarStyle(val label: String, val subtitle: String) {
    FLOATING_GLASS("Liquid Glass 底栏", "全透明悬浮玻璃胶囊，支持按住拖动跟手切换（默认）"),
    CLASSIC("经典底栏", "标准 Material 3 贴底四段式导航栏")
}

data class ScanFilterConfig(
    val minDurationSec: Int = 30,
    val minSizeKb: Int = 500,
    val excludeSystemAudio: Boolean = true,
    val requireMetadata: Boolean = false,
    val requireArtist: Boolean = false,
    val requireAlbumOrArt: Boolean = false,
    val classifyUnverifiedAsGeneralAudio: Boolean = true,
    val excludedFolders: Set<String> = setOf(
        "WhatsApp", "WeChat", "Recordings", "Notifications",
        "Alarms", "Ringtones", "CallRecordings", "Sounds"
    ),
    val includedFoldersOnly: Set<String> = emptySet()
)

const val DEFAULT_AI_MAX_OUTPUT_TOKENS = 393_216 // 384K

val AI_MAX_TOKEN_PRESETS: List<Pair<Int, String>> = listOf(
    65_536 to "64K",
    131_072 to "128K",
    262_144 to "256K",
    393_216 to "384K"
)

fun formatTokenCountLabel(tokens: Int): String = when {
    tokens >= 1024 && tokens % 1024 == 0 -> "${tokens / 1024}K"
    tokens >= 1000 && tokens % 1000 == 0 -> "${tokens / 1000}K"
    else -> tokens.toString()
}

fun parseTokenCountInput(raw: String): Int? {
    val cleaned = raw.trim().uppercase()
    if (cleaned.isEmpty()) return null
    return when {
        cleaned.endsWith("K") -> {
            val num = cleaned.removeSuffix("K").trim().toDoubleOrNull() ?: return null
            (num * 1024).toInt().takeIf { it >= 256 }
        }
        cleaned.endsWith("M") -> {
            val num = cleaned.removeSuffix("M").trim().toDoubleOrNull() ?: return null
            (num * 1024 * 1024).toInt().takeIf { it >= 256 }
        }
        else -> cleaned.toIntOrNull()?.takeIf { it >= 256 }
    }
}

data class AiCatalogAttachmentPrefs(
    val enabled: Boolean = false,
    val hasCustomSelection: Boolean = false,
    val selectedTrackIds: Set<String> = emptySet(),
    val lyricTrackIds: Set<String> = emptySet()
)

data class AiConfig(
    val activeProvider: AiProviderType = AiProviderType.DEEPSEEK,
    val deepSeekApiKey: String = "",
    val deepSeekBaseUrl: String = "https://api.deepseek.com",
    val deepSeekModelId: String = "deepseek-flash",
    val enableVisionForCovers: Boolean = true,
    val enableWebSearch: Boolean = true,
    val maxOutputTokens: Int = DEFAULT_AI_MAX_OUTPUT_TOKENS,
    val reasoningEffort: ReasoningEffortLevel = ReasoningEffortLevel.OFF,
    val enabledReasoningLevels: Set<ReasoningEffortLevel> = ReasoningEffortLevel.defaultEnabledSet,
    val showThinkingProcess: Boolean = true,
    val enableStreaming: Boolean = true,
    val autoGenerateSessionTitle: Boolean = false,
    val customSystemPrompt: String = ""
) {
    val activeOrderedReasoningLevels: List<ReasoningEffortLevel>
        get() = ReasoningEffortLevel.entries
            .filter { it in enabledReasoningLevels }
            .ifEmpty { ReasoningEffortLevel.defaultEnabledSet.toList() }
            .sortedBy { it.intensityRank }
}

const val EQ_BAND_COUNT = 10

fun normalizeEqBands(raw: List<Float>, targetSize: Int = EQ_BAND_COUNT): List<Float> {
    if (raw.isEmpty()) return List(targetSize) { 0f }
    if (raw.size == targetSize) return raw.map { it.coerceIn(-12f, 12f) }
    if (raw.size == 1) return List(targetSize) { raw[0].coerceIn(-12f, 12f) }
    val lastSrc = (raw.size - 1).toFloat()
    val lastDst = (targetSize - 1).coerceAtLeast(1).toFloat()
    return List(targetSize) { idx ->
        val pos = (idx / lastDst) * lastSrc
        val lo = pos.toInt().coerceIn(0, raw.lastIndex)
        val hi = (lo + 1).coerceIn(0, raw.lastIndex)
        val frac = pos - lo
        val v = raw[lo] + (raw[hi] - raw[lo]) * frac
        ((v * 2f).toInt() / 2f).coerceIn(-12f, 12f)
    }
}

data class EqPreset(
    val id: String,
    val name: String,
    val bandLevelsDb: List<Float>, // 10 bands: 31Hz..16kHz (-12f..+12f)
    val bassBoostPercent: Int = 0,
    val virtualizerPercent: Int = 0,
    val isCustom: Boolean = false
)

data class EqState(
    val enabled: Boolean = false,
    val presetId: String = "flat",
    val bandLevelsDb: List<Float> = List(EQ_BAND_COUNT) { 0f },
    val bassBoostPercent: Int = 0,
    val virtualizerPercent: Int = 0
)

val LYRIC_FONT_SCALE_TIERS: List<Pair<Float, String>> = listOf(
    0.85f to "极小",
    0.95f to "偏小",
    1.00f to "标准",
    1.10f to "偏大",
    1.20f to "大号",
    1.35f to "超大"
)

fun snapLyricFontScaleIndex(scale: Float): Int {
    return LYRIC_FONT_SCALE_TIERS.indices.minByOrNull { idx ->
        kotlin.math.abs(LYRIC_FONT_SCALE_TIERS[idx].first - scale)
    } ?: 2
}

fun snapLyricFontScale(scale: Float): Float {
    return LYRIC_FONT_SCALE_TIERS[snapLyricFontScaleIndex(scale)].first
}

fun lyricFontScaleLabel(scale: Float): String {
    val tier = LYRIC_FONT_SCALE_TIERS[snapLyricFontScaleIndex(scale)]
    return "${tier.second} (${(tier.first * 100).toInt()}%)"
}

data class LyricSettings(
    val enableSimulatedWordByWord: Boolean = true,
    val showTranslation: Boolean = true,
    val fontScale: Float = 1.0f,
    val fontWeight: Int = 700, // 300 (Light) .. 900 (Black)
    val keepScreenOnInLyrics: Boolean = true,
    val activeOnlineSource: LyricSource = LyricSource.ALL,
    val enableLyricBlur: Boolean = true,
    val enableImmersiveLyrics: Boolean = false,
    val enableElasticLyricWave: Boolean = false
) {
    val fontWeightLabel: String
        get() = when {
            fontWeight <= 350 -> "纤细 (300)"
            fontWeight <= 450 -> "常规 (400)"
            fontWeight <= 550 -> "适中 (500)"
            fontWeight <= 650 -> "半粗 (600)"
            fontWeight <= 750 -> "粗体 (700)"
            fontWeight <= 850 -> "特粗 (800)"
            else -> "极粗 (900)"
        }
}

data class AuraPalette(
    val primaryHex: Long = 0xFFFA2D48,
    val secondaryHex: Long = 0xFF7B61FF,
    val deepHex: Long = 0xFF1A1024,
    val glowHex: Long = 0xFFF0C28A
)

enum class PlaylistCoverMode(
    val label: String,
    val shortLabel: String,
    val subtitle: String
) {
    AUTO_COLLAGE("自动四宫格", "自动四图", "自动取歌单内前 4 张不同曲目封面拼贴"),
    SINGLE_TRACK("单曲封面", "单曲大图", "指定歌单内某一首曲目的封面作为独幅封面"),
    CUSTOM_COLLAGE("自选四宫格", "自选四图", "自由指定最多 4 首曲目封面及四宫格排布顺序"),
    RANDOM_COLLAGE("随机四宫格", "随机四图", "随机抽取歌单内 4 张曲目封面拼贴，支持随时换一批");

    companion object {
        fun fromStored(raw: String?): PlaylistCoverMode {
            if (raw.isNullOrBlank()) return AUTO_COLLAGE
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO_COLLAGE
        }
    }
}

data class PlaylistCoverConfig(
    val mode: PlaylistCoverMode = PlaylistCoverMode.AUTO_COLLAGE,
    val trackIds: List<String> = emptyList(),
    val randomSeed: Long = 0L
) {
    val isDefault: Boolean
        get() = mode == PlaylistCoverMode.AUTO_COLLAGE && trackIds.isEmpty() && randomSeed == 0L
}

object PlaylistCoverKeys {
    const val FAVORITES = "smart:favorites"
    const val RECENT = "smart:recent"
    const val MOST_PLAYED = "smart:most_played"

    fun custom(playlistId: Long): String = "custom:$playlistId"
    fun artist(artistName: String): String = "artist:${artistName.trim()}"
}

enum class DeletionGuardMode(
    val label: String,
    val shortLabel: String,
    val subtitle: String
) {
    CONFIRM_DIALOG(
        label = "删除前二次确认",
        shortLabel = "二次确认",
        subtitle = "删除预设、AI 对话、歌单或曲目前弹出确认窗，彻底杜绝误触"
    ),
    TOP_UNDO_BANNER(
        label = "置顶可撤销提示条",
        shortLabel = "置顶撤销",
        subtitle = "删除后在顶部滑出悬浮条，6 秒内支持一键撤销或左右/上滑关闭"
    );

    companion object {
        fun fromStored(raw: String?): DeletionGuardMode {
            if (raw.isNullOrBlank()) return CONFIRM_DIALOG
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CONFIRM_DIALOG
        }
    }
}

/**
 * Timestamp bookmark attached to a local audio file (ideal for podcasts, lectures,
 * voice recordings, and language listening in General Audio mode).
 */
data class AudioBookmark(
    val id: String,
    val positionMs: Long,
    val label: String,
    val createdAtMs: Long = 0L
)

/**
 * Multi-mode acoustic transition engine modeled after modern Hi-Fi players
 * (Apple Music AutoMix / Crossfade & Poweramp fading controls).
 */
enum class AudioTransitionMode(
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val supportsDurationSlider: Boolean,
    val isSecondary: Boolean = false
) {
    GAPLESS(
        label = "无缝直连 (Gapless)",
        shortLabel = "无缝直连",
        subtitle = "零衰减原生无缝衔接，完整保留现场专辑、交响乐与概念专辑的连贯听感",
        supportsDurationSlider = false
    ),
    CROSSFADE(
        label = "等功率交叉淡化",
        shortLabel = "交叉淡化",
        subtitle = "双轨实时重叠混音，采用余弦/正弦等功率曲线衔接，过渡全程响度平稳不掉音量",
        supportsDurationSlider = true
    ),
    AUTOMIX(
        label = "智能声场混接 (AutoMix)",
        shortLabel = "智能混接",
        subtitle = "自动识别专辑连贯性与音频类型：同专辑曲目轻吻衔接，跨专辑自动平滑 DJ 混接，普通音频自动避让不串音",
        supportsDurationSlider = true
    ),
    LYRIC_BRIDGE(
        label = "人声接唱 (跳过前尾奏)",
        shortLabel = "人声接唱",
        subtitle = "末句唱毕后自动渐弱淡出，并精准衔接至下一首第一句歌词前渐强入歌，大幅缩短无词间奏；任一首缺词时自动退化为智能声场混接",
        supportsDurationSlider = true
    ),
    SMOOTH_FADE(
        label = "柔和淡出淡入",
        shortLabel = "淡出淡入",
        subtitle = "曲尾以 S 曲线平滑淡出至静谧，随后新曲丝滑淡入，两首歌绝不混叠",
        supportsDurationSlider = true,
        isSecondary = true
    ),
    VINYL_BRAKE(
        label = "黑胶缓停过渡",
        shortLabel = "黑胶缓停",
        subtitle = "模拟黑胶唱机离合减速慢刹降调并轻柔收尾，随即干脆切入下一首曲目",
        supportsDurationSlider = false,
        isSecondary = true
    );

    companion object {
        val primaryModes: List<AudioTransitionMode>
            get() = entries.filter { !it.isSecondary }

        val secondaryModes: List<AudioTransitionMode>
            get() = entries.filter { it.isSecondary }

        fun fromStored(raw: String?): AudioTransitionMode {
            if (raw.isNullOrBlank()) return GAPLESS
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GAPLESS
        }
    }
}

data class AudioTransitionConfig(
    val mode: AudioTransitionMode = AudioTransitionMode.GAPLESS,
    val durationMs: Int = 3500, // 1500..10000 ms
    val fadeOnManualSkip: Boolean = false,
    val fadeOnPlayPause: Boolean = true
) {
    val durationSecondsLabel: String
        get() = String.format(java.util.Locale.US, "%.1f 秒", durationMs / 1000f)
}

/**
 * Archived play history & favorite snapshot preserved when the user clears only the
 * player's audio library, allowing seamless auto-restoration when the same audio is re-imported.
 */
data class ArchivedTrackRecord(
    val trackId: String,
    val fingerprint: String,
    val playCount: Int,
    val lastPlayedAt: Long,
    val isFavorite: Boolean,
    val isGeneralAudio: Boolean
)

/**
 * In-app metadata & cover art override for a track. Stored locally without modifying
 * the underlying source audio file, preserving original values for 1-tap restore.
 */
data class TrackMetadataOverride(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val customArtworkPath: String? = null,
    val originalTitle: String,
    val originalArtist: String,
    val originalAlbum: String,
    val originalArtworkPath: String? = null,
    val originalAlbumArtUri: String? = null
)

/**
 * Four clearly stratified destructive actions in the Settings Danger Zone.
 * Every action strictly enforces continuous 2-step confirmation.
 */
enum class DangerZoneAction(
    val title: String,
    val shortTitle: String,
    val subtitle: String,
    val step1Impact: String,
    val step1Preserved: String,
    val step2FinalWarning: String
) {
    CLEAR_AUDIO_LIBRARY_ONLY(
        title = "清除播放器内全部音频",
        shortTitle = "仅清除曲库音频",
        subtitle = "清空曲库与普通音频区列表，绝不删除手机本地原文件；播放次数、红心与歌词等记录完整保留，重新导入同名音乐时自动回填",
        step1Impact = "将清空播放器曲库与普通音频区中的全部曲目索引，并停止当前播放队列。",
        step1Preserved = "手机本地音频原文件完好无损；每首歌的播放次数、最近播放、红心收藏、自建歌单、缓存歌词、时间书签、AI 对话与全部设置均会完整保留，重新导入相同音乐时自动恢复播放记录。",
        step2FinalWarning = "再次确认：确定清空播放器内的全部音频索引吗？后续需要重新扫描或导入才能继续播放。"
    ),
    CLEAR_RECORDS_AND_LOGS(
        title = "清除全部播放与配置记录",
        shortTitle = "清除播放与记录",
        subtitle = "清空播放热度、最近播放、红心、歌单与封面配置、已获取歌词、时间书签、自定义 EQ 预设及全部 AI 对话记录",
        step1Impact = "将永久清除所有播放统计（热度/最近播放/红心）、历史归档、全部自建歌单及歌单封面配置、已匹配/导入的歌词记录、普通音频时间书签、自定义 EQ 预设以及全部 AI 对话窗口。",
        step1Preserved = "已导入的曲库音频列表、手机本地原文件，以及外观/歌词/扫描/过渡等偏好开关将保持不变。",
        step2FinalWarning = "最终警告：歌单、播放统计、歌词缓存与 AI 对话记录清空后无法撤销，确定立即清除？"
    ),
    RESET_ALL_SETTINGS(
        title = "还原所有设置项",
        shortTitle = "还原所有设置",
        subtitle = "将外观主题、底栏、视效增强、智能过渡、歌词引擎、扫描规则、均衡器与 AI 模型配置重置为出厂默认值",
        step1Impact = "将重置深浅主题、底栏样式、光影增强、智能过渡、触觉震动、歌词字重字号与动效、扫描筛选规则、均衡器状态、防误删保护及 AI 接口配置（含本地 API 密钥）。",
        step1Preserved = "已导入的曲库音频、播放统计、自建歌单、缓存歌词、时间书签与 AI 历史对话记录均完整保留。",
        step2FinalWarning = "最终警告：所有个性化设置与本地 AI 配置都将恢复默认，确定立即还原？"
    ),
    NUKE_ALL_DATA(
        title = "一键清除所有数据",
        shortTitle = "一键清除全部",
        subtitle = "彻底清空应用内曲库索引、全部播放/歌单/歌词/AI 记录并还原所有设置，恢复全新初始状态（绝不删除本地音频文件）",
        step1Impact = "将一次性清空播放器内全部音频索引、播放记录、歌单、歌词缓存、时间书签、自定义 EQ 预设、AI 对话历史，并将全部设置与 AI 密钥还原为出厂初始状态。",
        step1Preserved = "仅保留手机存储中的本地音频原文件，绝不会删除或修改你的磁盘文件。",
        step2FinalWarning = "最高级别危险确认：此操作将彻底重置整个应用内数据且不可恢复！确认继续一键清除所有数据？"
    )
}




