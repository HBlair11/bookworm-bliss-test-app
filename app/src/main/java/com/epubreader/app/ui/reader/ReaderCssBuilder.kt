package com.epubreader.app.ui.reader

import com.epubreader.app.data.PrefsManager
import com.epubreader.app.ui.ReaderTheme

/**
 * ReaderCssBuilder — Extracted from ReaderActivity (Phase 6).
 *
 * Builds the CSS injected into the EPUB WebView content area. This is a
 * pure function with no Android lifecycle or View dependencies: it takes
 * the reader's appearance parameters and produces a CSS string.
 *
 * Extraction rationale: ReaderActivity had become a 5,118-line god activity.
 * CSS generation is a self-contained responsibility with no interaction state,
 * making it the safest first extraction (minimal lifecycle risk).
 *
 * The caller (ReaderActivity) supplies the resolved values from prefs and
 * the reader theme registry; this class does not read SharedPreferences.
 */
object ReaderCssBuilder {

    /**
     * Build the full reader CSS for injection into the EPUB content WebView.
     *
     * @param bgColorInt  Background color as an Android [android.graphics.Color] int.
     * @param inkHex      Body text / link color as a CSS hex string (e.g. "#000000").
     * @param themeId     The persisted [ReaderTheme] id (drives dark-text override).
     * @param font        [PrefsManager.Font] id.
     * @param fontSize    Font size in pixels.
     * @param lineHeight  Line height multiplier (e.g. 1.6).
     * @param margin      Side margin in pixels.
     * @param align       [PrefsManager.Align] id.
     * @param hyphenation Whether soft hyphenation is enabled.
     * @return A `<style>...</style>` block (possibly followed by a dark-text
     *         override block).
     */
    fun build(
        bgColorInt: Int,
        inkHex: String,
        themeId: String,
        font: String,
        fontSize: Int,
        lineHeight: Float,
        margin: Int,
        align: String,
        hyphenation: Boolean,
    ): String {
        // PUBLISHER = use the book's own fonts: do NOT emit a font-family override
        // so the EPUB's @font-face + font-family declarations (served by
        // EpubResourceResolver with correct font MIME types) take effect.
        val fontFamily = when (font) {
            PrefsManager.Font.SANS -> "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
            PrefsManager.Font.MONO -> "'Courier New', Consolas, monospace"
            PrefsManager.Font.BOOK -> "Palatino, 'Palatino Linotype', 'Book Antiqua', Georgia, serif"
            PrefsManager.Font.HUMANIST -> "Tahoma, Verdana, Geneva, 'Segoe UI', sans-serif"
            PrefsManager.Font.PUBLISHER -> null
            else -> "Georgia, 'Times New Roman', serif"
        }
        // ORIGINAL = honor the book's own alignment (no text-align override).
        val alignCss = when (align) {
            PrefsManager.Align.JUSTIFY -> "text-align:justify !important;"
            PrefsManager.Align.CENTER -> "text-align:center !important;"
            PrefsManager.Align.RIGHT -> "text-align:right !important;"
            PrefsManager.Align.LEFT -> "text-align:left !important;"
            else -> ""
        }
        val hyphensCss = if (hyphenation)
            "-webkit-hyphens:auto;hyphens:auto;" else "-webkit-hyphens:manual;hyphens:manual;"
        val fontCss = fontFamily?.let { "font-family:$it !important;" } ?: ""
        val bgHex = colorToHex(bgColorInt)
        return """<style>
html, body {
  background:$bgHex !important;
  color:$inkHex !important;
  margin:0 !important;
  overflow-x:hidden !important;
  overflow-y:hidden !important;
  -webkit-tap-highlight-color:transparent;
}
body {
  $fontCss
  font-size:${fontSize}px !important;
  line-height:$lineHeight !important;
  $alignCss
  padding:0 ${margin}px !important;
  column-fill:auto !important;
  -webkit-column-fill:auto !important;
  column-gap:${2 * margin}px !important;
  -webkit-column-gap:${2 * margin}px !important;
  word-wrap:break-word;
  -webkit-text-size-adjust:100%;
  $hyphensCss
}
/* Patch 7-style image handling (reverted from Patch 8's object-fit/max-height
   block, which made inline icons render at full viewport height and "overpower"
   the surrounding text). We now only constrain horizontal overflow and let the
   EPUB's own CSS decide inline/block sizing, exactly like Calibre's editor /
   Readium: inline icons stay inline and natural-sized, large images scale to
   the column width while preserving aspect ratio. No max-height, no
   object-fit, no forced break-avoid on inline media. */
img, svg, video {
  max-width:100% !important;
  height:auto !important;
}
figure { margin:0.5em 0 !important; }
table { max-width:100% !important; }
a { color:$inkHex !important; }
h1,h2,h3,h4,h5,h6 { color:$inkHex !important; line-height:1.25 !important; break-after:avoid; }
/* Highlight marks: subtle background, no layout disruption. */
mark.livre-highlight {
  color:inherit !important;
  display:inline !important;
  padding:0 !important;
  margin:0 !important;
  border:0 !important;
  font:inherit !important;
  line-height:inherit !important;
}
</style>""".trimIndent() + darkTextOverride(themeId, inkHex)
    }

    /**
     * In DARK/SEPIA mode, some EPUBs hard-code a dark text color via an
     * inline `style="color:#..."` or a stylesheet rule. The `body { color:ink
     * !important }` rule only sets the *inherited* default; an explicit color
     * declared on a descendant (e.g. `<p style="color:#333">`) wins by
     * specificity and renders dark text on a dark page. To match what most
     * dark-mode readers do, in dark mode only we force the ink color down
     * through EVERY element (including inline-styled ones), so no dark-on-dark
     * text survives. This is applied only in dark mode so light/sepia books are
     * untouched. Images are not affected (they don't use `color`).
     *
     * The decision is driven by the theme registry's
     * [ReaderTheme.needsInkOverride] flag (true for every non-Ivory theme) so
     * new tinted/dark themes get the same protection automatically.
     */
    fun darkTextOverride(themeId: String, inkHex: String): String {
        if (!ReaderTheme.byId(themeId).needsInkOverride) return ""
        return """
<style>
body, body * { color:$inkHex !important; }
body *:not(mark.livre-highlight):not(.livre-tts-word):not(.livre-tts-sentence) { background-color: transparent !important; }
</style>""".trimIndent()
    }

    /** Convert an Android color int to a CSS hex string. Uses pure bit
     *  operations so it is unit-testable without Android framework mocking. */
    fun colorToHex(c: Int): String {
        val a = (c ushr 24) and 0xFF
        return if (a < 255) String.format("#%08X", c) else String.format("#%06X", c and 0xFFFFFF)
    }
}
