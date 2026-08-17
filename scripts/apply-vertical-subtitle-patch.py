#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

RIGHT_ROTATION_DEGREES = 90.0

DATA_CLASS_OLD = """private data class VerticalSubtitleGlyph(
    val text: String,
    val rotate: Boolean = false,
    val spacer: Boolean = false,
    val advanceScale: Float = 1f,
    val centerInCell: Boolean = false,
)"""

DATA_CLASS_NEW = """internal data class VerticalSubtitleGlyph(
    val text: String,
    val rotate: Boolean = false,
    val spacer: Boolean = false,
    val advanceScale: Float = 1f,
    val centerInCell: Boolean = false,
    val measureRotatedTextAdvance: Boolean = false,
)

internal const val VerticalSubtitleRightRotationDegrees = 90f"""

MEASURE_OLD = """        val maxColumnAdvance = maxVerticalColumnAdvance(availableHeight)
        val columns = sourceColumns.wrapVerticalSubtitleColumns(maxColumnAdvance)"""

MEASURE_NEW = """        val maxColumnAdvance = maxVerticalColumnAdvance(availableHeight)
        val measuredSourceColumns = sourceColumns.map { glyphs ->
            glyphs.map { glyph ->
                if (!glyph.measureRotatedTextAdvance) {
                    glyph
                } else {
                    glyph.copy(
                        advanceScale = (
                            (fillPaint.measureText(glyph.text) + outlineStrokeWidth * 2f) /
                                glyphAdvancePx
                            ).coerceAtLeast(1f),
                    )
                }
            }
        }
        val columns = measuredSourceColumns.wrapVerticalSubtitleColumns(maxColumnAdvance)"""

CENTER_OLD = """        val cellCenterY = baseline + (fillPaint.ascent() + fillPaint.descent()) / 2f"""

CENTER_NEW = """        val baseCellCenterY = baseline + (fillPaint.ascent() + fillPaint.descent()) / 2f
        val cellCenterY = if (glyph.measureRotatedTextAdvance) {
            baseCellCenterY + glyphAdvancePx * (glyph.advanceScale - 1f) / 2f
        } else {
            baseCellCenterY
        }"""

ENGLISH_BLOCK = """        if (isAsciiEnglishLetter(codePoint)) {
            val start = index
            while (index < codePoints.size && isAsciiEnglishLetter(codePoints[index])) {
                index++
            }
            val englishRun = codePoints.copyOfRange(start, index).toCodePointString()
            glyphs += if (index - start == 1) {
                VerticalSubtitleGlyph(englishRun)
            } else {
                VerticalSubtitleGlyph(
                    text = englishRun,
                    rotate = true,
                    centerInCell = true,
                    measureRotatedTextAdvance = true,
                )
            }
            continue
        }
"""

HELPER_BLOCK = """private fun isAsciiEnglishLetter(codePoint: Int): Boolean =
    codePoint in 'A'.code..'Z'.code || codePoint in 'a'.code..'z'.code

"""

TEST_SOURCE = """package io.mirr.plexplay.ui

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
"""


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one source match, found {count}")
    return text.replace(old, new, 1)


def patch_project(project_root: Path) -> None:
    player = project_root / "app/src/main/java/io/mirr/plexplay/ui/PlayerScreen.kt"
    text = player.read_text(encoding="utf-8")
    text = replace_once(text, DATA_CLASS_OLD, DATA_CLASS_NEW, "glyph model")
    text = replace_once(text, MEASURE_OLD, MEASURE_NEW, "measured advance")
    text = replace_once(text, CENTER_OLD, CENTER_NEW, "rotated center")
    text = replace_once(
        text,
        "            canvas.rotate(90f, x, cellCenterY)",
        "            canvas.rotate(VerticalSubtitleRightRotationDegrees, x, cellCenterY)",
        "clockwise rotation",
    )
    text = replace_once(
        text,
        "private fun String.toVerticalSubtitleGlyphs(): List<VerticalSubtitleGlyph> {",
        "internal fun String.toVerticalSubtitleGlyphs(): List<VerticalSubtitleGlyph> {",
        "tokenizer visibility",
    )
    if ENGLISH_BLOCK not in text:
        marker = "        if (isEllipsisDot(codePoint)) {\n"
        if text.count(marker) != 1:
            raise RuntimeError(
                f"English run insertion: expected one marker, found {text.count(marker)}"
            )
        text = text.replace(marker, ENGLISH_BLOCK + marker, 1)
    if HELPER_BLOCK not in text:
        marker = "private fun List<VerticalSubtitleGlyph>.verticalAdvance(): Float =\n"
        if text.count(marker) != 1:
            raise RuntimeError(
                f"ASCII helper insertion: expected one marker, found {text.count(marker)}"
            )
        text = text.replace(marker, HELPER_BLOCK + marker, 1)
    player.write_text(text, encoding="utf-8", newline="\n")

    test = (
        project_root
        / "app/src/test/java/io/mirr/plexplay/ui/VerticalSubtitleTypographyTest.kt"
    )
    test.parent.mkdir(parents=True, exist_ok=True)
    test.write_text(TEST_SOURCE, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("usage: apply-vertical-subtitle-patch.py PROJECT_ROOT [...]")
    for argument in sys.argv[1:]:
        patch_project(Path(argument).resolve())
