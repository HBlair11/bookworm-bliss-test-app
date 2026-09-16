package com.bookwormbliss.app.core.reader

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bookwormbliss.app.core.document.ReaderDocument
import com.bookwormbliss.app.core.epub.EpubDocument
import com.bookwormbliss.app.core.location.ReaderPosition

class ReaderWebRenderer(private val web:WebView):ReaderRenderer {
    private var document:EpubDocument?=null; private var settings=ReaderSettings(); private var spine=0; private var page=0; private var pages=1
    init{web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true;web.settings.allowFileAccess=true;web.webViewClient=WebViewClient()}
    override fun load(document:ReaderDocument){this.document=document as EpubDocument;spine=0;page=0;render()}
    fun loadChapter(index:Int){if(document==null)return;spine=index.coerceIn(0,document!!.chapters.lastIndex);page=0;render()}
    override fun nextPage(){if(settings.readingMode=="vertical")return; if(page+1<pages){page++;js("go($page)")}else if(spine<document!!.chapters.lastIndex){spine++;page=0;render()}}
    override fun previousPage(){if(settings.readingMode=="vertical")return; if(page>0){page--;js("go($page)")}else if(spine>0){spine--;render()}}
    override fun currentPosition()=ReaderPosition(spine,page,0)
    override fun restore(position:ReaderPosition){spine=position.spineIndex.coerceIn(0,(document?.chapters?.lastIndex?:0));page=position.pageIndex.coerceAtLeast(0);render()}
    override fun applySettings(settings:ReaderSettings){this.settings=settings;render()}
    override fun currentPage()=page+1
    override fun pageCount()=pages
    fun chapterCount()=document?.chapters?.size?:0
    fun toc()=document?.toc.orEmpty()
    private fun render(){val d=document?:return;val c=d.chapters.getOrNull(spine)?:return;val bg=themeBg(settings.theme);val ink=themeInk(settings.theme);val font=when(settings.font){"sans"->"sans-serif";"mono"->"monospace";"humanist"->"Trebuchet MS, sans-serif";"book"->"Georgia, serif";else->"Georgia, serif"};val align=if(settings.align=="original")"initial" else settings.align;val mode=if(settings.readingMode=="horizontal")"columns" else "flow";val html="""
<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'><style>
*{box-sizing:border-box}html,body{margin:0;padding:0;background:$bg;color:$ink}body{font-family:$font;font-size:${settings.fontSize}px;line-height:${settings.lineHeight};text-align:$align;overflow:hidden}#book{height:100vh;padding:${settings.margin}px;${if(mode=="columns")"column-width:calc(100vw - ${settings.margin*2}px);column-gap:${settings.margin*2}px;column-fill:auto;" else "overflow-y:auto;"}}img{max-width:100%;height:auto}table{max-width:100%}a{color:#8E5F72}blockquote{margin-left:1em;margin-right:1em}h1,h2,h3,h4,h5,h6{break-after:avoid}
</style></head><body><main id='book'>${c.html}</main><script>
let p=0;function metrics(){let b=document.getElementById('book');let n=${if(mode=="columns")"Math.max(1,Math.ceil(b.scrollWidth/b.clientWidth))" else "1"};return n}function go(i){p=i;document.getElementById('book').scrollTo({left:i*innerWidth,behavior:'instant'});}function init(){document.body.style.overflow='hidden';return metrics()}window.addEventListener('resize',()=>{});</script></body></html>"""
        web.setBackgroundColor(Color.parseColor(bg));web.loadDataWithBaseURL("file://${d.extractedRoot}/",html,"text/html","UTF-8",null);web.postDelayed({web.evaluateJavascript("init()") { value->pages=(value?.trim('"')?.toIntOrNull()?:1);page=page.coerceAtMost(pages-1);web.evaluateJavascript("go($page)"){} }},300)
    }
    private fun js(s:String){web.evaluateJavascript("$s"){} }
    private fun themeBg(t:String)=when(t){"ivory"->"#FFFFFF";"nordic_eco"->"#E8EFE9";"candlelight"->"#E8D3A7";"onyx"->"#000000";"midnight_slate"->"#1A1B1E";else->"#F1E3D3"}
    private fun themeInk(t:String)=when(t){"ivory"->"#000000";"nordic_eco"->"#242B27";"candlelight"->"#2B1A0A";"onyx"->"#FFFFFF";"midnight_slate"->"#D1D5DB";else->"#5A4650"}
}
