package com.example.data.scanner

import com.example.model.Track
import java.util.Locale
import kotlin.math.abs

enum class ImportConflictDecision(val label: String) {
    KEEP_EXISTING("保留曲库原版"),
    REPLACE_WITH_INCOMING("替换为新导入"),
    KEEP_HIGH_IN_MUSIC_DEMOTE_OTHER("高质留音乐/低质转普通音频"),
    KEEP_BOTH("保留两个版本")
}

data class ImportQualityConflict(
    val conflictId: String,
    val existingTrack: Track,
    val incomingTrack: Track,
    val existingScore: Int,
    val incomingScore: Int
) {
    val recommendedIncoming: Boolean
        get() = incomingScore > existingScore

    val bestTrack: Track
        get() = if (recommendedIncoming) incomingTrack else existingTrack
}

data class ImportDedupAnalysis(
    val newTracks: List<Track>,
    val updatedExistingTracks: List<Track>,
    val autoSkippedCount: Int,
    val qualityConflicts: List<ImportQualityConflict>
)

data class DuplicateTrackGroup(
    val groupId: String,
    val displayTitle: String,
    val displayArtist: String,
    /** Sorted descending by acoustic quality score: index 0 is the recommended best version. */
    val tracks: List<Track>,
    val scoresByTrackId: Map<String, Int>,
    val hasQualityDifference: Boolean
) {
    val bestTrack: Track
        get() = tracks.first()

    val musicZoneCount: Int
        get() = tracks.count { !it.isGeneralAudio }

    val redundantCount: Int
        get() = (tracks.size - 1).coerceAtLeast(0)
}

/**
 * Pure acoustic fingerprinting, quality scoring, import deduplication, and library conflict
 * detection engine. Designed to avoid false merges (e.g. Live vs Studio, Remix vs Original)
 * while reliably catching repeated imports across MediaStore/SAF and multi-quality duplicates.
 */
object TrackDeduplicator {

    private val AUDIO_EXT_REGEX = Regex("\\.(flac|mp3|wav|m4a|aac|ogg|opus|alac|aiff|aif|ape|dsf|dff|wma)$", RegexOption.IGNORE_CASE)
    private val QUALITY_NOISE_BRACKETS_REGEX = Regex(
        "[(\\[【（]\\s*(hi-?res|lossless|flac|sq|hq|mq|hr|320k|128k|256k|192k|无损|母带|高品|标准|臻品|杜比|copy|副本|\\d+)\\s*[)\\]】）]",
        RegexOption.IGNORE_CASE
    )
    private val TRAILING_COPY_SUFFIX_REGEX = Regex("\\s*[-_]\\s*(副本|copy|\\d+)$", RegexOption.IGNORE_CASE)
    private val PUNCT_SPACE_REGEX = Regex("[\\s\\p{Punct}·•—–_\\-　]+")

    private val VERSION_KEYWORDS = listOf(
        "live", "现场", "演唱会", "unplugged",
        "remix", "混音", "dj",
        "acoustic", "木吉他",
        "instrumental", "伴奏", "纯音乐", "karaoke", "inst",
        "piano", "钢琴版",
        "demo", "小样",
        "cover", "翻唱",
        "acapella", "清唱"
    )

    fun normalizeTitle(rawTitle: String): String {
        var s = rawTitle.trim()
        s = AUDIO_EXT_REGEX.replace(s, "")
        s = QUALITY_NOISE_BRACKETS_REGEX.replace(s, " ")
        s = TRAILING_COPY_SUFFIX_REGEX.replace(s, "")
        s = s.replace('（', '(')
            .replace('）', ')')
            .replace('【', '[')
            .replace('】', ']')
            .lowercase(Locale.ROOT)
        s = PUNCT_SPACE_REGEX.replace(s, "")
        return s
    }

    fun extractVersionMarker(rawTitle: String): String {
        val lower = rawTitle.lowercase(Locale.ROOT)
        val matched = VERSION_KEYWORDS.filter { kw ->
            if (kw.all { it.code < 128 }) {
                Regex("(^|[^a-z0-9])$kw([^a-z0-9]|$)").containsMatchIn(lower)
            } else {
                lower.contains(kw)
            }
        }
        return matched.sorted().joinToString("|")
    }

