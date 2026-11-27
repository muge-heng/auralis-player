package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.model.AiConfig
import com.example.model.AiProviderType
import com.example.model.AppVersionInfo
import com.example.model.AudioTransitionConfig
import com.example.model.AudioTransitionMode
import com.example.model.DangerZoneAction
import com.example.model.DeletionGuardMode
import com.example.model.HapticProfile
import com.example.model.LyricSettings
import com.example.model.NavBarStyle
import com.example.model.ScanFilterConfig
import com.example.model.lyricFontScaleLabel
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.adaptiveGoldColor
import com.example.viewmodel.PendingSettingsBackupImportPreview
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    navBarStyle: NavBarStyle,
    visualEffectsEnhanced: Boolean = false,
    showLibraryHero: Boolean,
    enableGeneralAudioMode: Boolean,
    deletionGuardMode: DeletionGuardMode = DeletionGuardMode.CONFIRM_DIALOG,
    hapticProfile: HapticProfile,
    scanFilter: ScanFilterConfig,
    lyricSettings: LyricSettings,
    audioTransition: AudioTransitionConfig = AudioTransitionConfig(),
    aiConfig: AiConfig,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetNavBarStyle: (NavBarStyle) -> Unit,
    onSetVisualEffectsEnhanced: (Boolean) -> Unit = {},
    onSetShowLibraryHero: (Boolean) -> Unit,
    onSetEnableGeneralAudioMode: (Boolean) -> Unit,
    onSetDeletionGuardMode: (DeletionGuardMode) -> Unit = {},
    onSetHapticProfile: (HapticProfile) -> Unit,
    onUpdateScanFilter: (ScanFilterConfig) -> Unit,
    onUpdateLyricSettings: (LyricSettings) -> Unit,
    onUpdateAudioTransition: (AudioTransitionConfig) -> Unit = {},
    onExecuteDangerZoneAction: (DangerZoneAction) -> Unit = {},
    onTriggerRescan: () -> Unit,
    onImportAudioFiles: () -> Unit,
    onImportFolder: () -> Unit,
    duplicateGroupCount: Int = 0,
    onOpenDuplicateManager: () -> Unit = {},
    onOpenEqualizer: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onExportSettingsBackup: (includeApiKey: Boolean) -> Unit = {},
    onImportSettingsBackup: () -> Unit = {},
    pendingBackupImport: PendingSettingsBackupImportPreview? = null,
    onConfirmImportSettingsBackup: (restoreApiKey: Boolean) -> Unit = {},
    onDismissImportSettingsBackup: () -> Unit = {},
    contentBottomPadding: Int
) {
    var activeSection by rememberSaveable { mutableStateOf(SettingsSection.HUB) }
    var showReleaseNotesSheet by remember { mutableStateOf(false) }
    var showVisualEnhanceConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = activeSection != SettingsSection.HUB) {
        activeSection = SettingsSection.HUB
    }

    AnimatedContent(
        targetState = activeSection,
        transitionSpec = {
            if (targetState == SettingsSection.HUB) {
                (slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(180)))
            } else {
                (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(180)))
            }
        },
        label = "settings_section_transition",
        modifier = Modifier.fillMaxSize()
    ) { section ->
        when (section) {
            SettingsSection.HUB -> SettingsHubContent(
                darkTheme = darkTheme,
                navBarStyle = navBarStyle,
                visualEffectsEnhanced = visualEffectsEnhanced,
                hapticProfile = hapticProfile,
                scanFilter = scanFilter,
                lyricSettings = lyricSettings,
                audioTransition = audioTransition,
                deletionGuardMode = deletionGuardMode,
                aiConfig = aiConfig,
                duplicateGroupCount = duplicateGroupCount,
                onOpenEqualizer = onOpenEqualizer,
                onOpenAiStudio = onOpenAiStudio,
                onTriggerRescan = onTriggerRescan,
                onImportAudioFiles = onImportAudioFiles,
                onImportFolder = onImportFolder,
                onOpenDuplicateManager = onOpenDuplicateManager,
                onNavigateSection = { activeSection = it },
                onOpenReleaseNotes = { showReleaseNotesSheet = true },
                contentBottomPadding = contentBottomPadding
            )

            SettingsSection.APPEARANCE -> AppearanceSettingsSubPage(
                darkTheme = darkTheme,
                navBarStyle = navBarStyle,
                visualEffectsEnhanced = visualEffectsEnhanced,
                hapticProfile = hapticProfile,
                onSetDarkTheme = onSetDarkTheme,
                onSetNavBarStyle = onSetNavBarStyle,
                onRequestEnableVisualEffects = { showVisualEnhanceConfirmDialog = true },
                onDisableVisualEffects = { onSetVisualEffectsEnhanced(false) },
                onSetHapticProfile = onSetHapticProfile,
                onBack = { activeSection = SettingsSection.HUB },
                contentBottomPadding = contentBottomPadding
            )

            SettingsSection.PLAYBACK_TRANSITION -> PlaybackTransitionSettingsSubPage(
                audioTransition = audioTransition,
                onUpdateAudioTransition = onUpdateAudioTransition,
                onBack = { activeSection = SettingsSection.HUB },
                contentBottomPadding = contentBottomPadding
            )

            SettingsSection.LYRICS -> LyricsSettingsSubPage(
                lyricSettings = lyricSettings,
                onUpdateLyricSettings = onUpdateLyricSettings,
                onBack = { activeSection = SettingsSection.HUB },
                contentBottomPadding = contentBottomPadding
            )

            SettingsSection.LIBRARY_SCAN -> LibraryAndScanSettingsSubPage(
                showLibraryHero = showLibraryHero,
                enableGeneralAudioMode = enableGeneralAudioMode,
                scanFilter = scanFilter,
                onSetShowLibraryHero = onSetShowLibraryHero,
                onSetEnableGeneralAudioMode = onSetEnableGeneralAudioMode,
                onUpdateScanFilter = onUpdateScanFilter,
                onTriggerRescan = onTriggerRescan,
                onBack = { activeSection = SettingsSection.HUB },
                contentBottomPadding = contentBottomPadding
            )

            SettingsSection.BACKUP_SYSTEM -> BackupAndSystemSettingsSubPage(
                deletionGuardMode = deletionGuardMode,
                onSetDeletionGuardMode = onSetDeletionGuardMode,
                onExportSettingsBackup = onExportSettingsBackup,
                onImportSettingsBackup = onImportSettingsBackup,
                onExecuteDangerZoneAction = onExecuteDangerZoneAction,
                onBack = { activeSection = SettingsSection.HUB },
                contentBottomPadding = contentBottomPadding
            )
        }
    }

    if (showVisualEnhanceConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showVisualEnhanceConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = RubyAccent
                )
            },
            title = { Text("开启视觉效果增强？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "开启后将激活旗舰级实时光影渲染管线，包含：悬浮播放条与悬浮底栏的液态玻璃光学折射及色散边缘、全局专辑流光氛围画布、播放页 AGSL 流体极光着色器与专辑封面动态光晕。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "性能与功耗提醒：该模式对 GPU 算力与内存带宽有更高要求，在部分中低端机型或低电量状态下可能出现耗电加快、机身发热或轻微掉帧。你可以随时在「工坊 → 外观、动效与触觉」中关闭以切回流畅平衡模式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVisualEnhanceConfirmDialog = false
                        onSetVisualEffectsEnhanced(true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RubyAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_enable_visual_effects_button")
                ) {
                    Text("确认开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVisualEnhanceConfirmDialog = false }) {
                    Text("暂不开启")
                }
            }
        )
    }

    if (pendingBackupImport != null) {
        SettingsBackupImportConfirmDialog(
            pending = pendingBackupImport,
            onConfirm = onConfirmImportSettingsBackup,
            onDismiss = onDismissImportSettingsBackup
        )
    }

    if (showReleaseNotesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showReleaseNotesSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            ReleaseNotesSheetContent()
        }
    }
}

