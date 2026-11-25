package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val durationMs: Long,
    val sizeBytes: Long,
    val folderPath: String,
    val fileName: String,
    val mimeType: String,
    val codecLabel: String,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 16,
    val isLossless: Boolean = false,
    val isHiRes: Boolean = false,
    val artworkPath: String? = null,
    val albumArtUri: String? = null,
    val embeddedLyrics: String? = null,
    val dateAddedSec: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val isGeneralAudio: Boolean = false
) {
    val hasValidArtist: Boolean
        get() = isValidMusicTag(artist, "未知艺术家")

    val hasValidAlbum: Boolean
        get() = isValidMusicTag(album, "未知专辑")

    val hasStrongMusicMetadata: Boolean
        get() = hasValidArtist || (hasValidAlbum && (!artworkPath.isNullOrBlank() || isLossless || year > 0 || genre.isNotBlank()))

    val qualityBadge: String
        get() = when {
            isHiRes -> "HI-RES ${codecLabel}"
            isLossless -> "LOSSLESS ${codecLabel}"
            bitrateKbps >= 256 -> "HQ ${codecLabel}"
            else -> codecLabel
        }

    val technicalSpecLine: String
        get() {
            val parts = mutableListOf<String>()
            parts.add(codecLabel)
            if (sampleRateHz > 0) {
                val khz = sampleRateHz / 1000.0
                val formattedKhz = if (khz % 1.0 == 0.0) {
                    String.format(Locale.US, "%.0fkHz", khz)
                } else {
                    String.format(Locale.US, "%.1fkHz", khz)
                }
                if (isLossless) {
                    parts.add("${bitDepth}-BIT / $formattedKhz")
                } else {
                    parts.add(formattedKhz)
                }
            }
            if (bitrateKbps > 0) {
                parts.add("${bitrateKbps} kbps")
            }
            return parts.joinToString(" · ")
        }
}

fun isValidMusicTag(raw: String?, unknownFallback: String = ""): Boolean {
    val cleaned = raw?.trim().orEmpty()
    if (cleaned.isEmpty()) return false
    if (unknownFallback.isNotEmpty() && cleaned.equals(unknownFallback, ignoreCase = true)) return false
    val lower = cleaned.lowercase()
    return lower != "<unknown>" &&
        lower != "unknown" &&
        lower != "unknown artist" &&
        lower != "unknown album" &&
        lower != "未知艺术家" &&
        lower != "未知专辑" &&
        lower != "未知" &&
        lower != "null"
}

fun Long.formatDuration(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
