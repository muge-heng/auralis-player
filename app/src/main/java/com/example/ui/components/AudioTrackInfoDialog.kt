package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Track
import com.example.model.formatDuration
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
    onToggleGeneralAudio: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (track.isGeneralAudio) "音频文件信息" else "曲目文件信息",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
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

                if (onToggleGeneralAudio != null || onShare != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