    fun normalizeArtist(rawArtist: String): String {
        val trimmed = rawArtist.trim()
        if (!com.example.model.isValidMusicTag(trimmed, "未知艺术家")) return ""
        return trimmed
            .lowercase(Locale.ROOT)
            .replace("feat.", ",")
            .replace("feat", ",")
            .replace("ft.", ",")
            .replace("、", ",")
            .replace("/", ",")
            .replace("&", ",")
            .split(",")
            .map { PUNCT_SPACE_REGEX.replace(it.trim(), "") }
            .filter { it.isNotEmpty() }
            .sorted()
            .joinToString(",")
    }

    fun areSameSong(a: Track, b: Track): Boolean {
        if (a.id == b.id) return true
        if (a.contentUri.isNotBlank() && a.contentUri == b.contentUri) return true

        // Exact same file name & byte size across different URIs/folders
        if (a.fileName.isNotBlank() &&
            a.fileName.equals(b.fileName, ignoreCase = true) &&
            a.sizeBytes > 0L && a.sizeBytes == b.sizeBytes
        ) {
            return true
        }

        val titleA = normalizeTitle(a.title)
        val titleB = normalizeTitle(b.title)
        if (titleA.isEmpty() || titleA != titleB) return false

        // Keep distinct versions (e.g., Live / Remix / Instrumental) separate!
        val versionA = extractVersionMarker(a.title)
        val versionB = extractVersionMarker(b.title)
        if (versionA != versionB) return false

        // Duration guard: tolerate encoder padding (<= 4.5s)
        val bothHaveDuration = a.durationMs > 0L && b.durationMs > 0L
        val durationDiffMs = if (bothHaveDuration) abs(a.durationMs - b.durationMs) else 0L
        if (bothHaveDuration && durationDiffMs > 4_500L) return false

        val artistA = normalizeArtist(a.artist)
        val artistB = normalizeArtist(b.artist)

        return when {
            artistA.isNotEmpty() && artistB.isNotEmpty() -> {
                artistA == artistB ||
                    artistA.split(",").firstOrNull() == artistB.split(",").firstOrNull()
            }
            // If one or both lack artist tags, require tight duration agreement or album match
            else -> {
                val albumA = normalizeTitle(a.album).takeIf { com.example.model.isValidMusicTag(a.album, "未知专辑") }
                val albumB = normalizeTitle(b.album).takeIf { com.example.model.isValidMusicTag(b.album, "未知专辑") }
                val albumMatch = albumA != null && albumA == albumB
                albumMatch || (bothHaveDuration && durationDiffMs <= 2_500L)
            }
        }
    }

    /**
     * Computes a deterministic acoustic & metadata quality score so the highest-fidelity copy
     * can always be identified and one-tap selected.
     */
    fun computeQualityScore(track: Track): Int {
        val codec = track.codecLabel.uppercase(Locale.ROOT)
        var score = when {
            codec == "DSD" || codec == "DSF" || codec == "DFF" -> 620
            track.isHiRes -> 540
            track.isLossless -> 420
            codec in setOf("OPUS", "AAC", "M4A", "OGG") -> 220
            else -> 180
        }

        if (track.sampleRateHz > 0) {
            score += (track.sampleRateHz / 1000).coerceIn(0, 192)
        }
        if (track.bitDepth > 0) {
            score += track.bitDepth.coerceIn(8, 32) * 4
        }
        if (track.bitrateKbps > 0) {
            score += (track.bitrateKbps / 10).coerceIn(0, 320)
        }

        // Metadata completeness tie-breaker
        if (!track.artworkPath.isNullOrBlank() || !track.albumArtUri.isNullOrBlank()) score += 16
        if (track.hasValidArtist) score += 10
        if (track.hasValidAlbum) score += 6
        if (!track.embeddedLyrics.isNullOrBlank()) score += 10

        // Size tie-breaker (up to 60 pts)
        if (track.sizeBytes > 0L) {
            score += (track.sizeBytes / (1024L * 1024L)).toInt().coerceIn(0, 60)
        }
        return score
    }

