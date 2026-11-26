package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LYRIC_FONT_SCALE_TIERS
import com.example.model.LyricDocument
import com.example.model.LyricSettings
import com.example.model.LyricSource
import com.example.model.OnlineLyricCandidate
import com.example.model.Track
import com.example.model.lyricFontScaleLabel
import com.example.model.snapLyricFontScale
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.adaptiveGoldColor
import com.example.ui.theme.adaptiveLyricSourceColor
import com.example.viewmodel.OnlineLyricSearchState
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsManagerSheet(
    track: Track,
    currentLyric: LyricDocument,
    lyricSettings: LyricSettings,
    onlineSearchState: OnlineLyricSearchState,
    onSearchOnline: (String, String, LyricSource) -> Unit,
    onSelectOnlineCandidate: (OnlineLyricCandidate) -> Unit,
    onImportLyricFile: () -> Unit,
    onSavePastedLyrics: (String) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onUpdateLyricSettings: (LyricSettings) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    var queryTitle by remember(track.id) { mutableStateOf(track.title) }
    var queryArtist by remember(track.id) {
        mutableStateOf(if (track.artist == "未知艺术家") "" else track.artist)
    }
    var selectedSource by remember(lyricSettings.activeOnlineSource) {
        mutableStateOf(lyricSettings.activeOnlineSource)
    }
    var showRawEditor by remember { mutableStateOf(false) }
    var pastedText by remember(currentLyric.rawContent) { mutableStateOf(currentLyric.rawContent) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = adaptiveGoldColor())
                Column {
                    Text("歌词工坊与多源引擎", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${track.title} · ${currentLyric.format.label} · ${currentLyric.sourceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_lyrics_manager")
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭歌词工坊")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Typography (Font Weight & Scale) + Word-by-Word & Timing Offset Calibration Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.FormatBold,
                                    contentDescription = null,
                                    tint = adaptiveGoldColor(),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("歌词排版与字体粗细", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "实时调整歌词字重 (${lyricSettings.fontWeightLabel}) 与字号比例",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Live Font Weight Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = currentLyric.lines.firstOrNull { it.text.isNotBlank() }?.text
                                    ?: "声波起伏间，字重清晰可见 · Auralis",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight(lyricSettings.fontWeight.coerceIn(300, 900)),
                                fontSize = (20f * lyricSettings.fontScale).sp,
                                lineHeight = (27f * lyricSettings.fontScale).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Weight ${lyricSettings.fontWeight} · ${(lyricSettings.fontScale * 100).roundToInt()}% 字号",
                                style = MaterialTheme.typography.labelSmall,
                                color = adaptiveGoldColor()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("歌词字体粗细", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = lyricSettings.fontWeightLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = adaptiveGoldColor()
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
                        modifier = Modifier.testTag("lyric_manager_weight_slider")
                    )

                    val snappedFontScale = snapLyricFontScale(lyricSettings.fontScale)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("歌词字号档位（点击即时切换）", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = lyricFontScaleLabel(snappedFontScale),
                            style = MaterialTheme.typography.labelMedium,
                            color = adaptiveGoldColor()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lyric_manager_scale_tiers"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LYRIC_FONT_SCALE_TIERS.forEach { (tierScale, tierLabel) ->
                            val isSelected = kotlin.math.abs(snappedFontScale - tierScale) < 0.02f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) adaptiveGoldColor().copy(alpha = 0.22f)
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .border(
                                        width = if (isSelected) 1.dp else 0.5.dp,
                                        color = if (isSelected) adaptiveGoldColor() else MaterialTheme.colorScheme.outlineVariant,
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
                                    color = if (isSelected) adaptiveGoldColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = adaptiveGoldColor(),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("模拟逐字歌词动效", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "对普通 LRC/SRT 歌词按音节智能生成流体逐字扫光与上浮效果",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lyricSettings.enableSimulatedWordByWord,
                            onCheckedChange = {
                                onUpdateLyricSettings(lyricSettings.copy(enableSimulatedWordByWord = it))
                            },
                            modifier = Modifier.testTag("lyric_manager_simulated_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("非本行渐进景深模糊", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "远离当前演唱行的歌词逐级缩小并柔化虚焦，手动滚动时自动清晰",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lyricSettings.enableLyricBlur,
                            onCheckedChange = {
                                onUpdateLyricSettings(lyricSettings.copy(enableLyricBlur = it))
                            },
                            modifier = Modifier.testTag("lyric_manager_blur_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("沉浸歌词模式", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "进入歌词页后隐藏顶部与底部控制栏，独享全屏流光歌词舞台",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lyricSettings.enableImmersiveLyrics,
                            onCheckedChange = {
                                onUpdateLyricSettings(lyricSettings.copy(enableImmersiveLyrics = it))
                            },
                            modifier = Modifier.testTag("lyric_manager_immersive_switch")
                        )
                    }

                    // Offset calibrator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("时间轴微调校准", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = String.format(Locale.US, "当前偏移 %+.1f 秒", currentLyric.globalOffsetMs / 1000f),
                                style = MaterialTheme.typography.labelMedium,
                                color = adaptiveGoldColor()
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onAdjustOffset(-200L) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("-0.2s")
                            }
                            OutlinedButton(
                                onClick = { onAdjustOffset(-currentLyric.globalOffsetMs) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("归零")
                            }
                            OutlinedButton(
                                onClick = { onAdjustOffset(200L) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("+0.2s")
                            }
                        }
                    }
                }
            }

            // 2. Multi-Source Cloud Lyric Search (NetEase / QQ Music / Kugou / LRCLIB / Aggregated)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("多源云端歌词精准检索", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "支持网易云音乐（含双语/YRC）、QQ 音乐（含双语）、酷狗音乐（原生逐字 KRC）与 LRCLIB 开源词库并发搜索",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(LyricSource.entries, key = { it.id }) { source ->
                            val isSelected = selectedSource == source
                            val accent = adaptiveLyricSourceColor(source)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedSource = source
                                    onUpdateLyricSettings(lyricSettings.copy(activeOnlineSource = source))
                                },
                                label = { Text(source.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.22f),
                                    selectedLabelColor = accent
                                ),
                                modifier = Modifier.testTag("lyric_source_${source.id}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = queryTitle,
                        onValueChange = { queryTitle = it },
                        label = { Text("曲名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = queryArtist,
                        onValueChange = { queryArtist = it },
                        label = { Text("艺术家（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onSearchOnline(queryTitle, queryArtist, selectedSource) },
                        enabled = !onlineSearchState.isSearching && queryTitle.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_online_lyrics_button")
                    ) {
                        if (onlineSearchState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在从 ${selectedSource.displayName} 检索歌词...")
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("在「${selectedSource.displayName}」智能检索")
                        }
                    }

                    // Per-source diagnostic summary pills
                    if (onlineSearchState.sourceSummaries.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            onlineSearchState.sourceSummaries.forEach { (src, status) ->
                                val srcColor = adaptiveLyricSourceColor(src)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(srcColor.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${src.shortBadge}: $status",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = srcColor
                                    )
                                }
                            }
                        }
                    }

                    onlineSearchState.error?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Candidate list
            items(onlineSearchState.candidates, key = { it.uniqueKey }) { candidate ->
                val isSynced = !candidate.syncedLyrics.isNullOrBlank()
                val sourceColor = adaptiveLyricSourceColor(candidate.source)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(0.75.dp, sourceColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .clickable { onSelectOnlineCandidate(candidate) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.trackName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${candidate.artistName} · ${candidate.albumName.ifBlank { "单曲" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(sourceColor.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = candidate.source.shortBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = sourceColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (candidate.isWordSynced) {
                            LyricFeatureTag("原生逐字 KRC/YRC", adaptiveGoldColor())
                        } else if (isSynced) {
                            LyricFeatureTag("同步 LRC", EmeraldLossless)
                        } else {
                            LyricFeatureTag("纯文本", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (candidate.hasTranslation) {
                            LyricFeatureTag("含双语译文", Color(0xFF38BDF8))
                        }
                    }

                    if (candidate.previewSnippet.isNotBlank()) {
                        Text(
                            text = candidate.previewSnippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3. Manual File Upload (.qrc / .yrc / .krc / .lrc / .ttml / .srt) & Raw Paste
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("手动导入多格式逐字歌词", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "完整适配 QQ音乐 .qrc、网易云 .yrc、酷狗 .krc、增强型 .lrc、Apple .ttml 及 .srt 字幕文件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onImportLyricFile,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_lyric_file_button")
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("选择本地歌词文件")
                        }
                        OutlinedButton(
                            onClick = { showRawEditor = !showRawEditor },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (showRawEditor) "收起编辑器" else "粘贴/编辑")
                        }
                    }

                    if (showRawEditor) {
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            label = { Text("粘贴 .qrc / .yrc / .lrc / .ttml 源码") },
                            minLines = 5,
                            maxLines = 9,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (pastedText.isNotBlank()) {
                                    onSavePastedLyrics(pastedText)
                                    showRawEditor = false
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("保存并立即解析")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun LyricFeatureTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
