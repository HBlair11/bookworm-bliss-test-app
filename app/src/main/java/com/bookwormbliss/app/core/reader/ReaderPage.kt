package com.bookwormbliss.app.core.reader

/**
 * A single page in the reader — a visual "screen" of content.
 *
 * Pages are renderer-specific and may change after reflow (font size
 * change, rotation, settings change). They are NOT a stable location;
 * for stable locations, use [ReaderPosition].
 *
 * @property spineIndex    Which spine item this page belongs to.
 * @property pageInChapter 0-based page index within the spine item.
 * @property absolutePage  0-based page index across the whole book.
 * @property totalInChapter Total pages in this spine item.
 * @property totalInBook    Total pages across the whole book.
 */
data class ReaderPage(
    val spineIndex: Int,
    val pageInChapter: Int,
    val absolutePage: Int,
    val totalInChapter: Int,
    val totalInBook: Int,
) {
    /** 1-based display page number (human-friendly). */
    val displayNumber: Int get() = absolutePage + 1

    /** Progress fraction within the book (0..1). */
    val progressFraction: Float
        get() = if (totalInBook <= 0) 0f else (absolutePage.toFloat() / totalInBook).coerceIn(0f, 1f)

    /** True if this is the last page of the book. */
    val isLastPage: Boolean get() = absolutePage >= totalInBook - 1

    /** True if this is the first page of the book. */
    val isFirstPage: Boolean get() = absolutePage <= 0

    /** Human-readable label, e.g. "3 / 142". */
    val label: String get() = "${displayNumber} / ${totalInBook.coerceAtLeast(1)}"
}
