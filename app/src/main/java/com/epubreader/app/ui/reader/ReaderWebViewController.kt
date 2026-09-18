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
    fun captureCurrentSelection(onCaptured: ((ReaderSelectionLocator?) -> Unit)? = null) {
        pendingSelectionCallback = onCaptured
        val href = getCurrentSpineHref().orEmpty()
        if (href.isBlank()) return
        val escapedHref = org.json.JSONObject.quote(href)
        webView.evaluateJavascript(
            "(function(){var s=window.getSelection&&window.getSelection();if(!s||s.rangeCount===0||!s.toString().trim())return;var r=s.getRangeAt(0);var rect=r.getBoundingClientRect();function p(n){var isText=n&&n.nodeType===3;if(isText){var parent=n.parentNode;var base=p(parent),ti=0,q=n.previousSibling;while(q){if(q.nodeType===3)ti++;q=q.previousSibling;}return base+'/#text:'+ti;}if(n&&n.nodeType!==1)n=n.parentNode;var a=[];while(n&&n.nodeType===1){var i=0,q=n.previousSibling;while(q){if(q.nodeType===n.nodeType&&q.nodeName===n.nodeName)i++;q=q.previousSibling;}a.unshift(n.nodeName.toLowerCase()+':'+i);n=n.parentNode;}return a.join('/');}var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);var allText='',nodes=[];while(walker.nextNode()){nodes.push({node:walker.currentNode,start:allText.length});allText+=walker.currentNode.textContent;}var t=s.toString().trim();var selStart=0,selEnd=0;var sc=r.startContainer,ec=r.endContainer;for(var i=0;i<nodes.length;i++){if(nodes[i].node===sc)selStart=nodes[i].start+r.startOffset;if(nodes[i].node===ec){selEnd=nodes[i].start+r.endOffset;break;}}var prefix=allText.slice(Math.max(0,selStart-40),selStart);var suffix=allText.slice(selEnd,selEnd+40);LivreSelection.onSelectionPayload(t,\" + escapedHref + \",p(r.startContainer),r.startOffset,p(r.endContainer),r.endOffset,prefix,suffix,Math.round(rect.left),Math.round(rect.top),Math.round(rect.right),Math.round(rect.bottom));})();",
            null
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
