package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiConfig
import com.example.model.AiProviderType
import com.example.model.AppVersionInfo
import com.example.model.DeletionGuardMode
import com.example.model.HapticProfile
import com.example.model.LYRIC_FONT_SCALE_TIERS
import com.example.model.LyricSettings
import com.example.model.LyricSource
import com.example.model.NavBarStyle
import com.example.model.ScanFilterConfig
import com.example.model.lyricFontScaleLabel
import com.example.model.snapLyricFontScale
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    navBarStyle: NavBarStyle,
    showLibraryHero: Boolean,
    enableGeneralAudioMode: Boolean,
    deletionGuardMode: DeletionGuardMode = DeletionGuardMode.CONFIRM_DIALOG,
    hapticProfile: HapticProfile,
    scanFilter: ScanFilterConfig,
    lyricSettings: LyricSettings,
    aiConfig: AiConfig,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetNavBarStyle: (NavBarStyle) -> Unit,
    onSetShowLibraryHero: (Boolean) -> Unit,
    onSetEnableGeneralAudioMode: (Boolean) -> Unit,
    onSetDeletionGuardMode: (DeletionGuardMode) -> Unit = {},
    onSetHapticProfile: (HapticProfile) -> Unit,
    onUpdateScanFilter: (ScanFilterConfig) -> Unit,
    onUpdateLyricSettings: (LyricSettings) -> Unit,
    onTriggerRescan: () -> Unit,
    onImportAudioFiles: () -> Unit,
    onImportFolder: () -> Unit,
    duplicateGroupCount: Int = 0,
    onOpenDuplicateManager: () -> Unit = {},
    onOpenEqualizer: () -> Unit,
    onOpenAiStudio: () -> Unit,
    contentBottomPadding: Int
) {
    val context = LocalContext.current
    var newExcludeFolder by remember { mutableStateOf("") }
    var showReleaseNotesSheet by remember { mutableStateOf(false) }

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
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("声学工坊与偏好", style = MaterialTheme.typography.headlineLarge)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(RubyAccent.copy(alpha = 0.14f))
                            .border(0.75.dp, RubyAccent.copy(alpha = 0.42f), RoundedCornerShape(50))
                            .clickable { showReleaseNotesSheet = true }
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
                    "定制智能扫描筛选规则、多源逐字歌词引擎、触觉震动与 AI 思考强度",
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
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Audio Import Hub & Library Layout Preferences (always accessible even when Hero Card is hidden)
        item {
            SettingsCard(
                icon = Icons.Rounded.LibraryMusic,
                title = "音频导入工坊与曲库展示",
                subtitle = "随时扫描设备音乐、导入单曲或文件夹，并定制首页卡片与普通音频区模式"
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

                // Smart Duplicate Detection & Conflict Resolution Entry
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (duplicateGroupCount > 0) ChampagneGold.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .border(
                            0.8.dp,
                            if (duplicateGroupCount > 0) ChampagneGold.copy(alpha = 0.55f)
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
                        tint = if (duplicateGroupCount > 0) Color(0xFFB37828) else MaterialTheme.colorScheme.primary,
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
                                "发现 $duplicateGroupCount 组重复曲目，支持一键保留高音质或分流普通音频区"
                            } else {
                                "当前曲库纯净 · 导入时自动跳过同质重复并对异质版本弹窗决策"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("首页显示顶部唱片馆卡片", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "关闭后曲库顶部更紧凑清爽，仍可在此处随时导入音频",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showLibraryHero,
                        onCheckedChange = onSetShowLibraryHero,
                        modifier = Modifier.testTag("settings_show_hero_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开启普通本地音频播放器模式", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开放独立「普通音频区」隔离录音/播客等非音乐音频，不混入歌单与 AI 策展",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableGeneralAudioMode,
                        onCheckedChange = onSetEnableGeneralAudioMode,
                        modifier = Modifier.testTag("settings_general_audio_mode_switch")
                    )
                }
            }
        }

        // System Default Music Player Setup Card
        item {
            SettingsCard(
                icon = Icons.Rounded.Smartphone,
                title = "系统默认音乐播放器",
                subtitle = "Auralis 已注册 audio/* 与系统音乐主入口，可在文件管理器中设为默认打开方式"
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("前往系统「默认打开」设置")
                }
            }
        }

        // Smart Scan & Intelligent Filter Rules
        item {
            SettingsCard(
                icon = Icons.Rounded.FilterAlt,
                title = "智能扫描与音频筛选引擎",
                subtitle = "自动过滤语音备忘录、系统铃声与聊天碎音频，只保留纯净音乐"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("最短曲目时长过滤", style = MaterialTheme.typography.bodyMedium)
                        Text("${scanFilter.minDurationSec} 秒", style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
                    }
                    Slider(
                        value = scanFilter.minDurationSec.toFloat(),
                        onValueChange = {
                            onUpdateScanFilter(scanFilter.copy(minDurationSec = it.toInt()))
                        },
                        valueRange = 0f..120f,
                        steps = 7
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("最小文件体积过滤", style = MaterialTheme.typography.bodyMedium)
                        Text("${scanFilter.minSizeKb} KB", style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
                    }
                    Slider(
                        value = scanFilter.minSizeKb.toFloat(),
                        onValueChange = {
                            onUpdateScanFilter(scanFilter.copy(minSizeKb = it.toInt()))
                        },
                        valueRange = 0f..2000f,
                        steps = 7
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("仅扫描系统标记为音乐的音频", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = scanFilter.excludeSystemAudio,
                            onCheckedChange = {
                                onUpdateScanFilter(scanFilter.copy(excludeSystemAudio = it))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("仅筛选含元数据音频", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "排除缺乏歌手、专辑与封面的杂项语音或剪辑音频",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = scanFilter.requireMetadata,
                            onCheckedChange = {
                                onUpdateScanFilter(scanFilter.copy(requireMetadata = it))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("仅筛选含歌手信息", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "严格校验艺术家标签，过滤“未知艺术家”文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = scanFilter.requireArtist,
                            onCheckedChange = {
                                onUpdateScanFilter(scanFilter.copy(requireArtist = it))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("仅筛选含专辑或封面", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "需具备有效专辑名或内嵌封面方可归入音乐曲库",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = scanFilter.requireAlbumOrArt,
                            onCheckedChange = {
                                onUpdateScanFilter(scanFilter.copy(requireAlbumOrArt = it))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("未达标音频归入普通音频区", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "关闭则直接跳过未达标文件；开启则收纳至独立普通音频区",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = scanFilter.classifyUnverifiedAsGeneralAudio,
                            onCheckedChange = {
                                onUpdateScanFilter(scanFilter.copy(classifyUnverifiedAsGeneralAudio = it))
                            }
                        )
                    }

                    Text("智能屏蔽目录（点击移除）", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        scanFilter.excludedFolders.forEach { folder ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    onUpdateScanFilter(
                                        scanFilter.copy(excludedFolders = scanFilter.excludedFolders - folder)
                                    )
                                },
                                label = { Text("$folder ×", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newExcludeFolder,
                            onValueChange = { newExcludeFolder = it },
                            placeholder = { Text("添加屏蔽关键字，如 QQVoice") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val cleaned = newExcludeFolder.trim()
                                if (cleaned.isNotEmpty()) {
                                    onUpdateScanFilter(
                                        scanFilter.copy(excludedFolders = scanFilter.excludedFolders + cleaned)
                                    )
                                    newExcludeFolder = ""
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("添加")
                        }
                    }

                    Button(
                        onClick = onTriggerRescan,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_rescan_button")
                    ) {
                        Text("按新规则重新智能扫描")
                    }
                }
            }
        }

        // Tactile Haptic Feedback System
        item {
            SettingsCard(
                icon = Icons.Rounded.Vibration,
                title = "深度触觉反馈系统",
                subtitle = "通过阻尼震动波封为播放、切歌、红心收藏与歌词跳转赋予机械实感"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HapticProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = hapticProfile == profile,
                            onClick = { onSetHapticProfile(profile) },
                            label = { Text(profile.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    text = hapticProfile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Lyric Engine Preferences
        item {
            SettingsCard(
                icon = Icons.Rounded.Lyrics,
                title = "多源逐字歌词视觉引擎",
                subtitle = "适配网易云 / QQ音乐 / 酷狗 / LRCLIB 及本地 .qrc/.yrc/.krc/.lrc/.ttml"
            ) {
                // Live font weight & size preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "流光声场中，每一行歌词皆清晰可感 · Auralis",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight(lyricSettings.fontWeight.coerceIn(300, 900)),
                        fontSize = (18f * lyricSettings.fontScale).sp,
                        lineHeight = (24f * lyricSettings.fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("歌词字体粗细", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = lyricSettings.fontWeightLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ChampagneGold
                    )
                }
                Slider(
                    value = lyricSettings.fontWeight.toFloat(),
                    onValueChange = { raw ->
                        val snapped = ((raw / 100f).roundToInt() * 100).coerceIn(300, 900)
                        if (snapped != lyricSettings.fontWeight) {
                            onUpdateLyricSettings(lyricSettings.copy(fontWeight = snapped))
                        }
                    },
                    valueRange = 300f..900f,
                    steps = 5,
                    modifier = Modifier.testTag("settings_lyric_weight_slider")
                )

                val snappedScale = snapLyricFontScale(lyricSettings.fontScale)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("歌词字号档位", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = lyricFontScaleLabel(snappedScale),
                        style = MaterialTheme.typography.labelMedium,
                        color = ChampagneGold
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_lyric_scale_tiers"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LYRIC_FONT_SCALE_TIERS.forEach { (tierScale, tierLabel) ->
                        val isSelected = abs(snappedScale - tierScale) < 0.02f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) ChampagneGold.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) ChampagneGold else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (!isSelected) {
                                        onUpdateLyricSettings(lyricSettings.copy(fontScale = tierScale))
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tierLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                Text("默认在线歌词获取源", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LyricSource.entries.forEach { src ->
                        val selected = lyricSettings.activeOnlineSource == src
                        val srcColor = Color(src.accentHex)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateLyricSettings(lyricSettings.copy(activeOnlineSource = src))
                            },
                            label = { Text(src.displayName) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = srcColor.copy(alpha = 0.22f),
                                selectedLabelColor = srcColor
                            ),
                            modifier = Modifier.testTag("settings_lyric_src_${src.id}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("开启模拟逐字扫光动效", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = lyricSettings.enableSimulatedWordByWord,
                        onCheckedChange = {
                            onUpdateLyricSettings(lyricSettings.copy(enableSimulatedWordByWord = it))
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("显示双语歌词译文", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = lyricSettings.showTranslation,
                        onCheckedChange = {
                            onUpdateLyricSettings(lyricSettings.copy(showTranslation = it))
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("非本行渐进景深模糊", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Apple Music 风格：非当前演唱行按距离柔化虚焦与微缩",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lyricSettings.enableLyricBlur,
                        onCheckedChange = {
                            onUpdateLyricSettings(lyricSettings.copy(enableLyricBlur = it))
                        },
                        modifier = Modifier.testTag("settings_lyric_blur_switch")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("沉浸歌词模式", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "切入歌词舞台时隐藏顶部与底部控制栏，独享全屏流光歌词",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lyricSettings.enableImmersiveLyrics,
                        onCheckedChange = {
                            onUpdateLyricSettings(lyricSettings.copy(enableImmersiveLyrics = it))
                        },
                        modifier = Modifier.testTag("settings_lyric_immersive_switch")
                    )
                }
            }
        }

        // Appearance Theme & Navigation Bar Style
        item {
            SettingsCard(
                icon = Icons.Rounded.DarkMode,
                title = "视觉主题与导航栏样式",
                subtitle = "全面适配曜石深色与通透浅色画布，可选 Apple Liquid Glass 悬浮胶囊底栏"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("曜石深色画布", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (darkTheme) "当前为暗黑曜石深色主题" else "当前为通透云白浅色主题（全界面已适配）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = onSetDarkTheme,
                        modifier = Modifier.testTag("settings_dark_theme_switch")
                    )
                }

                Text("底部四段导航栏样式", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NavBarStyle.entries.forEach { style ->
                        val selected = navBarStyle == style
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) RubyAccent.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (selected) 1.dp else 0.6.dp,
                                    color = if (selected) RubyAccent else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSetNavBarStyle(style) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("settings_nav_style_${style.name.lowercase()}"),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = style.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) RubyAccent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = style.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        // Unified Deletion Guard Mode Setting Card
        item {
            SettingsCard(
                icon = Icons.Rounded.Shield,
                title = "防误删保护机制",
                subtitle = "统一守护自定义 EQ 预设、AI 对话记录、歌单曲目与曲库移出等全部删除操作"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeletionGuardMode.entries.forEach { mode ->
                        val selected = deletionGuardMode == mode
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) RubyAccent.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (selected) 1.dp else 0.6.dp,
                                    color = if (selected) RubyAccent else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSetDeletionGuardMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("settings_deletion_guard_${mode.name.lowercase()}"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) RubyAccent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3
                            )
                        }
                    }
                }
                Text(
                    text = if (deletionGuardMode == DeletionGuardMode.CONFIRM_DIALOG) {
                        "当前模式：删除前强制弹出确认框，彻底杜绝误触丢失。"
                    } else {
                        "当前模式：执行删除后在屏幕顶部弹出置顶提示条，支持 6 秒内一键无损撤销，或左右/上滑关闭。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // About & Semantic Version Changelog Card
        item {
            val latestNote = AppVersionInfo.RELEASE_NOTES.first()
            SettingsCard(
                icon = Icons.Rounded.NewReleases,
                title = "关于 ${AppVersionInfo.APP_DISPLAY_NAME} 与版本记录",
                subtitle = "语义版本 v${AppVersionInfo.CURRENT_VERSION} · ${AppVersionInfo.APK_COMPAT_LABEL}"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "v${latestNote.version}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RubyAccent
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldLossless.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = latestNote.tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldLossless
                                )
                            }
                        }
                        Text(
                            text = "基线 v${AppVersionInfo.PREVIOUS_VERSION}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = latestNote.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    latestNote.highlights.forEach { itemText ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = ChampagneGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = itemText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showReleaseNotesSheet = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_open_release_notes_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("查看完整版本日志与早期演进脉络（共 ${AppVersionInfo.RELEASE_NOTES.size} 个公开版本）")
                }
            }
        }
    }

    if (showReleaseNotesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showReleaseNotesSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
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
                                release.highlights.forEach { bullet ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isCurrent) RubyAccent else ChampagneGold,
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

                    // Pre-v0.9.0 GitHub Origin & Evolution Storyline Card
                    item(key = "pre_release_origin") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(ChampagneGold.copy(alpha = 0.08f))
                                .border(
                                    width = 0.75.dp,
                                    color = ChampagneGold.copy(alpha = 0.40f),
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
                                    color = ChampagneGold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(ChampagneGold.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "版本溯源",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ChampagneGold
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
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RubyAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = RubyAccent, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        content()
    }
}
