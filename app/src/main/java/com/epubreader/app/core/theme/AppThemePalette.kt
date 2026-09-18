package com.epubreader.app.core.theme

import android.graphics.Color

/**
 * App theme palette — the semantic color values for a specific theme.
 *
 * Phase 10: the app theme catalog now offers exactly two themes — **Original**
 * (the existing mint/eggplant light theme) and **Pastel** (the web-app-derived
 * pink/plum light theme). Night, Sepia and High Contrast have been removed
 * from the app theme catalog per the Phase 10 spec.
 *
 * NOTE: this registry is the runtime color mirror of the XML themes in
 * themes.xml. The XML themes are what actually skin the app at runtime
 * (via [com.epubreader.app.util.AppThemeController]); this data class is kept
 * as the single source of truth for the catalog and for any code that needs
 * raw color ints (e.g. drawing covers, gradients). The reader-content theme
 * system (ReaderThemes.kt) is separate and unaffected.
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
        // ===== Original brand palette (from colors.xml) =====
        private val MINT = Color.parseColor("#E0F0EA")
        private val SLATE = Color.parseColor("#95ADBE")
        private val PURPLE = Color.parseColor("#574F7D")
        private val PLUM = Color.parseColor("#503A65")
        private val EGGPLANT = Color.parseColor("#3C2A4D")

        // ===== Pastel brand palette (from the test-epub web app) =====
        private val PASTEL_ACCENT = Color.parseColor("#D88C9A")
        private val PASTEL_ACCENT_DARK = Color.parseColor("#C6707E")
        private val PASTEL_SECONDARY = Color.parseColor("#B48EAE")
        private val PASTEL_BG = Color.parseColor("#FFEEF2")
        private val PASTEL_SURFACE = Color.parseColor("#FFFFFF")
        private val PASTEL_SURFACE_ALT = Color.parseColor("#FFE4F3")
        private val PASTEL_BORDER = Color.parseColor("#F1E3D3")
        private val PASTEL_TEXT = Color.parseColor("#5A4650")
        private val PASTEL_TEXT_MUTED = Color.parseColor("#B48EAE")
        private val PASTEL_TEXT_FAINT = Color.parseColor("#D88C9A")

        /** Original (Light) — matches the XML Theme.Livre.App / Theme.EpubReader */
        val DEFAULT = AppThemePalette(
            id = "original",
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

        /** Pastel — matches the XML Theme.Livre.App.Pastel / Theme.EpubReader.Pastel */
        val PASTEL = AppThemePalette(
            id = "pastel",
            displayName = "Pastel",
            primary = PASTEL_ACCENT,
            primaryVariant = PASTEL_ACCENT_DARK,
            background = PASTEL_BG,
            surface = PASTEL_SURFACE,
            surfaceVariant = PASTEL_SURFACE_ALT,
            textPrimary = PASTEL_TEXT,
            textSecondary = PASTEL_TEXT_MUTED,
            textTertiary = PASTEL_TEXT_FAINT,
            divider = PASTEL_BORDER,
            accent = PASTEL_ACCENT,
            isDark = false,
        )

        /** All available app themes, in display order. */
        val ALL: List<AppThemePalette> = listOf(
            DEFAULT,
            PASTEL,
        )

        /** Look up a theme by id. Falls back to DEFAULT (Original). */
        fun byId(id: String?): AppThemePalette =
            ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
