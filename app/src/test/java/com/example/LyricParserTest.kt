package com.example

import com.example.data.lyrics.LyricParser
import com.example.model.LyricFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricParserTest {

    @Test
    fun parsesQrcXmlWordByWordCorrectly() {
        val qrc = """
            <?xml version="1.0" encoding="utf-8"?>
            <QrcInfos>
                <Lyric_1 LyricType="1" LyricContent="[offset:0]
[1000,2500]夜(1000,400)空(1400,500)中(1900,400)最(2300,500)亮(2800,700)
[4000,2000]的(4000,500)星(4500,1500)"/>
            </QrcInfos>
        """.trimIndent()

        val doc = LyricParser.parse(qrc, sourceLabel = "test.qrc", fileNameHint = "song.qrc")
        assertEquals(LyricFormat.QRC, doc.format)
        assertEquals(2, doc.lines.size)
        assertEquals("夜空中最亮", doc.lines[0].text)
        assertEquals(5, doc.lines[0].words.size)
        assertTrue(doc.lines[0].isWordSyncedNative)
        assertFalse(doc.isSimulatedWordSync)
    }

    @Test
    fun parsesYrcWordByWordCorrectly() {
        val yrc = "[1200,1800](1200,450,0)Hello (1650,600,0)World"
        val doc = LyricParser.parse(yrc, fileNameHint = "track.yrc")
        assertEquals(LyricFormat.YRC, doc.format)
        assertEquals(1, doc.lines.size)
        assertEquals(2, doc.lines[0].words.size)
        assertEquals("Hello World", doc.lines[0].text)
    }

    @Test
    fun parsesStandardLrcWithBilingualPairingAndSimulatedWordSync() {
        val lrc = """
            [00:10.00]Midnight rain falls softly
            [00:10.00]午夜细雨轻落
            [00:15.00]Across the glass
        """.trimIndent()

        val simulatedDoc = LyricParser.parse(lrc, enableSimulatedWordByWord = true)
        assertEquals(LyricFormat.LRC, simulatedDoc.format)
        assertEquals(2, simulatedDoc.lines.size)
        assertEquals("午夜细雨轻落", simulatedDoc.lines[0].translation)
        assertTrue(simulatedDoc.isSimulatedWordSync)
        assertTrue(simulatedDoc.lines[0].words.isNotEmpty())

        val lineOnlyDoc = LyricParser.parse(lrc, enableSimulatedWordByWord = false)
        assertFalse(lineOnlyDoc.isSimulatedWordSync)
        assertTrue(lineOnlyDoc.lines[0].words.isEmpty())
    }

    @Test
    fun parsesTtmlWordSpansCorrectly() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:01.000" end="00:04.000">
                  <span begin="00:01.000" end="00:02.000">Golden </span>
                  <span begin="00:02.000" end="00:03.800">Hour</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val doc = LyricParser.parse(ttml, fileNameHint = "apple.ttml")
        assertEquals(LyricFormat.TTML, doc.format)
        assertEquals(1, doc.lines.size)
        assertEquals(2, doc.lines[0].words.size)
        assertEquals("Golden Hour", doc.lines[0].text)
    }
}
