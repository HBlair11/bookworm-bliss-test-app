package com.epubreader.app.ui.shelf

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.R
import com.epubreader.app.data.BookEntity
import com.epubreader.app.ui.BookAdapter
import com.epubreader.app.ui.BookshelfViewModel
import com.epubreader.app.ui.DisplayItem
import com.epubreader.app.ui.RowAdapter
import com.epubreader.app.ui.ShelfView

/**
 * LibraryScreenController — extracted from MainActivity (Phase 8).
 *
 * Owns the main shelf RecyclerView: the book/row adapters, the grid/list
 * layout configuration, the book-list empty states, and the scroll
 * capture/restore mechanics for the return-from-reader/details paths.
 *
 * Pure decisions live in the injected stores: ShelfStateStore owns the
 * "clean top reset" flag consumed below, and ScrollStateStore owns the anchor
 * map + restore decision — this controller supplies raw RecyclerView values
 * and applies the returned [ScrollStateStore.RestoreTarget].
 */
class LibraryScreenController(
    private val config: Config,
) {

    interface Callbacks {
        fun onOpenBook(book: BookEntity)
        fun onBookLongPressed(book: BookEntity): Boolean
        fun onOpenDetails(book: BookEntity)
        fun onOpenDetail(name: String)
    }

    data class Config(
        val recycler: RecyclerView,
        val emptyState: LinearLayout,
        val emptyIcon: View,
        val emptyText: TextView,
        val emptyHint: TextView,
        val emptyAction: View,
        val viewModel: BookshelfViewModel,
        val shelfState: ShelfStateStore,
        val scrollState: ScrollStateStore,
        val callbacks: Callbacks,
        /** Launched by the "Add books" button on the Library empty state. */
        val onPickFiles: () -> Unit,
        val rows: EmptyStateRows,
    )

    var bookAdapter: BookAdapter? = null
        private set
    var rowAdapter: RowAdapter? = null
        private set

    /** The most recently bound book list (kept for adapter reconfiguration
     * on grid/column changes). */
    var currentBooks: List<BookEntity> = emptyList()
        private set

    private val recycler get() = config.recycler
    private val viewModel get() = config.viewModel
    private val shelfState get() = config.shelfState
    private val scrollState get() = config.scrollState

    /** Swipe-to-dismiss helper attached only on the Currently Reading list
     * (see AppNavigationController.applyView). */
    private var touchHelper: ItemTouchHelper? = null

    /** Attaches [helper] to the shelf RecyclerView. */
    fun attachTouchHelper(helper: ItemTouchHelper) {
        touchHelper = helper
        helper.attachToRecyclerView(recycler)
    }

    /** Detaches any attached touch helper (called on every view change so
     * swipe-to-dismiss never leaks into a non-Reading list). */
    fun detachTouchHelper() {
        touchHelper?.attachToRecyclerView(null)
    }

    // ---------------------------------------------------------------- content

    /** Renders a book-list / row-list emission for [view], including the
     * empty states. Settings, Folders, and the placeholder are routed by the
     * Activity before this is called. */
    fun renderItems(items: List<DisplayItem>, view: ShelfView) {
        // Book list views: drop any Folders/Settings rows added earlier.
        config.rows.clear()

        if (items.isEmpty()) {
            config.emptyState.visibility = View.VISIBLE
            config.emptyState.gravity = Gravity.CENTER
            recycler.visibility = View.GONE
            config.emptyIcon.visibility = View.VISIBLE
            config.emptyText.visibility = View.VISIBLE
            config.emptyText.text =
                when (view) {
                    is ShelfView.Reading -> str(R.string.empty_reading)
                    is ShelfView.Favorites -> str(R.string.empty_favorites)
                    is ShelfView.AuthorsList -> str(R.string.empty_authors)
                    is ShelfView.SeriesList -> str(R.string.empty_series)
                    else -> str(R.string.empty_library)
                }
            config.emptyHint.text = if (view is ShelfView.Library) str(R.string.empty_library_hint) else ""
            config.emptyHint.visibility = if (view is ShelfView.Library) View.VISIBLE else View.GONE
            config.emptyAction.visibility = if (view is ShelfView.Library) View.VISIBLE else View.GONE
            config.emptyAction.setOnClickListener { config.onPickFiles() }
        } else {
            config.emptyState.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            config.emptyAction.visibility = View.GONE
        }

        val first = items.firstOrNull()
        if (first is DisplayItem.GroupRow) {
            bindRowAdapter(items.filterIsInstance<DisplayItem.GroupRow>(), view)
        } else {
            bindBookAdapter(items.filterIsInstance<DisplayItem.Book>().map { it.book })
        }
    }

    /** Reconfigures the adapter for the current grid/column settings. Called
     * when the view-mode or grid-column LiveData re-emits. */
    fun reconfigureAdapter() {
        val grid = viewModel.viewModeGrid.value == true
        val cols = viewModel.gridColumns.value ?: 3
        if (currentBooks.isNotEmpty() &&
            ShelfStateStore.isBookView(viewModel.view.value ?: ShelfView.Library)
        ) {
            // Grid <-> list mode and grid-column-count changes are also a
            // "different layout" event, so they get the same clean top reset
            // as a shelf-view switch (see bindBookAdapter): fresh adapter, no
            // item-diff animation, pinned to position 0 before layout.
            hardResetBind(grid, cols, currentBooks)
        }
    }

    // ---------------------------------------------------------------- binding

    private fun hardResetBind(grid: Boolean, cols: Int, books: List<BookEntity>) {
        val previousAnimator = recycler.itemAnimator
        recycler.stopScroll()
        recycler.itemAnimator = null

        val newAdapter = newBookAdapter(grid)
        val lm = if (grid) GridLayoutManager(recycler.context, cols)
        else LinearLayoutManager(recycler.context)

        bookAdapter = newAdapter
        rowAdapter = null
        recycler.layoutManager = lm
        recycler.adapter = newAdapter

        (lm as LinearLayoutManager).scrollToPositionWithOffset(0, 0)

        newAdapter.submitList(books) {
            recycler.post {
                (recycler.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(0, 0)
                recycler.itemAnimator = previousAnimator
            }
        }
    }

    private fun newBookAdapter(grid: Boolean): BookAdapter =
        BookAdapter(
            grid,
            config.callbacks::onOpenBook,
            config.callbacks::onBookLongPressed,
            config.callbacks::onOpenDetails,
        ).apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }

    private fun bindBookAdapter(books: List<BookEntity>) {
        currentBooks = books
        val grid = viewModel.viewModeGrid.value == true
        val cols = viewModel.gridColumns.value ?: 3

        // Patch 10: when switching shelf views (drawer tap / "Show" action /
        // sort change / grid-column change) the user wants a clean, instant
        // reset to the top — no visible fast-scroll, no mid-screen land, no
        // leftover scroll offset from the previous view. The Patch 9 fix only
        // ran scrollToPosition(0) inside the submitList commit callback, which
        // fires AFTER DiffUtil has already animated item inserts/removes; the
        // user therefore saw the old scroll offset and the item-diff animation
        // for a frame before the jump to 0 landed. Here we take a hard,
        // non-animated reset path: stop any in-flight scroll, disable item
        // animations for the swap, create a FRESH LayoutManager + BookAdapter
        // (so there is no saved scroll state to restore and no diff to animate
        // — the new list is laid out from position 0 from the start), then pin
        // to the top with scrollToPositionWithOffset before submitting.
        if (shelfState.scrollToTopOnNextContent) {
            shelfState.scrollToTopOnNextContent = false
            hardResetBind(grid, cols, books)
            return
        }

        if (bookAdapter == null || bookAdapter?.grid != grid) {
            bookAdapter = newBookAdapter(grid)
            rowAdapter = null
            recycler.adapter = bookAdapter
        }
        // CRITICAL: keep the LayoutManager in sync with the adapter's mode.
        // Forcing GridLayoutManager here even in list mode left list cards
        // laid out in a 3-column grid (cut off) after returning from the
        // reader. Patch 11: only recreate the LayoutManager when the grid/list
        // mode or column count actually changed. Recreating it on every Room
        // emission wipes scroll state whenever the database re-emits (e.g.
        // after opening a book updates last-read progress), which would
        // defeat scroll restoration. NOTE: GridLayoutManager extends
        // LinearLayoutManager, so we must check for the more specific type
        // first when in list mode, otherwise a leftover GridLayoutManager from
        // a previous grid view would pass an "is LinearLayoutManager" check and
        // never get replaced.
        val currentLm = recycler.layoutManager
        val needsNewLm = if (grid) {
            currentLm !is GridLayoutManager || currentLm.spanCount != cols
        } else {
            currentLm !is LinearLayoutManager || currentLm is GridLayoutManager
        }
        if (needsNewLm) {
            recycler.layoutManager =
                if (grid) GridLayoutManager(recycler.context, cols)
                else LinearLayoutManager(recycler.context)
        }
        if (grid) {
            (recycler.layoutManager as? GridLayoutManager)?.spanCount = cols
        }
        bookAdapter?.submitList(books) {
            // Patch 11: after the new list is committed, restore the saved
            // scroll position if the user is returning from the reader /
            // details.
            if (scrollState.pendingRestoreKey != null) {
                recycler.post { tryRestoreScroll() }
            }
        }
    }

    private fun bindRowAdapter(rows: List<DisplayItem.GroupRow>, view: ShelfView) {
        val icon = if (view is ShelfView.SeriesList) R.drawable.ic_series else R.drawable.ic_person_frame

        // Tapping an author/series row navigates into its books layout. We
        // must capture the parent list's scroll position BEFORE switching so
        // it can be restored when the user returns via the back arrow / phone
        // back. We deliberately do NOT set scrollToTopOnNextContent in the
        // non-reset path — the detail list itself top-resets via the existing
        // top-reset path in bindBookAdapter (openDetail changes the view →
        // content re-emits → top reset), but the parent list must NOT be
        // top-reset on return.
        val onRowClick: (Pair<String, Long?>) -> Unit = { pair ->
            captureScrollState()
            scrollState.queueRestore(viewModel.view.value ?: ShelfView.Library)
            // Patch 11: the newly-opened author/series book list should still
            // start cleanly at the top (this view's prior scroll has been
            // safely captured for its own future restore — top-resetting a
            // DIFFERENT view's list).
            shelfState.scrollToTopOnNextContent = true
            config.callbacks.onOpenDetail(pair.first)
        }

        // For consistency with the book-list reset path, apply the same hard
        // top reset on row-based (Authors/Series) views: stop scroll, disable
        // item animation, use a FRESH adapter (so switching Authors<->Series
        // never diffs the old rows against the new ones), pin to top before
        // the new list is submitted. This avoids leftover scroll offset and
        // visible fast-scroll when switching to/from Authors/Series.
        if (shelfState.scrollToTopOnNextContent) {
            shelfState.scrollToTopOnNextContent = false
            val previousAnimator = recycler.itemAnimator
            recycler.stopScroll()
            recycler.itemAnimator = null

            val newRowAdapter = RowAdapter(icon, onClick = onRowClick).apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            rowAdapter = newRowAdapter
            bookAdapter = null
            val lm = LinearLayoutManager(recycler.context)
            recycler.layoutManager = lm
            recycler.adapter = newRowAdapter
            lm.scrollToPositionWithOffset(0, 0)

            newRowAdapter.submitList(rows.map { com.epubreader.app.data.GroupedRow(it.name, it.count) }) {
                recycler.post {
                    (recycler.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(0, 0)
                    recycler.itemAnimator = previousAnimator
                }
            }
            return
        }

        if (rowAdapter == null) {
            rowAdapter = RowAdapter(icon, onClick = onRowClick)
            bookAdapter = null
            recycler.adapter = rowAdapter
            // Patch 12: GridLayoutManager extends LinearLayoutManager, so a
            // plain `!is LinearLayoutManager` check MISSES a leftover grid
            // layout manager from a previous book-list (grid) view. The
            // Authors/Series list then rendered inside that grid LM, cutting
            // off the list. Force a plain LinearLayoutManager whenever the
            // current one is a grid (or null, or any future non-linear
            // manager).
            forceLinearLayoutManager()
        } else {
            rowAdapter?.iconRes = icon
            // Even when reusing the row adapter, make sure we are NOT still
            // on a grid layout manager (e.g. returning from a grid detail
            // view).
            forceLinearLayoutManager()
        }
        rowAdapter?.submitList(
            rows.map { com.epubreader.app.data.GroupedRow(it.name, it.count) },
        ) {
            // Restore the parent list's scroll on return from a detail layout.
            if (scrollState.pendingRestoreKey != null) {
                recycler.post { tryRestoreScroll() }
            }
        }
    }

    private fun forceLinearLayoutManager() {
        val currentLm = recycler.layoutManager
        if (currentLm !is LinearLayoutManager || currentLm is GridLayoutManager) {
            recycler.layoutManager = LinearLayoutManager(recycler.context)
        }
    }

    // ---------------------------------------------------------------- scroll

    /** Captures the current first-visible row + its pixel offset (and, for
     * book lists, the clicked book's id + adapter position + top offset) so
     * the exact same layout can be restored later. If the list re-sorts
     * after reading progress changes, the clicked title is re-located by id
     * and aligned by its saved top offset — but only if its index actually
     * moved; otherwise the exact first-visible position + offset is restored
     * unchanged. */
    fun captureScrollState(clickedBookId: Long? = null) {
        val view = viewModel.view.value ?: return
        if (!ScrollStateStore.isRestoreEligible(view)) return
        val lm = recycler.layoutManager as? LinearLayoutManager ?: return
        val adapter = recycler.adapter ?: return
        if (adapter.itemCount == 0) return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos == RecyclerView.NO_POSITION) return
        val child = lm.findViewByPosition(pos)
        val offset = child?.top ?: 0
        // For book lists, if we know which book was tapped, also capture its
        // own adapter position + top offset so we can re-align it by id if
        // the list reorders. For row lists (authors/series) there is no
        // tapped book.
        val clickedPos = if (clickedBookId != null && adapter is BookAdapter) {
            adapter.currentList.indexOfFirst { it.id == clickedBookId }
        } else {
            -1
        }
        val clickedOffset = if (clickedPos >= 0) {
            lm.findViewByPosition(clickedPos)?.top ?: offset
        } else {
            0
        }
        scrollState.capture(
            view,
            ScrollAnchor(pos, offset, clickedBookId, clickedPos, clickedOffset)
        )
    }

    /** Applies a queued scroll restore if the current view matches and the
     * adapter has content; see ScrollStateStore.restore for the strategy. */
    fun tryRestoreScroll(): Boolean {
        val view = viewModel.view.value ?: return false
        val lm = recycler.layoutManager as? LinearLayoutManager ?: return false
        val adapter = recycler.adapter ?: return false
        val target = scrollState.restore(
            view = view,
            isBookList = adapter is BookAdapter,
            itemCount = adapter.itemCount,
            indexOfBook = { id ->
                (adapter as? BookAdapter)?.currentList?.indexOfFirst { it.id == id } ?: -1
            },
        ) ?: return false
        lm.scrollToPositionWithOffset(target.position, target.offset)
        return true
    }

    /** Patch 11: when returning from the reader after opening a book FROM
     * Currently Reading, force a scroll-to-top — Room may not re-emit in
     * time (or at all) on resume. Book Details uses the normal saved-scroll
     * restoration path instead. */
    fun applyReadingTopReset() {
        recycler.post {
            (recycler.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(0, 0)
            recycler.scrollToPosition(0)
        }
    }

    private fun str(res: Int): String = recycler.context.getString(res)
}
