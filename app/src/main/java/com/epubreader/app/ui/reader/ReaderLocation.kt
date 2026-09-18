package com.epubreader.app.ui.reader

/**
 * ReaderLocation — Extracted from ReaderActivity (Phase 6).
 *
 * Represents a specific reading position within the book: which spine item
 * (chapter), which page within that chapter, and the scroll ratio for
 * sub-page precision.
 *
 * Used by:
 * - Navigation history (back/forward stack)
 * - Exact seek locations
 * - Progress display
 *
 * This is a transitional model — the long-term goal is to replace it with
 * the canonical [com.epubreader.app.core.reader.ReaderPosition] once the
 * renderer migration is complete.
 */
data class ReaderLocation(
    val spineIndex: Int,
    val pageInChapter: Int,
    val ratio: Float,
)
