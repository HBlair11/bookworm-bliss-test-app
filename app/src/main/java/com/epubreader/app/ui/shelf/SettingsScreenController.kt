package com.epubreader.app.ui.shelf

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.R
import com.epubreader.app.AboutPrivacyActivity
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.ui.ReaderTheme

/**
 * SettingsScreenController — extracted from MainActivity (Phase 8).
 *
 * Renders the Settings screen into the shared empty-state container. The rows
 * are built by the shared [EmptyStateRows] helper so Folders and Settings
 * never leave stale rows behind in each other's view.
 */
class SettingsScreenController(
    private val config: Config,
) {

    data class Config(
        val recycler: RecyclerView,
        val emptyState: LinearLayout,
        val emptyIcon: View,
        val emptyText: TextView,
        val emptyHint: TextView,
        val rows: EmptyStateRows,
        val prefs: PrefsManager,
        /** Invoked after the Screen On toggle flips so the owning Activity
         * can refresh the shared KeepScreenOnController immediately. */
        val onScreenOnChanged: () -> Unit,
    )

    /** Shows the Settings screen. Screen starts at the TOP of the layout
     * instead of vertically centered (Patch 11); the top toolbar already
     * shows the view name, so no in-screen heading was kept. */
    fun show() {
        config.rows.clear()
        config.recycler.visibility = View.GONE
        config.emptyState.visibility = View.VISIBLE
        config.emptyState.gravity = android.view.Gravity.START or android.view.Gravity.TOP
        config.emptyIcon.visibility = View.GONE
        config.emptyText.visibility = View.GONE
        config.emptyHint.visibility = View.GONE

        val context = config.emptyState.context
        val folder = config.prefs.selectedFolderUri
        config.rows.addSettingsRow(
            context.getString(R.string.settings_scanned_folder),
            if (folder != null) FolderImportController.displayName(folder)
            else context.getString(R.string.folder_none)
        )
        config.rows.addSettingsRow(
            context.getString(R.string.settings_reader_theme),
            readerThemeLabel()
        )
        // Patch 11: app-level "Screen On" toggle — keeps the screen awake 10
        // minutes longer than the system timeout while the app is foreground.
        config.rows.addScreenOnToggle(config.prefs) { config.onScreenOnChanged() }
        // About/Privacy sits below the Screen On toggle so the user
        // encounters the privacy-forward about screen as the last item.
        config.rows.addSettingsRow(
            context.getString(R.string.settings_about),
            context.getString(R.string.settings_about_detail)
        ) {
            context.startActivity(Intent(context, AboutPrivacyActivity::class.java))
        }
    }

    /** Patch 17 (Addition #1): theme display name comes from the single-source
     * registry so the main-app settings row stays in sync with the reader
     * dropdown automatically. */
    private fun readerThemeLabel(): String =
        config.emptyState.context.getString(ReaderTheme.byId(config.prefs.theme).displayNameRes)
}
