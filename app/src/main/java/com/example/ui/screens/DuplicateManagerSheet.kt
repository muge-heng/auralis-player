package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.scanner.DuplicateTrackGroup
import com.example.data.scanner.TrackDeduplicator
import com.example.model.Track
import com.example.model.formatDuration
import com.example.ui.components.QualityBadgePill
import com.example.ui.components.TrackArtwork
import com.example.ui.theme.AuraViolet
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent

private enum class DuplicateFilterTab(val label: String) {
    ALL("全部重复"),
    MUSIC_CONFLICT("音乐区多版本"),
    QUALITY_DIFF("不同音质对比")
}

@Composable
fun DuplicateManagerSheet(
    duplicateGroups: List<DuplicateTrackGroup>,
    onPlayPreviewTrack: (Track, List<Track>) -> Unit,
    onResolveGroupKeepOnly: (DuplicateTrackGroup, String) -> Unit,
    onResolveGroupDemoteOthers: (DuplicateTrackGroup, String) -> Unit,
    onResolveAllKeepHighestQuality: () -> Unit,
    onResolveAllDemoteLowerToGeneral: () -> Unit,
    onResolveExactQualityOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    var activeFilter by remember { mutableStateOf(DuplicateFilterTab.ALL) }
    var showConfirmBulkDelete by remember { mutableStateOf(false) }

    // Track per-group user-selected keeper ID (defaults to highest-quality bestTrack)
    val selectedKeeperByGroup = remember { mutableStateMapOf<String, String>() }

    val filteredGroups = remember(duplicateGroups, activeFilter) {
        when (activeFilter) {
            DuplicateFilterTab.ALL -> duplicateGroups
            DuplicateFilterTab.MUSIC_CONFLICT -> duplicateGroups.filter { it.musicZoneCount >= 2 }
            DuplicateFilterTab.QUALITY_DIFF -> duplicateGroups.filter { it.hasQualityDifference }
        }
    }

    val totalRedundantCount = remember(duplicateGroups) {
        duplicateGroups.sumOf { it.redundantCount }
    }
    val exactQualityRedundantCount = remember(duplicateGroups) {
        duplicateGroups.sumOf { group ->
            val kept = mutableListOf<Track>()
            var extra = 0
            group.tracks.forEach { t ->
                if (kept.any { TrackDeduplicator.isEquivalentQuality(it, t) }) extra++ else kept.add(t)
            }
            extra
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("duplicate_manager_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "智能查重与音质冲突清理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (duplicateGroups.isEmpty()) {
                            "全库无重复音频冲突"
                        } else {
                            "检测到 ${duplicateGroups.size} 组重复曲目 · 共 $totalRedundantCount 个冗余副本"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (duplicateGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(EmeraldLossless.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = EmeraldLossless,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Text(
                            text = "唱片馆非常纯净",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "未检测到重复曲目或音质冲突。后续导入或扫描时，Auralis 也会自动跳过同音质重复，并在遇到不同音质时弹窗让你决策。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RubyAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("duplicate_empty_done")
                        ) {
                            Text("返回曲库")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // One-Tap Smart Resolution Hub
                    item(key = "smart_hub") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = ChampagneGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "一键智能治理方案",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "自动保留红心收藏、播放次数、自定义歌词与歌单关联，绝不删除手机本地存储文件。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            SmartActionTile(
                                icon = Icons.Rounded.HighQuality,
                                iconTint = RubyAccent,
                                title = "一键仅留最高音质（移除 $totalRedundantCount 个副本）",
                                subtitle = "每组自动选出 DSD / Hi-Res / 无损 / 高码率最佳版本，其余从曲库移除",
                                highlighted = true,
                                testTag = "dup_bulk_keep_highest",
                                onClick = { showConfirmBulkDelete = true }
                            )

                            SmartActionTile(
                                icon = Icons.Rounded.GraphicEq,
                                iconTint = AuraViolet,
                                title = "高质留音乐区 · 其余副本转入普通音频区",
                                subtitle = "零移除分流：音乐分类只保留 1 个最佳版，其余副本归入普通音频区备用",
                                highlighted = false,
                                testTag = "dup_bulk_demote_general",
                                onClick = onResolveAllDemoteLowerToGeneral
                            )

                            if (exactQualityRedundantCount > 0) {
                                SmartActionTile(
                                    icon = Icons.Rounded.CleaningServices,
                                    iconTint = EmeraldLossless,
                                    title = "仅清理同音质冗余副本（$exactQualityRedundantCount 个）",
                                    subtitle = "只移除完全同档音质的重复条目，保留 FLAC 与 MP3 等不同音质供手动挑选",
                                    highlighted = false,
                                    testTag = "dup_bulk_exact_only",
                                    onClick = onResolveExactQualityOnly
                                )
                            }
                        }
                    }

                    // Filter Tabs
                    item(key = "filter_tabs") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DuplicateFilterTab.entries.forEach { tab ->
                                val count = when (tab) {
                                    DuplicateFilterTab.ALL -> duplicateGroups.size
                                    DuplicateFilterTab.MUSIC_CONFLICT -> duplicateGroups.count { it.musicZoneCount >= 2 }
                                    DuplicateFilterTab.QUALITY_DIFF -> duplicateGroups.count { it.hasQualityDifference }
                                }
                                val selected = activeFilter == tab
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (selected) RubyAccent else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .border(
                                            0.7.dp,
                                            if (selected) RubyAccent else MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(999.dp)
                                        )
                                        .clickable { activeFilter = tab }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .testTag("dup_filter_${tab.name.lowercase()}")
                                ) {
                                    Text(
                                        text = "${tab.label} ($count)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Per-Group Cards
                    items(filteredGroups, key = { it.groupId }) { group ->
                        val selectedKeeperId = selectedKeeperByGroup[group.groupId]
                            ?.takeIf { id -> group.tracks.any { it.id == id } }
                            ?: group.bestTrack.id

                        DuplicateGroupCard(
                            group = group,
                            selectedKeeperId = selectedKeeperId,
                            onSelectKeeper = { trackId ->
                                selectedKeeperByGroup[group.groupId] = trackId
                            },
                            onPreviewTrack = { track ->
                                onPlayPreviewTrack(track, group.tracks)
                            },
                            onKeepOnlySelected = {
                                onResolveGroupKeepOnly(group, selectedKeeperId)
                            },
                            onKeepMusicAndDemoteOthers = {
                                onResolveGroupDemoteOthers(group, selectedKeeperId)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showConfirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmBulkDelete = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = RubyAccent
                )
            },
            title = { Text("一键仅留最高音质？") },
            text = {
                Text(
                    "将为全部 ${duplicateGroups.size} 组重复曲目保留音质最佳的版本，并从曲库列表移出 $totalRedundantCount 个较低/重复版本。\n\n• 收藏、播放次数、歌单归属与自定义歌词会自动合并至保留版本\n• 手机本地存储中的原始音频文件不会被删除"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmBulkDelete = false
                        onResolveAllKeepHighestQuality()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RubyAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("dup_confirm_bulk_keep_highest")
                ) {
                    Text("确认智能去重")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmBulkDelete = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SmartActionTile(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (highlighted) RubyAccent.copy(alpha = 0.11f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .border(
                width = 0.9.dp,
                color = if (highlighted) RubyAccent.copy(alpha = 0.48f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) RubyAccent else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateTrackGroup,
    selectedKeeperId: String,
    onSelectKeeper: (String) -> Unit,
    onPreviewTrack: (Track) -> Unit,
    onKeepOnlySelected: () -> Unit,
    onKeepMusicAndDemoteOthers: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Group Title Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrackArtwork(
                artworkPath = group.bestTrack.artworkPath,
                albumArtUri = group.bestTrack.albumArtUri,
                contentDescription = group.displayTitle,
                modifier = Modifier.size(46.dp),
                cornerRadius = 12.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = group.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (group.hasQualityDifference) ChampagneGold.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (group.hasQualityDifference) "${group.tracks.size} 版 · 音质不同" else "${group.tracks.size} 版 · 同音质",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (group.hasQualityDifference) Color(0xFFB37828) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "${group.displayArtist} · 音乐区 ${group.musicZoneCount} 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Track Variants List
        group.tracks.forEachIndexed { idx, track ->
            val isSelected = track.id == selectedKeeperId
            val isTopQuality = idx == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) RubyAccent.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.6.dp,
                        color = if (isSelected) RubyAccent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectKeeper(track.id) }
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) RubyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QualityBadgePill(
                            label = track.qualityBadge,
                            isHiRes = track.isHiRes,
                            isLossless = track.isLossless
                        )
                        if (isTopQuality) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(EmeraldLossless.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "最佳音质",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldLossless
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (track.isGeneralAudio) AuraViolet.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = if (track.isGeneralAudio) "普通音频区" else "音乐区",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (track.isGeneralAudio) AuraViolet else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "${TrackDeduplicator.qualitySummaryLine(track)} · ${track.durationMs.formatDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.folderPath} / ${track.fileName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onPreviewTrack(track) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "试听该版本",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Per-Group Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onKeepOnlySelected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RubyAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("仅保留选中", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            OutlinedButton(
                onClick = onKeepMusicAndDemoteOthers,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1.25f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderCopy,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("选中留音乐 · 其余转普通", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
