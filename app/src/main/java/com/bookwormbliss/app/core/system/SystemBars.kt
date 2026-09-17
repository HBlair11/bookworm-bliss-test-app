package com.bookwormbliss.app.core.system

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

object SystemBars {
    fun install(activity: Activity, root: View, backgroundColor: Int) {
        val window = activity.window
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            updateAppearance(activity, backgroundColor)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isLight(backgroundColor)) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else 0
        }
        applyInsets(root)
    }

    fun updateBackground(activity: Activity, backgroundColor: Int) {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) updateAppearance(activity, backgroundColor)
    }

    private fun updateAppearance(activity: Activity, color: Int) {
        val controller = activity.window.insetsController ?: return
        val light = isLight(color)
        var appearance = 0
        if (light) appearance = appearance or WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        if (light) appearance = appearance or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        controller.setSystemBarsAppearance(
            appearance,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }

    private fun applyInsets(root: View) {
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(baseLeft + bars.left, baseTop + bars.top, baseRight + bars.right, baseBottom + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(baseLeft + insets.systemWindowInsetLeft, baseTop + insets.systemWindowInsetTop, baseRight + insets.systemWindowInsetRight, baseBottom + insets.systemWindowInsetBottom)
            }
            insets
        }
        root.requestApplyInsets()
    }

    private fun isLight(color: Int): Boolean {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return (0.299 * r + 0.587 * g + 0.114 * b) > 170
    }
}
