package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.PlaylistCoverConfig
import com.example.model.PlaylistCoverMode
import com.example.model.Track
import com.example.model.isValidMusicTag
import com.example.ui.theme.AuraViolet
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.RubyAccent
import kotlin.random.Random

data class ArtistAutoPlaylist(
    val artistName: String,
    val tracks: List<Track>,
    val losslessCount: Int,
    val albumCount: Int
)

data class CoverArtSlot(
    val trackId: String,
    val title: String,
    val artworkPath: String?,
    val albumArtUri: String?
)

data class ResolvedPlaylistCover(
    val slots: List<CoverArtSlot>,
    val renderAsSingle: Boolean
)

fun Track.artworkDedupKey(): String? =
    artworkPath?.takeIf { it.isNotBlank() } ?: albumArtUri?.takeIf { it.isNotBlank() }

fun Track.hasArtwork(): Boolean = artworkDedupKey() != null

private fun Track.toCoverArtSlot(): CoverArtSlot = CoverArtSlot(
    trackId = id,
    title = title,
    artworkPath = artworkPath,
    albumArtUri = albumArtUri
)

/**
 * Resolves which cover artwork slots to display for a playlist given an optional [PlaylistCoverConfig].
 * Supports:
 * - AUTO_COLLAGE: first 4 distinct artworks (or 1 hero if fewer than 4 distinct artworks exist)
 * - SINGLE_TRACK: exact chosen track artwork rendered full-bleed
 * - CUSTOM_COLLAGE: user-chosen track order (up to 4), gracefully handling missing tracks
 * - RANDOM_COLLAGE: deterministic seeded random pick of up to 4 distinct artworks (zero flicker)
 */
fun resolvePlaylistCover(
    tracks: List<Track>,
    config: PlaylistCoverConfig?
): ResolvedPlaylistCover {
    val tracksWithArt = tracks.filter { it.hasArtwork() }
    if (tracksWithArt.isEmpty()) {
        return ResolvedPlaylistCover(slots = emptyList(), renderAsSingle = true)
    }
    val distinctArtTracks = tracksWithArt.distinctBy { it.artworkDedupKey() }
    val byId = tracksWithArt.associateBy { it.id }

    return when (config?.mode ?: PlaylistCoverMode.AUTO_COLLAGE) {
        PlaylistCoverMode.AUTO_COLLAGE -> {
            if (distinctArtTracks.size >= 4) {
                ResolvedPlaylistCover(
                    slots = distinctArtTracks.take(4).map { it.toCoverArtSlot() },
                    renderAsSingle = false
                )
            } else {
                ResolvedPlaylistCover(
                    slots = listOf(distinctArtTracks.first().toCoverArtSlot()),
                    renderAsSingle = true
                )
            }
        }

        PlaylistCoverMode.SINGLE_TRACK -> {
            val chosen = config?.trackIds
                ?.firstNotNullOfOrNull { id -> byId[id] }
                ?: distinctArtTracks.first()
            ResolvedPlaylistCover(
                slots = listOf(chosen.toCoverArtSlot()),
                renderAsSingle = true
            )
        }

        PlaylistCoverMode.CUSTOM_COLLAGE -> {
            val selected = config?.trackIds
                .orEmpty()
                .mapNotNull { id -> byId[id] }
                .distinctBy { it.id }
                .take(4)
                .toMutableList()
            if (selected.isEmpty()) {
                // Fall back to default auto collage if no valid tracks remain
                return resolvePlaylistCover(tracks, null)
            }
            // If user picked fewer than 4, fill remaining slots from distinct artworks only if needed for a full 4-tile grid
            if (selected.size in 2..3 && distinctArtTracks.size >= 4) {
                val chosenIds = selected.map { it.id }.toSet()
                val chosenArtKeys = selected.mapNotNull { it.artworkDedupKey() }.toSet()
                val extras = distinctArtTracks.filter { it.id !in chosenIds && it.artworkDedupKey() !in chosenArtKeys }
                for (extra in extras) {
                    if (selected.size >= 4) break
                    selected.add(extra)
                }
            }
            ResolvedPlaylistCover(
                slots = selected.map { it.toCoverArtSlot() },
                renderAsSingle = selected.size == 1
            )
        }

        PlaylistCoverMode.RANDOM_COLLAGE -> {
            val pool = if (distinctArtTracks.size >= 4) distinctArtTracks else tracksWithArt
            val seed = config?.randomSeed?.takeIf { it != 0L } ?: 1L
            val shuffled = pool.shuffled(Random(seed)).take(4)
            ResolvedPlaylistCover(
                slots = shuffled.map { it.toCoverArtSlot() },
                renderAsSingle = shuffled.size <= 1
            )
        }
    }
}

/**
 * Splits multi-artist strings (e.g. "扇宝/星瞳", "周杰伦、费玉清", "A & B") into clean individual
 * artist buckets so collaborations appear under each artist's auto-playlist seamlessly.
 */
