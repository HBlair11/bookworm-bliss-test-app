package com.epubreader.app.ui.shelf

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.R
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.ui.ReaderTheme
import com.epubreader.app.ui.ReaderSettingsActivity

/**
 * SettingsScreenController — extracted from MainActivity (Phase 8), rebuilt for
 * Phase 10.
 *
 * Renders the Settings screen into the shared empty-state container as
 * programmatic rows (the proven Phase 8 pattern — no generated ViewBinding IDs
 * to drift). The row builders live in [EmptyStateRows]; this controller owns
 * the section structure translated from the web app's SettingsView:
 *
 *   App Theme            — Original / Pastel picker (runtime re-skin)
 *   Reading Goal         — minutes/day stepper + show-on-stats toggle
 *   Reader Theme         — current theme, opens the reader settings activity
 *   Typography & Sizing   — opens the reader settings activity (font/size/spacing)
 *   Phone Screen On       — existing keep-awake toggle
 *   Full Backup & Restore — Export / Import (Merge) / Import (Replace)
 *   Maintenance          — Clear Search History, Reload Sample Books
 *   Privacy & Architecture — static guarantee + architecture note
 *
 * Per the Phase 10 spec, the old "Scanned Folder" and label-only "Reader Theme"
 * rows and the About/Privacy child navigation are removed; About/Privacy is
 * replaced by the inline Privacy & Architecture section.
 *
 * Heavy work (JSON, DAO) is NOT done here — [Callbacks] hands those to
 * [com.epubreader.app.MainActivity], which owns the ActivityResult launchers
 * and delegates the actual export/import to
 * [com.epubreader.app.service.BackupRestoreService].
 */
class SettingsScreenController(
    private val config: Config,
) {

    interface Callbacks {
        /** Export the full backup to a user-chosen document URI. */
        fun onExportBackup()

        /** Import a backup document, merging into existing data. */
        fun onImportBackupMerge()

        /** Import a backup document, replacing all existing data first. */
        fun onImportBackupReplace()

        /** Delete every row from the search_history table. */
        fun onClearSearchHistory()

        /** Re-import the bundled sample books. */
        fun onReloadSampleBooks()

        /** Called after the app theme changes so the activity can recreate(). */
        fun onAppThemeChanged()
    }

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
        val callbacks: Callbacks,
    )

    fun show() {
        config.rows.clear()
        config.recycler.visibility = View.GONE
        config.emptyState.visibility = View.VISIBLE
        config.emptyState.gravity = android.view.Gravity.START or android.view.Gravity.TOP
        config.emptyIcon.visibility = View.GONE
        config.emptyText.visibility = View.GONE
        config.emptyHint.visibility = View.GONE

        val ctx = config.emptyState.context
        val rows = config.rows
        val prefs = config.prefs
        val cb = config.callbacks

        // ---- App Theme ----
        rows.addSectionHeader(ctx.getString(R.string.settings_section_app_theme))
        rows.addThemePickerRow(
            current = prefs.appTheme,
            options = listOf(
                PrefsManager.AppTheme.ORIGINAL to ctx.getString(R.string.theme_original),
                PrefsManager.AppTheme.PASTEL to ctx.getString(R.string.theme_pastel),
            ),
        ) { selected ->
            if (selected != prefs.appTheme) {
                prefs.appTheme = selected
                cb.onAppThemeChanged()
            }
        }

        // ---- Reading Goal ----
        rows.addSectionHeader(ctx.getString(R.string.settings_section_reading_goal))
        rows.addStepperRow(
            label = ctx.getString(R.string.settings_reading_goal),
            summary = ctx.getString(R.string.settings_reading_goal_summary),
            value = prefs.readingGoalMinutes,
            onMinus = { prefs.readingGoalMinutes = (prefs.readingGoalMinutes - 5).coerceIn(0, 240) },
            onPlus = { prefs.readingGoalMinutes = (prefs.readingGoalMinutes + 5).coerceIn(0, 240) },
            valueText = { "${prefs.readingGoalMinutes} ${ctx.getString(R.string.settings_minutes_day)}" },
        )
        rows.addToggleRow(
            label = ctx.getString(R.string.settings_show_goal_on_stats),
            summary = ctx.getString(R.string.settings_show_goal_on_stats_summary),
            checked = prefs.showReadingGoalOnStats,
        ) { checked -> prefs.showReadingGoalOnStats = checked }

        // ---- Reader Theme + Typography & Sizing (open the reader settings) ----
        rows.addSectionHeader(ctx.getString(R.string.settings_section_reader))
        rows.addSettingsRow(
            ctx.getString(R.string.settings_reader_theme),
            ctx.getString(ReaderTheme.byId(prefs.theme).displayNameRes),
        ) { ctx.startActivity(Intent(ctx, ReaderSettingsActivity::class.java)) }
        rows.addSettingsRow(
            ctx.getString(R.string.settings_typography),
            ctx.getString(R.string.settings_typography_summary),
        ) { ctx.startActivity(Intent(ctx, ReaderSettingsActivity::class.java)) }

        // ---- Phone Screen On (kept) ----
        rows.addSectionHeader(ctx.getString(R.string.settings_section_screen))
        rows.addScreenOnToggle(prefs) { config.onScreenOnChanged() }

        // ---- Full Backup & Restore ----
        rows.addSectionHeader(ctx.getString(R.string.settings_section_backup))
        rows.addFullWidthButton(ctx.getString(R.string.settings_backup_export)) { cb.onExportBackup() }
        rows.addFullWidthButton(ctx.getString(R.string.settings_backup_import_merge)) { cb.onImportBackupMerge() }
        rows.addFullWidthButton(ctx.getString(R.string.settings_backup_import_replace)) { cb.onImportBackupReplace() }

        // ---- Maintenance & Search History ----
        rows.addSectionHeader(ctx.getString(R.string.settings_section_maintenance))
        rows.addFullWidthButton(ctx.getString(R.string.settings_clear_search_history)) { cb.onClearSearchHistory() }
        rows.addFullWidthButton(ctx.getString(R.string.settings_reload_samples)) { cb.onReloadSampleBooks() }

        // ---- Privacy & Architecture ----
        rows.addSectionHeader(ctx.getString(R.string.settings_section_privacy))
        rows.addParagraph(ctx.getString(R.string.settings_privacy_body))
        rows.addParagraph(ctx.getString(R.string.settings_architecture_body))
    }
}
