package com.example.data.lyrics

import com.example.model.LyricDocument
import com.example.model.LyricFormat
import com.example.model.LyricLine
import com.example.model.LyricWord
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlin.math.max
import kotlin.math.roundToLong

object LyricParser {

    private val QRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val QRC_WORD_REGEX = Regex("""([^\(\)\r\n]+?)\((\d+),(\d+)\)""")
    private val YRC_WORD_REGEX = Regex("""\((\d+),(\d+)(?:,\d+)?\)([^\(\)\r\n]*)""")
    private val KRC_WORD_REGEX = Regex("""<(\d+),(\d+)(?:,\d+)?>([^<\r\n]*)""")
    private val LRC_TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val ENHANCED_WORD_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>([^<\r\n]*)""")
    private val OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)
    private val SRT_TIME_REGEX = Regex(
        """(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})"""
    )
    private val TTML_P_REGEX = Regex(
        """<p\b[^>]*begin="([^"]+)"[^>]*end="([^"]+)"[^>]*>(.*?)</p>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val TTML_SPAN_REGEX = Regex(
        """<span\b[^>]*begin="([^"]+)"[^>]*end="([^"]+)"[^>]*>(.*?)</span>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    fun parse(
        rawInput: String,
        sourceLabel: String = "",
        fileNameHint: String? = null,
        userOffsetMs: Long = 0L,
        enableSimulatedWordByWord: Boolean = true
    ): LyricDocument {
        val cleaned = rawInput.trimStart('\uFEFF').trim()
        if (cleaned.isBlank()) {
            return LyricDocument(LyricFormat.NONE, emptyList(), sourceLabel)
        }

        val fileExt = fileNameHint?.substringAfterLast(".", "")?.lowercase().orEmpty()
        val parsedOffset = OFFSET_REGEX.find(cleaned)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val totalOffset = parsedOffset + userOffsetMs

        // Extract inner LyricContent from QRC XML wrapper if present
        val unwrappedQrc = extractQrcXmlPayload(cleaned)
        val isYrcBody = Regex("""^\[\d+,\d+\]\(\d+,\d+""", RegexOption.MULTILINE).containsMatchIn(cleaned)
        val isQrcBody = Regex("""^\[\d+,\d+\][^\(\r\n]+\(\d+,\d+\)""", RegexOption.MULTILINE)
            .containsMatchIn(unwrappedQrc ?: cleaned)

        val baseDoc = when {
            fileExt == "ttml" || (cleaned.contains("<tt", ignoreCase = true) && cleaned.contains("<p", ignoreCase = true)) ->
                parseTtml(cleaned, sourceLabel, totalOffset)

            fileExt == "qrc" || unwrappedQrc != null ->
                parseQrc(unwrappedQrc ?: cleaned, sourceLabel, totalOffset)

            fileExt == "krc" || KRC_WORD_REGEX.containsMatchIn(cleaned) ->
                parseKrc(cleaned, sourceLabel, totalOffset)

            fileExt == "yrc" || isYrcBody ->
                parseYrc(cleaned, sourceLabel, totalOffset)

            isQrcBody ->
                parseQrc(cleaned, sourceLabel, totalOffset)

            fileExt == "srt" || fileExt == "vtt" || SRT_TIME_REGEX.containsMatchIn(cleaned) ->
                parseSrt(cleaned, sourceLabel, totalOffset)

            ENHANCED_WORD_REGEX.containsMatchIn(cleaned) && LRC_TIME_REGEX.containsMatchIn(cleaned) ->
                parseEnhancedLrc(cleaned, sourceLabel, totalOffset)

            LRC_TIME_REGEX.containsMatchIn(cleaned) ->
                parseStandardLrc(cleaned, sourceLabel, totalOffset)

            else -> parsePlainText(cleaned, sourceLabel)
        }

        return if (enableSimulatedWordByWord && baseDoc.hasSyncedTimestamps && !baseDoc.hasWordTiming) {
            SimulatedKaraokeSynthesizer.synthesize(baseDoc)
        } else {
            baseDoc
        }
    }

    /**
     * Decodes binary Kugou .krc files starting with header "krc1".
     */
    fun decodeRawBytes(bytes: ByteArray, fileNameHint: String?): String {
        if (bytes.size > 4 &&
            bytes[0] == 'k'.code.toByte() &&
            bytes[1] == 'r'.code.toByte() &&
            bytes[2] == 'c'.code.toByte() &&
            bytes[3] == '1'.code.toByte()
        ) {
            return runCatching { decodeBinaryKrc(bytes) }
                .getOrElse { String(bytes, Charsets.UTF_8) }
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun decodeBinaryKrc(bytes: ByteArray): String {
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val payload = ByteArray(bytes.size - 4)
        for (i in payload.indices) {
            payload[i] = (bytes[i + 4].toInt() xor key[i % key.size]).toByte()
        }
        val inflater = Inflater()
        inflater.setInput(payload)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buf)
            if (count <= 0) break
            out.write(buf, 0, count)
        }
        inflater.end()
        return out.toString("UTF-8")
    }

    private fun extractQrcXmlPayload(text: String): String? {
        val regex = Regex("""LyricContent="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(text) ?: return null
        return match.groupValues[1]
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#10;", "\n")
            .replace("&#13;", "\r")
    }

    private fun parseQrc(text: String, sourceLabel: String, offsetMs: Long): LyricDocument {
        val lines = mutableListOf<LyricLine>()
        for (rawLine in text.lineSequence()) {
            val trimmed = rawLine.trim()
            val match = QRC_LINE_REGEX.matchEntire(trimmed) ?: continue
            val lineStart = match.groupValues[1].toLongOrNull() ?: continue
            val lineDur = match.groupValues[2].toLongOrNull() ?: 3000L
            val body = match.groupValues[3]

            val words = QRC_WORD_REGEX.findAll(body).mapNotNull { w ->
                val token = w.groupValues[1]
                val wStart = w.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val wDur = w.groupValues[3].toLongOrNull() ?: 180L
                LyricWord(token, wStart, max(40L, wDur))
            }.toList()

            val fullText = if (words.isNotEmpty()) {
                words.joinToString("") { it.text }.trim()
            } else {
                body.trim()
            }
            if (fullText.isBlank()) continue

            lines.add(
                LyricLine(
                    startMs = lineStart,
                    endMs = lineStart + max(400L, lineDur),
                    text = fullText,
                    words = words,
                    isWordSyncedNative = words.isNotEmpty()
                )
            )
        }
        return LyricDocument(
            format = LyricFormat.QRC,
            lines = lines.sortedBy { it.startMs },
            sourceLabel = sourceLabel,
            rawContent = text,
            globalOffsetMs = offsetMs
        )
    }

    private fun parseYrc(text: String, sourceLabel: String, offsetMs: Long): LyricDocument {
        val lines = mutableListOf<LyricLine>()
        for (rawLine in text.lineSequence()) {
            val trimmed = rawLine.trim()
            val match = QRC_LINE_REGEX.matchEntire(trimmed) ?: continue
            val lineStart = match.groupValues[1].toLongOrNull() ?: continue
            val lineDur = match.groupValues[2].toLongOrNull() ?: 3000L
            val body = match.groupValues[3]

            val words = YRC_WORD_REGEX.findAll(body).mapNotNull { w ->
                val wStart = w.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val wDur = w.groupValues[2].toLongOrNull() ?: 180L
                val token = w.groupValues[3]
                if (token.isEmpty()) return@mapNotNull null
                LyricWord(token, wStart, max(40L, wDur))
            }.toList()

            val fullText = words.joinToString("") { it.text }.trim()
            if (fullText.isBlank()) continue

            lines.add(
                LyricLine(
                    startMs = lineStart,
                    endMs = lineStart + max(400L, lineDur),
                    text = fullText,
                    words = words,
                    isWordSyncedNative = words.isNotEmpty()
                )
            )
        }
        return LyricDocument(
            format = LyricFormat.YRC,
            lines = lines.sortedBy { it.startMs },
            sourceLabel = sourceLabel,
            rawContent = text,
            globalOffsetMs = offsetMs
        )
    }

    private fun parseKrc(text: String, sourceLabel: String, offsetMs: Long): LyricDocument {
        val lines = mutableListOf<LyricLine>()
        for (rawLine in text.lineSequence()) {
            val trimmed = rawLine.trim()
            val match = QRC_LINE_REGEX.matchEntire(trimmed) ?: continue
            val lineStart = match.groupValues[1].toLongOrNull() ?: continue
            val lineDur = match.groupValues[2].toLongOrNull() ?: 3000L
            val body = match.groupValues[3]

            val words = KRC_WORD_REGEX.findAll(body).mapNotNull { w ->
                val relStart = w.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val wDur = w.groupValues[2].toLongOrNull() ?: 180L
                val token = w.groupValues[3]
                if (token.isEmpty()) return@mapNotNull null
                LyricWord(token, lineStart + relStart, max(40L, wDur))
            }.toList()

            val fullText = words.joinToString("") { it.text }.trim()
            if (fullText.isBlank()) continue

            lines.add(
                LyricLine(
                    startMs = lineStart,
                    endMs = lineStart + max(400L, lineDur),
                    text = fullText,
                    words = words,
                    isWordSyncedNative = words.isNotEmpty()
                )
            )
        }
        return LyricDocument(
            format = LyricFormat.KRC,
            lines = lines.sortedBy { it.startMs },
            sourceLabel = sourceLabel,
            rawContent = text,
            globalOffsetMs = offsetMs
        )
    }

    private fun parseEnhancedLrc(text: String, sourceLabel: String, offsetMs: Long): LyricDocument {
        val tempLines = mutableListOf<Pair<Long, List<Pair<Long, String>>>>()
        for (rawLine in text.lineSequence()) {
            val trimmed = rawLine.trim()
            val lineMatches = LRC_TIME_REGEX.findAll(trimmed).toList()
            if (lineMatches.isEmpty()) continue
            val body = trimmed.substring(lineMatches.last().range.last + 1).trim()
            if (body.isBlank()) continue

            val wordMatches = ENHANCED_WORD_REGEX.findAll(body).toList()
            for (lm in lineMatches) {
                val lineStart = toMillis(lm.groupValues[1], lm.groupValues[2], lm.groupValues[3])
                val tokens = wordMatches.map { wm ->
                    val wStart = toMillis(wm.groupValues[1], wm.groupValues[2], wm.groupValues[3])
                    wStart to wm.groupValues[4]
                }.filter { it.second.isNotEmpty() }
                tempLines.add(lineStart to tokens)
            }
        }
        tempLines.sortBy { it.first }
        val lines = tempLines.mapIndexed { idx, (startMs, rawWords) ->
            val nextStart = tempLines.getOrNull(idx + 1)?.first ?: (startMs + 4500L)
            val endMs = max(startMs + 600L, nextStart)
            val words = rawWords.mapIndexed { wIdx, (wStart, wText) ->
                val nextW = rawWords.getOrNull(wIdx + 1)?.first ?: endMs
                LyricWord(wText, wStart, max(60L, nextW - wStart))
            }
            val fullText = words.joinToString("") { it.text }.trim()
            LyricLine(
                startMs = startMs,
                endMs = endMs,
                text = fullText,
                words = words,
                isWordSyncedNative = words.isNotEmpty()
            )
        }.filter { it.text.isNotBlank() }

        return LyricDocument(
            format = LyricFormat.ENHANCED_LRC,
            lines = lines,
            sourceLabel = sourceLabel,
            rawContent = text,
            globalOffsetMs = offsetMs
        )
    }

    private fun parseStandardLrc(text: String, sourceLabel: String, offsetMs: Long): LyricDocument {
        val entries = mutableListOf<Pair<Long, String>>()
        for (rawLine in text.lineSequence()) {
            val trimmed = rawLine.trim()
            val matches = LRC_TIME_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) continue
            val lyricText = trimmed.substring(matches.last().range.last + 1).trim()
            if (lyricText.isBlank()) continue
            for (m in matches) {
                val timeMs = toMillis(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                entries.add(timeMs to lyricText)
            }
        }
        entries.sortBy { it.first }

        // Pair duplicate timestamps automatically as original + bilingual translation
        val merged = mutableListOf<Triple<Long, String, String?>>()
        for ((ts, content) in entries) {
            val last = merged.lastOrNull()
            if (last != null && kotlin.math.abs(last.first - ts) <= 60L && last.third == null) {
                merged[merged.lastIndex] = Triple(last.first, last.second, content)
            } else {
                merged.add(Triple(ts, content, null))
            }
        }

        val lines = merged.mapIndexed { idx, (startMs, lineText, translation) ->
            val nextStart = merged.getOrNull(idx + 1)?.first ?: (startMs + 4800L)
            val endMs = max(startMs + 600L, minOf(nextStart, startMs + 12_000L))
            LyricLine(
                startMs = startMs,
                endMs = endMs,
                text = lineText,
                translation = translation
            )
        }

        return LyricDocument(
            format = LyricFormat.LRC,
            lines = lines,
            sourceLabel = sourceLabel,
            rawContent = text,
            globalOffsetMs = offsetMs
        )
    }

    private fun parseTtml(text: String, sourceLabel: String, offsetMs: Long): LyricDocument {
        val lines = mutableListOf<LyricLine>()
        for (pMatch in TTML_P_REGEX.findAll(text)) {
            val pStart = parseClockTime(pMatch.groupValues[1]) ?: continue
            val pEnd = parseClockTime(pMatch.groupValues[2]) ?: (pStart + 4000L)
            val inner = pMatch.groupValues[3]

            val spans = TTML_SPAN_REGEX.findAll(inner).mapNotNull { sm ->
                val sStart = parseClockTime(sm.groupValues[1]) ?: return@mapNotNull null
                val sEnd = parseClockTime(sm.groupValues[2]) ?: (sStart + 250L)
                val wordText = sm.groupValues[3].replace(Regex("<[^>]+>"), "")
                if (wordText.isEmpty()) return@mapNotNull null
                LyricWord(wordText, sStart, max(50L, sEnd - sStart))
            }.toList()

            val plain = if (spans.isNotEmpty()) {
                spans.joinToString("") { it.text }.trim()
            } else {
                inner.replace(Regex("<[^>]+>"), "").trim()
            }
            if (plain.isBlank()) continue

            lines.add(
                LyricLine(
                    startMs = pStart,
                    endMs = max(pStart + 400L, pEnd),
                    text = plain,
                    words = spans,
                    isWordSyncedNative = spans.isNotEmpty()
                )
            )
        }
        return LyricDocument(
            format = LyricFormat.TTML,
            lines = lines.sortedBy { it.startMs },
            sourceLabel = sourceLabel,
            rawContent = text,
            globalOffsetMs = offsetMs
        )
    }

    private fun parseSrt(text: String, sourceLabel: String, offsetMs: Long): LyricDocument {
        val lines = mutableListOf<LyricLine>()
        val blocks = text.replace("\r\n", "\n").split(Regex("\n{2,}"))
        for (block in blocks) {
            val rowList = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val timeIdx = rowList.indexOfFirst { SRT_TIME_REGEX.containsMatchIn(it) }
            if (timeIdx < 0) continue
            val m = SRT_TIME_REGEX.find(rowList[timeIdx]) ?: continue
            val startMs = ((m.groupValues[1].toLong() * 3600 +
                m.groupValues[2].toLong() * 60 +
                m.groupValues[3].toLong()) * 1000L) + m.groupValues[4].toLong()
            val endMs = ((m.groupValues[5].toLong() * 3600 +
                m.groupValues[6].toLong() * 60 +
                m.groupValues[7].toLong()) * 1000L) + m.groupValues[8].toLong()
            val captionLines = rowList.drop(timeIdx + 1)
            if (captionLines.isEmpty()) continue
            lines.add(
                LyricLine(
                    startMs = startMs,
                    endMs = max(startMs + 500L, endMs),
                    text = captionLines.first(),
                    translation = captionLines.getOrNull(1)
                )
            )
        }
        return LyricDocument(
            format = LyricFormat.SRT,
            lines = lines.sortedBy { it.startMs },
            sourceLabel = sourceLabel,
            rawContent = text,
            globalOffsetMs = offsetMs
        )
    }

    private fun parsePlainText(text: String, sourceLabel: String): LyricDocument {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { idx, s ->
                LyricLine(startMs = idx * 4000L, endMs = (idx + 1) * 4000L, text = s)
            }
        return LyricDocument(
            format = LyricFormat.PLAIN,
            lines = lines,
            sourceLabel = sourceLabel,
            rawContent = text
        )
    }

    private fun toMillis(minStr: String, secStr: String, fracStr: String): Long {
        val minutes = minStr.toLongOrNull() ?: 0L
        val seconds = secStr.toLongOrNull() ?: 0L
        val millis = when (fracStr.length) {
            0 -> 0L
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.take(3).toLongOrNull() ?: 0L
        }
        return (minutes * 60L + seconds) * 1000L + millis
    }

    private fun parseClockTime(raw: String): Long? {
        val cleaned = raw.trim()
        if (cleaned.endsWith("s", ignoreCase = true)) {
            return (cleaned.dropLast(1).toDoubleOrNull()?.times(1000.0))?.roundToLong()
        }
        val parts = cleaned.split(":")
        return when (parts.size) {
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val s = parts[1].toDoubleOrNull() ?: return null
                (m * 60_000L) + (s * 1000.0).roundToLong()
            }
            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toDoubleOrNull() ?: return null
                (h * 3_600_000L) + (m * 60_000L) + (s * 1000.0).roundToLong()
            }
            else -> cleaned.toDoubleOrNull()?.times(1000.0)?.roundToLong()
        }
    }
}

/**
 * Intelligently synthesizes smooth, musically rhythmic word/character-level timings
 * for line-synced LRC/SRT lyrics when "Simulated Word-by-Word" mode is enabled.
 */
object SimulatedKaraokeSynthesizer {

    private val TOKEN_REGEX = Regex("""([\u4E00-\u9FFF\u3040-\u30FF\uAC00-\uD7AF]|[A-Za-z0-9'’\-]+|[^\s])(\s*)""")

    fun synthesize(doc: LyricDocument): LyricDocument {
        val enriched = doc.lines.map { line ->
            if (line.words.isNotEmpty() || line.text.isBlank()) {
                line
            } else {
                val tokens = TOKEN_REGEX.findAll(line.text).map { it.value }.toList()
                if (tokens.isEmpty()) {
                    line
                } else {
                    val weights = tokens.map { token ->
                        val core = token.trim()
                        when {
                            core.isEmpty() -> 0.4
                            core.length == 1 && isPunctuation(core[0]) -> 0.55
                            core.length == 1 && isCjk(core[0]) -> 1.35
                            else -> (0.85 + core.length * 0.22).coerceAtMost(2.8)
                        }
                    }
                    val totalWeight = weights.sum().coerceAtLeast(1.0)
                    // Leave a natural ~12% breath tail before next line so sweeps feel vocal, not robotic
                    val rawDuration = max(500L, line.endMs - line.startMs)
                    val activeSpan = (rawDuration * 0.88).roundToLong().coerceAtLeast(420L)

                    var cursor = line.startMs.toDouble()
                    val words = tokens.mapIndexed { idx, token ->
                        val dur = (activeSpan * (weights[idx] / totalWeight)).coerceAtLeast(65.0)
                        val word = LyricWord(
                            text = token,
                            startMs = cursor.roundToLong(),
                            durationMs = dur.roundToLong()
                        )
                        cursor += dur
                        word
                    }
                    line.copy(words = words, isWordSyncedNative = false)
                }
            }
        }
        return doc.copy(lines = enriched, isSimulatedWordSync = true)
    }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x4E00..0x9FFF) || (code in 0x3040..0x30FF) || (code in 0xAC00..0xD7AF)
    }

    private fun isPunctuation(ch: Char): Boolean {
        return ch in ",.?!;:'\"()[]{}，。？！；：、（）《》—"
    }
}
