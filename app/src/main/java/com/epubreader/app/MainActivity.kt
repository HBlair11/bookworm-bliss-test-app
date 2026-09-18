package com.epubreader.app

import com.epubreader.app.util.SystemBarController

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookRepository
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.databinding.ActivityMainBinding
import com.epubreader.app.epub.BookFileTypes
import com.epubreader.app.epub.EpubImporter
import com.epubreader.app.ui.BookshelfViewModel
import com.epubreader.app.ui.DisplayItem
import com.epubreader.app.ui.DrawerAdapter
import com.epubreader.app.ui.HomeContent
import com.epubreader.app.ui.ShelfView
import com.epubreader.app.ui.shelf.AppNavigationController
import com.epubreader.app.ui.shelf.EmptyStateRows
import com.epubreader.app.ui.shelf.FolderImportController
import com.epubreader.app.ui.shelf.HomeScreenController
import com.epubreader.app.ui.shelf.LibraryScreenController
import com.epubreader.app.ui.shelf.ScrollStateStore
import com.epubreader.app.ui.shelf.SettingsScreenController
import com.epubreader.app.ui.shelf.ShelfStateStore
import com.epubreader.app.util.CurrentlyReadingUndoSnackbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STATE_SHELF = "state_shelf"
private const val STATE_DETAIL_NAME = "state_detail_name"

