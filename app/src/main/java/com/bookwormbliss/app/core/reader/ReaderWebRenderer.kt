package com.bookwormbliss.app.core.reader

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bookwormbliss.app.core.document.ReaderDocument
import com.bookwormbliss.app.core.epub.EpubDocument
import com.bookwormbliss.app.core.location.ReaderPosition

class ReaderWebRenderer(private val web: WebView) : ReaderRenderer {
    private var document: EpubDocument? = null
    private var settings = ReaderSettings()
    private var spine = 0
    private var page = 0
    private var pages = 1

    init {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = false
        web.webViewClient = WebViewClient()
        web.overScrollMode = WebView.OVER_SCROLL_NEVER
    }

    override fun load(document: ReaderDocument) {
        this.document = document as EpubDocument
        spine = 0
        page = 0
        render()
    }

    fun loadChapter(index: Int) {
        val d = document ?: return
        spine = index.coerceIn(0, d.chapters.lastIndex)
        page = 0
        render()
    }

    override fun nextPage() {
        if (settings.readingMode == "vertical") {
            web.evaluateJavascript("window.scrollBy(0, Math.max(1, window.innerHeight * 0.88));") {}
            return
        }
        if (page + 1 < pages) {
            page++
            js("go($page)")
        } else if (spine < (document?.chapters?.lastIndex ?: 0)) {
            spine++
            page = 0
            render()
        }
    }

    override fun previousPage() {
        if (settings.readingMode == "vertical") {
            web.evaluateJavascript("window.scrollBy(0, -Math.max(1, window.innerHeight * 0.88));") {}
            return
        }
        if (page > 0) {
            page--
            js("go($page)")
        } else if (spine > 0) {
            spine--
            page = 0
            render()
        }
    }

    override fun currentPosition() = ReaderPosition(spine, page, 0)

    override fun restore(position: ReaderPosition) {
        val d = document ?: return
        spine = position.spineIndex.coerceIn(0, d.chapters.lastIndex)
        page = position.pageIndex.coerceAtLeast(0)
        render()
    }

    override fun applySettings(settings: ReaderSettings) {
        this.settings = settings
        if (document != null) render()
    }

    override fun currentPage() = page + 1
    override fun pageCount() = pages
    fun chapterCount() = document?.chapters?.size ?: 0
    fun toc() = document?.toc.orEmpty()

    private fun render() {
        val d = document ?: return
        val chapter = d.chapters.getOrNull(spine) ?: return
        val bg = themeBg(settings.theme)
        val ink = themeInk(settings.theme)
        val font = when (settings.font) {
            "sans" -> "sans-serif"
            "mono" -> "monospace"
            "humanist" -> "Trebuchet MS, sans-serif"
            "book" -> "Georgia, serif"
            else -> "Georgia, serif"
        }
        val align = if (settings.align == "original") "initial" else settings.align
        val horizontal = settings.readingMode == "horizontal"
        val modeCss = if (horizontal) "column-width:100vw;column-gap:0;column-fill:auto;overflow:hidden;" else "overflow-y:auto;overflow-x:hidden;"
        val bodyOverflow = if (horizontal) "hidden" else "auto"
        val html = """
<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'><style>
*{box-sizing:border-box}html,body{margin:0;padding:0;width:100%;height:100%;background:$bg;color:$ink}body{font-family:$font;font-size:${settings.fontSize}px;line-height:${settings.lineHeight};text-align:$align;overflow:$bodyOverflow}
#book{width:100%;height:100%;padding:${settings.margin}px;${modeCss}}img{max-width:100%;height:auto}svg{max-width:100%;height:auto}table{max-width:100%;border-collapse:collapse}a{color:#8E5F72}blockquote{margin-left:1em;margin-right:1em}pre{white-space:pre-wrap;overflow-wrap:anywhere}h1,h2,h3,h4,h5,h6{break-after:avoid}
</style></head><body><main id='book'>${chapter.html}</main><script>
function metrics(){const b=document.getElementById('book');return Math.max(1,Math.ceil(b.scrollWidth/Math.max(1,b.clientWidth)));}
function go(i){const b=document.getElementById('book');const w=Math.max(1,b.clientWidth);b.scrollTo({left:i*w,top:0,behavior:'auto'});window.scrollTo(0,0);}
function init(){return metrics();}
</script></body></html>
"""
        web.setBackgroundColor(Color.parseColor(bg))
        web.loadDataWithBaseURL("file://${d.extractedRoot}/", html, "text/html", "UTF-8", null)
        web.postDelayed({
            web.evaluateJavascript("init()") { value ->
                pages = value?.trim('"')?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                page = page.coerceIn(0, pages - 1)
                if (horizontal) js("go($page)")
            }
        }, 500)
    }

    private fun js(script: String) { web.evaluateJavascript(script) {} }
    private fun themeBg(t: String) = when (t) { "ivory" -> "#FFFFFF"; "nordic_eco" -> "#E8EFE9"; "candlelight" -> "#E8D3A7"; "onyx" -> "#000000"; "midnight_slate" -> "#1A1B1E"; else -> "#F1E3D3" }
    private fun themeInk(t: String) = when (t) { "ivory" -> "#000000"; "nordic_eco" -> "#242B27"; "candlelight" -> "#2B1A0A"; "onyx" -> "#FFFFFF"; "midnight_slate" -> "#D1D5DB"; else -> "#5A4650" }
}
