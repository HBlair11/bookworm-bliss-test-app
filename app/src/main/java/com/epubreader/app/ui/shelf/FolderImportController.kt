package com.epubreader.app.ui.shelf

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.MainActivity
import com.epubreader.app.MetadataRefreshActivity
import com.epubreader.app.R
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.epub.EpubImporter
import com.epubreader.app.epub.MetadataRefreshReportStore
import com.epubreader.app.epub.RescanDecision
import com.epubreader.app.ui.BookshelfViewModel
import com.epubreader.app.ui.ShelfView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FolderImportController — extracted from MainActivity (Phase 8).
 *
 * Owns the Folders screen and every folder-scan / book-import flow: the SAF
 * tree scan (with its fingerprint-based skip path), the metadata refresh, the
 * multi-file import, and the transient "Recently Added" result screen entry.
 *
 * The ActivityResultContracts launchers themselves stay registered in
 * MainActivity (they must be created before onCreate completes); the Activity
 * delegates results straight into this controller.
 */
class FolderImportController(
    private val config: Config,
) {

    data class Config(
        val activity: MainActivity,
        val scope: CoroutineScope,
        val prefs: PrefsManager,
        val importer: EpubImporter,
        val viewModel: BookshelfViewModel,
        val shelfState: ShelfStateStore,
        val recycler: RecyclerView,
        val emptyState: LinearLayout,
        val emptyIcon: View,
        val emptyText: TextView,
        val emptyHint: TextView,
        val rows: EmptyStateRows,
        /** Anchor view for scan snackbars (the SwipeRefreshLayout). */
        val snackbarAnchor: androidx.swiperefreshlayout.widget.SwipeRefreshLayout,
        /** Root view for snackbars that need full-screen anchoring. */
        val rootAnchor: View,
        /** Launches the SAF tree picker (MainActivity's OpenDocumentTree). */
        val onPickFolder: () -> Unit,
        /** Handler used for short delayed UI clears. */
        val handler: android.os.Handler,
    )

    /** Patch 12: the folder scan runs as a tracked background job so it
     * survives in-app navigation (the user can leave the Folders screen
     * while a scan is running and it keeps going). The reference is also used
     * to guard against launching a duplicate concurrent scan when the user
     * taps Scan Now again. */
    private var scanJob: Job? = null

    private val activity get() = config.activity

    // ---------------------------------------------------------------- folders view

    /** Shows the Folders screen. Starts at the TOP of the layout instead of
     * vertically centered in the middle of the screen (Patch 11). */
    fun showFoldersView() {
        config.rows.clear()
        config.recycler.visibility = View.GONE
        config.emptyState.gravity = Gravity.START or Gravity.TOP
        config.emptyState.visibility = View.VISIBLE
        config.emptyIcon.visibility = View.GONE
        val uri = config.prefs.selectedFolderUri
        config.emptyText.visibility = View.VISIBLE
        config.emptyText.text = if (uri != null) {
            str(R.string.folder_selected, displayName(uri))
        } else {
            str(R.string.folder_none)
        }
        config.emptyHint.text = str(R.string.folder_hint)
        config.emptyHint.visibility = View.VISIBLE

        if (uri != null) {
            config.rows.addDynamicButton(R.string.folder_scan_now) { rescanSelectedFolder() }
            config.rows.addDynamicButton(R.string.folder_refresh_metadata) { refreshMetadataSelectedFolder() }
            config.rows.addDynamicButton(R.string.folder_select) { config.onPickFolder() }
            config.rows.addDynamicButton(R.string.folder_remove) { removeSelectedFolder() }
        } else {
            config.rows.addDynamicButton(R.string.folder_select) { config.onPickFolder() }
        }
    }

    fun showFoldersEmptyState() {
        showFoldersView()
    }

    /** The Folders options dialog (bottom FAB / overflow path). */
    fun showFolderOptionsDialog() {
        val items = arrayOf(
            str(R.string.folder_select),
            str(R.string.folder_scan_now),
            str(R.string.folder_refresh_metadata),
            str(R.string.folder_remove)
        )

        AlertDialog
            .Builder(activity)
            .setTitle(R.string.folder_options)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> config.onPickFolder()
                    1 -> rescanSelectedFolder()
                    2 -> refreshMetadataSelectedFolder()
                    3 -> removeSelectedFolder()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Drops the selected folder (the "Remove" folder action). */
    private fun removeSelectedFolder() {
        config.prefs.selectedFolderUri = null
        showFoldersEmptyState()
        Snackbar.make(config.snackbarAnchor, R.string.folder_none, Snackbar.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- scanning

    fun rescanSelectedFolder() {
        val uriString = config.prefs.selectedFolderUri
        if (uriString == null) {
            // The SwipeRefresh spinner must not stay spinning when no folder
            // is selected.
            config.snackbarAnchor.isRefreshing = false
            Snackbar.make(config.rootAnchor, R.string.scan_no_folder, Snackbar.LENGTH_LONG)
                .setAction(R.string.folder_select) { config.onPickFolder() }
                .show()
            return
        }
        scanFolder(Uri.parse(uriString), fromRefresh = true)
    }

    /** Scans the selected SAF tree. Re-scans are fast because (a) every epub
     *  is listed in one bulk cursor pass per directory instead of per-file
     *  DocumentFile metadata calls, (b) existing books are matched against an
     *  in-memory fingerprint map (single DB query) and skipped when size +
     *  mtime are unchanged, and (c) all new/changed books are committed in one
     *  transaction so the library list refreshes once at the end instead of
     *  once per book (no visible layout thrash while scanning). */
    fun scanFolder(treeUri: Uri, fromRefresh: Boolean) {
        // Patch 12: don't start a second scan while one is already running —
        // two concurrent scans on the same importer/DB would conflict and the
        // second would silently fail, which looked like "Scan Now doesn't do
        // anything."
        if (scanJob?.isActive == true) {
            Snackbar.make(
                config.snackbarAnchor, R.string.scan_already_running, Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        try {
            activity.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
        config.prefs.selectedFolderUri = treeUri.toString()
        config.viewModel.setScanning(true)
        // Tracked job: lives in the Activity lifecycle scope, which is NOT
        // tied to any one shelf view. Navigating Library -> Settings ->
        // Folders (or anywhere else) does NOT cancel this job, so the scan
        // keeps running and the spinner stays visible until it finishes.
        scanJob = config.scope.launch(Dispatchers.IO) {
            // Patch 12: wrap the whole scan in try/catch/finally so the
            // scanning spinner is ALWAYS cleared — even if listEpubFiles /
            // fingerprint map / commitImports throws. Without this, an
            // exception would leave setScanning(true) forever and the spinner
            // stuck.
            try {
                // (1) One cursor pass per directory — name + size + mtime
                //     for every epub.
                val files = config.importer.listEpubFiles(treeUri)
                // (2) One DB query → in-memory fingerprint map for the fast
                //     skip path.
                val fingerprints = config.importer.sourceFingerprintMap()
                // (3) Prepare (copy + parse + cover) every new/changed book
                //     WITHOUT touching the database. Heavy file IO stays
                //     outside the DB lock.
                val prepared = mutableListOf<EpubImporter.PreparedImport>()
                for (file in files) {
                    val matches = fingerprints[file.name].orEmpty()
                    // A stable source URI is the primary identity. Legacy
                    // rows with no source URI are deliberately processed
                    // once so their identity is upgraded; they must never be
                    // skipped solely by filename.
                    if (matches.any { fp ->
                            fp.sourceUri == file.uri.toString() &&
                                    RescanDecision.shouldSkip(
                                        existingFileSize = fp.fileSize,
                                        existingMtime = fp.sourceLastModified,
                                        sourceSize = file.size,
                                        sourceMtime = file.lastModified,
                                        cachedFileExists = File(fp.path).exists(),
                                    )
                        }
                    ) continue
                    try {
                        config.importer
                            .prepareImport(file.uri, file.name, file.size, file.lastModified)
                            ?.let { prepared += it }
                    } catch (_: Exception) {
                    }
                }
                // (4) Commit all new/changed books in ONE transaction so the
                //     library's Room Flow re-emits a single time at the end.
                val newIds = config.importer.commitImports(prepared)
                withContext(Dispatchers.Main) {
                    showScanResult(newIds)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(
                        config.snackbarAnchor, R.string.scan_failed, Snackbar.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    config.viewModel.setScanning(false)
                }
            }
        }
    }

    /**
     * Re-parses every EPUB currently in the selected folder for metadata
     * only.
     *
     * This intentionally does NOT use RescanDecision because the whole point
     * of this operation is to re-read metadata even when the file's size and
     * modified timestamp have not changed. The importer updates only
     * metadata columns; reading state and cached EPUB content remain
     * untouched.
     */
    fun refreshMetadataSelectedFolder() {
        val uriString = config.prefs.selectedFolderUri
        if (uriString == null) {
            Snackbar
                .make(config.rootAnchor, R.string.scan_no_folder, Snackbar.LENGTH_LONG)
                .setAction(R.string.folder_select) { config.onPickFolder() }
                .show()
            return
        }

        if (scanJob?.isActive == true) {
            Snackbar
                .make(
                    config.snackbarAnchor,
                    R.string.scan_already_running,
                    Snackbar.LENGTH_SHORT
                )
                .show()
            return
        }

        val treeUri = Uri.parse(uriString)

        // Match Scan Now's immediate visual feedback, but only for a brief
        // moment. The metadata refresh itself remains a background job and
        // does not keep the spinner visible or restrict navigation.
        config.viewModel.setScanning(true)
        config.handler.postDelayed({
            config.viewModel.setScanning(false)
        }, 1200L)

        // Metadata refresh is deliberately a silent background job. Unlike
        // the user-facing folder scan, it must not activate the
        // SwipeRefresh spinner or otherwise restrict navigation while it
        // runs. The tracked Job keeps duplicate refreshes from starting, and
        // the completion snackbar appears wherever the user is in the app
        // when the work finishes.
        scanJob = config.scope.launch(Dispatchers.IO) {
            try {
                val files = config.importer.listEpubFiles(treeUri)
                val result = config.importer.refreshMetadata(files)
                MetadataRefreshReportStore.latest = result
                withContext(Dispatchers.Main) {
                    val snackbar = Snackbar.make(
                        config.snackbarAnchor,
                        str(
                            R.string.metadata_refresh_summary,
                            result.updated,
                            result.unchanged,
                            result.skipped,
                            result.failed,
                        ),
                        Snackbar.LENGTH_LONG
                    )
                    if (result.items.isNotEmpty()) {
                        snackbar.setAction(R.string.scan_show) {
                            activity.startActivity(
                                Intent(activity, MetadataRefreshActivity::class.java)
                            )
                        }
                    }
                    snackbar.show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar
                        .make(
                            config.snackbarAnchor,
                            R.string.scan_failed,
                            Snackbar.LENGTH_LONG
                        )
                        .show()
                }
            } finally {
                // No scanning-state UI is used for metadata refresh.
            }
        }
    }

    /** Multi-file import (Add books via the SAF file picker). */
    fun importMultiple(uris: List<Uri>) {
        if (uris.isEmpty()) return
        config.viewModel.setScanning(true)
        config.scope.launch(Dispatchers.IO) {
            val newIds = mutableListOf<Long>()
            for (uri in uris) {
                try {
                    val result = config.importer.importUriResult(uri)
                    if (result.isNew && result.bookId != null) newIds.add(result.bookId)
                } catch (_: Exception) {
                }
            }
            withContext(Dispatchers.Main) {
                config.viewModel.setScanning(false)
                showScanResult(newIds)
            }
        }
    }

    /** Patch 16 (Issue #3): shows the scan-complete snackbar with a "Show"
     * action that opens the transient Recently Added screen scoped to
     * exactly the newly imported ids. */
    fun showScanResult(newIds: List<Long>) {
        val newCount = newIds.size
        val text = if (newCount > 0)
            str(R.string.scan_complete_new, newCount)
        else
            str(R.string.scan_complete_none)
        val snackbar = Snackbar.make(config.snackbarAnchor, text, Snackbar.LENGTH_LONG)
        if (newCount > 0) {
            snackbar.setAction(R.string.scan_show) {
                // Instead of dumping the user on the Library and making them
                // scroll to find the new book(s), remember the shelf they're
                // currently on and open the transient Recently Added screen
                // scoped to just the newly imported ids. The screen is
                // destroyed (state discarded) on back — see
                // ShelfStateStore.exitRecentlyAdded().
                config.shelfState.enterRecentlyAdded(
                    config.viewModel.view.value ?: ShelfView.Library
                )
                config.viewModel.setView(ShelfView.RecentlyAdded(newIds))
            }
        }
        snackbar.show()
    }

    private fun str(res: Int, vararg args: Any): String = activity.getString(res, *args)

    companion object {
        /** Short display name for a SAF tree URI (e.g. "Books" instead of
         * the raw document id). Falls back to the raw string. */
        fun displayName(uriString: String): String {
            return try {
                val treeUri = Uri.parse(uriString)
                val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                docId.substringAfterLast(':').ifBlank { docId }
            } catch (_: Exception) {
                uriString
            }
        }
    }
}
