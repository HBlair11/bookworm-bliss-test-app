package com.bookwormbliss.app.core.reader

/**
 * Information about the renderer's viewport — the dimensions and
 * constraints that affect pagination.
 *
 * The renderer uses this to compute page counts and lay out content.
 * When the viewport changes (rotation, settings change), the layout
 * key changes and cached page counts are invalidated.
 *
 * @property widthPx       Content width in pixels.
 * @property heightPx      Content height in pixels.
 * @property density       Screen density (for dp↔px conversion).
 * @property columnGapPx   Gap between columns in the multi-column layout.
 * @property guardPx       Bottom guard height (for page indicator).
 * @property topGuardPx    Top guard height.
 */
data class ReaderViewport(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val columnGapPx: Int = 0,
    val guardPx: Int = 0,
    val topGuardPx: Int = 0,
) {
    /** Content width in dp. */
    val widthDp: Float get() = widthPx / density

    /** Content height in dp. */
    val heightDp: Float get() = heightPx / density

    /** A key that changes when the viewport dimensions change. */
    val dimensionKey: String
        get() = "${widthPx}x${heightPx}d${density}g${guardPx}t${topGuardPx}"
}
