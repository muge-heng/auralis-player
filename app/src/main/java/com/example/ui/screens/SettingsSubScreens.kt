package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SettingsBackupCodec
import com.example.model.AudioTransitionConfig
import com.example.model.AudioTransitionMode
import com.example.model.DangerZoneAction
import com.example.model.DeletionGuardMode
import com.example.model.HapticProfile
import com.example.model.LYRIC_FONT_SCALE_TIERS
import com.example.model.LyricSettings
import com.example.model.LyricSource
import com.example.model.NavBarStyle
import com.example.model.ScanFilterConfig
import com.example.model.lyricFontScaleLabel
import com.example.model.snapLyricFontScale
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.adaptiveGoldColor
import com.example.ui.theme.adaptiveLyricSourceColor
import com.example.viewmodel.PendingSettingsBackupImportPreview
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SettingsSection(
    val title: String,
    val subtitle: String
) {
    HUB(
        title = "声学工坊",
        subtitle = "调音、过渡、歌词与个性偏好"
    ),
    APPEARANCE(
        title = "外观与触感",
        subtitle = "主题画布、悬浮底栏、实时光影与触觉反馈"
    ),
    PLAYBACK_TRANSITION(
        title = "播放与过渡",
        subtitle = "切歌衔接方式、淡入淡出时长与播放控制"
    ),
    LYRICS(
        title = "歌词与舞台",
        subtitle = "字体排版、在线歌词源、弹性波浪与沉浸显示"
    ),
    LIBRARY_SCAN(
        title = "曲库与扫描",
        subtitle = "首页卡片、普通音频分区与本地扫描筛选"
    ),
    BACKUP_SYSTEM(
        title = "数据与安全",
        subtitle = "设置备份恢复、防误删保护、默认应用与数据清理"
    )
}

/**
 * Opens Android's system "Default Apps" screen directly (API 24+),
 * instead of falling into the generic App Info page.
 */
fun openSystemDefaultAppsSettings(context: Context): Boolean {
    val candidates = buildList {
        add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                Intent(
                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        }
        add(Intent(Settings.ACTION_SETTINGS))
    }
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) {
            return true
        }
    }
    return false
}

/**
 * On Android 12+ (API 31+), jumps directly to this app's "Open by default" screen.
 */
fun openAppOpenByDefaultSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val directIntent = Intent(
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(directIntent) }.isSuccess) {
            return true
        }
    }
    return openSystemDefaultAppsSettings(context)
}

