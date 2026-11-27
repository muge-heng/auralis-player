package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.scanner.ScanProgress
import com.example.model.LibraryFilterFormat
import com.example.model.LibrarySortOption
import com.example.model.PlaylistWithCount
import com.example.model.Track
import com.example.model.TrackMetadataOverride
import com.example.model.formatDuration
import com.example.ui.components.AudioTrackInfoDialog
import com.example.ui.components.BatchPlaylistPickerDialog
import com.example.ui.components.LiveEqualizerBars
import com.example.ui.components.QualityBadgePill
import com.example.ui.components.TrackArtwork
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.adaptiveGoldColor

enum class LibraryViewTab(val label: String) {
    TRACKS("歌曲"),
    ALBUMS("专辑"),
    ARTISTS("艺术家"),
    FOLDERS("文件夹")
}

@Composable
fun LibraryScreen(
    musicTracks: List<Track>,
    filteredMusicTracks: List<Track>,
    generalAudioTracks: List<Track>,
    filteredGeneralAudioTracks: List<Track>,
    showHeroBanner: Boolean,
    showGeneralAudioNotice: Boolean = true,
    enableGeneralAudioMode: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    searchQuery: String,
    formatFilter: LibraryFilterFormat,
    sortOption: LibrarySortOption,
    selectedFolder: String?,
    selectedAlbum: String? = null,
    selectedArtist: String? = null,
    trackMetadataOverrides: Map<String, TrackMetadataOverride> = emptyMap(),
    scanProgress: ScanProgress,
    playlists: List<PlaylistWithCount>,
    onSearchChange: (String) -> Unit,
    onFormatFilterChange: (LibraryFilterFormat) -> Unit,
    onSortChange: (LibrarySortOption) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onSelectAlbum: (String?) -> Unit = {},
    onSelectArtist: (String?) -> Unit = {},
    onRequestSmartScan: () -> Unit,
    onImportAudioFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onHideHeroBanner: () -> Unit,
    onDismissGeneralAudioNotice: () -> Unit = {},
    onSetTracksGeneralAudio: (List<String>, Boolean) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAppendToQueue: (List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onBatchFavorite: (List<String>, Boolean) -> Unit,
    onAddToPlaylist: (Long, Track) -> Unit,
    onBatchAddToPlaylist: (Long, List<String>) -> Unit,
    onCreatePlaylistWithTracks: (String, String, List<String>) -> Unit,
    onBatchRemoveFromLibrary: (List<String>) -> Unit,
    onBurnLyricsToAudio: (Track) -> Unit = {},
    onExportTrackLyrics: (Track) -> Unit = {},
    onShareTrack: (Track) -> Unit = {},
    onSaveTrackMetadata: (Track, String, String, String, Uri?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onRestoreTrackMetadata: (Track) -> Unit = {},
    duplicateGroupCount: Int = 0,
    duplicateRedundantCount: Int = 0,
    onOpenDuplicateManager: () -> Unit = {},
    onOpenAiStudio: () -> Unit,
    contentBottomPadding: Int
) {
    var activeTab by remember { mutableStateOf(LibraryViewTab.TRACKS) }
    var drilldownOriginTab by remember { mutableStateOf<LibraryViewTab?>(null) }
    var viewingGeneralAudio by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingPlaylistTargetIds by remember { mutableStateOf<List<String>?>(null) }
    var inspectingTrack by remember { mutableStateOf<Track?>(null) }

    val showGeneralAudioSwitcher = enableGeneralAudioMode || generalAudioTracks.isNotEmpty()
    val effectiveViewingGeneral = showGeneralAudioSwitcher && viewingGeneralAudio
    val activeAllTracks = if (effectiveViewingGeneral) generalAudioTracks else musicTracks
    val activeFilteredTracks = if (effectiveViewingGeneral) filteredGeneralAudioTracks else filteredMusicTracks
    val totalTrackCount = musicTracks.size + generalAudioTracks.size

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    val hasActiveDrilldown = !effectiveViewingGeneral &&
        activeTab == LibraryViewTab.TRACKS &&
        (selectedFolder != null || selectedAlbum != null || selectedArtist != null)

    BackHandler(enabled = !isSelectionMode && hasActiveDrilldown) {
        val returnTab = drilldownOriginTab ?: when {
            selectedAlbum != null -> LibraryViewTab.ALBUMS
            selectedArtist != null -> LibraryViewTab.ARTISTS
            selectedFolder != null -> LibraryViewTab.FOLDERS
            else -> LibraryViewTab.TRACKS
        }
        onSelectFolder(null)
        onSelectAlbum(null)
        onSelectArtist(null)
        drilldownOriginTab = null
        activeTab = returnTab
    }

    val losslessCount = remember(musicTracks) { musicTracks.count { it.isLossless } }
    val albums = remember(activeFilteredTracks) {
        activeFilteredTracks.groupBy { it.album }.entries.sortedByDescending { it.value.size }
    }
    val artists = remember(activeFilteredTracks) {
        activeFilteredTracks.groupBy { it.artist }.entries.sortedByDescending { it.value.size }
    }
    val folders = remember(activeAllTracks) {
        activeAllTracks.groupBy { it.folderPath }.entries.sortedByDescending { it.value.size }
    }

    val selectedTracks = remember(activeFilteredTracks, selectedIds) {
        activeFilteredTracks.filter { it.id in selectedIds }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = (contentBottomPadding + if (isSelectionMode) 96 else 24).dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Editorial Studio Hero Banner (Hideable)
        if (showHeroBanner) {
            item(key = "hero_banner") {
                StudioHeroBanner(
                    trackCount = musicTracks.size,
                    losslessCount = losslessCount,
                    onSmartScan = onRequestSmartScan,
                    onImportAudio = onImportAudioFiles,
                    onImportFolder = onImportFolder,
                    onOpenAi = onOpenAiStudio,
                    onHideBanner = onHideHeroBanner
                )
            }
        } else {
            item(key = "compact_library_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (effectiveViewingGeneral) "普通音频区" else "高保真本地唱片馆",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (effectiveViewingGeneral) {
                                "${generalAudioTracks.size} 个本地音频 · 不混入歌单与 AI 策展"
                            } else {
                                "${musicTracks.size} 首音乐 · $losslessCount 首无损母带"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onRequestSmartScan,
                            modifier = Modifier.testTag("compact_smart_scan")
                        ) {
                            Icon(Icons.Rounded.Radar, contentDescription = "智能扫描", tint = RubyAccent)
                        }
                        IconButton(
                            onClick = onOpenAiStudio,
                            modifier = Modifier.testTag("compact_open_ai")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI 导赏", tint = adaptiveGoldColor())
                        }
                    }
                }
            }
        }

        // Music Library <-> Ordinary Local Audio Mode Switcher
        if (showGeneralAudioSwitcher) {
            item(key = "audio_zone_switcher") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (!effectiveViewingGeneral) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable {
                                viewingGeneralAudio = false
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }
                            .padding(vertical = 9.dp)
                            .testTag("zone_tab_music"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LibraryMusic,
                                contentDescription = null,
                                tint = if (!effectiveViewingGeneral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "音乐唱片馆 (${musicTracks.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (!effectiveViewingGeneral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (effectiveViewingGeneral) MaterialTheme.colorScheme.secondary
                                else Color.Transparent
                            )
                            .clickable {
                                viewingGeneralAudio = true
                                activeTab = LibraryViewTab.TRACKS
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }
                            .padding(vertical = 9.dp)
                            .testTag("zone_tab_general"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = if (effectiveViewingGeneral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "普通音频区 (${generalAudioTracks.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (effectiveViewingGeneral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Smart Duplicate Conflict Alert Banner
        if (duplicateGroupCount > 0) {
            item(key = "duplicate_conflict_banner") {
                val dupGold = adaptiveGoldColor()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(dupGold.copy(alpha = 0.14f))
                        .border(0.8.dp, dupGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenDuplicateManager)
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                        .testTag("library_duplicate_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(dupGold.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CompareArrows,
                            contentDescription = null,
                            tint = dupGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "检测到 $duplicateGroupCount 组重复音频（$duplicateRedundantCount 个冗余副本）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "点击一键保留最高音质，或将重复副本分流至普通音频区",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "进入智能查重",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Active Scan Progress Card
        if (scanProgress.isScanning) {
            item(key = "scan_progress") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "正在智能扫描与解析内嵌元数据...",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${scanProgress.matchedCount} 首",
                                style = MaterialTheme.typography.labelMedium,
                                color = adaptiveGoldColor()
                            )
                        }
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (scanProgress.currentFile.isNotBlank()) {
                            Text(
                                text = scanProgress.currentFile,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Empty State Onboarding Card
        if (totalTrackCount == 0 && !scanProgress.isScanning) {
            item(key = "empty_state") {
                EmptyLibraryCard(
                    onSmartScan = onRequestSmartScan,
                    onImportAudio = onImportAudioFiles,
                    onImportFolder = onImportFolder
                )
            }
        } else {
            if (effectiveViewingGeneral && showGeneralAudioNotice) {
                item(key = "general_audio_notice") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 6.dp)
                            .testTag("general_audio_notice_card"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "普通音频区：收录录音、播客或未验证元数据的本地音频，独立播放且不参与歌单与 AI 策展。可在右侧菜单随时「标记为音乐」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissGeneralAudioNotice,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("dismiss_general_audio_notice")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭提示",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Search Bar + Sort trigger (guaranteed single-line placeholder height)
            item(key = "search_bar") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = {
                            Text(
                                text = if (effectiveViewingGeneral) "搜索普通音频或文件夹..." else "搜索曲目、歌手、专辑或格式...",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "清除搜索")
                                }
                            }
                        },
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("library_search_input")
                    )

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .testTag("library_sort_button")
                        ) {
                            Icon(Icons.Rounded.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            LibrarySortOption.entries.forEach { opt ->
                                val isCurrent = opt == sortOption
                                val itemLabel = if (opt == LibrarySortOption.SMART && isCurrent) {
                                    "${opt.label} · 换一批"
                                } else {
                                    opt.label
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = itemLabel,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = if (opt == LibrarySortOption.SMART) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else adaptiveGoldColor(),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        onSortChange(opt)
                                        showSortMenu = false
                                    },
                                    modifier = Modifier.testTag("sort_option_${opt.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // View Mode Segment Row + Format Smart Chips
            if (!effectiveViewingGeneral) {
                item(key = "filter_chips") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LibraryViewTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                        .clickable {
                                            activeTab = tab
                                            if (tab != LibraryViewTab.TRACKS) {
                                                onSelectFolder(null)
                                                onSelectAlbum(null)
                                                onSelectArtist(null)
                                                drilldownOriginTab = null
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LibraryFilterFormat.entries) { fmt ->
                                FilterChip(
                                    selected = formatFilter == fmt,
                                    onClick = { onFormatFilterChange(fmt) },
                                    label = { Text(fmt.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                            if (selectedAlbum != null) {
                                item(key = "chip_selected_album") {
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            onSelectAlbum(null)
                                            drilldownOriginTab = null
                                        },
                                        label = { Text("专辑: $selectedAlbum ×") },
                                        modifier = Modifier.testTag("filter_chip_album")
                                    )
                                }
                            }
                            if (selectedArtist != null) {
                                item(key = "chip_selected_artist") {
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            onSelectArtist(null)
                                            drilldownOriginTab = null
                                        },
                                        label = { Text("艺术家: $selectedArtist ×") },
                                        modifier = Modifier.testTag("filter_chip_artist")
                                    )
                                }
                            }
                            if (selectedFolder != null) {
                                item(key = "chip_selected_folder") {
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            onSelectFolder(null)
                                            drilldownOriginTab = null
                                        },
                                        label = { Text("目录: $selectedFolder ×") },
                                        modifier = Modifier.testTag("filter_chip_folder")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Play All / Shuffle / Quick Batch Manage Bar
            if ((effectiveViewingGeneral || activeTab == LibraryViewTab.TRACKS) && activeFilteredTracks.isNotEmpty()) {
                item(key = "play_actions") {
                    if (!isSelectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onPlayAll(activeFilteredTracks, false) },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("library_play_all")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("播放全部 (${activeFilteredTracks.size})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { onPlayAll(activeFilteredTracks, true) },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .weight(0.85f)
                                    .height(48.dp)
                                    .testTag("library_shuffle_all")
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action_shuffle_all), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    isSelectionMode = true
                                    selectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("library_manage_toggle")
                            ) {
                                Icon(
                                    Icons.Rounded.Checklist,
                                    contentDescription = stringResource(R.string.cd_batch_manage),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("管理")
                            }
                        }
                    } else {
                        // Compact Selection Control Bar (eliminates tall M3 TextButton/FilterChip vertical bloat)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    0.85.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "批量管理 · 已选 ${selectedIds.size} / ${activeFilteredTracks.size} 首",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = adaptiveGoldColor(),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                        .clickable {
                                            isSelectionMode = false
                                            selectedIds = emptySet()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("batch_done_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "完成",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    val allSelected = activeFilteredTracks.isNotEmpty() && activeFilteredTracks.all { it.id in selectedIds }
                                    CompactBatchActionPill(
                                        label = if (allSelected) "取消全选" else "全选当前列表",
                                        selected = allSelected,
                                        onClick = {
                                            selectedIds = if (allSelected) {
                                                emptySet()
                                            } else {
                                                activeFilteredTracks.map { it.id }.toSet()
                                            }
                                        },
                                        modifier = Modifier.testTag("batch_select_all")
                                    )
                                }
                                item {
                                    val losslessIds = activeFilteredTracks.filter { it.isLossless }.map { it.id }.toSet()
                                    if (losslessIds.isNotEmpty()) {
                                        CompactBatchActionPill(
                                            label = "仅选无损 (${losslessIds.size})",
                                            selected = losslessIds.all { it in selectedIds },
                                            onClick = { selectedIds = losslessIds }
                                        )
                                    }
                                }
                                item {
                                    CompactBatchActionPill(
                                        label = "反选",
                                        selected = false,
                                        onClick = {
                                            val allSet = activeFilteredTracks.map { it.id }.toSet()
                                            selectedIds = allSet - selectedIds
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Content according to active segment
            val effectiveTab = if (effectiveViewingGeneral) LibraryViewTab.TRACKS else activeTab
            when (effectiveTab) {
                LibraryViewTab.TRACKS -> {
                    if (activeFilteredTracks.isEmpty()) {
                        item(key = "empty_filtered_zone") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (effectiveViewingGeneral) {
                                        "普通音频区暂无文件。开启扫描归类或将任意曲目手动移至此处即可管理。"
                                    } else {
                                        "当前筛选条件下暂无匹配音乐"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(activeFilteredTracks, key = { it.id }) { track ->
                            val isSelected = track.id in selectedIds
                            val isGeneralItem = effectiveViewingGeneral || track.isGeneralAudio
                            TrackListItem(
                                track = track,
                                isCurrent = track.id == currentTrackId,
                                isPlaying = isPlaying,
                                playlists = if (isGeneralItem) emptyList() else playlists,
                                selectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - track.id else selectedIds + track.id
                                    } else {
                                        onPlayTrack(track, activeFilteredTracks)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(track.id)
                                    }
                                },
                                onPlayNext = { onPlayNext(track) },
                                onAppendToQueue = { onAppendToQueue(listOf(track)) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onAddToPlaylist = { plId -> onAddToPlaylist(plId, track) },
                                onOpenPlaylistPicker = if (isGeneralItem) null else {
                                    { pendingPlaylistTargetIds = listOf(track.id) }
                                },
                                onBurnLyricsToAudio = if (isGeneralItem) null else {
                                    { onBurnLyricsToAudio(track) }
                                },
                                onExportTrackLyrics = if (isGeneralItem) null else {
                                    { onExportTrackLyrics(track) }
                                },
                                onShowTrackInfo = { inspectingTrack = track },
                                onShareTrack = { onShareTrack(track) },
                                onRemoveFromLibrary = { onBatchRemoveFromLibrary(listOf(track.id)) },
                                customExtraMenuItems = { dismiss ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (track.isGeneralAudio) "标记为音乐（移入唱片馆）"
                                                else "移至普通音频区"
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.SwapHoriz,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            dismiss()
                                            onSetTracksGeneralAudio(listOf(track.id), !track.isGeneralAudio)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                LibraryViewTab.ALBUMS -> {
                    items(albums, key = { "album_${it.key}" }) { (albumName, tracks) ->
                        val cover = tracks.firstOrNull()
                        val hasHiRes = tracks.any { it.isHiRes }
                        val hasLossless = tracks.any { it.isLossless }
                        GroupedCollectionCard(
                            title = albumName,
                            subtitle = "${cover?.artist ?: "群星"} · ${tracks.size} 首",
                            artworkPath = cover?.artworkPath,
                            albumArtUri = cover?.albumArtUri,
                            badgeText = when {
                                hasHiRes -> "Hi-Res"
                                hasLossless -> "无损"
                                else -> null
                            },
                            badgeIsHiRes = hasHiRes,
                            onClick = {
                                drilldownOriginTab = LibraryViewTab.ALBUMS
                                onSelectAlbum(albumName)
                                activeTab = LibraryViewTab.TRACKS
                            },
                            onPlay = { onPlayAll(tracks, false) },
                            onAppendQueue = { onAppendToQueue(tracks) },
                            onAddToPlaylist = { pendingPlaylistTargetIds = tracks.map { it.id } }
                        )
                    }
                }

                LibraryViewTab.ARTISTS -> {
                    items(artists, key = { "artist_${it.key}" }) { (artistName, tracks) ->
                        val cover = tracks.firstOrNull()
                        GroupedCollectionCard(
                            title = artistName,
                            subtitle = "${tracks.map { it.album }.distinct().size} 张专辑 · ${tracks.size} 首曲目",
                            artworkPath = cover?.artworkPath,
                            albumArtUri = cover?.albumArtUri,
                            badgeText = null,
                            onClick = {
                                drilldownOriginTab = LibraryViewTab.ARTISTS
                                onSelectArtist(artistName)
                                activeTab = LibraryViewTab.TRACKS
                            },
                            onPlay = { onPlayAll(tracks, false) },
                            onAppendQueue = { onAppendToQueue(tracks) },
                            onAddToPlaylist = { pendingPlaylistTargetIds = tracks.map { it.id } }
                        )
                    }
                }

                LibraryViewTab.FOLDERS -> {
                    items(folders, key = { "folder_${it.key}" }) { (folderName, tracks) ->
                        var folderMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    drilldownOriginTab = LibraryViewTab.FOLDERS
                                    onSelectFolder(folderName)
                                    activeTab = LibraryViewTab.TRACKS
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${tracks.size} 首音频 · ${tracks.count { it.isLossless }} 首无损",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onPlayAll(tracks, false) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "播放此目录")
                            }
                            Box {
                                IconButton(onClick = { folderMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "目录操作")
                                }
                                DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("一键加入歌单...") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            folderMenu = false
                                            pendingPlaylistTargetIds = tracks.map { it.id }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("加入播放队列") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.QueueMusic,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            folderMenu = false
                                            onAppendToQueue(tracks)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("从曲库移除此目录曲目", color = RubyAccent) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = null,
                                                tint = RubyAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            folderMenu = false
                                            onBatchRemoveFromLibrary(tracks.map { it.id })
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Floating One-Click Batch Action Bar
    AnimatedVisibility(
        visible = isSelectionMode && (effectiveViewingGeneral || activeTab == LibraryViewTab.TRACKS),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = 10.dp,
                bottom = (contentBottomPadding + 10).dp
            )
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
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasSelection = selectedTracks.isNotEmpty()
                BatchBarAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = "播放",
                    enabled = hasSelection,
                    onClick = {
                        onPlayAll(selectedTracks, false)
                        isSelectionMode = false
                        selectedIds = emptySet()
                    },
                    testTag = "batch_play"
                )
                BatchBarAction(
                    icon = Icons.Rounded.QueueMusic,
                    label = "入队列",
                    enabled = hasSelection,
                    onClick = {
                        onAppendToQueue(selectedTracks)
                        isSelectionMode = false
                        selectedIds = emptySet()
                    },
                    testTag = "batch_queue"
                )
                if (!effectiveViewingGeneral) {
                    BatchBarAction(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        label = "加歌单",
                        enabled = hasSelection,
                        onClick = { pendingPlaylistTargetIds = selectedTracks.map { it.id } },
                        testTag = "batch_add_playlist"
                    )
                }
                BatchBarAction(
                    icon = Icons.Rounded.SwapHoriz,
                    label = if (effectiveViewingGeneral) "标为音乐" else "普通音频",
                    enabled = hasSelection,
                    onClick = {
                        onSetTracksGeneralAudio(selectedTracks.map { it.id }, !effectiveViewingGeneral)
                        isSelectionMode = false
                        selectedIds = emptySet()
                    },
                    testTag = "batch_reclassify_audio"
                )
                if (!effectiveViewingGeneral) {
                    BatchBarAction(
                        icon = Icons.Rounded.Favorite,
                        label = "红心",
                        enabled = hasSelection,
                        onClick = {
                            onBatchFavorite(selectedTracks.map { it.id }, true)
                            isSelectionMode = false
                            selectedIds = emptySet()
                        },
                        testTag = "batch_favorite"
                    )
                }
                BatchBarAction(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "移出",
                    enabled = hasSelection,
                    tint = RubyAccent,
                    onClick = {
                        onBatchRemoveFromLibrary(selectedTracks.map { it.id })
                        isSelectionMode = false
                        selectedIds = emptySet()
                    },
                    testTag = "batch_remove"
                )
            }
        }
    }
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

    inspectingTrack?.let { infoTrack ->
        val liveTrack = activeAllTracks.firstOrNull { it.id == infoTrack.id } ?: infoTrack
        AudioTrackInfoDialog(
            track = liveTrack,
            onDismiss = { inspectingTrack = null },
            onShare = { onShareTrack(liveTrack) },
            onToggleGeneralAudio = {
                onSetTracksGeneralAudio(listOf(liveTrack.id), !liveTrack.isGeneralAudio)
            },
            hasMetadataOverride = trackMetadataOverrides.containsKey(liveTrack.id),
            onSaveMetadata = { title, artist, album, coverUri, clearCover ->
                onSaveTrackMetadata(liveTrack, title, artist, album, coverUri, clearCover)
            },
            onRestoreMetadata = { onRestoreTrackMetadata(liveTrack) }
        )
    }
}

@Composable
private fun StudioHeroBanner(
    trackCount: Int,
    losslessCount: Int,
    onSmartScan: () -> Unit,
    onImportAudio: () -> Unit,
    onImportFolder: () -> Unit,
    onOpenAi: () -> Unit,
    onHideBanner: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_studio_stage_1795327223994),
            contentDescription = "Auralis 私人声学唱片馆",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF09090D).copy(alpha = 0.25f),
                            Color(0xFF09090D).copy(alpha = 0.72f),
                            Color(0xFF09090D).copy(alpha = 0.94f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AURALIS STUDIO",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChampagneGold
                    )
                    Text(
                        text = "高保真本地唱片馆",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable(onClick = onOpenAi)
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                            .testTag("hero_ai_studio_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = ChampagneGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "AI 导赏",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable(onClick = onHideBanner)
                            .testTag("hero_hide_banner_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "隐藏顶部卡片",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (trackCount > 0) {
                        "已收录 $trackCount 首曲目 · $losslessCount 首无损母带 · 支持系统默认播放"
                    } else {
                        "原生支持 QRC/YRC/KRC/LRC 逐字歌词、内嵌封面与硬解 EQ"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSmartScan,
                        colors = ButtonDefaults.buttonColors(containerColor = RubyAccent),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("hero_smart_scan")
                    ) {
                        Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_scan_library), style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onImportAudio,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("hero_import_audio")
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入单曲", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onImportFolder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("hero_import_folder")
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("文件夹", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    onSmartScan: () -> Unit,
    onImportAudio: () -> Unit,
    onImportFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.empty_library_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_library_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onSmartScan,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("empty_scan_button")
        ) {
            Icon(Icons.Rounded.Radar, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("立即智能扫描本机音乐")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onImportAudio,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Text("选择音频文件")
            }
            OutlinedButton(
                onClick = onImportFolder,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Text("选择音乐目录")
            }
        }
    }
}

@Composable
private fun BatchBarAction(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    playlists: List<PlaylistWithCount>,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onAppendToQueue: (() -> Unit)? = null,
    onOpenPlaylistPicker: (() -> Unit)? = null,
    onBurnLyricsToAudio: (() -> Unit)? = null,
    onExportTrackLyrics: (() -> Unit)? = null,
    onShowTrackInfo: (() -> Unit)? = null,
    onShareTrack: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
    customExtraMenuItems: (@Composable (dismiss: () -> Unit) -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val highlighted = if (selectionMode) isSelected else isCurrent
    val isGeneralAudio = track.isGeneralAudio

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = if (highlighted) 1.dp else 0.5.dp,
                color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isSelected) "已选中" else "未选中",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(24.dp)
            )
        }

        Box(modifier = Modifier.size(54.dp)) {
            TrackArtwork(
                artworkPath = track.artworkPath,
                albumArtUri = track.albumArtUri,
                contentDescription = track.title,
                cornerRadius = 12.dp,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    LiveEqualizerBars(isPlaying = isPlaying, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isGeneralAudio) {
                    "${track.folderPath.ifBlank { "本地音频" }} · ${track.fileName.ifBlank { track.codecLabel }}"
                } else {
                    "${track.artist} · ${track.album}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QualityBadgePill(
                    label = track.codecLabel,
                    isHiRes = track.isHiRes,
                    isLossless = track.isLossless
                )
                Text(
                    text = buildString {
                        append(track.durationMs.formatDuration())
                        if (track.bitrateKbps > 0) append(" · ${track.bitrateKbps}k")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!selectionMode) {
            if (!isGeneralAudio) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (track.isFavorite) RubyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isGeneralAudio) "下一条播放" else "下一首播放") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        }
                    )
                    if (onAppendToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("加入播放队列") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAppendToQueue()
                            }
                        )
                    }
                    if (!isGeneralAudio) {
                        if (onOpenPlaylistPicker != null) {
                            DropdownMenuItem(
                                text = { Text("加入歌单...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenPlaylistPicker()
                                }
                            )
                        } else if (playlists.isNotEmpty()) {
                            playlists.take(5).forEach { pl ->
                                DropdownMenuItem(
                                    text = { Text("加入歌单：${pl.playlist.name}") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToPlaylist(pl.playlist.id)
                                    }
                                )
                            }
                        }
                        if (onBurnLyricsToAudio != null) {
                            DropdownMenuItem(
                                text = { Text("刻录歌词到音频文件") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Album,
                                        contentDescription = null,
                                        tint = adaptiveGoldColor(),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onBurnLyricsToAudio()
                                },
                                modifier = Modifier.testTag("track_menu_burn_lyrics")
                            )
                        }
                        if (onExportTrackLyrics != null) {
                            DropdownMenuItem(
                                text = { Text("导出歌词文件") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.FileDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onExportTrackLyrics()
                                },
                                modifier = Modifier.testTag("track_menu_export_lyrics")
                            )
                        }
                    }
                    if (onShowTrackInfo != null) {
                        DropdownMenuItem(
                            text = { Text(if (isGeneralAudio) "查看音频信息" else "查看曲目信息") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onShowTrackInfo()
                            },
                            modifier = Modifier.testTag("track_menu_info")
                        )
                    }
                    if (onShareTrack != null) {
                        DropdownMenuItem(
                            text = { Text("分享音频文件") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onShareTrack()
                            },
                            modifier = Modifier.testTag("track_menu_share")
                        )
                    }
                    customExtraMenuItems?.invoke { menuExpanded = false }
                    if (onRemoveFromLibrary != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isGeneralAudio) "从普通音频列表移出" else "从曲库列表移出",
                                    color = RubyAccent
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = RubyAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRemoveFromLibrary()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactBatchActionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
            )
            .border(
                width = 0.7.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun GroupedCollectionCard(
    title: String,
    subtitle: String,
    artworkPath: String?,
    albumArtUri: String?,
    badgeText: String?,
    badgeIsHiRes: Boolean = false,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onAppendQueue: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 11.dp, bottom = 11.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(
            artworkPath = artworkPath,
            albumArtUri = albumArtUri,
            contentDescription = title,
            cornerRadius = 14.dp,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Album / Artist title gets 100% of the primary row width — never squeezed by badges
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (badgeText != null) {
                    QualityBadgePill(
                        label = badgeText,
                        isHiRes = badgeIsHiRes,
                        isLossless = !badgeIsHiRes
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
        IconButton(
            onClick = onPlay,
            modifier = Modifier.size(width = 40.dp, height = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(width = 36.dp, height = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("一键加入歌单...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("全部加入播放队列") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onAppendQueue()
                    }
                )
            }
        }
    }
}
