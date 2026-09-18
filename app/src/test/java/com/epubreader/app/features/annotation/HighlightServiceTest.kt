package com.epubreader.app.features.annotation

import com.epubreader.app.data.HighlightEntity
import com.epubreader.app.features.FakeHighlightDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightServiceTest {

    private val dao = FakeHighlightDao()
    private val service = HighlightService(dao)

    private fun highlight(
        text: String,
        startPath: String = "p#2",
        startOffset: Int = 4,
        normalizedStart: Int = 0,
        createdAt: Long = 0L,
    ) = HighlightEntity(
        bookId = 1L,
        spineHref = "text/chapter1.xhtml",
        text = text,
        color = HighlightService.HIGHLIGHT_YELLOW,
        prefix = "before ",
        suffix = " after",
        startPath = startPath,
        endPath = "p#2",
        startOffset = startOffset,
        endOffset = startOffset + text.length,
        normalizedStart = normalizedStart,
        createdAt = createdAt,
    )

    @Test
    fun save_assignsIdAndPersists() = runTest {
        val id = service.save(highlight("serene"))
        assertEquals(1L, id)
        assertEquals(1, dao.rows.size)
        assertEquals("serene", dao.rows[0].text)
    }

    @Test
    fun deleteAndRestore_restoresExactRow() = runTest {
        val id = service.save(highlight("serene"))
        val stored = dao.rows[0]

        service.delete(stored)
        assertTrue(dao.rows.isEmpty())

        service.restore(stored)
        assertEquals(1, dao.rows.size)
        assertEquals(id, dao.rows[0].id)
        assertEquals("serene", dao.rows[0].text)
    }

    @Test
    fun getForChapter_ordersByDocumentPosition() = runTest {
        service.save(highlight("later text", normalizedStart = 90, createdAt = 1L))
        service.save(highlight("early text", normalizedStart = 10, createdAt = 2L))

        val chapter = service.getForChapter(1L, "text/chapter1.xhtml")
        assertEquals(listOf("early text", "later text"), chapter.map { it.text })
    }

    @Test
    fun getForChapter_filtersByBookAndHref() = runTest {
        service.save(highlight("one"))
        service.save(highlight("other").copy(bookId = 2L, spineHref = "text/chapter9.xhtml"))

        val chapter = service.getForChapter(1L, "text/chapter1.xhtml")
        assertEquals(listOf("one"), chapter.map { it.text })
    }

    @Test
    fun updateNote_updatesStoredRow() = runTest {
        val id = service.save(highlight("note me"))
        service.updateNote(id, "marginalia")
        assertEquals("marginalia", dao.rows[0].note)
        service.updateNote(id, null)
        assertEquals(null, dao.rows[0].note)
    }

    @Test
    fun observeForBook_ordersNewestFirst() = runTest {
        service.save(highlight("old", createdAt = 10L))
        service.save(highlight("new", createdAt = 20L))
        val collected = service.observeForBook(1L).first()
        assertEquals(listOf("new", "old"), collected.map { it.text })
    }

    @Test
    fun cssColor_usesFortyPercentAlpha() {
        assertEquals("rgba(255,235,59,0.4)", HighlightService.highlightCssColor(HighlightService.HIGHLIGHT_YELLOW))
        assertEquals("rgba(102,187,106,0.4)", HighlightService.highlightCssColor(HighlightService.HIGHLIGHT_GREEN))
        assertEquals("rgba(66,165,245,0.4)", HighlightService.highlightCssColor(HighlightService.HIGHLIGHT_BLUE))
        assertEquals("rgba(171,71,188,0.4)", HighlightService.highlightCssColor(HighlightService.HIGHLIGHT_PURPLE))
    }

    @Test
    fun palette_matchesOriginalConstants() {
        assertEquals(0xFFFFEB3B.toInt(), HighlightService.HIGHLIGHT_YELLOW)
        assertEquals(0xFF66BB6A.toInt(), HighlightService.HIGHLIGHT_GREEN)
        assertEquals(0xFF42A5F5.toInt(), HighlightService.HIGHLIGHT_BLUE)
        assertEquals(0xFFAB47BC.toInt(), HighlightService.HIGHLIGHT_PURPLE)
    }
}
