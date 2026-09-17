package com.epubreader.app.ui.reader

import com.epubreader.app.data.PrefsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Phase 6 extracted [ReaderCssBuilder].
 *
 * Verifies that the CSS generation produces correct output for various
 * reader settings, and that the dark-text override logic matches the
 * original ReaderActivity behavior.
 */
class ReaderCssBuilderTest {

    @Test
    fun `build produces valid CSS with style tag`() {
        val css = ReaderCssBuilder.build(
            bgColorInt = 0xFFFFFFFF.toInt(),
            inkHex = "#000000",
            themeId = "ivory",
            font = PrefsManager.Font.SERIF,
            fontSize = 24,
            lineHeight = 1.6f,
            margin = 20,
            align = PrefsManager.Align.LEFT,
            hyphenation = false,
        )
        assertTrue("CSS should start with <style>", css.startsWith("<style>"))
        assertTrue("CSS should contain background color", css.contains("background:#FFFFFF"))
        assertTrue("CSS should contain ink color", css.contains("color:#000000"))
        assertTrue("CSS should contain font-size", css.contains("font-size:24px"))
        assertTrue("CSS should contain line-height", css.contains("line-height:1.6"))
        assertTrue("CSS should contain margin padding", css.contains("padding:0 20px"))
        assertTrue("CSS should contain column-gap", css.contains("column-gap:40px"))
    }

    @Test
    fun `build with publisher font omits font-family override`() {
        val css = ReaderCssBuilder.build(
            bgColorInt = 0xFFFFFFFF.toInt(),
            inkHex = "#000000",
            themeId = "ivory",
            font = PrefsManager.Font.PUBLISHER,
            fontSize = 24,
            lineHeight = 1.6f,
            margin = 20,
            align = PrefsManager.Align.ORIGINAL,
            hyphenation = false,
        )
        assertFalse("Publisher font should not emit font-family", css.contains("font-family:"))
    }

    @Test
    fun `build with justify alignment includes text-align justify`() {
        val css = ReaderCssBuilder.build(
            bgColorInt = 0xFFFFFFFF.toInt(),
            inkHex = "#000000",
            themeId = "ivory",
            font = PrefsManager.Font.SERIF,
            fontSize = 24,
            lineHeight = 1.6f,
            margin = 20,
            align = PrefsManager.Align.JUSTIFY,
            hyphenation = false,
        )
        assertTrue("Justify alignment should be present", css.contains("text-align:justify"))
    }

    @Test
    fun `build with original alignment omits text-align override`() {
        val css = ReaderCssBuilder.build(
            bgColorInt = 0xFFFFFFFF.toInt(),
            inkHex = "#000000",
            themeId = "ivory",
            font = PrefsManager.Font.SERIF,
            fontSize = 24,
            lineHeight = 1.6f,
            margin = 20,
            align = PrefsManager.Align.ORIGINAL,
            hyphenation = false,
        )
        assertFalse("Original alignment should not override", css.contains("text-align:"))
    }

    @Test
    fun `build with hyphenation includes auto hyphens`() {
        val css = ReaderCssBuilder.build(
            bgColorInt = 0xFFFFFFFF.toInt(),
            inkHex = "#000000",
            themeId = "ivory",
            font = PrefsManager.Font.SERIF,
            fontSize = 24,
            lineHeight = 1.6f,
            margin = 20,
            align = PrefsManager.Align.LEFT,
            hyphenation = true,
        )
        assertTrue("Hyphenation auto should be present", css.contains("hyphens:auto"))
    }

    @Test
    fun `darkTextOverride returns empty for ivory theme`() {
        val override = ReaderCssBuilder.darkTextOverride("ivory", "#000000")
        assertEquals("Ivory theme should not need ink override", "", override)
    }

    @Test
    fun `darkTextOverride returns CSS for onyx theme`() {
        val override = ReaderCssBuilder.darkTextOverride("onyx", "#FFFFFF")
        assertTrue("Onyx theme should have ink override", override.contains("<style>"))
        assertTrue("Override should force ink color", override.contains("color:#FFFFFF"))
        assertTrue("Override should flatten backgrounds", override.contains("background-color: transparent"))
    }

    @Test
    fun `darkTextOverride returns CSS for alabaster theme`() {
        val override = ReaderCssBuilder.darkTextOverride("alabaster", "#5A4650")
        assertTrue("Alabaster theme should have ink override", override.contains("<style>"))
    }

    @Test
    fun `colorToHex converts opaque color correctly`() {
        val hex = ReaderCssBuilder.colorToHex(0xFFFFFFFF.toInt())
        assertEquals("#FFFFFF", hex)
    }

    @Test
    fun `colorToHex converts color with alpha`() {
        val hex = ReaderCssBuilder.colorToHex(0x80FF0000.toInt())
        assertEquals("#80FF0000", hex)
    }
}
