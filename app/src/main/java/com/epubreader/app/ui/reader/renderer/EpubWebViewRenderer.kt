package com.epubreader.app.ui.reader.renderer

import com.epubreader.app.core.epub.EpubDocument
import com.epubreader.app.core.reader.ReaderDocument
import com.epubreader.app.core.reader.ReaderPosition
import com.epubreader.app.core.reader.ReaderProgress
import com.epubreader.app.core.reader.ReaderRenderer
import com.epubreader.app.core.reader.ReaderRendererCallback
import com.epubreader.app.core.reader.ReaderSettings
import com.epubreader.app.core.reader.ReaderTextSelection
import com.epubreader.app.epub.ReaderSelectionLocator
import com.epubreader.app.ui.reader.ReaderNavigationController
import com.epubreader.app.ui.reader.ReaderWebViewController
import com.epubreader.app.epub.EpubBook
import com.epubreader.app.epub.ReaderPageMapping

/**
 * WebView-backed implementation of [ReaderRenderer] for EPUB documents.
 *
 * This is a thin adapter that delegates to the existing controllers
 * ([ReaderWebViewController], [ReaderNavigationController]) rather than
 * duplicating their logic. It provides the architectural seam the
 * blueprint calls for: the UI layer can interact with a [ReaderRenderer]
 * without knowing about WebView, JS injection, pagination scripts, or
 * DOM path calculation.
 *
 * Over time, the rendering logic currently inside [ReaderWebViewController]
 * and [ReaderNavigationController] can be extracted into this class (or
 * a core-layer EpubRenderer), and the controllers can be thinned down
 * to pure UI wiring. For now, this adapter preserves all existing
 * behavior while giving the app the contract-based seam.
 *
 * Lives in `ui/reader/renderer/` (not `core/epub/`) because it depends
 * on Android WebView and the UI-layer controllers. Once the WebView
 * logic is fully extracted, it can move toward `core/epub/EpubRenderer`.
 *
 * @param webView       The WebView used for rendering.
 * @param webViewController  Existing WebView controller (JS injection, selection capture).
 * @param navigationController  Existing navigation controller (page turns, spine jumps).
 * @param epubProvider  Returns the current EpubBook, or null if not loaded.
 * @param spineIndexProvider  Returns the current spine index.
 * @param pageCountsProvider  Returns per-spine page counts (may be null during measurement).
 * @param currentPageProvider  Returns the current page within the spine item.
 * @param scrollRatioProvider  Returns the current scroll ratio (0..1) within the spine item.
 * @param settingsProvider  Returns the current ReaderSettings.
 * @param loadChapter  Lambda to load a chapter by spine index (delegates to Activity).
 * @param applySettings  Lambda to apply reader settings (CSS injection, theme change).
 */
class EpubWebViewRenderer(
    private val webView: android.webkit.WebView,
    private val webViewController: ReaderWebViewController,
    private val navigationController: ReaderNavigationController,
    private val epubProvider: () -> EpubBook?,
    private val spineIndexProvider: () -> Int,
    private val pageCountsProvider: () -> IntArray?,
    private val currentPageProvider: () -> Int,
    private val scrollRatioProvider: () -> Float,
    private val settingsProvider: () -> ReaderSettings,
    private val loadChapter: (Int) -> Unit,
    private val applySettings: (ReaderSettings) -> Unit,
    private val restorePosition: (ReaderPosition) -> Unit = {},
) : ReaderRenderer {

    private var callback: ReaderRendererCallback? = null
    private var pendingSelectionCallback: ((ReaderTextSelection?) -> Unit)? = null

    // ---- ReaderRenderer implementation ----

    override fun load(document: ReaderDocument) {
        // The actual document loading (WebView.loadUrl with the spine item)
        // is handled by the Activity's loadChapter(). Here we just trigger
        // the initial chapter load if the document is an EpubDocument.
        if (document is EpubDocument && document.spineSize > 0) {
            loadChapter(0)
        }
    }

    override fun nextPage(): Boolean {
        // Delegate to the existing navigation controller so snapshot animation,
        // progress polling, and chapter-edge behavior are preserved.
        navigationController.turnPage(forward = true)
        return true
    }

    override fun previousPage(): Boolean {
        navigationController.turnPage(forward = false)
        return true
    }

    override fun currentPosition(): ReaderPosition {
        val epub = epubProvider() ?: return ReaderPosition.START
        val spineIndex = spineIndexProvider()
        val pageInChapter = currentPageProvider()
        val ratio = scrollRatioProvider()

        return ReaderPosition(
            spineIndex = spineIndex,
            scrollRatio = ratio,
            pageInChapter = pageInChapter,
        ).clamped(epub.spine.size)
    }

    override fun restore(position: ReaderPosition) {
        // Delegate to the Activity's existing restore mechanism, which handles
        // cross-spine loads, ratio restoration via pendingFragment/restoreRatio,
        // and the measurement pipeline. This avoids reimplementing restore
        // logic that already exists and is tested.
        restorePosition.invoke(position)
    }

    override fun applySettings(settings: ReaderSettings) {
        applySettings.invoke(settings)
    }

    override fun currentPage(): Int {
        return currentPageProvider()
    }

    override fun pageCount(): Int {
        val counts = pageCountsProvider() ?: return 0
        val spine = spineIndexProvider()
        return counts.getOrNull(spine) ?: 0
    }

    override fun goToSpine(index: Int) {
        navigationController.goToSpine(index)
    }

    override fun goToAbsolutePage(absolutePage: Int) {
        navigationController.seekToAbsolutePage(absolutePage)
    }

    override fun release() {
        callback = null
        pendingSelectionCallback = null
        webViewController.bumpGeneration()
    }

    // ---- Selection ----

    /**
     * Capture the current text selection asynchronously.
     * The result is delivered as a [ReaderTextSelection] (the canonical
     * core-layer model), not a [ReaderSelectionLocator].
     */
    fun captureSelection(onCaptured: (ReaderTextSelection?) -> Unit) {
        pendingSelectionCallback = onCaptured
        webViewController.captureCurrentSelection { locator ->
            val selection = locator?.let { ReaderTextSelection.fromLocator(it) }
            pendingSelectionCallback?.invoke(selection)
            pendingSelectionCallback = null

            // Notify the callback listener
            selection?.let { callback?.onSelection(it) }
        }
    }

    // ---- Callback registration ----

    fun setRendererCallback(callback: ReaderRendererCallback?) {
        this.callback = callback
    }

    /**
     * Compute the current [ReaderProgress] from the renderer's state.
     */
    fun currentProgress(): ReaderProgress {
        val epub = epubProvider() ?: return ReaderProgress.ZERO
        val position = currentPosition()
        val pageCounts = pageCountsProvider()
        return ReaderProgress.compute(position, epub.spine.size, pageCounts)
    }

    /**
     * Total page count across all spine items.
     */
    fun totalPages(): Int {
        val counts = pageCountsProvider() ?: return 0
        return ReaderPageMapping.totalPages(counts)
    }
}