/**
 * MainActivity — the app shell (Phase 8 decomposition).
 *
 * After Phase 8 this Activity is pure screen wiring: it inflates the layout,
 * registers the ActivityResult launchers and observers, forwards lifecycle
 * events, and owns the options menu + its dialogs. Every piece of independent
 * responsibility moved out:
 *
 *  - ShelfStateStore / ScrollStateStore — pure navigation + scroll-restore
 *    state machines (unit-tested).
 *  - AppNavigationController — drawer, titles, up-affordance, applyView.
 *  - HomeScreenController — the curated Home screen.
 *  - LibraryScreenController — shelf adapters, grid/list config, empty
 *    states, scroll capture/restore.
 *  - FolderImportController — Folders screen, scans, imports.
 *  - SettingsScreenController — Settings screen rows.
 *
 * The activity still owns what must: launchers (registered before onCreate
 * completes), the back-press double-tap exit, the reader/details intents,
 * and the toolbar dialogs.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var importer: EpubImporter
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var drawerAdapter: DrawerAdapter

    private var backPressedOnce = false
    private val backResetHandler = Handler(Looper.getMainLooper())

    private val viewModel: BookshelfViewModel by viewModels {
        BookshelfViewModel.Factory(BookRepository(applicationContext), PrefsManager(applicationContext))
    }

    // Patch 11 "Screen On" controller (see util/KeepScreenOnController.kt).
    private lateinit var keepScreenOnController: com.epubreader.app.util.KeepScreenOnController

    // ------------------------------------------------------------- Phase 8 components
    private val shelfState = ShelfStateStore()
    private val scrollState = ScrollStateStore()
    private lateinit var emptyStateRows: EmptyStateRows
    private lateinit var homeController: HomeScreenController
    private lateinit var libraryController: LibraryScreenController
    private lateinit var folderController: FolderImportController
    private lateinit var settingsController: SettingsScreenController
    private lateinit var navigationController: AppNavigationController

    private val openTreeLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri -> uri?.let { folderController.scanFolder(it, fromRefresh = false) } }

    private val openMultiFileLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris -> folderController.importMultiple(uris) }

    // Phase 10: Full Backup & Restore launchers. JSON/DAO work is delegated to
    // BackupRestoreService; MainActivity only owns the ActivityResult wiring.
    private val backupService by lazy {
        com.epubreader.app.service.BackupRestoreService(applicationContext)
    }
    private var pendingImportMode: com.epubreader.app.service.BackupRestoreService.RestoreMode? = null
    private val createBackupLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(com.epubreader.app.service.BackupRestoreService.MIME_TYPE),
        ) { uri ->
            if (uri != null) exportBackupTo(uri)
        }
    private val openBackupLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            val mode = pendingImportMode
            pendingImportMode = null
            if (uri != null && mode != null) importBackupFrom(uri, mode)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PrefsManager(applicationContext)
        importer = EpubImporter(applicationContext)
        keepScreenOnController = com.epubreader.app.util.KeepScreenOnController(this, prefs)
        // Phase 10: apply the selected app theme (Original / Pastel) before any
        // view is inflated so every ?attr/livre* token resolves correctly.
        com.epubreader.app.util.AppThemeController.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)
        setSupportActionBar(binding.toolbar)

        emptyStateRows = EmptyStateRows(this, binding.emptyState)
        homeController = HomeScreenController(
            binding.homeContent,
            object : HomeScreenController.Callbacks {
                override fun onOpenBook(book: BookEntity) = this@MainActivity.openBook(book)
                override fun onBookLongPressed(book: BookEntity) = this@MainActivity.showBookOptions(book)
                override fun onOpenDetails(book: BookEntity) = this@MainActivity.openDetails(book)
                override fun onOpenAuthor(name: String) = this@MainActivity.openAuthorFromHome(name)
                override fun onOpenSeries(name: String) = this@MainActivity.openSeriesFromHome(name)
                override fun onViewAllAuthors() = this@MainActivity.viewAllAuthors()
                override fun onViewAllSeries() = this@MainActivity.viewAllSeries()
                override fun onViewAllFavorites() = this@MainActivity.viewAllFavorites()
            },
        )
        libraryController = LibraryScreenController(
            LibraryScreenController.Config(
                recycler = binding.recycler,
                emptyState = binding.emptyState,
                emptyIcon = binding.emptyIcon,
                emptyText = binding.emptyText,
                emptyHint = binding.emptyHint,
                emptyAction = binding.emptyAction,
                viewModel = viewModel,
                shelfState = shelfState,
                scrollState = scrollState,
                callbacks = object : LibraryScreenController.Callbacks {
                    override fun onOpenBook(book: BookEntity) = this@MainActivity.openBook(book)
                    override fun onBookLongPressed(book: BookEntity) = this@MainActivity.showBookOptions(book)
                    override fun onOpenDetails(book: BookEntity) = this@MainActivity.openDetails(book)
                    override fun onOpenDetail(name: String) = viewModel.openDetail(name)
                },
                onPickFiles = { openMultiFileLauncher.launch(BookFileTypes.acceptedMimeTypes) },
                rows = emptyStateRows,
            )
        )
        folderController = FolderImportController(
            FolderImportController.Config(
                activity = this,
                scope = lifecycleScope,
                prefs = prefs,
                importer = importer,
                viewModel = viewModel,
                shelfState = shelfState,
                recycler = binding.recycler,
                emptyState = binding.emptyState,
                emptyIcon = binding.emptyIcon,
                emptyText = binding.emptyText,
                emptyHint = binding.emptyHint,
                rows = emptyStateRows,
                snackbarAnchor = binding.refresh,
                rootAnchor = binding.root,
                onPickFolder = { openTreeLauncher.launch(null) },
                handler = backResetHandler,
            )
        )
        settingsController = SettingsScreenController(
            SettingsScreenController.Config(
                recycler = binding.recycler,
                emptyState = binding.emptyState,
                emptyIcon = binding.emptyIcon,
                emptyText = binding.emptyText,
                emptyHint = binding.emptyHint,
                rows = emptyStateRows,
                prefs = prefs,
                onScreenOnChanged = { keepScreenOnController.refresh() },
                callbacks = object : SettingsScreenController.Callbacks {
                    override fun onExportBackup() =
                        createBackupLauncher.launch("bookworm-bliss-backup${com.epubreader.app.service.BackupRestoreService.FILE_SUFFIX}")
                    override fun onImportBackupMerge() {
                        pendingImportMode = com.epubreader.app.service.BackupRestoreService.RestoreMode.MERGE
                        openBackupLauncher.launch(arrayOf(com.epubreader.app.service.BackupRestoreService.MIME_TYPE))
                    }
                    override fun onImportBackupReplace() {
                        pendingImportMode = com.epubreader.app.service.BackupRestoreService.RestoreMode.REPLACE
                        openBackupLauncher.launch(arrayOf(com.epubreader.app.service.BackupRestoreService.MIME_TYPE))
                    }
                    override fun onClearSearchHistory() = clearSearchHistory()
                    override fun onReloadSampleBooks() = reloadSampleBooks()
                    override fun onAppThemeChanged() = recreate()
                },
            )
        )
        drawerAdapter = DrawerAdapter { item -> navigationController.selectDrawer(item) }
        navigationController = AppNavigationController(
            AppNavigationController.Config(
                activity = this,
                binding = binding,
                viewModel = viewModel,
                shelfState = shelfState,
                scrollState = scrollState,
                homeController = homeController,
                libraryController = libraryController,
                drawerAdapter = drawerAdapter,
            )
        )

        // Create the drawer toggle BEFORE setupObservers() so that when the
        // viewModel.view observer fires (which calls applyView → accesses
        // drawerToggle), the toggle is already initialized. Previously the
        // toggle was created after setupObservers(), causing a crash when the
        // observer delivered a restored view.
        drawerToggle =
            ActionBarDrawerToggle(
                this,
                binding.drawerRoot,
                binding.toolbar,
                R.string.nav_open,
                R.string.nav_close,
            ).also { toggle ->
                binding.drawerRoot.addDrawerListener(toggle)
                toggle.syncState()
            }
        navigationController.drawerToggle = drawerToggle

        restoreViewFromSavedState(savedInstanceState)
        navigationController.setupDrawer()
        homeController.setup()
        binding.refresh.setOnRefreshListener { folderController.rescanSelectedFolder() }
        setupObservers()
        binding.fabScan.setOnClickListener { onFabClicked() }
        binding.homeContent.homeEmptyAction.setOnClickListener {
            openMultiFileLauncher.launch(BookFileTypes.acceptedMimeTypes)
        }
        navigationController.updateFab(viewModel.view.value ?: ShelfView.Home)

        // Patch 18 (Addition #3): if launched by the system "Open with" for an
        // .epub, import it and jump straight into the reader.
        handleViewIntent(intent)

        // Explicitly apply the current view once now that the toggle is ready
        // and observers are registered, so the drawer indicator, title, and
        // up-affordance are all correct on launch.
        navigationController.applyView(viewModel.view.value ?: ShelfView.Home)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        binding.drawerRoot.isOpen -> binding.drawerRoot.close()
                        viewModel.view.value is ShelfView.RecentlyAdded -> {
                            navigationController.exitRecentlyAdded()
                        }

                        viewModel.isDetailOpen() -> {
                            navigationController.returnToParentList()
                        }

                        else -> {
                            if (backPressedOnce) {
                                backResetHandler.removeCallbacksAndMessages(null)
                                finish()
                                return
                            }
                            backPressedOnce = true
                            Snackbar.make(binding.root, R.string.back_again_to_exit, Snackbar.LENGTH_SHORT)
                                .addCallback(object : Snackbar.Callback() {
                                    override fun onDismissed(s: Snackbar?, event: Int) {
                                        backPressedOnce = false
                                    }
                                })
                                .show()
                        }
                    }
                }
            },
        )
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::drawerToggle.isInitialized) drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val snapshot = shelfState.snapshotForSave(viewModel.view.value ?: ShelfView.Home)
        if (snapshot != null) {
            outState.putString(STATE_SHELF, snapshot.key)
            snapshot.detailName?.let { outState.putString(STATE_DETAIL_NAME, it) }
        }
    }

    private fun restoreViewFromSavedState(savedInstanceState: Bundle?) {
        val key = savedInstanceState?.getString(STATE_SHELF) ?: return
        val detailName = savedInstanceState.getString(STATE_DETAIL_NAME)
        shelfState.restore(ShelfStateStore.SavedShelf(key, detailName))?.let { viewModel.setView(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Patch 18 (Addition #3): re-handle a VIEW intent delivered to the
        // already-running (singleTop) instance.
        handleViewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        keepScreenOnController.onResume()
        if (shelfState.pendingReadingTopReset) {
            shelfState.pendingReadingTopReset = false
            libraryController.applyReadingTopReset()
        }
        // Returning from the reader / book-details activity lands here. If a
        // restore was queued and the list content has already re-emitted before
        // this point, try once more now (harmless if there is nothing to
        // restore or the adapter is still empty — tryRestoreScroll guards
        // both).
        if (scrollState.pendingRestoreKey != null) {
            binding.recycler.post { libraryController.tryRestoreScroll() }
        }
        binding.root.post {
            CurrentlyReadingUndoSnackbar.showPending(this, binding.root)
        }
    }

    override fun onPause() {
        super.onPause()
        keepScreenOnController.onPause()
    }

    // ---------------------------------------------------------------- observers

    private fun setupObservers() {
        viewModel.view.observe(this) { view -> navigationController.applyView(view) }

        // View mode + column count apply immediately: reconfigure the adapter
        // without refetching.
        viewModel.viewModeGrid.observe(this) {
            libraryController.reconfigureAdapter()
            invalidateOptionsMenu()
        }
        viewModel.gridColumns.observe(this) { libraryController.reconfigureAdapter() }

        viewModel.homeContent.observe(this) { content: HomeContent ->
            homeController.render(content)
        }

        viewModel.content.observe(this) { items ->
            val view = viewModel.view.value ?: ShelfView.Library
            if (view is ShelfView.Home) return@observe

            if (view is ShelfView.Settings) {
                binding.emptyAction.visibility = View.GONE
                settingsController.show()
                return@observe
            }

            if (ShelfStateStore.isFoldersView(view)) {
                binding.emptyAction.visibility = View.GONE
                folderController.showFoldersView()
                return@observe
            }

            if (ShelfStateStore.isPlaceholder(view)) {
                showPlaceholderView()
                return@observe
            }

            libraryController.renderItems(items, view)
        }

        viewModel.scanning.asLiveData().observe(this) { binding.refresh.isRefreshing = it }
        viewModel.scanMessage.asLiveData().observe(this) { msg ->
            msg?.let {
                Snackbar.make(binding.refresh, it, Snackbar.LENGTH_SHORT).show()
                viewModel.setScanMessage(null)
            }
        }
    }

    /** The Collections "coming soon" placeholder screen. */
    private fun showPlaceholderView() {
        emptyStateRows.clear()
        binding.emptyState.visibility = View.VISIBLE
        binding.emptyState.gravity = android.view.Gravity.CENTER
        binding.recycler.visibility = View.GONE
        binding.emptyIcon.visibility = View.GONE
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = getString(R.string.empty_placeholder)
        binding.emptyHint.text = getString(R.string.coming_soon)
        binding.emptyHint.visibility = View.VISIBLE
        binding.emptyAction.visibility = View.GONE
    }

    // ---------------------------------------------------------------- menu

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bookshelf, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val view = viewModel.view.value ?: ShelfView.Library
        val bookView = ShelfStateStore.isBookView(view)
        val isSettings = view is ShelfView.Settings
        val isReading = view is ShelfView.Reading
        menu.findItem(R.id.action_view_mode)?.isVisible = bookView
        menu.findItem(R.id.action_sort)?.isVisible = bookView && !isReading
        menu.findItem(R.id.action_search)?.isVisible = !isSettings
        menu.findItem(R.id.action_import)?.isVisible = !isSettings
        menu.findItem(R.id.action_view_mode)?.setIcon(
            if (viewModel.viewModeGrid.value == true) R.drawable.ic_list else R.drawable.ic_grid,
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_search -> {
                launchSearch()
                true
            }

            R.id.action_sort -> {
                showSortDialog()
                true
            }

            R.id.action_view_mode -> {
                showViewModeDialog()
                true
            }

            R.id.action_import -> {
                openMultiFileLauncher.launch(BookFileTypes.acceptedMimeTypes)
                true
            }

            android.R.id.home -> {
                val view = viewModel.view.value ?: ShelfView.Home
                when {
                    ShelfStateStore.isRecentlyAdded(view) -> {
                        navigationController.exitRecentlyAdded()
                    }
                    ShelfStateStore.isDetail(view) -> {
                        navigationController.returnToParentList()
                    }
                    else -> {
                        binding.drawerRoot.openDrawer(androidx.core.view.GravityCompat.START)
                    }
                }
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    // ---------------------------------------------------------------- search (separate screen)
    private fun launchSearch() {
        startActivity(Intent(this, SearchActivity::class.java))
    }

    // ---------------------------------------------------------------- sort (field -> asc/desc -> refresh + scroll top)
    private fun showSortDialog() {
        val options =
            listOf(
                getString(R.string.sort_recently_added) to PrefsManager.SortOption.RECENTLY_ADDED,
                getString(R.string.sort_recently_read) to PrefsManager.SortOption.RECENTLY_READ,
                getString(R.string.sort_title) to PrefsManager.SortOption.TITLE,
                getString(R.string.sort_series) to PrefsManager.SortOption.SERIES,
                getString(R.string.sort_author) to PrefsManager.SortOption.AUTHOR,
            )
        val current = options.indexOfFirst { it.second == viewModel.sort.value }
        AlertDialog
            .Builder(this)
            .setTitle(R.string.action_sort)
            .setSingleChoiceItems(
                options.map { it.first }.toTypedArray(),
                if (current < 0) 0 else current
            ) { d, which ->
                d.dismiss()
                showSortDirectionDialog(options[which].first, options[which].second)
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSortDirectionDialog(
        fieldLabel: String,
        fieldKey: String,
    ) {
        val asc = viewModel.sortAscending.value ?: true
        val labels = if (fieldKey == PrefsManager.SortOption.RECENTLY_ADDED) {
            arrayOf(getString(R.string.sort_newer_first), getString(R.string.sort_older_first))
        } else {
            arrayOf(getString(R.string.sort_ascending), getString(R.string.sort_descending))
        }
        AlertDialog
            .Builder(this)
            .setTitle("$fieldLabel · ${getString(R.string.sort_choose_order)}")
            .setSingleChoiceItems(labels, if (asc) 0 else 1) { d, which ->
                shelfState.scrollToTopOnNextContent = true
                viewModel.setSort(fieldKey, which == 0)
                d.dismiss()
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- view mode (List / Grid 2 / Grid 3 / Grid 4)
    private fun showViewModeDialog() {
        val grid = viewModel.viewModeGrid.value == true
        val cols = viewModel.gridColumns.value ?: 3
        val labels =
            arrayOf(
                getString(R.string.view_mode_list),
                getString(R.string.view_mode_grid) + " 2",
                getString(R.string.view_mode_grid) + " 3",
                getString(R.string.view_mode_grid) + " 4",
            )
        val checked = if (!grid) 0 else (cols - 1).coerceIn(1, 3)
        AlertDialog
            .Builder(this)
            .setTitle(R.string.action_view_mode)
            .setSingleChoiceItems(labels, checked) { d, which ->
                when (which) {
                    0 -> {
                        viewModel.setViewMode(false)
                    }

                    else -> {
                        viewModel.setViewMode(true)
                        viewModel.setGridColumns(which + 1)
                    }
                }
                d.dismiss()
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- book options
    private fun showBookOptions(book: BookEntity): Boolean {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        labels += getString(R.string.option_open)
        actions += { openBook(book) }
        labels += getString(R.string.option_details)
        actions += { openDetails(book) }
        labels += if (book.isFavorite) getString(R.string.option_favorite_remove) else getString(R.string.option_favorite_add)
        actions += { viewModel.toggleFavorite(book) }
        if (viewModel.view.value is ShelfView.Reading) {
            labels += getString(R.string.option_remove_reading)
            actions += { viewModel.clearCurrentlyReading(book.id) }
        }
        labels += getString(R.string.option_remove)
        actions += { confirmDeleteBook(book) }
        AlertDialog
            .Builder(this)
            .setTitle(book.title)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
        return true
    }

    private fun confirmDeleteBook(book: BookEntity) {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.option_remove)
            .setMessage(getString(R.string.option_remove) + ": " + book.title)
            .setPositiveButton(R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    importer.deleteImported(book)
                    BookRepository(applicationContext).deleteBook(book)
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- FAB
    private fun onFabClicked() {
        when (val view = viewModel.view.value ?: ShelfView.Library) {
            is ShelfView.Library -> openMultiFileLauncher.launch(BookFileTypes.acceptedMimeTypes)
            is ShelfView.Folders -> folderController.showFolderOptionsDialog()
            else -> {}
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        keepScreenOnController.bump()
    }

    /**
     * Patch 18 (Addition #3): handles an incoming ACTION_VIEW intent for an
     * .epub (from the system "Open with" sheet / a file manager). Validates
     * the file type client-side (defense in depth — the intent filter already
     * scopes to epub MIME + .epub path), imports it, and opens the reader.
     */
    private fun handleViewIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (!BookFileTypes.isBookFile(uri, contentResolver)) {
            Snackbar.make(binding.fabScan, R.string.import_unsupported_file, Snackbar.LENGTH_SHORT).show()
            return
        }
        viewModel.setScanning(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val bookId = try {
                importer.importUri(uri)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                viewModel.setScanning(false)
                if (bookId != null) {
                    startActivity(
                        Intent(this@MainActivity, ReaderActivity::class.java)
                            .putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                } else {
                    Snackbar.make(binding.fabScan, R.string.import_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openBook(book: BookEntity) {
        viewModel.markOpened(book.id)
        // Patch 11 / Fix #4: capture this view's scroll position before
        // navigating away. Currently Reading restores this position when
        // returning from Book Details, while the reader path below continues
        // to use its existing top-reset behaviour.
        libraryController.captureScrollState(book.id)
        viewModel.view.value?.let {
            if (it is ShelfView.Reading) {
                shelfState.pendingReadingTopReset = true
            } else if (ScrollStateStore.isRestoreEligible(it)) {
                scrollState.queueRestore(it)
            }
        }
        startActivity(Intent(this, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id))
    }

    private fun openDetails(book: BookEntity) {
        libraryController.captureScrollState(book.id)
        viewModel.view.value?.let {
            if (ScrollStateStore.isRestoreEligible(it)) {
                scrollState.queueRestore(it)
            }
        }
        startActivity(
            Intent(this, BookDetailsActivity::class.java).putExtra(
                BookDetailsActivity.EXTRA_BOOK_ID,
                book.id
            )
        )
    }

    // ---------------------------------------------------------------- Phase 10: Home navigation

    /** Opens an author's bookshelf from Home with fromHome=true so back returns
     *  to Home, not the Authors list. Also captures Home scroll for restore. */
    private fun openAuthorFromHome(name: String) {
        scrollState.capture(
            ShelfView.Home,
            com.epubreader.app.ui.shelf.ScrollAnchor(
                firstVisiblePosition = 0,
                firstVisibleOffset = 0,
                clickedBookId = null,
                clickedBookPosition = -1,
                clickedBookTopOffset = 0,
            )
        )
        viewModel.openAuthorFromHome(name)
        shelfState.scrollToTopOnNextContent = true
    }

    /** Opens a series' bookshelf from Home with fromHome=true so back returns
     *  to Home, not the Series list. */
    private fun openSeriesFromHome(name: String) {
        scrollState.capture(
            ShelfView.Home,
            com.epubreader.app.ui.shelf.ScrollAnchor(
                firstVisiblePosition = 0,
                firstVisibleOffset = 0,
                clickedBookId = null,
                clickedBookPosition = -1,
                clickedBookTopOffset = 0,
            )
        )
        viewModel.openSeriesFromHome(name)
        shelfState.scrollToTopOnNextContent = true
    }

    /** Navigate to the full Authors list from Home, scrolling to top. */
    private fun viewAllAuthors() {
        shelfState.scrollToTopOnNextContent = true
        scrollState.clearPendingRestore()
        shelfState.onDrawerNavigation()
        viewModel.setView(ShelfView.AuthorsList)
    }

    /** Navigate to the full Series list from Home, scrolling to top. */
    private fun viewAllSeries() {
        shelfState.scrollToTopOnNextContent = true
        scrollState.clearPendingRestore()
        shelfState.onDrawerNavigation()
        viewModel.setView(ShelfView.SeriesList)
    }

    /** Navigate to the full Favorites shelf from Home. */
    private fun viewAllFavorites() {
        shelfState.scrollToTopOnNextContent = true
        scrollState.clearPendingRestore()
        shelfState.onDrawerNavigation()
        viewModel.setView(ShelfView.Favorites)
    }

    // ---------------------------------------------------------------- Phase 10: backup / restore / search history

    private fun exportBackupTo(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val json = try {
                backupService.export()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.fabScan, R.string.settings_backup_export_failed, Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: throw java.io.IOException()
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.fabScan, R.string.settings_backup_export_done, Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.fabScan, R.string.settings_backup_export_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importBackupFrom(uri: android.net.Uri, mode: com.epubreader.app.service.BackupRestoreService.RestoreMode) {
        lifecycleScope.launch(Dispatchers.IO) {
            val json = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: throw java.io.IOException()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.fabScan, R.string.settings_backup_import_failed, Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }
            try {
                val summary = backupService.importBackup(json, mode)
                withContext(Dispatchers.Main) {
                    Snackbar.make(
                        binding.fabScan,
                        getString(R.string.settings_backup_import_done, summary),
                        Snackbar.LENGTH_LONG,
                    ).show()
                    // A restore may have changed the app theme or reader data;
                    // recreate so the new theme applies immediately.
                    recreate()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.fabScan, R.string.settings_backup_import_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun clearSearchHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            BookRepository(applicationContext).clearSearchHistory()
            withContext(Dispatchers.Main) {
                Snackbar.make(binding.fabScan, R.string.settings_search_history_cleared, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    /** No bundled sample books ship with the Android app, so "Reload sample
     *  books" re-scans the user's selected library folder (the closest
     *  functional equivalent) and surfaces a toast when no folder is set. */
    private fun reloadSampleBooks() {
        if (prefs.selectedFolderUri != null) {
            folderController.rescanSelectedFolder()
            Snackbar.make(binding.fabScan, R.string.settings_samples_reloaded, Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(binding.fabScan, R.string.folder_none, Snackbar.LENGTH_SHORT).show()
        }
    }
}
