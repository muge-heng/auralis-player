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
    val enableImmersiveLyrics: Boolean = false
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



