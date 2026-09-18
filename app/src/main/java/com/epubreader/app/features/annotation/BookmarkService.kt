package com.epubreader.app.features.annotation

import com.epubreader.app.data.BookmarkDao
import com.epubreader.app.data.BookmarkEntity
import kotlinx.coroutines.flow.Flow

/**
 * BookmarkService — Phase 7 feature service.
 *
 * Owns the bookmark feature's domain logic so reader UI controllers only
 * handle presentation:
 *  - creating whole-page and selected-text bookmarks,
 *  - duplicate detection (semantic snippet first, then page/ratio),
 *  - delete / restore (undo),
 *  - observation for the reader's bookmark list.
 *
 * The duplicate-detection strategy is preserved exactly from the original
 * ReaderActivity implementation (Patch G/H/J): a bookmark is identified
 * primarily by its semantic snippet so it survives reader reflow (font-size
 * changes that repaginate the chapter), and only falls back to the
 * renderer-specific page/ratio pair when no snippet was captured.
 *
 * Controllers receive an [AddResult] and translate it into snackbars; this
 * class never touches UI, which keeps it unit-testable with a fake DAO.
 *
 * @param bookmarkDao Room DAO — an interface, so tests can substitute an
 *   in-memory implementation.
 */
class BookmarkService(private val bookmarkDao: BookmarkDao) {

    /** Outcome of an add attempt; the UI layer decides how to present it. */
    sealed interface AddResult {
        /** A new bookmark was inserted; [bookmark] carries the assigned row id. */
        data class Created(val bookmark: BookmarkEntity) : AddResult

        /** An equivalent bookmark already exists; [existing] is the stored row. */
        data class Duplicate(val existing: BookmarkEntity) : AddResult
    }

    /**
     * Adds a whole-page bookmark for the current chapter page.
     *
     * @param snippet semantic page snippet captured by the renderer; used as
     *   the primary duplicate key because it is stable across reflow.
     */
    suspend fun addWholePageBookmark(
        bookId: Long,
        spineIndex: Int,
        pageInChapter: Int,
        scrollRatio: Float,
        chapterTitle: String,
        snippet: String,
    ): AddResult {
        val trimmed = snippet.trim()
        val existing = bookmarkDao.findWholePageBySnippet(bookId, spineIndex, trimmed)
            ?: bookmarkDao.findWholePage(bookId, spineIndex, pageInChapter, scrollRatio)
        if (existing != null) return AddResult.Duplicate(existing)

        val bookmark = BookmarkEntity(
            bookId = bookId,
            spineIndex = spineIndex,
            scrollRatio = scrollRatio,
            pageInChapter = pageInChapter,
            chapterTitle = chapterTitle,
            snippet = trimmed.ifBlank { chapterTitle },
            bookmarkType = BookmarkEntity.TYPE_WHOLE_PAGE,
        )
        val id = bookmarkDao.insert(bookmark)
        return AddResult.Created(bookmark.copy(id = id))
    }

    /**
     * Adds a selected-text bookmark anchored to a DOM selection.
     *
     * @param selectionAnchor the serialized selection anchor
     *   (`__LIVRE_SELECTED_V1__{...}` JSON) that stores the DOM paths and
     *   offsets of the highlighted text; it is both the stored snippet and
     *   the duplicate key, so re-selecting the same sentence never creates
     *   a second bookmark even after reflow.
     */
    suspend fun addTextBookmark(
        bookId: Long,
        spineIndex: Int,
        pageInChapter: Int,
        scrollRatio: Float,
        chapterTitle: String,
        selectionAnchor: String,
    ): AddResult {
        val existing = bookmarkDao.findTextBySnippet(bookId, spineIndex, selectionAnchor)
            ?: bookmarkDao.findText(bookId, spineIndex, pageInChapter, selectionAnchor)
        if (existing != null) return AddResult.Duplicate(existing)

        val bookmark = BookmarkEntity(
            bookId = bookId,
            spineIndex = spineIndex,
            scrollRatio = scrollRatio,
            pageInChapter = pageInChapter,
            chapterTitle = chapterTitle,
            snippet = selectionAnchor,
            bookmarkType = BookmarkEntity.TYPE_TEXT,
        )
        val id = bookmarkDao.insert(bookmark)
        return AddResult.Created(bookmark.copy(id = id))
    }

    /** Deletes a bookmark (undo path restores it with [restore]). */
    suspend fun delete(bookmark: BookmarkEntity) = bookmarkDao.delete(bookmark)

    /**
     * Restores a previously deleted bookmark. The entity keeps its original
     * row id, and the DAO's REPLACE strategy re-inserts it under that id, so
     * the undo is transparent to observers.
     */
    suspend fun restore(bookmark: BookmarkEntity) {
        bookmarkDao.insert(bookmark)
    }

    /** Live bookmark list for a book, newest first (created_at DESC, id DESC). */
    fun observeForBook(bookId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeForBook(bookId)
}
