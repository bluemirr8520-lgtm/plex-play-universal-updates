package io.mirr.plexplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalSubtitleTypographyTest {
    @Test
    fun singleEnglishLetterRemainsUpright() {
        val glyph = "A".toVerticalSubtitleGlyphs().single()

        assertEquals("A", glyph.text)
        assertFalse(glyph.rotate)
        assertFalse(glyph.measureRotatedTextAdvance)
    }

    @Test
    fun consecutiveEnglishLettersRotateClockwiseAsOneRun() {
        val glyph = "Plex".toVerticalSubtitleGlyphs().single()

        assertEquals("Plex", glyph.text)
        assertTrue(glyph.rotate)
        assertTrue(glyph.centerInCell)
        assertTrue(glyph.measureRotatedTextAdvance)
        assertEquals(90f, VerticalSubtitleRightRotationDegrees, 0f)
    }

    @Test
    fun koreanAndSingleEnglishLettersKeepTheirOrientation() {
        val glyphs = "한AB-글C".toVerticalSubtitleGlyphs()

        assertEquals(listOf("한", "AB", "︲", "글", "C"), glyphs.map { it.text })
        assertEquals(listOf(false, true, false, false, false), glyphs.map { it.rotate })
    }

    @Test
    fun punctuationSeparatesRotatedEnglishRuns() {
        val glyphs = "AB-CD".toVerticalSubtitleGlyphs()

        assertEquals(listOf("AB", "︲", "CD"), glyphs.map { it.text })
        assertEquals(listOf(true, false, true), glyphs.map { it.rotate })
    }
}
