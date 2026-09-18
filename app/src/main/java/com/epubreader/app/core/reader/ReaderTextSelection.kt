package com.epubreader.app.core.reader

/**
 * Format-agnostic text selection — the payload produced when a user
 * selects text in the reader.
 *
 * This is the core-layer model that the renderer contract uses.
 * The legacy [com.epubreader.app.epub.ReaderSelectionLocator] is an
 * EPUB/WebView-specific implementation that can be adapted into this
 * model. This keeps the core/reader package free of any EPUB or
 * WebView dependency.
 *
 * @property text         The selected text (raw, untrimmed — offsets are
 *                       based on this; use [displayText] for UI display).
 * @property spineHref    The spine item href where the selection occurred.
 * @property startPath    DOM path to the selection start (format-specific).
 * @property startOffset  Character offset at the selection start.
 * @property endPath      DOM path to the selection end.
 * @property endOffset    Character offset at the selection end.
 * @property prefix       Text immediately before the selection (for anchor matching).
 * @property suffix       Text immediately after the selection (for anchor matching).
 * @property rectLeft     Selection bounding box left (CSS px, renderer-local).
 * @property rectTop      Selection bounding box top.
 * @property rectRight    Selection bounding box right.
 * @property rectBottom   Selection bounding box bottom.
 */
data class ReaderTextSelection(
    val text: String,
    val spineHref: String,
    val startPath: String,
    val startOffset: Int,
    val endPath: String,
    val endOffset: Int,
    val prefix: String = "",
    val suffix: String = "",
    val rectLeft: Int = 0,
    val rectTop: Int = 0,
    val rectRight: Int = 0,
    val rectBottom: Int = 0,
) {
    val hasRect: Boolean get() = rectRight > rectLeft && rectBottom > rectTop

    /** Trimmed text for display (copy, share, define, highlight list).
     *  The raw [text] is stored untrimmed so DOM offsets remain consistent
     *  with the original range; display surfaces should use this. */
    val displayText: String get() = text.trim()

    /** Convert to a canonical [ReaderPosition] at the selection start. */
    fun toPosition(spineIndex: Int): ReaderPosition = ReaderPosition(
        spineIndex = spineIndex,
        domAnchor = startPath,
        charOffset = startOffset,
        fragment = spineHref,
    )

    companion object {
        /**
         * Adapt from the legacy EPUB-specific [ReaderSelectionLocator].
         * This bridge lets existing code produce core-layer selections
         * without the core package depending on the epub package.
         */
        fun fromLocator(
            locator: com.epubreader.app.epub.ReaderSelectionLocator,
        ): ReaderTextSelection = ReaderTextSelection(
            text = locator.text,
            spineHref = locator.spineHref,
            startPath = locator.startPath,
            startOffset = locator.startOffset,
            endPath = locator.endPath,
            endOffset = locator.endOffset,
            prefix = locator.prefix,
            suffix = locator.suffix,
            rectLeft = locator.rectLeft,
            rectTop = locator.rectTop,
            rectRight = locator.rectRight,
            rectBottom = locator.rectBottom,
        )
    }
}
