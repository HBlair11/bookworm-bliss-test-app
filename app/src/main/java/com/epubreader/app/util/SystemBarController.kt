package com.epubreader.app.util

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.epubreader.app.data.PrefsManager

/** Keeps phone system-bar areas visually consistent across every app screen.
 *
 * Android 15 enforces edge-to-edge for apps targeting API 35, so simply setting
 * statusBarColor/navigationBarColor is not sufficient there. The inset views
 * cover the transparent system-bar areas without changing the existing screen
 * layouts or reader content.
 *
 * Phase 10: the bar appearance is now theme-aware. App activities pass
 * [forceDark] = false (the default) so the bars follow the selected app theme —
 * Pastel paints a light pink bar with dark icons, Original paints black bars.
 * The reader and its settings sheet pass [forceDark] = true so their static dark
 * chrome is preserved regardless of the app theme.
 */
object SystemBarController {
    private const val TOP_TAG = "livre_system_bar_top"
    private const val BOTTOM_TAG = "livre_system_bar_bottom"

    fun apply(activity: Activity, forceDark: Boolean = false) {
        val pastel = !forceDark &&
            PrefsManager(activity.applicationContext).appTheme == PrefsManager.AppTheme.PASTEL
        val barColor = if (pastel) {
            // pastel_light_bg #FFEEF2
            Color.parseColor("#FFEEF2")
        } else {
            Color.BLACK
        }
        val window = activity.window
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = barColor
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            // Pastel = light bar surface → dark icons; everything else = dark bar → light icons.
            isAppearanceLightStatusBars = pastel
            isAppearanceLightNavigationBars = pastel
        }

        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        if (Build.VERSION.SDK_INT < 35) return
        if (content.findViewWithTag<View>(TOP_TAG) == null) {
            content.addView(View(activity).apply {
                tag = TOP_TAG
                setBackgroundColor(barColor)
                isClickable = false
                isFocusable = false
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                0,
                android.view.Gravity.TOP,
            ))
        }
        if (content.findViewWithTag<View>(BOTTOM_TAG) == null) {
            content.addView(View(activity).apply {
                tag = BOTTOM_TAG
                setBackgroundColor(barColor)
                isClickable = false
                isFocusable = false
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                0,
                android.view.Gravity.BOTTOM,
            ))
        }
        // Re-color the existing inset views in case the theme changed at runtime.
        content.findViewWithTag<View>(TOP_TAG)?.setBackgroundColor(barColor)
        content.findViewWithTag<View>(BOTTOM_TAG)?.setBackgroundColor(barColor)

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val top = view.findViewWithTag<View>(TOP_TAG)
            val bottom = view.findViewWithTag<View>(BOTTOM_TAG)
            (top.layoutParams as? FrameLayout.LayoutParams)?.also { lp ->
                lp.height = bars.top
                top.layoutParams = lp
            }
            (bottom.layoutParams as? FrameLayout.LayoutParams)?.also { lp ->
                lp.height = bars.bottom
                bottom.layoutParams = lp
            }
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

}
