package com.bookwormbliss.app.core.reader

import android.graphics.Color
import com.bookwormbliss.app.data.PrefsManager
import com.bookwormbliss.app.epub.ReaderTheme

/**
 * Extracted from ReaderActivity.buildReaderCss().
 *
 * Builds the CSS injected into the reader WebView based on current
 * ReaderSettings + ReaderTheme. The CSS controls typography, layout,
 * pagination columns, and ink/background colors for the reading surface.
 *
 * This is a pure function of [ReaderCssConfig] — it has no Android Context
 * dependency beyond [Color] (used for hex conversion) and [ReaderTheme].
 */
object ReaderCssBuilder {

    /**
     * Configuration snapshot extracted from ReaderActivity state.
     * Captured once at build time so the CSS string is self-contained.
     */
    data class Config(
        val font: PrefsManager.Font,
        val fontSize: Int,
        val lineHeight: Float,
        val margin: Int,
        val align: PrefsManager.Align,
        val hyphenation: Boolean,
        val themeId: String,
        val bgColor: Int,
        val inkHex: String
    )

    /**
     * Builds the complete <style> block for the reader WebView.
     * Output is identical to the original ReaderActivity.buildReaderCss().
     */
    fun build(config: Config): String {
        // PUBLISHER = use the book's own fonts: do NOT emit a font-family override
        // so the EPUB's @font-face + font-family declarations (served by
        // EpubResourceResolver with correct font MIME types) take effect.
        val fontFamily = when (config.font) {
            PrefsManager.Font.SANS -> "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
            PrefsManager.Font.MONO -> "'Courier New', Consolas, monospace"
            PrefsManager.Font.BOOK -> "Palatino, 'Palatino Linotype', 'Book Antiqua', Georgia, serif"
            PrefsManager.Font.HUMANIST -> "Tahoma, Verdana, Geneva, 'Segoe UI', sans-serif"
            PrefsManager.Font.PUBLISHER -> null
            else -> "Georgia, 'Times New Roman', serif"
        }

        // ORIGINAL = honor the book's own alignment (no text-align override).
        val alignCss = when (config.align) {
            PrefsManager.Align.JUSTIFY -> "text-align:justify !important;"
            PrefsManager.Align.CENTER -> "text-align:center !important;"
            PrefsManager.Align.RIGHT -> "text-align:right !important;"
            PrefsManager.Align.LEFT -> "text-align:left !important;"
            else -> ""
        }

        val hyphensCss = if (config.hyphenation)
            "-webkit-hyphens:auto;hyphens:auto;" else "-webkit-hyphens:manual;hyphens:manual;"

        val size = config.fontSize
        val line = config.lineHeight
        val margin = config.margin
        val fontCss = fontFamily?.let { "font-family:$it !important;" } ?: ""
        val bgHex = colorToHex(config.bgColor)
        val ink = config.inkHex

        return """<style>
html, body {
  background:$bgHex !important;
  color:$ink !important;
  margin:0 !important;
  overflow-x:hidden !important;
  overflow-y:hidden !important;
  -webkit-tap-highlight-color:transparent;
}
body {
  $fontCss
  font-size:${size}px !important;
  line-height:$line !important;
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
a { color:$ink !important; }
h1,h2,h3,h4,h5,h6 { color:$ink !important; line-height:1.25 !important; break-after:avoid; }
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
</style>""".trimIndent() + darkTextOverride(config.themeId, ink)
    }

    /**
     * Patch 10: in DARK mode, some EPUBs hard-code a dark text color via an
     * inline `style="color:#..."` or a stylesheet rule. The `body { color:ink
     * !important }` rule only sets the *inherited* default; an explicit color
     * declared on a descendant (e.g. `<p style="color:#333">`) wins by
     * specificity and renders dark text on a dark page. To match what most
     * dark-mode readers do, in dark mode only we force the ink color down
     * through EVERY element (including inline-styled ones), so no dark-on-dark
     * text survives. This is applied only in dark mode so light/sepia books are
     * untouched. Images are not affected (they don't use `color`).
     */
    private fun darkTextOverride(themeId: String, ink: String): String {
        // Patch 13/14: apply in dark AND sepia. In both, a hard-coded light/white
        // `background-color` on a heading (e.g. a "1" badge or all-caps title) becomes a
        // visible white bar: in light mode that white box blends into the white page so
        // it's invisible and we leave light mode untouched; in dark/sepia the box stands
        // out (and, in dark, the forced-white text disappears into it). Forcing descendant
        // backgrounds to transparent (leaving `body`'s own page background, set in
        // buildReaderCss, intact) removes those bars. We also push the ink color through
        // every element so hard-coded text colors don't survive into a tinted/dark page.
        // Colored callout boxes flatten in dark/sepia — the standard reader tradeoff.
        //
        // Patch 17 (Addition #1): the decision is now driven by the theme registry's
        // [ReaderTheme.needsInkOverride] flag (true for every non-Ivory theme) so new
        // tinted/dark themes get the same protection automatically.
        if (!ReaderTheme.byId(themeId).needsInkOverride) return ""
        return """
<style>
body, body * { color:$ink !important; }
body *:not(mark.livre-highlight):not(.livre-tts-word):not(.livre-tts-sentence) { background-color: transparent !important; }
</style>""".trimIndent()
    }

    private fun colorToHex(c: Int): String {
        val a = Color.alpha(c)
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        return if (a < 255) String.format("#%08X", c) else String.format("#%06X", c and 0xFFFFFF)
    }
}
