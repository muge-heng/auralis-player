package com.example.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.adaptiveGoldColor
import java.io.File

/**
 * Resolves a stable artwork data source for a track (custom/extracted local file first, then MediaStore URI).
 */
fun resolveTrackArtworkModel(artworkPath: String?, albumArtUri: String?): Any? {
    if (!artworkPath.isNullOrBlank()) {
        val file = File(artworkPath)
        if (file.exists() && file.length() > 0L) return file
    }
    if (!albumArtUri.isNullOrBlank()) return albumArtUri
    return null
}

/**
 * Deterministic cache key shared between [TrackArtwork] and cold-start/next-track Coil pre-warming
 * so pre-loaded bitmaps hit the memory cache on Frame 1 with zero flicker.
 */
fun buildTrackArtworkCacheKey(
    artworkPath: String?,
    albumArtUri: String?,
    decodeSizePx: Int?
): String? {
    val src = when {
        !artworkPath.isNullOrBlank() -> "file:$artworkPath"
        !albumArtUri.isNullOrBlank() -> "uri:$albumArtUri"
        else -> return null
    }
    return if (decodeSizePx != null && decodeSizePx > 0) "${src}@${decodeSizePx}" else "${src}@full"
}

/**
 * Builds a Coil [ImageRequest] tuned for instant memory-cached rendering (no placeholder flash).
 */
fun buildTrackArtworkRequest(
    context: Context,
    artworkPath: String?,
    albumArtUri: String?,
    decodeSizePx: Int? = 256,
    crossfadeMs: Int = 0
): ImageRequest? {
    val model = resolveTrackArtworkModel(artworkPath, albumArtUri) ?: return null
    val cacheKey = buildTrackArtworkCacheKey(artworkPath, albumArtUri, decodeSizePx)
    return ImageRequest.Builder(context)
        .data(model)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .apply {
            if (cacheKey != null) {
                memoryCacheKey(cacheKey)
                diskCacheKey(cacheKey)
            }
            if (decodeSizePx != null && decodeSizePx > 0) {
                size(decodeSizePx, decodeSizePx)
                precision(Precision.INEXACT)
            }
            if (crossfadeMs > 0) {
                crossfade(crossfadeMs)
            } else {
                crossfade(false)
            }
        }
        .build()
}

/**
 * Clean minimalist icon-based fallback cover art (replaces heavy AI bitmap placeholders).
 * Draws instantaneously with zero disk or bitmap decoding latency.
 */
@Composable
fun MinimalArtworkPlaceholder(
    modifier: Modifier = Modifier,
    isGeneralAudio: Boolean = false
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.25f
    val topBg = if (isDark) Color(0xFF1C1D27) else Color(0xFFEBEFF5)
    val bottomBg = if (isDark) Color(0xFF12131B) else Color(0xFFDCE2EC)
    val ringColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF181B24).copy(alpha = 0.08f)
    val iconTint = if (isDark) Color.White.copy(alpha = 0.42f) else Color(0xFF2C3242).copy(alpha = 0.46f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(topBg, bottomBg))),
        contentAlignment = Alignment.Center
    ) {
        val iconSize = (minOf(maxWidth, maxHeight) * 0.42f).coerceIn(18.dp, 72.dp)
        Box(
            modifier = Modifier
                .size((iconSize * 1.55f).coerceAtMost(minOf(maxWidth, maxHeight) * 0.76f))
                .border(0.8.dp, ringColor, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGeneralAudio) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun TrackArtwork(
    artworkPath: String?,
    albumArtUri: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    decodeSizePx: Int? = 256,
    holdLastArtwork: Boolean = false,
    isGeneralAudio: Boolean = false
) {
    val context = LocalContext.current
    val request = remember(artworkPath, albumArtUri, decodeSizePx) {
        buildTrackArtworkRequest(
            context = context,
            artworkPath = artworkPath,
            albumArtUri = albumArtUri,
            decodeSizePx = decodeSizePx,
            crossfadeMs = 0
        )
    }

    var lastSuccessPainter by remember { mutableStateOf<Painter?>(null) }
    var loadFailed by remember(request) { mutableStateOf(false) }
    var isLoaded by remember(request) { mutableStateOf(false) }

    val bgTint = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgTint)
    ) {
        if (request != null) {
            // Only show minimal icon fallback when no image is available yet AND we aren't holding a previous frame
            if ((!isLoaded && (!holdLastArtwork || lastSuccessPainter == null)) || loadFailed) {
                MinimalArtworkPlaceholder(isGeneralAudio = isGeneralAudio)
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = if (holdLastArtwork) lastSuccessPainter else null,
                onSuccess = { success ->
                    isLoaded = true
                    loadFailed = false
                    if (holdLastArtwork) {
                        lastSuccessPainter = success.painter
                    }
                },
                onError = {
                    isLoaded = false
                    loadFailed = true
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            MinimalArtworkPlaceholder(isGeneralAudio = isGeneralAudio)
        }
    }
}

@Composable
fun QualityBadgePill(
    label: String,
    isHiRes: Boolean,
    isLossless: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = when {
        isHiRes -> adaptiveGoldColor()
        isLossless -> EmeraldLossless
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(0.75.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1
        )
    }
}

@Composable
fun LiveEqualizerBars(
    isPlaying: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(490, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(390, easing = LinearEasing), RepeatMode.Reverse),
        label = "b4"
    )

    val heights = if (isPlaying) listOf(bar1, bar2, bar3, bar4) else listOf(0.28f, 0.45f, 0.32f, 0.25f)
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((16.dp * fraction).coerceAtLeast(3.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
