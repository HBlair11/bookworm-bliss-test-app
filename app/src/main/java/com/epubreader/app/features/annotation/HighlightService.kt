package com.epubreader.app.features.annotation

import com.epubreader.app.data.HighlightDao
import com.epubreader.app.data.HighlightEntity
import kotlinx.coroutines.flow.Flow

/**
 * HighlightService — Phase 7 feature service.
 *
 * Owns the highlight feature's domain logic and its design tokens:
 * persistence (save / delete / restore / observe / per-chapter lookup) plus
 * the canonical highlight color palette used across the reader.
 *
 * This is the single source of truth for highlight colors — the palette and
 * the rgba() CSS conversion used to live as duplicated constants in both
 * ReaderActivity and ReaderOverlayController (Phase 6 aftermath). UI code
 * now references [HighlightService.HIGHLIGHT_YELLOW] etc. so the palette can
 * change in exactly one place.
 *
 * @param highlightDao Room DAO — an interface, so tests can substitute an
 *   in-memory implementation.
 */
class HighlightService(private val highlightDao: HighlightDao) {

    companion object {
        // Highlight colors — stored as Int ARGB in the DB, rendered as
        // rgba() in CSS. The color Int is the full-opacity color; the CSS
        // uses 40% opacity for a subtle highlight that doesn't obscure the
        // text underneath.
        const val HIGHLIGHT_YELLOW = 0xFFFFEB3B.toInt()
        const val HIGHLIGHT_GREEN = 0xFF66BB6A.toInt()
        const val HIGHLIGHT_BLUE = 0xFF42A5F5.toInt()
        const val HIGHLIGHT_PURPLE = 0xFFAB47BC.toInt()

        /** The picker palette in display order. */
        val COLORS = listOf(HIGHLIGHT_YELLOW, HIGHLIGHT_GREEN, HIGHLIGHT_BLUE, HIGHLIGHT_PURPLE)

        /** Converts a stored highlight color Int to the CSS rgba() string
         *  used by the WebView highlight decoration. Pure bit math — no
         *  android.graphics dependency, so it stays unit-testable on the JVM. */
        fun highlightCssColor(color: Int): String {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return "rgba($r,$g,$b,0.4)"
        }
    }

    /**
     * Saves a new highlight and returns the assigned row id.
     *
     * The caller keeps the entity around for undo; combine with the returned
     * id via `highlight.copy(id = id)` when re-injecting into the WebView.
     */
    suspend fun save(highlight: HighlightEntity): Long = highlightDao.insert(highlight)

    /** Deletes a highlight (undo path restores it with [restore]). */
    suspend fun delete(highlight: HighlightEntity) = highlightDao.delete(highlight)

    /**
     * Restores a previously deleted highlight. The entity keeps its original
     * row id and the DAO's REPLACE strategy re-inserts it under that id.
     */
    suspend fun restore(highlight: HighlightEntity) {
        highlightDao.insert(highlight)
    }

    /** Highlights within one chapter, in document order. */
    suspend fun getForChapter(bookId: Long, spineHref: String): List<HighlightEntity> =
        highlightDao.getForChapter(bookId, spineHref)

    /** Live highlight list for a book, newest first. */
    fun observeForBook(bookId: Long): Flow<List<HighlightEntity>> =
        highlightDao.observeForBook(bookId)

    /** Updates a highlight's note (null clears it). */
    suspend fun updateNote(id: Long, note: String?) = highlightDao.updateNote(id, note)
}
