package com.bookwormbliss.app.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bookwormbliss.app.epub.EpubResourceResolver
import com.bookwormbliss.app.ui.ReaderTheme

/**
 * Owns the Chromium WebView rendering lifecycle for one EPUB document.
 *
 * The controller is deliberately small: it knows how to configure Chromium,
 * load an XHTML spine item, intercept EPUB resources, and move within a
 * horizontally paginated document. Reader UI and persistence remain outside it.
 */
class ReaderWebRenderer(
    private val webView: WebView,
    private val bookId: Long,
    private val resolver: EpubResourceResolver,
) {
    private var destroyed = false
    private var chapterReadyCallback: ((Int, Float) -> Unit)? = null

    init {
        configure()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = false
            useWideViewPort = false
        }
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                resolver.intercept(request) ?: super.shouldInterceptRequest(view, request)

            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
                resolver.intercept(url) ?: super.shouldInterceptRequest(view, url)

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                chapterReadyCallback?.invoke(currentChapterIndex, pendingRatio)
                chapterReadyCallback = null
            }
        }
    }

    private var currentChapterIndex: Int = 0
    private var pendingRatio: Float = 0f

    fun load(
        document: ReaderDocument,
        position: ReaderDocumentPosition,
        theme: ReaderTheme,
        marginDp: Int,
        fontSizeSp: Int,
        lineHeight: Float,
        fontFamily: String,
        alignment: String,
        hyphenation: Boolean,
        onReady: (spineIndex: Int, ratio: Float) -> Unit,
    ) {
        if (destroyed) return
        val chapter = document.chapter(position.spineIndex) ?: return
        val bytes = resolver.resolve(chapter.href)?.use { it.readBytes() } ?: return
        val html = bytes.toString(Charsets.UTF_8)
        currentChapterIndex = position.spineIndex
        pendingRatio = position.offsetRatio.coerceIn(0f, 1f)
        chapterReadyCallback = onReady

        val baseUrl = EpubResourceResolver.baseUrl(bookId, chapter.href)
        val css = buildReaderCss(theme, marginDp, fontSizeSp, lineHeight, fontFamily, alignment, hyphenation)
        val documentHtml = injectCss(html, css)
        webView.loadDataWithBaseURL(baseUrl, documentHtml, "text/html", "UTF-8", baseUrl)
    }

    fun setPositionRatio(ratio: Float, onApplied: ((Float, Int) -> Unit)? = null) {
        if (destroyed) return
        val js = """
            (function() {
              var root = document.scrollingElement || document.documentElement || document.body;
              var max = Math.max(0, root.scrollWidth - root.clientWidth);
              var r = Math.max(0, Math.min(1, ${ratio.coerceIn(0f,1f)}));
              root.scrollLeft = max * r;
              var pageCount = max <= 0 ? 1 : Math.floor(max / Math.max(1, root.clientWidth)) + 1;
              var page = max <= 0 ? 0 : Math.round(root.scrollLeft / Math.max(1, root.clientWidth));
              return JSON.stringify({ratio: max <= 0 ? 0 : root.scrollLeft / max, page: page, count: pageCount});
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            val clean = result?.trim()?.removeSurrounding(""")?.replace("\"", """) ?: return@evaluateJavascript
            val match = Regex("""ratio":([0-9.]+).*"page":([0-9]+).*"count":([0-9]+)""").find(clean) ?: return@evaluateJavascript
            val applied = match.groupValues[1].toFloatOrNull() ?: return@evaluateJavascript
            val page = match.groupValues[2].toIntOrNull() ?: 0
            val count = match.groupValues[3].toIntOrNull() ?: 1
            onApplied?.invoke(applied, count)
        }
    }

    fun readPosition(onResult: (Float, Int, Int) -> Unit) {
        if (destroyed) return
        val js = """
            (function() {
              var root = document.scrollingElement || document.documentElement || document.body;
              var max = Math.max(0, root.scrollWidth - root.clientWidth);
              var width = Math.max(1, root.clientWidth);
              var ratio = max <= 0 ? 0 : root.scrollLeft / max;
              var page = max <= 0 ? 0 : Math.round(root.scrollLeft / width);
              var count = max <= 0 ? 1 : Math.floor(max / width) + 1;
              return JSON.stringify({ratio:ratio,page:page,count:count});
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            val clean = result?.trim()?.removeSurrounding(""")?.replace("\"", """) ?: return@evaluateJavascript
            val match = Regex("""ratio":([0-9.]+).*"page":([0-9]+).*"count":([0-9]+)""").find(clean) ?: return@evaluateJavascript
            val ratio = match.groupValues[1].toFloatOrNull() ?: 0f
            val page = match.groupValues[2].toIntOrNull() ?: 0
            val count = match.groupValues[3].toIntOrNull() ?: 1
            onResult(ratio.coerceIn(0f,1f), page, count.coerceAtLeast(1))
        }
    }

    fun pageTurn(forward: Boolean, onBoundary: (Boolean) -> Unit) {
        if (destroyed) return
        val delta = if (forward) 1 else -1
        val js = """
            (function() {
              var root = document.scrollingElement || document.documentElement || document.body;
              var width = Math.max(1, root.clientWidth);
              var max = Math.max(0, root.scrollWidth - width);
              var next = Math.max(0, Math.min(max, root.scrollLeft + ($delta * width)));
              var changed = Math.abs(next - root.scrollLeft) > 1;
              root.scrollLeft = next;
              return changed ? "changed" : "boundary";
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            onBoundary(result?.contains("boundary") == true)
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        chapterReadyCallback = null
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.loadUrl("about:blank")
        webView.destroy()
        resolver.close()
    }

    private fun injectCss(html: String, css: String): String {
        val style = "<style id=\"bookworm-reader-style\">$css</style>"
        val head = Regex("(?i)<head[^>]*>").find(html)
        return if (head != null) {
            html.substring(0, head.range.last + 1) + style + html.substring(head.range.last + 1)
        } else {
            "<html><head>$style</head><body><div>$html</div></body></html>"
        }
    }

    private fun buildReaderCss(
        theme: ReaderTheme,
        marginDp: Int,
        fontSizeSp: Int,
        lineHeight: Float,
        fontFamily: String,
        alignment: String,
        hyphenation: Boolean,
    ): String {
        val family = when (fontFamily) {
            "sans" -> "sans-serif"
            "mono" -> "monospace"
            "book" -> "Georgia, serif"
            "humanist" -> "Trebuchet MS, sans-serif"
            "publisher" -> "inherit"
            else -> "serif"
        }
        val align = when (alignment) {
            "justify" -> "justify"
            "center" -> "center"
            "right" -> "right"
            "original" -> "initial"
            else -> "left"
        }
        val hyphens = if (hyphenation) "auto" else "manual"
        return """
            html, body {
                margin: 0 !important;
                padding: 0 !important;
                width: 100% !important;
                height: 100% !important;
                overflow: hidden !important;
                background: ${theme.bgHex} !important;
                color: ${theme.inkHex} !important;
            }
            body {
                font-family: $family !important;
                font-size: ${fontSizeSp}sp !important;
                line-height: ${lineHeight} !important;
                text-align: $align;
                hyphens: $hyphens;
                -webkit-hyphens: $hyphens;
                box-sizing: border-box;
                width: 100%;
                min-height: 100%;
                padding: 0 ${marginDp}px;
                column-width: calc(100vw - ${marginDp * 2}px);
                column-gap: 0;
            }
            p, h1, h2, h3, h4, h5, h6, li, blockquote, pre, figure {
                break-inside: avoid;
            }
            img, svg, video, table {
                max-width: 100% !important;
            }
            a { color: inherit; }
        """.trimIndent()
    }
}
