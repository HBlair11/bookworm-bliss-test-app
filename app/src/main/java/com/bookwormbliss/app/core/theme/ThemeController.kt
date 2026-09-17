package com.bookwormbliss.app.core.theme

import android.app.Activity
import com.bookwormbliss.app.R
import com.bookwormbliss.app.data.PrefsManager

/**
 * Applies the user-selected app theme (Original or Pastel) at runtime.
 *
 * Activities call [applyTheme] in their `onCreate()` before `super.onCreate()`:
 *
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     ThemeController.applyTheme(this)
 *     super.onCreate(savedInstanceState)
 *     ...
 * }
 * ```
 *
 * The theme is selected from [PrefsManager.appTheme] and applied via
 * [Activity.setTheme]. This overrides whatever theme is declared in the
 * AndroidManifest for that activity, allowing runtime theme switching
 * without restarting the app process.
 *
 * The reader activities (ReaderActivity, ReaderSettingsActivity) keep
 * their dark chrome theme regardless of the app theme selection — only
 * the main app screens (Home, Library, Book Details, Settings, etc.)
 * respond to the Original/Pastel toggle.
 */
object ThemeController {

    /**
     * Apply the user-selected app theme to the given activity.
     * Call this before `super.onCreate()`.
     */
    fun applyTheme(activity: Activity) {
        val prefs = PrefsManager(activity)
        val themeId = prefs.appTheme
        val themeResId = when (themeId) {
            PrefsManager.AppTheme.PASTEL -> R.style.Theme_BookwormBliss_Pastel
            else -> R.style.Theme_BookwormBliss_Original
        }
        activity.setTheme(themeResId)
    }

    /**
     * Check whether the pastel theme is currently active.
     */
    fun isPastel(activity: Activity): Boolean {
        return PrefsManager(activity).appTheme == PrefsManager.AppTheme.PASTEL
    }
}
