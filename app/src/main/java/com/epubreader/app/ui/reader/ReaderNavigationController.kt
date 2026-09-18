package com.epubreader.app.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import com.epubreader.app.epub.EpubBook
import com.epubreader.app.epub.EpubResourceResolver
import com.epubreader.app.epub.ReaderPageMapping
import kotlin.math.abs

/**
 * ReaderNavigationController — Extracted from ReaderActivity (Phase 6).
 *
 * Owns reading-position navigation: URL resolution, spine jumps, per-page
 * seeking, page turns (with snapshot crossfade), reader history (back/forward),
 * and restore/fragment handling.
 *
 * State remains in the Activity to avoid split-brain issues. The controller
 * reads/writes through [State] and fires cross-controller actions through
 * [Callbacks].
 */
class ReaderNavigationController(
    val config: Config,
    val state: State,
    val callbacks: Callbacks,
) {

    data class Config(
        val webView: android.webkit.WebView,
        val seekChapter: SeekBar,
        val snapshotView: ImageView,
        val readerHistory: View,
        val readerHistoryBack: View,
        val readerHistoryForward: View,
        val handler: Handler,
        val pageTurnDurationMs: Long,
    )

    interface State {
        val epub: EpubBook?
        val spineIndex: Int
        val currentPageInChapter: Int
        val currentScrollRatio: Float
        val chapterPageCounts: IntArray?
        val perPageSeekerActive: Boolean
        val chromeVisible: Boolean
        val overlayVisible: Boolean
        val restoringHistoryLocation: Boolean
        val backHistory: ArrayDeque<ReaderLocation>
        val forwardHistory: ArrayDeque<ReaderLocation>
        val historyCursorLocation: ReaderLocation?
        val pendingFragment: String?
        val pendingTargetPageInChapter: Int?
        val restoreRatio: Float?
        val pendingHistoryCursorAfterRestore: Boolean
        val pageTurnThrottleUntil: Long
        val snapshotForward: Boolean
        val snapshotAnimToken: Int

        var spineIndexVar: Int
        var currentPageInChapterVar: Int
        var currentScrollRatioVar: Float
        var restoreRatioVar: Float?
        var pendingFragmentVar: String?
        var pendingTargetPageInChapterVar: Int?
        var pendingHistoryCursorAfterRestoreVar: Boolean
        var historyCursorLocationVar: ReaderLocation?
        var restoringHistoryLocationVar: Boolean
        var perPageSeekerActiveVar: Boolean
        var pageTurnThrottleUntilVar: Long
        var snapshotForwardVar: Boolean
        var snapshotAnimTokenVar: Int
    }

    interface Callbacks {
        fun loadChapter(index: Int)
        fun pollProgress()
        fun invalidatePendingPolls()
        fun updatePageIndicator()
        fun updateOverallProgress()
        fun updateSectionPages()
        fun updateHistoryUi()
        fun syncSeekBarFromCurrentPage()
        fun currentAbsoluteBookPage(): Int
        fun clearReaderSelection()
        fun stopTtsCompletely()
        fun readerBackgroundColor(): Int
        fun getString(resId: Int, vararg args: Any): String
        val historyPageLabel: (ReaderLocation) -> String
    }

    // ---- Location helpers ----

    fun captureReaderLocation(): ReaderLocation? {
        val book = state.epub ?: return null
        if (state.spineIndex < 0 || state.spineIndex >= book.spine.size) return null
        return ReaderLocation(
            state.spineIndex,
            state.currentPageInChapter.coerceAtLeast(0),
            state.currentScrollRatio.coerceIn(0f, 1f)
        )
    }

    fun locationForAbsolutePage(absolute: Int): ReaderLocation? {
        val counts = state.chapterPageCounts ?: return null
        if (counts.isEmpty() || counts.any { it < 0 }) return null
        val (spine, page) = ReaderPageMapping.spineAndPageFor(counts, absolute)
        val count = counts[spine].coerceAtLeast(1)
        val ratio = if (count > 1) page / (count - 1).toFloat() else 0f
        return ReaderLocation(spine, page, ratio)
    }

    fun sameLocation(a: ReaderLocation, b: ReaderLocation): Boolean =
        a.spineIndex == b.spineIndex &&
                abs(a.ratio - b.ratio) < 0.01f &&
                a.pageInChapter == b.pageInChapter

    // ---- History ----

    fun pushHistory(location: ReaderLocation) {
        val last = state.backHistory.lastOrNull()
        if (last != null && sameLocation(last, location)) return
        state.backHistory.addLast(location)
        state.forwardHistory.clear()
        callbacks.updateHistoryUi()
    }

    fun goBackInReaderHistory() {
        if (state.backHistory.isEmpty()) return
        val target = state.backHistory.removeLast()
        val current = state.historyCursorLocation ?: captureReaderLocation()
        if (current != null) state.forwardHistory.addLast(current)
        state.historyCursorLocationVar = target
        state.restoringHistoryLocationVar = true
        callbacks.updateHistoryUi()
        navigateToReaderLocation(target)
    }

    fun goForwardInReaderHistory() {
        if (state.forwardHistory.isEmpty()) return
        val target = state.forwardHistory.removeLast()
        val current = state.historyCursorLocation ?: captureReaderLocation()
        if (current != null) state.backHistory.addLast(current)
        state.historyCursorLocationVar = target
        state.restoringHistoryLocationVar = true
        callbacks.updateHistoryUi()
        navigateToReaderLocation(target)
    }

    fun clearReaderHistory() {
        state.backHistory.clear()
        state.forwardHistory.clear()
        callbacks.updateHistoryUi()
    }

    fun navigateToReaderLocation(location: ReaderLocation) {
        val count = state.chapterPageCounts?.getOrNull(location.spineIndex)?.coerceAtLeast(1) ?: 1
        val ratio = if (count > 1) location.pageInChapter.coerceIn(0, count - 1) / (count - 1).toFloat() else location.ratio
        state.restoreRatioVar = ratio.coerceIn(0f, 1f)
        state.historyCursorLocationVar = location
        state.pendingHistoryCursorAfterRestoreVar = false
        if (location.spineIndex == state.spineIndex) {
            config.webView.evaluateJavascript(
                "if(window.Caesura){window.Caesura.gotoPage(${location.pageInChapter.coerceAtLeast(0)},false);}"
            ) {
                state.restoringHistoryLocationVar = false
                config.handler.postDelayed({ callbacks.pollProgress() }, 80L)
                callbacks.updateHistoryUi()
            }
        } else {
            callbacks.loadChapter(location.spineIndex)
        }
    }

    fun historyPageLabel(location: ReaderLocation): String {
        val counts = state.chapterPageCounts
        if (counts != null && counts.isNotEmpty() && counts.none { it < 0 }) {
            val prefix = ReaderPageMapping.prefixSums(counts)
            val page = (prefix.getOrNull(location.spineIndex) ?: 0) + location.pageInChapter + 1
            return page.toString()
        }
        return (location.pageInChapter + 1).toString()
    }

    fun updateHistoryUi() {
        val hasBack = state.backHistory.isNotEmpty()
        val hasForward = state.forwardHistory.isNotEmpty()
        val bgColor = callbacks.readerBackgroundColor()
        config.readerHistory.setBackgroundColor(bgColor)
        val showHistory = (hasBack || hasForward) && state.chromeVisible && !state.overlayVisible
        config.readerHistory.visibility = if (showHistory) View.VISIBLE else View.GONE
        config.readerHistoryBack.visibility = if (hasBack) View.VISIBLE else View.INVISIBLE
        config.readerHistoryForward.visibility = if (hasForward) View.VISIBLE else View.INVISIBLE
        if (hasBack) {
            (config.readerHistoryBack as? android.widget.TextView)?.text =
                callbacks.getString(
                    com.epubreader.app.R.string.reader_history_back,
                    historyPageLabel(state.backHistory.last())
                )
        }
        if (hasForward) {
            (config.readerHistoryForward as? android.widget.TextView)?.text =
                callbacks.getString(
                    com.epubreader.app.R.string.reader_history_forward,
                    historyPageLabel(state.forwardHistory.last())
                )
        }
    }

    // ---- Link tap detection ----

    fun tappedLinkOnWebView(e: MotionEvent): Boolean {
        val hit = config.webView.hitTestResult
        val type = hit?.type ?: android.webkit.WebView.HitTestResult.UNKNOWN_TYPE
        return type == android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                type == android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
    }

    // ---- Page turns ----

    fun turnPage(forward: Boolean) {
        if (state.overlayVisible) return
        if (state.chromeVisible) return
        val now = System.currentTimeMillis()
        if (now < state.pageTurnThrottleUntil) return
        state.pageTurnThrottleUntilVar = now + config.pageTurnDurationMs + 40L
        performPageTurn(forward)
    }

    fun performPageTurn(forward: Boolean) {
        capturePageSnapshot(forward)
        val js = if (forward) "(function(){return window.Caesura?window.Caesura.nextPage(false):'not-ready';})()"
        else "(function(){return window.Caesura?window.Caesura.prevPage(false):'not-ready';})()"
        config.webView.evaluateJavascript(js) { result ->
            when (result?.trim('\"')) {
                "next-chapter" -> callbacks.loadChapter(state.spineIndex + 1)
                "prev-chapter" -> {
                    state.restoreRatioVar = 1.0f
                    callbacks.loadChapter(state.spineIndex - 1)
                }
                "not-ready" -> {
                    dismissPageSnapshot(forward)
                    config.handler.postDelayed({ performPageTurn(forward) }, 120)
                }
                else -> dismissPageSnapshot(forward)
            }
        }
    }

    // ---- URL navigation ----

    fun navigateToUrl(url: String) {
        callbacks.clearReaderSelection()
        val book = state.epub ?: return
        val path = url.substringAfter(EpubResourceResolver.VIRTUAL_HOST).trimStart('/')
        val parts = path.split("/", limit = 2)
        if (parts.size < 2) return
        val entryPath = parts[1].substringBefore('#').substringBefore('?')
        val frag = if ('#' in parts[1]) parts[1].substringAfter('#') else null
        val idx = book.spine.indexOfFirst { it.href == entryPath }
        if (idx < 0) return

        config.webView.evaluateJavascript(
            "(function(){if(!window.Caesura) return '';return window.Caesura.currentPage()+','+window.Caesura.ratio();})();"
        ) { result ->
            val values = result?.trim()?.removeSurrounding("\"")?.split(',')
            val actualPage = values?.getOrNull(0)?.toIntOrNull()
            val actualRatio = values?.getOrNull(1)?.toFloatOrNull()
            val current = captureReaderLocation()?.let { location ->
                if (actualPage != null && actualPage >= 0) {
                    location.copy(pageInChapter = actualPage, ratio = actualRatio ?: location.ratio)
                } else location
            }

            if (idx != state.spineIndex) {
                if (!state.restoringHistoryLocation && current != null) {
                    pushHistory(current)
                }
                state.pendingFragmentVar = frag
                state.pendingTargetPageInChapterVar = null
                state.restoreRatioVar = null
                state.pendingHistoryCursorAfterRestoreVar = true
                callbacks.loadChapter(idx)
                return@evaluateJavascript
            }

            val targetExpression = if (frag != null) {
                val safe = frag.replace("'", "")
                "window.Caesura.pageForElementById('$safe')"
            } else "0"

            config.webView.evaluateJavascript(
                "if(window.Caesura){window.Caesura.currentPage() + '|' + $targetExpression;}"
            ) { targetResult ->
                val targetValues = targetResult?.trim()?.removeSurrounding("\"")?.split('|')
                val currentPage = targetValues?.getOrNull(0)?.toIntOrNull() ?: actualPage
                val targetPage = targetValues?.getOrNull(1)?.toIntOrNull()

                if (targetPage == null || targetPage < 0) return@evaluateJavascript

                if (!state.restoringHistoryLocation && current != null &&
                    currentPage != null && targetPage != currentPage
                ) {
                    pushHistory(current.copy(pageInChapter = currentPage))
                }

                if (frag != null) {
                    state.pendingFragmentVar = frag
                    state.pendingTargetPageInChapterVar = null
                    state.restoreRatioVar = null
                    state.pendingHistoryCursorAfterRestoreVar = true
                    config.webView.evaluateJavascript(
                        "if(window.Caesura){window.Caesura.gotoElementById('$frag');}"
                    ) {
                        config.webView.evaluateJavascript(
                            "if(window.Caesura){window.Caesura.currentPage()+','+window.Caesura.ratio();}"
                        ) { targetLocationResult ->
                            val targetValues2 = targetLocationResult?.trim()?.removeSurrounding("\"")?.split(',')
                            val targetActualPage = targetValues2?.getOrNull(0)?.toIntOrNull()
                            val targetActualRatio = targetValues2?.getOrNull(1)?.toFloatOrNull()
                            if (state.pendingHistoryCursorAfterRestore && targetActualPage != null) {
                                state.historyCursorLocationVar = ReaderLocation(state.spineIndex, targetActualPage, targetActualRatio ?: 0f)
                                state.pendingHistoryCursorAfterRestoreVar = false
                            }
                            config.handler.postDelayed({ callbacks.pollProgress() }, 80L)
                        }
                    }
                } else {
                    state.pendingFragmentVar = null
                    state.pendingTargetPageInChapterVar = targetPage
                    state.pendingHistoryCursorAfterRestoreVar = true
                    capturePageSnapshot(forward = false)
                    applyPendingFragmentOrRestore()
                }
            }
        }
    }

    fun goToSpine(index: Int) {
        callbacks.clearReaderSelection()
        val book = state.epub ?: return
        if (index in book.spine.indices && index != state.spineIndex) {
            state.pendingFragmentVar = null
            callbacks.loadChapter(index)
        }
    }

    // ---- Per-page seeker ----

    fun enablePerPageSeeker() {
        val counts = state.chapterPageCounts ?: return
        if (counts.isEmpty() || counts.any { it < 0 }) return
        val total = ReaderPageMapping.totalPages(counts)
        if (total <= 1) return
        state.perPageSeekerActiveVar = true
        config.seekChapter.max = total - 1
        callbacks.syncSeekBarFromCurrentPage()
    }

    fun syncSeekBarFromCurrentPage() {
        if (!state.perPageSeekerActive) return
        val counts = state.chapterPageCounts ?: return
        if (counts.isEmpty() || counts.any { it < 0 }) return
        val abs = callbacks.currentAbsoluteBookPage()
        config.seekChapter.progress = abs.coerceIn(0, config.seekChapter.max)
    }

    fun seekToAbsolutePage(absolute: Int) {
        val counts = state.chapterPageCounts ?: return
        if (counts.isEmpty() || counts.any { it < 0 }) return
        val (targetSpine, pageInSpine) = ReaderPageMapping.spineAndPageFor(counts, absolute)
        val pagesInSpine = counts[targetSpine].coerceAtLeast(1)
        val ratio = if (pagesInSpine > 1) pageInSpine / (pagesInSpine - 1).toFloat() else 0f
        if (targetSpine == state.spineIndex) {
            capturePageSnapshot(forward = absolute >= callbacks.currentAbsoluteBookPage())
            config.webView.evaluateJavascript(
                "if(window.Caesura){window.Caesura.gotoPage(${pageInSpine.coerceAtLeast(0)},false);}"
            ) {
                state.currentPageInChapterVar = pageInSpine.coerceIn(0, pagesInSpine - 1)
                state.currentScrollRatioVar = ratio.coerceIn(0f, 1f)
                callbacks.updateOverallProgress()
                callbacks.updatePageIndicator()
                callbacks.updateSectionPages()
                dismissPageSnapshot()
                config.handler.postDelayed({ callbacks.pollProgress() }, 80L)
            }
        } else {
            state.pendingTargetPageInChapterVar = pageInSpine
            state.restoreRatioVar = ratio
            goToSpine(targetSpine)
        }
    }

    // ---- Snapshot / restore ----

    fun capturePageSnapshot(forward: Boolean = true) {
        state.snapshotForwardVar = forward
        state.snapshotAnimTokenVar++
        val view = config.webView
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return
        val snapshot = config.snapshotView
        snapshot.animate().cancel()
        val previous = (snapshot.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            previous?.recycle()
            return
        }
        try {
            val canvas = Canvas(bmp)
            view.draw(canvas)
            snapshot.setImageDrawable(null)
            snapshot.setImageBitmap(bmp)
            snapshot.alpha = 1f
            snapshot.translationX = 0f
            snapshot.visibility = View.VISIBLE
            previous?.recycle()
        } catch (_: Exception) {
            bmp.recycle()
        }
    }

    fun dismissPageSnapshot(forward: Boolean? = null) {
        val snapshot = config.snapshotView
        val token = state.snapshotAnimToken
        val direction = forward ?: state.snapshotForward
        snapshot.animate().cancel()
        snapshot.animate()
            .translationX(if (direction) -snapshot.width.toFloat() else snapshot.width.toFloat())
            .alpha(0f)
            .setDuration(config.pageTurnDurationMs)
            .withEndAction {
                if (token != state.snapshotAnimToken) return@withEndAction
                snapshot.visibility = View.GONE
                snapshot.translationX = 0f
                snapshot.alpha = 1f
                (snapshot.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
                snapshot.setImageDrawable(null)
            }
            .start()
    }

    fun clearSnapshot(v: ImageView) {
        v.animate().cancel()
        v.visibility = View.GONE
        v.translationX = 0f
        v.alpha = 1f
        (v.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
        v.setImageDrawable(null)
    }

    fun applyPendingFragmentOrRestore() {
        val frag = state.pendingFragment
        val targetPage = state.pendingTargetPageInChapter
        val ratio = state.restoreRatio
        when {
            frag != null -> {
                config.webView.evaluateJavascript(
                    "if(window.Caesura){window.Caesura.gotoElementById('${frag.replace("'", "")}');}"
                ) {
                    state.pendingFragmentVar = null
                    config.handler.postDelayed({ callbacks.pollProgress() }, 80L)
                }
            }
            targetPage != null -> {
                config.webView.evaluateJavascript(
                    "if(window.Caesura){window.Caesura.gotoPage(${targetPage.coerceAtLeast(0)},false);}"
                ) {
                    state.pendingTargetPageInChapterVar = null
                    config.handler.postDelayed({ callbacks.pollProgress() }, 80L)
                }
            }
            ratio != null && ratio > 0f -> {
                config.webView.evaluateJavascript(
                    "if(window.Caesura){window.Caesura.scrollToRatio($ratio);}"
                ) {
                    state.restoreRatioVar = null
                    config.handler.postDelayed({ callbacks.pollProgress() }, 80L)
                }
            }
            else -> {
                config.handler.postDelayed({ callbacks.pollProgress() }, 80L)
            }
        }
    }
}
