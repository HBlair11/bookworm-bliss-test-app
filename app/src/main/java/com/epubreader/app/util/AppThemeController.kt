package com.epubreader.app.util

import android.app.Activity
import android.content.Context
import com.epubreader.app.R
import com.epubreader.app.data.PrefsManager

/**
 * Phase 10 — runtime app-theme switching.
 *
 * The app offers two app themes: **Original** (the mint/eggplant
 * `Theme.EpubReader`, which follows the system Day/Night setting via
 * `Theme.MaterialComponents.DayNight`) and **Pastel** (the web-app-derived
 * pink/plum `Theme.EpubReader.Pastel`, which is always light). The reader
 * content theme ([PrefsManager.theme]) is a separate concern.
 *
 * This controller is the single entry point activities call at the very start
 * of `onCreate` (before `super.onCreate`) so the resolved theme — and therefore
 * every `?attr/livre*` token — is correct before any view is inflated.
 *
 * System-bar appearance is delegated to [SystemBarController], which is
 * theme-aware: Pastel paints light bars (dark icons on a pink bar); Original
 * paints black bars with light icons in both day and night modes.
 */
object AppThemeController {

    /** Call at the top of an app activity's `onCreate`, before `super.onCreate`. */
    fun apply(activity: Activity) {
        val prefs = PrefsManager(activity.applicationContext)
        val styleRes = if (prefs.appTheme == PrefsManager.AppTheme.PASTEL) {
            R.style.Theme_EpubReader_Pastel
        } else {
            R.style.Theme_EpubReader
        }
        activity.setTheme(styleRes)
    }

    /** Whether the pastel app theme is currently selected. */
    fun isPastel(context: Context): Boolean =
        PrefsManager(context.applicationContext).appTheme == PrefsManager.AppTheme.PASTEL
}
