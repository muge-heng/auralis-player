package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.NavBarStyle
import com.example.model.ScanFilterConfig
import com.example.ui.components.AmbientAppAuraOverlay
import com.example.ui.components.ImportConflictDialog
import com.example.ui.components.LiquidGlassNavBar
import com.example.ui.components.LiquidGlassNavItem
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.MorphingFloatingArtwork
import com.example.ui.components.SmartScanConfirmDialog
import com.example.ui.components.TopStatusToast
import com.example.ui.components.TopUndoBanner
import com.example.ui.components.liquidGlassBackdropSource
import com.example.ui.components.rememberLiquidGlassBackdropState
import com.example.ui.components.shareAudioTrack
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.DuplicateManagerSheet
import com.example.ui.screens.EqualizerSheet
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LyricsManagerSheet
import com.example.ui.screens.NowPlayingSheet
import com.example.ui.screens.NowPlayingStage
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.shouldCollapseNowPlayingSheet
import com.example.ui.theme.AuralisTheme
import com.example.ui.theme.RubyAccent
import com.example.viewmodel.AuralisViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class MainDestination(val labelRes: Int, val icon: ImageVector) {
    LIBRARY(R.string.nav_library, Icons.Rounded.LibraryMusic),
    PLAYLISTS(R.string.nav_playlists, Icons.AutoMirrored.Rounded.QueueMusic),
    AI_STUDIO(R.string.nav_ai_studio, Icons.Rounded.AutoAwesome),
    SETTINGS(R.string.nav_settings, Icons.Rounded.Tune)
}

class MainActivity : ComponentActivity() {

    private val viewModel: AuralisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        handleIncomingIntent(intent)

        setContent {
            val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }
            AuralisTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuralisRootApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            if (uri != null) {
                viewModel.handleExternalAudioIntent(uri)
            }
        }
    }
}

