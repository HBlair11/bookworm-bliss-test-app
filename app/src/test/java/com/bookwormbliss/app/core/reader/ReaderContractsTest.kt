package com.bookwormbliss.app.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPositionTest {

    @Test
    fun `START position is at start`() {
        val pos = ReaderPosition.START
        assertTrue(pos.isAtStart)
    }

    @Test
    fun `clamped position respects spine size`() {
        val pos = ReaderPosition(spineIndex = 5, scrollRatio = 1.5f, pageInChapter = -1)
        val clamped = pos.clamped(3)
        assertEquals(2, clamped.spineIndex)
        assertEquals(1f, clamped.scrollRatio, 0.001f)
        assertEquals(0, clamped.pageInChapter)
    }

    @Test
    fun `isAtEnd returns true at last spine with high ratio`() {
        val pos = ReaderPosition(spineIndex = 9, scrollRatio = 0.995f, pageInChapter = 5)
        val pageCounts = intArrayOf(3, 4, 5, 2, 6, 4, 3, 2, 5, 6)
        assertTrue(pos.isAtEnd(10, pageCounts))
    }

    @Test
    fun `isAtEnd returns false mid-book`() {
        val pos = ReaderPosition(spineIndex = 3, scrollRatio = 0.5f, pageInChapter = 2)
        val pageCounts = intArrayOf(3, 4, 5, 2, 6, 4, 3, 2, 5, 6)
        assertFalse(pos.isAtEnd(10, pageCounts))
    }

    @Test
    fun `isAtEnd handles null page counts`() {
        val pos = ReaderPosition(spineIndex = 4, scrollRatio = 1f, pageInChapter = 0)
        assertTrue(pos.isAtEnd(5, null))
    }
}

class ReaderProgressTest {

    @Test
    fun `ZERO progress has zero fraction`() {
        assertEquals(0f, ReaderProgress.ZERO.fraction, 0.001f)
        assertEquals(0, ReaderProgress.ZERO.percent)
    }

    @Test
    fun `COMPLETE progress has full fraction`() {
        assertEquals(1f, ReaderProgress.COMPLETE.fraction, 0.001f)
        assertEquals(100, ReaderProgress.COMPLETE.percent)
    }

    @Test
    fun `chapter-level progress is stable before page measurement`() {
        val pos = ReaderPosition(spineIndex = 4, scrollRatio = 0f, pageInChapter = 0)
        val progress = ReaderProgress.compute(pos, 10, null)
        assertEquals(0.4f, progress.fraction, 0.0001f)
    }

    @Test
    fun `chapter-level progress with scroll ratio`() {
        val pos = ReaderPosition(spineIndex = 4, scrollRatio = 0.5f, pageInChapter = 0)
        val progress = ReaderProgress.compute(pos, 10, null)
        assertEquals(0.45f, progress.fraction, 0.0001f)
    }

    @Test
    fun `page-mapped progress uses measured page counts`() {
        val pos = ReaderPosition(spineIndex = 2, scrollRatio = 0f, pageInChapter = 0)
        val pageCounts = intArrayOf(3, 5, 4, 6)
        val progress = ReaderProgress.compute(pos, 4, pageCounts)
        // prefix[2] = 3 + 5 = 8, total = 3+5+4+6 = 18
        // fraction = 8 / 18 = 0.4444
        assertEquals(0.4444f, progress.fraction, 0.001f)
    }

    @Test
    fun `page-mapped progress with scroll ratio`() {
        val pos = ReaderPosition(spineIndex = 1, scrollRatio = 0.5f, pageInChapter = 2)
        val pageCounts = intArrayOf(3, 5, 4, 6)
        val progress = ReaderProgress.compute(pos, 4, pageCounts)
        // prefix[1] = 3, inSpine = 5, total = 18
        // absolute = 3 + 0.5 * 5 = 5.5
        // fraction = 5.5 / 18 = 0.3055
        assertEquals(0.3055f, progress.fraction, 0.001f)
    }

    @Test
    fun `zero spine size returns zero progress`() {
        val pos = ReaderPosition(spineIndex = 0, scrollRatio = 0f, pageInChapter = 0)
        val progress = ReaderProgress.compute(pos, 0, null)
        assertEquals(0f, progress.fraction, 0.001f)
    }

    @Test
    fun `label formats as percentage`() {
        val progress = ReaderProgress(0.375f, 0, 0, 0)
        assertEquals("37%", progress.label)
    }
}

class ReaderPageTest {

    @Test
    fun `display number is 1-based`() {
        val page = ReaderPage(spineIndex = 0, pageInChapter = 0, absolutePage = 0, totalInChapter = 10, totalInBook = 100)
        assertEquals(1, page.displayNumber)
    }

    @Test
    fun `progress fraction computes correctly`() {
        val page = ReaderPage(spineIndex = 5, pageInChapter = 2, absolutePage = 36, totalInChapter = 8, totalInBook = 142)
        assertEquals(36f / 142f, page.progressFraction, 0.001f)
    }

    @Test
    fun `first page detection`() {
        val page = ReaderPage(spineIndex = 0, pageInChapter = 0, absolutePage = 0, totalInChapter = 10, totalInBook = 100)
        assertTrue(page.isFirstPage)
        assertFalse(page.isLastPage)
    }

    @Test
    fun `last page detection`() {
        val page = ReaderPage(spineIndex = 9, pageInChapter = 4, absolutePage = 49, totalInChapter = 5, totalInBook = 50)
        assertTrue(page.isLastPage)
        assertFalse(page.isFirstPage)
    }

    @Test
    fun `label formats as current of total`() {
        val page = ReaderPage(spineIndex = 0, pageInChapter = 0, absolutePage = 2, totalInChapter = 10, totalInBook = 100)
        assertEquals("3 / 100", page.label)
    }
}

class ReaderSettingsTest {

    @Test
    fun `default settings have expected values`() {
        val settings = ReaderSettings()
        assertEquals("serif", settings.fontFamily)
        assertEquals(24, settings.fontSize)
        assertEquals(1.6f, settings.lineHeight, 0.001f)
        assertEquals(20, settings.margin)
        assertEquals("left", settings.alignment)
        assertEquals("ivory", settings.themeId)
    }

    @Test
    fun `layout key changes with font size`() {
        val small = ReaderSettings(fontSize = 24)
        val large = ReaderSettings(fontSize = 32)
        assertNotEquals(small.layoutKey, large.layoutKey)
    }

    @Test
    fun `layout key changes with line height`() {
        val tight = ReaderSettings(lineHeight = 1.2f)
        val loose = ReaderSettings(lineHeight = 2.0f)
        assertNotEquals(tight.layoutKey, loose.layoutKey)
    }

    @Test
    fun `layout key changes with margin`() {
        val narrow = ReaderSettings(margin = 20)
        val wide = ReaderSettings(margin = 48)
        assertNotEquals(narrow.layoutKey, wide.layoutKey)
    }

    @Test
    fun `layout key is same for same settings`() {
        val a = ReaderSettings(fontSize = 28, lineHeight = 1.8f, margin = 36)
        val b = ReaderSettings(fontSize = 28, lineHeight = 1.8f, margin = 36)
        assertEquals(a.layoutKey, b.layoutKey)
    }

    private fun assertNotEquals(a: String, b: String) {
        assert(a != b) { "Expected '$a' != '$b'" }
    }
}
