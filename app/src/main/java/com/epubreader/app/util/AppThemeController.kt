package com.epubreader.app.util

import android.app.Activity
import android.content.Context
import com.epubreader.app.R
import com.epubreader.app.data.PrefsManager

/**
 * Phase 10 — runtime app-theme switching.
 *
 * The app offers two app themes: **Original** (the existing mint/eggplant
 * `Theme.EpubReader`) and **Pastel** (the web-app-derived pink/plum
 * `Theme.EpubReader.Pastel`). Night, Sepia and High Contrast are no longer
 * offered as app themes.
 *
 * This controller is the single entry point activities call at the very start
 * of `onCreate` (before `super.onCreate`) so the resolved theme — and therefore
 * every `?attr/livre*` token — is correct before any view is inflated. The
 * reader-content theme ([PrefsManager.theme]) is a separate concern and is not
 * touched here.
 *
 * System-bar appearance is delegated to [SystemBarController], which becomes
 * theme-aware: Pastel paints light bars (dark icons on a pink bar); Original
 * and the reader's dark chrome paint black bars.
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
