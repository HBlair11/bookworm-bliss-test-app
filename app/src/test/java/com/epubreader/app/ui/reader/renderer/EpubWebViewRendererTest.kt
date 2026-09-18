package com.epubreader.app.ui.reader.renderer

import com.epubreader.app.core.reader.ReaderPosition
import com.epubreader.app.core.reader.ReaderSettings
import com.epubreader.app.core.reader.ReaderTextSelection
import com.epubreader.app.epub.ReaderSelectionLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the selection bridge through EpubWebViewRenderer.
 *
 * These tests verify that the canonical ReaderTextSelection model
 * preserves raw text (no trimming) and that displayText is available
 * for UI consumers. The renderer adapter itself requires a WebView
 * context, so we test the selection model contract here.
 */
class EpubWebViewRendererTest {

    @Test
    fun `ReaderTextSelection from locator preserves raw text`() {
        val locator = ReaderSelectionLocator(
            text = "  selected text  ",
            spineHref = "chapter1.xhtml",
            startPath = "/html/body/p[0]/#text:0",
            startOffset = 5,
            endPath = "/html/body/p[0]/#text:0",
            endOffset = 18,
            prefix = "Some prefix",
            suffix = "Some suffix",
            rectLeft = 10,
            rectTop = 20,
            rectRight = 100,
            rectBottom = 40,
        )

        val selection = ReaderTextSelection.fromLocator(locator)

        assertEquals("  selected text  ", selection.text)
        assertEquals("selected text", selection.displayText)
        assertEquals("chapter1.xhtml", selection.spineHref)
        assertEquals(5, selection.startOffset)
        assertEquals(18, selection.endOffset)
        assertEquals("Some prefix", selection.prefix)
        assertEquals("Some suffix", selection.suffix)
    }

    @Test
    fun `ReaderTextSelection toPosition converts correctly`() {
        val selection = ReaderTextSelection(
            text = "highlight",
            spineHref = "ch3.xhtml",
            startPath = "/html/body/div[1]/p[2]/#text:0",
            startOffset = 10,
            endPath = "/html/body/div[1]/p[2]/#text:0",
            endOffset = 19,
        )

        val position = selection.toPosition(spineIndex = 5)

        assertEquals(5, position.spineIndex)
        assertEquals("/html/body/div[1]/p[2]/#text:0", position.domAnchor)
        assertEquals(10, position.charOffset)
        assertEquals("ch3.xhtml", position.fragment)
    }

    @Test
    fun `ReaderTextSelection displayText handles empty selection`() {
        val selection = ReaderTextSelection(
            text = "",
            spineHref = "ch1.xhtml",
            startPath = "",
            startOffset = 0,
            endPath = "",
            endOffset = 0,
        )

        assertEquals("", selection.text)
        assertEquals("", selection.displayText)
    }

    @Test
    fun `ReaderTextSelection displayText handles whitespace-only selection`() {
        val selection = ReaderTextSelection(
            text = "   \n\t  ",
            spineHref = "ch1.xhtml",
            startPath = "",
            startOffset = 0,
            endPath = "",
            endOffset = 5,
        )

        assertEquals("   \n\t  ", selection.text)
        assertEquals("", selection.displayText)
    }
}
