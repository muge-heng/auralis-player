package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PlaylistCoverConfig
import com.example.model.PlaylistCoverKeys
import com.example.model.PlaylistWithCount
import com.example.model.Track
import com.example.ui.components.ArtistAutoPlaylist
import com.example.ui.components.BatchPlaylistPickerDialog
import com.example.ui.components.LibraryTrackSelectorDialog
import com.example.ui.components.PlaylistCoverArt
import com.example.ui.components.PlaylistCoverEditorSheet
import com.example.ui.components.bouncyClickable
import com.example.ui.components.buildArtistAutoPlaylists
import com.example.ui.components.formatCollectionTotalDuration
import com.example.ui.theme.AuraViolet
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent

private sealed interface ActiveCollectionKey {
    data object Favorites : ActiveCollectionKey
    data object ArtistsDirectory : ActiveCollectionKey
    data object Recent : ActiveCollectionKey
    data object MostPlayed : ActiveCollectionKey
    data class Artist(val artistName: String) : ActiveCollectionKey
    data class Custom(val playlistId: Long) : ActiveCollectionKey
}

private fun ActiveCollectionKey.coverStorageKey(): String? = when (this) {
    ActiveCollectionKey.Favorites -> PlaylistCoverKeys.FAVORITES
    ActiveCollectionKey.Recent -> PlaylistCoverKeys.RECENT
    ActiveCollectionKey.MostPlayed -> PlaylistCoverKeys.MOST_PLAYED
    is ActiveCollectionKey.Artist -> PlaylistCoverKeys.artist(artistName)
    is ActiveCollectionKey.Custom -> PlaylistCoverKeys.custom(playlistId)
    ActiveCollectionKey.ArtistsDirectory -> null
}

private data class PlaylistCoverEditorTarget(
    val storageKey: String,
    val title: String,
    val tracks: List<Track>,
    val fallbackIcon: ImageVector,
    val heroAccent: Color
)

