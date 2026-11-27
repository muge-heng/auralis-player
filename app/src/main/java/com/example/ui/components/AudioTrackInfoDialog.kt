package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Track
import com.example.model.formatDuration
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.adaptiveGoldColor
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "未知大小"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun shareAudioTrack(context: Context, track: Track): Boolean {
    return runCatching {
        val uri = Uri.parse(track.contentUri)
        val mime = track.mimeType.takeIf { it.isNotBlank() && it.contains("/") } ?: "audio/*"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, track.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "分享音频：${track.title}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}

@Composable
fun AudioTrackInfoDialog(
    track: Track,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onToggleGeneralAudio: (() -> Unit)? = null,
    hasMetadataOverride: Boolean = false,
    onSaveMetadata: ((title: String, artist: String, album: String, pickedCoverUri: Uri?, clearCustomCover: Boolean) -> Unit)? = null,
    onRestoreMetadata: (() -> Unit)? = null
) {
    var isEditing by remember(track.id) { mutableStateOf(false) }
    var draftTitle by remember(track.id, track.title) { mutableStateOf(track.title) }
    var draftArtist by remember(track.id, track.artist) { mutableStateOf(track.artist) }
    var draftAlbum by remember(track.id, track.album) { mutableStateOf(track.album) }
    var pickedCoverUri by remember(track.id) { mutableStateOf<Uri?>(null) }
    var clearCustomCover by remember(track.id) { mutableStateOf(false) }

    // Zero-permission Android Photo Picker (Google Play Policy compliant)
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedCoverUri = uri
            clearCustomCover = false
        }
    }

    val goldAccent = adaptiveGoldColor()

    if (!isEditing) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (track.isGeneralAudio) "音频文件信息" else "曲目文件信息",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (hasMetadataOverride) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(goldAccent.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "已编辑",
                                style = MaterialTheme.typography.labelSmall,
                                color = goldAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoMetaRow("标题", track.title)
                    if (!track.isGeneralAudio) {
                        InfoMetaRow("艺术家", track.artist)
                        InfoMetaRow("专辑", track.album)
                    }
                    InfoMetaRow("文件名", track.fileName.ifBlank { track.title })
                    InfoMetaRow("所在目录", track.folderPath.ifBlank { "本地音频" })
                    InfoMetaRow("格式与规格", track.technicalSpecLine)
                    InfoMetaRow(
                        "时长 / 体积",
                        "${track.durationMs.formatDuration()} · ${formatFileSize(track.sizeBytes)}"
                    )
                    InfoMetaRow("分类区域", if (track.isGeneralAudio) "普通音频区" else "本地唱片馆")

                    if (onSaveMetadata != null || onToggleGeneralAudio != null || onShare != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onSaveMetadata != null) {
                                OutlinedButton(
                                    onClick = {
                                        draftTitle = track.title
                                        draftArtist = track.artist
                                        draftAlbum = track.album
                                        pickedCoverUri = null
                                        clearCustomCover = false
                                        isEditing = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("audio_info_edit_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (track.isGeneralAudio) "编辑音频信息" else "编辑曲目信息")
                                }
                            }
                            if (hasMetadataOverride && onRestoreMetadata != null) {
                                OutlinedButton(
                                    onClick = {
                                        onRestoreMetadata()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("audio_info_restore_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("恢复原始信息")
                                }
                            }
                            if (onShare != null) {
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onShare()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("audio_info_share_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("分享音频文件")
                                }
                            }
                            if (onToggleGeneralAudio != null) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onToggleGeneralAudio()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("audio_info_reclassify_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (track.isGeneralAudio) "标记为音乐（移入唱片馆）"
                                        else "移至普通音频区"
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { isEditing = false },
            title = {
                Text(
                    text = if (track.isGeneralAudio) "编辑音频信息" else "编辑曲目信息",
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
                    // Cover Art Picker Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
                            .border(
                                0.6.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val previewPath = when {
                            pickedCoverUri != null -> null
                            clearCustomCover -> null
                            else -> track.artworkPath
                        }
                        val previewUri = when {
                            pickedCoverUri != null -> pickedCoverUri.toString()
                            clearCustomCover -> null
                            else -> track.albumArtUri
                        }
                        TrackArtwork(
                            artworkPath = previewPath,
                            albumArtUri = previewUri,
                            contentDescription = "封面预览",
                            cornerRadius = 12.dp,
                            isGeneralAudio = track.isGeneralAudio,
                            modifier = Modifier.size(62.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coverPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("audio_info_pick_cover_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("更换专辑封面", maxLines = 1)
                            }
                            if (pickedCoverUri != null || hasMetadataOverride) {
                                TextButton(
                                    onClick = {
                                        pickedCoverUri = null
                                        clearCustomCover = true
                                    },
                                    modifier = Modifier.align(Alignment.Start)
                                ) {
                                    Text(
                                        text = "恢复默认/原始封面",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_track_title_input")
                    )

                    if (!track.isGeneralAudio) {
                        OutlinedTextField(
                            value = draftArtist,
                            onValueChange = { draftArtist = it },
                            label = { Text("艺术家") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_track_artist_input")
                        )

                        OutlinedTextField(
                            value = draftAlbum,
                            onValueChange = { draftAlbum = it },
                            label = { Text("专辑") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_track_album_input")
                        )
                    }

                    Text(
                        text = "提示：修改仅保存在应用内显示与检索，不会改动手机本地原始音频文件；编码规格、时长、文件名与目录路径为只读属性。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasMetadataOverride && onRestoreMetadata != null) {
                        TextButton(
                            onClick = {
                                onRestoreMetadata()
                                isEditing = false
                                onDismiss()
                            },
                            modifier = Modifier.testTag("edit_track_restore_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("恢复全部原始扫描信息")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = draftTitle.trim().isNotEmpty(),
                    onClick = {
                        onSaveMetadata?.invoke(
                            draftTitle.trim(),
                            draftArtist.trim().ifBlank { track.artist },
                            draftAlbum.trim().ifBlank { track.album },
                            pickedCoverUri,
                            clearCustomCover
                        )
                        isEditing = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RubyAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("edit_track_save_button")
                ) {
                    Text("保存修改")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditing = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun InfoMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
