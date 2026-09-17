package com.bookwormbliss.app.core.reader

/**
 * The renderer contract — the interface between the reader UI and
 * the underlying rendering technology (Chromium/WebView for EPUB).
 *
 * The UI interacts with this interface; it does not know:
 *  - how XHTML is loaded
 *  - how CSS is injected
 *  - how pagination works
 *  - how DOM positions are calculated
 *  - how resources are resolved
 *  - how an EPUB CFI is generated
 *  - how Chromium calculates its viewport
 *
 * That's renderer responsibility. The actual implementation can
 * evolve (e.g. from WebView to a custom layout engine) without
 * changing the UI.
 *
 * The current EPUB implementation lives in
 * [com.bookwormbliss.app.core.epub.EpubRenderer] (to be created).
 *
 * Future formats implement this same interface:
 *  - EpubDocument → EpubRenderer → Chromium/WebView
 *  - PdfDocument  → PdfRenderer  → PdfRenderer (Android system)
 *  - CbzDocument  → CbzRenderer  → ImageView pager
 */
interface ReaderRenderer {

    /** Load a document into the renderer. */
    fun load(document: ReaderDocument)

    /** Navigate to the next page. Returns false if at the end. */
    fun nextPage(): Boolean

    /** Navigate to the previous page. Returns false if at the start. */
    fun previousPage(): Boolean

    /** Get the current position in the document. */
    fun currentPosition(): ReaderPosition

    /** Restore a previously saved position. */
    fun restore(position: ReaderPosition)

    /** Apply reader settings (font, size, margins, etc.). May trigger reflow. */
    fun applySettings(settings: ReaderSettings)

    /** Get the current page index within the current spine item. */
    fun currentPage(): Int

    /** Get the total page count for the current spine item. */
    fun pageCount(): Int

    /** Navigate to a specific spine item by index. */
    fun goToSpine(index: Int)

    /** Navigate to an absolute page across the whole book. */
    fun goToAbsolutePage(absolutePage: Int)

    /** Release renderer resources. */
    fun release()
}

/**
 * Callbacks the renderer fires back to the UI layer.
 * The renderer should never hold a reference to an Activity;
 * it communicates through this interface.
 */
interface ReaderRendererCallback {

    /** Called when a page navigation completes (page turn, seek, restore). */
    fun onPageChanged(position: ReaderPosition, progress: ReaderProgress)

    /** Called when the renderer finishes loading a spine item. */
    fun onSpineLoaded(spineIndex: Int, pageCount: Int)

    /** Called when the renderer measures page counts for all spine items. */
    fun onPageCountsMeasured(pageCounts: IntArray)

    /** Called when an internal link is tapped within the content. */
    fun onInternalLink(href: String)

    /** Called when a text selection occurs. */
    fun onSelection(selection: ReaderTextSelection)

    /** Called when a highlight decoration is tapped. */
    fun onHighlightTap(highlightId: Long)

    /** Called when the renderer encounters an error. */
    fun onError(error: Throwable)
}