@Composable
private fun SettingsHubContent(
    darkTheme: Boolean,
    navBarStyle: NavBarStyle,
    visualEffectsEnhanced: Boolean,
    hapticProfile: HapticProfile,
    scanFilter: ScanFilterConfig,
    lyricSettings: LyricSettings,
    audioTransition: AudioTransitionConfig,
    deletionGuardMode: DeletionGuardMode,
    aiConfig: AiConfig,
    duplicateGroupCount: Int,
    onOpenEqualizer: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onTriggerRescan: () -> Unit,
    onImportAudioFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onOpenDuplicateManager: () -> Unit,
    onNavigateSection: (SettingsSection) -> Unit,
    onOpenReleaseNotes: () -> Unit,
    contentBottomPadding: Int
) {
    val goldAccent = adaptiveGoldColor()
    val appearanceSummary = buildString {
        append(if (darkTheme) "深色画布" else "浅色画布")
        append(" · ")
        append(navBarStyle.label)
        if (visualEffectsEnhanced) append(" · 旗舰光影")
        append(" · ")
        append(hapticProfile.label)
    }
    val transitionSummary = buildString {
        append(audioTransition.mode.label)
        if (audioTransition.mode.supportsDurationSlider) {
            append(" · ")
            append(String.format(Locale.US, "%.1fs", audioTransition.durationMs / 1000f))
        }
        if (audioTransition.fadeOnPlayPause) append(" · 暂停淡出")
    }
    val lyricSummary = buildString {
        append("字重 ${lyricSettings.fontWeight}")
        append(" · ")
        append(lyricFontScaleLabel(lyricSettings.fontScale).substringBefore(" "))
        append(" · ")
        append(lyricSettings.activeOnlineSource.displayName)
        if (lyricSettings.enableElasticLyricWave) append(" · 弹性波浪")
        else if (lyricSettings.enableLyricBlur) append(" · 景深模糊")
    }
    val librarySummary =
        "≥${scanFilter.minDurationSec}秒 · ≥${scanFilter.minSizeKb}KB · ${scanFilter.excludedFolders.size} 个屏蔽目录"
    val backupSystemSummary =
        "${deletionGuardMode.shortLabel} · 本地备份恢复 · 危险区清除"

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
        // Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(SettingsSection.HUB.title, style = MaterialTheme.typography.headlineLarge)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(RubyAccent.copy(alpha = 0.14f))
                            .border(0.75.dp, RubyAccent.copy(alpha = 0.42f), RoundedCornerShape(50))
                            .clickable(onClick = onOpenReleaseNotes)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("settings_header_version_badge")
                    ) {
                        Text(
                            text = "v${AppVersionInfo.CURRENT_VERSION}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = RubyAccent
                        )
                    }
                }
                Text(
                    text = SettingsSection.HUB.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick Studio Doors: Hardware EQ & AI Studio
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    title = "硬件均衡器",
                    subtitle = "10 段频响 · 自定义预设",
                    icon = Icons.Rounded.Equalizer,
                    onClick = onOpenEqualizer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_quick_eq")
                )
                QuickActionCard(
                    title = "AI 声学对话",
                    subtitle = if (aiConfig.activeProvider == AiProviderType.GEMINI) {
                        "Google Gemini · 接口预留"
                    } else {
                        "${aiConfig.deepSeekModelId} · 思考 ${aiConfig.reasoningEffort.shortLabel}"
                    },
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = onOpenAiStudio,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_quick_ai")
                )
            }
        }

        // Quick Audio Import & Duplicate Check Card
        item {
            SettingsCard(
                icon = Icons.Rounded.LibraryMusic,
                title = "音频导入与曲库健康",
                subtitle = "随时扫描设备曲库、导入单曲或文件夹，并一键排查音质冲突"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onTriggerRescan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RubyAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("settings_import_smart_scan")
                    ) {
                        Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("智能扫描", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onImportAudioFiles,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_import_single")
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入单曲", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onImportFolder,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("settings_import_folder")
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("文件夹", maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (duplicateGroupCount > 0) goldAccent.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .border(
                            0.8.dp,
                            if (duplicateGroupCount > 0) goldAccent.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(onClick = onOpenDuplicateManager)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("settings_open_duplicate_manager"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CompareArrows,
                        contentDescription = null,
                        tint = if (duplicateGroupCount > 0) goldAccent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "智能查重与音质冲突清理",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (duplicateGroupCount > 0) {
                                "发现 $duplicateGroupCount 组重复曲目，支持一键保留高音质"
                            } else {
                                "当前曲库纯净 · 导入时自动去重与音质决策"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (duplicateGroupCount > 0) RubyAccent
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (duplicateGroupCount > 0) "$duplicateGroupCount 组" else "纯净",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (duplicateGroupCount > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Categorized Multi-Level Preferences Menu
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                SettingsCategoryRow(
                    icon = Icons.Rounded.DarkMode,
                    iconTint = RubyAccent,
                    title = SettingsSection.APPEARANCE.title,
                    summary = appearanceSummary,
                    badge = if (visualEffectsEnhanced) "光影增强" else null,
                    badgeColor = RubyAccent,
                    onClick = { onNavigateSection(SettingsSection.APPEARANCE) },
                    testTag = "settings_nav_appearance"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
                SettingsCategoryRow(
                    icon = Icons.Rounded.SwapHoriz,
                    iconTint = Color(0xFF42A5F5),
                    title = SettingsSection.PLAYBACK_TRANSITION.title,
                    summary = transitionSummary,
                    badge = if (audioTransition.mode != AudioTransitionMode.GAPLESS) audioTransition.mode.shortLabel else null,
                    badgeColor = Color(0xFF42A5F5),
                    onClick = { onNavigateSection(SettingsSection.PLAYBACK_TRANSITION) },
                    testTag = "settings_nav_playback_transition"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
                SettingsCategoryRow(
                    icon = Icons.Rounded.Lyrics,
                    iconTint = goldAccent,
                    title = SettingsSection.LYRICS.title,
                    summary = lyricSummary,
                    badge = if (lyricSettings.enableElasticLyricWave) "弹性波浪" else null,
                    badgeColor = RubyAccent,
                    onClick = { onNavigateSection(SettingsSection.LYRICS) },
                    testTag = "settings_nav_lyrics"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
                SettingsCategoryRow(
                    icon = Icons.Rounded.FilterAlt,
                    iconTint = EmeraldLossless,
                    title = SettingsSection.LIBRARY_SCAN.title,
                    summary = librarySummary,
                    onClick = { onNavigateSection(SettingsSection.LIBRARY_SCAN) },
                    testTag = "settings_nav_library_scan"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
                SettingsCategoryRow(
                    icon = Icons.Rounded.Backup,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = SettingsSection.BACKUP_SYSTEM.title,
                    summary = backupSystemSummary,
                    badge = "备份/清理",
                    badgeColor = goldAccent,
                    onClick = { onNavigateSection(SettingsSection.BACKUP_SYSTEM) },
                    testTag = "settings_nav_backup_system"
                )
            }
        }

        // Compact About & Release Notes Entry Card
        item {
            val latestNote = AppVersionInfo.RELEASE_NOTES.first()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                    .clickable(onClick = onOpenReleaseNotes)
                    .padding(16.dp)
                    .testTag("settings_open_release_notes_button"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RubyAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = RubyAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "关于 ${AppVersionInfo.APP_DISPLAY_NAME} 与版本日志",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldLossless.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${latestNote.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldLossless
                                )
                            }
                        }
                        Text(
                            text = latestNote.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "查看版本日志",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "${AppVersionInfo.APK_COMPAT_LABEL} · 共记录 ${AppVersionInfo.RELEASE_NOTES.size} 个公开版本与早期演进脉络",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    summary: String,
    badge: String? = null,
    badgeColor: Color = RubyAccent,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!badge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = RubyAccent)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReleaseNotesSheetContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "${AppVersionInfo.APP_DISPLAY_NAME} 版本更新日志",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "当前 v${AppVersionInfo.CURRENT_VERSION} · ${AppVersionInfo.APK_COMPAT_LABEL}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AppVersionInfo.RELEASE_NOTES, key = { it.version }) { release ->
                val isCurrent = release.version == AppVersionInfo.CURRENT_VERSION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            width = if (isCurrent) 1.dp else 0.5.dp,
                            color = if (isCurrent) RubyAccent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "v${release.version}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) RubyAccent else MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isCurrent) EmeraldLossless.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = release.tag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrent) EmeraldLossless else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = release.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val releaseGold = adaptiveGoldColor()
                        release.highlights.forEach { bullet ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrent) RubyAccent else releaseGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item(key = "pre_release_origin") {
                val originGold = adaptiveGoldColor()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(originGold.copy(alpha = 0.09f))
                        .border(
                            width = 0.75.dp,
                            color = originGold.copy(alpha = 0.42f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "v0.9.0 之前 · 早期演进脉络",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = originGold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(originGold.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "版本溯源",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = originGold
                            )
                        }
                    }
                    Text(
                        text = AppVersionInfo.PRE_RELEASE_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppVersionInfo.PRE_RELEASE_MILESTONES.forEachIndexed { idx, milestone ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(RubyAccent.copy(alpha = 0.14f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "阶段 ${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = RubyAccent
                                        )
                                    }
                                    Text(
                                        text = milestone.phase,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = milestone.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
