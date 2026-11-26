package com.example.data.lyrics

import com.example.model.LyricFormat
import com.example.model.Track
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AudioTagWriteOutcome(
    val patchedBytes: ByteArray,
    val formatLabel: String,
    val tagFieldLabel: String
)

/**
 * Pure-Kotlin, zero-dependency binary audio metadata writer that embeds synchronized or plain lyrics
 * directly into original audio files while preserving all existing tags, embedded artwork, and
 * lossless/lossy audio stream bytes intact.
 *
 * Supported containers:
 * - MP3 (ID3v2.3 UTF-16 USLT & ID3v2.4 UTF-8 USLT; prepends ID3v2.4 header if bare MP3)
 * - FLAC (VORBIS_COMMENT block #4 `LYRICS=...` UTF-8)
 * - MP4 / M4A / ALAC / AAC (`moov -> udta -> meta -> ilst -> ©lyr -> data` UTF-8 with exact `stco`/`co64` offset adjustment)
 */
object AudioLyricsTagWriter {

    fun embedLyrics(
        originalBytes: ByteArray,
        lyricsText: String,
        fileName: String,
        mimeType: String = ""
    ): Result<AudioTagWriteOutcome> {
        val cleanedLyrics = lyricsText.trim()
        if (cleanedLyrics.isEmpty()) {
            return Result.failure(IllegalArgumentException("歌词内容为空，无法刻录到音频文件"))
        }
        if (originalBytes.size < 16) {
            return Result.failure(IllegalArgumentException("音频文件数据不完整，无法写入标签"))
        }

        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = mimeType.lowercase()

        return runCatching {
            when {
                isFlac(originalBytes) -> AudioTagWriteOutcome(
                    patchedBytes = embedFlacVorbisLyrics(originalBytes, cleanedLyrics),
                    formatLabel = "FLAC",
                    tagFieldLabel = "Vorbis Comment (LYRICS)"
                )

                isMp4(originalBytes) || ext in setOf("m4a", "mp4", "alac", "aac") || mime.contains("mp4") || mime.contains("m4a") -> {
                    if (!isMp4(originalBytes)) {
                        error("该文件不是标准的 MP4/M4A 容器，暂不支持直接刻录标签")
                    }
                    AudioTagWriteOutcome(
                        patchedBytes = embedMp4Lyrics(originalBytes, cleanedLyrics),
                        formatLabel = "MP4/M4A",
                        tagFieldLabel = "iTunes Atom (©lyr)"
                    )
                }

                isId3v2(originalBytes) || ext == "mp3" || mime.contains("mpeg") || isMp3SyncFrame(originalBytes) -> AudioTagWriteOutcome(
                    patchedBytes = embedId3v2Uslt(originalBytes, cleanedLyrics),
                    formatLabel = "MP3",
                    tagFieldLabel = "ID3v2 (USLT)"
                )

                else -> error("暂不支持将歌词直接刻录到「${ext.uppercase().ifBlank { "此格式" }}」音频文件（当前支持 MP3 / FLAC / M4A / MP4），建议使用「导出歌词文件」")
            }
        }
    }

