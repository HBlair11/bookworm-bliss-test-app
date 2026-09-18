package com.epubreader.app.ui.reader

import android.os.Handler
import android.webkit.WebView
import androidx.lifecycle.LifecycleCoroutineScope
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.epub.EpubBook
import com.epubreader.app.epub.ReaderPageMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ReaderProgressController — Extracted from ReaderActivity (Phase 6).
 *
 * Owns reading-progress mechanics: WebView polling for current page/ratio,
 * progress calculation, debounced persistence to Room, and page/section
 * indicator updates.
 *
 * The controller owns progress-specific state (request tokens, pending
 * persistence values, persistence tracking). Cross-cutting reader state
 * (spineIndex, currentPageInChapter, chapterPageCounts, etc.) is accessed
 * through lambda providers so the Activity remains the single source of truth
 * for reader position. UI updates flow through direct view references passed
 * in the constructor.
 *
 * @param webView          Main reader WebView for JavaScript evaluation.
 * @param tvPercent        Percent display view.
 * @param tvPageIndicator  Page indicator view (bottom bar).
 * @param tvPageInfo       Page info view (top bar).
 * @param tvSectionPages   Section pages view (top bar).
 * @param handler          Main-thread handler for debounced persistence.
 * @param lifecycleScope   Coroutine scope for IO database writes.
 * @param db               Room database for progress persistence.
 * @param bookId           Current book ID.
 * @param getString        Lambda for localized string resources.
 */