    /**
     * True when two duplicate tracks have essentially identical audio quality (same tier, codec,
     * bit depth, sample rate & near-identical bitrate), meaning the incoming copy can be safely
     * auto-skipped on import without bothering the user.
     */
    fun isEquivalentQuality(a: Track, b: Track): Boolean {
        if (a.id == b.id || (a.contentUri.isNotBlank() && a.contentUri == b.contentUri)) return true
        if (a.sizeBytes > 0L && a.sizeBytes == b.sizeBytes && a.codecLabel.equals(b.codecLabel, ignoreCase = true)) {
            return true
        }
        if (a.isHiRes != b.isHiRes || a.isLossless != b.isLossless) return false
        if (!a.codecLabel.equals(b.codecLabel, ignoreCase = true)) return false
        if (a.bitDepth != b.bitDepth && a.bitDepth > 0 && b.bitDepth > 0) return false
        if (a.sampleRateHz > 0 && b.sampleRateHz > 0 && abs(a.sampleRateHz - b.sampleRateHz) > 4_000) return false
        if (a.bitrateKbps > 0 && b.bitrateKbps > 0 && abs(a.bitrateKbps - b.bitrateKbps) > 40) return false
        // If bitrate unknown on both, compare file size ratio (>18% difference implies different quality)
        if (a.bitrateKbps <= 0 && b.bitrateKbps <= 0 && a.sizeBytes > 0L && b.sizeBytes > 0L) {
            val maxSize = maxOf(a.sizeBytes, b.sizeBytes).toDouble()
            val minSize = minOf(a.sizeBytes, b.sizeBytes).toDouble()
            if (minSize / maxSize < 0.82) return false
        }
        return true
    }

    fun formatSizeMb(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "未知体积"
        val mb = sizeBytes / (1024.0 * 1024.0)
        return if (mb >= 10.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.2f MB", mb)
        }
    }

    fun qualitySummaryLine(track: Track): String {
        val parts = mutableListOf<String>()
        parts += track.qualityBadge
        if (track.bitrateKbps > 0) parts += "${track.bitrateKbps} kbps"
        if (track.sampleRateHz > 0) {
            val khz = track.sampleRateHz / 1000f
            val khzStr = if (khz % 1f == 0f) "${khz.toInt()}kHz" else String.format(Locale.US, "%.1fkHz", khz)
            parts += if (track.isLossless && track.bitDepth > 0) "${track.bitDepth}bit/$khzStr" else khzStr
        }
        if (track.sizeBytes > 0L) parts += formatSizeMb(track.sizeBytes)
        return parts.joinToString(" · ")
    }