    fun suggestExportFileNameAndMime(
        track: Track,
        formatName: String?,
        rawContent: String
    ): Pair<String, String> {
        val format = runCatching {
            if (!formatName.isNullOrBlank()) LyricFormat.valueOf(formatName) else null
        }.getOrNull() ?: LyricParser.parse(rawContent).format

        val ext = when (format) {
            LyricFormat.QRC -> "qrc"
            LyricFormat.YRC -> "yrc"
            LyricFormat.KRC -> "krc"
            LyricFormat.TTML -> "ttml"
            LyricFormat.SRT -> "srt"
            LyricFormat.LRC, LyricFormat.ENHANCED_LRC -> "lrc"
            LyricFormat.PLAIN, LyricFormat.NONE -> "txt"
        }

        val safeTitle = sanitizeFileSegment(track.title).ifBlank { "track" }
        val safeArtist = sanitizeFileSegment(track.artist)
            .takeIf { it.isNotBlank() && it != "未知艺术家" }
        val baseName = if (safeArtist != null) "$safeTitle - $safeArtist" else safeTitle
        val mime = when (ext) {
            "ttml" -> "application/ttml+xml"
            "srt" -> "application/x-subrip"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
        return "$baseName.$ext" to mime
    }

    private fun sanitizeFileSegment(raw: String): String {
        return raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
            .replace(Regex("\\s+"), " ")
            .take(64)
            .trim()
    }

    // -------------------------------------------------------------------------
    // Container Detection
    // -------------------------------------------------------------------------

    private fun isId3v2(b: ByteArray): Boolean =
        b.size >= 10 && b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()

    private fun isMp3SyncFrame(b: ByteArray): Boolean =
        b.size >= 2 && (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xE0) == 0xE0

    private fun isFlac(b: ByteArray): Boolean =
        b.size >= 8 && b[0] == 'f'.code.toByte() && b[1] == 'L'.code.toByte() &&
            b[2] == 'a'.code.toByte() && b[3] == 'C'.code.toByte()

    private fun isMp4(b: ByteArray): Boolean =
        b.size >= 12 && b[4] == 'f'.code.toByte() && b[5] == 't'.code.toByte() &&
            b[6] == 'y'.code.toByte() && b[7] == 'p'.code.toByte()

    // -------------------------------------------------------------------------
    // 1. MP3 ID3v2 USLT Writer (preserves all existing frames & audio bytes)
    // -------------------------------------------------------------------------

    private fun embedId3v2Uslt(original: ByteArray, lyrics: String): ByteArray {
        val out = ByteArrayOutputStream(original.size + lyrics.length * 2 + 128)
        if (!isId3v2(original)) {
            // Bare MP3 without ID3v2 header -> prepend clean ID3v2.4 header with UTF-8 USLT frame
            val usltFrame = buildUsltFrame(versionMajor = 4, lyrics = lyrics)
            out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0))
            out.write(encodeSyncSafeInt(usltFrame.size))
            out.write(usltFrame)
            out.write(original)
            return out.toByteArray()
        }

        val versionMajor = original[3].toInt() and 0xFF
        val versionMinor = original[4].toInt() and 0xFF
        val flags = original[5].toInt() and 0xFF
        if (versionMajor != 3 && versionMajor != 4) {
            error("不支持的 ID3v2.$versionMajor 标签版本，建议使用「导出歌词文件」")
        }
        if ((flags and 0x80) != 0) {
            error("该 MP3 使用了全局非同步化 (Unsynchronisation) ID3v2 标签，为保护文件安全请使用「导出歌词文件」")
        }

        val oldTagSize = readSyncSafeInt(original, 6)
        val audioStart = (10 + oldTagSize).coerceAtMost(original.size)

        val framesBuffer = ByteArrayOutputStream(oldTagSize + lyrics.length * 2 + 64)
        var cursor = 10

        // Preserve extended header if present
        if ((flags and 0x40) != 0 && cursor + 4 <= audioStart) {
            val extRawSize = if (versionMajor == 4) readSyncSafeInt(original, cursor) else readBigEndianInt(original, cursor)
            val extTotal = if (versionMajor == 4) extRawSize else extRawSize + 4
            if (extTotal > 0 && cursor + extTotal <= audioStart) {
                framesBuffer.write(original, cursor, extTotal)
                cursor += extTotal
            }
        }

        // Copy every existing frame EXCEPT old USLT frames (and trailing zero padding)
        while (cursor + 10 <= audioStart) {
            if (original[cursor] == 0.toByte()) break
            val frameId = String(original, cursor, 4, Charsets.ISO_8859_1)
            val frameSize = if (versionMajor == 4) {
                readSyncSafeInt(original, cursor + 4)
            } else {
                readBigEndianInt(original, cursor + 4)
            }
            if (frameSize <= 0 || cursor + 10 + frameSize > audioStart) break
            if (frameId != "USLT") {
                framesBuffer.write(original, cursor, 10 + frameSize)
            }
            cursor += 10 + frameSize
        }

        // Append our fresh USLT frame
        val newUsltFrame = buildUsltFrame(versionMajor = versionMajor, lyrics = lyrics)
        framesBuffer.write(newUsltFrame)

