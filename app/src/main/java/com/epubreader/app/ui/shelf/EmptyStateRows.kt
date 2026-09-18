package com.epubreader.app.ui.shelf

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.epubreader.app.R
import com.epubreader.app.data.PrefsManager

/**
 * EmptyStateRows — extracted from MainActivity (Phase 8).
 *
 * Shared machinery for the two screen controllers that render their content
 * into the shared `emptyState` container as dynamic programmatic views: the
 * Folders screen (inline action buttons) and the Settings screen (setting
 * rows + the Screen On toggle). The container is also used by the static
 * book-list empty states, so every render first clears whatever the previous
 * view added — that bookkeeping lives here.
 *
 * View construction stays activity-context based (it needs the theme to
 * resolve text colors and Material/MaterialComponents widgets); logic-only
 * callers receive no knowledge of how the rows are built.
 */
class EmptyStateRows(
    private val activity: AppCompatActivity,
    private val container: ViewGroup,
) {
    private val dynamicChildren = mutableListOf<View>()

    /** Removes every previously-added dynamic row/button from the shared
     * empty-state container. Safe to call when nothing was added. */
    fun clear() {
        dynamicChildren.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        dynamicChildren.clear()
    }

    /** Resolves a theme color attribute to a concrete ARGB int. Uses
     * obtainStyledAttributes (not TypedValue.data) because textColorPrimary /
     * textColorSecondary are ColorStateList attrs whose .data can be wrong. */
    fun themeColor(attr: Int): Int {
        val ta = activity.obtainStyledAttributes(intArrayOf(attr))
        try {
            return ta.getColor(0, 0xFF000000.toInt())
        } finally {
            ta.recycle()
        }
    }

    fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    /** Adds a two-line label/value settings row, optionally clickable. */
    fun addSettingsRow(label: String, value: String, onClick: (() -> Unit)? = null) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
        }
        TextView(activity).apply {
            text = label
            setTextColor(themeColor(android.R.attr.textColorSecondary))
            textSize = 12f
            row.addView(this)
        }
        TextView(activity).apply {
            text = value
            setTextColor(themeColor(android.R.attr.textColorPrimary))
            textSize = 15f
            row.addView(this)
        }
        container.addView(row)
        dynamicChildren.add(row)
    }

    /**
     * Adds the app-level "Screen On" toggle row (Patch 11). The switch state
     * comes from [prefs]; flips persist immediately and [onChanged] lets the
     * caller nudge the shared KeepScreenOnController. Material2
     * (Theme.MaterialComponents) is used, so SwitchCompat is required for
     * proper theming — MaterialSwitch would not theme well.
     */
    fun addScreenOnToggle(prefs: PrefsManager, onChanged: (Boolean) -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
            // Fill the available width so the label column (weight 1f)
            // expands and the switch hugs the trailing edge cleanly.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val labelColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { weight = 1f }
        }
        TextView(activity).apply {
            text = activity.getString(R.string.settings_keep_screen_on)
            setTextColor(themeColor(android.R.attr.textColorPrimary))
            textSize = 15f
            labelColumn.addView(this)
        }
        TextView(activity).apply {
            text = activity.getString(R.string.settings_keep_screen_on_summary)
            setTextColor(themeColor(android.R.attr.textColorSecondary))
            textSize = 12f
            labelColumn.addView(this)
        }
        val switch = androidx.appcompat.widget.SwitchCompat(activity).apply {
            isChecked = prefs.keepScreenOn
        }
        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.keepScreenOn = isChecked
            onChanged(isChecked)
        }
        row.addView(labelColumn)
        row.addView(switch)
        container.addView(row)
        dynamicChildren.add(row)
    }

    /** Adds a Material text button (Folders screen inline actions). */
    fun addDynamicButton(labelRes: Int, onClick: () -> Unit) {
        val btn = com.google.android.material.button.MaterialButton(activity)
        btn.text = activity.getString(labelRes)
        val lp = androidx.recyclerview.widget.RecyclerView.LayoutParams(
            androidx.recyclerview.widget.RecyclerView.LayoutParams.WRAP_CONTENT,
            androidx.recyclerview.widget.RecyclerView.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        btn.layoutParams = lp
        btn.setOnClickListener { onClick() }
        container.addView(btn)
        dynamicChildren.add(btn)
    }
}