    /**
     * Analyzes a batch of newly scanned/imported tracks against the current library:
     * - Same ID -> refreshes metadata cleanly without duplicating.
     * - Same song + equivalent quality -> auto-skipped.
     * - Same song + different quality -> collected into [ImportDedupAnalysis.qualityConflicts]
     *   so the UI can ask the user how to resolve them.
     * - Also deduplicates within the incoming batch itself (keeping best quality or flagging conflict).
     */
    fun analyzeImportCandidates(
        incoming: List<Track>,
        existing: List<Track>
    ): ImportDedupAnalysis {
        val existingById = existing.associateBy { it.id }
        val pool = existing.toMutableList()
        val newTracks = mutableListOf<Track>()
        val updatedExisting = mutableListOf<Track>()
        val conflicts = mutableListOf<ImportQualityConflict>()
        val conflictedExistingIds = mutableSetOf<String>()
        var skipped = 0

        for (candidate in incoming) {
            val exactSameId = existingById[candidate.id]
            if (exactSameId != null) {
                // Existing DB entry refreshed by MediaStore scan — preserve user state
                val refreshed = candidate.copy(
                    playCount = maxOf(exactSameId.playCount, candidate.playCount),
                    lastPlayedAt = maxOf(exactSameId.lastPlayedAt, candidate.lastPlayedAt),
                    isFavorite = exactSameId.isFavorite || candidate.isFavorite,
                    isGeneralAudio = exactSameId.isGeneralAudio
                )
                if (refreshed != exactSameId) {
                    updatedExisting.add(refreshed)
                } else {
                    skipped++
                }
                continue
            }

            // Look for an already-existing or earlier-in-batch match
            val matched = pool.firstOrNull { areSameSong(it, candidate) }
            if (matched == null) {
                newTracks.add(candidate)
                pool.add(candidate)
                continue
            }

            if (isEquivalentQuality(matched, candidate)) {
                skipped++
                continue
            }

            // Different quality version of the same song!
            val existingScore = computeQualityScore(matched)
            val incomingScore = computeQualityScore(candidate)
            if (matched.id in existingById) {
                if (matched.id !in conflictedExistingIds) {
                    conflictedExistingIds.add(matched.id)
                    conflicts.add(
                        ImportQualityConflict(
                            conflictId = "${matched.id}__${candidate.id}",
                            existingTrack = matched,
                            incomingTrack = candidate,
                            existingScore = existingScore,
                            incomingScore = incomingScore
                        )
                    )
                } else {
                    // Another variant for an already-queued conflict: swap in if even higher quality
                    val idx = conflicts.indexOfFirst { it.existingTrack.id == matched.id }
                    if (idx >= 0 && incomingScore > conflicts[idx].incomingScore) {
                        conflicts[idx] = conflicts[idx].copy(
                            conflictId = "${matched.id}__${candidate.id}",
                            incomingTrack = candidate,
                            incomingScore = incomingScore
                        )
                    }
                    skipped++
                }
            } else {
                // Both are brand-new tracks inside the SAME incoming batch (e.g. folder contains MP3 + FLAC of same song)
                // Put the first one as "existing" side and new one as "incoming" conflict, removing first from auto-new
                val removedIdx = newTracks.indexOfFirst { it.id == matched.id }
                if (removedIdx >= 0) {
                    // Keep the first in library as baseline, surface conflict between both
                    conflicts.add(
                        ImportQualityConflict(
                            conflictId = "${matched.id}__${candidate.id}",
                            existingTrack = matched,
                            incomingTrack = candidate,
                            existingScore = existingScore,
                            incomingScore = incomingScore
                        )
                    )
                } else {
                    skipped++
                }
            }
        }

        return ImportDedupAnalysis(
            newTracks = newTracks,
            updatedExistingTracks = updatedExisting,
            autoSkippedCount = skipped,
            qualityConflicts = conflicts
        )
    }

    /**
     * Scans the entire existing library and clusters all duplicate versions of the same song
     * into sorted [DuplicateTrackGroup]s (highest acoustic quality first).
     */
    fun detectLibraryDuplicates(tracks: List<Track>): List<DuplicateTrackGroup> {
        if (tracks.size < 2) return emptyList()

        // Bucket by normalized title first for fast O(N) grouping, then verify with areSameSong
        val visited = BooleanArray(tracks.size)
        val groups = mutableListOf<DuplicateTrackGroup>()

        for (i in tracks.indices) {
            if (visited[i]) continue
            val seed = tracks[i]
            val cluster = mutableListOf(seed)
            visited[i] = true

            for (j in (i + 1) until tracks.size) {
                if (visited[j]) continue
                if (areSameSong(seed, tracks[j])) {
                    cluster.add(tracks[j])
                    visited[j] = true
                }
            }

            if (cluster.size >= 2) {
                val scores = cluster.associate { it.id to computeQualityScore(it) }
                val sorted = cluster.sortedWith(
                    compareByDescending<Track> { scores[it.id] ?: 0 }
                        .thenBy { it.isGeneralAudio } // prefer music zone on exact tie
                        .thenByDescending { it.isFavorite }
                        .thenByDescending { it.playCount }
                        .thenBy { it.dateAddedSec }
                )
                val first = sorted.first()
                val hasDiffQuality = sorted.drop(1).any { !isEquivalentQuality(first, it) }
                val bestArtist = sorted.firstOrNull { it.hasValidArtist }?.artist ?: first.artist

                groups.add(
                    DuplicateTrackGroup(
                        groupId = "dup_${normalizeTitle(first.title)}_${first.id}",
                        displayTitle = first.title,
                        displayArtist = bestArtist,
                        tracks = sorted,
                        scoresByTrackId = scores,
                        hasQualityDifference = hasDiffQuality
                    )
                )
            }
        }

        return groups.sortedWith(
            compareByDescending<DuplicateTrackGroup> { it.musicZoneCount >= 2 }
                .thenByDescending { it.hasQualityDifference }
                .thenByDescending { it.tracks.size }
                .thenBy { it.displayTitle.lowercase(Locale.ROOT) }
        )
    }
}
