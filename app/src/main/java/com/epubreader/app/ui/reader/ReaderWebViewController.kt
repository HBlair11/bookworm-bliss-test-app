package com.epubreader.app.ui.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.epubreader.app.epub.EpubResourceResolver
import com.epubreader.app.epub.ReaderSelectionBridge
import com.epubreader.app.epub.ReaderSelectionLocator

/**
 * ReaderWebViewController — Extracted from ReaderActivity (Phase 6).
 *
 * Owns the main reader WebView lifecycle: settings configuration, the
 * WebViewClient (resource interception, URL routing, page-finished
 * notification), WebChromeClient, and the JavaScript interfaces
 * ([ReaderSelectionBridge] for text selection capture, [HighlightBridge]
 * for highlight tap events).
 *
 * Cross-controller communication flows through callbacks so the Activity
 * remains the orchestrator:
 * - [onNavigateToUrl] fires when the user taps an internal link.
 * - [onPageFinished] fires after Chromium finishes loading + Caesura
 *   pagination is applied. The Activity handles progress polling,
 *   fragment restore, and highlight injection.
 * - [onHighlightTap] fires when a `<mark>` element is tapped.
 *
 * The measurement WebView and its associated page-count pipeline remain
 * in ReaderActivity for now — they are deeply coupled with measurement
 * state and will be extracted in a later slice once this controller is
 * stable.
 *
 * @param webView              The main reader WebView from the Activity binding.
 * @param resolverProvider      Lambda returning the current [EpubResourceResolver], or null.
 * @param onNavigateToUrl       Called when an internal (virtual-host) URL is tapped.
 * @param onPageFinished        Called after page load + Caesura.apply(); Activity orchestrates
 *                              progress polling, fragment restore, and highlight injection.
 * @param onHighlightTap        Called when a highlight `<mark>` is tapped in the WebView.
 * @param getCurrentSpineHref   Lambda returning the current spine item's href (for selection capture).
 */
