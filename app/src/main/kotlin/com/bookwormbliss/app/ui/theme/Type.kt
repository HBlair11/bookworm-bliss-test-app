package com.bookwormbliss.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SINGLE SOURCE OF TRUTH — font families.
 *
 * The spec calls for two personalities: "Cinzel" (literary/display, used
 * sparingly for branding, titles and section headings) and "Plus Jakarta
 * Sans" (general UI). Neither can be embedded here as binary font files
 * (this project ships no font assets), so both currently resolve to a
 * close system fallback. To activate the real typefaces:
 *
 *   1. Drop the OFL font files under app/src/main/res/font/, e.g.
 *      cinzel_regular.ttf, cinzel_semibold.ttf, plus_jakarta_sans_regular.ttf,
 *      plus_jakarta_sans_medium.ttf, plus_jakarta_sans_semibold.ttf
 *   2. Build a FontFamily(Font(R.font.cinzel_regular), ...) for each of the
 *      two vals below.
 *
 * Every screen must go through [BookwormType] / MaterialTheme.typography —
 * never construct a one-off TextStyle with its own fontFamily.
 */
val LiteraryFontFamily: FontFamily = FontFamily.Serif // stand-in for Cinzel
val UiFontFamily: FontFamily = FontFamily.SansSerif   // stand-in for Plus Jakarta Sans

/** Reader-only font choices (independent from app chrome typography). */
enum class ReaderFontId(val label: String, val family: FontFamily) {
    SERIF("Serif", FontFamily.Serif),
    SANS("Sans", FontFamily.SansSerif),
    MONO("Monospace", FontFamily.Monospace),
    BOOK("Book (classic serif)", FontFamily.Serif),
    HUMANIST("Humanist", FontFamily.SansSerif),
}

/**
 * Typography scale (section 7). Material3's [Typography] slots are reused
 * with our two font families rather than introducing a parallel scale.
 */
val BookwormType = Typography(
    displayLarge = TextStyle(fontFamily = LiteraryFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = 0.2.sp),
    displayMedium = TextStyle(fontFamily = LiteraryFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.2.sp),
    headlineLarge = TextStyle(fontFamily = LiteraryFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = LiteraryFontFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = LiteraryFontFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
