package com.example.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.model.Track
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlin.math.min

private const val TAG = "MetadataExtractor"

data class ExtractedAudioDetails(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val year: Int,
    val trackNumber: Int,
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val bitDepth: Int,
    val mimeType: String,
    val codecLabel: String,
    val isLossless: Boolean,
    val isHiRes: Boolean,
    val artworkPath: String?,
    val embeddedLyrics: String?
)

class MetadataExtractor(private val context: Context) {

    private val artCacheDir: File by lazy {
        File(context.cacheDir, "auralis_artwork").apply { mkdirs() }
    }

    fun extract(uri: Uri, fallbackFileName: String, fallbackSizeBytes: Long): ExtractedAudioDetails {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var genre: String? = null
        var year = 0
        var trackNumber = 0
        var durationMs = 0L
        var bitrateKbps = 0
        var sampleRateHz = 0
        var bitDepth = 16
        var mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
        var artworkPath: String? = null

        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() }
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()?.takeIf { it.isNotEmpty() }
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotEmpty() }
            albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?.trim()?.takeIf { it.isNotEmpty() }
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.trim()?.takeIf { it.isNotEmpty() }
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.take(4)?.toIntOrNull() ?: 0
            trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore("/")?.toIntOrNull() ?: 0
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            bitrateKbps = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0) / 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRateHz = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull() ?: 0
                bitDepth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    ?.toIntOrNull() ?: 16
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let {
                if (it.isNotBlank()) mimeType = it
            }

            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                artworkPath = persistArtwork(uri.toString(), artBytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Retriever failed for $uri: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }

        val codecInfo = detectCodec(fallbackFileName, mimeType, bitrateKbps, sampleRateHz, bitDepth)
        val embeddedLyrics = runCatching { extractEmbeddedLyrics(uri) }.getOrNull()

        return ExtractedAudioDetails(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            durationMs = durationMs,
            bitrateKbps = if (bitrateKbps > 0) bitrateKbps else estimateBitrateKbps(fallbackSizeBytes, durationMs),
            sampleRateHz = sampleRateHz,
            bitDepth = codecInfo.bitDepth,
            mimeType = mimeType,
            codecLabel = codecInfo.codecLabel,
            isLossless = codecInfo.isLossless,
            isHiRes = codecInfo.isHiRes,
            artworkPath = artworkPath,
            embeddedLyrics = embeddedLyrics
        )
    }

    fun buildTrackFromUri(uri: Uri, displayName: String, sizeBytes: Long, folderLabel: String): Track {
        val cleanName = displayName.substringBeforeLast(".").ifBlank { "未知曲目" }
        val details = extract(uri, displayName, sizeBytes)
        val stableId = sha256(uri.toString())
        val resolvedArtist = details.artist ?: "未知艺术家"
        val resolvedAlbum = details.album ?: "未知专辑"
        val isGeneral = classifyAudioAsGeneral(
            rawTitleTag = details.title,
            artist = resolvedArtist,
            album = resolvedAlbum,
            artworkPath = details.artworkPath,
            genre = details.genre.orEmpty(),
            year = details.year,
            isLossless = details.isLossless,
            isMusicFlag = true
        )
        return Track(
            id = stableId,
            contentUri = uri.toString(),
            title = details.title ?: cleanName,
            artist = resolvedArtist,
            album = resolvedAlbum,
            albumArtist = details.albumArtist.orEmpty(),
            genre = details.genre.orEmpty(),
            year = details.year,
            trackNumber = details.trackNumber,
            durationMs = details.durationMs,
            sizeBytes = sizeBytes,
            folderPath = folderLabel,
            fileName = displayName,
            mimeType = details.mimeType,
            codecLabel = details.codecLabel,
            bitrateKbps = details.bitrateKbps,
            sampleRateHz = details.sampleRateHz,
            bitDepth = details.bitDepth,
            isLossless = details.isLossless,
            isHiRes = details.isHiRes,
            artworkPath = details.artworkPath,
            albumArtUri = null,
            embeddedLyrics = details.embeddedLyrics,
            dateAddedSec = System.currentTimeMillis() / 1000L,
            isGeneralAudio = isGeneral
        )
    }

    companion object {
        /**
         * Returns true when an audio file lacks sufficient evidence of being a structured music track
         * (e.g. voice memos, downloaded clips, sound effects with no artist/album/artwork/genre tags).
         */
        fun classifyAudioAsGeneral(
            rawTitleTag: String?,
            artist: String?,
            album: String?,
            artworkPath: String?,
            genre: String,
            year: Int,
            isLossless: Boolean,
            isMusicFlag: Boolean
        ): Boolean {
            val hasArtist = com.example.model.isValidMusicTag(artist, "未知艺术家")
            val hasAlbum = com.example.model.isValidMusicTag(album, "未知专辑")
            val hasTitleTag = com.example.model.isValidMusicTag(rawTitleTag)
            val hasArt = !artworkPath.isNullOrBlank()
            val hasExtraMeta = genre.isNotBlank() || year > 0
            if (!isMusicFlag && !hasArtist && !hasArt) return true
            if (hasArtist) return false
            if (hasAlbum && (hasArt || isLossless || hasExtraMeta)) return false
            if (hasArt && (hasTitleTag || isLossless)) return false
            return true
        }
    }

    private fun persistArtwork(key: String, bytes: ByteArray): String? {
        return try {
            val fileName = "${sha256(key).take(20)}.jpg"
            val outFile = File(artCacheDir, fileName)
            if (!outFile.exists() || outFile.length() == 0L) {
                FileOutputStream(outFile).use { it.write(bytes) }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache artwork: ${e.message}")
            null
        }
    }

    private data class CodecInfo(
        val codecLabel: String,
        val isLossless: Boolean,
        val isHiRes: Boolean,
        val bitDepth: Int
    )

    private fun detectCodec(
        fileName: String,
        mimeType: String,
        bitrateKbps: Int,
        sampleRateHz: Int,
        bitDepth: Int
    ): CodecInfo {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        val mime = mimeType.lowercase()
        val label = when {
            ext == "flac" || mime.contains("flac") -> "FLAC"
            ext == "wav" || mime.contains("wav") -> "WAV"
            ext == "alac" || ext == "aiff" || ext == "aif" -> ext.uppercase()
            ext == "ape" -> "APE"
            ext == "dsf" || ext == "dff" -> "DSD"
            ext == "opus" || mime.contains("opus") -> "OPUS"
            ext == "ogg" || mime.contains("ogg") || mime.contains("vorbis") -> "OGG"
            ext == "m4a" || ext == "aac" || mime.contains("mp4") || mime.contains("aac") -> {
                if (bitrateKbps >= 700) "ALAC" else "AAC"
            }
            ext == "mp3" || mime.contains("mpeg") -> "MP3"
            else -> ext.uppercase().ifBlank { "AUDIO" }
        }
        val lossless = label in setOf("FLAC", "WAV", "ALAC", "AIFF", "AIF", "APE", "DSD")
        val effectiveBitDepth = if (lossless && bitDepth <= 16 && bitrateKbps > 1500) 24 else bitDepth
        val hiRes = lossless && (sampleRateHz > 48000 || effectiveBitDepth >= 24 || bitrateKbps >= 1600)
        return CodecInfo(label, lossless, hiRes, effectiveBitDepth)
    }

    private fun estimateBitrateKbps(sizeBytes: Long, durationMs: Long): Int {
        if (sizeBytes <= 0L || durationMs <= 1000L) return 0
        return ((sizeBytes * 8L) / durationMs).toInt().coerceIn(32, 9600)
    }

    /**
     * Reads embedded lyrics from ID3v2 (USLT), FLAC Vorbis Comments (LYRICS/UNSYNCEDLYRICS),
     * or MP4/M4A (©lyr) atoms by inspecting the container header safely within a capped window.
     */
    private fun extractEmbeddedLyrics(uri: Uri): String? {
        val maxScanBytes = 512 * 1024 // Read up to 512KB header safely
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(maxScanBytes)
            var totalRead = 0
            while (totalRead < maxScanBytes) {
                val read = input.read(buffer, totalRead, maxScanBytes - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            buffer.copyOf(totalRead)
        } ?: return null

        if (bytes.size < 16) return null

        // 1. Check ID3v2 USLT frame (MP3 / AIFF)
        if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            parseId3v2Uslt(bytes)?.let { return it }
        }

        // 2. Check FLAC Vorbis Comment LYRICS / UNSYNCEDLYRICS
        if (bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()
        ) {
            parseFlacLyrics(bytes)?.let { return it }
        }

        // 3. Check MP4 / M4A ©lyr atom
        parseMp4Lyr(bytes)?.let { return it }

        return null
    }

    private fun parseId3v2Uslt(data: ByteArray): String? {
        val versionMajor = data[3].toInt() and 0xFF
        val tagSize = syncSafeInt(data, 6)
        val limit = min(data.size, 10 + tagSize)
        var offset = 10

        // Skip extended header if present
        val flags = data[5].toInt() and 0xFF
        if ((flags and 0x40) != 0 && offset + 4 <= limit) {
            val extSize = if (versionMajor == 4) syncSafeInt(data, offset) else bigEndianInt(data, offset)
            offset += extSize + 4
        }

        while (offset + 10 < limit) {
            val frameId = String(data, offset, 4, Charsets.ISO_8859_1)
            if (frameId[0] == '\u0000') break
            val frameSize = if (versionMajor == 4) {
                syncSafeInt(data, offset + 4)
            } else {
                bigEndianInt(data, offset + 4)
            }
            if (frameSize <= 0 || offset + 10 + frameSize > limit) break

            if (frameId == "USLT") {
                val payloadStart = offset + 10
                val encoding = data[payloadStart].toInt() and 0xFF
                // Skip 1 byte encoding + 3 bytes language code
                var cursor = payloadStart + 4
                val payloadEnd = payloadStart + frameSize
                if (cursor < payloadEnd) {
                    val textBytes = when (encoding) {
                        1, 2 -> {
                            // UTF-16 null-terminated description (2 zero bytes)
                            var descEnd = cursor
                            while (descEnd + 1 < payloadEnd) {
                                if (data[descEnd] == 0.toByte() && data[descEnd + 1] == 0.toByte()) {
                                    descEnd += 2
                                    break
                                }
                                descEnd += 2
                            }
                            cursor = descEnd
                            data.copyOfRange(cursor.coerceAtMost(payloadEnd), payloadEnd)
                        }
                        else -> {
                            while (cursor < payloadEnd && data[cursor] != 0.toByte()) cursor++
                            if (cursor < payloadEnd) cursor++ // skip null
                            data.copyOfRange(cursor.coerceAtMost(payloadEnd), payloadEnd)
                        }
                    }
                    val charset: Charset = when (encoding) {
                        1 -> Charsets.UTF_16
                        2 -> Charsets.UTF_16BE
                        3 -> Charsets.UTF_8
                        else -> Charsets.ISO_8859_1
                    }
                    val decoded = String(textBytes, charset).trim().trimEnd('\u0000')
                    if (decoded.isNotBlank()) return decoded
                }
            }
            offset += 10 + frameSize
        }
        return null
    }

    private fun parseFlacLyrics(data: ByteArray): String? {
        var offset = 4
        while (offset + 4 <= data.size) {
            val header = data[offset].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val length = ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
            offset += 4
            if (offset + length > data.size) break

            if (blockType == 4) { // VORBIS_COMMENT
                var cursor = offset
                if (cursor + 4 > offset + length) break
                val vendorLen = littleEndianInt(data, cursor)
                cursor += 4 + vendorLen
                if (cursor + 4 > offset + length) break
                val commentCount = littleEndianInt(data, cursor)
                cursor += 4
                for (i in 0 until commentCount) {
                    if (cursor + 4 > offset + length) break
                    val commentLen = littleEndianInt(data, cursor)
                    cursor += 4
                    if (commentLen < 0 || cursor + commentLen > offset + length) break
                    val comment = String(data, cursor, commentLen, Charsets.UTF_8)
                    cursor += commentLen
                    val eqIdx = comment.indexOf('=')
                    if (eqIdx > 0) {
                        val key = comment.substring(0, eqIdx).uppercase()
                        if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYLT") {
                            val value = comment.substring(eqIdx + 1).trim()
                            if (value.isNotBlank()) return value
                        }
                    }
                }
            }
            offset += length
            if (isLast) break
        }
        return null
    }

    private fun parseMp4Lyr(data: ByteArray): String? {
        // Search for ©lyr (0xA9, 'l', 'y', 'r') followed by 'data' sub-atom
        for (i in 0 until data.size - 24) {
            if (data[i] == 0xA9.toByte() &&
                data[i + 1] == 'l'.code.toByte() &&
                data[i + 2] == 'y'.code.toByte() &&
                data[i + 3] == 'r'.code.toByte()
            ) {
                val dataAtomStart = i + 4
                if (dataAtomStart + 16 < data.size &&
                    data[dataAtomStart + 4] == 'd'.code.toByte() &&
                    data[dataAtomStart + 5] == 'a'.code.toByte() &&
                    data[dataAtomStart + 6] == 't'.code.toByte() &&
                    data[dataAtomStart + 7] == 'a'.code.toByte()
                ) {
                    val atomSize = bigEndianInt(data, dataAtomStart)
                    val textStart = dataAtomStart + 16
                    val textEnd = min(data.size, dataAtomStart + atomSize)
                    if (textEnd > textStart) {
                        val text = String(data, textStart, textEnd - textStart, Charsets.UTF_8).trim()
                        if (text.isNotBlank()) return text
                    }
                }
            }
        }
        return null
    }

    private fun syncSafeInt(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0x7F) shl 21) or
            ((b[offset + 1].toInt() and 0x7F) shl 14) or
            ((b[offset + 2].toInt() and 0x7F) shl 7) or
            (b[offset + 3].toInt() and 0x7F)
    }

    private fun bigEndianInt(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
    }

    private fun littleEndianInt(b: ByteArray, offset: Int): Int {
        return (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
