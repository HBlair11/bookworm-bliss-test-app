package com.epubreader.app.core.reader

/**
 * Abstraction over a document that the reader can render.
 *
 * This is the format-agnostic contract. Concrete implementations:
 *  - [com.epubreader.app.core.epub.EpubDocument] for EPUB files
 *  - (future) PdfDocument for PDF
 *  - (future) CbzDocument for comic archives
 *
 * The UI and renderer features consume this interface, never a
 * format-specific type directly. That keeps the reader format-agnostic
 * and lets us add new formats without reopening the renderer.
 */
interface ReaderDocument {

    /** Human-readable title (from metadata). */
    val title: String

    /** Human-readable author(s) (from metadata). */
    val author: String

    /** The number of spine items (chapters/sections). */
    val spineSize: Int

    /** The file this document was loaded from. */
    val sourceFile: java.io.File

    /** Per-spine page counts (screen-accurate, measured or estimated). */
    val pageCounts: IntArray

    /** Total pages across all spine items. */
    val totalPages: Int
        get() = if (pageCounts.isEmpty()) 0
                else pageCounts.sumOf { it.coerceAtLeast(1) }

    /** Look up a spine item's href by index. */
    fun spineHref(index: Int): String

    /** Resolve a relative href to an absolute resource path. */
    fun resolveHref(href: String, baseDir: String = ""): String

    /** Find the spine index for a given href (-1 if not found). */
    fun spineIndexForHref(href: String): Int
}
