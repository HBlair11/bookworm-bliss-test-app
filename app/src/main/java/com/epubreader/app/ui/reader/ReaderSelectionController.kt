package com.epubreader.app.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Handler
import android.os.SystemClock
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.epubreader.app.R
import com.epubreader.app.features.define.DefinitionService
import com.epubreader.app.epub.DictionaryLookup
import com.epubreader.app.epub.EpubBook
import com.epubreader.app.epub.ReaderSelectionLocator
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ReaderSelectionController — Extracted from ReaderActivity (Phase 6).
 *
 * Owns text-selection handling: ActionMode lifecycle, the floating selection
 * toolbar (with drag support), define / search / share / translate / copy
 * actions, the contextual definition card, and tap-suppression guards that
 * prevent a selection-dismiss tap from also turning the page or toggling chrome.
 *
 * State (currentSelectionActionMode, selectionToolbarPopup, etc.) remains in
 * the Activity to avoid split-brain issues with other controllers and the
 * touch listener that reads it. The controller reads/writes state through
 * the [State] interface and fires cross-controller actions through [Callbacks].
 *
 * @param config     View references, layout inflater, handler, and app context.
 * @param state      Read/write access to selection and definition state.
 * @param callbacks  Cross-controller actions (capture selection, string lookup,
 *                   activity launch, bookmark, highlight, coroutine scope, etc.).
 */
