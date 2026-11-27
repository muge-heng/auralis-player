package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ScanFilterConfig
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.adaptiveGoldColor

@Composable
fun SmartScanConfirmDialog(
    initialConfig: ScanFilterConfig,
    onConfirmScan: (ScanFilterConfig) -> Unit,
    onOpenDetailedSettings: (ScanFilterConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var config by remember(initialConfig) { mutableStateOf(initialConfig.normalized()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Radar,
                contentDescription = null,
                tint = RubyAccent
            )
        },
        title = {
            Text(
                text = "智能扫描确认与高级筛选",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterAlt,
                            contentDescription = null,
                            tint = adaptiveGoldColor(),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "当前规则：时长 ${config.formatDurationRangeLabel()} · 体积 ≥ ${config.minSizeKb} KB · 屏蔽 ${config.excludedFolders.size} 个目录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RubyAccent.copy(alpha = 0.10f))
                            .border(0.6.dp, RubyAccent.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                            .clickable { onOpenDetailedSettings(config) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("scan_dialog_open_detailed_settings"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = RubyAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "前往「曲库与扫描」详细设置（时长上下限 / 目录）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = RubyAccent,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = RubyAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                ScanToggleOption(
                    title = "仅筛选含元数据音频",
                    subtitle = "过滤掉缺失歌手、专辑与封面的无标签录音或提示音",
                    checked = config.requireMetadata,
                    onCheckedChange = { config = config.copy(requireMetadata = it) },
                    testTag = "scan_dialog_require_metadata"
                )

                ScanToggleOption(
                    title = "仅筛选含歌手信息",
                    subtitle = "严格要求有效的艺术家标签（自动排除“未知艺术家”）",
                    checked = config.requireArtist,
                    onCheckedChange = { config = config.copy(requireArtist = it) },
                    testTag = "scan_dialog_require_artist"
                )

                ScanToggleOption(
                    title = "仅筛选含专辑或内嵌封面",
                    subtitle = "进一步屏蔽语音备忘录、导出剪辑与零碎音效",
                    checked = config.requireAlbumOrArt,
                    onCheckedChange = { config = config.copy(requireAlbumOrArt = it) },
                    testTag = "scan_dialog_require_album_art"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ScanToggleOption(
                    title = "未达标音频归入「普通音频区」",
                    subtitle = "保留无法确认是音乐的本地音频至独立分区，不混入音乐歌单与 AI 策展",
                    checked = config.classifyUnverifiedAsGeneralAudio,
                    onCheckedChange = { config = config.copy(classifyUnverifiedAsGeneralAudio = it) },
                    testTag = "scan_dialog_classify_general"
                )

                ScanToggleOption(
                    title = "仅扫描系统标记为音乐的音频",
                    subtitle = "利用 Android MediaStore IS_MUSIC 过滤系统铃声与通知音",
                    checked = config.excludeSystemAudio,
                    onCheckedChange = { config = config.copy(excludeSystemAudio = it) },
                    testTag = "scan_dialog_exclude_system"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmScan(config.normalized()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RubyAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("scan_dialog_confirm_button")
            ) {
                Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("开始智能扫描")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("scan_dialog_cancel_button")
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ScanToggleOption(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
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
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