@Composable
private fun AuralisRootApp(viewModel: AuralisViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val auraPalette by viewModel.auraPalette.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val musicTracks by viewModel.musicTracks.collectAsStateWithLifecycle()
    val generalAudioTracks by viewModel.generalAudioTracks.collectAsStateWithLifecycle()
    val filteredTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val filteredGeneralAudioTracks by viewModel.filteredGeneralAudioTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistTracksMap by viewModel.playlistTracksMap.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val formatFilter by viewModel.formatFilter.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val currentLyric by viewModel.currentLyric.collectAsStateWithLifecycle()
    val lyricSettings by viewModel.lyricSettings.collectAsStateWithLifecycle()
    val onlineLyricSearch by viewModel.onlineLyricSearch.collectAsStateWithLifecycle()
    val eqState by viewModel.eqState.collectAsStateWithLifecycle()
    val customEqPresets by viewModel.customEqPresets.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val aiCatalogAttachmentPrefs by viewModel.aiCatalogAttachmentPrefs.collectAsStateWithLifecycle()
    val playlistCoverConfigs by viewModel.playlistCoverConfigs.collectAsStateWithLifecycle()
    val audioBookmarks by viewModel.audioBookmarks.collectAsStateWithLifecycle()
    val aiSessions by viewModel.aiSessions.collectAsStateWithLifecycle()
    val activeAiSessionId by viewModel.activeAiSessionId.collectAsStateWithLifecycle()
    val activeAiMessages by viewModel.activeAiMessages.collectAsStateWithLifecycle()
    val aiSessionRuntimeStates by viewModel.aiSessionRuntimeStates.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val navBarStyle by viewModel.navBarStyle.collectAsStateWithLifecycle()
    val visualEffectsEnhanced by viewModel.visualEffectsEnhanced.collectAsStateWithLifecycle()
    val showLibraryHero by viewModel.showLibraryHero.collectAsStateWithLifecycle()
    val showGeneralAudioNotice by viewModel.showGeneralAudioNotice.collectAsStateWithLifecycle()
    val enableGeneralAudioMode by viewModel.enableGeneralAudioMode.collectAsStateWithLifecycle()
    val deletionGuardMode by viewModel.deletionGuardMode.collectAsStateWithLifecycle()
    val hapticProfile by viewModel.hapticProfile.collectAsStateWithLifecycle()
    val scanFilter by viewModel.scanFilter.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val undoBanner by viewModel.undoBanner.collectAsStateWithLifecycle()
    val pendingConfirmDeletion by viewModel.pendingConfirmDeletion.collectAsStateWithLifecycle()
    val pendingLyricExport by viewModel.pendingLyricExport.collectAsStateWithLifecycle()
    val pendingBurnConfirm by viewModel.pendingBurnConfirm.collectAsStateWithLifecycle()
    val pendingAudioWritePermission by viewModel.pendingAudioWritePermission.collectAsStateWithLifecycle()
    val pendingAudioSaveCopy by viewModel.pendingAudioSaveCopy.collectAsStateWithLifecycle()
    val pendingSettingsBackupExport by viewModel.pendingSettingsBackupExport.collectAsStateWithLifecycle()
    val pendingSettingsBackupImport by viewModel.pendingSettingsBackupImport.collectAsStateWithLifecycle()
    val openNowPlayingTrigger by viewModel.openNowPlayingTrigger.collectAsStateWithLifecycle()
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val pendingImportConflicts by viewModel.pendingImportConflicts.collectAsStateWithLifecycle()
    val pendingImportSourceLabel by viewModel.pendingImportSourceLabel.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainDestination.LIBRARY) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showLyricsManager by remember { mutableStateOf(false) }
    var showDuplicateManager by remember { mutableStateOf(false) }
    var showSmartScanDialog by remember { mutableStateOf(false) }
    var pendingScanConfig by remember { mutableStateOf<ScanFilterConfig?>(null) }

    // Continuous, follow-finger interruptible expansion progress: 0f = MiniPlayer, 1f = Full NowPlaying
    val playerExpansion = remember { Animatable(0f) }
    var miniArtworkRect by remember { mutableStateOf(Rect.Zero) }
    var activeSheetArtworkRect by remember { mutableStateOf(Rect.Zero) }
    var activeSheetArtworkCornerDp by remember { mutableStateOf(24.dp) }
    var activeNowPlayingStage by remember { mutableStateOf(NowPlayingStage.COVER) }

    fun expandNowPlaying() {
        scope.launch {
            playerExpansion.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    fun collapseNowPlaying(initialVelocityFraction: Float = 0f) {
        scope.launch {
            playerExpansion.animateTo(
                targetValue = 0f,
                initialVelocity = initialVelocityFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    fun openAiWorkspace() {
        viewModel.haptics.tickLight()
        if (playerExpansion.value > 0.05f) {
            collapseNowPlaying()
        }
        currentTab = MainDestination.AI_STUDIO
    }

    LaunchedEffect(openNowPlayingTrigger) {
        if (openNowPlayingTrigger) {
            expandNowPlaying()
            viewModel.consumeOpenNowPlayingTrigger()
        }
    }

    BackHandler(enabled = playerExpansion.value > 0.5f && !showEqualizer && !showLyricsManager) {
        viewModel.haptics.tickLight()
        collapseNowPlaying()
    }

    // Audio Permission Launcher (Android 13+ READ_MEDIA_AUDIO / Android <=12 READ_EXTERNAL_STORAGE)
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val configToUse = pendingScanConfig
        pendingScanConfig = null
        if (granted) {
            if (configToUse != null) {
                viewModel.scanDeviceLibraryWithConfig(configToUse)
            } else {
                viewModel.scanDeviceLibrary()
            }
        } else {
            viewModel.showBanner("未授予本地音频权限，你仍可通过「导入单曲」或「文件夹」零权限播放")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // SAF Multi-Audio Picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(uris, playImmediately = allTracks.isEmpty())
        }
    }

    // SAF Folder Tree Picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { viewModel.importFolder(it) }
    }

    // SAF Lyric File Picker (.qrc, .yrc, .krc, .lrc, .ttml, .srt, .txt)
    val lyricPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val activeTrack = playbackState.currentTrack
        if (uri != null && activeTrack != null) {
            viewModel.importLyricFileForTrack(activeTrack, uri)
        }
    }

    // SAF Lyric File Exporter
    val lyricExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        viewModel.completeExportTrackLyrics(uri)
    }

    // System MediaStore Write-Consent Launcher (Android 10+ Scoped Storage)
    val audioWritePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.completeAuthorizedAudioWrite(result.resultCode == Activity.RESULT_OK)
    }

    // SAF Audio Save-Copy Fallback when original URI is strictly read-only
    val audioSaveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/*")
    ) { uri ->
        viewModel.completeAudioSaveCopy(uri)
    }

    LaunchedEffect(pendingLyricExport) {
        pendingLyricExport?.let { req ->
            lyricExportLauncher.launch(req.suggestedFileName)
        }
    }

    LaunchedEffect(pendingAudioWritePermission) {
        pendingAudioWritePermission?.let { req ->
            runCatching {
                audioWritePermissionLauncher.launch(
                    IntentSenderRequest.Builder(req.intentSender).build()
                )
            }.onFailure {
                viewModel.completeAuthorizedAudioWrite(false)
            }
        }
    }

    // SAF Local Settings Backup Exporter & Importer
    val settingsBackupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        viewModel.completeExportSettingsBackup(uri)
    }

    val settingsBackupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.inspectSettingsBackupFile(it) }
    }

    LaunchedEffect(pendingAudioSaveCopy) {
        pendingAudioSaveCopy?.let { req ->
            audioSaveCopyLauncher.launch(req.suggestedFileName)
        }
    }

    LaunchedEffect(pendingSettingsBackupExport) {
        pendingSettingsBackupExport?.let { req ->
            settingsBackupExportLauncher.launch(req.suggestedFileName)
        }
    }

    fun requestSmartScanDialog() {
        viewModel.haptics.tickLight()
        showSmartScanDialog = true
    }

    fun executeSmartScanWithConfig(config: ScanFilterConfig) {
        viewModel.haptics.springImpact()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.scanDeviceLibraryWithConfig(config)
        } else {
            pendingScanConfig = config
            permissionLauncher.launch(audioPermission)
        }
    }

    val currentTrack = playbackState.currentTrack
    val showMiniBarInTab = currentTrack != null && currentTab != MainDestination.AI_STUDIO
    val glassBackdropState = rememberLiquidGlassBackdropState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        val containerWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val containerHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
        val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
        val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp().value.roundToInt() }
        val isImeVisible = WindowInsets.ime.getBottom(density) > 0
        val isFloatingGlassNav = !useNavigationRail && navBarStyle == NavBarStyle.FLOATING_GLASS
        val showFloatingGlassNavNow = isFloatingGlassNav && !(currentTab == MainDestination.AI_STUDIO && isImeVisible)
        val hasFloatingChrome = (showMiniBarInTab && currentTrack != null) || showFloatingGlassNavNow
        val recordBackdropForGlass = hasFloatingChrome && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        // Bottom scroll clearance so content glides smoothly BEHIND transparent floating chrome yet last items stay reachable
        val scrollableTabBottomPadding = when {
            useNavigationRail -> navBarBottomDp + if (showMiniBarInTab) 96 else 16
            isFloatingGlassNav -> navBarBottomDp + 86 + if (showMiniBarInTab) 80 else 0
            else -> if (showMiniBarInTab) 82 else 16
        }
        val aiTabBottomPadding = when {
            isFloatingGlassNav && !isImeVisible -> navBarBottomDp + 86
            useNavigationRail -> navBarBottomDp
            else -> 0
        }
        val expansion = playerExpansion.value.coerceIn(0f, 1f)

        // Deterministic pre-layout cover rect so frame 1 of expand NEVER snaps from Rect.Zero
        val estimatedCoverRect = remember(containerWidthPx, containerHeightPx, statusBarTopPx, playbackState.isPlaying) {
            val baseSizePx = with(density) {
                minOf(maxWidth - 48.dp, 316.dp).toPx().coerceAtLeast(160f)
            }
            val scale = if (playbackState.isPlaying) 1f else 0.91f
            val visualSizePx = baseSizePx * scale
            val left = (containerWidthPx - visualSizePx) / 2f
            val top = statusBarTopPx + with(density) { 104.dp.toPx() } + (baseSizePx - visualSizePx) / 2f
            Rect(left = left, top = top, right = left + visualSizePx, bottom = top + visualSizePx)
        }

        val effectiveTargetArtworkRect = if (activeSheetArtworkRect != Rect.Zero) {
            activeSheetArtworkRect
        } else {
            estimatedCoverRect
        }

        // Works seamlessly for BOTH Cover stage (large artwork) and Lyrics/Queue stage (compact top header artwork)
        val isMorphingArt = currentTrack != null &&
            showMiniBarInTab &&
            expansion in 0.002f..0.996f &&
            miniArtworkRect != Rect.Zero &&
            effectiveTargetArtworkRect != Rect.Zero

        val miniArtAlpha = if (isMorphingArt) 0f else 1f
        val fullArtAlpha = if (isMorphingArt) 0f else 1f

        val handleMiniDragStart: () -> Unit = {
            scope.launch { playerExpansion.stop() }
        }
        val handleMiniDragDelta: (Float) -> Unit = { deltaY ->
            scope.launch {
                val next = (playerExpansion.value - deltaY / containerHeightPx).coerceIn(0f, 1.04f)
                playerExpansion.snapTo(next)
            }
        }
        val handleMiniDragEnd: (Float) -> Unit = { velY ->
            val shouldExpand = velY < -420f || (velY <= 350f && playerExpansion.value > 0.24f)
            if (shouldExpand) {
                viewModel.haptics.springImpact()
                expandNowPlaying()
            } else {
                collapseNowPlaying()
            }
        }

        val handleSheetDragStart: () -> Unit = {
            scope.launch { playerExpansion.stop() }
        }
        val handleSheetDragDelta: (Float) -> Unit = { deltaY ->
            scope.launch {
                val next = (playerExpansion.value - deltaY / containerHeightPx).coerceIn(0f, 1.02f)
                playerExpansion.snapTo(next)
            }
        }
        val handleSheetDragEnd: (Float, Float) -> Unit = { velY, totalDragDownPx ->
            val shouldCollapse = shouldCollapseNowPlayingSheet(
                expansion = playerExpansion.value,
                velocityYPxPerSec = velY,
                totalDragDownPx = totalDragDownPx,
                containerHeightPx = containerHeightPx
            )
            if (shouldCollapse) {
                viewModel.haptics.tickLight()
                // Hand off thumb downward flick speed directly into the collapse spring
                val initialVelFrac = (-velY / containerHeightPx).coerceIn(-6f, 0f)
                collapseNowPlaying(initialVelocityFraction = initialVelFrac)
            } else {
                expandNowPlaying()
            }
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val depthScale = lerp(1f, 0.94f, expansion)
                    scaleX = depthScale
                    scaleY = depthScale
                    alpha = lerp(1f, 0.84f, expansion)
                },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                if (!useNavigationRail && navBarStyle == NavBarStyle.CLASSIC) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ) {
                        MainDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = currentTab == dest,
                                onClick = {
                                    viewModel.haptics.tickLight()
                                    currentTab = dest
                                },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                    ) {
                        MainDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = currentTab == dest,
                                onClick = {
                                    viewModel.haptics.tickLight()
                                    currentTab = dest
                                },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${dest.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Full-bleed scrollable screen backdrop recorded live for transparent Liquid Glass chrome.
                    // CRITICAL: liquidGlassBackdropSource MUST wrap .background(...) so the opaque theme
                    // canvas is recorded inside contentLayer, preventing transparent (0,0,0,0) regions from
                    // turning black during GPU blur/AGSL refraction in light mode!
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .liquidGlassBackdropSource(
                                state = glassBackdropState,
                                enabled = recordBackdropForGlass
                            )
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        if (visualEffectsEnhanced) {
                            AmbientAppAuraOverlay(
                                palette = auraPalette,
                                isPlaying = playbackState.isPlaying,
                                darkTheme = darkTheme
                            )
                        }
                        when (currentTab) {
                        MainDestination.LIBRARY -> LibraryScreen(
                            musicTracks = musicTracks,
                            filteredMusicTracks = filteredTracks,
                            generalAudioTracks = generalAudioTracks,
                            filteredGeneralAudioTracks = filteredGeneralAudioTracks,
                            showHeroBanner = showLibraryHero,
                            showGeneralAudioNotice = showGeneralAudioNotice,
                            enableGeneralAudioMode = enableGeneralAudioMode,
                            currentTrackId = currentTrack?.id,
                            isPlaying = playbackState.isPlaying,
                            searchQuery = searchQuery,
                            formatFilter = formatFilter,
                            sortOption = sortOption,
                            selectedFolder = selectedFolder,
                            scanProgress = scanProgress,
                            playlists = playlists,
                            onSearchChange = viewModel::updateSearchQuery,
                            onFormatFilterChange = viewModel::updateFormatFilter,
                            onSortChange = viewModel::updateSortOption,
                            onSelectFolder = viewModel::selectFolder,
                            onRequestSmartScan = ::requestSmartScanDialog,
                            onImportAudioFiles = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            onImportFolder = { folderPickerLauncher.launch(null) },
                            onHideHeroBanner = {
                                viewModel.haptics.tickLight()
                                viewModel.setShowLibraryHero(false)
                            },
                            onDismissGeneralAudioNotice = {
                                viewModel.setShowGeneralAudioNotice(false)
                            },
                            onSetTracksGeneralAudio = viewModel::setTracksGeneralAudio,
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onPlayAll = { tracks, shuffle -> viewModel.playAll(tracks, shuffle) },
                            onPlayNext = viewModel::playNextInQueue,
                            onAppendToQueue = viewModel::appendTracksToQueue,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onBatchFavorite = viewModel::batchFavoriteTracks,
                            onAddToPlaylist = viewModel::addTrackToPlaylist,
                            onBatchAddToPlaylist = viewModel::batchAddTracksToPlaylist,
                            onCreatePlaylistWithTracks = viewModel::createPlaylist,
                            onBatchRemoveFromLibrary = viewModel::batchRemoveTracksFromLibrary,
                            onBurnLyricsToAudio = viewModel::requestBurnLyricsToAudio,
                            onExportTrackLyrics = viewModel::requestExportTrackLyrics,
                            onShareTrack = { track ->
                                if (!shareAudioTrack(context, track)) {
                                    viewModel.showBanner("无法调起系统分享该音频文件")
                                }
                            },
                            duplicateGroupCount = duplicateGroups.size,
                            duplicateRedundantCount = duplicateGroups.sumOf { it.redundantCount },
                            onOpenDuplicateManager = { showDuplicateManager = true },
                            onOpenAiStudio = ::openAiWorkspace,
                            contentBottomPadding = scrollableTabBottomPadding
                        )

                        MainDestination.PLAYLISTS -> PlaylistsScreen(
                            allTracks = musicTracks,
                            favoriteTracks = favoriteTracks,
                            recentlyPlayed = recentlyPlayed,
                            mostPlayed = mostPlayed,
                            playlists = playlists,
                            playlistTracksMap = playlistTracksMap,
                            playlistCoverConfigs = playlistCoverConfigs,
                            currentTrackId = currentTrack?.id,
                            isPlaying = playbackState.isPlaying,
                            onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                            onCreatePlaylistWithTracks = viewModel::createPlaylist,
                            onRenamePlaylist = viewModel::renamePlaylist,
                            onDeletePlaylist = { id, onDeleted -> viewModel.deletePlaylist(id, onDeleted) },
                            onClearPlaylist = viewModel::clearPlaylist,
                            onUpdatePlaylistCoverConfig = viewModel::updatePlaylistCoverConfig,
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onPlayAll = { tracks, shuffle -> viewModel.playAll(tracks, shuffle) },
                            onPlayNext = viewModel::playNextInQueue,
                            onAppendToQueue = viewModel::appendTracksToQueue,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onBatchFavorite = viewModel::batchFavoriteTracks,
                            onClearAllFavorites = viewModel::clearAllFavorites,
                            onAddToPlaylist = viewModel::addTrackToPlaylist,
                            onBatchAddToPlaylist = viewModel::batchAddTracksToPlaylist,
                            onRemoveFromPlaylist = viewModel::removeTracksFromPlaylist,
                            onMovePlaylistTrack = viewModel::movePlaylistTrack,
                            onClearRecentHistory = viewModel::clearRecentHistory,
                            onRemoveFromRecent = viewModel::removeFromRecentHistory,
                            onClearMostPlayed = viewModel::clearMostPlayedStats,
                            onBurnLyricsToAudio = viewModel::requestBurnLyricsToAudio,
                            onExportTrackLyrics = viewModel::requestExportTrackLyrics,
                            contentBottomPadding = scrollableTabBottomPadding
                        )

                        MainDestination.AI_STUDIO -> AiChatScreen(
                            aiConfig = aiConfig,
                            sessions = aiSessions,
                            activeSessionId = activeAiSessionId,
                            messages = activeAiMessages,
                            sessionRuntimeStates = aiSessionRuntimeStates,
                            currentTrack = currentTrack?.takeIf { !it.isGeneralAudio },
                            musicTracks = musicTracks,
                            catalogAttachmentPrefs = aiCatalogAttachmentPrefs,
                            onCreateSession = viewModel::createNewAiSession,
                            onSelectSession = viewModel::selectAiSession,
                            onRenameSession = viewModel::renameAiSession,
                            onRegenerateSessionTitle = { sessionId ->
                                viewModel.regenerateSessionTitle(sessionId, showFeedbackToast = true)
                            },
                            onDeleteSession = viewModel::deleteAiSession,
                            undoBanner = undoBanner,
                            onDismissUndoBanner = viewModel::dismissUndoBanner,
                            onSendMessage = { text, attachTrack, attachVision, attachCatalog ->
                                viewModel.sendAiMessage(
                                    userText = text,
                                    attachTrackContext = attachTrack,
                                    attachCoverVision = attachVision,
                                    attachLibraryCatalog = attachCatalog
                                )
                            },
                            onRunQuickTask = { task, attachVision, attachCatalog ->
                                viewModel.runQuickAiTask(
                                    task = task,
                                    attachCoverVision = attachVision,
                                    attachLibraryCatalog = attachCatalog
                                )
                            },
                            onStopGeneration = viewModel::stopAiGeneration,
                            onFeedbackErrorToModel = viewModel::sendErrorFeedbackToModel,
                            onRetryLastMessage = viewModel::retryLastAiMessage,
                            onUpdateReasoningEffort = viewModel::updateReasoningEffort,
                            onToggleExtendedReasoningLevel = viewModel::toggleExtendedReasoningLevel,
                            onDetentHaptic = viewModel::triggerReasoningDetentHaptic,
                            onToggleWebSearch = viewModel::toggleAiWebSearch,
                            onUpdateCatalogAttachmentPrefs = viewModel::updateAiCatalogAttachmentPrefs,
                            onUpdateAiConfig = viewModel::updateAiConfig,
                            onApplySuggestedEq = { preset ->
                                viewModel.applyEqPreset(preset)
                                showEqualizer = true
                            },
                            onApplyAndSaveSuggestedEq = { preset ->
                                viewModel.applyAndSaveEqPreset(preset)
                                showEqualizer = true
                            },
                            onCreatePlaylistFromAi = viewModel::createPlaylistFromAiSuggestion,
                            onPlayCuratedTitles = { titles ->
                                viewModel.playCuratedTracks(titles)
                            },
                            onCopiedNotice = { msg -> viewModel.showBanner(msg) },
                            contentBottomPadding = aiTabBottomPadding
                        )

                        MainDestination.SETTINGS -> SettingsScreen(
                            darkTheme = darkTheme,
                            navBarStyle = navBarStyle,
                            visualEffectsEnhanced = visualEffectsEnhanced,
                            showLibraryHero = showLibraryHero,
                            enableGeneralAudioMode = enableGeneralAudioMode,
                            deletionGuardMode = deletionGuardMode,
                            hapticProfile = hapticProfile,
                            scanFilter = scanFilter,
                            lyricSettings = lyricSettings,
                            aiConfig = aiConfig,
                            onSetDarkTheme = viewModel::setDarkTheme,
                            onSetNavBarStyle = viewModel::setNavBarStyle,
                            onSetVisualEffectsEnhanced = viewModel::setVisualEffectsEnhanced,
                            onSetShowLibraryHero = viewModel::setShowLibraryHero,
                            onSetEnableGeneralAudioMode = viewModel::setEnableGeneralAudioMode,
                            onSetDeletionGuardMode = viewModel::setDeletionGuardMode,
                            onSetHapticProfile = viewModel::setHapticProfile,
                            onUpdateScanFilter = viewModel::updateScanFilter,
                            onUpdateLyricSettings = viewModel::updateLyricSettings,
                            onTriggerRescan = ::requestSmartScanDialog,
                            onImportAudioFiles = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            onImportFolder = { folderPickerLauncher.launch(null) },
                            duplicateGroupCount = duplicateGroups.size,
                            onOpenDuplicateManager = { showDuplicateManager = true },
                            onOpenEqualizer = { showEqualizer = true },
                            onOpenAiStudio = ::openAiWorkspace,
                            onExportSettingsBackup = viewModel::requestExportSettingsBackup,
                            onImportSettingsBackup = {
                                settingsBackupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            pendingBackupImport = pendingSettingsBackupImport,
                            onConfirmImportSettingsBackup = viewModel::confirmImportSettingsBackup,
                            onDismissImportSettingsBackup = viewModel::dismissImportSettingsBackup,
                            contentBottomPadding = scrollableTabBottomPadding
                        )
                    }
                    }

                    // Transparent Floating Chrome Overlay (zero opaque backing plate — floats directly over scrolling content)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(
                                if (useNavigationRail || isFloatingGlassNav) {
                                    Modifier.navigationBarsPadding()
                                } else {
                                    Modifier
                                }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showMiniBarInTab && currentTrack != null) {
                            val dur = playbackState.durationMs.coerceAtLeast(currentTrack.durationMs).coerceAtLeast(1L)
                            MiniPlayerBar(
                                track = currentTrack,
                                previousTrack = playbackState.previousTrack,
                                nextTrack = playbackState.nextTrack,
                                isPlaying = playbackState.isPlaying,
                                progressFraction = playbackState.positionMs.toFloat() / dur.toFloat(),
                                auraPalette = auraPalette,
                                darkTheme = darkTheme,
                                backdropState = glassBackdropState,
                                visualEffectsEnhanced = visualEffectsEnhanced,
                                artworkAlpha = miniArtAlpha,
                                onArtworkBoundsChanged = { rect ->
                                    if (playerExpansion.value < 0.04f) {
                                        miniArtworkRect = rect
                                    }
                                },
                                onExpand = {
                                    viewModel.haptics.springImpact()
                                    expandNowPlaying()
                                },
                                onDragExpandStart = handleMiniDragStart,
                                onDragExpandDelta = handleMiniDragDelta,
                                onDragExpandEnd = handleMiniDragEnd,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onSkipNext = { viewModel.skipNext() },
                                onSkipPrevious = { viewModel.skipPrevious() },
                                modifier = if (useNavigationRail) {
                                    Modifier
                                        .widthIn(max = 580.dp)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                } else {
                                    Modifier
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = showFloatingGlassNavNow,
                            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                        ) {
                            val glassItems = MainDestination.entries.map { dest ->
                                LiquidGlassNavItem(
                                    id = dest.name,
                                    label = stringResource(dest.labelRes),
                                    icon = dest.icon,
                                    testTag = "nav_${dest.name.lowercase()}"
                                )
                            }
                            LiquidGlassNavBar(
                                items = glassItems,
                                selectedIndex = MainDestination.entries.indexOf(currentTab).coerceAtLeast(0),
                                darkTheme = darkTheme,
                                accentColor = RubyAccent,
                                auraPalette = auraPalette,
                                backdropState = glassBackdropState,
                                visualEffectsEnhanced = visualEffectsEnhanced,
                                onHoverTick = { viewModel.haptics.tickLight() },
                                onSelectIndex = { idx ->
                                    val target = MainDestination.entries.getOrNull(idx)
                                    if (target != null) {
                                        viewModel.haptics.tickLight()
                                        currentTab = target
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Continuous Interruptible Follow-Finger Full-Screen Now Playing Sheet
        if (playerExpansion.value > 0.002f && currentTrack != null) {
            val offsetYPx = ((1f - expansion) * containerHeightPx).roundToInt()
            val sheetCornerDp = lerp(30.dp, 0.dp, expansion)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetYPx) }
                    .clip(RoundedCornerShape(topStart = sheetCornerDp, topEnd = sheetCornerDp))
            ) {
                NowPlayingSheet(
                    playbackState = playbackState,
                    auraPalette = auraPalette,
                    lyricDocument = currentLyric,
                    lyricSettings = lyricSettings,
                    audioBookmarks = audioBookmarks[currentTrack.id].orEmpty(),
                    eqEnabled = eqState.enabled,
                    darkTheme = darkTheme,
                    visualEffectsEnhanced = visualEffectsEnhanced,
                    artworkAlpha = fullArtAlpha,
                    onActiveArtworkBoundsChanged = { rect, cornerDp ->
                        // Undo live vertical sheet translation so target rect stays pinned at expansion=1f
                        val currentOffsetY = (1f - playerExpansion.value.coerceIn(0f, 1f)) * containerHeightPx
                        activeSheetArtworkRect = rect.translate(0f, -currentOffsetY)
                        activeSheetArtworkCornerDp = cornerDp
                    },
                    onStageChanged = { stage -> activeNowPlayingStage = stage },
                    onDragCollapseStart = handleSheetDragStart,
                    onDragCollapseDelta = handleSheetDragDelta,
                    onDragCollapseEnd = handleSheetDragEnd,
                    onDismiss = {
                        viewModel.haptics.tickLight()
                        collapseNowPlaying()
                    },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSeekTo = viewModel::seekTo,
                    onSeekRelative = viewModel::seekRelative,
                    onAddAudioBookmark = { posMs, label ->
                        viewModel.addAudioBookmark(currentTrack.id, posMs, label)
                    },
                    onRemoveAudioBookmark = { bmId ->
                        viewModel.removeAudioBookmark(currentTrack.id, bmId)
                    },
                    onPromoteGeneralAudioToMusic = { track ->
                        viewModel.setTracksGeneralAudio(listOf(track.id), false)
                    },
                    onShareTrack = { track ->
                        if (!shareAudioTrack(context, track)) {
                            viewModel.showBanner("无法调起系统分享该音频文件")
                        }
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onToggleShuffle = {
                        viewModel.haptics.tickLight()
                        viewModel.playbackController.toggleShuffle()
                    },
                    onCycleRepeat = {
                        viewModel.haptics.tickLight()
                        viewModel.playbackController.cycleRepeatMode()
                    },
                    onSetSpeed = {
                        viewModel.haptics.tickLight()
                        viewModel.playbackController.setPlaybackSpeed(it)
                    },
                    onToggleAbLoop = {
                        viewModel.haptics.springImpact()
                        viewModel.playbackController.toggleAbLoopPoint()
                    },
                    onStartSleepTimer = { mins ->
                        viewModel.haptics.tickLight()
                        viewModel.playbackController.startSleepTimer(mins)
                    },
                    onJumpQueueIndex = { idx ->
                        viewModel.haptics.springImpact()
                        viewModel.playbackController.jumpToQueueIndex(idx)
                    },
                    onRemoveQueueIndex = viewModel::removeQueueIndex,
                    onMoveQueueIndexToNext = viewModel::moveQueueIndexToNext,
                    onClearUpcomingQueue = viewModel::clearUpcomingQueue,
                    onSaveQueueAsPlaylist = viewModel::saveQueueAsPlaylist,
                    onToggleSimulatedWordByWord = { enabled ->
                        viewModel.updateLyricSettings(lyricSettings.copy(enableSimulatedWordByWord = enabled))
                    },
                    onUpdateLyricSettings = viewModel::updateLyricSettings,
                    onOpenLyricManager = { showLyricsManager = true },
                    onQuickOnlineLyricMatch = { track ->
                        showLyricsManager = true
                        viewModel.smartMatchLyricsOnline(track)
                    },
                    onOpenEqualizer = { showEqualizer = true },
                    onOpenAiStudio = ::openAiWorkspace,
                    onScrubHaptic = { viewModel.haptics.tickLight() }
                )
            }
        }

        // Continuous Shared-Bounds Cover Art Morph Overlay (MiniPlayer <-> Full Cover OR Lyrics/Queue Compact Cover)
        if (isMorphingArt && currentTrack != null) {
            MorphingFloatingArtwork(
                track = currentTrack,
                auraPalette = auraPalette,
                expansionProgress = expansion,
                miniRect = miniArtworkRect,
                fullRect = effectiveTargetArtworkRect,
                startCornerDp = 11.dp,
                endCornerDp = activeSheetArtworkCornerDp
            )
        }

        // Studio Equalizer Modal
        AnimatedVisibility(
            visible = showEqualizer,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            EqualizerSheet(
                eqState = eqState,
                customPresets = customEqPresets,
                onUpdateEq = viewModel::updateEqState,
                onApplyPreset = viewModel::applyEqPreset,
                onSaveCustomPreset = viewModel::saveCustomEqPreset,
                onDeleteCustomPreset = viewModel::deleteCustomEqPreset,
                onTickHaptic = { viewModel.haptics.tickLight() },
                onDismiss = { showEqualizer = false }
            )
        }

        // Lyrics Manager Modal (Font Weight / Multi-Source Online Match / Upload / Offset Calibration)
        AnimatedVisibility(
            visible = showLyricsManager && currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            currentTrack?.let { active ->
                LyricsManagerSheet(
                    track = active,
                    currentLyric = currentLyric,
                    lyricSettings = lyricSettings,
                    onlineSearchState = onlineLyricSearch,
                    onSearchOnline = { title, artist, source ->
                        viewModel.smartMatchLyricsOnline(active, title, artist, source)
                    },
                    onSelectOnlineCandidate = { cand ->
                        viewModel.applyOnlineLyricCandidate(active, cand)
                        showLyricsManager = false
                    },
                    onImportLyricFile = {
                        lyricPickerLauncher.launch(arrayOf("*/*"))
                    },
                    onSavePastedLyrics = { raw ->
                        viewModel.saveCustomLyrics(active, raw, sourceLabel = "手动粘贴编辑")
                    },
                    onAdjustOffset = viewModel::adjustLyricOffset,
                    onUpdateLyricSettings = viewModel::updateLyricSettings,
                    onDismiss = { showLyricsManager = false }
                )
            }
        }

        // Smart Duplicate Detection & Conflict Resolution Modal
        AnimatedVisibility(
            visible = showDuplicateManager,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            DuplicateManagerSheet(
                duplicateGroups = duplicateGroups,
                onPlayPreviewTrack = { track, groupTracks ->
                    viewModel.playTrack(track, groupTracks)
                },
                onResolveGroupKeepOnly = viewModel::resolveDuplicateGroupKeepOnly,
                onResolveGroupDemoteOthers = viewModel::resolveDuplicateGroupDemoteOthers,
                onResolveAllKeepHighestQuality = viewModel::resolveAllDuplicatesKeepHighestQuality,
                onResolveAllDemoteLowerToGeneral = viewModel::resolveAllDuplicatesDemoteLowerToGeneral,
                onResolveExactQualityOnly = viewModel::resolveExactQualityDuplicatesOnly,
                onDismiss = { showDuplicateManager = false }
            )
        }

        // Import-time Quality Conflict Decision Dialog
        if (pendingImportConflicts.isNotEmpty()) {
            ImportConflictDialog(
                sourceLabel = pendingImportSourceLabel,
                conflicts = pendingImportConflicts,
                onConfirmDecisions = viewModel::resolveImportConflicts,
                onDismissSkipAll = viewModel::dismissImportConflicts
            )
        }

        // Pre-Scan Confirmation & Strict Metadata Filter Dialog
        if (showSmartScanDialog) {
            SmartScanConfirmDialog(
                initialConfig = scanFilter,
                onConfirmScan = { confirmedConfig ->
                    showSmartScanDialog = false
                    executeSmartScanWithConfig(confirmedConfig)
                },
                onDismiss = { showSmartScanDialog = false }
            )
        }

        // Global Top-Pinned Swipeable Undo Deletion Banner (takes priority at top)
        TopUndoBanner(
            state = undoBanner,
            onDismiss = viewModel::dismissUndoBanner,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // Global Top-Pinned Swipeable Lightweight Status Toast (never covers bottom controls)
        TopStatusToast(
            state = if (undoBanner == null) statusBanner else null,
            onDismiss = viewModel::dismissBanner,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // Global Deletion Confirmation Dialog (when DeletionGuardMode == CONFIRM_DIALOG)
        pendingConfirmDeletion?.let { req ->
            AlertDialog(
                onDismissRequest = viewModel::dismissPendingConfirmDeletion,
                title = { Text(req.title) },
                text = { Text(req.message) },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmPendingDeletion,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RubyAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("global_confirm_delete_button")
                    ) {
                        Text(req.confirmLabel)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::dismissPendingConfirmDeletion,
                        modifier = Modifier.testTag("global_cancel_delete_button")
                    ) {
                        Text("取消")
                    }
                }
            )
        }

        // Burn Lyrics Into Original Audio File Confirmation Dialog
        pendingBurnConfirm?.let { req ->
            AlertDialog(
                onDismissRequest = viewModel::dismissBurnConfirm,
                title = { Text("刻录歌词到原音频文件") },
                text = {
                    Text(
                        "即将把当前获取到的歌词（来源：${req.sourceLabel}）无损写入「${req.track.title}」的音频元数据标签（MP3 ID3v2 USLT / FLAC Vorbis LYRICS / M4A ©lyr）。\n\n原始音频流、封面与其他元数据将保持完整不变；若系统限制直接覆写，将自动申请写入授权或引导另存为完整副本。"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmBurnLyricsToAudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RubyAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("confirm_burn_lyrics_button")
                    ) {
                        Text("确认刻录")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::dismissBurnConfirm,
                        modifier = Modifier.testTag("cancel_burn_lyrics_button")
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
