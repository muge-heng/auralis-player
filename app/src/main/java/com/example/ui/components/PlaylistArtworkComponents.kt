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
import com.example.model.Track
import com.example.model.isValidMusicTag
import com.example.ui.theme.AuraViolet
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.RubyAccent

data class ArtistAutoPlaylist(
    val artistName: String,
    val tracks: List<Track>,
    val losslessCount: Int,
    val albumCount: Int
)

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
 * - 4+ distinct artworks -> crisp 2x2 vinyl mosaic
 * - 2-3 distinct artworks -> balanced side-by-side split or single hero artwork
 * - 1 artwork -> full-bleed studio cover
 * - 0 artworks -> tasteful gradient studio emblem
 */
@Composable
fun PlaylistCoverArt(
    tracks: List<Track>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    preferSingleHero: Boolean = false,
    fallbackIcon: ImageVector = Icons.Rounded.Album,
    fallbackGradient: List<Color> = listOf(RubyAccent.copy(alpha = 0.8f), AuraViolet.copy(alpha = 0.85f), Color(0xFF1A1429))
) {
    val distinctCovers = tracks
        .mapNotNull { t ->
            val key = t.artworkPath?.takeIf { it.isNotBlank() } ?: t.albumArtUri?.takeIf { it.isNotBlank() }
            if (key != null) key to (t.artworkPath to t.albumArtUri) else null
        }
        .distinctBy { it.first }
        .map { it.second }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(fallbackGradient))
            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        when {
            distinctCovers.isEmpty() -> {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(34.dp)
                )
            }
            preferSingleHero || distinctCovers.size < 4 -> {
                val (artPath, artUri) = distinctCovers.first()
                TrackArtwork(
                    artworkPath = artPath,
                    albumArtUri = artUri,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = cornerRadius
                )
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
                            artworkPath = distinctCovers[0].first,
                            albumArtUri = distinctCovers[0].second,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                        TrackArtwork(
                            artworkPath = distinctCovers[1].first,
                            albumArtUri = distinctCovers[1].second,
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
                            artworkPath = distinctCovers[2].first,
                            albumArtUri = distinctCovers[2].second,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                        TrackArtwork(
                            artworkPath = distinctCovers[3].first,
                            albumArtUri = distinctCovers[3].second,
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
