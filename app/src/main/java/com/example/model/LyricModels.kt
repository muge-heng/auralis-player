package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LyricSource(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val accentHex: Long
) {
    ALL("all", "全网聚合", "多源", 0xFFF0C28A),
    NETEASE("netease", "网易云音乐", "网易云", 0xFFEC4141),
    QQ_MUSIC("qq", "QQ 音乐", "QQ音乐", 0xFF31C27C),
    KUGOU("kugou", "酷狗音乐", "酷狗", 0xFF2B9BFF),
    LRCLIB("lrclib", "LRCLIB", "LRCLIB", 0xFFA78BFA);

    companion object {
        val selectableProviders: List<LyricSource> = listOf(NETEASE, QQ_MUSIC, KUGOU, LRCLIB)
    }
}

enum class LyricFormat(val label: String) {
    QRC("QRC 逐字"),
    YRC("YRC 逐字"),
    KRC("KRC 逐字"),
    ENHANCED_LRC("LRC+ 逐字"),
    TTML("TTML 逐字"),
    LRC("LRC 逐行"),
    SRT("SRT 字幕"),
    PLAIN("纯文本"),
    NONE("无歌词")
}

data class LyricWord(
    val text: String,
    val startMs: Long,
    val durationMs: Long
) {
    val endMs: Long get() = startMs + durationMs
}

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<LyricWord> = emptyList(),
    val isWordSyncedNative: Boolean = false
)

data class LyricDocument(
    val format: LyricFormat,
    val lines: List<LyricLine>,
    val sourceLabel: String = "",
    val rawContent: String = "",
    val globalOffsetMs: Long = 0L,
    val isSimulatedWordSync: Boolean = false
) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val hasSyncedTimestamps: Boolean
        get() = format != LyricFormat.PLAIN && format != LyricFormat.NONE && lines.isNotEmpty()
    val hasWordTiming: Boolean
        get() = lines.any { it.words.isNotEmpty() }
}

@Entity(tableName = "custom_lyrics")
data class CustomLyricEntity(
    @PrimaryKey val trackId: String,
    val rawContent: String,
    val formatName: String,
    val sourceLabel: String,
    val offsetMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

data class OnlineLyricCandidate(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Double,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val source: LyricSource = LyricSource.LRCLIB,
    val isWordSynced: Boolean = false,
    val hasTranslation: Boolean = false,
    val previewSnippet: String = ""
) {
    val uniqueKey: String get() = "${source.id}_${id}_${trackName.hashCode()}"
}