class ReaderWebViewController(
    private val webView: WebView,
    private val resolverProvider: () -> EpubResourceResolver?,
    private val onNavigateToUrl: (String) -> Unit,
    private val onPageFinished: () -> Unit,
    private val onHighlightTap: (Long) -> Unit,
    private val getCurrentSpineHref: () -> String?,
) {

    /** Pending callback for the next selection capture request. */
    private var pendingSelectionCallback: ((ReaderSelectionLocator?) -> Unit)? = null

    /** Generation token to guard against stale page-finished callbacks. */
    @Volatile
    private var pageLoadGeneration: Int = 0

    /**
     * JavaScript interface for highlight tap callbacks from the WebView.
     * When a `<mark>` element is tapped, it calls
     * `LivreHighlight.onHighlightTap(id)` which fires [onHighlightTap].
     */
    inner class HighlightBridge {
        @JavascriptInterface
        fun onHighlightTap(id: Long) {
            onHighlightTap(id)
        }
    }

    /**
     * Attach the WebView client, Chrome client, and JavaScript interfaces.
     * Call this once during Activity onCreate after the binding is inflated.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun attach() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest,
            ): WebResourceResponse? =
                resolverProvider()?.intercept(request)

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url.toString()
                if (url.contains(EpubResourceResolver.VIRTUAL_HOST)) {
                    onNavigateToUrl(url)
                    return true
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        webView.context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        )
                    } catch (_: Exception) {
                    }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                val generation = pageLoadGeneration

                webView.evaluateJavascript(
                    "if(window.Caesura){window.Caesura.apply();}"
                ) {
                    if (generation != pageLoadGeneration) return@evaluateJavascript
                    installSelectionTracking()
                    onPageFinished()
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(
            ReaderSelectionBridge { selection ->
                webView.post {
                    val callback = pendingSelectionCallback
                    pendingSelectionCallback = null
                    callback?.invoke(selection)
                }
            },
            "LivreSelection"
        )

        webView.addJavascriptInterface(
            HighlightBridge(),
            "LivreHighlight"
        )

        webView.setOnLongClickListener { false }
    }

    /**
     * Increment the generation token to invalidate any in-flight
     * page-finished callbacks. Call this before loading a new chapter
     * or navigating to a new spine item.
     */
    fun bumpGeneration() {
        pageLoadGeneration++
    }

    /**
     * Capture the current text selection from the WebView. The JS
     * payload is sent to the [ReaderSelectionBridge] which invokes
     * [onCaptured] on the UI thread.
     *
     * @param onCaptured Callback receiving the selection locator, or null
     *                   if no selection is active.
     */
    /**
     * Installs one document-local selection cache. The cache is updated from
     * selectionchange plus the pointer-up events that commonly race the native
     * Android ActionMode. The action button then reads the last stable snapshot
     * instead of asking Chromium for a brand-new selection after the toolbar has
     * appeared.
     */
    private fun installSelectionTracking() {
        webView.evaluateJavascript(
            """(function(){
                if(window.LivreSelectionState && window.LivreSelectionState.installed)return;
                var state=window.LivreSelectionState=window.LivreSelectionState||{};
                state.installed=true;
                state.snapshot=null;

                function elementPath(n){
                    var parts=[];
                    while(n&&n.nodeType===1){
                        var idx=0,q=n.previousElementSibling;
                        while(q){
                            if(q.tagName===n.tagName)idx++;
                            q=q.previousElementSibling;
                        }
                        parts.unshift(n.tagName.toLowerCase()+':'+idx);
                        if(n===document.body)break;
                        n=n.parentElement;
                    }
                    return parts.join('/');
                }

                function textPath(n){
                    if(!n||n.nodeType!==3)return elementPath(n&&n.parentNode);
                    var parent=n.parentNode;
                    var base=elementPath(parent),idx=0,q=n.previousSibling;
                    while(q){
                        if(q.nodeType===3)idx++;
                        q=q.previousSibling;
                    }
                    return base+'/#text:'+idx;
                }

                function allTextNodes(){
                    var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);
                    var out=[],n;
                    while((n=walker.nextNode()))out.push(n);
                    return out;
                }

                function pointToTextPoint(container,offset,nodes){
                    if(!container)return null;
                    if(container.nodeType===3){
                        var len=(container.textContent||'').length;
                        return {node:container,offset:Math.max(0,Math.min(Number(offset)||0,len))};
                    }
                    if(container.nodeType!==1)return null;

                    // For an Element boundary (the browser uses these for
                    // selections that begin/end between inline elements), turn
                    // the child-node offset into a character offset within that
                    // element, then map that character boundary to a real text node.
                    var range=document.createRange();
                    try{
                        range.selectNodeContents(container);
                        range.setEnd(container,Math.max(0,Math.min(Number(offset)||0,container.childNodes.length)));
                        var localLength=range.toString().length;
                        range.detach();
                    }catch(e){
                        try{range.detach();}catch(ignore){}
                        return null;
                    }

                    var first=null,last=null,remaining=localLength;
                    var walker=document.createTreeWalker(container,NodeFilter.SHOW_TEXT,null,false);
                    var n;
                    while((n=walker.nextNode())){
                        var len=(n.textContent||'').length;
                        if(!first && remaining<=len)first={node:n,offset:remaining};
                        remaining-=len;
                        last={node:n,offset:len};
                    }
                    return first||last||null;
                }

                function buildSnapshot(){
                    var sel=window.getSelection&&window.getSelection();
                    if(!sel||!sel.rangeCount||!sel.toString()){
                        state.snapshot=null;
                        return null;
                    }
                    var range=sel.getRangeAt(0);
                    var nodes=allTextNodes();
                    var start=pointToTextPoint(range.startContainer,range.startOffset,nodes);
                    var end=pointToTextPoint(range.endContainer,range.endOffset,nodes);
                    if(!start||!end||!start.node||!end.node){
                        state.snapshot=null;
                        return null;
                    }

                    var startIndex=nodes.indexOf(start.node),endIndex=nodes.indexOf(end.node);
                    if(startIndex<0||endIndex<0){
                        state.snapshot=null;
                        return null;
                    }
                    if(startIndex>endIndex || (startIndex===endIndex&&start.offset>end.offset)){
                        var tmp=start;start=end;end=tmp;
                        var ti=startIndex;startIndex=endIndex;endIndex=ti;
                    }

                    var stream='',starts=[],i;
                    for(i=0;i<nodes.length;i++){
                        starts.push(stream.length);
                        stream+=(nodes[i].textContent||'');
                    }
                    var absoluteStart=starts[startIndex]+start.offset;
                    var absoluteEnd=starts[endIndex]+end.offset;
                    if(absoluteEnd<absoluteStart){
                        state.snapshot=null;
                        return null;
                    }
                    var text=sel.toString();
                    if(!text)return null;
                    var rect=range.getBoundingClientRect();
                    state.snapshot={
                        text:text,
                        spineHref:window.LivreSelectionSpineHref||'',
                        startPath:textPath(start.node),
                        startOffset:start.offset,
                        endPath:textPath(end.node),
                        endOffset:end.offset,
                        prefix:stream.slice(Math.max(0,absoluteStart-80),absoluteStart),
                        suffix:stream.slice(absoluteEnd,absoluteEnd+80),
                        rectLeft:Math.round(rect.left),
                        rectTop:Math.round(rect.top),
                        rectRight:Math.round(rect.right),
                        rectBottom:Math.round(rect.bottom),
                    };
                    return state.snapshot;
                }

                function refresh(){
                    try{buildSnapshot();}catch(e){state.snapshot=null;}
                }

                document.addEventListener('selectionchange',refresh);
                document.addEventListener('mouseup',function(){setTimeout(refresh,0);},true);
                document.addEventListener('touchend',function(){setTimeout(refresh,0);},true);
                refresh();
            })();""".trimIndent(),
            null,
        )
    }

    /**
     * Capture the last stable selection snapshot. If the event cache is not
     * available yet, build one synchronously from the current Range as a safe
     * fallback. The fallback uses the same element-boundary normalization as the
     * cache, so element-node selections do not silently become offset 0.
     */
    fun captureCurrentSelection(onCaptured: ((ReaderSelectionLocator?) -> Unit)? = null) {
        pendingSelectionCallback = onCaptured
        val href = getCurrentSpineHref().orEmpty()
        if (href.isBlank()) {
            pendingSelectionCallback = null
            onCaptured?.invoke(null)
            return
        }
        val escapedHref = org.json.JSONObject.quote(href)
        webView.evaluateJavascript(
            """(function(){
                var href=$escapedHref;
                if(window.LivreSelectionState && window.LivreSelectionState.snapshot){
                    var s=window.LivreSelectionState.snapshot;
                    LivreSelection.onSelectionPayload(s.text,href,s.startPath,s.startOffset,s.endPath,s.endOffset,s.prefix,s.suffix,s.rectLeft,s.rectTop,s.rectRight,s.rectBottom);
                    return;
                }
                var sel=window.getSelection&&window.getSelection();
                if(!sel||!sel.rangeCount||!sel.toString())return;
                var range=sel.getRangeAt(0);
                function ep(n){
                    var a=[];
                    while(n&&n.nodeType===1){
                        var idx=0,q=n.previousElementSibling;
                        while(q){if(q.tagName===n.tagName)idx++;q=q.previousElementSibling;}
                        a.unshift(n.tagName.toLowerCase()+':'+idx);
                        if(n===document.body)break;
                        n=n.parentElement;
                    }
                    return a.join('/');
                }
                function tp(n){
                    if(!n||n.nodeType!==3)return ep(n&&n.parentNode);
                    var idx=0,q=n.previousSibling;
                    while(q){if(q.nodeType===3)idx++;q=q.previousSibling;}
                    return ep(n.parentNode)+'/#text:'+idx;
                }
                function toTextPoint(container,offset){
                    if(container&&container.nodeType===3){
                        var len=(container.textContent||'').length;
                        return {node:container,offset:Math.max(0,Math.min(Number(offset)||0,len))};
                    }
                    if(!container||container.nodeType!==1)return null;
                    var r=document.createRange(),len=0;
                    try{
                        r.selectNodeContents(container);
                        r.setEnd(container,Math.max(0,Math.min(Number(offset)||0,container.childNodes.length)));
                        len=r.toString().length;
                        r.detach();
                    }catch(e){try{r.detach();}catch(ignore){}return null;}
                    var w=document.createTreeWalker(container,NodeFilter.SHOW_TEXT,null,false),n,last=null,remaining=len;
                    while((n=w.nextNode())){
                        var l=(n.textContent||'').length;
                        if(remaining<=l)return {node:n,offset:remaining};
                        remaining-=l;last=n;
                    }
                    return last?{node:last,offset:(last.textContent||'').length}:null;
                }
                var a=toTextPoint(range.startContainer,range.startOffset),b=toTextPoint(range.endContainer,range.endOffset);
                if(!a||!b)return;
                var rr=range.getBoundingClientRect(),nodes=[],w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false),n;
                while((n=w.nextNode()))nodes.push(n);
                var ai=nodes.indexOf(a.node),bi=nodes.indexOf(b.node);
                if(ai<0||bi<0)return;
                if(ai>bi||(ai===bi&&a.offset>b.offset)){var tmp=a;a=b;b=tmp;var ti=ai;ai=bi;bi=ti;}
                var stream='',starts=[];
                for(var i=0;i<nodes.length;i++){starts.push(stream.length);stream+=(nodes[i].textContent||'');}
                var as=starts[ai]+a.offset,ae=starts[bi]+b.offset;
                LivreSelection.onSelectionPayload(sel.toString(),href,tp(a.node),a.offset,tp(b.node),b.offset,stream.slice(Math.max(0,as-80),as),stream.slice(ae,ae+80),Math.round(rr.left),Math.round(rr.top),Math.round(rr.right),Math.round(rr.bottom));
            })();""".trimIndent(),
            null,
        )
    }

    /**
     * Evaluate a JavaScript expression in the main WebView.
     * Convenience wrapper for [WebView.evaluateJavascript].
     */
    fun evaluateJavascript(script: String, resultCallback: ((String?) -> Unit)? = null) {
        webView.evaluateJavascript(script, resultCallback)
    }
}
