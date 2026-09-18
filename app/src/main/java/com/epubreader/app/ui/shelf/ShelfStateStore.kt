package com.epubreader.app.ui.shelf

import com.epubreader.app.ui.BookshelfViewModel
import com.epubreader.app.ui.ShelfView

/**
 * ShelfStateStore — extracted from MainActivity (Phase 8).
 *
 * Pure shelf-navigation state and transition rules. This class owns NO Android
 * view types, adapters, or RecyclerViews — it only records and computes what
 * should happen when the user navigates between shelf views:
 *
 *  - the transient "Recently Added" screen (entered from the scan-complete
 *    snackbar's Show action, exited via back — restoring the shelf the user
 *    was on before tapping Show),
 *  - the parent-list return rule for Author/Series detail views,
 *  - the "clean top reset on next content emission" flag shared by every
 *    navigation path that should land the user at the top of the new list
 *    (drawer taps, sort changes, Recently Added exits),
 *  - the "Currently Reading opens with a top reset on return from the reader"
 *    flag, and
 *  - process-death save/restore of the active shelf view.
 *
 * The [com.epubreader.app.ui.BookshelfViewModel] remains the single source of
 * truth for the CURRENT view (it persists it); this store holds the transient
 * navigation context the ViewModel deliberately does not persist. The Activity
 * applies the transitions computed here by calling back into the ViewModel
 * (setView / clearDetail / openDetail).
 */
class ShelfStateStore {

    /** The shelf the user was viewing before entering the transient
     * "Recently Added" temp screen; null while not on it. Patch 16 (Issue #3):
     * back / the toolbar back arrow returns there exactly instead of dumping
     * the user on the Library. */
    var viewBeforeRecentlyAdded: ShelfView? = null
        private set

    /** Patch 10/11: set by navigation paths that want the next list emission
     * to hard-reset the list to the top (drawer tap, sort change, Show action).
     * Consumed by LibraryScreenController when it binds the next content. */
    var scrollToTopOnNextContent: Boolean = false

    /** Patch 11: when a book is opened FROM Currently Reading, the reader
     * return path forces a scroll-to-top in onResume as a guaranteed fallback
     * (Room may not re-emit in time — or at all). Book Details instead uses
     * the saved-scroll restoration path. */
    var pendingReadingTopReset: Boolean = false

    /** Drawer navigation cancels any queued Currently Reading top-reset —
     * the user deliberately navigated somewhere else. */
    fun onDrawerNavigation() {
        pendingReadingTopReset = false
    }

    /** Enters the transient "Recently Added" screen scoped to [from] — the
     * shelf to return to when the user leaves it. */
    fun enterRecentlyAdded(from: ShelfView) {
        viewBeforeRecentlyAdded = from
        scrollToTopOnNextContent = true
    }

    /** Leaves the transient "Recently Added" screen and returns the shelf to
     * restore. Falls back to Library if the pre-entry shelf was lost (e.g.
     * process death). The returned shelf should land at the top of its list,
     * matching the clean-refresh-on-nav-switch contract used by drawer taps. */
    fun exitRecentlyAdded(): ShelfView {
        val target = viewBeforeRecentlyAdded ?: ShelfView.Library
        viewBeforeRecentlyAdded = null
        scrollToTopOnNextContent = true
        return target
    }

    /** Saved-state snapshot for [onSaveInstanceState]: the current view
     * flattened into a key (+ detail name for Author/Series detail views).
     * Null when the current view is the transient Recently Added screen — it
     * must never be persisted or a relaunch would land on a stale empty list. */
    fun snapshotForSave(view: ShelfView): SavedShelf? {
        if (view is ShelfView.RecentlyAdded) return null
        val detailName = when (view) {
            is ShelfView.AuthorDetail -> view.name
            is ShelfView.SeriesDetail -> view.name
            else -> null
        }
        return SavedShelf(keyForSavedState(view), detailName)
    }

    /** Restore counterpart of [snapshotForSave]. */
    fun restore(snapshot: SavedShelf?): ShelfView? {
        snapshot ?: return null
        return restoreFromKey(snapshot.key, snapshot.detailName)
    }

