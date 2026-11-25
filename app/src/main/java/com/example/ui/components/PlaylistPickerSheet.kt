package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PlaylistWithCount
import com.example.model.Track
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldLossless

@Composable
fun BatchPlaylistPickerDialog(
    trackCount: Int,
    playlists: List<PlaylistWithCount>,
    onSelectPlaylist: (Long) -> Unit,
    onCreateNewPlaylist: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateInline by remember { mutableStateOf(playlists.isEmpty()) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "将 $trackCount 首曲目加入歌单",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Quick Create New Playlist trigger / inline form
                if (!showCreateInline) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .border(
                                0.75.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { showCreateInline = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("picker_new_playlist_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "新建歌单并一键加入",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("新歌单名称") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("picker_new_playlist_name")
                        )
                        OutlinedTextField(
                            value = newDesc,
                            onValueChange = { newDesc = it },
                            label = { Text("简介（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (playlists.isNotEmpty()) {
                                TextButton(onClick = { showCreateInline = false }) {
                                    Text("取消新建")
                                }
                            }
                            Button(
                                onClick = {
                                    if (newName.isNotBlank()) {
                                        onCreateNewPlaylist(newName, newDesc)
                                        onDismiss()
                                    }
                                },
                                enabled = newName.isNotBlank(),
                                modifier = Modifier.testTag("picker_create_confirm")
                            ) {
                                Text("创建并加入")
                            }
                        }
                    }
                }

                if (playlists.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists, key = { it.playlist.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable {
                                        onSelectPlaylist(item.playlist.id)
                                        onDismiss()
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackArtwork(
                                    artworkPath = item.coverArtworkPath,
                                    albumArtUri = null,
                                    contentDescription = item.playlist.name,
                                    cornerRadius = 10.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.playlist.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.trackCount} 首曲目",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = ChampagneGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun LibraryTrackSelectorDialog(
    playlistName: String,
    allTracks: List<Track>,
    existingTrackIds: Set<String>,
    onConfirmAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filtered = remember(allTracks, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allTracks
        else allTracks.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }
    val addableFilteredIds = remember(filtered, existingTrackIds) {
        filtered.filterNot { it.id in existingTrackIds }.map { it.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("向「$playlistName」添加曲目", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "已选 ${selectedIds.size} 首新曲目",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChampagneGold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索曲库...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = addableFilteredIds.isNotEmpty() && addableFilteredIds.all { it in selectedIds },
                        onClick = {
                            selectedIds = if (addableFilteredIds.all { it in selectedIds }) {
                                selectedIds - addableFilteredIds.toSet()
                            } else {
                                selectedIds + addableFilteredIds
                            }
                        },
                        label = { Text("全选未收录 (${addableFilteredIds.size})") }
                    )
                    if (selectedIds.isNotEmpty()) {
                        FilterChip(
                            selected = false,
                            onClick = { selectedIds = emptySet() },
                            label = { Text("清空") }
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "曲库中暂无匹配歌曲",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.id }) { track ->
                            val alreadyIn = track.id in existingTrackIds
                            val isSelected = track.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceContainer
                                    )
                                    .clickable(enabled = !alreadyIn) {
                                        selectedIds = if (isSelected) selectedIds - track.id else selectedIds + track.id
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (alreadyIn) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "已在歌单",
                                        tint = EmeraldLossless,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + track.id else selectedIds - track.id
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (alreadyIn) "${track.artist} · 已在歌单" else track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (alreadyIn) EmeraldLossless else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmAdd(selectedIds.toList())
                    onDismiss()
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.testTag("selector_confirm_add")
            ) {
                Text("加入 (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