@Composable
fun PlaylistsScreen(
    allTracks: List<Track>,
    favoriteTracks: List<Track>,
    recentlyPlayed: List<Track>,
    mostPlayed: List<Track>,
    playlists: List<PlaylistWithCount>,
    playlistTracksMap: Map<Long, List<Track>>,
    playlistCoverConfigs: Map<String, PlaylistCoverConfig> = emptyMap(),
    currentTrackId: String?,
    isPlaying: Boolean,
    onCreatePlaylist: (String, String) -> Unit,
    onCreatePlaylistWithTracks: (String, String, List<String>) -> Unit,
    onRenamePlaylist: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onClearPlaylist: (Long) -> Unit,
    onUpdatePlaylistCoverConfig: (String, PlaylistCoverConfig?) -> Unit = { _, _ -> },
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAppendToQueue: (List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onBatchFavorite: (List<String>, Boolean) -> Unit,
    onAddToPlaylist: (Long, Track) -> Unit,
    onBatchAddToPlaylist: (Long, List<String>) -> Unit,
    onRemoveFromPlaylist: (Long, List<String>) -> Unit,
    onMovePlaylistTrack: (Long, Int, Int) -> Unit,
    onClearRecentHistory: () -> Unit,
    onRemoveFromRecent: (List<String>) -> Unit,
    onClearMostPlayed: () -> Unit,
    contentBottomPadding: Int
) {
    val artistCollections = remember(allTracks) { buildArtistAutoPlaylists(allTracks) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }

    var selectedKey by remember { mutableStateOf<ActiveCollectionKey?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showTrackSelectorForPlaylistId by remember { mutableStateOf<Long?>(null) }
    var pendingPlaylistTargetIds by remember { mutableStateOf<List<String>?>(null) }
    var renameTarget by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var renameName by remember { mutableStateOf("") }
    var renameDesc by remember { mutableStateOf("") }
    var saveSmartAsPlaylistDefaults by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var saveSmartName by remember { mutableStateOf("") }
    var pendingConfirmAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var coverEditorTarget by remember { mutableStateOf<PlaylistCoverEditorTarget?>(null) }

    when (val activeKey = selectedKey) {
        null -> {
            PlaylistsOverviewContent(
                favoriteTracks = favoriteTracks,
                artistCollections = artistCollections,
                recentlyPlayed = recentlyPlayed,
                mostPlayed = mostPlayed,
                playlists = playlists,
                playlistTracksMap = playlistTracksMap,
                playlistCoverConfigs = playlistCoverConfigs,
                contentBottomPadding = contentBottomPadding,
                onCreatePlaylistClick = { showCreateDialog = true },
                onSelectCollection = { key ->
                    isSelectionMode = false
                    selectedIds = emptySet()
                    selectedKey = key
                },
                onPlayAll = onPlayAll,
                onAppendToQueue = onAppendToQueue,
                onQuickAddTracks = { plId -> showTrackSelectorForPlaylistId = plId },
                onEditPlaylist = { item ->
                    renameTarget = item
                    renameName = item.playlist.name
                    renameDesc = item.playlist.description
                },
                onCustomizeCover = { item, plTracks ->
                    coverEditorTarget = PlaylistCoverEditorTarget(
                        storageKey = PlaylistCoverKeys.custom(item.playlist.id),
                        title = item.playlist.name,
                        tracks = plTracks,
                        fallbackIcon = Icons.Rounded.Album,
                        heroAccent = RubyAccent
                    )
                },
                onRequestClearPlaylist = { item ->
                    pendingConfirmAction = "确定清空「${item.playlist.name}」内的全部曲目吗？" to {
                        onClearPlaylist(item.playlist.id)
                    }
                },
                onRequestDeletePlaylist = { item ->
                    pendingConfirmAction = "确定永久删除歌单「${item.playlist.name}」吗？（本地歌曲文件不会被删除）" to {
                        onDeletePlaylist(item.playlist.id)
                    }
                }
            )
        }

        ActiveCollectionKey.ArtistsDirectory -> {
            BackHandler { selectedKey = null }
            ArtistsDirectoryView(
                artists = artistCollections,
                playlistCoverConfigs = playlistCoverConfigs,
                contentBottomPadding = contentBottomPadding,
                onBack = { selectedKey = null },
                onOpenArtist = { artistName ->
                    isSelectionMode = false
                    selectedIds = emptySet()
                    selectedKey = ActiveCollectionKey.Artist(artistName)
                },
                onPlayArtist = { tracks -> onPlayAll(tracks, false) }
            )
        }

        else -> {
            val customPlaylist = (activeKey as? ActiveCollectionKey.Custom)?.let { key ->
                playlists.firstOrNull { it.playlist.id == key.playlistId }
            }
            val artistCollection = (activeKey as? ActiveCollectionKey.Artist)?.let { key ->
                artistCollections.firstOrNull { it.artistName == key.artistName }
            }
            if ((activeKey is ActiveCollectionKey.Custom && customPlaylist == null) ||
                (activeKey is ActiveCollectionKey.Artist && artistCollection == null)
            ) {
                selectedKey = if (activeKey is ActiveCollectionKey.Artist) {
                    ActiveCollectionKey.ArtistsDirectory
                } else {
                    null
                }
                isSelectionMode = false
                selectedIds = emptySet()
            } else {
                val navigateBack: () -> Unit = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                        selectedIds = emptySet()
                    } else {
                        selectedKey = if (activeKey is ActiveCollectionKey.Artist) {
                            ActiveCollectionKey.ArtistsDirectory
                        } else {
                            null
                        }
                    }
                }
                BackHandler(onBack = navigateBack)

                val collectionBadge = when (activeKey) {
                    ActiveCollectionKey.Favorites -> "红心精选"
                    ActiveCollectionKey.Recent -> "聆听足迹"
                    ActiveCollectionKey.MostPlayed -> "高频循环"
                    is ActiveCollectionKey.Artist -> "歌手自动歌单"
                    is ActiveCollectionKey.Custom -> "自建歌单"
                    ActiveCollectionKey.ArtistsDirectory -> ""
                }
                val collectionTitle = when (activeKey) {
                    ActiveCollectionKey.Favorites -> "红心挚爱"
                    ActiveCollectionKey.Recent -> "最近聆听"
                    ActiveCollectionKey.MostPlayed -> "高频循环"
                    is ActiveCollectionKey.Artist -> artistCollection!!.artistName
                    is ActiveCollectionKey.Custom -> customPlaylist!!.playlist.name
                    ActiveCollectionKey.ArtistsDirectory -> ""
                }
                val collectionDesc = when (activeKey) {
                    ActiveCollectionKey.Favorites -> "你手动点亮红心收藏的所有高保真曲目"
                    ActiveCollectionKey.Recent -> "按最近聆听时间自动汇总的音乐记忆"
                    ActiveCollectionKey.MostPlayed -> "按历史循环播放次数自动排列的心头好"
                    is ActiveCollectionKey.Artist -> {
                        val albums = artistCollection!!.albumCount
                        if (albums > 0) {
                            "自动汇总「${artistCollection.artistName}」在本地曲库中的全部作品 · 涵盖 $albums 张专辑"
                        } else {
                            "自动汇总「${artistCollection.artistName}」在本地曲库中的全部作品"
                        }
                    }
                    is ActiveCollectionKey.Custom -> customPlaylist!!.playlist.description
                    ActiveCollectionKey.ArtistsDirectory -> ""
                }
                val collectionTracks = when (activeKey) {
                    ActiveCollectionKey.Favorites -> favoriteTracks
                    ActiveCollectionKey.Recent -> recentlyPlayed
                    ActiveCollectionKey.MostPlayed -> mostPlayed
                    is ActiveCollectionKey.Artist -> artistCollection!!.tracks
                    is ActiveCollectionKey.Custom -> playlistTracksMap[activeKey.playlistId].orEmpty()
                    ActiveCollectionKey.ArtistsDirectory -> emptyList()
                }
                val heroAccent = when (activeKey) {
                    ActiveCollectionKey.Favorites -> RubyAccent
                    ActiveCollectionKey.Recent -> AuraViolet
                    ActiveCollectionKey.MostPlayed -> ChampagneGold
                    is ActiveCollectionKey.Artist -> EmeraldLossless
                    is ActiveCollectionKey.Custom -> RubyAccent
                    ActiveCollectionKey.ArtistsDirectory -> RubyAccent
                }
                val fallbackIcon = when (activeKey) {
                    ActiveCollectionKey.Favorites -> Icons.Rounded.Favorite
                    ActiveCollectionKey.Recent -> Icons.Rounded.History
                    ActiveCollectionKey.MostPlayed -> Icons.Rounded.LocalFireDepartment
                    is ActiveCollectionKey.Artist -> Icons.Rounded.Person
                    is ActiveCollectionKey.Custom -> Icons.Rounded.Album
                    ActiveCollectionKey.ArtistsDirectory -> Icons.Rounded.Person
                }
                val losslessCount = remember(collectionTracks) { collectionTracks.count { it.isLossless } }
                val totalDurationLabel = remember(collectionTracks) { formatCollectionTotalDuration(collectionTracks) }
                val selectedTracks = remember(collectionTracks, selectedIds) {
                    collectionTracks.filter { it.id in selectedIds }
                }
                var detailMenuExpanded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = (contentBottomPadding + if (isSelectionMode) 96 else 28).dp,
                            start = 16.dp,
                            end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Editorial Hero Header with Large Cover Art, Editable Title/Intro, Stats & Balanced Action Bar
                        item(key = "detail_hero_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                heroAccent.copy(alpha = 0.22f),
                                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                                                MaterialTheme.colorScheme.surfaceContainer
                                            )
                                        )
                                    )
                                    .border(
                                        0.75.dp,
                                        heroAccent.copy(alpha = 0.30f),
                                        RoundedCornerShape(28.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Top Navigation & Overflow Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = navigateBack,
                                        modifier = Modifier.testTag("collection_detail_back")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "返回"
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(heroAccent.copy(alpha = 0.16f))
                                            .border(0.6.dp, heroAccent.copy(alpha = 0.42f), RoundedCornerShape(50))
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = collectionBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = heroAccent
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (activeKey is ActiveCollectionKey.Custom && customPlaylist != null) {
                                            IconButton(
                                                onClick = {
                                                    renameTarget = customPlaylist
                                                    renameName = customPlaylist.playlist.name
                                                    renameDesc = customPlaylist.playlist.description
                                                },
                                                modifier = Modifier.testTag("playlist_edit_info_icon")
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Edit,
                                                    contentDescription = "编辑歌单名与简介",
                                                    tint = ChampagneGold
                                                )
                                            }
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { detailMenuExpanded = true },
                                                modifier = Modifier.testTag("collection_detail_more")
                                            ) {
                                                Icon(Icons.Rounded.MoreVert, contentDescription = "管理列表")
                                            }
                                            DropdownMenu(
                                                expanded = detailMenuExpanded,
                                                onDismissRequest = { detailMenuExpanded = false }
                                            ) {
                                                val storageKey = activeKey.coverStorageKey()
                                                if (collectionTracks.isNotEmpty() && storageKey != null) {
                                                    DropdownMenuItem(
                                                        text = { Text("设置歌单封面") },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Rounded.PhotoLibrary,
                                                                contentDescription = null,
                                                                tint = heroAccent
                                                            )
                                                        },
                                                        onClick = {
                                                            detailMenuExpanded = false
                                                            coverEditorTarget = PlaylistCoverEditorTarget(
                                                                storageKey = storageKey,
                                                                title = collectionTitle,
                                                                tracks = collectionTracks,
                                                                fallbackIcon = fallbackIcon,
                                                                heroAccent = heroAccent
                                                            )
                                                        },
                                                        modifier = Modifier.testTag("collection_customize_cover_menu_item")
                                                    )
                                                }
                                                if (collectionTracks.isNotEmpty()) {
                                                    DropdownMenuItem(
                                                        text = { Text("随机播放此列表") },
                                                        onClick = {
                                                            detailMenuExpanded = false
                                                            onPlayAll(collectionTracks, true)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("全部加入待播队列") },
                                                        onClick = {
                                                            detailMenuExpanded = false
                                                            onAppendToQueue(collectionTracks)
                                                        }
                                                    )
                                                }
                                                when (activeKey) {
                                                    is ActiveCollectionKey.Custom -> {
                                                        DropdownMenuItem(
                                                            text = { Text("从曲库批量加歌") },
                                                            onClick = {
                                                                detailMenuExpanded = false
                                                                showTrackSelectorForPlaylistId = activeKey.playlistId
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("编辑歌单名与介绍") },
                                                            onClick = {
                                                                detailMenuExpanded = false
                                                                renameTarget = customPlaylist
                                                                renameName = customPlaylist!!.playlist.name
                                                                renameDesc = customPlaylist.playlist.description
                                                            }
                                                        )
                                                        if (collectionTracks.isNotEmpty()) {
                                                            DropdownMenuItem(
                                                                text = { Text("一键清空歌单", color = RubyAccent) },
                                                                onClick = {
                                                                    detailMenuExpanded = false
                                                                    pendingConfirmAction = "确定清空「$collectionTitle」内的全部曲目吗？" to {
                                                                        onClearPlaylist(activeKey.playlistId)
                                                                    }
                                                                }
                                                            )
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text("删除此歌单", color = RubyAccent) },
                                                            onClick = {
                                                                detailMenuExpanded = false
                                                                pendingConfirmAction = "确定永久删除歌单「$collectionTitle」吗？（本地歌曲文件不会被删除）" to {
                                                                    onDeletePlaylist(activeKey.playlistId)
                                                                    selectedKey = null
                                                                }
                                                            }
                                                        )
                                                    }
                                                    ActiveCollectionKey.Favorites -> {
                                                        if (collectionTracks.isNotEmpty()) {
                                                            DropdownMenuItem(
                                                                text = { Text("取消全部红心", color = RubyAccent) },
                                                                onClick = {
                                                                    detailMenuExpanded = false
                                                                    pendingConfirmAction = "确定取消全部 ${collectionTracks.size} 首曲目的红心收藏吗？" to {
                                                                        onBatchFavorite(collectionTracks.map { it.id }, false)
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                    ActiveCollectionKey.Recent -> {
                                                        if (collectionTracks.isNotEmpty()) {
                                                            DropdownMenuItem(
                                                                text = { Text("清空最近聆听记录", color = RubyAccent) },
                                                                onClick = {
                                                                    detailMenuExpanded = false
                                                                    pendingConfirmAction = "确定清空所有最近聆听历史记录吗？" to {
                                                                        onClearRecentHistory()
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                    ActiveCollectionKey.MostPlayed -> {
                                                        if (collectionTracks.isNotEmpty()) {
                                                            DropdownMenuItem(
                                                                text = { Text("重置循环播放统计", color = RubyAccent) },
                                                                onClick = {
                                                                    detailMenuExpanded = false
                                                                    pendingConfirmAction = "确定重置所有曲目的高频播放次数统计吗？" to {
                                                                        onClearMostPlayed()
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                    is ActiveCollectionKey.Artist -> {
                                                        if (collectionTracks.isNotEmpty()) {
                                                            DropdownMenuItem(
                                                                text = { Text("将此歌手曲目存为歌单") },
                                                                onClick = {
                                                                    detailMenuExpanded = false
                                                                    val defaultTitle = "${collectionTitle}精选"
                                                                    saveSmartName = defaultTitle
                                                                    saveSmartAsPlaylistDefaults = defaultTitle to collectionTracks.map { it.id }
                                                                }
                                                            )
                                                        }
                                                    }
                                                    ActiveCollectionKey.ArtistsDirectory -> Unit
                                                }
                                            }
                                        }
                                    }
                                }

                                // Large Centered Hero Playlist Cover Art
                                val activeCoverConfig = activeKey.coverStorageKey()?.let { playlistCoverConfigs[it] }
                                Box(
                                    modifier = Modifier
                                        .size(196.dp)
                                        .shadow(
                                            elevation = 18.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            ambientColor = heroAccent.copy(alpha = 0.55f),
                                            spotColor = heroAccent.copy(alpha = 0.55f)
                                        )
                                ) {
                                    PlaylistCoverArt(
                                        tracks = collectionTracks,
                                        contentDescription = collectionTitle,
                                        cornerRadius = 24.dp,
                                        coverConfig = activeCoverConfig,
                                        fallbackIcon = fallbackIcon,
                                        fallbackGradient = listOf(
                                            heroAccent.copy(alpha = 0.85f),
                                            AuraViolet.copy(alpha = 0.78f),
                                            Color(0xFF181426)
                                        ),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Title & Multi-line Editable Description
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .then(
                                            if (activeKey is ActiveCollectionKey.Custom && customPlaylist != null) {
                                                Modifier.clickable {
                                                    renameTarget = customPlaylist
                                                    renameName = customPlaylist.playlist.name
                                                    renameDesc = customPlaylist.playlist.description
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = collectionTitle,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (collectionDesc.isNotBlank()) {
                                        Text(
                                            text = collectionDesc,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else if (activeKey is ActiveCollectionKey.Custom) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Edit,
                                                contentDescription = null,
                                                tint = ChampagneGold,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "点击添加歌单介绍",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ChampagneGold
                                            )
                                        }
                                    }
                                }

                                // Acoustic Stats Metadata Strip
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${collectionTracks.size} 首曲目 · $totalDurationLabel",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (losslessCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(EmeraldLossless.copy(alpha = 0.16f))
                                                .border(0.5.dp, EmeraldLossless.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$losslessCount 首无损",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = EmeraldLossless
                                            )
                                        }
                                    }
                                }

                                // Primary Split Action Bar: Play All + Shuffle (never clips or truncates!)
                                if (collectionTracks.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { onPlayAll(collectionTracks, false) },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = RubyAccent,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("collection_play_all")
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("播放全部", fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = { onPlayAll(collectionTracks, true) },
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("collection_shuffle_all")
                                        ) {
                                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("随机播放", fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        }
                                    }
                                }

                                // Secondary Utility Bar (horizontally scrollable, zero clipping)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (collectionTracks.isNotEmpty()) {
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = { onAppendToQueue(collectionTracks) },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                label = { Text("加入队列", maxLines = 1) },
                                                modifier = Modifier.testTag("collection_append_queue")
                                            )
                                        }
                                    }

                                    if (activeKey is ActiveCollectionKey.Custom && customPlaylist != null) {
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = { showTrackSelectorForPlaylistId = activeKey.playlistId },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                label = { Text("快速加歌", maxLines = 1) },
                                                modifier = Modifier.testTag("collection_add_tracks")
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    renameTarget = customPlaylist
                                                    renameName = customPlaylist.playlist.name
                                                    renameDesc = customPlaylist.playlist.description
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                                },
                                                label = { Text("编辑信息", maxLines = 1) },
                                                modifier = Modifier.testTag("collection_edit_info")
                                            )
                                        }
                                    } else if (collectionTracks.isNotEmpty()) {
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    val defaultTitle = "${collectionTitle}精选"
                                                    saveSmartName = defaultTitle
                                                    saveSmartAsPlaylistDefaults = defaultTitle to collectionTracks.map { it.id }
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                label = { Text("存为歌单", maxLines = 1) },
                                                modifier = Modifier.testTag("collection_save_as_playlist")
                                            )
                                        }
                                    }

                                    if (collectionTracks.isNotEmpty()) {
                                        item {
                                            FilterChip(
                                                selected = isSelectionMode,
                                                onClick = {
                                                    isSelectionMode = !isSelectionMode
                                                    if (!isSelectionMode) selectedIds = emptySet()
                                                },
                                                label = { Text(if (isSelectionMode) "完成管理" else "批量管理", maxLines = 1) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = if (isSelectionMode) Icons.Rounded.Close else Icons.Rounded.Checklist,
                                                        contentDescription = "批量管理",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                modifier = Modifier.testTag("collection_batch_manage")
                                            )
                                        }
                                    }
                                }

                                // Quick Selection Helper Strip when in batch management mode
                                if (isSelectionMode && collectionTracks.isNotEmpty()) {
                                    val allSelected = collectionTracks.all { it.id in selectedIds }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        item {
                                            FilterChip(
                                                selected = allSelected,
                                                onClick = {
                                                    selectedIds = if (allSelected) {
                                                        emptySet()
                                                    } else {
                                                        collectionTracks.map { it.id }.toSet()
                                                    }
                                                },
                                                label = { Text(if (allSelected) "取消全选" else "全选 (${collectionTracks.size})") }
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    val allIds = collectionTracks.map { it.id }.toSet()
                                                    selectedIds = allIds - selectedIds
                                                },
                                                label = { Text("反选") }
                                            )
                                        }
                                        item {
                                            Text(
                                                text = "已选 ${selectedTracks.size} 首",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = ChampagneGold,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (collectionTracks.isEmpty()) {
                            item(key = "detail_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = when (activeKey) {
                                                is ActiveCollectionKey.Custom -> "此歌单暂无曲目，可一键从曲库批量选取加入。"
                                                ActiveCollectionKey.Favorites -> "还没有红心曲目，点亮歌曲右侧红心即可自动收录。"
                                                ActiveCollectionKey.Recent -> "暂无近期播放足迹。"
                                                ActiveCollectionKey.MostPlayed -> "多听几首歌曲，这里会自动生成你的高频循环榜。"
                                                is ActiveCollectionKey.Artist -> "该歌手下暂无曲目。"
                                                ActiveCollectionKey.ArtistsDirectory -> ""
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        if (activeKey is ActiveCollectionKey.Custom) {
                                            Button(
                                                onClick = { showTrackSelectorForPlaylistId = activeKey.playlistId },
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Icon(Icons.Rounded.Add, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("立即从曲库添加歌曲")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(collectionTracks, key = { _, t -> t.id }) { index, track ->
                                val isSelected = track.id in selectedIds
                                TrackListItem(
                                    track = track,
                                    isCurrent = track.id == currentTrackId,
                                    isPlaying = isPlaying,
                                    playlists = playlists,
                                    selectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - track.id else selectedIds + track.id
                                        } else {
                                            onPlayTrack(track, collectionTracks)
                                        }
                                    },
                                    onLongClick = {
                                        isSelectionMode = true
                                        selectedIds = selectedIds + track.id
                                    },
                                    onPlayNext = { onPlayNext(track) },
                                    onAppendToQueue = { onAppendToQueue(listOf(track)) },
                                    onToggleFavorite = { onToggleFavorite(track) },
                                    onAddToPlaylist = { plId -> onAddToPlaylist(plId, track) },
                                    onOpenPlaylistPicker = { pendingPlaylistTargetIds = listOf(track.id) },
                                    customExtraMenuItems = { dismiss ->
                                        when (activeKey) {
                                            is ActiveCollectionKey.Custom -> {
                                                if (index > 0) {
                                                    DropdownMenuItem(
                                                        text = { Text("上移一位") },
                                                        onClick = {
                                                            dismiss()
                                                            onMovePlaylistTrack(activeKey.playlistId, index, index - 1)
                                                        }
                                                    )
                                                }
                                                if (index < collectionTracks.lastIndex) {
                                                    DropdownMenuItem(
                                                        text = { Text("下移一位") },
                                                        onClick = {
                                                            dismiss()
                                                            onMovePlaylistTrack(activeKey.playlistId, index, index + 1)
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text("从此歌单移出", color = RubyAccent) },
                                                    onClick = {
                                                        dismiss()
                                                        onRemoveFromPlaylist(activeKey.playlistId, listOf(track.id))
                                                    }
                                                )
                                            }
                                            ActiveCollectionKey.Recent -> {
                                                DropdownMenuItem(
                                                    text = { Text("从最近聆听移出", color = RubyAccent) },
                                                    onClick = {
                                                        dismiss()
                                                        onRemoveFromRecent(listOf(track.id))
                                                    }
                                                )
                                            }
                                            else -> Unit
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Floating One-Click Batch Action Bar for Playlist / Smart Collection / Artist Detail
                    AnimatedVisibility(
                        visible = isSelectionMode && collectionTracks.isNotEmpty(),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            tonalElevation = 8.dp,
                            shadowElevation = 14.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    RoundedCornerShape(22.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val hasSelection = selectedTracks.isNotEmpty()
                                PlaylistBatchAction(
                                    icon = Icons.Rounded.PlayArrow,
                                    label = "播放",
                                    enabled = hasSelection,
                                    onClick = {
                                        onPlayAll(selectedTracks, false)
                                        isSelectionMode = false
                                        selectedIds = emptySet()
                                    },
                                    testTag = "playlist_batch_play"
                                )
                                PlaylistBatchAction(
                                    icon = Icons.Rounded.QueueMusic,
                                    label = "入队列",
                                    enabled = hasSelection,
                                    onClick = {
                                        onAppendToQueue(selectedTracks)
                                        isSelectionMode = false
                                        selectedIds = emptySet()
                                    },
                                    testTag = "playlist_batch_queue"
                                )
                                PlaylistBatchAction(
                                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    label = "加歌单",
                                    enabled = hasSelection,
                                    onClick = { pendingPlaylistTargetIds = selectedTracks.map { it.id } },
                                    testTag = "playlist_batch_add"
                                )
                                when (activeKey) {
                                    is ActiveCollectionKey.Custom -> {
                                        PlaylistBatchAction(
                                            icon = Icons.Rounded.DeleteOutline,
                                            label = "移出歌单",
                                            enabled = hasSelection,
                                            tint = RubyAccent,
                                            onClick = {
                                                onRemoveFromPlaylist(activeKey.playlistId, selectedTracks.map { it.id })
                                                isSelectionMode = false
                                                selectedIds = emptySet()
                                            },
                                            testTag = "playlist_batch_remove"
                                        )
                                    }
                                    ActiveCollectionKey.Favorites -> {
                                        PlaylistBatchAction(
                                            icon = Icons.Rounded.DeleteOutline,
                                            label = "取消红心",
                                            enabled = hasSelection,
                                            tint = RubyAccent,
                                            onClick = {
                                                onBatchFavorite(selectedTracks.map { it.id }, false)
                                                isSelectionMode = false
                                                selectedIds = emptySet()
                                            },
                                            testTag = "playlist_batch_unfav"
                                        )
                                    }
                                    ActiveCollectionKey.Recent -> {
                                        PlaylistBatchAction(
                                            icon = Icons.Rounded.DeleteOutline,
                                            label = "移出记录",
                                            enabled = hasSelection,
                                            tint = RubyAccent,
                                            onClick = {
                                                onRemoveFromRecent(selectedTracks.map { it.id })
                                                isSelectionMode = false
                                                selectedIds = emptySet()
                                            },
                                            testTag = "playlist_batch_remove_recent"
                                        )
                                    }
                                    ActiveCollectionKey.MostPlayed,
                                    is ActiveCollectionKey.Artist -> {
                                        PlaylistBatchAction(
                                            icon = Icons.Rounded.Favorite,
                                            label = "红心",
                                            enabled = hasSelection,
                                            onClick = {
                                                onBatchFavorite(selectedTracks.map { it.id }, true)
                                                isSelectionMode = false
                                                selectedIds = emptySet()
                                            },
                                            testTag = "playlist_batch_fav"
                                        )
                                    }
                                    ActiveCollectionKey.ArtistsDirectory -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs (available from overview, artist directory, and detail views)
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建私人歌单") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("歌单介绍（可选，支持多行）") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylist(newName.trim(), newDesc.trim())
                            newName = ""
                            newDesc = ""
                            showCreateDialog = false
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("编辑歌单名与介绍") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it },
                        label = { Text("歌单名称") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_playlist_name_input")
                    )
                    OutlinedTextField(
                        value = renameDesc,
                        onValueChange = { renameDesc = it },
                        label = { Text("歌单介绍（支持多行氛围描述）") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_playlist_desc_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameName.isNotBlank()) {
                            onRenamePlaylist(target.playlist.id, renameName.trim(), renameDesc.trim())
                            renameTarget = null
                        }
                    },
                    enabled = renameName.isNotBlank(),
                    modifier = Modifier.testTag("edit_playlist_save_button")
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    saveSmartAsPlaylistDefaults?.let { (_, trackIds) ->
        AlertDialog(
            onDismissRequest = { saveSmartAsPlaylistDefaults = null },
            title = { Text("另存为自建歌单") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "将当前列表的 ${trackIds.size} 首曲目一键保存为独立歌单，方便随时增删排序与撰写介绍。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = saveSmartName,
                        onValueChange = { saveSmartName = it },
                        label = { Text("新歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveSmartName.isNotBlank()) {
                            onCreatePlaylistWithTracks(saveSmartName.trim(), "", trackIds)
                            saveSmartAsPlaylistDefaults = null
                        }
                    },
                    enabled = saveSmartName.isNotBlank()
                ) {
                    Text("一键保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { saveSmartAsPlaylistDefaults = null }) {
                    Text("取消")
                }
            }
        )
    }

    showTrackSelectorForPlaylistId?.let { plId ->
        val plName = playlists.firstOrNull { it.playlist.id == plId }?.playlist?.name ?: "歌单"
        val existingIds = playlistTracksMap[plId].orEmpty().map { it.id }.toSet()
        LibraryTrackSelectorDialog(
            playlistName = plName,
            allTracks = allTracks,
            existingTrackIds = existingIds,
            onConfirmAdd = { selectedTrackIds ->
                onBatchAddToPlaylist(plId, selectedTrackIds)
            },
            onDismiss = { showTrackSelectorForPlaylistId = null }
        )
    }

    pendingPlaylistTargetIds?.let { targetIds ->
        BatchPlaylistPickerDialog(
            trackCount = targetIds.size,
            playlists = playlists,
            onSelectPlaylist = { plId ->
                onBatchAddToPlaylist(plId, targetIds)
                isSelectionMode = false
                selectedIds = emptySet()
            },
            onCreateNewPlaylist = { name, desc ->
                onCreatePlaylistWithTracks(name, desc, targetIds)
                isSelectionMode = false
                selectedIds = emptySet()
            },
            onDismiss = { pendingPlaylistTargetIds = null }
        )
    }

    pendingConfirmAction?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { pendingConfirmAction = null },
            title = { Text("确认操作") },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        action()
                        pendingConfirmAction = null
                        isSelectionMode = false
                        selectedIds = emptySet()
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmAction = null }) {
                    Text("取消")
                }
            }
        )
    }

    coverEditorTarget?.let { target ->
        PlaylistCoverEditorSheet(
            playlistTitle = target.title,
            tracks = target.tracks,
            initialConfig = playlistCoverConfigs[target.storageKey],
            fallbackIcon = target.fallbackIcon,
            heroAccent = target.heroAccent,
            onSave = { newConfig ->
                onUpdatePlaylistCoverConfig(target.storageKey, newConfig)
            },
            onDismiss = { coverEditorTarget = null }
        )
    }
}

@Composable
private fun PlaylistsOverviewContent(
    favoriteTracks: List<Track>,
    artistCollections: List<ArtistAutoPlaylist>,
    recentlyPlayed: List<Track>,
    mostPlayed: List<Track>,
    playlists: List<PlaylistWithCount>,
    playlistTracksMap: Map<Long, List<Track>>,
    playlistCoverConfigs: Map<String, PlaylistCoverConfig>,
    contentBottomPadding: Int,
    onCreatePlaylistClick: () -> Unit,
    onSelectCollection: (ActiveCollectionKey) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onAppendToQueue: (List<Track>) -> Unit,
    onQuickAddTracks: (Long) -> Unit,
    onEditPlaylist: (PlaylistWithCount) -> Unit,
    onCustomizeCover: (PlaylistWithCount, List<Track>) -> Unit,
    onRequestClearPlaylist: (PlaylistWithCount) -> Unit,
    onRequestDeletePlaylist: (PlaylistWithCount) -> Unit
) {
    val topArtistPreview = remember(artistCollections) {
        artistCollections.take(2).joinToString(" · ") { it.artistName }
    }
    val topArtistCoverTracks = remember(artistCollections) {
        artistCollections.flatMap { it.tracks.take(1) }.take(4)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = (contentBottomPadding + 28).dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Editorial Top Header
        item(key = "overview_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("歌单与歌手合集", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "按歌手自动聚合，支持多封面拼贴、自由编排与精选介绍",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(RubyAccent)
                        .bouncyClickable(pressedScale = 0.94f) { onCreatePlaylistClick() }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                        .testTag("create_playlist_button")
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "新建歌单",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "新建",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // 2x2 Editorial Quick-Access Grid (including new "歌手" Artist Auto-Playlists tile!)
        item(key = "overview_smart_grid") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmartCollectionTile(
                        title = "红心挚爱",
                        subtitle = "${favoriteTracks.size} 首珍藏",
                        icon = Icons.Rounded.Favorite,
                        gradient = listOf(Color(0xFFE11D48), Color(0xFF7B152A)),
                        previewTracks = favoriteTracks,
                        coverConfig = playlistCoverConfigs[PlaylistCoverKeys.FAVORITES],
                        onClick = { onSelectCollection(ActiveCollectionKey.Favorites) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("playlists_favorites_tile")
                    )
                    SmartCollectionTile(
                        title = "歌手",
                        subtitle = if (topArtistPreview.isNotBlank()) {
                            "${artistCollections.size} 位 · $topArtistPreview"
                        } else {
                            "${artistCollections.size} 位歌手"
                        },
                        icon = Icons.Rounded.Person,
                        gradient = listOf(Color(0xFF0D9488), Color(0xFF134E4A)),
                        previewTracks = topArtistCoverTracks,
                        coverConfig = null,
                        onClick = { onSelectCollection(ActiveCollectionKey.ArtistsDirectory) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("playlists_artists_tile")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmartCollectionTile(
                        title = "最近聆听",
                        subtitle = "${recentlyPlayed.size} 首足迹",
                        icon = Icons.Rounded.History,
                        gradient = listOf(AuraViolet, Color(0xFF281D5E)),
                        previewTracks = recentlyPlayed,
                        coverConfig = playlistCoverConfigs[PlaylistCoverKeys.RECENT],
                        onClick = { onSelectCollection(ActiveCollectionKey.Recent) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("playlists_recent_tile")
                    )
                    SmartCollectionTile(
                        title = "高频循环",
                        subtitle = "${mostPlayed.size} 首常听",
                        icon = Icons.Rounded.LocalFireDepartment,
                        gradient = listOf(Color(0xFFD99A4E), Color(0xFF6B4214)),
                        previewTracks = mostPlayed,
                        coverConfig = playlistCoverConfigs[PlaylistCoverKeys.MOST_PLAYED],
                        onClick = { onSelectCollection(ActiveCollectionKey.MostPlayed) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("playlists_most_played_tile")
                    )
                }
            }
        }

        // Section Header: Custom Playlists
        item(key = "overview_custom_title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的自建歌单 (${playlists.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (playlists.isNotEmpty()) {
                    Text(
                        text = "点击查看封面与介绍",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (playlists.isEmpty()) {
            item(key = "overview_empty_playlists") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                        .clickable(onClick = onCreatePlaylistClick)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(RubyAccent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = RubyAccent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text("轻触新建你的第一个私人歌单", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "也可在 AI 对话中让助手直接为你策展并一键建单",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(playlists, key = { it.playlist.id }) { item ->
                val plTracks = playlistTracksMap[item.playlist.id].orEmpty()
                val durationStr = remember(plTracks) { formatCollectionTotalDuration(plTracks) }
                var rowMenuExpanded by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                        .bouncyClickable(pressedScale = 0.98f) {
                            onSelectCollection(ActiveCollectionKey.Custom(item.playlist.id))
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaylistCoverArt(
                        tracks = plTracks,
                        contentDescription = item.playlist.name,
                        cornerRadius = 16.dp,
                        coverConfig = playlistCoverConfigs[PlaylistCoverKeys.custom(item.playlist.id)],
                        modifier = Modifier.size(66.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = item.playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.playlist.description.isNotBlank()) {
                            Text(
                                text = item.playlist.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${item.trackCount} 首曲目" + if (plTracks.isNotEmpty()) " · $durationStr" else " · 暂未添加曲目",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChampagneGold.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (plTracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(RubyAccent.copy(alpha = 0.16f))
                                .border(0.6.dp, RubyAccent.copy(alpha = 0.45f), CircleShape)
                                .clickable { onPlayAll(plTracks, false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "播放歌单",
                                tint = RubyAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { rowMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "管理歌单",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = rowMenuExpanded,
                            onDismissRequest = { rowMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("从曲库快速加歌") },
                                onClick = {
                                    rowMenuExpanded = false
                                    onQuickAddTracks(item.playlist.id)
                                }
                            )
                            if (plTracks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("随机播放全部") },
                                    onClick = {
                                        rowMenuExpanded = false
                                        onPlayAll(plTracks, true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("全部加入待播队列") },
                                    onClick = {
                                        rowMenuExpanded = false
                                        onAppendToQueue(plTracks)
                                    }
                                )
                            }
                            if (plTracks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("设置歌单封面") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.PhotoLibrary,
                                            contentDescription = null,
                                            tint = RubyAccent
                                        )
                                    },
                                    onClick = {
                                        rowMenuExpanded = false
                                        onCustomizeCover(item, plTracks)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("编辑歌单名与介绍") },
                                onClick = {
                                    rowMenuExpanded = false
                                    onEditPlaylist(item)
                                }
                            )
                            if (plTracks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("一键清空歌单", color = RubyAccent) },
                                    onClick = {
                                        rowMenuExpanded = false
                                        onRequestClearPlaylist(item)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("删除歌单", color = RubyAccent) },
                                onClick = {
                                    rowMenuExpanded = false
                                    onRequestDeletePlaylist(item)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistsDirectoryView(
    artists: List<ArtistAutoPlaylist>,
    playlistCoverConfigs: Map<String, PlaylistCoverConfig>,
    contentBottomPadding: Int,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlayArtist: (List<Track>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(false) }

    val displayedArtists = remember(artists, query, sortByName) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) {
            artists
        } else {
            artists.filter { artist ->
                artist.artistName.contains(q, ignoreCase = true) ||
                    artist.tracks.any { it.title.contains(q, ignoreCase = true) }
            }
        }
        if (sortByName) {
            filtered.sortedBy { it.artistName.lowercase() }
        } else {
            filtered
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = (contentBottomPadding + 28).dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "artists_header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("artists_directory_back")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回歌单")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("歌手自动歌单", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${artists.size} 位歌手 · 点选进入查看或一键连播",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilterChip(
                        selected = sortByName,
                        onClick = { sortByName = !sortByName },
                        leadingIcon = {
                            Icon(Icons.Rounded.SortByAlpha, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        label = { Text(if (sortByName) "按姓名" else "按曲目数", maxLines = 1) }
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = "搜索歌手姓名或曲目...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空搜索", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("artists_search_input")
                )
            }
        }

        if (displayedArtists.isEmpty()) {
            item(key = "artists_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (artists.isEmpty()) {
                            "曲库暂未导入音乐，扫描或导入后会自动按歌手生成专属歌单"
                        } else {
                            "没有找到匹配「$query」的歌手"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(displayedArtists, key = { it.artistName }) { artist ->
                val durationLabel = remember(artist.tracks) { formatCollectionTotalDuration(artist.tracks) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .bouncyClickable(pressedScale = 0.98f) { onOpenArtist(artist.artistName) }
                        .padding(12.dp)
                        .testTag("artist_row_${artist.artistName}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaylistCoverArt(
                        tracks = artist.tracks,
                        contentDescription = artist.artistName,
                        cornerRadius = 16.dp,
                        coverConfig = playlistCoverConfigs[PlaylistCoverKeys.artist(artist.artistName)],
                        fallbackIcon = Icons.Rounded.Person,
                        fallbackGradient = listOf(Color(0xFF0D9488), Color(0xFF134E4A)),
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = artist.artistName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${artist.tracks.size} 首曲目 · $durationLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (artist.losslessCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(EmeraldLossless.copy(alpha = 0.16f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${artist.losslessCount} 无损",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldLossless
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmeraldLossless.copy(alpha = 0.16f))
                            .border(0.6.dp, EmeraldLossless.copy(alpha = 0.45f), CircleShape)
                            .clickable { onPlayArtist(artist.tracks) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "播放${artist.artistName}的全部曲目",
                            tint = EmeraldLossless,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistBatchAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    tint: Color? = null
) {
    val activeTint = tint ?: MaterialTheme.colorScheme.onSurface
    val finalTint = if (enabled) activeTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = finalTint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = finalTint)
    }
}

@Composable
private fun SmartCollectionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    previewTracks: List<Track>,
    coverConfig: PlaylistCoverConfig? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(114.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(gradient))
            .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .bouncyClickable(pressedScale = 0.96f) { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (previewTracks.isNotEmpty()) {
                PlaylistCoverArt(
                    tracks = previewTracks,
                    contentDescription = title,
                    cornerRadius = 10.dp,
                    preferSingleHero = true,
                    coverConfig = coverConfig,
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.84f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
