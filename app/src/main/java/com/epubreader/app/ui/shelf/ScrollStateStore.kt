package com.epubreader.app.ui.shelf

import com.epubreader.app.ui.ShelfView

/**
 * ScrollStateStore — extracted from MainActivity (Phase 8).
 *
 * Pure scroll-restoration state and decision logic for the bookshelf lists.
 * The store owns the per-view scroll anchors and the pending-restore key, but
 * NO RecyclerView/LayoutManager/Adapter types: the caller (the Activity or
 * LibraryScreenController) supplies the raw first-visible position/offset (and
 * the clicked book's id/index/top-offset when capturing from a book list) and
 * receives a [RestoreTarget] to apply to the layout manager. That keeps the
 * restore strategy — exact first-visible restoration when the list did not
 * reorder, re-location by book id when it did — unit-testable on the JVM.
 *
 * Only the return-from-reader/details path restores scroll. Drawer
 * navigation, sort changes, and view-mode changes top-reset instead
 * (ShelfStateStore.scrollToTopOnNextContent); the two behaviours are mutually
 * exclusive by design.
 */
class ScrollStateStore {

    /** Per-view saved scroll anchor, keyed by [viewKey]. */
    private val anchors = mutableMapOf<String, ScrollAnchor>()

    /** The view key whose scroll position should be restored on the next
     * eligible content emission; null when no restore is queued. Kept until a
     * matching emission consumes it, so a restore queued before an emission
     * for a different view does not fire against the wrong list. */
    var pendingRestoreKey: String? = null
        private set

    /** Captures [anchor] for [view]. The caller resolves the raw values from
     * the live RecyclerView/adapter; this only records them (and only for
     * restore-eligible views). */
    fun capture(view: ShelfView, anchor: ScrollAnchor) {
        if (!isRestoreEligible(view)) return
        anchors[viewKey(view)] = anchor
    }

    /** Queues a restore of [view]'s last anchor so the next content emission
     * for that view scrolls the list back to where the user left it. */
    fun queueRestore(view: ShelfView) {
        if (!isRestoreEligible(view)) return
        pendingRestoreKey = viewKey(view)
    }

    /** Cancels a queued restore (drawer navigation abandons it). */
    fun clearPendingRestore() {
        pendingRestoreKey = null
    }

    /**
     * Decides the restore target for [view], if one is queued and applicable.
     *
     * @param isBookList   true when the bound adapter is the book adapter
     *                     (book lists can re-locate the clicked book by id;
     *                     row lists cannot).
     * @param itemCount    the adapter's current item count.
     * @param indexOfBook  resolves a book id to its current adapter index
     *                     (-1 when absent) — only consulted for book lists.
     *
     * Restore strategy: if the clicked book is still at the SAME adapter index
     * it had when the user navigated away (the list did not reorder), restore
     * the exact first-visible position + offset — the same rows that were on
     * screen before. Only when the clicked book MOVED (sort by progress /
     * last-read changed its position) do we re-locate it by id and align it to
     * its saved top offset.
     *
     * Returns null (keeping the pending key) when the current view does not
     * match the queued key or the list is still empty — a later emission may
     * still consume it. Clears the pending key when the anchor is gone.
     */
    fun restore(
        view: ShelfView,
        isBookList: Boolean,
        itemCount: Int,
        indexOfBook: (Long) -> Int,
    ): RestoreTarget? {
        if (!isRestoreEligible(view)) return null
        val key = pendingRestoreKey ?: return null
        if (viewKey(view) != key) return null
        if (itemCount == 0) return null
        val anchor = anchors[key] ?: run {
            pendingRestoreKey = null
            return null
        }

        var pos = anchor.firstVisiblePosition.coerceIn(0, itemCount - 1)
        var offset = anchor.firstVisibleOffset
        if (isBookList && anchor.clickedBookId != null && anchor.clickedBookPosition >= 0) {
            val currentClickedIdx = indexOfBook(anchor.clickedBookId)
            if (currentClickedIdx >= 0 && currentClickedIdx != anchor.clickedBookPosition) {
                // Book moved: re-locate by id, align to its saved top offset.
                pos = currentClickedIdx
                offset = anchor.clickedBookTopOffset
            } else {
                // Unchanged (or book not found): restore exact first-visible.
                pos = anchor.firstVisiblePosition.coerceIn(0, itemCount - 1)
                offset = anchor.firstVisibleOffset
            }
        }

        pendingRestoreKey = null
        return RestoreTarget(pos, offset)
    }

    data class RestoreTarget(val position: Int, val offset: Int)

    companion object {
        /** Stable per-view key for the anchor map. Detail views are keyed by
         * name so two different authors never collide. */
        fun viewKey(view: ShelfView): String = when (view) {
            is ShelfView.AuthorDetail -> "author_detail:${view.name}"
            is ShelfView.SeriesDetail -> "series_detail:${view.name}"
            else -> view::class.simpleName ?: "unknown"
        }

        /** Views whose scroll position is restored on return from a
         * sub-activity. Currently Reading is included for the Book Details
         * return path (the reader return path top-resets instead). */
        fun isRestoreEligible(view: ShelfView): Boolean =
            view is ShelfView.Reading ||
                    view is ShelfView.Library ||
                    view is ShelfView.AuthorsList ||
                    view is ShelfView.SeriesList ||
                    view is ShelfView.AuthorDetail ||
                    view is ShelfView.SeriesDetail
    }
}

/**
 * A captured scroll position: the first-visible row + its pixel offset, and
 * (for book lists) the clicked book's id + adapter position + top offset so
 * the exact same layout can be restored later, or the clicked title
 * re-located by id if the list re-sorted while the user was away.
 */
data class ScrollAnchor(
    val firstVisiblePosition: Int,
    val firstVisibleOffset: Int,
    val clickedBookId: Long?,
    val clickedBookPosition: Int,
    val clickedBookTopOffset: Int,
)
