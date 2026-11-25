package com.example

import com.example.data.ai.DeepSeekChatClient
import com.example.data.scanner.MetadataExtractor
import com.example.model.AI_MAX_TOKEN_PRESETS
import com.example.model.AiAttachmentBadge
import com.example.model.AiAttachmentBadgeCodec
import com.example.model.AiAttachmentBadgeType
import com.example.model.AiConfig
import com.example.model.AiErrorReport
import com.example.model.DEFAULT_AI_MAX_OUTPUT_TOKENS
import com.example.model.EQ_BAND_COUNT
import com.example.model.LyricSettings
import com.example.model.LyricSource
import com.example.model.ReasoningEffortLevel
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.model.formatTokenCountLabel
import com.example.model.lyricFontScaleLabel
import com.example.model.normalizeEqBands
import com.example.model.parseTokenCountInput
import com.example.model.snapLyricFontScale
import com.example.playback.PlaybackState
import com.example.ui.components.buildArtistAutoPlaylists
import com.example.ui.components.extractArtistNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun reasoningEffortDefaultsAndExtendedTiersAreAccurate() {
        assertEquals(
            setOf(
                ReasoningEffortLevel.OFF,
                ReasoningEffortLevel.LOW,
                ReasoningEffortLevel.HIGH,
                ReasoningEffortLevel.MAX
            ),
            ReasoningEffortLevel.defaultEnabledSet
        )
        assertEquals(
            listOf(
                ReasoningEffortLevel.MEDIUM,
                ReasoningEffortLevel.XHIGH,
                ReasoningEffortLevel.ULTRA
            ),
            ReasoningEffortLevel.optionalExtendedLevels
        )
    }

    @Test
    fun errorReportBuildsStructuredFeedbackPromptForModel() {
        val report = AiErrorReport(
            summary = "HTTP 400 · invalid reasoning_effort",
            httpStatusCode = 400,
            endpoint = "https://api.deepseek.com/chat/completions",
            modelId = "deepseek-flash",
            reasoningEffort = "ultra",
            rawDetail = """{"error":{"message":"unsupported reasoning_effort value"}}"""
        )
        val prompt = report.buildModelFeedbackPrompt()
        assertTrue(prompt.contains("400"))
        assertTrue(prompt.contains("deepseek-flash"))
        assertTrue(prompt.contains("ultra"))
        assertTrue(prompt.contains("unsupported reasoning_effort value"))
    }

    @Test
    fun lyricSettingsAndSourcesSupportFontWeightAndChineseProviders() {
        val settings = LyricSettings(
            fontWeight = 500,
            activeOnlineSource = LyricSource.QQ_MUSIC,
            enableLyricBlur = true,
            enableImmersiveLyrics = true
        )
        assertTrue(settings.fontWeightLabel.contains("500"))
        assertTrue(settings.enableLyricBlur)
        assertTrue(settings.enableImmersiveLyrics)
        assertEquals(
            listOf(LyricSource.NETEASE, LyricSource.QQ_MUSIC, LyricSource.KUGOU, LyricSource.LRCLIB),
            LyricSource.selectableProviders
        )
    }

    @Test
    fun normalizeEqBandsInterpolatesFiveBandToTenBandSmoothly() {
        val legacyFive = listOf(4f, 2f, 0f, -2f, 4f)
        val ten = normalizeEqBands(legacyFive)
        assertEquals(EQ_BAND_COUNT, ten.size)
        assertEquals(4f, ten.first(), 0.01f)
        assertEquals(4f, ten.last(), 0.01f)
        val extreme = normalizeEqBands(listOf(25f, -20f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 15f))
        assertEquals(12f, extreme.first(), 0.01f)
        assertEquals(-12f, extreme[1], 0.01f)
        assertEquals(12f, extreme.last(), 0.01f)
    }

    @Test
    fun lyricFontScaleSnapsToDiscreteTiersCleanly() {
        assertEquals(0.85f, snapLyricFontScale(0.82f), 0.001f)
        assertEquals(1.00f, snapLyricFontScale(1.02f), 0.001f)
        assertEquals(1.20f, snapLyricFontScale(1.19f), 0.001f)
        assertEquals(1.35f, snapLyricFontScale(1.50f), 0.001f)
        assertEquals("标准 (100%)", lyricFontScaleLabel(1.00f))
        assertEquals("超大 (135%)", lyricFontScaleLabel(1.35f))
    }

    @Test
    fun audioClassificationSeparatesVoiceMemoFromTaggedMusic() {
        assertTrue(
            MetadataExtractor.classifyAudioAsGeneral(
                rawTitleTag = null,
                artist = "未知艺术家",
                album = "未知专辑",
                artworkPath = null,
                genre = "",
                year = 0,
                isLossless = false,
                isMusicFlag = false
            )
        )
        assertFalse(
            MetadataExtractor.classifyAudioAsGeneral(
                rawTitleTag = "暖暖",
                artist = "梁静茹",
                album = "亲亲",
                artworkPath = "/cache/cover.jpg",
                genre = "Pop",
                year = 2006,
                isLossless = true,
                isMusicFlag = true
            )
        )
    }

    @Test
    fun playbackStateResolvesPreviousAndNextTracksCorrectly() {
        fun sampleTrack(id: String, title: String) = Track(
            id = id,
            contentUri = "content://media/$id",
            title = title,
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            sizeBytes = 5_000_000L,
            folderPath = "Music",
            fileName = "$title.flac",
            mimeType = "audio/flac",
            codecLabel = "FLAC"
        )
        val t1 = sampleTrack("1", "Song 1")
        val t2 = sampleTrack("2", "Song 2")
        val t3 = sampleTrack("3", "Song 3")

        val midState = PlaybackState(queue = listOf(t1, t2, t3), currentIndex = 1, repeatMode = RepeatMode.OFF)
        assertEquals("Song 1", midState.previousTrack?.title)
        assertEquals("Song 3", midState.nextTrack?.title)

        val firstState = PlaybackState(queue = listOf(t1, t2, t3), currentIndex = 0, repeatMode = RepeatMode.ALL)
        assertEquals("Song 3", firstState.previousTrack?.title)

        val endState = PlaybackState(queue = listOf(t1, t2, t3), currentIndex = 2, repeatMode = RepeatMode.ALL)
        assertEquals("Song 1", endState.nextTrack?.title)

        val emptyState = PlaybackState(queue = emptyList(), currentIndex = -1)
        assertNull(emptyState.previousTrack)
        assertNull(emptyState.nextTrack)
    }

    @Test
    fun deduplicatorNormalizesTitlesAndGuardsDistinctVersions() {
        assertEquals(
            com.example.data.scanner.TrackDeduplicator.normalizeTitle("暖暖 (Hi-Res).flac"),
            com.example.data.scanner.TrackDeduplicator.normalizeTitle("暖暖 [无损] - 副本")
        )
        assertEquals(
            "",
            com.example.data.scanner.TrackDeduplicator.extractVersionMarker("暖暖 (Hi-Res)")
        )
        assertEquals(
            "live",
            com.example.data.scanner.TrackDeduplicator.extractVersionMarker("暖暖 (Live)")
        )
        assertEquals(
            "伴奏",
            com.example.data.scanner.TrackDeduplicator.extractVersionMarker("暖暖 (伴奏)")
        )
    }

    @Test
    fun deduplicatorAutoSkipsEquivalentDuplicatesAndSurfacesQualityConflicts() {
        fun track(
            id: String,
            title: String = "暖暖",
            artist: String = "梁静茹",
            codec: String = "MP3",
            isLossless: Boolean = false,
            isHiRes: Boolean = false,
            bitrateKbps: Int = 128,
            sampleRateHz: Int = 44100,
            bitDepth: Int = 16,
            sizeBytes: Long = 3_800_000L
        ) = Track(
            id = id,
            contentUri = "content://media/$id",
            title = title,
            artist = artist,
            album = "亲亲",
            durationMs = 243_000L,
            sizeBytes = sizeBytes,
            folderPath = "Music",
            fileName = "$title.$codec",
            mimeType = if (isLossless) "audio/flac" else "audio/mpeg",
            codecLabel = codec,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            isLossless = isLossless,
            isHiRes = isHiRes
        )

        val existingMp3 = track(id = "ex_mp3", bitrateKbps = 128)
        val sameQualityCopy = track(id = "in_mp3_copy", bitrateKbps = 128)
        val hiResFlac = track(
            id = "in_flac_hires",
            title = "暖暖 (Hi-Res)",
            codec = "FLAC",
            isLossless = true,
            isHiRes = true,
            bitrateKbps = 2800,
            sampleRateHz = 96000,
            bitDepth = 24,
            sizeBytes = 68_000_000L
        )
        val liveVersion = track(id = "in_live", title = "暖暖 (Live)", codec = "FLAC", isLossless = true, bitrateKbps = 980)

        // 1. Equivalent quality duplicate -> auto-skipped
        val skipAnalysis = com.example.data.scanner.TrackDeduplicator.analyzeImportCandidates(
            incoming = listOf(sameQualityCopy),
            existing = listOf(existingMp3)
        )
        assertEquals(0, skipAnalysis.newTracks.size)
        assertEquals(1, skipAnalysis.autoSkippedCount)
        assertEquals(0, skipAnalysis.qualityConflicts.size)

        // 2. Different quality -> quality conflict with Hi-Res FLAC recommended; Live version -> treated as distinct new track
        val conflictAnalysis = com.example.data.scanner.TrackDeduplicator.analyzeImportCandidates(
            incoming = listOf(hiResFlac, liveVersion),
            existing = listOf(existingMp3)
        )
        assertEquals(1, conflictAnalysis.newTracks.size)
        assertEquals("in_live", conflictAnalysis.newTracks.first().id)
        assertEquals(1, conflictAnalysis.qualityConflicts.size)
        val conflict = conflictAnalysis.qualityConflicts.first()
        assertTrue(conflict.recommendedIncoming)
        assertEquals("in_flac_hires", conflict.bestTrack.id)

        // 3. Library duplicate clustering sorts highest quality first
        val groups = com.example.data.scanner.TrackDeduplicator.detectLibraryDuplicates(
            listOf(existingMp3, sameQualityCopy, hiResFlac, liveVersion)
        )
        assertEquals(1, groups.size)
        assertEquals(3, groups.first().tracks.size)
        assertEquals("in_flac_hires", groups.first().bestTrack.id)
        assertTrue(groups.first().hasQualityDifference)
    }

    @Test
    fun maxOutputTokensDefaultsTo384KAndParsesPresetsAndCustomValues() {
        assertEquals(393_216, DEFAULT_AI_MAX_OUTPUT_TOKENS)
        assertEquals(393_216, AiConfig().maxOutputTokens)
        assertEquals(listOf("64K", "128K", "256K", "384K"), AI_MAX_TOKEN_PRESETS.map { it.second })
        assertEquals("384K", formatTokenCountLabel(393_216))
        assertEquals("64K", formatTokenCountLabel(65_536))
        assertEquals(65_536, parseTokenCountInput("64K"))
        assertEquals(131_072, parseTokenCountInput("128k"))
        assertEquals(393_216, parseTokenCountInput("384K"))
        assertEquals(8192, parseTokenCountInput("8192"))
        assertNull(parseTokenCountInput("abc"))
    }

    @Test
    fun attachmentBadgeCodecRoundTripsAndSupportsLegacyLabels() {
        val encoded = AiAttachmentBadgeCodec.encode(
            trackTitle = "流明 (Lumen)",
            catalogCount = 12,
            lyricCount = 2,
            usedVision = true,
            usedWebSearch = true
        )
        val decoded = AiAttachmentBadgeCodec.decode(encoded, usedVisionFallback = false)
        assertEquals(5, decoded.size)
        assertEquals(AiAttachmentBadgeType.TRACK, decoded[0].type)
        assertEquals("正在播放 · 流明 (Lumen)", decoded[0].label)
        assertEquals(AiAttachmentBadgeType.CATALOG, decoded[1].type)
        assertEquals(AiAttachmentBadgeType.LYRICS, decoded[2].type)
        assertEquals(AiAttachmentBadgeType.VISION, decoded[3].type)
        assertEquals(AiAttachmentBadgeType.WEB_SEARCH, decoded[4].type)

        val legacy = AiAttachmentBadgeCodec.decode("暖暖 · 梁静茹", usedVisionFallback = false)
        assertEquals(1, legacy.size)
        assertEquals(AiAttachmentBadgeType.TRACK, legacy.first().type)
        assertEquals("暖暖 · 梁静茹", legacy.first().label)
    }

    @Test
    fun artistAutoPlaylistsSplitsCollaborationsAndCountsAccurately() {
        fun makeTrack(id: String, title: String, artist: String, lossless: Boolean = true) = Track(
            id = id,
            contentUri = "content://media/$id",
            title = title,
            artist = artist,
            album = "Album $id",
            durationMs = 200_000L,
            sizeBytes = 10_000_000L,
            folderPath = "Music",
            fileName = "$title.flac",
            mimeType = "audio/flac",
            codecLabel = "FLAC",
            isLossless = lossless
        )

        assertEquals(listOf("扇宝", "星瞳"), extractArtistNames("扇宝/星瞳"))
        assertEquals(listOf("周杰伦", "费玉清"), extractArtistNames("周杰伦、费玉清"))

        val tracks = listOf(
            makeTrack("1", "流明", "星瞳"),
            makeTrack("2", "合唱曲", "扇宝/星瞳"),
            makeTrack("3", "独唱曲", "扇宝", lossless = false)
        )
        val autoPlaylists = buildArtistAutoPlaylists(tracks)
        assertEquals(2, autoPlaylists.size)
        val xingtong = autoPlaylists.first { it.artistName == "星瞳" }
        val shanbao = autoPlaylists.first { it.artistName == "扇宝" }
        assertEquals(2, xingtong.tracks.size)
        assertEquals(2, xingtong.losslessCount)
        assertEquals(2, shanbao.tracks.size)
        assertEquals(1, shanbao.losslessCount)
    }

    @Test
    fun systemPromptDeduplicatesPlayingTrackLyricsWhenPresentInSelectedCatalog() {
        val currentTrack = Track(
            id = "track_lumen",
            contentUri = "content://media/lumen",
            title = "流明 (Lumen)",
            artist = "星瞳",
            album = "星·征途",
            durationMs = 215_000L,
            sizeBytes = 25_000_000L,
            folderPath = "Music",
            fileName = "lumen.flac",
            mimeType = "audio/flac",
            codecLabel = "FLAC",
            isLossless = true
        )
        val otherTrack = currentTrack.copy(id = "track_other", title = "屏中人")
        val uniqueLyricLine = "UNIQUE_LYRIC_MARKER_LUMEN_CHORUS"
        val prompt = DeepSeekChatClient().buildSystemPrompt(
            config = AiConfig(enableWebSearch = true),
            attachTrackContext = true,
            track = currentTrack,
            lyricsSnippet = uniqueLyricLine,
            attachLibraryCatalog = true,
            librarySample = listOf(currentTrack, otherTrack),
            catalogLyricsByTrackId = mapOf(currentTrack.id to uniqueLyricLine)
        )
        val occurrences = Regex(uniqueLyricLine).findAll(prompt).count()
        assertEquals(1, occurrences)
        assertTrue(prompt.contains("【当前正在播放】"))
        assertTrue(prompt.contains("deepseek_web_search"))
    }

    @Test
    fun markdownParserHandlesGfmTablesIncludingBrokenTrailingPipes() {
        val md = """
            一首都踩在这条线的关键节点上：
            |时间|事件|
            |---|---|
            |2017.12.01|在QQ炫舞手游预热的《舞动人》MMD视频中初次登场|
            |2018.05.26|以陈伟霆“小师妹”身份出道，首唱《Shining You》
            |
            |2019.11.08|发行正式出道单曲《YO!》
            |
            |2025.11.07|单曲《Paradise》发行|
        """.trimIndent()

        val blocks = com.example.ui.components.parseMarkdownBlocks(md)
        val table = blocks.filterIsInstance<com.example.ui.components.MarkdownBlock.Table>().single()
        assertEquals(listOf("时间", "事件"), table.headers)
        assertEquals(4, table.rows.size)
        assertEquals("2017.12.01", table.rows[0][0])
        assertEquals("2018.05.26", table.rows[1][0])
        assertTrue(table.rows[1][1].contains("Shining You"))
        assertEquals("2019.11.08", table.rows[2][0])
        assertEquals("2025.11.07", table.rows[3][0])
    }

    @Test
    fun markdownParserConvertsToolTracesAndReferenceLinksWithoutEmojis() {
        val md = """
            > [[SEARCH]] **联网搜索**：星瞳 虚拟偶像 专辑
            
            > 🔍 **联网搜索**：https://baike.baidu.com/item/%E6%98%9F%E7%9E%B3/55966162
            
            > [[RESULT]] **已检索到 42 条网页资料**（星瞳百度百科）
            
            ### 参考来源
            - [星瞳 - 百度百科](https://baike.baidu.com/item/xingtong)
        """.trimIndent()

        val blocks = com.example.ui.components.parseMarkdownBlocks(md)
        val traces = blocks.filterIsInstance<com.example.ui.components.MarkdownBlock.ToolTrace>()
        assertEquals(3, traces.size)
        assertEquals(com.example.ui.components.ToolTraceKind.SEARCH, traces[0].kind)
        assertEquals("联网搜索", traces[0].label)
        assertEquals(com.example.ui.components.ToolTraceKind.FETCH, traces[1].kind)
        assertEquals("读取网页", traces[1].label)
        assertTrue(traces[1].detail.contains("星瞳"))
        assertEquals(com.example.ui.components.ToolTraceKind.RESULT, traces[2].kind)

        val refLinks = blocks.filterIsInstance<com.example.ui.components.MarkdownBlock.ReferenceLink>()
        assertEquals(1, refLinks.size)
        assertEquals("星瞳 - 百度百科", refLinks[0].title)
        assertEquals("https://baike.baidu.com/item/xingtong", refLinks[0].url)
    }

    @Test
    fun playlistCoverResolverHandlesAutoSingleCustomAndSeededRandomModes() {
        fun trackWithArt(id: String, art: String?) = Track(
            id = id,
            contentUri = "content://media/$id",
            title = "Song $id",
            artist = "星瞳",
            album = "Album $id",
            durationMs = 210_000L,
            sizeBytes = 12_000_000L,
            folderPath = "Music",
            fileName = "$id.flac",
            mimeType = "audio/flac",
            codecLabel = "FLAC",
            artworkPath = art
        )

        val tracks = listOf(
            trackWithArt("t1", "/art/1.jpg"),
            trackWithArt("t2", "/art/2.jpg"),
            trackWithArt("t3", "/art/3.jpg"),
            trackWithArt("t4", "/art/4.jpg"),
            trackWithArt("t5", "/art/5.jpg"),
            trackWithArt("t6", null)
        )

        // 1. Default Auto Collage takes first 4 distinct artworks
        val auto = com.example.ui.components.resolvePlaylistCover(tracks, null)
        assertFalse(auto.renderAsSingle)
        assertEquals(listOf("t1", "t2", "t3", "t4"), auto.slots.map { it.trackId })

        // 2. Single Track mode forces full-bleed single cover for chosen track
        val single = com.example.ui.components.resolvePlaylistCover(
            tracks,
            com.example.model.PlaylistCoverConfig(
                mode = com.example.model.PlaylistCoverMode.SINGLE_TRACK,
                trackIds = listOf("t5")
            )
        )
        assertTrue(single.renderAsSingle)
        assertEquals(listOf("t5"), single.slots.map { it.trackId })

        // 3. Custom Collage preserves exact user order across 4 chosen songs
        val custom = com.example.ui.components.resolvePlaylistCover(
            tracks,
            com.example.model.PlaylistCoverConfig(
                mode = com.example.model.PlaylistCoverMode.CUSTOM_COLLAGE,
                trackIds = listOf("t5", "t3", "t1", "t4")
            )
        )
        assertFalse(custom.renderAsSingle)
        assertEquals(listOf("t5", "t3", "t1", "t4"), custom.slots.map { it.trackId })

        // 4. Random Collage is deterministic for the same seed and picks 4 tracks with art
        val randA = com.example.ui.components.resolvePlaylistCover(
            tracks,
            com.example.model.PlaylistCoverConfig(
                mode = com.example.model.PlaylistCoverMode.RANDOM_COLLAGE,
                randomSeed = 42L
            )
        )
        val randASame = com.example.ui.components.resolvePlaylistCover(
            tracks,
            com.example.model.PlaylistCoverConfig(
                mode = com.example.model.PlaylistCoverMode.RANDOM_COLLAGE,
                randomSeed = 42L
            )
        )
        assertFalse(randA.renderAsSingle)
        assertEquals(4, randA.slots.size)
        assertEquals(randA.slots.map { it.trackId }, randASame.slots.map { it.trackId })
        assertTrue(randA.slots.none { it.trackId == "t6" })
    }

    @Test
    fun appVersionInfoTracksSemanticVersionFromBaseline090To091() {
        assertEquals("0.9.1", com.example.model.AppVersionInfo.CURRENT_VERSION)
        assertEquals("0.9.0", com.example.model.AppVersionInfo.PREVIOUS_VERSION)
        assertTrue(com.example.model.AppVersionInfo.RELEASE_NOTES.size >= 2)
        assertEquals("0.9.1", com.example.model.AppVersionInfo.RELEASE_NOTES.first().version)
        assertEquals("0.9.0", com.example.model.AppVersionInfo.RELEASE_NOTES[1].version)
    }
}