@Composable
internal fun SettingsSubPageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag("settings_subpage_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回工坊主页",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SettingsCard(
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RubyAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = RubyAccent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

@Composable
internal fun AppearanceSettingsSubPage(
    darkTheme: Boolean,
    navBarStyle: NavBarStyle,
    visualEffectsEnhanced: Boolean,
    hapticProfile: HapticProfile,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetNavBarStyle: (NavBarStyle) -> Unit,
    onRequestEnableVisualEffects: () -> Unit,
    onDisableVisualEffects: () -> Unit,
    onSetHapticProfile: (HapticProfile) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Int
) {
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
            SettingsSubPageHeader(
                title = SettingsSection.APPEARANCE.title,
                subtitle = SettingsSection.APPEARANCE.subtitle,
                onBack = onBack
            )
        }

        item {
            SettingsCard(
                icon = Icons.Rounded.DarkMode,
                title = "视觉主题与光影引擎",
                subtitle = "悬浮播放条与 Liquid Glass 底栏均已移除固色底板，支持开启旗舰级实时光影增强"
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NavBarStyle.entries.forEach { style ->
                        val selected = navBarStyle == style
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
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
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = style.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) RubyAccent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = style.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    thickness = 0.6.dp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (visualEffectsEnhanced) RubyAccent.copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .border(
                            width = if (visualEffectsEnhanced) 1.dp else 0.6.dp,
                            color = if (visualEffectsEnhanced) RubyAccent.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val enhanceGold = adaptiveGoldColor()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (visualEffectsEnhanced) RubyAccent else enhanceGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "视觉效果增强",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (visualEffectsEnhanced) RubyAccent.copy(alpha = 0.18f)
                                            else enhanceGold.copy(alpha = 0.16f)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (visualEffectsEnhanced) "旗舰光影已开启" else "旗舰光影",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (visualEffectsEnhanced) RubyAccent else enhanceGold,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = "解锁实时液态玻璃光学折射与色散边缘、全局专辑流光氛围画布、播放页 AGSL 流体极光着色器与封面动态光晕",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = visualEffectsEnhanced,
                            onCheckedChange = { targetEnabled ->
                                if (targetEnabled) onRequestEnableVisualEffects()
                                else onDisableVisualEffects()
                            },
                            modifier = Modifier.testTag("settings_visual_effects_enhanced_switch")
                        )
                    }

                    val warningAccent = adaptiveGoldColor()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(warningAccent.copy(alpha = if (darkTheme) 0.12f else 0.10f))
                            .border(
                                0.6.dp,
                                warningAccent.copy(alpha = 0.36f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = warningAccent,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(16.dp)
                        )
                        Text(
                            text = "性能提示：开启后将持续运行多通道 GPU 模糊折射、着色器流光与动态光晕，可能带来更高的性能要求、功耗与机身发热，建议中高端及以上设备开启。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(
                icon = Icons.Rounded.Vibration,
                title = "深度触觉反馈系统",
                subtitle = "通过阻尼震动波封为播放、切歌、底栏滑选与歌词跳转赋予机械实感"
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
    }
}

@Composable
internal fun PlaybackTransitionSettingsSubPage(
    audioTransition: AudioTransitionConfig,
    onUpdateAudioTransition: (AudioTransitionConfig) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Int
) {
    val goldAccent = adaptiveGoldColor()

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
            SettingsSubPageHeader(
                title = SettingsSection.PLAYBACK_TRANSITION.title,
                subtitle = SettingsSection.PLAYBACK_TRANSITION.subtitle,
                onBack = onBack
            )
        }

        item {
            SettingsCard(
                icon = Icons.Rounded.SwapHoriz,
                title = "曲目衔接过渡模式",
                subtitle = "自由挑选适合无损发烧聆听、专辑连听或氛围歌单的切歌过渡风格"
            ) {
                var showMoreModes by remember { mutableStateOf(audioTransition.mode.isSecondary) }
                val chevronRotation by animateFloatAsState(
                    targetValue = if (showMoreModes) 180f else 0f,
                    label = "more_transition_chevron"
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioTransitionMode.primaryModes.forEach { mode ->
                        TransitionModeOptionRow(
                            mode = mode,
                            selected = audioTransition.mode == mode,
                            goldAccent = goldAccent,
                            onSelect = {
                                onUpdateAudioTransition(audioTransition.copy(mode = mode))
                            }
                        )
                    }

                    // Expandable "更多效果" drawer for secondary transition modes (SMOOTH_FADE, VINYL_BRAKE)
                    val secondarySelected = audioTransition.mode.isSecondary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (secondarySelected && !showMoreModes) RubyAccent.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
                            )
                            .border(
                                width = if (secondarySelected && !showMoreModes) 0.9.dp else 0.6.dp,
                                color = if (secondarySelected && !showMoreModes) RubyAccent.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { showMoreModes = !showMoreModes }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("settings_transition_more_effects_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = if (secondarySelected) RubyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "更多效果",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (secondarySelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(RubyAccent.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = audioTransition.mode.shortLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RubyAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "柔和淡出淡入 · 黑胶缓停减速",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = if (showMoreModes) "收起更多效果" else "展开更多效果",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = showMoreModes,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioTransitionMode.secondaryModes.forEach { mode ->
                                TransitionModeOptionRow(
                                    mode = mode,
                                    selected = audioTransition.mode == mode,
                                    goldAccent = goldAccent,
                                    onSelect = {
                                        onUpdateAudioTransition(audioTransition.copy(mode = mode))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsCard(
                icon = Icons.Rounded.GraphicEq,
                title = "过渡时长与触发细节",
                subtitle = "精细控制交叉淡化时长、手动切歌响应与暂停/恢复消噪微淡入"
            ) {
                if (audioTransition.mode.supportsDurationSlider) {
                    val secValue = (audioTransition.durationMs / 1000f).coerceIn(1.5f, 10.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自动过渡时长", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = String.format(Locale.US, "%.1f 秒", secValue),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = goldAccent
                        )
                    }
                    Slider(
                        value = secValue,
                        onValueChange = { raw ->
                            val snappedHalfSec = (raw * 2f).roundToInt() / 2f
                            val ms = (snappedHalfSec * 1000f).roundToInt().coerceIn(1500, 10_000)
                            if (ms != audioTransition.durationMs) {
                                onUpdateAudioTransition(audioTransition.copy(durationMs = ms))
                            }
                        },
                        valueRange = 1.5f..10.0f,
                        steps = 16,
                        modifier = Modifier.testTag("settings_transition_duration_slider")
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        thickness = 0.6.dp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("手动切歌应用过渡", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "默认关闭以保持点按切歌即时响应；开启后点击上一首/下一首时将施加轻快短时过渡（约 0.6s–1.4s）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = audioTransition.fadeOnManualSkip,
                        enabled = audioTransition.mode != AudioTransitionMode.GAPLESS,
                        onCheckedChange = {
                            onUpdateAudioTransition(audioTransition.copy(fadeOnManualSkip = it))
                        },
                        modifier = Modifier.testTag("settings_transition_manual_skip_switch")
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    thickness = 0.6.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("暂停 / 恢复柔滑淡入淡出", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "在暂停与继续播放瞬间施加约 160ms 声压包络，消除数字切换突兀断层与爆音",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = audioTransition.fadeOnPlayPause,
                        onCheckedChange = {
                            onUpdateAudioTransition(audioTransition.copy(fadeOnPlayPause = it))
                        },
                        modifier = Modifier.testTag("settings_transition_play_pause_fade_switch")
                    )
                }

                Text(
                    text = "智能保护：「人声接唱」仅在当前与下一曲均具备同步歌词时跳过前尾奏，缺词曲目将自动退化为智能声场混接；播放「普通音频区」播客或录音时自动降级为柔和淡出淡入避免人声叠音；单曲循环与 A-B 复读期间暂停跨曲过渡。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TransitionModeOptionRow(
    mode: AudioTransitionMode,
    selected: Boolean,
    goldAccent: Color,
    onSelect: () -> Unit
) {
    val modeIcon = when (mode) {
        AudioTransitionMode.GAPLESS -> Icons.Rounded.GraphicEq
        AudioTransitionMode.SMOOTH_FADE -> Icons.Rounded.TrendingDown
        AudioTransitionMode.CROSSFADE -> Icons.Rounded.SwapHoriz
        AudioTransitionMode.AUTOMIX -> Icons.Rounded.AutoAwesome
        AudioTransitionMode.LYRIC_BRIDGE -> Icons.Rounded.Lyrics
        AudioTransitionMode.VINYL_BRAKE -> Icons.Rounded.Album
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag("settings_transition_mode_${mode.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) RubyAccent.copy(alpha = 0.20f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = modeIcon,
                contentDescription = null,
                tint = if (selected) RubyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
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
                    text = mode.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) RubyAccent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (mode == AudioTransitionMode.AUTOMIX) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(goldAccent.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "智能自适应",
                            style = MaterialTheme.typography.labelSmall,
                            color = goldAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (mode == AudioTransitionMode.LYRIC_BRIDGE) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RubyAccent.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "跳过前尾奏",
                            style = MaterialTheme.typography.labelSmall,
                            color = RubyAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (mode == AudioTransitionMode.CROSSFADE) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldLossless.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "等功率叠加",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldLossless,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Text(
                text = mode.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (selected) "已选中" else "未选中",
            tint = if (selected) RubyAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LyricsSettingsSubPage(
    lyricSettings: LyricSettings,
    onUpdateLyricSettings: (LyricSettings) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Int
) {
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
            SettingsSubPageHeader(
                title = SettingsSection.LYRICS.title,
                subtitle = SettingsSection.LYRICS.subtitle,
                onBack = onBack
            )
        }

        item {
            SettingsCard(
                icon = Icons.Rounded.Lyrics,
                title = "多源逐字歌词视觉引擎",
                subtitle = "适配网易云 / QQ音乐 / 酷狗 / LRCLIB 及本地 .qrc/.yrc/.krc/.lrc/.ttml"
            ) {
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

                val lyricGold = adaptiveGoldColor()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("歌词字体粗细", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = lyricSettings.fontWeightLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = lyricGold
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
                        color = lyricGold
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
                                    if (isSelected) lyricGold.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) lyricGold else MaterialTheme.colorScheme.outlineVariant,
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
                                color = if (isSelected) lyricGold else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        val srcColor = adaptiveLyricSourceColor(src)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateLyricSettings(lyricSettings.copy(activeOnlineSource = src))
                            },
                            label = { Text(src.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = srcColor.copy(alpha = 0.20f),
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
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("灵动弹性波浪", style = MaterialTheme.typography.bodyMedium)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(RubyAccent.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "强化弹跳",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RubyAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            "大幅放大歌词切行时的级联拉升、纵向弹性伸展与低阻尼回弹幅度，呈现更澎湃的波浪视觉张力",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lyricSettings.enableElasticLyricWave,
                        onCheckedChange = {
                            onUpdateLyricSettings(lyricSettings.copy(enableElasticLyricWave = it))
                        },
                        modifier = Modifier.testTag("settings_lyric_elastic_wave_switch")
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryAndScanSettingsSubPage(
    showLibraryHero: Boolean,
    enableGeneralAudioMode: Boolean,
    scanFilter: ScanFilterConfig,
    onSetShowLibraryHero: (Boolean) -> Unit,
    onSetEnableGeneralAudioMode: (Boolean) -> Unit,
    onUpdateScanFilter: (ScanFilterConfig) -> Unit,
    onTriggerRescan: () -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Int
) {
    var newExcludeFolder by remember { mutableStateOf("") }

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
            SettingsSubPageHeader(
                title = SettingsSection.LIBRARY_SCAN.title,
                subtitle = SettingsSection.LIBRARY_SCAN.subtitle,
                onBack = onBack
            )
        }

        item {
            SettingsCard(
                icon = Icons.Rounded.LibraryMusic,
                title = "曲库展示与分区策略",
                subtitle = "定制首页唱片馆卡片与独立「普通音频区」分流体验"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("首页显示顶部唱片馆卡片", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "关闭后曲库顶部更紧凑清爽，仍可在工坊主页随时导入音频",
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

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    thickness = 0.6.dp
                )

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

        item {
            SettingsCard(
                icon = Icons.Rounded.FilterAlt,
                title = "智能扫描与音频筛选引擎",
                subtitle = "自动过滤语音备忘录、系统铃声与聊天碎音频，只保留纯净音乐"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val filterGold = adaptiveGoldColor()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("最短曲目时长过滤", style = MaterialTheme.typography.bodyMedium)
                        Text("${scanFilter.minDurationSec} 秒", style = MaterialTheme.typography.labelMedium, color = filterGold)
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
                        Text("${scanFilter.minSizeKb} KB", style = MaterialTheme.typography.labelMedium, color = filterGold)
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
    }
}

@Composable
internal fun BackupAndSystemSettingsSubPage(
    deletionGuardMode: DeletionGuardMode,
    onSetDeletionGuardMode: (DeletionGuardMode) -> Unit,
    onExportSettingsBackup: (includeApiKey: Boolean) -> Unit,
    onImportSettingsBackup: () -> Unit,
    onExecuteDangerZoneAction: (DangerZoneAction) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Int
) {
    val context = LocalContext.current
    var includeApiKeyInExport by remember { mutableStateOf(false) }
    var pendingDangerAction by remember { mutableStateOf<DangerZoneAction?>(null) }

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
            SettingsSubPageHeader(
                title = SettingsSection.BACKUP_SYSTEM.title,
                subtitle = SettingsSection.BACKUP_SYSTEM.subtitle,
                onBack = onBack
            )
        }

        // Local Settings Backup Export & Import Card
        item {
            SettingsCard(
                icon = Icons.Rounded.Backup,
                title = "本地设置备份与恢复",
                subtitle = "导出或导入 JSON 配置文件，跨设备迁移外观、歌词、扫描规则、自定义 EQ 预设与书签"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable { includeApiKeyInExport = !includeApiKeyInExport }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = if (includeApiKeyInExport) RubyAccent else adaptiveGoldColor(),
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "导出时包含 AI API 密钥",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (includeApiKeyInExport) {
                                "已开启：导出文件将包含你的 DeepSeek 密钥，请妥善保管"
                            } else {
                                "默认脱敏隐藏密钥，便于安全分享或长期归档"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includeApiKeyInExport,
                        onCheckedChange = { includeApiKeyInExport = it },
                        modifier = Modifier.testTag("settings_backup_include_key_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onExportSettingsBackup(includeApiKeyInExport) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RubyAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_export_backup_button")
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导出备份", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onImportSettingsBackup,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_import_backup_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导入恢复", maxLines = 1)
                    }
                }

                Text(
                    text = "覆盖范围：深浅主题、底栏样式、视效增强、触觉震动、智能音频过渡、歌词字重字号/灵动弹性波浪与源、扫描筛选规则、10 段 EQ 状态及自定义预设、防误删模式、AI 模型配置、歌单封面设置与音频时间书签。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Unified Deletion Guard Mode Card
        item {
            SettingsCard(
                icon = Icons.Rounded.Shield,
                title = "防误删保护机制",
                subtitle = "统一守护自定义 EQ 预设、AI 对话记录、歌单曲目与曲库移出等全部删除操作"
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeletionGuardMode.entries.forEach { mode ->
                        val selected = deletionGuardMode == mode
                        val optionIcon = if (mode == DeletionGuardMode.CONFIRM_DIALOG) {
                            Icons.Rounded.VerifiedUser
                        } else {
                            Icons.AutoMirrored.Rounded.Undo
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                                .testTag("settings_deletion_guard_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) RubyAccent.copy(alpha = 0.20f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = optionIcon,
                                    contentDescription = null,
                                    tint = if (selected) RubyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) RubyAccent else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (selected) "已选中" else "未选中",
                                tint = if (selected) RubyAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // System Default Music Player Card (Fixed Direct Deep Links!)
        item {
            SettingsCard(
                icon = Icons.Rounded.Smartphone,
                title = "系统默认音乐播放器",
                subtitle = "Auralis 已注册 audio/* 与系统音乐主入口，可直接跳转系统「默认应用」页进行指派"
            ) {
                Button(
                    onClick = { openSystemDefaultAppsSettings(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_open_default_apps_button")
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("打开系统「默认应用」设置")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    OutlinedButton(
                        onClick = { openAppOpenByDefaultSettings(context) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_app_by_default_button")
                    ) {
                        Icon(Icons.Rounded.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("配置 Auralis「默认打开」关联")
                    }
                }

                Text(
                    text = "提示：部分定制系统不支持在「默认应用」中单独列出音乐项，你也可在文件管理器中点击任意本地音频，选择 Auralis 并点选「始终」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Danger Zone: Stratified Data Clearing with Continuous Double Confirmation
        item {
            val dangerRed = Color(0xFFE53935)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(dangerRed.copy(alpha = 0.06f))
                    .border(1.dp, dangerRed.copy(alpha = 0.36f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(dangerRed.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = dangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "危险区 · 清除数据",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = dangerRed
                        )
                        Text(
                            text = "均仅清理应用内数据，绝不删除手机本地音频文件；每项操作均需连续二次确认",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DangerZoneAction.entries.forEach { action ->
                    val isNuke = action == DangerZoneAction.NUKE_ALL_DATA
                    val actionIcon = when (action) {
                        DangerZoneAction.CLEAR_AUDIO_LIBRARY_ONLY -> Icons.Rounded.LibraryMusic
                        DangerZoneAction.CLEAR_RECORDS_AND_LOGS -> Icons.Rounded.History
                        DangerZoneAction.RESET_ALL_SETTINGS -> Icons.Rounded.SettingsBackupRestore
                        DangerZoneAction.NUKE_ALL_DATA -> Icons.Rounded.DeleteForever
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isNuke) dangerRed.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .border(
                                width = if (isNuke) 1.dp else 0.7.dp,
                                color = if (isNuke) dangerRed.copy(alpha = 0.65f) else dangerRed.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { pendingDangerAction = action }
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                            .testTag("danger_zone_${action.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(dangerRed.copy(alpha = if (isNuke) 0.24f else 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                tint = dangerRed,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isNuke) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isNuke) dangerRed else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = action.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isNuke) dangerRed
                                    else dangerRed.copy(alpha = 0.14f)
                                )
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isNuke) "彻底清空" else "清除",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isNuke) Color.White else dangerRed
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDangerAction?.let { action ->
        DangerZoneDoubleConfirmDialog(
            action = action,
            onConfirmed = {
                pendingDangerAction = null
                onExecuteDangerZoneAction(action)
            },
            onDismiss = { pendingDangerAction = null }
        )
    }
}

@Composable
private fun DangerZoneDoubleConfirmDialog(
    action: DangerZoneAction,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember(action) { mutableStateOf(1) }
    val dangerRed = Color(0xFFE53935)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (step == 1) Icons.Rounded.WarningAmber else Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = dangerRed
            )
        },
        title = {
            Text(
                text = if (step == 1) {
                    "确认执行「${action.title}」？ (1/2)"
                } else {
                    "最终安全确认 · 即将执行 (2/2)"
                },
                fontWeight = FontWeight.Bold,
                color = if (step == 2) dangerRed else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step == 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(dangerRed.copy(alpha = 0.10f))
                            .border(0.7.dp, dangerRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "本次将被清除：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = dangerRed
                        )
                        Text(
                            text = action.step1Impact,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldLossless.copy(alpha = 0.10f))
                            .border(0.7.dp, EmeraldLossless.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "完整保留不受影响：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldLossless
                        )
                        Text(
                            text = action.step1Preserved,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = "你正在对 Auralis 执行「${action.title}」。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = action.step2FinalWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = dangerRed
                    )
                    Text(
                        text = "提示：手机存储中的本地音频原文件始终完好无损。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dangerRed.copy(alpha = 0.88f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("danger_zone_step1_continue")
                ) {
                    Text("继续下一步 (1/2)")
                }
            } else {
                Button(
                    onClick = onConfirmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dangerRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("danger_zone_step2_execute")
                ) {
                    Text("确认立即清除")
                }
            }
        },
        dismissButton = {
            if (step == 1) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            } else {
                TextButton(onClick = { step = 1 }) {
                    Text("返回核对")
                }
            }
        }
    )
}

@Composable
internal fun SettingsBackupImportConfirmDialog(
    pending: PendingSettingsBackupImportPreview,
    onConfirm: (restoreApiKey: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val preview = pending.parsed.preview
    var restoreApiKey by remember(pending) { mutableStateOf(preview.includesApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Restore,
                contentDescription = null,
                tint = RubyAccent
            )
        },
        title = { Text("确认恢复本地设置备份？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "文件：${pending.fileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "来源版本：Auralis v${preview.appVersion}  ·  ${SettingsBackupCodec.formatExportTimestamp(preview.exportedAtMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "界面样式：${preview.themeLabel} · ${preview.navBarLabel}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "包含内容：${preview.customEqPresetCount} 个自定义 EQ 预设 · ${preview.excludedFolderCount} 个屏蔽目录 · ${preview.playlistCoverCount} 项歌单封面 · ${preview.bookmarkCount} 条时间书签",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (preview.includesApiKey) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldLossless.copy(alpha = 0.10f))
                            .clickable { restoreApiKey = !restoreApiKey }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = restoreApiKey,
                            onCheckedChange = { restoreApiKey = it }
                        )
                        Text(
                            text = "同时恢复备份中的 AI API 密钥",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        text = "此备份为脱敏文件（不含 API 密钥），恢复时将保留你当前本地已填写的密钥。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(restoreApiKey && preview.includesApiKey) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RubyAccent,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_import_backup_button")
            ) {
                Text("确认恢复")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
