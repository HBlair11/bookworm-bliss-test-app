package com.epubreader.app.features.annotation

import com.epubreader.app.data.BookmarkEntity
import com.epubreader.app.features.FakeBookmarkDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkServiceTest {

    private val dao = FakeBookmarkDao()
    private val service = BookmarkService(dao)

    @Test
    fun addWholePageBookmark_createsWithAssignedId() = runTest {
        val result = service.addWholePageBookmark(
            bookId = 7L, spineIndex = 2, pageInChapter = 3, scrollRatio = 0.5f,
            chapterTitle = "Chapter 3", snippet = "It was the best of times",
        )
        assertTrue(result is BookmarkService.AddResult.Created)
        result as BookmarkService.AddResult.Created
        assertEquals(1L, result.bookmark.id)
        assertEquals(BookmarkEntity.TYPE_WHOLE_PAGE, result.bookmark.bookmarkType)
        assertEquals("It was the best of times", result.bookmark.snippet)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun addWholePageBookmark_blankSnippetFallsBackToChapterTitle() = runTest {
        val result = service.addWholePageBookmark(
            bookId = 1L, spineIndex = 0, pageInChapter = 0, scrollRatio = 0f,
            chapterTitle = "Opening", snippet = "   ",
        )
        result as BookmarkService.AddResult.Created
        assertEquals("Opening", result.bookmark.snippet)
    }

    @Test
    fun addWholePageBookmark_duplicateBySnippetWinsEvenAfterReflow() = runTest {
        // First bookmark: page 3, snippet captured.
        service.addWholePageBookmark(1L, 1, 3, 0.25f, "Ch. 2", "The quick brown fox")
        // Reflow: same text now paginates to page 9 with a different ratio —
        // the semantic snippet must still dedupe it.
        val second = service.addWholePageBookmark(1L, 1, 9, 0.9f, "Ch. 2", "The quick brown fox")
        assertTrue(second is BookmarkService.AddResult.Duplicate)
        second as BookmarkService.AddResult.Duplicate
        assertEquals(3, second.existing.pageInChapter)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun addWholePageBookmark_duplicateByPageAndRatioWithoutSnippet() = runTest {
        service.addWholePageBookmark(1L, 1, 4, 0.5f, "Ch. 2", "")
        val second = service.addWholePageBookmark(1L, 1, 4, 0.5f, "Ch. 2", "")
        assertTrue(second is BookmarkService.AddResult.Duplicate)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun addWholePageBookmark_differentBookOrSpineIsNotDuplicate() = runTest {
        service.addWholePageBookmark(1L, 1, 3, 0.5f, "Ch. 2", "Same words")
        val other = service.addWholePageBookmark(2L, 1, 3, 0.5f, "Ch. 2", "Same words")
        assertTrue(other is BookmarkService.AddResult.Created)
        val otherSpine = service.addWholePageBookmark(1L, 2, 3, 0.5f, "Ch. 2", "Same words")
        assertTrue(otherSpine is BookmarkService.AddResult.Created)
        assertEquals(3, dao.rows.size)
    }

    @Test
    fun addTextBookmark_duplicateBySelectionAnchor() = runTest {
        val anchor = """__LIVRE_SELECTED_V1__{"start":"p#3/1","end":"p#3/5"}"""
        service.addTextBookmark(1L, 2, 5, 0.4f, "Ch. 3", anchor)
        // Same selection re-tapped on a different page still dedupes.
        val second = service.addTextBookmark(1L, 2, 11, 0.8f, "Ch. 3", anchor)
        assertTrue(second is BookmarkService.AddResult.Duplicate)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun addTextBookmark_createsWithTypeText() = runTest {
        val result = service.addTextBookmark(1L, 0, 1, 0.1f, "Ch. 1", "anchor-json")
        result as BookmarkService.AddResult.Created
        assertEquals(BookmarkEntity.TYPE_TEXT, result.bookmark.bookmarkType)
        assertEquals("anchor-json", result.bookmark.snippet)
    }

    @Test
    fun deleteAndRestore_restoresExactRowId() = runTest {
        val created = service.addWholePageBookmark(1L, 1, 2, 0.5f, "Ch.", "snip")
        created as BookmarkService.AddResult.Created
        val bookmark = created.bookmark

        service.delete(bookmark)
        assertTrue(dao.rows.isEmpty())

        service.restore(bookmark)
        assertEquals(1, dao.rows.size)
        assertEquals(bookmark.id, dao.rows[0].id)
        assertEquals(bookmark.snippet, dao.rows[0].snippet)
    }

    @Test
    fun observeForBook_ordersNewestFirstAndFiltersByBook() = runTest {
        dao.insert(BookmarkEntity(bookId = 1L, spineIndex = 0, scrollRatio = 0f, pageInChapter = 0, chapterTitle = "a", snippet = "a", createdAt = 10L))
        dao.insert(BookmarkEntity(bookId = 1L, spineIndex = 1, scrollRatio = 0f, pageInChapter = 0, chapterTitle = "b", snippet = "b", createdAt = 20L))
        dao.insert(BookmarkEntity(bookId = 2L, spineIndex = 0, scrollRatio = 0f, pageInChapter = 0, chapterTitle = "x", snippet = "x", createdAt = 30L))

        val collected = service.observeForBook(1L).first()
        assertEquals(listOf(20L, 10L), collected.map { it.createdAt })
    }
}
