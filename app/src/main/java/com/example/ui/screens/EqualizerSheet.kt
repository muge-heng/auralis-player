package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EQ_BAND_COUNT
import com.example.model.EqPreset
import com.example.model.EqState
import com.example.model.normalizeEqBands
import com.example.playback.AcousticDspEngine
import com.example.playback.StudioEqPresets
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent
import java.util.Locale

@Composable
fun EqualizerSheet(
    eqState: EqState,
    customPresets: List<EqPreset>,
    onUpdateEq: (EqState) -> Unit,
    onApplyPreset: (EqPreset) -> Unit,
    onSaveCustomPreset: (name: String, state: EqState) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onTickHaptic: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    val bands = remember(eqState.bandLevelsDb) { normalizeEqBands(eqState.bandLevelsDb, EQ_BAND_COUNT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
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
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = RubyAccent
                )
                Column {
                    Text("10 段母带声学均衡器", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (eqState.enabled) {
                            if (AcousticDspEngine.isTrueTenBandHardware) {
                                "10 段 DynamicsProcessing 硬件实时渲染中"
                            } else {
                                "10 段声学曲线 · AudioFX DSP 实时渲染中"
                            }
                        } else {
                            "直通模式 (Bypass)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Switch(
                    checked = eqState.enabled,
                    onCheckedChange = {
                        onTickHaptic()
                        onUpdateEq(eqState.copy(enabled = it, bandLevelsDb = bands))
                    },
                    modifier = Modifier.testTag("eq_master_switch")
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭均衡器")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Glowing 10-Band Frequency Response Curve Visualizer
            item {
                EqCurveCanvas(
                    bands = bands,
                    enabled = eqState.enabled
                )
            }

            // Action Strip: Save Custom Preset + Reset Flat
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            presetNameInput = ""
                            showSaveDialog = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("eq_save_custom_preset_button")
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存当前为自定义预设")
                    }
                    OutlinedButton(
                        onClick = {
                            onTickHaptic()
                            onUpdateEq(
                                eqState.copy(
                                    presetId = "flat",
                                    bandLevelsDb = List(EQ_BAND_COUNT) { 0f },
                                    bassBoostPercent = 0,
                                    virtualizerPercent = 0
                                )
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("eq_reset_flat_button")
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("归零")
                    }
                }
            }

            // Custom Saved Presets Strip (when any exist)
            if (customPresets.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("我的自定义预设", style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                            Text(
                                text = "(${customPresets.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(customPresets, key = { it.id }) { preset ->
                                val selected = eqState.presetId == preset.id && eqState.enabled
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) EmeraldLossless.copy(alpha = 0.22f)
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .border(
                                            width = if (selected) 1.dp else 0.6.dp,
                                            color = if (selected) EmeraldLossless else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onApplyPreset(preset) }
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) EmeraldLossless else MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { onDeleteCustomPreset(preset.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "删除自定义预设 ${preset.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Built-in Studio Presets Strip
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("内置母带预设", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(StudioEqPresets.all, key = { it.id }) { preset ->
                            FilterChip(
                                selected = eqState.presetId == preset.id && eqState.enabled,
                                onClick = { onApplyPreset(preset) },
                                label = { Text(preset.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RubyAccent.copy(alpha = 0.22f),
                                    selectedLabelColor = RubyAccent
                                )
                            )
                        }
                    }
                }
            }

            // 10-Band Precision Faders
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("10 段精调频响 (-12 dB ~ +12 dB)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "从 31Hz 极低频到 16kHz 极高频泛音，逐段独立塑形",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    StudioEqPresets.bandLabels.forEachIndexed { index, freqLabel ->
                        val db = bands.getOrElse(index) { 0f }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(50.dp)
                            )
                            Slider(
                                value = db,
                                onValueChange = { newVal ->
                                    val snapped = (newVal * 2f).toInt() / 2f
                                    if (snapped != db) {
                                        onTickHaptic()
                                        val mutable = bands.toMutableList()
                                        mutable[index] = snapped
                                        onUpdateEq(
                                            eqState.copy(
                                                enabled = true,
                                                presetId = "custom",
                                                bandLevelsDb = mutable
                                            )
                                        )
                                    }
                                },
                                valueRange = -12f..12f,
                                colors = SliderDefaults.colors(
                                    thumbColor = if (eqState.enabled) RubyAccent else MaterialTheme.colorScheme.outline,
                                    activeTrackColor = if (eqState.enabled) RubyAccent else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(Locale.US, "%+.1f", db),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (eqState.enabled) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(44.dp)
                            )
                        }
                    }
                }
            }

            // Bass Boost & Spatial Virtualizer
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
                    Text("声场扩展与低频潜深", style = MaterialTheme.typography.titleSmall)

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("超低音增强 (Bass Boost)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${eqState.bassBoostPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = ChampagneGold
                            )
                        }
                        Slider(
                            value = eqState.bassBoostPercent.toFloat(),
                            onValueChange = {
                                val pct = it.toInt()
                                if (pct != eqState.bassBoostPercent) {
                                    onUpdateEq(eqState.copy(enabled = true, presetId = "custom", bandLevelsDb = bands, bassBoostPercent = pct))
                                }
                            },
                            valueRange = 0f..100f
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("空间环绕声场 (Virtualizer)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${eqState.virtualizerPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = ChampagneGold
                            )
                        }
                        Slider(
                            value = eqState.virtualizerPercent.toFloat(),
                            onValueChange = {
                                val pct = it.toInt()
                                if (pct != eqState.virtualizerPercent) {
                                    onUpdateEq(eqState.copy(enabled = true, presetId = "custom", bandLevelsDb = bands, virtualizerPercent = pct))
                                }
                            },
                            valueRange = 0f..100f
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    // Save Custom Preset Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存自定义均衡器预设") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "将当前 10 段频响曲线、低音增强 (${eqState.bassBoostPercent}%) 与环绕 (${eqState.virtualizerPercent}%) 保存到我的预设库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        label = { Text("预设名称（如：深夜监听 / 耳机通透）") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eq_custom_preset_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = presetNameInput.trim()
                        if (cleaned.isNotEmpty()) {
                            onSaveCustomPreset(cleaned, eqState.copy(bandLevelsDb = bands))
                            showSaveDialog = false
                        }
                    },
                    enabled = presetNameInput.isNotBlank()
                ) {
                    Text("保存预设")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EqCurveCanvas(
    bands: List<Float>,
    enabled: Boolean
) {
    val lineColor = if (enabled) RubyAccent else Color(0xFF6B6B7E)
    val glowColor = if (enabled) ChampagneGold else Color(0xFF4A4A5A)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF101018))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f

                // Reference 0dB center line & grid lines
                drawLine(
                    color = Color.White.copy(alpha = 0.16f),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, h * 0.25f),
                    end = Offset(w, h * 0.25f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, h * 0.75f),
                    end = Offset(w, h * 0.75f),
                    strokeWidth = 1f
                )

                val points = bands.mapIndexed { idx, db ->
                    val x = if (bands.size <= 1) 0f else w * (idx.toFloat() / (bands.size - 1))
                    val y = midY - (db / 12f) * (h * 0.42f)
                    Offset(x, y)
                }

                // Vertical faint band guide lines
                points.forEach { pt ->
                    drawLine(
                        color = Color.White.copy(alpha = 0.04f),
                        start = Offset(pt.x, 0f),
                        end = Offset(pt.x, h),
                        strokeWidth = 1f
                    )
                }

                if (points.size >= 2) {
                    val curvePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.lastIndex) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val ctrlX = (p0.x + p1.x) / 2f
                            cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                        }
                    }
                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(lineColor.copy(alpha = 0.34f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = curvePath,
                        brush = Brush.horizontalGradient(listOf(lineColor, glowColor)),
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )
                    points.forEach { pt ->
                        drawCircle(color = Color.White, radius = 5f, center = pt)
                        drawCircle(color = lineColor, radius = 2.8f, center = pt)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k").forEach { tick ->
                Text(
                    text = tick,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.48f)
                )
            }
        }
    }
}