fun extractArtistNames(rawArtist: String): List<String> {
    val trimmed = rawArtist.trim()
    if (!isValidMusicTag(trimmed, "未知艺术家")) return listOf("未知艺术家")
    val split = trimmed
        .replace("、", "/")
        .replace("／", "/")
        .replace(" feat. ", "/", ignoreCase = true)
        .replace(" ft. ", "/", ignoreCase = true)
        .replace(" & ", "/")
        .replace(" x ", "/", ignoreCase = true)
        .split("/", ";", "；")
        .map { it.trim() }
        .filter { it.isNotEmpty() && isValidMusicTag(it, "未知艺术家") }
        .distinct()
    return split.ifEmpty { listOf(trimmed) }
}

fun buildArtistAutoPlaylists(tracks: List<Track>): List<ArtistAutoPlaylist> {
    if (tracks.isEmpty()) return emptyList()
    val grouped = linkedMapOf<String, MutableList<Track>>()
    for (track in tracks) {
        val names = extractArtistNames(track.artist)
        for (name in names) {
            grouped.getOrPut(name) { mutableListOf() }.add(track)
        }
    }
    return grouped.entries.map { (artistName, list) ->
        val uniqueTracks = list.distinctBy { it.id }
        ArtistAutoPlaylist(
            artistName = artistName,
            tracks = uniqueTracks,
            losslessCount = uniqueTracks.count { it.isLossless },
            albumCount = uniqueTracks.map { it.album }.filter { isValidMusicTag(it, "未知专辑") }.distinct().size
        )
    }.sortedWith(
        compareByDescending<ArtistAutoPlaylist> { it.tracks.size }
            .thenBy { it.artistName.lowercase() }
    )
}

fun formatCollectionTotalDuration(tracks: List<Track>): String {
    val totalSec = tracks.sumOf { it.durationMs.coerceAtLeast(0L) } / 1000L
    if (totalSec <= 0L) return "0 分钟"
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    return when {
        hours > 0L -> "${hours} 小时 ${mins} 分钟"
        mins > 0L -> "${mins} 分钟"
        else -> "${totalSec} 秒"
    }
}

/**
 * Editorial multi-cover collage / hero cover artwork for playlists and artist auto-collections.
 * Supports optional user-configured [coverConfig] (single cover, custom 4-track collage, or seeded random 4-track collage).
 */
@Composable
fun PlaylistCoverArt(
    tracks: List<Track>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    preferSingleHero: Boolean = false,
    coverConfig: PlaylistCoverConfig? = null,
    fallbackIcon: ImageVector = Icons.Rounded.Album,
    fallbackGradient: List<Color> = listOf(RubyAccent.copy(alpha = 0.8f), AuraViolet.copy(alpha = 0.85f), Color(0xFF1A1429))
) {
    val resolved = resolvePlaylistCover(tracks, coverConfig)
    val slots = resolved.slots
    val forceSingle = preferSingleHero && (coverConfig == null || coverConfig.mode == PlaylistCoverMode.AUTO_COLLAGE)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(fallbackGradient))
            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        when {
            slots.isEmpty() -> {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(34.dp)
                )
            }

            forceSingle || resolved.renderAsSingle || slots.size == 1 -> {
                val hero = slots.first()
                TrackArtwork(
                    artworkPath = hero.artworkPath,
                    albumArtUri = hero.albumArtUri,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = cornerRadius
                )
            }

            slots.size == 2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    TrackArtwork(
                        artworkPath = slots[0].artworkPath,
                        albumArtUri = slots[0].albumArtUri,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = 0.dp
                    )
                    TrackArtwork(
                        artworkPath = slots[1].artworkPath,
                        albumArtUri = slots[1].albumArtUri,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = 0.dp
                    )
                }
            }

            slots.size == 3 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    TrackArtwork(
                        artworkPath = slots[0].artworkPath,
                        albumArtUri = slots[0].albumArtUri,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = 0.dp
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        TrackArtwork(
                            artworkPath = slots[1].artworkPath,
                            albumArtUri = slots[1].albumArtUri,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            cornerRadius = 0.dp
                        )
                        TrackArtwork(
                            artworkPath = slots[2].artworkPath,
                            albumArtUri = slots[2].albumArtUri,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            cornerRadius = 0.dp
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        TrackArtwork(
                            artworkPath = slots[0].artworkPath,
                            albumArtUri = slots[0].albumArtUri,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                        TrackArtwork(
                            artworkPath = slots[1].artworkPath,
                            albumArtUri = slots[1].albumArtUri,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        TrackArtwork(
                            artworkPath = slots[2].artworkPath,
                            albumArtUri = slots[2].albumArtUri,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                        TrackArtwork(
                            artworkPath = slots[3].artworkPath,
                            albumArtUri = slots[3].albumArtUri,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                    }
                }
            }
        }
    }
}