class ReaderProgressController(
    private val webView: WebView,
    private val tvPercent: android.widget.TextView,
    private val tvPageIndicator: android.widget.TextView,
    private val tvPageInfo: android.widget.TextView,
    private val tvSectionPages: android.widget.TextView,
    private val handler: Handler,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val db: AppDatabase,
    private val bookId: Long,
    private val getString: (Int, Array<Any>) -> String,
    // State providers (read access to Activity state)
    private val getSpineIndex: () -> Int,
    private val getEpub: () -> EpubBook?,
    private val getCurrentPageInChapter: () -> Int,
    private val getPagesInChapter: () -> Int,
    private val getCurrentScrollRatio: () -> Float,
    private val getChapterPageCounts: () -> IntArray?,
    private val getUserSeeking: () -> Boolean,
    private val getPendingExactSeekLocation: () -> ReaderLocation?,
    private val getExactSeekUiLocation: () -> ReaderLocation?,
    private val getOverlayVisible: () -> Boolean,
    private val getTocSections: () -> List<Pair<Int, String>>,
    private val getSectionLabel: () -> String,
    // State setters (write access to Activity state)
    private val setCurrentPageInChapter: (Int) -> Unit,
    private val setPagesInChapter: (Int) -> Unit,
    private val setCurrentScrollRatio: (Float) -> Unit,
    private val setChapterPageCountsSpine: (Int, Int) -> Unit,
    private val clearExactSeekLocation: () -> Unit,
    private val setExactSeekUiLocation: (ReaderLocation?) -> Unit,
    // Cross-controller callbacks
    private val onSyncSeekBar: () -> Unit,
) {

    // ---- Progress-specific state (owned by this controller) ----

    private var progressRequestToken = 0
    private var pendingProgressValue = 0f
    private var pendingProgressSpine = 0
    private var pendingProgressRatio = 0f
    private var lastPersistedProgress: Float? = null
    private var lastPersistedSpine = -1
    private var lastPersistedRatio = 0f
    private var lastProgressPersistAt = 0L

    private val persistProgressRunnable = Runnable {
        persistProgressNow()
    }

    /** Invalidate any in-flight pollProgress() callback so a stale WebView
     *  response cannot overwrite a newer seek/navigation result. */
    fun invalidatePendingPolls() {
        progressRequestToken++
    }

    /** Cancel any pending debounced progress write and flush immediately. */
    fun flushProgressNow() {
        handler.removeCallbacks(persistProgressRunnable)
        persistProgressNow()
    }

    // ---- Public API ----

    /** Poll the WebView for current page/pageCount/ratio. Called on a timer. */
    fun pollProgress() {
        if (getOverlayVisible()) return
        if (getUserSeeking()) return

        val requestToken = ++progressRequestToken

        webView.evaluateJavascript(
            "(function(){if(!window.Caesura) return '';return window.Caesura.currentPage()+','+window.Caesura.pageCount()+','+window.Caesura.ratio();})();"
        ) { result ->
            if (requestToken != progressRequestToken) return@evaluateJavascript
            if (getUserSeeking()) return@evaluateJavascript
            parseProgress(result)
        }
    }

    /** Parse the JS poll result and update reader state + UI. */
    fun parseProgress(result: String?) {
        if (result == null || result == "null" || result.isBlank()) return
        try {
            val parts = result.trim('\"').split(",")
            if (parts.size != 3) return
            val reportedPage = parts[0].toIntOrNull() ?: 0
            val reportedCount = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: 1

            // Do not let a stale poll overwrite a user's exact seek.
            getPendingExactSeekLocation()?.let { expected ->
                if (expected.spineIndex == getSpineIndex() && reportedPage == expected.pageInChapter) {
                    clearExactSeekLocation()
                    setExactSeekUiLocation(null)
                } else {
                    return
                }
            }

            setCurrentPageInChapter(reportedPage)
            setPagesInChapter(reportedCount)
            getChapterPageCounts()?.let { counts ->
                val spine = getSpineIndex()
                if (spine in counts.indices) setChapterPageCountsSpine(spine, reportedCount)
            }
            val r = parts[2].toFloatOrNull() ?: return
            setCurrentScrollRatio(r.coerceIn(0f, 1f))
            updateOverallProgress()
            updatePageIndicator()
            updateSectionPages()
            onSyncSeekBar()
        } catch (_: Exception) {
        }
    }

    /** Calculate and display overall book progress, scheduling persistence. */
    fun updateOverallProgress() {
        val book = getEpub() ?: return
        if (book.spine.isEmpty()) return

        val counts = getChapterPageCounts()
        val progress = if (counts != null && counts.isNotEmpty() && counts.none { it < 0 }) {
            val total = ReaderPageMapping.totalPages(counts)
            val absolute = getExactSeekUiLocation()?.let { target ->
                val prefix = ReaderPageMapping.prefixSums(counts)
                (prefix.getOrNull(target.spineIndex) ?: 0) + target.pageInChapter
            } ?: currentAbsoluteBookPage()
            if (total <= 1) 1f else
                (absolute.coerceIn(0, total - 1) / (total - 1).toFloat()).coerceIn(0f, 1f)
        } else {
            ((getSpineIndex() + getCurrentScrollRatio()) / book.spine.size).toFloat().coerceIn(0f, 1f)
        }
        tvPercent.text = "${(progress * 100).toInt()}%"
        pendingProgressValue = progress
        pendingProgressSpine = getSpineIndex()
        pendingProgressRatio = getCurrentScrollRatio()

        val now = System.currentTimeMillis()
        val materiallyChanged =
            lastPersistedProgress == null ||
                    abs(progress - (lastPersistedProgress ?: 0f)) >= 0.001f ||
                    getSpineIndex() != lastPersistedSpine ||
                    abs(getCurrentScrollRatio() - lastPersistedRatio) >= 0.01f

        if (materiallyChanged || now - lastProgressPersistAt >= 5000L) {
            handler.removeCallbacks(persistProgressRunnable)
            handler.postDelayed(persistProgressRunnable, 1200L)
        }
    }

    /** Persist current progress to Room (debounced via [persistProgressRunnable]). */
    fun persistProgressNow() {
        if (bookId < 0L) return
        val progress = pendingProgressValue
        val spine = pendingProgressSpine
        val ratio = pendingProgressRatio
        val now = System.currentTimeMillis()
        lastPersistedProgress = progress
        lastPersistedSpine = spine
        lastPersistedRatio = ratio
        lastProgressPersistAt = now
        lifecycleScope.launch(Dispatchers.IO) {
            db.bookDao().updateProgress(bookId, progress, spine, ratio, now)
        }
    }

    /** Update the page X/Y indicator. */
    fun updatePageIndicator() {
        val counts = getChapterPageCounts()
        if (counts == null || counts.isEmpty() || counts.any { it < 0 }) {
            setPageText("…")
            return
        }
        val total = ReaderPageMapping.totalPages(counts)
        val displayAbsolute = getExactSeekUiLocation()?.let { target ->
            if (target.spineIndex in counts.indices) {
                val prefix = ReaderPageMapping.prefixSums(counts)
                (prefix.getOrNull(target.spineIndex) ?: 0) +
                    target.pageInChapter.coerceIn(0, counts[target.spineIndex].coerceAtLeast(1) - 1)
            } else null
        } ?: currentAbsoluteBookPage()
        val currentBookPage = (displayAbsolute + 1).coerceIn(1, total.coerceAtLeast(1))
        setPageText(getString(com.epubreader.app.R.string.reader_page_of_pages, arrayOf(currentBookPage, total)))
    }

    /** Set text on both page indicator views. */
    fun setPageText(text: String) {
        tvPageIndicator.text = text
        tvPageInfo.text = text
    }

    /** Current absolute book page (across all spine items). */
    fun currentAbsoluteBookPage(): Int {
        val counts = getChapterPageCounts() ?: return 0
        val spine = getSpineIndex()
        if (counts.isEmpty() || spine !in counts.indices) return 0
        val prefix = ReaderPageMapping.prefixSums(counts)
        val pageCount = counts[spine].coerceAtLeast(1)
        val page = getCurrentPageInChapter().coerceIn(0, pageCount - 1)
        return (prefix.getOrNull(spine) ?: 0) + page
    }

    /** Spine index range of the current TOC section. */
    fun sectionSpineRange(): IntRange {
        val book = getEpub() ?: return 0..0
        val spine = getSpineIndex()
        val sections = getTocSections()
        val start = sections.lastOrNull { it.first <= spine }?.first ?: 0
        val nextStart = sections.firstOrNull { it.first > start }?.first ?: book.spine.size
        val endExclusive = nextStart.coerceAtLeast(start + 1)
        return start until endExclusive
    }

    /** Update the top-bar section label + page X/Y within that section. */
    fun updateSectionPages() {
        val label = getSectionLabel()
        val counts = getChapterPageCounts()
        val spine = getSpineIndex()
        val text = if (counts == null || counts.isEmpty() || counts.any { it < 0 }) {
            val cur = (getCurrentPageInChapter() + 1).coerceAtLeast(1)
            "$label - $cur/${getPagesInChapter()}"
        } else {
            val range = sectionSpineRange()
            val total = range.sumOf { counts.getOrNull(it)?.coerceAtLeast(0) ?: 0 }.coerceAtLeast(1)
            val prior = (range.first until spine).sumOf { counts.getOrNull(it)?.coerceAtLeast(0) ?: 0 }
            val current = getCurrentPageInChapter().coerceIn(
                0, counts.getOrNull(spine)?.coerceAtLeast(1)?.minus(1) ?: 0
            )
            val cur = (prior + current + 1).coerceIn(1, total)
            "$label - $cur/$total"
        }
        tvSectionPages.text = text
    }
}
