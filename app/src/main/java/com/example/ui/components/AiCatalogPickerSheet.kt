package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AiCatalogAttachmentPrefs
import com.example.model.Track
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.adaptiveGoldColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCatalogPickerSheet(
    musicTracks: List<Track>,
    currentTrackId: String?,
    attachCurrentTrackContext: Boolean,
    initialPrefs: AiCatalogAttachmentPrefs,
    onSaveAndApply: (AiCatalogAttachmentPrefs) -> Unit,
    onDismiss: () -> Unit
) {
    val allIds = remember(musicTracks) { musicTracks.map { it.id }.toSet() }
    var enabled by remember(initialPrefs) { mutableStateOf(initialPrefs.enabled) }
    var selectedIds by remember(initialPrefs, allIds) {
        mutableStateOf(
            if (!initialPrefs.hasCustomSelection) {
                allIds
            } else {
                initialPrefs.selectedTrackIds.intersect(allIds)
            }
        )
    }
    var lyricIds by remember(initialPrefs, allIds) {
        mutableStateOf(initialPrefs.lyricTrackIds.intersect(allIds))
    }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = remember(musicTracks, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) musicTracks
        else musicTracks.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }

    val effectiveLyricCount = remember(selectedIds, lyricIds, currentTrackId, attachCurrentTrackContext) {
        val explicit = lyricIds.intersect(selectedIds)
        val playingIncluded = currentTrackId != null && attachCurrentTrackContext && currentTrackId in selectedIds
        (if (playingIncluded) explicit + currentTrackId else explicit).size
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RubyAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = RubyAccent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "选择附带的音乐曲库清单",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "清单与歌词开关将自动记住，下次无需重选",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            // Master Switch Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "对话时发送已选曲库清单",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "已选 ${selectedIds.size}/${musicTracks.size} 首 · 附带歌词 $effectiveLyricCount 首（后台传输，不刷屏气泡）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    modifier = Modifier.testTag("ai_catalog_master_switch")
                )
            }

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("筛选歌名、歌手或专辑...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清空搜索", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Quick Batch Operation Strip: 全选 / 反选 / 清空 / 全部带词 / 全部去词
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                val visibleIds = filteredTracks.map { it.id }.toSet()
                QuickActionChip(
                    label = "全选 (${visibleIds.size})",
                    highlighted = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds },
                    testTag = "ai_catalog_select_all",
                    onClick = {
                        selectedIds = selectedIds + visibleIds
                        enabled = true
                    }
                )
                QuickActionChip(
                    label = "反选",
                    highlighted = false,
                    testTag = "ai_catalog_invert",
                    onClick = {
                        val toggled = visibleIds.mapNotNull { id -> if (id in selectedIds) null else id }.toSet()
                        selectedIds = (selectedIds - visibleIds) + toggled
                        if (selectedIds.isNotEmpty()) enabled = true
                    }
                )
                QuickActionChip(
                    label = "清空已选",
                    highlighted = false,
                    testTag = "ai_catalog_clear",
                    onClick = {
                        selectedIds = selectedIds - visibleIds
                        lyricIds = lyricIds - visibleIds
                    }
                )
                QuickActionChip(
                    label = "已选全带歌词",
                    highlighted = selectedIds.isNotEmpty() && selectedIds.all { it in lyricIds },
                    testTag = "ai_catalog_lyrics_all",
                    onClick = { lyricIds = lyricIds + selectedIds }
                )
                QuickActionChip(
                    label = "全部不带歌词",
                    highlighted = lyricIds.isEmpty(),
                    testTag = "ai_catalog_lyrics_none",
                    onClick = { lyricIds = emptySet() }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Track Selection List with per-song Lyrics toggle
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTracks, key = { it.id }) { track ->
                    val isSelected = track.id in selectedIds
                    val isPlayingHere = track.id == currentTrackId && attachCurrentTrackContext
                    val explicitLyricOn = track.id in lyricIds
                    val effectiveLyricOn = explicitLyricOn || (isSelected && isPlayingHere)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) RubyAccent.copy(alpha = 0.09f)
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .border(
                                width = if (isSelected) 0.9.dp else 0.5.dp,
                                color = if (isSelected) RubyAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (isSelected) {
                                    selectedIds = selectedIds - track.id
                                } else {
                                    selectedIds = selectedIds + track.id
                                    enabled = true
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) RubyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        TrackArtwork(
                            artworkPath = track.artworkPath,
                            albumArtUri = track.albumArtUri,
                            contentDescription = track.title,
                            modifier = Modifier.size(40.dp),
                            cornerRadius = 9.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isPlayingHere) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(adaptiveGoldColor().copy(alpha = 0.22f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "正在播放",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = adaptiveGoldColor(),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isSelected && isPlayingHere) {
                                    "${track.artist} · 歌词将在清单内统一附带不重复"
                                } else {
                                    "${track.artist} · ${track.album}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Per-song Lyrics Attachment Toggle Pill (default OFF)
                        if (isSelected) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (effectiveLyricOn) EmeraldLossless.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .border(
                                        width = 0.7.dp,
                                        color = if (effectiveLyricOn) EmeraldLossless.copy(alpha = 0.65f)
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .clickable {
                                        lyricIds = if (explicitLyricOn) lyricIds - track.id else lyricIds + track.id
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "附带歌词",
                                    tint = if (effectiveLyricOn) EmeraldLossless else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = when {
                                        isPlayingHere && !explicitLyricOn -> "自动带词"
                                        explicitLyricOn -> "带歌词"
                                        else -> "不含词"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (effectiveLyricOn) FontWeight.Bold else FontWeight.Normal,
                                    color = if (effectiveLyricOn) EmeraldLossless else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Confirm & Remember Button
            Button(
                onClick = {
                    val finalEnabled = enabled && selectedIds.isNotEmpty()
                    onSaveAndApply(
                        AiCatalogAttachmentPrefs(
                            enabled = finalEnabled,
                            hasCustomSelection = true,
                            selectedTrackIds = selectedIds,
                            lyricTrackIds = lyricIds.intersect(selectedIds)
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RubyAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_catalog_save_button")
            ) {
                Text(
                    text = if (enabled && selectedIds.isNotEmpty()) {
                        "记住并附带 ${selectedIds.size} 首曲目（含 $effectiveLyricCount 首歌词）"
                    } else {
                        "保存清单配置（暂不附带曲库）"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    highlighted: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (highlighted) RubyAccent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .border(
                width = 0.7.dp,
                color = if (highlighted) RubyAccent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = if (highlighted) RubyAccent else MaterialTheme.colorScheme.onSurface
        )
    }
}
