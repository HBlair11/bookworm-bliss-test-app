package com.epubreader.app.features.annotation

import com.epubreader.app.core.annotation.AnnotationLocation
import com.epubreader.app.core.annotation.AnnotationType
import com.epubreader.app.core.annotation.ReaderAnnotation
import com.epubreader.app.core.reader.ReaderPosition
import com.epubreader.app.data.BookmarkDao
import com.epubreader.app.data.BookmarkEntity
import com.epubreader.app.data.HighlightDao
import com.epubreader.app.data.HighlightEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * AnnotationRepository — Phase 7 feature service.
 *
 * A unified, read-model facade over the two annotation stores (bookmarks
 * and highlights). It exposes them through the core annotation contracts
 * ([ReaderAnnotation], [AnnotationLocation]) so future UI — a combined
 * "Annotations" panel, reading-stats screens, export tools — can consume
 * every annotation type through one stream instead of stitching per-feature
 * DAOs together.
 *
 * Ownership rules:
 *  - This repository owns the unified READ model (observe + map + counts).
 *  - Writes (create/delete/restore) stay on the typed services —
 *    [BookmarkService] and [HighlightService] — because each type carries
 *    type-specific columns (page/ratio for bookmarks, DOM paths/offsets for
 *    highlights) that a generic write API would only lose.
 *
 * Location model notes (deliberate, documented trade-offs):
 *  - Bookmarks store a spine index, not an href; their [AnnotationLocation.spineHref]
 *    is empty. Navigate by [ReaderPosition.spineIndex].
 *  - Highlights store a spine href, not an index; [ReaderPosition.spineIndex]
 *    is -1 (unresolved) unless a [spineResolver] is supplied. Callers should
 *    navigate by href for highlights and treat spineIndex -1 as "unresolved",
 *    never as chapter 0's predecessor.
 *
 * @param bookmarkDao  Room DAO (interface — fake-able in tests).
 * @param highlightDao Room DAO (interface — fake-able in tests).
 */
class AnnotationRepository(
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
) {

    /** Counts of each annotation type for one book. */
    data class AnnotationCounts(val bookmarks: Int, val highlights: Int) {
        val total: Int get() = bookmarks + highlights
    }

    /**
     * Live, unified annotation stream for a book, newest first.
     *
     * @param spineResolver optional href → spine index resolver (the reader
     *   supplies `epub.spine.indexOfFirst { it.href == href }`). Without it,
     *   highlight positions carry spineIndex = -1 (unresolved).
     */
    fun observeForBook(
        bookId: Long,
        spineResolver: (String) -> Int = { -1 },
    ): Flow<List<ReaderAnnotation>> =
        combine(
            bookmarkDao.observeForBook(bookId),
            highlightDao.observeForBook(bookId),
        ) { bookmarks, highlights ->
            (bookmarks.map { toAnnotation(it) } + highlights.map { toAnnotation(it, spineResolver) })
                .sortedByDescending { it.location.createdAt }
        }

    /** Live per-type counts for one book. */
    fun observeCounts(bookId: Long): Flow<AnnotationCounts> =
        combine(
            bookmarkDao.observeForBook(bookId),
            highlightDao.observeForBook(bookId),
        ) { bookmarks, highlights ->
            AnnotationCounts(bookmarks = bookmarks.size, highlights = highlights.size)
        }

    /** Maps a stored bookmark onto the unified annotation contract. */
    fun toAnnotation(bookmark: BookmarkEntity): ReaderAnnotation = ReaderAnnotation(
        id = bookmark.id,
        bookId = bookmark.bookId,
        type = AnnotationType.BOOKMARK,
        location = AnnotationLocation(
            position = ReaderPosition(
                spineIndex = bookmark.spineIndex,
                scrollRatio = bookmark.scrollRatio,
                pageInChapter = bookmark.pageInChapter.coerceAtLeast(0),
            ),
            spineHref = "",
            text = bookmark.snippet,
            note = null,
            color = 0,
            createdAt = bookmark.createdAt,
        ),
    )

    /**
     * Maps a stored highlight onto the unified annotation contract.
     *
     * @param spineResolver resolves the highlight's spine href to a spine
     *   index; defaults to "unresolved" (-1).
     */
    fun toAnnotation(
        highlight: HighlightEntity,
        spineResolver: (String) -> Int = { -1 },
    ): ReaderAnnotation = ReaderAnnotation(
        id = highlight.id,
        bookId = highlight.bookId,
        type = AnnotationType.HIGHLIGHT,
        location = AnnotationLocation(
            position = ReaderPosition(
                spineIndex = spineResolver(highlight.spineHref),
                scrollRatio = 0f,
                pageInChapter = 0,
                domAnchor = highlight.startPath.takeIf { it.isNotBlank() },
                charOffset = highlight.startOffset,
            ),
            spineHref = highlight.spineHref,
            text = highlight.text,
            note = highlight.note,
            color = highlight.color,
            createdAt = highlight.createdAt,
        ),
    )
}
