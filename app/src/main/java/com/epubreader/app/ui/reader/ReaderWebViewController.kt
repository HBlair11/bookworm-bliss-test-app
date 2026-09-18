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

    companion object {
        /**
         * JS injected after each page load to maintain a cached selection
         * snapshot. The snapshot is refreshed on `selectionchange`,
         * `touchend`, and `mouseup` so it reflects the user's most recent
         * selection even if the live WebView selection has been cleared
         * by the time [captureCurrentSelection] is called.
         *
         * Key design decisions:
         *  - Text is stored RAW (no trim) so DOM offsets remain consistent
         *    with the stored text. Display trimming happens on the Kotlin
         *    side via [ReaderSelectionLocator.displayText].
         *  - Element-node range boundaries are resolved to their text-node
         *    equivalents so the prefix/suffix computation works for
         *    selections that start/end on element nodes.
         *  - The DOM path function `p()` handles both text and element nodes.
         */
        private val SELECTION_SNAPSHOT_SCRIPT = """
(function(){
  if(window.__livreSelInstalled) return;
  window.__livreSelInstalled = true;
  window.__livreSelectionCache = null;

  function p(n){
    var isText=n&&n.nodeType===3;
    if(isText){
      var parent=n.parentNode;
      var base=p(parent),ti=0,q=n.previousSibling;
      while(q){if(q.nodeType===3)ti++;q=q.previousSibling;}
      return base+'/#text:'+ti;
    }
    if(n&&n.nodeType!==1)n=n.parentNode;
    var a=[];
    while(n&&n.nodeType===1){
      var i=0,q=n.previousSibling;
      while(q){if(q.nodeType===n.nodeType&&q.nodeName===n.nodeName)i++;q=q.previousSibling;}
      a.unshift(n.nodeName.toLowerCase()+':'+i);
      n=n.parentNode;
    }
    return a.join('/');
  }

  function resolveToText(container, offset){
    if(!container) return null;
    if(container.nodeType===3) return {node:container,offset:offset};
    var child=container.childNodes[offset];
    if(child&&child.nodeType===3) return {node:child,offset:0};
    if(child&&child.nodeType===1){
      var w=document.createTreeWalker(child,NodeFilter.SHOW_TEXT,null,false);
      if(w.nextNode()) return {node:w.currentNode,offset:0};
    }
    var pw=document.createTreeWalker(container,NodeFilter.SHOW_TEXT,null,false);
    var found=null,idx=0;
    while(pw.nextNode()){
      if(idx===offset){found=pw.currentNode;break;}
      idx++;
    }
    if(found) return {node:found,offset:0};
    return null;
  }

  function resolveEndToText(container, offset){
    if(!container) return null;
    if(container.nodeType===3) return {node:container,offset:offset};
    // Element node: range ends BEFORE childNodes[offset], so the end
    // is at the END of childNodes[offset-1].
    if(offset>0){
      var prev=container.childNodes[offset-1];
      if(prev&&prev.nodeType===3) return {node:prev,offset:(prev.textContent||'').length};
      if(prev&&prev.nodeType===1){
        var w=document.createTreeWalker(prev,NodeFilter.SHOW_TEXT,null,false);
        var last=null;
        while(w.nextNode()) last=w.currentNode;
        if(last) return {node:last,offset:(last.textContent||'').length};
      }
    }
    // offset===0 or fallback: start of the child at offset
    var child=container.childNodes[offset];
    if(child&&child.nodeType===3) return {node:child,offset:0};
    if(child&&child.nodeType===1){
      var w2=document.createTreeWalker(child,NodeFilter.SHOW_TEXT,null,false);
      if(w2.nextNode()) return {node:w2.currentNode,offset:0};
    }
    return null;
  }

  function capture(){
    var s=window.getSelection&&window.getSelection();
    if(!s||s.rangeCount===0){window.__livreSelectionCache=null;return;}
    var raw=s.toString();
    if(!raw||raw.trim().length===0){window.__livreSelectionCache=null;return;}
    var r=s.getRangeAt(0);
    var rect=r.getBoundingClientRect();

    var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);
    var allText='',nodes=[];
    while(walker.nextNode()){nodes.push({node:walker.currentNode,start:allText.length});allText+=walker.currentNode.textContent;}

    var startResolved=resolveToText(r.startContainer,r.startOffset);
    var endResolved=resolveEndToText(r.endContainer,r.endOffset);

    var selStart=0,selEnd=0;
    if(startResolved){
      for(var i=0;i<nodes.length;i++){
        if(nodes[i].node===startResolved.node){selStart=nodes[i].start+startResolved.offset;break;}
      }
    }
    if(endResolved){
      for(var j=0;j<nodes.length;j++){
        if(nodes[j].node===endResolved.node){selEnd=nodes[j].start+endResolved.offset;break;}
      }
    }
    if(selEnd===0&&endResolved){
      selEnd=selStart+raw.length;
    }

    var prefix=allText.slice(Math.max(0,selStart-40),selStart);
    var suffix=allText.slice(selEnd,selEnd+40);

    window.__livreSelectionCache={
      text:raw,
      startPath:startResolved?p(startResolved.node):p(r.startContainer),
      startOffset:startResolved?startResolved.offset:r.startOffset,
      endPath:endResolved?p(endResolved.node):p(r.endContainer),
      endOffset:endResolved?endResolved.offset:r.endOffset,
      prefix:prefix,
      suffix:suffix,
      rectLeft:Math.round(rect.left),
      rectTop:Math.round(rect.top),
      rectRight:Math.round(rect.right),
      rectBottom:Math.round(rect.bottom)
    };
  }

  document.addEventListener('selectionchange',capture);
  document.addEventListener('touchend',capture);
  document.addEventListener('mouseup',capture);
})();
""".trimIndent()
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
                    // Inject the selection-snapshot listeners after Caesura
                    // has applied pagination so they observe the final DOM.
                    webView.evaluateJavascript(SELECTION_SNAPSHOT_SCRIPT, null)
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
     * Capture the current text selection from the WebView.
     *
     * Uses the cached selection snapshot (maintained by
     * [SELECTION_SNAPSHOT_SCRIPT]) when available. The snapshot is
     * captured at the moment of selection (on `selectionchange` /
     * `touchend` / `mouseup`) rather than at action time, so it
     * reflects the user's actual selection even if the live WebView
     * selection has since been modified or cleared.
     *
     * Falls back to a live capture if no cached snapshot exists.
     *
     * @param onCaptured Callback receiving the selection locator, or null
     *                   if no selection is active.
     */
    fun captureCurrentSelection(onCaptured: ((ReaderSelectionLocator?) -> Unit)? = null) {
        pendingSelectionCallback = onCaptured
        val href = getCurrentSpineHref().orEmpty()
        if (href.isBlank()) {
            onCaptured?.invoke(null)
            return
        }
        // JSON-quote the href so it's a valid JS string literal (e.g.
        // "chapter1.xhtml").  Concatenate it into the JS with + so the
        // actual href value is passed — the original code had the
        // variable name as literal text inside one big string, so the
        // spineHref was always the literal string " + escapedHref + ".
        val escapedHref = org.json.JSONObject.quote(href)
        val js = "(" +
            "function(){" +
            "var cached=window.__livreSelectionCache;" +
            "if(cached){" +
            "LivreSelection.onSelectionPayload(cached.text," + escapedHref + ",cached.startPath,cached.startOffset,cached.endPath,cached.endOffset,cached.prefix,cached.suffix,cached.rectLeft,cached.rectTop,cached.rectRight,cached.rectBottom);" +
            "return;" +
            "}" +
            // --- Fallback: live capture (no cached snapshot) ---
            "var s=window.getSelection&&window.getSelection();" +
            "if(!s||s.rangeCount===0||!s.toString().trim())return;" +
            "var r=s.getRangeAt(0);" +
            "var rect=r.getBoundingClientRect();" +
            "function p(n){" +
            "var isText=n&&n.nodeType===3;" +
            "if(isText){var parent=n.parentNode;var base=p(parent),ti=0,q=n.previousSibling;while(q){if(q.nodeType===3)ti++;q=q.previousSibling;}return base+'/#text:'+ti;}" +
            "if(n&&n.nodeType!==1)n=n.parentNode;" +
            "var a=[];while(n&&n.nodeType===1){var i=0,q=n.previousSibling;while(q){if(q.nodeType===n.nodeType&&q.nodeName===n.nodeName)i++;q=q.previousSibling;}a.unshift(n.nodeName.toLowerCase()+':'+i);n=n.parentNode;}" +
            "return a.join('/');" +
            "}" +
            "function resolveToText(container,offset){" +
            "if(!container)return null;" +
            "if(container.nodeType===3)return{node:container,offset:offset};" +
            "var child=container.childNodes[offset];" +
            "if(child&&child.nodeType===3)return{node:child,offset:0};" +
            "if(child&&child.nodeType===1){var w=document.createTreeWalker(child,NodeFilter.SHOW_TEXT,null,false);if(w.nextNode())return{node:w.currentNode,offset:0};}" +
            "return null;" +
            "}" +
            "function resolveEndToText(container,offset){" +
            "if(!container)return null;" +
            "if(container.nodeType===3)return{node:container,offset:offset};" +
            "if(offset>0){var prev=container.childNodes[offset-1];if(prev&&prev.nodeType===3)return{node:prev,offset:(prev.textContent||'').length};if(prev&&prev.nodeType===1){var w=document.createTreeWalker(prev,NodeFilter.SHOW_TEXT,null,false);var last=null;while(w.nextNode())last=w.currentNode;if(last)return{node:last,offset:(last.textContent||'').length};}}" +
            "var child=container.childNodes[offset];" +
            "if(child&&child.nodeType===3)return{node:child,offset:0};" +
            "if(child&&child.nodeType===1){var w2=document.createTreeWalker(child,NodeFilter.SHOW_TEXT,null,false);if(w2.nextNode())return{node:w2.currentNode,offset:0};}" +
            "return null;" +
            "}" +
            "var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);" +
            "var allText='',nodes=[];" +
            "while(walker.nextNode()){nodes.push({node:walker.currentNode,start:allText.length});allText+=walker.currentNode.textContent;}" +
            "var t=s.toString();" +
            "var startResolved=resolveToText(r.startContainer,r.startOffset);" +
            "var endResolved=resolveEndToText(r.endContainer,r.endOffset);" +
            "var selStart=0,selEnd=0;" +
            "if(startResolved){for(var i=0;i<nodes.length;i++){if(nodes[i].node===startResolved.node){selStart=nodes[i].start+startResolved.offset;break;}}}" +
            "if(endResolved){for(var j=0;j<nodes.length;j++){if(nodes[j].node===endResolved.node){selEnd=nodes[j].start+endResolved.offset;break;}}}" +
            "if(selEnd===0&&endResolved){selEnd=selStart+t.length;}" +
            "var prefix=allText.slice(Math.max(0,selStart-40),selStart);" +
            "var suffix=allText.slice(selEnd,selEnd+40);" +
            "LivreSelection.onSelectionPayload(t," + escapedHref + ",startResolved?p(startResolved.node):p(r.startContainer),startResolved?startResolved.offset:r.startOffset,endResolved?p(endResolved.node):p(r.endContainer),endResolved?endResolved.offset:r.endOffset,prefix,suffix,Math.round(rect.left),Math.round(rect.top),Math.round(rect.right),Math.round(rect.bottom));" +
            "})();"
        webView.evaluateJavascript(js, null)
    }

    /**
     * Evaluate a JavaScript expression in the main WebView.
     * Convenience wrapper for [WebView.evaluateJavascript].
     */
    fun evaluateJavascript(script: String, resultCallback: ((String?) -> Unit)? = null) {
        webView.evaluateJavascript(script, resultCallback)
    }
}