        val framesBytes = framesBuffer.toByteArray()
        // Clear footer bit (0x10) since we emit clean header + frames directly before audio
        val cleanFlags = (flags and 0xEF).toByte()
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), versionMajor.toByte(), versionMinor.toByte(), cleanFlags))
        out.write(encodeSyncSafeInt(framesBytes.size))
        out.write(framesBytes)
        if (audioStart < original.size) {
            out.write(original, audioStart, original.size - audioStart)
        }
        return out.toByteArray()
    }

    private fun buildUsltFrame(versionMajor: Int, lyrics: String): ByteArray {
        val payload = ByteArrayOutputStream(lyrics.length * 2 + 16)
        if (versionMajor == 3) {
            // ID3v2.3 universal compatibility: encoding = 1 (UTF-16 with BOM)
            payload.write(1)
            payload.write("und".toByteArray(Charsets.ISO_8859_1))
            // Empty content descriptor (UTF-16 BOM + double-null terminator)
            payload.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00))
            // UTF-16LE with BOM for lyrics text
            payload.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            payload.write(lyrics.toByteArray(Charsets.UTF_16LE))
        } else {
            // ID3v2.4 native UTF-8: encoding = 3
            payload.write(3)
            payload.write("und".toByteArray(Charsets.ISO_8859_1))
            payload.write(0) // empty description null terminator
            payload.write(lyrics.toByteArray(Charsets.UTF_8))
        }
        val payloadBytes = payload.toByteArray()
        val frame = ByteArrayOutputStream(10 + payloadBytes.size)
        frame.write("USLT".toByteArray(Charsets.ISO_8859_1))
        if (versionMajor == 4) {
            frame.write(encodeSyncSafeInt(payloadBytes.size))
        } else {
            frame.write(encodeBigEndianInt(payloadBytes.size))
        }
        frame.write(byteArrayOf(0, 0)) // frame flags
        frame.write(payloadBytes)
        return frame.toByteArray()
    }

    // -------------------------------------------------------------------------
    // 2. FLAC VORBIS_COMMENT LYRICS Writer (preserves STREAMINFO, PICTURE, etc.)
    // -------------------------------------------------------------------------

    private data class FlacBlock(val type: Int, val payload: ByteArray)

    private fun embedFlacVorbisLyrics(original: ByteArray, lyrics: String): ByteArray {
        val blocks = mutableListOf<FlacBlock>()
        var offset = 4
        var reachedLast = false
        while (offset + 4 <= original.size) {
            val header = original[offset].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val length = ((original[offset + 1].toInt() and 0xFF) shl 16) or
                ((original[offset + 2].toInt() and 0xFF) shl 8) or
                (original[offset + 3].toInt() and 0xFF)
            offset += 4
            if (offset + length > original.size) {
                error("FLAC 元数据块已损坏，终止写入以保护原文件")
            }
            blocks.add(FlacBlock(blockType, original.copyOfRange(offset, offset + length)))
            offset += length
            if (isLast) {
                reachedLast = true
                break
            }
        }
        if (!reachedLast || blocks.isEmpty()) {
            error("未能识别完整的 FLAC 元数据头，终止写入以保护原文件")
        }

        val audioStart = offset
        val existingVorbisIdx = blocks.indexOfFirst { it.type == 4 }
        val updatedVorbisPayload = if (existingVorbisIdx >= 0) {
            patchVorbisCommentBlock(blocks[existingVorbisIdx].payload, lyrics)
        } else {
            buildFreshVorbisCommentBlock("Auralis", listOf("LYRICS=$lyrics"))
        }

        if (existingVorbisIdx >= 0) {
            blocks[existingVorbisIdx] = FlacBlock(4, updatedVorbisPayload)
        } else {
            // Insert right after STREAMINFO (index 0)
            blocks.add(1.coerceAtMost(blocks.size), FlacBlock(4, updatedVorbisPayload))
        }

        // Guard FLAC 24-bit block length limit (16MB)
        if (updatedVorbisPayload.size > 0xFFFFFF) {
            error("歌词数据超出 FLAC 元数据块上限")
        }

        val out = ByteArrayOutputStream(original.size + lyrics.length + 128)
        out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
        blocks.forEachIndexed { idx, block ->
            val isLast = idx == blocks.lastIndex
            val headerByte = (block.type and 0x7F) or (if (isLast) 0x80 else 0x00)
            val len = block.payload.size
            out.write(headerByte)
            out.write((len ushr 16) and 0xFF)
            out.write((len ushr 8) and 0xFF)
            out.write(len and 0xFF)
            out.write(block.payload)
        }
        if (audioStart < original.size) {
            out.write(original, audioStart, original.size - audioStart)
        }
        return out.toByteArray()
    }

    private fun patchVorbisCommentBlock(existing: ByteArray, lyrics: String): ByteArray {
        if (existing.size < 8) {
            return buildFreshVorbisCommentBlock("Auralis", listOf("LYRICS=$lyrics"))
        }
        var cursor = 0
        val vendorLen = readLittleEndianInt(existing, cursor)
        cursor += 4
        if (vendorLen < 0 || cursor + vendorLen + 4 > existing.size) {
            return buildFreshVorbisCommentBlock("Auralis", listOf("LYRICS=$lyrics"))
        }
        val vendor = String(existing, cursor, vendorLen, Charsets.UTF_8)
        cursor += vendorLen
        val count = readLittleEndianInt(existing, cursor)
        cursor += 4
        val comments = mutableListOf<String>()
        for (i in 0 until count) {
            if (cursor + 4 > existing.size) break
            val len = readLittleEndianInt(existing, cursor)
            cursor += 4
            if (len < 0 || cursor + len > existing.size) break
            val entry = String(existing, cursor, len, Charsets.UTF_8)
            cursor += len
            val eq = entry.indexOf('=')
            val key = if (eq > 0) entry.substring(0, eq).uppercase() else ""
            if (key != "LYRICS" && key != "UNSYNCEDLYRICS") {
                comments.add(entry)
            }
        }
        comments.add("LYRICS=$lyrics")
        return buildFreshVorbisCommentBlock(vendor.ifBlank { "Auralis" }, comments)
    }

    private fun buildFreshVorbisCommentBlock(vendor: String, comments: List<String>): ByteArray {
        val out = ByteArrayOutputStream(256)
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        out.write(encodeLittleEndianInt(vendorBytes.size))
        out.write(vendorBytes)
        out.write(encodeLittleEndianInt(comments.size))
        for (c in comments) {
            val cb = c.toByteArray(Charsets.UTF_8)
            out.write(encodeLittleEndianInt(cb.size))
            out.write(cb)
        }
        return out.toByteArray()
    }

    // -------------------------------------------------------------------------
    // 3. MP4 / M4A ©lyr Atom Writer (with exact stco/co64 chunk offset patching)
    // -------------------------------------------------------------------------

    private data class Mp4Box(
        val type: String,
        val start: Int,
        val size: Int,
        val headerSize: Int
    )

    private fun embedMp4Lyrics(original: ByteArray, lyrics: String): ByteArray {
        val topBoxes = parseMp4Boxes(original, 0, original.size)
        val moovBox = topBoxes.firstOrNull { it.type == "moov" }
            ?: error("未能找到 MP4/M4A 的 moov 元数据容器")
        val mdatBox = topBoxes.firstOrNull { it.type == "mdat" }

        val oldMoovBytes = original.copyOfRange(moovBox.start, moovBox.start + moovBox.size)
        val newMoovContent = patchMoovWithLyrics(
            oldMoovBytes.copyOfRange(moovBox.headerSize, oldMoovBytes.size),
            lyrics
        )
        val rebuiltMoov = wrapMp4Box("moov", newMoovContent)
        val delta = rebuiltMoov.size - oldMoovBytes.size

        // If moov comes before mdat and changed size, update all stco/co64 chunk offsets inside moov!
        val finalMoov = if (delta != 0 && mdatBox != null && moovBox.start < mdatBox.start) {
            adjustMp4ChunkOffsetsInMoov(rebuiltMoov, delta.toLong())
        } else {
            rebuiltMoov
        }

        val out = ByteArrayOutputStream(original.size + delta.coerceAtLeast(0))
        out.write(original, 0, moovBox.start)
        out.write(finalMoov)
        val afterMoov = moovBox.start + moovBox.size
        if (afterMoov < original.size) {
            out.write(original, afterMoov, original.size - afterMoov)
        }
        return out.toByteArray()
    }

    private fun patchMoovWithLyrics(moovPayload: ByteArray, lyrics: String): ByteArray {
        val children = parseMp4Boxes(moovPayload, 0, moovPayload.size)
        val udtaBox = children.firstOrNull { it.type == "udta" }
        val newUdtaPayload = if (udtaBox != null) {
            patchUdtaWithLyrics(
                moovPayload.copyOfRange(udtaBox.start + udtaBox.headerSize, udtaBox.start + udtaBox.size),
                lyrics
            )
        } else {
            wrapMp4Box("meta", buildFullBoxMeta(buildIlstWithLyrics(emptyList(), lyrics)))
        }
        val newUdtaBox = wrapMp4Box("udta", newUdtaPayload)
        return replaceOrAppendMp4Child(moovPayload, children, "udta", newUdtaBox)
    }

    private fun patchUdtaWithLyrics(udtaPayload: ByteArray, lyrics: String): ByteArray {
        val children = parseMp4Boxes(udtaPayload, 0, udtaPayload.size)
        val metaBox = children.firstOrNull { it.type == "meta" }
        val newMetaPayload = if (metaBox != null && metaBox.size >= metaBox.headerSize + 4) {
            val metaBodyStart = metaBox.start + metaBox.headerSize
            val versionFlags = udtaPayload.copyOfRange(metaBodyStart, metaBodyStart + 4)
            val metaInner = udtaPayload.copyOfRange(metaBodyStart + 4, metaBox.start + metaBox.size)
            val metaChildren = parseMp4Boxes(metaInner, 0, metaInner.size)
            val ilstBox = metaChildren.firstOrNull { it.type == "ilst" }
            val existingIlstChildren = if (ilstBox != null) {
                val ilstPayload = metaInner.copyOfRange(ilstBox.start + ilstBox.headerSize, ilstBox.start + ilstBox.size)
                parseMp4Boxes(ilstPayload, 0, ilstPayload.size).map { box ->
                    box.type to ilstPayload.copyOfRange(box.start, box.start + box.size)
                }
            } else {
                emptyList()
            }
            val newIlstBox = wrapMp4Box("ilst", buildIlstWithLyrics(existingIlstChildren, lyrics))
            val patchedMetaInner = if (metaChildren.any { it.type == "hdlr" }) {
                replaceOrAppendMp4Child(metaInner, metaChildren, "ilst", newIlstBox)
            } else {
                val hdlr = buildMdirHdlrBox()
                val rest = replaceOrAppendMp4Child(metaInner, metaChildren, "ilst", newIlstBox)
                hdlr + rest
            }
            versionFlags + patchedMetaInner
        } else {
            buildFullBoxMeta(buildIlstWithLyrics(emptyList(), lyrics))
        }
        val newMetaBox = wrapMp4Box("meta", newMetaPayload)
        return replaceOrAppendMp4Child(udtaPayload, children, "meta", newMetaBox)
    }

    private fun buildFullBoxMeta(ilstPayload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 0)) // FullBox version + flags
        out.write(buildMdirHdlrBox())
        out.write(wrapMp4Box("ilst", ilstPayload))
        return out.toByteArray()
    }

    private fun buildMdirHdlrBox(): ByteArray {
        val payload = ByteArrayOutputStream(32)
        payload.write(byteArrayOf(0, 0, 0, 0)) // version/flags
        payload.write(byteArrayOf(0, 0, 0, 0)) // pre_defined
        payload.write("mdir".toByteArray(Charsets.ISO_8859_1))
        payload.write("appl".toByteArray(Charsets.ISO_8859_1))
        payload.write(ByteArray(9)) // reserved + empty name null terminator
        return wrapMp4Box("hdlr", payload.toByteArray())
    }

    private fun buildIlstWithLyrics(existingItems: List<Pair<String, ByteArray>>, lyrics: String): ByteArray {
        val lyrType = String(byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()), Charsets.ISO_8859_1)
        val out = ByteArrayOutputStream()
        for ((type, rawBox) in existingItems) {
            if (type != lyrType) {
                out.write(rawBox)
            }
        }
        val textBytes = lyrics.toByteArray(Charsets.UTF_8)
        val dataPayload = ByteArrayOutputStream(8 + textBytes.size)
        dataPayload.write(byteArrayOf(0, 0, 0, 1)) // data type 1 = UTF-8 text
        dataPayload.write(byteArrayOf(0, 0, 0, 0)) // locale = 0
        dataPayload.write(textBytes)
        val dataBox = wrapMp4Box("data", dataPayload.toByteArray())
        val lyrBox = wrapMp4BoxBytes(
            byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()),
            dataBox
        )
        out.write(lyrBox)
        return out.toByteArray()
    }

    private fun replaceOrAppendMp4Child(
        parentPayload: ByteArray,
        children: List<Mp4Box>,
        targetType: String,
        replacementBox: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream(parentPayload.size + replacementBox.size)
        var replaced = false
        for (child in children) {
            if (child.type == targetType) {
                if (!replaced) {
                    out.write(replacementBox)
                    replaced = true
                }
            } else {
                out.write(parentPayload, child.start, child.size)
            }
        }
        if (!replaced) {
            out.write(replacementBox)
        }
        return out.toByteArray()
    }

    private fun adjustMp4ChunkOffsetsInMoov(moovBoxBytes: ByteArray, delta: Long): ByteArray {
        val patched = moovBoxBytes.copyOf()
        fun walk(start: Int, end: Int) {
            val boxes = parseMp4Boxes(patched, start, end)
            for (box in boxes) {
                val payloadStart = box.start + box.headerSize
                val payloadEnd = box.start + box.size
                when (box.type) {
                    "moov", "trak", "mdia", "minf", "stbl", "edts" -> walk(payloadStart, payloadEnd)
                    "stco" -> {
                        if (payloadStart + 8 <= payloadEnd) {
                            val entryCount = readBigEndianInt(patched, payloadStart + 4)
                            var cur = payloadStart + 8
                            for (i in 0 until entryCount) {
                                if (cur + 4 > payloadEnd) break
                                val oldOff = readBigEndianInt(patched, cur).toLong() and 0xFFFFFFFFL
                                val newOff = (oldOff + delta).coerceAtLeast(0L)
                                val encoded = encodeBigEndianInt(newOff.toInt())
                                System.arraycopy(encoded, 0, patched, cur, 4)
                                cur += 4
                            }
                        }
                    }
                    "co64" -> {
                        if (payloadStart + 8 <= payloadEnd) {
                            val entryCount = readBigEndianInt(patched, payloadStart + 4)
                            var cur = payloadStart + 8
                            for (i in 0 until entryCount) {
                                if (cur + 8 > payloadEnd) break
                                val bb = ByteBuffer.wrap(patched, cur, 8).order(ByteOrder.BIG_ENDIAN)
                                val newOff = (bb.long + delta).coerceAtLeast(0L)
                                ByteBuffer.wrap(patched, cur, 8).order(ByteOrder.BIG_ENDIAN).putLong(newOff)
                                cur += 8
                            }
                        }
                    }
                }
            }
        }
        walk(0, patched.size)
        return patched
    }

    private fun parseMp4Boxes(data: ByteArray, start: Int, end: Int): List<Mp4Box> {
        val result = mutableListOf<Mp4Box>()
        var offset = start
        while (offset + 8 <= end) {
            val rawSize = readBigEndianInt(data, offset).toLong() and 0xFFFFFFFFL
            val type = String(data, offset + 4, 4, Charsets.ISO_8859_1)
            val (boxSize, headerSize) = when (rawSize) {
                0L -> (end - offset) to 8
                1L -> {
                    if (offset + 16 > end) break
                    val extSize = ByteBuffer.wrap(data, offset + 8, 8).order(ByteOrder.BIG_ENDIAN).long
                    if (extSize < 16L || extSize > Int.MAX_VALUE) break
                    extSize.toInt() to 16
                }
                else -> {
                    if (rawSize < 8L || rawSize > Int.MAX_VALUE) break
                    rawSize.toInt() to 8
                }
            }
            if (offset + boxSize > end) break
            result.add(Mp4Box(type = type, start = offset, size = boxSize, headerSize = headerSize))
            offset += boxSize
        }
        return result
    }

    private fun wrapMp4Box(type: String, payload: ByteArray): ByteArray =
        wrapMp4BoxBytes(type.toByteArray(Charsets.ISO_8859_1), payload)

    private fun wrapMp4BoxBytes(typeBytes: ByteArray, payload: ByteArray): ByteArray {
        val total = 8 + payload.size
        val out = ByteArrayOutputStream(total)
        out.write(encodeBigEndianInt(total))
        out.write(typeBytes, 0, 4)
        out.write(payload)
        return out.toByteArray()
    }

    // -------------------------------------------------------------------------
    // Binary Integer Codecs
    // -------------------------------------------------------------------------

    private fun readSyncSafeInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0x7F) shl 21) or
            ((b[offset + 1].toInt() and 0x7F) shl 14) or
            ((b[offset + 2].toInt() and 0x7F) shl 7) or
            (b[offset + 3].toInt() and 0x7F)

    private fun encodeSyncSafeInt(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte()
    )

    private fun readBigEndianInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

    private fun encodeBigEndianInt(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun readLittleEndianInt(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)

    private fun encodeLittleEndianInt(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )
}
