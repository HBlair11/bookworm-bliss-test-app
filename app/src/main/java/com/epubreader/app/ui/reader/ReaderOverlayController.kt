package com.epubreader.app.ui.reader

import android.view.LayoutInflater
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.R
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookmarkEntity
import com.epubreader.app.data.HighlightEntity
import com.epubreader.app.epub.EpubBook
import com.epubreader.app.epub.EpubResourceResolver
import com.epubreader.app.epub.ReaderSelectionLocator
import com.epubreader.app.features.annotation.BookmarkService
import com.epubreader.app.features.annotation.HighlightService
import com.epubreader.app.features.search.ReaderSearchService
import com.epubreader.app.ui.BookmarkAdapter
import com.epubreader.app.ui.HighlightListAdapter
import com.epubreader.app.ui.SearchResultAdapter
import com.epubreader.app.ui.TocAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * ReaderOverlayController — Extracted from ReaderActivity (Phase 6).
 *
 * Owns overlay management for the reader: the TOC/Bookmarks/Highlights overlay,
 * the search overlay, and all highlight lifecycle operations (color picker,
 * injection, navigation, note sheet, decoration removal).
 *
 * State remains in the Activity to avoid split-brain issues with other
 * controllers that read it. The controller reads/writes through [State] and
 * fires cross-controller actions through [Callbacks].
 *
 * @param config     View references and context.
 * @param state      Read/write access to overlay and reader state.
 * @param callbacks  Cross-controller navigation, history, and DB callbacks.
 */