    data class SavedShelf(val key: String, val detailName: String?)

    companion object {
        /** The parent list an Author/Series detail view returns to, or null
         * when [view] is not a detail view. Phase 10: detail views opened
         * from Home return to Home, not the Authors/Series list. */
        fun parentOf(view: ShelfView): ShelfView? = when (view) {
            is ShelfView.AuthorDetail -> if (view.fromHome) ShelfView.Home else ShelfView.AuthorsList
            is ShelfView.SeriesDetail -> if (view.fromHome) ShelfView.Home else ShelfView.SeriesList
            else -> null
        }

        /** True when the view renders a book list (not Home, the parent
         * lists, Collections placeholder, Folders, or Settings). */
        fun isBookView(view: ShelfView): Boolean =
            view !is ShelfView.Home &&
                    view !is ShelfView.AuthorsList &&
                    view !is ShelfView.SeriesList &&
                    view !is ShelfView.Collections &&
                    view !is ShelfView.Folders &&
                    view !is ShelfView.Settings

        fun isPlaceholder(view: ShelfView): Boolean = view is ShelfView.Collections

        fun isFoldersView(view: ShelfView): Boolean = view is ShelfView.Folders

        fun isDetail(view: ShelfView): Boolean =
            view is ShelfView.AuthorDetail || view is ShelfView.SeriesDetail

        fun isRecentlyAdded(view: ShelfView): Boolean = view is ShelfView.RecentlyAdded

        /** Flattens a view into a stable saved-state key. Mirrors the
         * ViewModel's own keys so restore works across versions. */
        fun keyForSavedState(view: ShelfView): String = when (view) {
            is ShelfView.Home -> BookshelfViewModel.KEY_HOME
            is ShelfView.Reading -> BookshelfViewModel.KEY_READING
            is ShelfView.Library -> BookshelfViewModel.KEY_LIBRARY
            is ShelfView.Favorites -> BookshelfViewModel.KEY_FAVORITES
            is ShelfView.AuthorsList -> BookshelfViewModel.KEY_AUTHORS
            is ShelfView.SeriesList -> BookshelfViewModel.KEY_SERIES
            is ShelfView.Finished -> BookshelfViewModel.KEY_FINISHED
            is ShelfView.ToBeRead -> BookshelfViewModel.KEY_TBR
            is ShelfView.Collections -> BookshelfViewModel.KEY_COLLECTIONS
            is ShelfView.Folders -> BookshelfViewModel.KEY_FOLDERS
            is ShelfView.Settings -> BookshelfViewModel.KEY_SETTINGS
            is ShelfView.AuthorDetail -> "author_detail"
            is ShelfView.SeriesDetail -> "series_detail"
            else -> BookshelfViewModel.KEY_HOME
        }

        /** Rebuilds a view from a saved-state key (+ detail name). Returns
         * null for unknown keys or a detail key without a name. */
        fun restoreFromKey(key: String?, detailName: String?): ShelfView? {
            return when (key) {
                BookshelfViewModel.KEY_HOME -> ShelfView.Home
                BookshelfViewModel.KEY_READING -> ShelfView.Reading
                BookshelfViewModel.KEY_LIBRARY -> ShelfView.Library
                BookshelfViewModel.KEY_FAVORITES -> ShelfView.Favorites
                BookshelfViewModel.KEY_AUTHORS -> ShelfView.AuthorsList
                BookshelfViewModel.KEY_SERIES -> ShelfView.SeriesList
                BookshelfViewModel.KEY_FINISHED -> ShelfView.Finished
                BookshelfViewModel.KEY_TBR -> ShelfView.ToBeRead
                BookshelfViewModel.KEY_COLLECTIONS -> ShelfView.Collections
                BookshelfViewModel.KEY_FOLDERS -> ShelfView.Folders
                BookshelfViewModel.KEY_SETTINGS -> ShelfView.Settings
                "author_detail" -> detailName?.let { ShelfView.AuthorDetail(it) }
                "series_detail" -> detailName?.let { ShelfView.SeriesDetail(it) }
                else -> null
            }
        }
    }
}
