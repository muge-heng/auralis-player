package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PlaylistCoverConfig
import com.example.model.PlaylistCoverMode
import com.example.model.Track
import com.example.ui.theme.AuraViolet
import com.example.ui.theme.adaptiveGoldColor
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistCoverEditorSheet(
    playlistTitle: String,
    tracks: List<Track>,
    initialConfig: PlaylistCoverConfig?,
    fallbackIcon: ImageVector,
    heroAccent: Color = RubyAccent,
    onSave: (PlaylistCoverConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tracksWithArt = remember(tracks) { tracks.filter { it.hasArtwork() } }
    val distinctArtTracks = remember(tracksWithArt) { tracksWithArt.distinctBy { it.artworkDedupKey() } }

    var selectedMode by remember(initialConfig) {
        mutableStateOf(initialConfig?.mode ?: PlaylistCoverMode.AUTO_COLLAGE)
    }
    var selectedSingleId by remember(initialConfig, tracksWithArt) {
        val preset = initialConfig?.takeIf { it.mode == PlaylistCoverMode.SINGLE_TRACK }?.trackIds?.firstOrNull()
        mutableStateOf(
            preset?.takeIf { id -> tracksWithArt.any { it.id == id } }
                ?: tracksWithArt.firstOrNull()?.id
        )
    }
    var customQuadIds by remember(initialConfig, tracksWithArt) {
        val preset = initialConfig?.takeIf { it.mode == PlaylistCoverMode.CUSTOM_COLLAGE }?.trackIds
            ?.filter { id -> tracksWithArt.any { it.id == id } }
            ?.distinct()
            ?.take(4)
            .orEmpty()
        val initialQuad = if (preset.isNotEmpty()) {
            preset
        } else {
            distinctArtTracks.take(4).map { it.id }
        }
        mutableStateOf(initialQuad)
    }
    var randomSeed by remember(initialConfig) {
        mutableLongStateOf(
            initialConfig?.takeIf { it.mode == PlaylistCoverMode.RANDOM_COLLAGE && it.randomSeed != 0L }?.randomSeed
                ?: System.currentTimeMillis()
        )
    }

    val draftConfig = remember(selectedMode, selectedSingleId, customQuadIds, randomSeed) {
        when (selectedMode) {
            PlaylistCoverMode.AUTO_COLLAGE -> PlaylistCoverConfig(mode = PlaylistCoverMode.AUTO_COLLAGE)
            PlaylistCoverMode.SINGLE_TRACK -> PlaylistCoverConfig(
                mode = PlaylistCoverMode.SINGLE_TRACK,
                trackIds = listOfNotNull(selectedSingleId)
            )
            PlaylistCoverMode.CUSTOM_COLLAGE -> PlaylistCoverConfig(
                mode = PlaylistCoverMode.CUSTOM_COLLAGE,
                trackIds = customQuadIds.take(4)
            )
            PlaylistCoverMode.RANDOM_COLLAGE -> PlaylistCoverConfig(
                mode = PlaylistCoverMode.RANDOM_COLLAGE,
                randomSeed = randomSeed.takeIf { it != 0L } ?: 1L
            )
        }
    }

    val liveResolved = remember(tracks, draftConfig) {
        resolvePlaylistCover(tracks, draftConfig)
    }

    fun pickRandomFourIds(): List<String> {
        val pool = if (distinctArtTracks.size >= 4) distinctArtTracks else tracksWithArt
        return pool.shuffled(Random(System.nanoTime())).take(4).map { it.id }
    }

    val slotPositionLabels = remember { listOf("左上", "右上", "左下", "右下") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "设置歌单封面",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = playlistTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = {
                        selectedMode = PlaylistCoverMode.AUTO_COLLAGE
                        onSave(null)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("playlist_cover_reset_default")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("恢复默认")
                }
            }

            // Live Large Preview Stage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(0.75.dp, heroAccent.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = heroAccent.copy(alpha = 0.45f),
                            spotColor = heroAccent.copy(alpha = 0.45f)
                        )
                ) {
                    PlaylistCoverArt(
                        tracks = tracks,
                        contentDescription = playlistTitle,
                        cornerRadius = 18.dp,
                        coverConfig = draftConfig,
                        fallbackIcon = fallbackIcon,
                        fallbackGradient = listOf(
                            heroAccent.copy(alpha = 0.85f),
                            AuraViolet.copy(alpha = 0.78f),
                            Color(0xFF181426)
                        ),
                        modifier = Modifier
                            .size(116.dp)
                            .testTag("playlist_cover_live_preview")
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(heroAccent.copy(alpha = 0.16f))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = selectedMode.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = heroAccent
                        )
                    }
                    Text(
                        text = selectedMode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val previewSummary = when {
                        liveResolved.slots.isEmpty() -> "当前歌单暂无带封面的曲目"
                        liveResolved.renderAsSingle -> "当前预览：${liveResolved.slots.first().title}"
                        else -> "已合成 ${liveResolved.slots.size} 张曲目封面"
                    }
                    Text(
                        text = previewSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = adaptiveGoldColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 4 Mode Selector Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(PlaylistCoverMode.entries) { mode ->
                    val active = selectedMode == mode
                    val modeIcon = when (mode) {
                        PlaylistCoverMode.AUTO_COLLAGE -> Icons.Rounded.GridView
                        PlaylistCoverMode.SINGLE_TRACK -> Icons.Rounded.Image
                        PlaylistCoverMode.CUSTOM_COLLAGE -> Icons.Rounded.DashboardCustomize
                        PlaylistCoverMode.RANDOM_COLLAGE -> Icons.Rounded.Shuffle
                    }
                    FilterChip(
                        selected = active,
                        onClick = {
                            selectedMode = mode
                            if (mode == PlaylistCoverMode.RANDOM_COLLAGE && randomSeed == 0L) {
                                randomSeed = System.currentTimeMillis()
                            } else if (mode == PlaylistCoverMode.CUSTOM_COLLAGE && customQuadIds.isEmpty()) {
                                customQuadIds = distinctArtTracks.take(4).map { it.id }
                            } else if (mode == PlaylistCoverMode.SINGLE_TRACK && selectedSingleId == null) {
                                selectedSingleId = tracksWithArt.firstOrNull()?.id
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = mode.shortLabel,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = heroAccent.copy(alpha = 0.20f),
                            selectedLabelColor = heroAccent,
                            selectedLeadingIconColor = heroAccent
                        ),
                        modifier = Modifier.testTag("cover_mode_${mode.name.lowercase()}")
                    )
                }
            }

            // Mode-Specific Interactive Controls
            when (selectedMode) {
                PlaylistCoverMode.AUTO_COLLAGE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = EmeraldLossless,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "自动规则说明",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "当歌单内包含 4 张及以上不同曲目封面时，自动按曲目顺序拼贴 2×2 四宫格；不足 4 张不同封面时自动展示首张大图。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PlaylistCoverMode.RANDOM_COLLAGE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    var next = System.nanoTime()
                                    if (next == randomSeed) next += 37L
                                    randomSeed = next
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = heroAccent,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cover_reroll_random_four")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("换一批随机四首", maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }
                            if (liveResolved.slots.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        customQuadIds = liveResolved.slots.map { it.trackId }.take(4)
                                        selectedMode = PlaylistCoverMode.CUSTOM_COLLAGE
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.testTag("cover_convert_random_to_custom")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("微调这四首", maxLines = 1)
                                }
                            }
                        }

                        if (liveResolved.slots.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "当前随机命中的封面顺序",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                liveResolved.slots.forEachIndexed { idx, slot ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(heroAccent.copy(alpha = 0.20f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = heroAccent
                                            )
                                        }
                                        TrackArtwork(
                                            artworkPath = slot.artworkPath,
                                            albumArtUri = slot.albumArtUri,
                                            contentDescription = slot.title,
                                            cornerRadius = 8.dp,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = slot.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = slotPositionLabels.getOrElse(idx) { "" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PlaylistCoverMode.SINGLE_TRACK -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "点选一首曲目作为单曲大图封面（共 ${tracksWithArt.size} 首含封面）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(tracks, key = { it.id }) { track ->
                                val hasArt = track.hasArtwork()
                                val selected = track.id == selectedSingleId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) heroAccent.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                        .border(
                                            width = if (selected) 1.dp else 0.5.dp,
                                            color = if (selected) heroAccent else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable(enabled = hasArt) { selectedSingleId = track.id }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("cover_pick_single_${track.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TrackArtwork(
                                        artworkPath = track.artworkPath,
                                        albumArtUri = track.albumArtUri,
                                        contentDescription = track.title,
                                        cornerRadius = 10.dp,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (hasArt) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (hasArt) "${track.artist} · ${track.album}" else "无内嵌封面图",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "已选中",
                                            tint = heroAccent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PlaylistCoverMode.CUSTOM_COLLAGE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "按点击顺序排布（已选 ${customQuadIds.size}/4）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(heroAccent.copy(alpha = 0.16f))
                                        .clickable(enabled = tracksWithArt.isNotEmpty()) {
                                            customQuadIds = pickRandomFourIds()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .testTag("cover_custom_shuffle_four")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = null,
                                            tint = heroAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "随机四首",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = heroAccent
                                        )
                                    }
                                }
                                if (customQuadIds.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .clickable { customQuadIds = emptyList() }
                                            .padding(horizontal = 9.dp, vertical = 5.dp)
                                            .testTag("cover_custom_clear")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "清空",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(tracks, key = { it.id }) { track ->
                                val hasArt = track.hasArtwork()
                                val slotIdx = customQuadIds.indexOf(track.id)
                                val isPicked = slotIdx >= 0
                                val canAdd = isPicked || customQuadIds.size < 4
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isPicked) heroAccent.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                        .border(
                                            width = if (isPicked) 1.dp else 0.5.dp,
                                            color = if (isPicked) heroAccent else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable(enabled = hasArt && canAdd) {
                                            customQuadIds = if (isPicked) {
                                                customQuadIds.filterNot { it == track.id }
                                            } else {
                                                (customQuadIds + track.id).take(4)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("cover_pick_quad_${track.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TrackArtwork(
                                        artworkPath = track.artworkPath,
                                        albumArtUri = track.albumArtUri,
                                        contentDescription = track.title,
                                        cornerRadius = 10.dp,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                            color = if (hasArt) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (hasArt) "${track.artist} · ${track.album}" else "无内嵌封面图",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isPicked) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = slotPositionLabels.getOrElse(slotIdx) { "" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = heroAccent
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(heroAccent),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${slotIdx + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Bottom Confirm / Cancel Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                val canSave = when (selectedMode) {
                    PlaylistCoverMode.AUTO_COLLAGE -> true
                    PlaylistCoverMode.SINGLE_TRACK -> selectedSingleId != null
                    PlaylistCoverMode.CUSTOM_COLLAGE -> customQuadIds.isNotEmpty()
                    PlaylistCoverMode.RANDOM_COLLAGE -> tracksWithArt.isNotEmpty()
                }
                Button(
                    onClick = {
                        onSave(if (draftConfig.isDefault) null else draftConfig)
                        onDismiss()
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = heroAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("playlist_cover_save_button")
                ) {
                    Text("应用此封面", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