class ReaderOverlayController(
    val config: Config,
    val state: State,
    val callbacks: Callbacks,
) {

    data class Config(
        val activity: android.app.Activity,
        val webView: android.webkit.WebView,
        val tocBookmarkOverlay: View,
        val searchOverlay: View,
        val overlayContent: ViewGroup,
        val overlayTabGroup: com.google.android.material.button.MaterialButtonToggleGroup,
        val searchContent: ViewGroup,
        val searchEdit: EditText,
        val tvPageIndicator: View,
        val topBar: View,
        val bottomBar: View,
        val root: View,
        val handler: android.os.Handler,
        val readerHistory: View,
        // Phase 7 feature services — the controller renders, services own logic.
        val bookmarkService: BookmarkService,
        val highlightService: HighlightService,
        val searchService: ReaderSearchService,
    )

    interface State {
        val epub: EpubBook?
        val bookId: Long
        val bookEntity: BookEntity?
        val spineIndex: Int
        val currentPageInChapter: Int
        val currentScrollRatio: Float
        val pagesInChapter: Int
        val restoringHistoryLocation: Boolean
        val chromeVisible: Boolean
        val overlayVisible: Boolean
        val backHistory: ArrayDeque<ReaderLocation>
        val forwardHistory: ArrayDeque<ReaderLocation>
        val historyCursorLocation: ReaderLocation?

        var bookmarksTabActive: Boolean
        var activeOverlayTab: Int
        var tocRv: RecyclerView?
        var tocEmpty: TextView?
        var bookmarkRv: RecyclerView?
        var bookmarkEmpty: TextView?
        var bookmarkAdapter: BookmarkAdapter?
        var bookmarkObserverStarted: Boolean
        var highlightRv: RecyclerView?
        var highlightEmpty: TextView?
        var highlightObserverStarted: Boolean
        var searchRv: RecyclerView?
        var searchEmpty: TextView?
        var searchAdapter: SearchResultAdapter?
        var searchEdit: EditText?

        var pendingBookmarkAnchor: String?
        var pendingBookmarkFallbackPage: Int
        var pendingBookmarkIsWholePage: Boolean
        var pendingHighlightId: Long?
        var pendingHighlightHistoryLocation: ReaderLocation?
        var pendingFragment: String?
        var pendingTargetPageInChapter: Int?
        var restoreRatio: Float?
        var pendingHistoryCursorAfterRestore: Boolean
        var historyCursorLocationVar: ReaderLocation?
        var restoringHistoryLocationVar: Boolean
    }

    interface Callbacks {
        fun loadChapter(index: Int)
        fun navigateToUrl(url: String)
        fun clearReaderSelection()
        fun hideTtsOverlay()
        fun toggleChrome()
        fun updateHistoryUi()
        fun updatePageIndicator()
        fun captureCurrentSelection(onCaptured: ((ReaderSelectionLocator?) -> Unit)? = null)
        fun captureReaderLocation(): ReaderLocation?
        fun pushHistory(location: ReaderLocation)
        fun capturePageSnapshot(forward: Boolean)
        fun applyPendingFragmentOrRestore()
        fun pollProgress()
        fun sectionLabel(): String
        fun themeColor(attr: Int): Int
        fun getString(resId: Int, vararg args: Any): String
        val lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope
    }

    // ---- Highlight color constants ----

    companion object {
        // Highlight colors and their CSS form live in HighlightService
        // (single source of truth since Phase 7).
    }

    // ---- TOC / Bookmarks / Highlights overlay ----

    fun showTocBookmarks(selectBookmarks: Boolean = false, selectHighlights: Boolean = false) {
        callbacks.clearReaderSelection()
        val book = state.epub ?: return
        val root = config.overlayContent
        if (state.tocRv == null) {
            val tocView = LayoutInflater.from(config.activity).inflate(R.layout.overlay_list, root, false)
            state.tocRv = tocView.findViewById(R.id.recycler)
            state.tocEmpty = tocView.findViewById(R.id.emptyText)
            state.tocRv!!.layoutManager = LinearLayoutManager(config.activity)
            state.tocRv!!.adapter = TocAdapter { entry ->
                callbacks.navigateToUrl("https://${EpubResourceResolver.VIRTUAL_HOST}/${state.bookId}/${entry.href.trimStart('/')}")
                hideOverlays()
            }
            root.addView(tocView)

            val bmView = LayoutInflater.from(config.activity).inflate(R.layout.overlay_list, root, false)
            state.bookmarkRv = bmView.findViewById(R.id.recycler)
            state.bookmarkEmpty = bmView.findViewById(R.id.emptyText)
            state.bookmarkRv!!.layoutManager = LinearLayoutManager(config.activity)
            state.bookmarkAdapter = BookmarkAdapter(
                onDelete = { bookmark ->
                    callbacks.lifecycleScope.launch(Dispatchers.IO) {
                        config.bookmarkService.delete(bookmark)
                        withContext(Dispatchers.Main) {
                            Snackbar
                                .make(config.root, R.string.bookmark_deleted, Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo) {
                                    callbacks.lifecycleScope.launch(Dispatchers.IO) {
                                        config.bookmarkService.restore(bookmark)
                                    }
                                }
                                .show()
                        }
                    }
                }
            ) { b -> goToBookmark(b); hideOverlays() }
            state.bookmarkRv!!.adapter = state.bookmarkAdapter
            root.addView(bmView)

            // Highlights list
            val hlView = LayoutInflater.from(config.activity).inflate(R.layout.overlay_list, root, false)
            state.highlightRv = hlView.findViewById(R.id.recycler)
            state.highlightEmpty = hlView.findViewById(R.id.emptyText)
            state.highlightRv!!.layoutManager = LinearLayoutManager(config.activity)
            state.highlightRv!!.adapter = HighlightListAdapter(
                onClick = { h ->
                    goToHighlight(h)
                    hideOverlays()
                },
                onDelete = { h ->
                    callbacks.lifecycleScope.launch(Dispatchers.IO) {
                        config.highlightService.delete(h)
                        withContext(Dispatchers.Main) {
                            removeHighlightDecorationsFromWebView(h.id)
                            Snackbar
                                .make(config.root, R.string.highlight_deleted, Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo) {
                                    callbacks.lifecycleScope.launch(Dispatchers.IO) {
                                        config.highlightService.restore(h)
                                        withContext(Dispatchers.Main) {
                                            val currentHref = state.epub?.spine?.getOrNull(state.spineIndex)?.href
                                            if (currentHref == h.spineHref) {
                                                injectHighlightIntoWebView(h.id, h.text, h.prefix, h.suffix, h.color, h.startPath, h.endPath, h.startOffset, h.endOffset)
                                            }
                                        }
                                    }
                                }
                                .show()
                        }
                    }
                },
            )
            root.addView(hlView)
        }
        val tocAdapter = state.tocRv!!.adapter as TocAdapter

        tocAdapter.submitList(book.toc) {
            highlightCurrentTocEntry()
        }

        state.tocEmpty!!.visibility =
            if (book.toc.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        state.tocEmpty!!.text = callbacks.getString(R.string.no_toc)
        state.bookmarkEmpty!!.text = callbacks.getString(R.string.reader_bookmarks_empty)
        refreshBookmarkList()

        if (!state.bookmarkObserverStarted) {
            state.bookmarkObserverStarted = true
            config.bookmarkService.observeForBook(state.bookId).asLiveData().observe(config.activity as androidx.lifecycle.LifecycleOwner) { list ->
                (state.bookmarkRv?.adapter as? BookmarkAdapter)?.submitList(list)
                refreshBookmarkList()
            }
        }
        if (!state.highlightObserverStarted) {
            state.highlightObserverStarted = true
            config.highlightService.observeForBook(state.bookId).asLiveData().observe(config.activity as androidx.lifecycle.LifecycleOwner) { list ->
                (state.highlightRv?.adapter as? HighlightListAdapter)?.submitList(list)
                if (state.activeOverlayTab == R.id.btnTabHighlights) {
                    state.highlightEmpty?.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
        state.highlightEmpty?.text = callbacks.getString(R.string.reader_highlights_empty)

        val initialTab = when {
            selectHighlights -> R.id.btnTabHighlights
            selectBookmarks -> R.id.btnTabBookmarks
            else -> R.id.btnTabContents
        }
        config.overlayTabGroup.check(initialTab)
        applyOverlayTab(initialTab)

        config.topBar.visibility = View.GONE
        config.bottomBar.visibility = View.GONE
        config.tvPageIndicator.visibility = View.GONE
        config.tocBookmarkOverlay.visibility = View.VISIBLE
        callbacks.updateHistoryUi()
    }

    fun refreshBookmarkList() {
        val list = (state.bookmarkRv?.adapter as? BookmarkAdapter)?.currentList ?: return
        // Only toggle the bookmark empty-hint while the Bookmarks tab is active —
        // otherwise an async DB update would surface it over the TOC tab.
        if (state.bookmarksTabActive) {
            state.bookmarkEmpty?.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    fun applyOverlayTab(checkedId: Int) {
        state.activeOverlayTab = checkedId
        val isBookmarks = checkedId == R.id.btnTabBookmarks
        val isHighlights = checkedId == R.id.btnTabHighlights
        state.bookmarksTabActive = isBookmarks
        val tocEmpty = state.epub?.toc.isNullOrEmpty()
        if (isBookmarks) {
            state.tocRv?.visibility = View.GONE
            state.tocEmpty?.visibility = View.GONE
            state.highlightRv?.visibility = View.GONE
            state.highlightEmpty?.visibility = View.GONE
            state.bookmarkRv?.visibility = View.VISIBLE
            val bmEmpty = (state.bookmarkAdapter?.currentList?.isEmpty() != false)
            state.bookmarkEmpty?.visibility = if (bmEmpty) View.VISIBLE else View.GONE
        } else if (isHighlights) {
            state.tocRv?.visibility = View.GONE
            state.tocEmpty?.visibility = View.GONE
            state.bookmarkRv?.visibility = View.GONE
            state.bookmarkEmpty?.visibility = View.GONE
            state.highlightRv?.visibility = View.VISIBLE
            val hlEmpty = ((state.highlightRv?.adapter as? HighlightListAdapter)?.currentList?.isEmpty() != false)
            state.highlightEmpty?.visibility = if (hlEmpty) View.VISIBLE else View.GONE
        } else {
            state.bookmarkRv?.visibility = View.GONE
            state.bookmarkEmpty?.visibility = View.GONE
            state.highlightRv?.visibility = View.GONE
            state.highlightEmpty?.visibility = View.GONE
            state.tocRv?.visibility = View.VISIBLE
            state.tocEmpty?.visibility = if (tocEmpty) View.VISIBLE else View.GONE
        }
    }

    // ---- Search overlay ----

    fun showSearchOverlay() {
        callbacks.clearReaderSelection()
        if (state.searchRv == null) {
            val v = LayoutInflater.from(config.activity).inflate(R.layout.overlay_list, config.searchContent, false)
            state.searchRv = v.findViewById(R.id.recycler)
            state.searchEmpty = v.findViewById(R.id.emptyText)
            state.searchRv!!.layoutManager = LinearLayoutManager(config.activity)
            state.searchAdapter = SearchResultAdapter { r ->
                state.epub?.let { book ->
                    callbacks.navigateToUrl("https://${EpubResourceResolver.VIRTUAL_HOST}/${state.bookId}/${book.spine[r.chapterIndex].href}")
                    hideOverlays()
                    config.webView.postDelayed({ config.webView.findAllAsync(state.searchEdit?.text.toString()) }, 400)
                }
            }
            state.searchRv!!.adapter = state.searchAdapter
            config.searchContent.addView(v)
        }
        state.searchEmpty?.text = callbacks.getString(R.string.reader_search_empty)
        state.searchEmpty?.visibility = View.VISIBLE
        state.searchAdapter?.submitList(emptyList())
        state.searchEdit = config.searchEdit
        state.searchEdit?.setOnEditorActionListener { _, _, _ ->
            val q = state.searchEdit?.text.toString().orEmpty()
            if (q.isNotBlank()) performSearch(q) else {
                state.searchAdapter?.submitList(emptyList()); state.searchEmpty?.visibility = View.VISIBLE
            }
            true
        }
        config.topBar.visibility = View.GONE
        config.bottomBar.visibility = View.GONE
        config.tvPageIndicator.visibility = View.GONE
        config.searchOverlay.visibility = View.VISIBLE
        callbacks.updateHistoryUi()
        state.searchEdit?.setText("")
        // Show the on-screen keyboard + place the cursor, mirroring how the
        // main app's SearchActivity opens search (the user does not have to
        // tap the field a second time to bring up the IME). Posted after the
        // overlay is visible so the IME reliably attaches to the field.
        config.searchEdit.post {
            if (config.searchOverlay.visibility == View.VISIBLE) {
                config.searchEdit.requestFocus()
                val imm = config.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(config.searchEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun performSearch(query: String) {
        val book = state.epub ?: return
        val file = state.bookEntity?.path?.let { java.io.File(it) } ?: return
        state.searchEmpty?.visibility = View.GONE
        config.searchService.search(
            scope = callbacks.lifecycleScope,
            file = file,
            spine = book.spine,
            tocTitles = tocMap(),
            query = query,
        ) { results ->
            (state.searchRv?.adapter as? SearchResultAdapter)?.submitList(results)
            state.searchEmpty?.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    fun tocMap(): Map<String, String> {
        val book = state.epub ?: return emptyMap()
        val map = HashMap<String, String>()
        for (e in book.toc) {
            val key = e.href.substringBefore('#')
            if (key.isNotBlank() && !map.containsKey(key)) map[key] = e.label
        }
        return map
    }

    // ---- Bookmarks ----

    fun goToBookmark(b: BookmarkEntity) {
        callbacks.clearReaderSelection()
        val book = state.epub ?: return
        if (b.spineIndex !in book.spine.indices) return

        config.webView.evaluateJavascript(
            "(function(){if(!window.Caesura) return '';return window.Caesura.currentPage()+','+window.Caesura.pageCount()+','+window.Caesura.ratio();})();"
        ) { result ->
            val values = result?.trim()?.removeSurrounding("\"")?.split(',')
            val actualPage = values?.getOrNull(0)?.toIntOrNull()
            val pageCount = values?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1)
            val actualRatio = values?.getOrNull(2)?.toFloatOrNull()
            val current = callbacks.captureReaderLocation()?.let { location ->
                if (actualPage != null && actualPage >= 0) {
                    location.copy(pageInChapter = actualPage, ratio = actualRatio ?: location.ratio)
                } else location
            }

            if (b.spineIndex != state.spineIndex) {
                if (!state.restoringHistoryLocation && current != null) {
                    callbacks.pushHistory(current)
                }
                state.pendingFragment = null
                state.pendingTargetPageInChapter = null
                state.restoreRatio = null
                state.pendingBookmarkAnchor = b.snippet.trim().takeIf { it.isNotBlank() }
                state.pendingBookmarkIsWholePage = b.bookmarkType == BookmarkEntity.TYPE_WHOLE_PAGE
                state.pendingBookmarkFallbackPage = b.pageInChapter.coerceAtLeast(0)
                state.pendingHistoryCursorAfterRestore = true
                callbacks.loadChapter(b.spineIndex)
                return@evaluateJavascript
            }

            val count = pageCount ?: state.pagesInChapter.coerceAtLeast(1)
            val fallbackPage = if (b.pageInChapter >= 0) {
                b.pageInChapter.coerceIn(0, count - 1)
            } else if (count > 1) {
                kotlin.math.round(b.scrollRatio.coerceIn(0f, 1f) * (count - 1)).toInt()
            } else 0

            if (!state.restoringHistoryLocation && current != null && actualPage != null) {
                // The semantic anchor is the primary bookmark location. We only
                // know the destination page after resolving that anchor below.
                // Record the page being left now if the bookmark is elsewhere.
                if (b.snippet.isBlank() || fallbackPage != actualPage) {
                    callbacks.pushHistory(current.copy(pageInChapter = actualPage))
                }
            }

            state.pendingFragment = null
            state.pendingTargetPageInChapter = null
            state.pendingBookmarkAnchor = b.snippet.trim().takeIf { it.isNotBlank() }
            state.pendingBookmarkIsWholePage = b.bookmarkType == BookmarkEntity.TYPE_WHOLE_PAGE
            state.pendingBookmarkFallbackPage = fallbackPage
            state.pendingHistoryCursorAfterRestore = true
            callbacks.capturePageSnapshot(forward = false)
            callbacks.applyPendingFragmentOrRestore()
        }
    }

    fun addBookmark() {
        val idx = state.spineIndex
        val title = callbacks.sectionLabel()
        config.webView.evaluateJavascript(
            "(function(){if(!window.Caesura) return '';var p=window.Caesura.currentPage();var r=window.Caesura.ratio();var t=window.Caesura.pageSnippet(p);return JSON.stringify({page:p,ratio:r,snippet:t});})();"
        ) { result ->
            val raw = runCatching { org.json.JSONTokener(result?.trim().orEmpty()).nextValue() as? String }.getOrNull() ?: ""
            val json = runCatching { org.json.JSONObject(raw) }.getOrNull()
            val page = json?.optInt("page", state.currentPageInChapter)?.coerceAtLeast(0) ?: state.currentPageInChapter
            val ratio = json?.optDouble("ratio", state.currentScrollRatio.toDouble())?.toFloat()?.coerceIn(0f, 1f) ?: state.currentScrollRatio
            val snippet = json?.optString("snippet").orEmpty().trim()
            callbacks.lifecycleScope.launch(Dispatchers.IO) {
                val result = config.bookmarkService.addWholePageBookmark(
                    bookId = state.bookId,
                    spineIndex = idx,
                    pageInChapter = page,
                    scrollRatio = ratio,
                    chapterTitle = title,
                    snippet = snippet,
                )
                withContext(Dispatchers.Main) {
                    when (result) {
                        is BookmarkService.AddResult.Duplicate ->
                            showBookmarkExistsSnackbar(result.existing)
                        is BookmarkService.AddResult.Created ->
                            Snackbar.make(config.root, R.string.bookmark_added, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun addBookmarkFromSelection(selection: ReaderSelectionLocator) {
        val text = selection.text.trim()
        if (text.isBlank()) return
        val href = selection.spineHref
        val selectionAnchor = "__LIVRE_SELECTED_V1__" + org.json.JSONObject().apply {
            put("text", text)
            put("startPath", selection.startPath)
            put("startOffset", selection.startOffset)
            put("endPath", selection.endPath)
            put("endOffset", selection.endOffset)
            put("prefix", selection.prefix)
            put("suffix", selection.suffix)
        }.toString()
        val idx = state.epub?.spine?.indexOfFirst { it.href == href }?.takeIf { it >= 0 } ?: state.spineIndex
        val title = callbacks.sectionLabel()
        config.webView.evaluateJavascript(
            "if(window.Caesura){window.Caesura.currentPage()+','+window.Caesura.ratio();}"
        ) { result ->
            val values = result?.trim()?.removeSurrounding("\"")?.split(',')
            val page = values?.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: state.currentPageInChapter
            val ratio = values?.getOrNull(1)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: state.currentScrollRatio
            callbacks.lifecycleScope.launch(Dispatchers.IO) {
                val result = config.bookmarkService.addTextBookmark(
                    bookId = state.bookId,
                    spineIndex = idx,
                    pageInChapter = page,
                    scrollRatio = ratio,
                    chapterTitle = title,
                    selectionAnchor = selectionAnchor,
                )
                withContext(Dispatchers.Main) {
                    when (result) {
                        is BookmarkService.AddResult.Duplicate ->
                            showBookmarkExistsSnackbar(result.existing)
                        is BookmarkService.AddResult.Created ->
                            Snackbar.make(config.root, R.string.bookmark_added, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /** "Bookmark already exists" prompt with a delete-and-undo path.
     *  Shared by the whole-page and selection bookmark flows. */
    private fun showBookmarkExistsSnackbar(existing: BookmarkEntity) {
        Snackbar
            .make(config.root, R.string.bookmark_exists, Snackbar.LENGTH_LONG)
            .setAction(R.string.delete) {
                callbacks.lifecycleScope.launch(Dispatchers.IO) {
                    config.bookmarkService.delete(existing)
                    withContext(Dispatchers.Main) {
                        Snackbar
                            .make(config.root, R.string.bookmark_deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.undo) {
                                callbacks.lifecycleScope.launch(Dispatchers.IO) {
                                    config.bookmarkService.restore(existing)
                                }
                            }
                            .show()
                    }
                }
            }
            .show()
    }

    // ---- Overlay helpers / back ----

    fun overlayVisible(): Boolean =
        config.tocBookmarkOverlay.visibility == View.VISIBLE || config.searchOverlay.visibility == View.VISIBLE

    fun hideOverlays() {
        // A dismissed search must never deliver stale results afterwards.
        config.searchService.cancel()
        config.tocBookmarkOverlay.visibility = View.GONE
        config.searchOverlay.visibility = View.GONE
        state.bookmarksTabActive = false
        config.tvPageIndicator.visibility = View.VISIBLE
        callbacks.updateHistoryUi()
        config.webView.requestFocus()
    }

    fun setupOverlays() {
        val btnOverlayBack = config.activity.findViewById<View>(R.id.btnOverlayBack)
        btnOverlayBack?.setOnClickListener { hideOverlays() }
        val btnSearchBack = config.activity.findViewById<View>(R.id.btnSearchBack)
        btnSearchBack?.setOnClickListener {
            // Mirror the main app's search back behavior: closing the overlay
            // also dismisses the IME so the user doesn't have to reach for the
            // nav-bar back button to hide the keyboard.
            config.searchEdit.clearFocus()
            val imm = config.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(config.searchEdit.windowToken, 0)
            hideOverlays()
        }
        config.overlayTabGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked && checkedId != View.NO_ID) applyOverlayTab(checkedId)
        }
        // Capture taps so they don't fall through to the WebView.
        config.tocBookmarkOverlay.setOnClickListener { }
        config.searchOverlay.setOnClickListener { }
    }

    fun onBackPressed() {
        if (config.searchOverlay.visibility == View.VISIBLE) {
            hideOverlays(); return
        }
        if (config.tocBookmarkOverlay.visibility == View.VISIBLE) {
            hideOverlays(); return
        }
        // Patch v37: if the Read Aloud overlay is open, back minimizes it
        // (playback keeps going — Stop is the button that ends read-aloud).
        if (config.activity.findViewById<View>(R.id.ttsOverlay)?.visibility == View.VISIBLE) {
            callbacks.hideTtsOverlay(); return
        }
        if (state.chromeVisible) {
            callbacks.toggleChrome(); return
        }
    }

    // ---- Highlights ----

    /** Shows a compact color picker bottom sheet for creating a highlight. */
    fun showHighlightColorPicker(selection: ReaderSelectionLocator?) {
        if (selection == null || selection.text.isBlank()) {
            Snackbar.make(config.root, R.string.selection_none, Snackbar.LENGTH_SHORT).show()
            return
        }
        val colors = listOf(
            HighlightService.HIGHLIGHT_YELLOW to R.string.highlight_color_yellow to R.drawable.highlight_color_yellow,
            HighlightService.HIGHLIGHT_GREEN to R.string.highlight_color_green to R.drawable.highlight_color_green,
            HighlightService.HIGHLIGHT_BLUE to R.string.highlight_color_blue to R.drawable.highlight_color_blue,
            HighlightService.HIGHLIGHT_PURPLE to R.string.highlight_color_purple to R.drawable.highlight_color_purple,
        )
        val dialog = BottomSheetDialog(config.activity)
        val root = LinearLayout(config.activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * config.activity.resources.displayMetrics.density).roundToInt()
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(config.activity).apply {
            text = callbacks.getString(R.string.highlight_action)
            textSize = 16f
            setTextColor(callbacks.themeColor(android.R.attr.textColorPrimary))
            setPadding(0, 0, 0, (12 * config.activity.resources.displayMetrics.density).roundToInt())
        })
        val colorRow = LinearLayout(config.activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        colors.forEach { (pair, drawableRes) ->
            val (colorInt, labelRes) = pair
            val btn = android.widget.ImageButton(config.activity).apply {
                setImageResource(drawableRes)
                background = null
                val size = (48 * config.activity.resources.displayMetrics.density).roundToInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins((8 * config.activity.resources.displayMetrics.density).roundToInt(), 0, (8 * config.activity.resources.displayMetrics.density).roundToInt(), 0)
                }
                contentDescription = callbacks.getString(labelRes)
                setOnClickListener {
                    dialog.dismiss()
                    saveHighlight(selection, colorInt)
                }
            }
            colorRow.addView(btn)
        }
        root.addView(colorRow)
        dialog.setContentView(root)
        dialog.show()
    }

    /** Saves a highlight to the database and injects it into the WebView. */
    fun saveHighlight(selection: ReaderSelectionLocator, color: Int) {
        val href = selection.spineHref
        val highlight = HighlightEntity(
            bookId = state.bookId,
            spineHref = href,
            text = selection.text,
            color = color,
            prefix = selection.prefix,
            suffix = selection.suffix,
            startPath = selection.startPath,
            endPath = selection.endPath,
            startOffset = selection.startOffset,
            endOffset = selection.endOffset,
        )
        callbacks.lifecycleScope.launch(Dispatchers.IO) {
            val id = config.highlightService.save(highlight)
            withContext(Dispatchers.Main) {
                injectHighlightIntoWebView(id, selection.text, selection.prefix, selection.suffix, color, selection.startPath, selection.endPath, selection.startOffset, selection.endOffset)
                Snackbar.make(config.root, R.string.highlight_added, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    /** Injects a single highlight into the WebView immediately after creation.
     *
     * Patch v37 anchoring strategy (most-specific first):
     *  1. Resolve the stored start element path and search for the text
     *     within that element only - this keeps a repeated phrase from
     *     matching an earlier occurrence elsewhere in the chapter.
     *  2. If the path cannot be resolved, search all chapter occurrences and
     *     score each candidate against the saved prefix and suffix context.
     */
    fun injectHighlightIntoWebView(
        id: Long,
        text: String,
        prefix: String,
        suffix: String,
        color: Int,
        startPath: String = "",
        endPath: String = "",
        startOffset: Int = 0,
        endOffset: Int = 0,
    ) {
        val cssColor = HighlightService.highlightCssColor(color)
        val safeText = org.json.JSONObject.quote(text)
        val safePrefix = org.json.JSONObject.quote(prefix)
        val safeSuffix = org.json.JSONObject.quote(suffix)
        val safeStartPath = org.json.JSONObject.quote(startPath)
        val safeEndPath = org.json.JSONObject.quote(endPath)
        config.webView.evaluateJavascript(
            """(function(){
                var text=$safeText,prefix=$safePrefix,suffix=$safeSuffix,color='$cssColor',id=$id,sp=$safeStartPath,ep=$safeEndPath,so=$startOffset,eo=$endOffset;
                if(document.querySelector('mark.livre-highlight[data-highlight-id="'+id+'"]'))return true;

                /*
                 * Persistent highlights are decorations only. Never extract or
                 * surround a multi-node Range. EPUBs commonly place words in
                 * nested inline elements, and range-subtree operations can
                 * reparent those elements and change layout/pagination.
                 */
                function textNodes(root){
                    var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null,false),a=[],n;
                    while((n=w.nextNode())){
                        if(n.parentNode)a.push(n);
                    }
                    return a;
                }

                /* Build a searchable stream while retaining the exact DOM point
                   for every non-whitespace character. Whitespace is collapsed so
                   selections crossing line/paragraph boundaries can be matched
                   without assuming that the browser's selection string uses the
                   same newline representation as the EPUB DOM. */
                function normalizedStream(root){
                    var nodes=textNodes(root),chars=[],points=[],pendingSpace=false;
                    for(var ni=0;ni<nodes.length;ni++){
                        var n=nodes[ni],source=n.textContent||'';
                        for(var i=0;i<source.length;i++){
                            var ch=source.charAt(i);
                            if(/\s/.test(ch)){
                                if(chars.length&&!pendingSpace){
                                    chars.push(' ');
                                    points.push({node:n,offset:i,synthetic:true});
                                }
                                pendingSpace=true;
                            }else{
                                chars.push(ch.toLowerCase());
                                points.push({node:n,offset:i,synthetic:false});
                                pendingSpace=false;
                            }
                        }
                    }
                    while(chars.length&&chars[0]===' '){chars.shift();points.shift();}
                    while(chars.length&&chars[chars.length-1]===' '){chars.pop();points.pop();}
                    return {nodes:nodes,text:chars.join(''),points:points};
                }

                function normalize(s){
                    return String(s||'').replace(/\s+/g,' ').trim().toLowerCase();
                }

                function contextScore(all,pos,needle){
                    var score=0;
                    var pfx=normalize(prefix),sfx=normalize(suffix);
                    if(pfx){
                        var before=all.slice(Math.max(0,pos-pfx.length),pos);
                        var common=0;
                        while(common<before.length&&common<pfx.length&&before.charAt(before.length-1-common)===pfx.charAt(pfx.length-1-common))common++;
                        score+=common*2;
                        if(before===pfx)score+=10000;
                    }
                    if(sfx){
                        var after=all.slice(pos+needle.length,pos+needle.length+sfx.length);
                        var commonAfter=0;
                        while(commonAfter<after.length&&commonAfter<sfx.length&&after.charAt(commonAfter)===sfx.charAt(commonAfter))commonAfter++;
                        score+=commonAfter*2;
                        if(after===sfx)score+=10000;
                    }
                    return score;
                }

                function bestOccurrence(all,needle){
                    if(!needle)return -1;
                    var wanted=normalize(needle);
                    if(!wanted)return -1;
                    var best=-1,bestScore=-1,from=0,pos;
                    while((pos=all.indexOf(wanted,from))>=0){
                        var score=contextScore(all,pos,wanted);
                        if(score>bestScore){bestScore=score;best=pos;}
                        from=pos+Math.max(1,wanted.length);
                    }
                    return best;
                }

                function wrapTextNodePart(node,startOffset,endOffset){
                    if(!node||!node.parentNode)return false;
                    var len=(node.textContent||'').length;
                    var a=Math.max(0,Math.min(startOffset,len));
                    var b=Math.max(a,Math.min(endOffset,len));
                    if(b<=a)return false;
                    try{
                        var selected=node;
                        if(b<len)node.splitText(b);
                        if(a>0)selected=node.splitText(a);
                        var mark=document.createElement('mark');
                        mark.className='livre-highlight';
                        mark.style.backgroundColor=color;
                        mark.style.borderRadius='2px';
                        mark.style.display='inline';
                        mark.style.padding='0';
                        mark.style.margin='0';
                        mark.style.border='0';
                        mark.style.font='inherit';
                        mark.style.lineHeight='inherit';
                        mark.dataset.highlightId=id;
                        mark.addEventListener('click',function(e){
                            e.preventDefault();
                            e.stopPropagation();
                            LivreHighlight.onHighlightTap(id);
                        });
                        selected.parentNode.insertBefore(mark,selected);
                        mark.appendChild(selected);
                        return true;
                    }catch(e){
                        return false;
                    }
                }

                function wrapStreamRange(data,startIndex,endIndex){
                    if(startIndex<0||endIndex<=startIndex||!data.points[startIndex]||!data.points[endIndex-1])return false;
                    var first=data.points[startIndex],last=data.points[endIndex-1];
                    var startNode=first.node,endNode=last.node;
                    var startOffset=first.offset;
                    var endOffset=last.offset+1;
                    var startNodeIndex=data.nodes.indexOf(startNode),endNodeIndex=data.nodes.indexOf(endNode);
                    if(startNodeIndex<0||endNodeIndex<startNodeIndex)return false;
                    var changed=false;
                    for(var i=endNodeIndex;i>=startNodeIndex;i--){
                        var node=data.nodes[i];
                        if(!node||!node.parentNode)continue;
                        var a=(i===startNodeIndex)?startOffset:0;
                        var b=(i===endNodeIndex)?endOffset:(node.textContent||'').length;
                        if(b>a)changed=wrapTextNodePart(node,a,b)||changed;
                    }
                    return changed;
                }

                function resolveEl(path){
                    if(!path)return null;
                    var parts=path.split('/'),node=document.body,started=false;
                    for(var i=0;i<parts.length;i++){
                        var seg=parts[i].split(':'),tag=seg[0],idx=parseInt(seg[1]||'0',10);
                        if(tag==='body'){started=true;continue;}
                        if(!started)continue;
                        var kids=node.children,seen=0,found=null;
                        for(var j=0;j<kids.length;j++){
                            if(kids[j].tagName.toLowerCase()===tag){
                                if(seen===idx){found=kids[j];break;}
                                seen++;
                            }
                        }
                        if(!found)return null;
                        node=found;
                    }
                    return node;
                }

                function resolveTextPoint(path,offset){
                    if(!path)return null;
                    var parts=path.split('/');
                    var last=parts[parts.length-1];
                    if(last.indexOf('#text:')!==0)return null;
                    var parentPath=parts.slice(0,-1).join('/');
                    var parent=resolveEl(parentPath);
                    if(!parent)return null;
                    var wantedIndex=parseInt(last.slice(6),10);
                    if(!isFinite(wantedIndex)||wantedIndex<0)return null;
                    var walker=document.createTreeWalker(parent,NodeFilter.SHOW_TEXT,null,false);
                    var direct=[],n;
                    while((n=walker.nextNode())){
                        if(n.parentNode===parent)direct.push(n);
                    }
                    var node=direct[wantedIndex]||null;
                    if(!node)return null;
                    var len=(node.textContent||'').length;
                    return {node:node,offset:Math.max(0,Math.min(offset||0,len))};
                }

                function wrapExactPoints(startPoint,endPoint){
                    if(!startPoint||!endPoint)return false;
                    var data=normalizedStream(document.body);
                    var startIndex=-1,endIndex=-1;
                    for(var i=0;i<data.points.length;i++){
                        var pt=data.points[i];
                        if(pt.node===startPoint.node&&pt.offset===startPoint.offset){startIndex=i;break;}
                    }
                    if(startIndex<0){
                        for(var j=0;j<data.points.length;j++){
                            var pt2=data.points[j];
                            if(pt2.node===startPoint.node&&pt2.offset>=startPoint.offset){startIndex=j;break;}
                        }
                    }
                    for(var k=startIndex>=0?startIndex:0;k<data.points.length;k++){
                        var pt3=data.points[k];
                        if(pt3.node===endPoint.node&&pt3.offset===Math.max(0,endPoint.offset-1)){endIndex=k+1;break;}
                    }
                    if(startIndex<0||endIndex<=startIndex)return false;
                    return wrapStreamRange(data,startIndex,endIndex);
                }

                var wanted=normalize(text);
                if(!wanted)return false;

                /* First use the exact text-node locator captured from the live selection.
                   This disambiguates a common word/phrase that appears multiple times
                   inside the same paragraph or inline element. */
                var exactStart=resolveTextPoint(sp,so);
                var exactEnd=resolveTextPoint(ep,eo);
                if(exactStart&&exactEnd&&wrapExactPoints(exactStart,exactEnd))return true;

                /* Legacy/path fallback: keep the existing local-element + context strategy
                   for highlights created before the exact text-node locator existed. */
                var startEl=resolveEl(sp);
                if(startEl){
                    var local=normalizedStream(startEl);
                    var localPos=bestOccurrence(local.text,wanted);
                    if(localPos>=0&&wrapStreamRange(local,localPos,localPos+wanted.length))return true;
                }

                /* Fallback: search the complete chapter using the saved context. */
                var data=normalizedStream(document.body);
                var pos=bestOccurrence(data.text,wanted);
                if(pos<0)return false;
                return wrapStreamRange(data,pos,pos+wanted.length);
            })();""",
            null,
        )
    }

    /**
     * Navigates from the Highlights tab to the exact rendered page containing
     * the selected highlight. A highlight is not an EPUB URL/fragment, so using
     * navigateToUrl() here can reload the same chapter and restore the current
     * page while still adding a history entry.
     */
    fun goToHighlight(highlight: HighlightEntity) {
        callbacks.clearReaderSelection()
        val book = state.epub ?: return
        val targetIndex = book.spine.indexOfFirst { it.href == highlight.spineHref }
        if (targetIndex < 0) return

        val current = callbacks.captureReaderLocation()
        val sameChapter = targetIndex == state.spineIndex
        if (!sameChapter) {
            // Resolve the visible WebView page before changing chapters. The
            // destination is asynchronous, but the history entry must represent
            // the exact location the user is leaving.
            state.pendingHighlightHistoryLocation = null
            state.pendingHighlightId = highlight.id
            state.pendingFragment = null
            state.pendingTargetPageInChapter = null
            state.restoreRatio = null
            config.webView.evaluateJavascript(
                "(function(){if(!window.Caesura) return '';return window.Caesura.currentPage()+','+window.Caesura.ratio();})();"
            ) { result ->
                val values = result?.trim()?.removeSurrounding("\"")?.split(',')
                val actualPage = values?.getOrNull(0)?.toIntOrNull()
                val actualRatio = values?.getOrNull(1)?.toFloatOrNull()
                if (!state.restoringHistoryLocation && current != null) {
                    callbacks.pushHistory(if (actualPage != null && actualPage >= 0) {
                        current.copy(pageInChapter = actualPage, ratio = actualRatio ?: current.ratio)
                    } else current)
                }
                state.pendingHistoryCursorAfterRestore = true
                callbacks.loadChapter(targetIndex)
            }
            return
        }

        // Resolve the target page without changing the WebView first. This makes
        // same-chapter history deterministic: the page being left is recorded
        // before gotoPage() changes the rendered page.
        config.webView.evaluateJavascript(
            "if(window.Caesura){window.Caesura.currentPage() + '|' + window.Caesura.highlightPageById(${highlight.id});}"
        ) { result ->
            val values = result?.trim()?.removeSurrounding("\"")?.split('|')
            val currentPage = values?.getOrNull(0)?.toIntOrNull()
            val targetPage = values?.getOrNull(1)?.toIntOrNull()

            if (targetPage != null && targetPage >= 0 && currentPage != null &&
                targetPage != currentPage && !state.restoringHistoryLocation && current != null
            ) {
                callbacks.pushHistory(current.copy(pageInChapter = currentPage))
            }

            if (targetPage != null && targetPage >= 0) {
                state.pendingHistoryCursorAfterRestore = true
                config.webView.evaluateJavascript(
                    "if(window.Caesura){window.Caesura.gotoHighlightById(${highlight.id});}"
                ) {
                    config.webView.evaluateJavascript(
                        "if(window.Caesura){window.Caesura.currentPage()+','+window.Caesura.ratio();}"
                    ) { targetLocationResult ->
                        val targetValues = targetLocationResult?.trim()?.removeSurrounding("\"")?.split(',')
                        val targetActualPage = targetValues?.getOrNull(0)?.toIntOrNull()
                        val targetActualRatio = targetValues?.getOrNull(1)?.toFloatOrNull()
                        if (state.pendingHistoryCursorAfterRestore && targetActualPage != null) {
                            state.historyCursorLocationVar = ReaderLocation(state.spineIndex, targetActualPage, targetActualRatio ?: 0f)
                            state.pendingHistoryCursorAfterRestore = false
                        }
                        config.handler.postDelayed({ callbacks.pollProgress() }, 80L)
                    }
                }
            }
        }
    }

    /** Loads all highlights for the current chapter and injects them into the WebView. */
    fun injectHighlightsForChapter() {
        val book = state.epub ?: return
        val href = book.spine.getOrNull(state.spineIndex)?.href ?: return
        callbacks.lifecycleScope.launch(Dispatchers.IO) {
            val highlights = config.highlightService.getForChapter(state.bookId, href)
            if (highlights.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                highlights.forEach { h ->
                    injectHighlightIntoWebView(h.id, h.text, h.prefix, h.suffix, h.color, h.startPath, h.endPath, h.startOffset, h.endOffset)
                }
                val targetId = state.pendingHighlightId
                if (targetId != null && highlights.any { it.id == targetId }) {
                    state.pendingHighlightId = null
                    state.pendingHighlightHistoryLocation = null
                    config.handler.postDelayed({
                        config.webView.evaluateJavascript(
                            "if(window.Caesura){window.Caesura.gotoHighlightById($targetId);}",
                        ) {
                            // Cross-chapter highlight navigation resolves its
                            // exact rendered page only after the highlights have
                            // been injected. Keep that page as the history cursor
                            // so Back/Forward preserve the explicit destination.
                            config.webView.evaluateJavascript(
                                "if(window.Caesura){window.Caesura.currentPage()+','+window.Caesura.ratio();}"
                            ) { result ->
                                val values = result?.trim()?.removeSurrounding("\"")?.split(',')
                                val page = values?.getOrNull(0)?.toIntOrNull()
                                val ratio = values?.getOrNull(1)?.toFloatOrNull()
                                if (page != null) {
                                    state.historyCursorLocationVar = ReaderLocation(state.spineIndex, page, ratio ?: 0f)
                                }
                                config.handler.postDelayed({ callbacks.pollProgress() }, 80L)
                            }
                        }
                    }, 120L)
                }
            }
        }
    }

    /** Removes every rendered fragment belonging to one persisted highlight.
     *
     * A single logical selection may be decorated by multiple <mark> nodes when
     * it crosses EPUB text nodes or inline spans. Deletion must remove all of
     * those decoration nodes without changing the underlying EPUB text.
     */
    fun removeHighlightDecorationsFromWebView(highlightId: Long) {
        config.webView.evaluateJavascript(
            """(function(){
                var ms=document.querySelectorAll('mark.livre-highlight[data-highlight-id="$highlightId"]');
                for(var i=ms.length-1;i>=0;i--){
                    var m=ms[i],p=m.parentNode;
                    if(!p)continue;
                    while(m.firstChild)p.insertBefore(m.firstChild,m);
                    p.removeChild(m);
                }
            })();""",
            null,
        )
    }

    /** Shows a bottom sheet for viewing a highlight and adding/editing a note. */
    fun showHighlightNoteSheet(highlightId: Long) {
        callbacks.lifecycleScope.launch(Dispatchers.IO) {
            // Find the highlight by loading all highlights for this book and finding by id.
            val href = state.epub?.spine?.getOrNull(state.spineIndex)?.href ?: return@launch
            val highlights = config.highlightService.getForChapter(state.bookId, href)
            val highlight = highlights.find { it.id == highlightId } ?: return@launch
            withContext(Dispatchers.Main) {
                val dialog = BottomSheetDialog(config.activity)
                val root = LinearLayout(config.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    val pad = (16 * config.activity.resources.displayMetrics.density).roundToInt()
                    setPadding(pad, pad, pad, pad)
                }
                // Highlighted text
                root.addView(TextView(config.activity).apply {
                    text = highlight.text
                    textSize = 15f
                    setTextColor(callbacks.themeColor(android.R.attr.textColorPrimary))
                    setPadding(0, 0, 0, (12 * config.activity.resources.displayMetrics.density).roundToInt())
                })
                // Existing note (if any)
                if (!highlight.note.isNullOrBlank()) {
                    root.addView(TextView(config.activity).apply {
                        text = highlight.note
                        textSize = 14f
                        setTextColor(callbacks.themeColor(android.R.attr.textColorSecondary))
                        setPadding(0, 0, 0, (12 * config.activity.resources.displayMetrics.density).roundToInt())
                    })
                }
                // Note input
                val input = EditText(config.activity).apply {
                    hint = callbacks.getString(R.string.highlight_note_hint)
                    setText(highlight.note ?: "")
                    setSingleLine(false)
                    minLines = 2
                    maxLines = 4
                }
                root.addView(input)
                // Button row
                val btnRow = LinearLayout(config.activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    setPadding(0, (12 * config.activity.resources.displayMetrics.density).roundToInt(), 0, 0)
                }
                btnRow.addView(MaterialButton(config.activity).apply {
                    text = callbacks.getString(R.string.delete)
                    setOnClickListener {
                        dialog.dismiss()
                        callbacks.lifecycleScope.launch(Dispatchers.IO) {
                            config.highlightService.delete(highlight)
                            withContext(Dispatchers.Main) {
                                removeHighlightDecorationsFromWebView(highlightId)
                                Snackbar
                                    .make(config.root, R.string.highlight_deleted, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.undo) {
                                        callbacks.lifecycleScope.launch(Dispatchers.IO) {
                                            config.highlightService.restore(highlight)
                                            withContext(Dispatchers.Main) {
                                                val currentHref = state.epub?.spine?.getOrNull(state.spineIndex)?.href
                                                if (currentHref == highlight.spineHref) {
                                                    injectHighlightIntoWebView(highlight.id, highlight.text, highlight.prefix, highlight.suffix, highlight.color, highlight.startPath, highlight.endPath, highlight.startOffset, highlight.endOffset)
                                                }
                                            }
                                        }
                                    }
                                    .show()
                            }
                        }
                    }
                })
                btnRow.addView(MaterialButton(config.activity).apply {
                    text = callbacks.getString(R.string.ok)
                    // Patch v37: add spacing between Delete and OK so they're
                    // not cramped together.
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginStart = config.activity.resources.getDimensionPixelSize(R.dimen.app_section_spacing)
                    }
                    setOnClickListener {
                        val note = input.text.toString().trim().ifEmpty { null }
                        dialog.dismiss()
                        callbacks.lifecycleScope.launch(Dispatchers.IO) {
                            config.highlightService.updateNote(highlightId, note)
                        }
                        Snackbar.make(config.root, R.string.highlight_note_saved, Snackbar.LENGTH_SHORT).show()
                    }
                })
                root.addView(btnRow)
                dialog.setContentView(root)
                dialog.show()
            }
        }
    }

    // ---- TOC helpers ----

    private fun highlightCurrentTocEntry() {
        val recycler = state.tocRv ?: return
        val adapter = recycler.adapter as? TocAdapter ?: return

        val position = currentTocPosition()

        adapter.setSelectedPosition(position)

        if (position == RecyclerView.NO_POSITION) {
            return
        }

        recycler.post {
            if (position !in 0 until adapter.itemCount) {
                return@post
            }

            val layoutManager =
                recycler.layoutManager as? LinearLayoutManager ?: return@post

            val offset = (recycler.height * 0.35f).toInt()

            layoutManager.scrollToPositionWithOffset(position, offset)
        }
    }

    private fun currentTocPosition(): Int {
        val book = state.epub ?: return RecyclerView.NO_POSITION

        var bestPosition = RecyclerView.NO_POSITION
        var bestSpineIndex = -1

        for (position in book.toc.indices) {
            val entry = book.toc[position]

            val tocPath = entry.href
                .substringBefore('#')
                .substringBefore('?')
                .trimStart('/')

            val entrySpineIndex = book.spine.indexOfFirst {
                it.href
                    .substringBefore('#')
                    .substringBefore('?')
                    .trimStart('/') == tocPath
            }

            if (
                entrySpineIndex >= 0 &&
                entrySpineIndex <= state.spineIndex &&
                (
                    entrySpineIndex > bestSpineIndex ||
                        (
                            entrySpineIndex == bestSpineIndex &&
                                position > bestPosition
                            )
                )
            ) {
                bestSpineIndex = entrySpineIndex
                bestPosition = position
            }
        }

        return bestPosition
    }
}
