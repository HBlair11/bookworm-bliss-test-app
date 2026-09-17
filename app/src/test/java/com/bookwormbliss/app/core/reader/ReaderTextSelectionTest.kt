package com.bookwormbliss.app.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextSelectionTest {

    @Test
    fun `hasRect returns true when rect has dimensions`() {
        val selection = ReaderTextSelection(
            text = "hello",
            spineHref = "ch1.xhtml",
            startPath = "/html/body/p[0]",
            startOffset = 5,
            endPath = "/html/body/p[0]",
            endOffset = 10,
            rectLeft = 10,
            rectTop = 20,
            rectRight = 50,
            rectBottom = 40,
        )
        assertTrue(selection.hasRect)
    }

    @Test
    fun `hasRect returns false for zero-size rect`() {
        val selection = ReaderTextSelection(
            text = "hello",
            spineHref = "ch1.xhtml",
            startPath = "/p",
            startOffset = 0,
            endPath = "/p",
            endOffset = 5,
        )
        assertFalse(selection.hasRect)
    }

    @Test
    fun `toPosition creates correct position`() {
        val selection = ReaderTextSelection(
            text = "word",
            spineHref = "ch2.xhtml",
            startPath = "/html/body/p[1]",
            startOffset = 15,
            endPath = "/html/body/p[1]",
            endOffset = 19,
        )
        val pos = selection.toPosition(spineIndex = 3)
        assertEquals(3, pos.spineIndex)
        assertEquals("/html/body/p[1]", pos.domAnchor)
        assertEquals(15, pos.charOffset)
        assertEquals("ch2.xhtml", pos.fragment)
    }
}
