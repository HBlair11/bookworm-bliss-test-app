package com.epubreader.app.features.annotation

import com.epubreader.app.core.annotation.AnnotationType
import com.epubreader.app.core.reader.ReaderPosition
import com.epubreader.app.data.BookmarkEntity
import com.epubreader.app.data.HighlightEntity
import com.epubreader.app.features.FakeBookmarkDao
import com.epubreader.app.features.FakeHighlightDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationRepositoryTest {

    private val bookmarkDao = FakeBookmarkDao()
    private val highlightDao = FakeHighlightDao()
    private val repository = AnnotationRepository(bookmarkDao, highlightDao)

    private val spine = listOf(
        "text/chapter1.xhtml",
        "text/chapter2.xhtml",
        "text/chapter3.xhtml",
    )

    @Test
    fun bookmarkMapping_usesSpineIndexWithEmptyHref() {
        val bookmark = BookmarkEntity(
            id = 5L, bookId = 1L, spineIndex = 2, scrollRatio = 0.75f,
            pageInChapter = 4, chapterTitle = "Ch. 3", snippet = "the snippet",
            createdAt = 100L,
        )
        val annotation = repository.toAnnotation(bookmark)

        assertEquals(AnnotationType.BOOKMARK, annotation.type)
        assertEquals(5L, annotation.id)
        val position = annotation.location.position as ReaderPosition
        assertEquals(2, position.spineIndex)
        assertEquals(0.75f, position.scrollRatio, 0.0001f)
        assertEquals("", annotation.location.spineHref)
        assertEquals("the snippet", annotation.location.text)
    }

    @Test
    fun highlightMapping_resolvesSpineIndexViaResolver() {
        val highlight = HighlightEntity(
            id = 9L, bookId = 1L, spineHref = "text/chapter2.xhtml",
            text = "chosen words", color = 0xFF42A5F5.toInt(),
            note = "a note", startPath = "p#1", endPath = "p#2",
            startOffset = 3, endOffset = 15, normalizedStart = 12, createdAt = 50L,
        )
        val resolver: (String) -> Int = { href -> spine.indexOf(href) }

        val annotation = repository.toAnnotation(highlight, resolver)

        assertEquals(AnnotationType.HIGHLIGHT, annotation.type)
        val position = annotation.location.position as ReaderPosition
        assertEquals(1, position.spineIndex)
        assertEquals("text/chapter2.xhtml", annotation.location.spineHref)
        assertEquals("a note", annotation.location.note)
        assertEquals("chosen words", annotation.location.text)
        // DOM anchors survive the mapping for relocation after reflow.
        assertEquals("p#1", position.domAnchor)
    }

    @Test
    fun highlightMapping_withoutResolver_spineIndexIsUnresolved() {
        val highlight = HighlightEntity(
            id = 1L, bookId = 1L, spineHref = "text/chapter9.xhtml",
            text = "x", color = 0,
        )
        val position = repository.toAnnotation(highlight).location.position as ReaderPosition
        assertEquals(-1, position.spineIndex)
    }

    @Test
    fun observeForBook_mergesBothTypesNewestFirst() = runTest {
        bookmarkDao.insert(
            BookmarkEntity(id = 1L, bookId = 1L, spineIndex = 0, scrollRatio = 0f, pageInChapter = 0, chapterTitle = "c", snippet = "older bookmark", createdAt = 10L)
        )
        highlightDao.insert(
            HighlightEntity(id = 2L, bookId = 1L, spineHref = "text/chapter1.xhtml", text = "newer highlight", color = 0, createdAt = 20L)
        )
        bookmarkDao.insert(
            BookmarkEntity(id = 3L, bookId = 2L, spineIndex = 0, scrollRatio = 0f, pageInChapter = 0, chapterTitle = "other book", snippet = "x", createdAt = 30L)
        )

        val annotations = repository.observeForBook(1L) { href -> spine.indexOf(href) }.first()

        assertEquals(2, annotations.size)
        // Newest first across both stores.
        assertEquals(20L, annotations[0].location.createdAt)
        assertEquals(AnnotationType.HIGHLIGHT, annotations[0].type)
        assertEquals(10L, annotations[1].location.createdAt)
        assertEquals(AnnotationType.BOOKMARK, annotations[1].type)
    }

    @Test
    fun observeCounts_reportsPerType() = runTest {
        bookmarkDao.insert(BookmarkEntity(bookId = 1L, spineIndex = 0, scrollRatio = 0f, pageInChapter = 0, chapterTitle = "a", snippet = "a"))
        bookmarkDao.insert(BookmarkEntity(bookId = 1L, spineIndex = 1, scrollRatio = 0f, pageInChapter = 0, chapterTitle = "b", snippet = "b"))
        highlightDao.insert(HighlightEntity(bookId = 1L, spineHref = "text/chapter1.xhtml", text = "h", color = 0))

        val counts = repository.observeCounts(1L).first()

        assertEquals(2, counts.bookmarks)
        assertEquals(1, counts.highlights)
        assertEquals(3, counts.total)
    }
}
