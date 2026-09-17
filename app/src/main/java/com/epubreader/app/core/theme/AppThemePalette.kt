package com.epubreader.app.core.theme

import android.graphics.Color

/**
 * App theme palette — the semantic color values for a specific theme.
 *
 * This is the runtime equivalent of the XML theme attributes. When the
 * user selects an app theme (Default, Dark, Sepia, Night, High Contrast,
 * Custom), the corresponding [AppThemePalette] provides the color values
 * that are applied to the semantic attributes.
 *
 * The XML themes in themes.xml handle the Default and Night (dark) variants
 * via Android's resource system. This registry handles:
 *  - Themes that can't be expressed in XML (Custom user colors)
 *  - Runtime theme switching without activity recreation (future)
 *  - The reader-specific theme system (already in ReaderThemes.kt)
 *
 * For now, this serves as the single source of truth for the app theme
 * catalog. When full runtime switching is implemented, the selected
 * palette's values will be applied via ContextThemeWrapper or activity
 * recreation.
 *
 * @property id          Unique theme id (persisted in SharedPreferences).
 * @property displayName Human-readable name.
 * @property primary     Primary brand color (FAB, chips, accent).
 * @property primaryVariant  Darker variant of primary.
 * @property background  App background color.
 * @property surface     Card/surface background.
 * @property surfaceVariant  Alternative surface (for contrast).
 * @property textPrimary Primary text color.
 * @property textSecondary  Secondary text color.
 * @property textTertiary   Tertiary/faint text color.
 * @property divider    Divider/border color.
 * @property accent     Accent/highlight color.
 * @property isDark     Whether this is a dark theme (affects system bars).
 */
data class AppThemePalette(
    val id: String,
    val displayName: String,
    val primary: Int,
    val primaryVariant: Int,
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val divider: Int,
    val accent: Int,
    val isDark: Boolean,
) {
    companion object {
        // ===== Brand palette (from colors.xml) =====
        private val MINT = Color.parseColor("#E0F0EA")
        private val SLATE = Color.parseColor("#95ADBE")
        private val PURPLE = Color.parseColor("#574F7D")
        private val PLUM = Color.parseColor("#503A65")
        private val EGGPLANT = Color.parseColor("#3C2A4D")

        /** Default (Light) — matches the XML Theme.Livre.App */
        val DEFAULT = AppThemePalette(
            id = "default",
            displayName = "Original",
            primary = PLUM,
            primaryVariant = PURPLE,
            background = MINT,
            surface = Color.WHITE,
            surfaceVariant = MINT,
            textPrimary = EGGPLANT,
            textSecondary = PURPLE,
            textTertiary = SLATE,
            divider = SLATE,
            accent = MINT,
            isDark = false,
        )

        /** Dark / Night — matches the XML night theme */
        val NIGHT = AppThemePalette(
            id = "night",
            displayName = "Night",
            primary = PLUM,
            primaryVariant = PURPLE,
            background = EGGPLANT,
            surface = PLUM,
            surfaceVariant = PURPLE,
            textPrimary = MINT,
            textSecondary = SLATE,
            textTertiary = SLATE,
            divider = PURPLE,
            accent = MINT,
            isDark = true,
        )

        /** Sepia — warm cream tones for the app shell */
        val SEPIA = AppThemePalette(
            id = "sepia",
            displayName = "Sepia",
            primary = PLUM,
            primaryVariant = PURPLE,
            background = Color.parseColor("#F1E3D3"),
            surface = Color.parseColor("#F7EFE3"),
            surfaceVariant = Color.parseColor("#EBD9C4"),
            textPrimary = Color.parseColor("#5A4650"),
            textSecondary = Color.parseColor("#7A6470"),
            textTertiary = Color.parseColor("#9A8490"),
            divider = Color.parseColor("#C4A88E"),
            accent = MINT,
            isDark = false,
        )

        /** High Contrast — maximum readability */
        val HIGH_CONTRAST = AppThemePalette(
            id = "high_contrast",
            displayName = "High Contrast",
            primary = Color.parseColor("#1A1A2E"),
            primaryVariant = Color.parseColor("#000000"),
            background = Color.WHITE,
            surface = Color.WHITE,
            surfaceVariant = Color.parseColor("#F0F0F0"),
            textPrimary = Color.BLACK,
            textSecondary = Color.parseColor("#333333"),
            textTertiary = Color.parseColor("#666666"),
            divider = Color.BLACK,
            accent = Color.parseColor("#0066CC"),
            isDark = false,
        )

        /** All available app themes, in display order */
        val ALL: List<AppThemePalette> = listOf(
            DEFAULT,
            NIGHT,
            SEPIA,
            HIGH_CONTRAST,
        )

        /** Look up a theme by id. Falls back to DEFAULT. */
        fun byId(id: String?): AppThemePalette =
            ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
