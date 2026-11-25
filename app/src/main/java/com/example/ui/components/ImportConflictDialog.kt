package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.scanner.ImportConflictDecision
import com.example.data.scanner.ImportQualityConflict
import com.example.data.scanner.TrackDeduplicator
import com.example.model.Track
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportConflictDialog(
    sourceLabel: String,
    conflicts: List<ImportQualityConflict>,
    onConfirmDecisions: (Map<String, ImportConflictDecision>) -> Unit,
    onDismissSkipAll: () -> Unit
) {
    // Pre-select the highest-quality recommendation for every conflict by default
    val decisions = remember(conflicts) {
        mutableStateMapOf<String, ImportConflictDecision>().apply {
            conflicts.forEach { c ->
                put(
                    c.conflictId,
                    if (c.recommendedIncoming) {
                        ImportConflictDecision.REPLACE_WITH_INCOMING
                    } else {
                        ImportConflictDecision.KEEP_EXISTING
                    }
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissSkipAll,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChampagneGold.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CompareArrows,
                            contentDescription = null,
                            tint = ChampagneGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "发现 ${conflicts.size} 首不同音质版本",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${sourceLabel.ifBlank { "导入" }} · 同音质重复已自动跳过，替换将自动继承收藏与歌单",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismissSkipAll,
                        modifier = Modifier.testTag("import_conflict_close")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭并跳过")
                    }
                }

                // One-tap Bulk Strategy Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BulkStrategyPill(
                        label = "一键选最高音质",
                        highlighted = true,
                        testTag = "conflict_bulk_best_quality",
                        onClick = {
                            conflicts.forEach { c ->
                                decisions[c.conflictId] = if (c.recommendedIncoming) {
                                    ImportConflictDecision.REPLACE_WITH_INCOMING
                                } else {
                                    ImportConflictDecision.KEEP_EXISTING
                                }
                            }
                        }
                    )
                    BulkStrategyPill(
                        label = "高质留音乐 · 低质转普通音频",
                        highlighted = false,
                        testTag = "conflict_bulk_demote_low",
                        onClick = {
                            conflicts.forEach { c ->
                                decisions[c.conflictId] = ImportConflictDecision.KEEP_HIGH_IN_MUSIC_DEMOTE_OTHER
                            }
                        }
                    )
                    BulkStrategyPill(
                        label = "全留曲库原版",
                        highlighted = false,
                        testTag = "conflict_bulk_keep_existing",
                        onClick = {
                            conflicts.forEach { c ->
                                decisions[c.conflictId] = ImportConflictDecision.KEEP_EXISTING
                            }
                        }
                    )
                    BulkStrategyPill(
                        label = "全替换为新版",
                        highlighted = false,
                        testTag = "conflict_bulk_replace_incoming",
                        onClick = {
                            conflicts.forEach { c ->
                                decisions[c.conflictId] = ImportConflictDecision.REPLACE_WITH_INCOMING
                            }
                        }
                    )
                    BulkStrategyPill(
                        label = "全保留两版",
                        highlighted = false,
                        testTag = "conflict_bulk_keep_both",
                        onClick = {
                            conflicts.forEach { c ->
                                decisions[c.conflictId] = ImportConflictDecision.KEEP_BOTH
                            }
                        }
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Conflict Comparison Cards
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(conflicts, key = { it.conflictId }) { conflict ->
                        val activeDecision = decisions[conflict.conflictId]
                            ?: if (conflict.recommendedIncoming) {
                                ImportConflictDecision.REPLACE_WITH_INCOMING
                            } else {
                                ImportConflictDecision.KEEP_EXISTING
                            }
                        ImportConflictCard(
                            conflict = conflict,
                            decision = activeDecision,
                            onSelectDecision = { decisions[conflict.conflictId] = it }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismissSkipAll,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.85f)
                            .testTag("import_conflict_skip_all")
                    ) {
                        Text("暂跳过冲突")
                    }
                    Button(
                        onClick = { onConfirmDecisions(decisions.toMap()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RubyAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.35f)
                            .testTag("import_conflict_confirm")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("应用决策 (${conflicts.size} 项)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkStrategyPill(
    label: String,
    highlighted: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val bg = if (highlighted) {
        RubyAccent.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (highlighted) {
        RubyAccent.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (highlighted) RubyAccent else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(0.8.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (highlighted) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = RubyAccent,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportConflictCard(
    conflict: ImportQualityConflict,
    decision: ImportConflictDecision,
    onSelectDecision: (ImportConflictDecision) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Song Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrackArtwork(
                artworkPath = conflict.bestTrack.artworkPath ?: conflict.existingTrack.artworkPath,
                albumArtUri = conflict.bestTrack.albumArtUri ?: conflict.existingTrack.albumArtUri,
                contentDescription = conflict.bestTrack.title,
                modifier = Modifier.size(44.dp),
                cornerRadius = 11.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conflict.bestTrack.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${conflict.bestTrack.artist} · ${conflict.bestTrack.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Existing Version Row
        ConflictVersionOptionRow(
            roleLabel = "曲库原版",
            track = conflict.existingTrack,
            isRecommendedBest = !conflict.recommendedIncoming,
            isSelected = decision == ImportConflictDecision.KEEP_EXISTING ||
                decision == ImportConflictDecision.KEEP_BOTH ||
                (decision == ImportConflictDecision.KEEP_HIGH_IN_MUSIC_DEMOTE_OTHER && !conflict.recommendedIncoming),
            onClick = { onSelectDecision(ImportConflictDecision.KEEP_EXISTING) }
        )

        // Incoming Version Row
        ConflictVersionOptionRow(
            roleLabel = "待导入新版",
            track = conflict.incomingTrack,
            isRecommendedBest = conflict.recommendedIncoming,
            isSelected = decision == ImportConflictDecision.REPLACE_WITH_INCOMING ||
                decision == ImportConflictDecision.KEEP_BOTH ||
                (decision == ImportConflictDecision.KEEP_HIGH_IN_MUSIC_DEMOTE_OTHER && conflict.recommendedIncoming),
            onClick = { onSelectDecision(ImportConflictDecision.REPLACE_WITH_INCOMING) }
        )

        // Decision Action Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val options = listOf(
                ImportConflictDecision.KEEP_EXISTING to "保留原版",
                ImportConflictDecision.REPLACE_WITH_INCOMING to "替换为新版",
                ImportConflictDecision.KEEP_HIGH_IN_MUSIC_DEMOTE_OTHER to "高质留音乐/低质转普通",
                ImportConflictDecision.KEEP_BOTH to "保留两版"
            )
            options.forEach { (opt, shortLabel) ->
                val selected = decision == opt
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) RubyAccent else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .border(
                            width = 0.7.dp,
                            color = if (selected) RubyAccent else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectDecision(opt) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictVersionOptionRow(
    roleLabel: String,
    track: Track,
    isRecommendedBest: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected -> RubyAccent.copy(alpha = 0.75f)
        isRecommendedBest -> EmeraldLossless.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val bgColor = if (isSelected) {
        RubyAccent.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) RubyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                QualityBadgePill(
                    label = track.qualityBadge,
                    isHiRes = track.isHiRes,
                    isLossless = track.isLossless
                )
                if (isRecommendedBest) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ChampagneGold.copy(alpha = 0.22f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "更高音质 · 推荐",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB37828)
                        )
                    }
                }
            }
            Text(
                text = TrackDeduplicator.qualitySummaryLine(track),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.folderPath} / ${track.fileName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