class ReaderSelectionController(
    val config: Config,
    val state: State,
    val callbacks: Callbacks,
) {

    data class Config(
        val webView: WebView,
        val rootView: View,
        val layoutInflater: LayoutInflater,
        val handler: Handler,
        val applicationContext: Context,
        // Phase 7 feature service — owns dictionary lifecycle + history.
        val definitionService: DefinitionService,
    )

    interface State {
        val epub: EpubBook?
        val isFinishing: Boolean
        val isDestroyed: Boolean
        val bookId: Long

        var currentSelectionActionMode: ActionMode?
        var selectionToolbarPopup: PopupWindow?
        var currentReaderSelection: ReaderSelectionLocator?
        var consumingSelectionDismissTap: Boolean
        var suppressReaderTapUntilMs: Long
        var definitionPopup: PopupWindow?
    }

    interface Callbacks {
        fun captureCurrentSelection(onCaptured: ((ReaderSelectionLocator?) -> Unit)?)
        fun getString(resId: Int, vararg args: Any): String
        fun startActivity(intent: Intent)
        fun resolveActivity(intent: Intent): Boolean
        fun addBookmarkFromSelection(selection: ReaderSelectionLocator)
        fun showHighlightColorPicker(selection: ReaderSelectionLocator?)
        fun themeColor(attr: Int): Int
        fun launchIo(block: suspend CoroutineScope.() -> Unit)
    }

    private val resources get() = config.rootView.resources
    private val context get() = config.rootView.context

    // ---- ActionMode lifecycle ----

    fun onActionModeStarted(mode: ActionMode) {
        state.currentSelectionActionMode = mode
        state.definitionPopup?.dismiss()
        config.webView.evaluateJavascript(
            "if(window.Caesura&&window.Caesura.setSelectionPageLock){window.Caesura.setSelectionPageLock(true);}",
            null,
        )
        // LivreWebView suppresses the native floating menu through the ActionMode
        // callback lifecycle, without calling ActionMode.hide()/finish() or mutating
        // the live menu from asynchronous touch callbacks. This keeps Chromium's
        // selection handles and lifecycle intact.
        config.webView.postDelayed({ showReaderSelectionToolbar() }, 50L)
    }

    fun onActionModeFinished(mode: ActionMode) {
        config.webView.evaluateJavascript(
            "if(window.Caesura&&window.Caesura.setSelectionPageLock){window.Caesura.setSelectionPageLock(false);}",
            null,
        )
        if (state.currentSelectionActionMode === mode) state.currentSelectionActionMode = null
        state.selectionToolbarPopup?.dismiss()
        state.selectionToolbarPopup = null
        state.currentReaderSelection = null
    }

    // ---- Floating selection toolbar ----

    fun showReaderSelectionToolbar() {
        callbacks.captureCurrentSelection { selection ->
            if (selection == null || selection.text.isBlank()) return@captureCurrentSelection
            state.currentReaderSelection = selection
            val content = config.layoutInflater.inflate(R.layout.reader_selection_toolbar, null, false)
            val copy = content.findViewById<TextView>(R.id.selection_toolbar_copy)
            val define = content.findViewById<TextView>(R.id.selection_toolbar_define)
            val highlight = content.findViewById<TextView>(R.id.selection_toolbar_highlight)
            val more = content.findViewById<ImageButton>(R.id.selection_toolbar_more)

            copy.setOnClickListener { copySelectedText() }
            define.setOnClickListener {
                val selected = state.currentReaderSelection ?: return@setOnClickListener
                dismissReaderSelectionToolbar(true)
                showDefinition(selected)
            }
            highlight.setOnClickListener {
                val selected = state.currentReaderSelection ?: return@setOnClickListener
                dismissReaderSelectionToolbar(true)
                callbacks.showHighlightColorPicker(selected)
            }
            more.setOnClickListener { showSelectionMoreMenu(more, state.currentReaderSelection) }

            val popup = PopupWindow(
                content,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                isOutsideTouchable = false
                isFocusable = false
                elevation = resources.getDimension(R.dimen.definition_card_elevation)
                setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.reader_selection_toolbar_bg))
            }

            state.selectionToolbarPopup?.dismiss()
            state.selectionToolbarPopup = popup
            installSelectionToolbarDrag(content, popup)
            popup.showAtLocation(config.rootView, Gravity.TOP or Gravity.START, toolbarX(selection), toolbarY(selection))
            positionSelectionToolbar(popup, selection)
        }
    }

    /**
     * Lets the entire custom toolbar act as a draggable surface. A tap on an
     * action view still performs that action; once the touch moves beyond the
     * normal touch slop, it becomes a toolbar drag and the action is suppressed.
     * Movement is free in both directions and clamped to the app's existing
     * popup edge margin.
     */
    fun installSelectionToolbarDrag(content: View, popup: PopupWindow) {
        val touchSlop = ViewConfiguration.get(content.context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var dragging = false
        var moved = false
        var popupX = 0
        var popupY = 0
        var positionInitialized = false

        fun refreshPopupPosition() {
            val popupWidth = popup.contentView.measuredWidth
            val popupHeight = popup.contentView.measuredHeight
            val margin = resources.getDimensionPixelSize(R.dimen.screen_padding_h)
            val rootWidth = config.rootView.width
            val rootHeight = config.rootView.height
            val maxX = (rootWidth - popupWidth - margin).coerceAtLeast(margin)
            val maxY = (rootHeight - popupHeight - margin).coerceAtLeast(margin)
            popupX = popupX.coerceIn(margin, maxX)
            popupY = popupY.coerceIn(margin, maxY)
            popup.update(popupX, popupY, -1, -1)
        }

        fun initializePositionIfNeeded() {
            if (positionInitialized) return
            val selection = state.currentReaderSelection ?: return
            popupX = toolbarX(selection)
            popupY = toolbarY(selection)
            positionInitialized = true
        }

        val touchListener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (state.selectionToolbarPopup !== popup) {
                        false
                    } else {
                        initializePositionIfNeeded()
                        downRawX = event.rawX
                        downRawY = event.rawY
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        dragging = true
                        moved = false
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        false
                    } else {
                        val deltaX = event.rawX - lastRawX
                        val deltaY = event.rawY - lastRawY
                        if (!moved &&
                            (abs(event.rawX - downRawX) > touchSlop ||
                                abs(event.rawY - downRawY) > touchSlop)
                        ) {
                            moved = true
                        }
                        if (moved) {
                            popupX += deltaX.roundToInt()
                            popupY += deltaY.roundToInt()
                            refreshPopupPosition()
                        }
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        false
                    } else {
                        val wasMoved = moved
                        dragging = false
                        moved = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)

                        // A tap on an action view is still a normal action click.
                        // A drag on that same view moves the toolbar instead and
                        // must not trigger the action.
                        if (!wasMoved && view !== content && view.isClickable) {
                            view.performClick()
                        }
                        true
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    moved = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                else -> dragging
            }
        }

        // The root handles its own padding/background. The inner container
        // handles the spaces between actions. Action views themselves retain
        // their existing click listeners and therefore remain tappable.
        content.setOnTouchListener(touchListener)
        if (content is ViewGroup && content.childCount > 0) {
            content.getChildAt(0).setOnTouchListener(touchListener)
        }
    }

    fun positionSelectionToolbar(popup: PopupWindow, selection: ReaderSelectionLocator) {
        // Positioning is recalculated immediately after layout so measured width
        // is available. All later movement uses the same root-relative coordinate
        // system and is clamped to the existing app popup edge margin.
        config.rootView.post {
            if (state.selectionToolbarPopup !== popup) return@post
            val x = toolbarX(selection)
            val y = toolbarY(selection)
            popup.update(x, y, -1, -1)
        }
    }

    fun toolbarX(selection: ReaderSelectionLocator): Int {
        val rootLocation = IntArray(2)
        val webViewLocation = IntArray(2)
        config.rootView.getLocationOnScreen(rootLocation)
        config.webView.getLocationOnScreen(webViewLocation)
        val scale = config.webView.scale
        val center = ((selection.rectLeft + selection.rectRight) / 2f) * scale
        val widthEstimate = resources.getDimensionPixelSize(R.dimen.selection_toolbar_estimated_width)
        val margin = resources.getDimensionPixelSize(R.dimen.screen_padding_h)
        return (webViewLocation[0] + center - widthEstimate / 2f - rootLocation[0]).roundToInt()
            .coerceAtLeast(margin)
    }

    fun toolbarY(selection: ReaderSelectionLocator): Int {
        val rootLocation = IntArray(2)
        val webViewLocation = IntArray(2)
        config.rootView.getLocationOnScreen(rootLocation)
        config.webView.getLocationOnScreen(webViewLocation)
        val scale = config.webView.scale
        val top = webViewLocation[1] + selection.rectTop * scale
        val toolbarHeight = resources.getDimensionPixelSize(R.dimen.selection_toolbar_height)
        val margin = resources.getDimensionPixelSize(R.dimen.screen_padding_h)
        return (top - toolbarHeight - margin - rootLocation[1]).roundToInt().coerceAtLeast(margin)
    }

    fun dismissReaderSelectionToolbar(finishActionMode: Boolean) {
        state.selectionToolbarPopup?.dismiss()
        state.selectionToolbarPopup = null
        state.currentReaderSelection = null
        if (finishActionMode) {
            state.currentSelectionActionMode?.finish()
            state.currentSelectionActionMode = null
        }
    }

    // ---- Selection actions ----

    fun copySelectedText() {
        val text = state.currentReaderSelection?.text?.trim().orEmpty()
        if (text.isBlank()) return
        val clipboard = config.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(callbacks.getString(R.string.selection_copy), text))
        dismissReaderSelectionToolbar(true)
    }

    fun processSelectedText(action: String) {
        val text = state.currentReaderSelection?.text?.trim().orEmpty()
        if (text.isBlank()) return
        val intent = Intent(action).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }
        if (callbacks.resolveActivity(intent)) callbacks.startActivity(Intent.createChooser(intent, callbacks.getString(R.string.selection_translate)))
    }

    fun webSearchSelectedText() {
        val text = state.currentReaderSelection?.text?.trim().orEmpty()
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra("query", text) }
        if (callbacks.resolveActivity(intent)) callbacks.startActivity(intent)
        dismissReaderSelectionToolbar(true)
    }

    fun showSelectionMoreMenu(anchor: View, selection: ReaderSelectionLocator?) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(callbacks.getString(R.string.selection_web_search)).setOnMenuItemClickListener {
            webSearchSelectedText()
            true
        }
        popup.menu.add(callbacks.getString(R.string.selection_translate)).setOnMenuItemClickListener {
            processSelectedText(Intent.ACTION_PROCESS_TEXT)
            true
        }
        popup.menu.add(callbacks.getString(R.string.selection_share)).setOnMenuItemClickListener {
            val text = selection?.text?.trim().orEmpty()
            if (text.isNotBlank()) {
                callbacks.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, callbacks.getString(R.string.selection_share)))
            }
            dismissReaderSelectionToolbar(true)
            true
        }
        popup.menu.add(callbacks.getString(R.string.selection_select_all)).setOnMenuItemClickListener {
            config.webView.evaluateJavascript("if(window.getSelection){var s=window.getSelection();s.selectAllChildren(document.body);}", null)
            popup.dismiss()
            true
        }
        popup.menu.add(callbacks.getString(R.string.add_bookmark)).setOnMenuItemClickListener {
            val selected = selection ?: state.currentReaderSelection
            popup.dismiss()
            dismissReaderSelectionToolbar(true)
            if (selected != null) callbacks.addBookmarkFromSelection(selected)
            true
        }
        popup.show()
    }

    /** Dismisses the active text-selection ActionMode (the floating toolbar with
     *  Copy / Define / Highlight / …) and clears the WebView selection ranges.
     *  Called before any navigation away from the reader page (TOC / Bookmarks /
     *  Highlights / Search / Settings / TTS overlay / chapter change) and on a
     *  fresh tap on the page, so the selection toolbar never lingers. */
    fun clearReaderSelection() {
        config.webView.evaluateJavascript(
            "if(window.Caesura&&window.Caesura.setSelectionPageLock){window.Caesura.setSelectionPageLock(false);}",
            null,
        )
        dismissReaderSelectionToolbar(false)
        state.currentSelectionActionMode?.finish()
        state.currentSelectionActionMode = null
        config.webView.evaluateJavascript(
            "if(window.getSelection){try{window.getSelection().removeAllRanges();}catch(e){}}",
            null,
        )
    }

    // ---- Tap suppression ----

    /** Suppresses page-turn / chrome-toggle for a short window after a tap that
     *  should not change the page (highlight tap, selection dismissal). The
     *  GestureDetector's onSingleTapConfirmed fires ~200ms after ACTION_DOWN;
     *  a 700ms window comfortably covers it. Patch v37. */
    fun suppressReaderTap(durationMs: Long = 700L) {
        state.suppressReaderTapUntilMs = SystemClock.uptimeMillis() + durationMs
    }

    fun shouldSuppressReaderTap(): Boolean =
        SystemClock.uptimeMillis() < state.suppressReaderTapUntilMs

    // ---- Definition card ----

    fun showDefinition(selection: ReaderSelectionLocator?) {
        val raw = selection?.text?.trim().orEmpty()
        if (raw.isBlank()) {
            Snackbar.make(config.rootView, R.string.selection_none, Snackbar.LENGTH_SHORT).show()
            return
        }
        callbacks.launchIo {
            // Language follows the EPUB's dc:language so multi-language
            // libraries switch dictionaries automatically (falls back to
            // English when no matching dict/<lang>.db asset is bundled).
            // Dictionary lifecycle (language switching, close) is owned by
            // the DefinitionService (Phase 7).
            val lang = state.epub?.metadata?.language
            val result = config.definitionService.define(raw, lang)
            withContext(Dispatchers.Main) {
                if (state.isFinishing || state.isDestroyed) return@withContext
                showDefinitionCard(raw, result, selection)
            }
        }
    }

    /**
     * Patch v37: contextual definition card anchored near the selection.
     *
     * Positioned above the selection when there is room, otherwise below it,
     * clamped to the screen so it never runs off either edge; falls back to a
     * bottom-anchored card when no selection rect is available. Suggestions
     * are re-lookable with a tap, and a hook hands the word off to any
     * external dictionary app via ACTION_PROCESS_TEXT.
     */
    fun showDefinitionCard(
        raw: String,
        result: DictionaryLookup.Result,
        selection: ReaderSelectionLocator?,
    ) {
        state.definitionPopup?.dismiss()
        val parent = config.rootView as ViewGroup
        val card = config.layoutInflater.inflate(R.layout.view_definition_card, parent, false)
        val wordView = card.findViewById<TextView>(R.id.dictWord)
        val noResult = card.findViewById<TextView>(R.id.dictNoResult)
        val entriesBox = card.findViewById<LinearLayout>(R.id.dictEntries)
        val suggestionsLabel = card.findViewById<TextView>(R.id.dictSuggestionsLabel)
        val suggestionsBox = card.findViewById<LinearLayout>(R.id.dictSuggestions)
        val externalButton = card.findViewById<MaterialButton>(R.id.dictExternalButton)

        wordView.text = raw.trim()
        val primary = themeColor(android.R.attr.textColorPrimary)
        val accent = themeColor(com.google.android.material.R.attr.colorPrimary)

        if (result.entries.isEmpty()) {
            noResult.visibility = View.VISIBLE
        } else {
            noResult.visibility = View.GONE
            result.entries.forEach { entry ->
                val line = SpannableString("${entry.partOfSpeech}  ${entry.definition}")
                line.setSpan(StyleSpan(Typeface.BOLD), 0, entry.partOfSpeech.length, 0)
                entriesBox.addView(TextView(context).apply {
                    text = line
                    textSize = 14f
                    setTextColor(primary)
                    setPadding(0, (4 * resources.displayMetrics.density).roundToInt(), 0, 0)
                })
            }
        }

        if (result.suggestions.isNotEmpty()) {
            suggestionsLabel.visibility = View.VISIBLE
            result.suggestions.forEach { suggestion ->
                suggestionsBox.addView(TextView(context).apply {
                    text = suggestion
                    textSize = 14f
                    setTextColor(accent)
                    setPadding(0, (2 * resources.displayMetrics.density).roundToInt(), 0, 0)
                    setOnClickListener { showDefinition(ReaderSelectionLocator(suggestion, "", "", 0, "", 0)) }
                })
            }
        } else {
            suggestionsLabel.visibility = View.GONE
        }

        externalButton.visibility = View.VISIBLE
        externalButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, raw)
                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            }
            try {
                callbacks.startActivity(Intent.createChooser(intent, callbacks.getString(R.string.dictionary_lookup_external)))
            } catch (_: android.content.ActivityNotFoundException) {
                Snackbar.make(config.rootView, R.string.dictionary_external_none, Snackbar.LENGTH_SHORT).show()
            }
        }

        // Persist the lookup in the offline vocabulary history (IO thread).
        // Ownership moved to DefinitionService (Phase 7); recorded only when
        // a definition card is actually shown, matching the original behavior.
        if (result.entries.isNotEmpty()) {
            val bookId = state.bookId
            callbacks.launchIo {
                config.definitionService.recordLookup(raw, result, bookId)
            }
        }

        val density = resources.displayMetrics.density
        val popup = PopupWindow(card, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = resources.getDimension(R.dimen.definition_card_elevation)
        }
        state.definitionPopup = popup
        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val cardW = card.measuredWidth.coerceAtMost(resources.displayMetrics.widthPixels)
        val cardH = card.measuredHeight
        val anchorLeft: Float
        val anchorTop: Float
        val anchorBottom: Float
        if (selection != null && selection.hasRect) {
            val loc = IntArray(2)
            config.webView.getLocationInWindow(loc)
            anchorLeft = loc[0] + selection.rectLeft * density
            anchorTop = loc[1] + selection.rectTop * density
            anchorBottom = loc[1] + selection.rectBottom * density
        } else {
            anchorLeft = 0f
            anchorTop = resources.displayMetrics.heightPixels.toFloat()
            anchorBottom = resources.displayMetrics.heightPixels.toFloat()
        }
        val margin = resources.getDimension(R.dimen.popup_screen_margin)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val x = (anchorLeft - cardW / 2f).roundToInt().coerceIn(margin.toInt(), (screenW - cardW - margin).toInt().coerceAtLeast(margin.toInt()))
        val y = if (anchorTop - cardH - margin >= 0) {
            // Enough room above the selection.
            (anchorTop - cardH - margin).roundToInt()
        } else if (anchorBottom + cardH + margin <= screenH) {
            // Room below.
            (anchorBottom + margin).roundToInt()
        } else {
            // Center of the screen as a last resort.
            ((screenH - cardH) / 2f).roundToInt()
        }
        popup.showAtLocation(config.rootView, Gravity.NO_GRAVITY, x, y)
    }

    // ---- Theme helper ----

    fun themeColor(attr: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attr, value, true)
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }
}
