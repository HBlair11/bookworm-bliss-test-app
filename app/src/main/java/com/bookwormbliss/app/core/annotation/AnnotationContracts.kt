package com.bookwormbliss.app.core.annotation

import com.bookwormbliss.app.core.reader.ReaderPosition
import com.bookwormbliss.app.core.reader.ReaderTextSelection

/**
 * Canonical annotation location — shared by bookmarks, highlights, and notes.
 *
 * This uses [ReaderPosition] as its foundation so all annotation types
 * share the same "where am I?" logic, rather than each inventing its own.
 *
 * @property position     The canonical reader position.
 * @property spineHref    The spine item href this annotation belongs to.
 * @property text         Selected text (for highlights) or snippet (for bookmarks).
 * @property note         Optional user note.
 * @property color        Color for highlights (as Int).
 * @property createdAt    Creation timestamp (epoch millis).
 */
data class AnnotationLocation(
    val position: ReaderPosition,
    val spineHref: String,
    val text: String = "",
    val note: String? = null,
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /**
         * Create an annotation location from a text selection.
         * The selection's spine href and DOM anchor are preserved.
         */
        fun fromSelection(
            selection: ReaderTextSelection,
            spineIndex: Int,
            color: Int = 0,
            note: String? = null,
        ): AnnotationLocation = AnnotationLocation(
            position = selection.toPosition(spineIndex),
            spineHref = selection.spineHref,
            text = selection.text,
            note = note,
            color = color,
        )
    }
}

/**
 * Type of annotation.
 */
enum class AnnotationType {
    BOOKMARK,
    HIGHLIGHT,
    NOTE,
}

/**
 * A unified annotation — bookmarks, highlights, and notes all share
 * this structure so the feature layer can treat them uniformly.
 */
data class ReaderAnnotation(
    val id: Long,
    val bookId: Long,
    val type: AnnotationType,
    val location: AnnotationLocation,
) {
    /** Whether this annotation has a note attached. */
    val hasNote: Boolean get() = !location.note.isNullOrBlank()
}
