package com.bookwormbliss.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SINGLE SOURCE OF TRUTH — raw color palette.
 *
 * These are the only hex literals that should ever appear anywhere in the
 * app. Every screen, component and the reader itself consumes colors
 * through [BookwormColors] (see Theme.kt), which is built from this object.
 * Never hard-code a hex value in a screen or component — add/change a token
 * here instead, and it propagates everywhere automatically.
 *
 * Values below are taken directly from the web-app design tokens in
 * section 3 ("Visual Design System") of the reconstruction spec.
 */
object BookwormPalette {
    val Accent = Color(0xFFD88C9A)
    val AccentDark = Color(0xFFC6707E)
    val SecondaryMauve = Color(0xFFB48EAE)
    val WarmPop = Color(0xFFF2D0A9)
    val PurplePop = Color(0xFF8E7DBE)
    val LightAccent = Color(0xFFF1E3D3)
    val LightBackground = Color(0xFFFFEEF2)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceAlt = Color(0xFFFFE4F3)
    val LightBorder = Color(0xFFF1E3D3)
    val PrimaryText = Color(0xFF5A4650)
    val MutedText = Color(0xFFB48EAE)
    val FaintAccent = Color(0xFFD88C9A)
    val WarmNeutral = Color(0xFFFAF7F5)

    val Success = Color(0xFF7BA98C)
    val Warning = Color(0xFFE0A458)
    val Error = Color(0xFFC65B5B)

    // Dark-mode analogues, kept in the same warm/literary family rather than
    // falling back to generic Android dark grays.
    val DarkBackground = Color(0xFF211A1E)
    val DarkSurface = Color(0xFF2C2329)
    val DarkSurfaceAlt = Color(0xFF362A32)
    val DarkBorder = Color(0xFF4A3A42)
    val DarkPrimaryText = Color(0xFFF1E3D3)
    val DarkMutedText = Color(0xFFC9A8BE)

    // Reader theme swatches (ReaderThemeConfig.id in the web app: ThemeId).
    val ReaderIvoryBg = Color(0xFFFBF6EE)
    val ReaderIvoryInk = Color(0xFF3A2E2A)
    val ReaderIvorySurface = Color(0xFFFFFFFF)

    val ReaderNordicEcoBg = Color(0xFFEAEFE8)
    val ReaderNordicEcoInk = Color(0xFF2F3A32)
    val ReaderNordicEcoSurface = Color(0xFFF5F8F3)

    val ReaderAlabasterBg = Color(0xFFF7F5F2)
    val ReaderAlabasterInk = Color(0xFF3D3733)
    val ReaderAlabasterSurface = Color(0xFFFFFFFF)

    val ReaderCandlelightBg = Color(0xFFF4E3C1)
    val ReaderCandlelightInk = Color(0xFF4A3620)
    val ReaderCandlelightSurface = Color(0xFFFBEED2)

    val ReaderOnyxBg = Color(0xFF1B1B1D)
    val ReaderOnyxInk = Color(0xFFE7E2DD)
    val ReaderOnyxSurface = Color(0xFF242426)

    val ReaderMidnightSlateBg = Color(0xFF1D232B)
    val ReaderMidnightSlateInk = Color(0xFFD7DEE6)
    val ReaderMidnightSlateSurface = Color(0xFF262E38)
}

/**
 * Semantic tokens (section 4 of the spec) — this is what screens/components
 * actually reference (via `MaterialTheme.bookwormColors.xxx`, see Theme.kt).
 */
data class BookwormColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceAccent: Color,
    val border: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentDark: Color,
    val secondary: Color,
    val warmPop: Color,
    val purplePop: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val readerBackground: Color,
    val readerSurface: Color,
    val readerInk: Color,
)

fun lightBookwormColors(): BookwormColors = BookwormColors(
    background = BookwormPalette.LightBackground,
    surface = BookwormPalette.LightSurface,
    surfaceAlt = BookwormPalette.LightSurfaceAlt,
    surfaceAccent = BookwormPalette.LightAccent,
    border = BookwormPalette.LightBorder,
    divider = BookwormPalette.LightBorder,
    textPrimary = BookwormPalette.PrimaryText,
    textSecondary = BookwormPalette.PrimaryText.copy(alpha = 0.72f),
    textMuted = BookwormPalette.MutedText,
    accent = BookwormPalette.Accent,
    accentPressed = BookwormPalette.AccentDark,
    accentDark = BookwormPalette.AccentDark,
    secondary = BookwormPalette.SecondaryMauve,
    warmPop = BookwormPalette.WarmPop,
    purplePop = BookwormPalette.PurplePop,
    success = BookwormPalette.Success,
    warning = BookwormPalette.Warning,
    error = BookwormPalette.Error,
    readerBackground = BookwormPalette.ReaderIvoryBg,
    readerSurface = BookwormPalette.ReaderIvorySurface,
    readerInk = BookwormPalette.ReaderIvoryInk,
)

fun darkBookwormColors(): BookwormColors = BookwormColors(
    background = BookwormPalette.DarkBackground,
    surface = BookwormPalette.DarkSurface,
    surfaceAlt = BookwormPalette.DarkSurfaceAlt,
    surfaceAccent = BookwormPalette.DarkSurfaceAlt,
    border = BookwormPalette.DarkBorder,
    divider = BookwormPalette.DarkBorder,
    textPrimary = BookwormPalette.DarkPrimaryText,
    textSecondary = BookwormPalette.DarkPrimaryText.copy(alpha = 0.75f),
    textMuted = BookwormPalette.DarkMutedText,
    accent = BookwormPalette.Accent,
    accentPressed = BookwormPalette.AccentDark,
    accentDark = BookwormPalette.AccentDark,
    secondary = BookwormPalette.SecondaryMauve,
    warmPop = BookwormPalette.WarmPop,
    purplePop = BookwormPalette.PurplePop,
    success = BookwormPalette.Success,
    warning = BookwormPalette.Warning,
    error = BookwormPalette.Error,
    readerBackground = BookwormPalette.ReaderOnyxBg,
    readerSurface = BookwormPalette.ReaderOnyxSurface,
    readerInk = BookwormPalette.ReaderOnyxInk,
)

/** One swatch set per selectable in-reader theme (ThemeId in the web app). */
enum class ReaderThemeId(val bg: Color, val ink: Color, val surface: Color, val label: String) {
    IVORY(BookwormPalette.ReaderIvoryBg, BookwormPalette.ReaderIvoryInk, BookwormPalette.ReaderIvorySurface, "Ivory"),
    NORDIC_ECO(BookwormPalette.ReaderNordicEcoBg, BookwormPalette.ReaderNordicEcoInk, BookwormPalette.ReaderNordicEcoSurface, "Nordic Eco"),
    ALABASTER(BookwormPalette.ReaderAlabasterBg, BookwormPalette.ReaderAlabasterInk, BookwormPalette.ReaderAlabasterSurface, "Alabaster"),
    CANDLELIGHT(BookwormPalette.ReaderCandlelightBg, BookwormPalette.ReaderCandlelightInk, BookwormPalette.ReaderCandlelightSurface, "Candlelight"),
    ONYX(BookwormPalette.ReaderOnyxBg, BookwormPalette.ReaderOnyxInk, BookwormPalette.ReaderOnyxSurface, "Onyx"),
    MIDNIGHT_SLATE(BookwormPalette.ReaderMidnightSlateBg, BookwormPalette.ReaderMidnightSlateInk, BookwormPalette.ReaderMidnightSlateSurface, "Midnight Slate"),
}
