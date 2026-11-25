package com.example.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import com.example.model.AuraPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PaletteExtractor {

    suspend fun extractFromTrackArtwork(
        context: Context,
        artworkPath: String?,
        albumArtUri: String?
    ): AuraPalette = withContext(Dispatchers.IO) {
        val bitmap = loadSampledBitmap(context, artworkPath, albumArtUri)
            ?: return@withContext AuraPalette()
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext AuraPalette()
        }

        try {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val primary = palette.getVibrantColor(
                palette.getLightVibrantColor(
                    palette.getDominantColor(0xFFFA2D48.toInt())
                )
            )
            val secondary = palette.getMutedColor(
                palette.getDarkVibrantColor(0xFF7B61FF.toInt())
            )
            val deep = palette.getDarkMutedColor(
                palette.getDarkVibrantColor(0xFF140D1E.toInt())
            )
            val glow = palette.getLightVibrantColor(
                palette.getLightMutedColor(0xFFF0C28A.toInt())
            )

            AuraPalette(
                primaryHex = (primary.toLong() and 0xFFFFFFFFL),
                secondaryHex = (secondary.toLong() and 0xFFFFFFFFL),
                deepHex = darkenColor(deep.toLong() and 0xFFFFFFFFL),
                glowHex = (glow.toLong() and 0xFFFFFFFFL)
            )
        } catch (_: Throwable) {
            AuraPalette()
        } finally {
            runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    private fun loadSampledBitmap(
        context: Context,
        artworkPath: String?,
        albumArtUri: String?
    ): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        if (!artworkPath.isNullOrBlank() && File(artworkPath).exists()) {
            runCatching { BitmapFactory.decodeFile(artworkPath, opts) }.getOrNull()?.let { return it }
        }
        if (!albumArtUri.isNullOrBlank()) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(albumArtUri))?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun darkenColor(argb: Long): Long {
        val r = (((argb shr 16) and 0xFF) * 0.35f).toInt().coerceIn(8, 70)
        val g = (((argb shr 8) and 0xFF) * 0.35f).toInt().coerceIn(8, 70)
        val b = ((argb and 0xFF) * 0.35f).toInt().coerceIn(12, 85)
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }
}
