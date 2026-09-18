package com.epubreader.app.ui.shelf

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.R
import com.epubreader.app.ReadingStatsActivity
import com.epubreader.app.databinding.ActivityMainBinding
import com.epubreader.app.ui.BookshelfViewModel
import com.epubreader.app.ui.DrawerAdapter
import com.epubreader.app.ui.DrawerItem
import com.epubreader.app.ui.ShelfView
import com.epubreader.app.ui.VocabularyActivity
import com.epubreader.app.util.CurrentlyReadingUndoSnackbar

/**
 * AppNavigationController — extracted from MainActivity (Phase 8).
 *
 * Orchestrates the app-shell navigation: the drawer (setup, item list,
 * selection), the toolbar title, the drawer-toggle up affordance (hamburger
 * on top-level views, back arrow on detail / Recently Added screens), the FAB
 * visibility per view, the back-button handling for the Recently Added exit
 * and detail-view parent return, and applyView() — the single place where a
 * ShelfView change fans out to every screen controller.
 *
 * Pure transition rules live in ShelfStateStore/ScrollStateStore; this class
 * performs the UI orchestration they decide on.
 */
class AppNavigationController(
    private val config: Config,
) {

    data class Config(
        val activity: AppCompatActivity,
        val binding: ActivityMainBinding,
        val viewModel: BookshelfViewModel,
        val shelfState: ShelfStateStore,
        val scrollState: ScrollStateStore,
        val homeController: HomeScreenController,
        val libraryController: LibraryScreenController,
        val drawerAdapter: DrawerAdapter,
    )

    private val binding get() = config.binding
    private val viewModel get() = config.viewModel
    private val shelfState get() = config.shelfState
    private val scrollState get() = config.scrollState

    /** Provided by the Activity right after it constructs the toggle. */
    lateinit var drawerToggle: ActionBarDrawerToggle

    // ---------------------------------------------------------------- drawer

    fun setupDrawer() {
        // Patch 16 (Addition #1): drawer widened from 3/5 (60%) to 70% of
        // screen width.
        val w = config.activity.resources.displayMetrics.widthPixels
        binding.drawerPane.layoutParams =
            (binding.drawerPane.layoutParams as androidx.drawerlayout.widget.DrawerLayout.LayoutParams)
                .apply { width = (w * 70 / 100).coerceAtLeast(280) }

        binding.drawerList.layoutManager = LinearLayoutManager(config.activity)
        binding.drawerList.adapter = config.drawerAdapter
        submitDrawerItems()
    }

    private fun drawerItems(): List<DrawerItem> =
        listOf(
            DrawerItem(str(R.string.nav_home), R.drawable.ic_home, view = ShelfView.Home),
            DrawerItem(str(R.string.nav_currently_reading), R.drawable.ic_book, view = ShelfView.Reading),
            DrawerItem(str(R.string.nav_library), R.drawable.ic_library, view = ShelfView.Library),
            DrawerItem(str(R.string.nav_favorites), R.drawable.ic_favorite, view = ShelfView.Favorites),
            DrawerItem(str(R.string.nav_authors), R.drawable.ic_person, view = ShelfView.AuthorsList),
            DrawerItem(str(R.string.nav_series), R.drawable.ic_series, view = ShelfView.SeriesList),
            DrawerItem(
                str(R.string.nav_collections),
                R.drawable.ic_collections,
                isPlaceholder = true,
                view = ShelfView.Collections
            ),
            DrawerItem(
                label = "",
                iconRes = 0,
                isDivider = true,
            ),
            DrawerItem(str(R.string.nav_reading_stats), R.drawable.ic_menu_book, launchActivity = ReadingStatsActivity::class.java),
            DrawerItem(str(R.string.nav_vocabulary), R.drawable.ic_vocabulary, launchActivity = VocabularyActivity::class.java),
            DrawerItem(str(R.string.nav_folders), R.drawable.ic_folder, view = ShelfView.Folders),
            DrawerItem(
                str(R.string.nav_settings),
                R.drawable.ic_settings,
                isPlaceholder = true,
                view = ShelfView.Settings
            ),
        )

    fun submitDrawerItems() {
        config.drawerAdapter.submitList(drawerItems())
        config.drawerAdapter.setSelected(viewModel.view.value)
    }

    fun titleFor(view: ShelfView?): String =
        when (view) {
            is ShelfView.Home -> str(R.string.nav_home)
            is ShelfView.Reading -> str(R.string.nav_currently_reading)
            is ShelfView.Library -> str(R.string.nav_library)
            is ShelfView.Favorites -> str(R.string.nav_favorites)
            is ShelfView.Finished -> str(R.string.nav_finished)
            is ShelfView.ToBeRead -> str(R.string.nav_tbr)
            is ShelfView.RecentlyAdded -> str(R.string.recently_added_screen_title)
            is ShelfView.AuthorsList -> str(R.string.nav_authors)
            is ShelfView.SeriesList -> str(R.string.nav_series)
            is ShelfView.AuthorDetail -> view.name
            is ShelfView.SeriesDetail -> view.name
            is ShelfView.Collections -> str(R.string.nav_collections)
            is ShelfView.Folders -> str(R.string.nav_folders)
            is ShelfView.Settings -> str(R.string.nav_settings)
            null -> str(R.string.app_name)
        }

    /** Drawer row tapped: closes the drawer, launches external activities
     * (Reading Stats / Vocabulary) directly, otherwise switches the shelf
     * view with a clean top reset. */
    fun selectDrawer(item: DrawerItem) {
        binding.drawerRoot.close()
        // If the drawer item launches a separate activity (e.g. Reading
        // Stats), start it directly without changing the current shelf view.
        item.launchActivity?.let {
            config.activity.startActivity(Intent(config.activity, it))
            return
        }
        item.view?.let {
            // If already on this view (e.g. Home), just scroll to top.
            if (viewModel.view.value == it && it is ShelfView.Home) {
                config.homeController.scrollToTop()
                return
            }
            shelfState.scrollToTopOnNextContent = true
            scrollState.clearPendingRestore()
            shelfState.onDrawerNavigation()
            viewModel.setView(it)
        }
    }

    fun toggleDrawer() {
        if (binding.drawerRoot.isOpen) binding.drawerRoot.close() else binding.drawerRoot.open()
    }

    // ---------------------------------------------------------------- transitions

    /** Returns from an Author/Series detail (books layout) back to its parent
     *  list (Authors or Series), or back to Home when the detail was opened
     *  from Home (Phase 10). Patch 11: the parent list's last scroll position
     *  is restored — NOT top-reset — so the author/series row the user
     *  originally tapped is still on screen. Phase 10: when returning to Home
     *  from a fromHome detail, Home's scroll position is restored so the user
     *  lands where they left off. */
    fun returnToParentList() {
        val current = viewModel.view.value ?: return
        val parent = ShelfStateStore.parentOf(current) ?: return
        scrollState.queueRestore(parent)
        viewModel.clearDetail()
    }

    // Patch 16 (Issue #3): leave the transient "Recently Added" temp screen
    // and return to whatever shelf the user was viewing before they tapped
    // Show on the scan-complete snackbar. Falls back to Library if state was
    // lost (e.g. process death). Crucially this does NOT go through
    // setView()-persistence for the RecentlyAdded view itself (see
    // ViewModel.setView) and restores the real underlying shelf, so lastView
    // stays correct for the next launch.
    fun exitRecentlyAdded() {
        val target = shelfState.exitRecentlyAdded()
        viewModel.setView(target)
    }

    // ---------------------------------------------------------------- applyView

    /** The single fan-out point for a shelf-view change: drawer indicator,
     * title, FAB, menu invalidation, Home visibility, and the Currently
     * Reading swipe-to-dismiss wiring. */
    fun applyView(view: ShelfView) {
        // Up affordance: top-level views show the hamburger (opens drawer);
        // detail views (author/series) and the Patch 16 "Recently Added" temp
        // screen show a back arrow that returns to the previous view — it must
        // NOT open the drawer. (Bug: previously the toggle intercepted the
        // back-arrow click and opened the drawer instead.)
        val isDetail = ShelfStateStore.isDetail(view)
        val isRecentlyAdded = ShelfStateStore.isRecentlyAdded(view)
        drawerToggle.isDrawerIndicatorEnabled = !isDetail && !isRecentlyAdded
        if (isDetail || isRecentlyAdded) {
            // Patch 12: when the drawer indicator is disabled,
            // ActionBarDrawerToggle draws NO icon on its own — so the back
            // arrow was invisible on author/series detail. Explicitly set the
            // up-indicator drawable so the back arrow renders and is
            // tappable.
            drawerToggle.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            drawerToggle.setToolbarNavigationClickListener {
                if (isRecentlyAdded) exitRecentlyAdded() else returnToParentList()
            }
        } else {
            // Re-enable the hamburger. Passing 0 clears any previously-set
            // up-indicator so the toggle's own drawer indicator takes over
            // again.
            drawerToggle.setHomeAsUpIndicator(0)
            drawerToggle.setToolbarNavigationClickListener {
                binding.drawerRoot.openDrawer(GravityCompat.START)
            }
        }
        drawerToggle.syncState()
        binding.toolbar.title = titleFor(view)
        config.drawerAdapter.setSelected(view)
        config.activity.invalidateOptionsMenu()
        binding.homeContent.root.visibility = if (view is ShelfView.Home) View.VISIBLE else View.GONE
        if (view is ShelfView.Home) {
            binding.recycler.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            config.homeController.visible = true
            // Re-render the cached Home content so Continue Reading reflects
            // the latest DB state even if the LiveData emitted while Home was
            // hidden, and always scroll Home to the top.
            config.homeController.onShown()
        } else {
            config.homeController.visible = false
        }
        updateFab(view)
        binding.refresh.isEnabled = true // Patch 12: keep the refresh layout
        // always enabled so the scanning spinner stays visible on EVERY
        // view — previously it was disabled on non-book views
        // (Authors/Series/Folders/Settings) and a scan started on Folders
        // appeared to "stop" the moment the user navigated away, because the
        // spinner was no longer drawn. The scan job itself was never
        // cancelled; only the spinner vanished. Now the spinner remains on
        // screen until the scan finishes, and pull-to-refresh also works
        // everywhere.

        // Swipe-to-dismiss only on the Currently Reading list
        config.libraryController.detachTouchHelper()
        if (view is ShelfView.Reading) {
            val cb =
                object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                    override fun onMove(
                        rv: RecyclerView,
                        vh: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder,
                    ) = false

                    override fun onSwiped(
                        viewHolder: RecyclerView.ViewHolder,
                        dir: Int,
                    ) {
                        val pos = viewHolder.bindingAdapterPosition
                        val books = config.libraryController.bookAdapter?.currentList ?: return
                        if (pos in books.indices) {
                            val book = books[pos]
                            viewModel.clearCurrentlyReading(book.id)
                            CurrentlyReadingUndoSnackbar.show(config.activity, binding.root, book.id)
                        }
                    }
                }
            config.libraryController.attachTouchHelper(ItemTouchHelper(cb))
        }
        config.activity.invalidateOptionsMenu()
    }

    // ---------------------------------------------------------------- FAB

    fun updateFab(view: ShelfView) {
        when (view) {
            is ShelfView.Library -> {
                binding.fabScan.show()
                binding.fabScan.setImageResource(R.drawable.ic_add)
                binding.fabScan.contentDescription = str(R.string.action_add)
            }
            // Patch 12: the Folders view already shows its action buttons
            // inline (Scan Now / Select Folder / Remove). The bottom FAB here
            // only popped up the SAME options in a dialog — a duplicate the
            // user explicitly does not want. Hide the FAB on Folders so only
            // the inline buttons remain.
            is ShelfView.Folders -> binding.fabScan.hide()
            else -> binding.fabScan.hide()
        }
    }

    private fun str(res: Int): String = config.activity.getString(res)
}
