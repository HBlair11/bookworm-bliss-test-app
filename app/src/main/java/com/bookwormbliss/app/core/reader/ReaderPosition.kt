package com.bookwormbliss.app.core.reader

/**
 * Canonical reader position — the "where am I?" answer that is
 * independent of the rendering technology.
 *
 * A reflowable EPUB doesn't have permanent pages, so a position
 * can't be just `page = 37`. Instead, we capture:
 *  - which spine item (chapter) the reader is in
 *  - a scroll/page ratio within that spine item
 *  - an optional DOM anchor (XPath/element path) for precise
 *    restoration after reflow
 *  - an optional character offset for TTS/highlight alignment
 *  - the in-chapter page index (renderer-specific)
 *
 * This single model is consumed by:
 *  - Bookmark storage
 *  - Highlight storage
 *  - Reading progress persistence
 *  - TTS word alignment
 *  - Search result navigation
 *  - Future annotations
 *
 * That prevents building "where was the user?" logic six times.
 *
 * @property spineIndex     0-based index into the document's spine.
 * @property scrollRatio    0..1 fraction of the way through the spine item.
 * @property pageInChapter  0-based page index within the current spine item
 *                           (renderer-specific; may change after reflow).
 * @property domAnchor      Optional XPath or CSS-selector-like path to a DOM
 *                           node for precise restoration after reflow.
 * @property charOffset     Optional character offset within the spine item's
 *                           text content (for TTS/highlight alignment).
 * @property fragment       Optional URL fragment (#id) for deep-linking.
 */
data class ReaderPosition(
    val spineIndex: Int = 0,
    val scrollRatio: Float = 0f,
    val pageInChapter: Int = 0,
    val domAnchor: String? = null,
    val charOffset: Int? = null,
    val fragment: String? = null,
) {
    /** Clamp values to valid ranges. */
    fun clamped(spineSize: Int): ReaderPosition = copy(
        spineIndex = spineIndex.coerceIn(0, (spineSize - 1).coerceAtLeast(0)),
        scrollRatio = scrollRatio.coerceIn(0f, 1f),
        pageInChapter = pageInChapter.coerceAtLeast(0),
    )

    /** True if this position is at the very start of the book. */
    val isAtStart: Boolean
        get() = spineIndex == 0 && scrollRatio <= 0f && pageInChapter == 0

    /** True if this position is at or near the end of the book. */
    fun isAtEnd(spineSize: Int, pageCounts: IntArray?): Boolean {
        if (spineSize <= 0) return true
        val last = spineSize - 1
        if (spineIndex != last) return false
        val pages = pageCounts?.getOrNull(last) ?: 1
        return scrollRatio >= 0.99f || pageInChapter >= pages - 1
    }

    companion object {
        /** A position at the very start of the book. */
        val START = ReaderPosition(spineIndex = 0, scrollRatio = 0f, pageInChapter = 0)
    }
}
