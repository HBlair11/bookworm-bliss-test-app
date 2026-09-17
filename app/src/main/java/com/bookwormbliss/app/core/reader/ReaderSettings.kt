package com.bookwormbliss.app.core.reader

/**
 * Reader appearance and behavior settings.
 *
 * These are the reading preferences that affect how the renderer
 * lays out content. They are separate from the app's theme (colors)
 * because reading preferences are per-user, per-book, and content-focused.
 *
 * The [ReaderRenderer] consumes these via [ReaderRenderer.applySettings].
 *
 * @property fontFamily     Font family id (e.g. "serif", "sans", "publisher").
 * @property fontSize       Font size in sp (reader content, not UI).
 * @property lineHeight     Line height multiplier (e.g. 1.6).
 * @property margin         Side margin in dp (reader content area).
 * @property alignment      Text alignment ("left", "justify", "center", "right", "original").
 * @property hyphenation    Enable soft hyphenation.
 * @property pageBottomGuard Reserve space at the bottom for the page indicator.
 * @property pageTurnAnimation Animate page turns.
 * @property themeId        Reader theme id (see ReaderThemes.kt).
 * @property brightness     Screen brightness override (0..1, or null = system default).
 */
data class ReaderSettings(
    val fontFamily: String = DEFAULT_FONT,
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val margin: Int = DEFAULT_MARGIN,
    val alignment: String = DEFAULT_ALIGNMENT,
    val hyphenation: Boolean = false,
    val pageBottomGuard: Boolean = true,
    val pageTurnAnimation: Boolean = true,
    val themeId: String = DEFAULT_THEME,
    val brightness: Float? = null,
) {
    companion object {
        const val DEFAULT_FONT = "serif"
        const val DEFAULT_FONT_SIZE = 24
        const val DEFAULT_LINE_HEIGHT = 1.6f
        const val DEFAULT_MARGIN = 20
        const val DEFAULT_ALIGNMENT = "left"
        const val DEFAULT_THEME = "ivory"

        const val MIN_FONT_SIZE = 24
        const val MAX_FONT_SIZE = 56

        const val MIN_LINE_HEIGHT = 1.0f
        const val MAX_LINE_HEIGHT = 2.6f

        const val MIN_MARGIN = 20
        const val MAX_MARGIN = 72
    }

    /** A layout key that changes whenever the renderer would produce different pagination. */
    val layoutKey: String
        get() = buildString {
            append(fontFamily)
            append("|fs=").append(fontSize)
            append("|lh=").append(lineHeight)
            append("|m=").append(margin)
            append("|a=").append(alignment)
            append("|h=").append(hyphenation)
            append("|bg=").append(pageBottomGuard)
        }
}
