package com.epubreader.app.ui.reader

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.data.TtsSettingsEntity
import com.epubreader.app.epub.EpubBook
import com.epubreader.app.epub.ReaderTtsController
import com.epubreader.app.epub.ReaderTtsSegment
import com.epubreader.app.tts.ReaderTtsService
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * ReaderTtsUiController — Extracted from ReaderActivity (Phase 6).
 *
 * Owns all Read Aloud (TTS) UI: the full-screen TTS overlay, transport
 * controls (play/pause, prev/next sentence, stop, settings), the settings
 * bottom sheet (speed/pitch sliders, sleep timer, background playback
 * toggle, voice picker), bimodal word/sentence highlighting, and the
 * keep-alive foreground service lifecycle.
 *
 * State (ttsOverlayVisible, ttsOverlayRestoresChrome, ttsSettingsSheet,
 * ttsSleepTimer, ttsSettingsSavePending, ttsHighlightedRange) is managed
 * through the [State] interface to avoid split-brain issues with other
 * controllers. Cross-controller actions (clearReaderSelection,
 * updateHistoryUi, updatePageIndicator, sectionLabel, etc.) flow through
 * [Callbacks].
 *
 * @param config     View references, handler, contexts, and coroutine scope.
 * @param state      Read/write access to TTS and shared reader state.
 * @param callbacks  Cross-controller and Activity actions.
 */
class ReaderTtsUiController(
    val config: Config,
    val state: State,
    val callbacks: Callbacks,
) {

    data class Config(
        val ttsOverlay: View,
        val btnTtsPlayPause: ImageView,
        val btnTtsPrev: View,
        val btnTtsNext: View,
        val btnTtsStop: View,
        val btnTtsSettings: View,
        val btnTtsClose: View,
        val tvTtsStatus: TextView,
        val tvTtsBookTitle: TextView,
        val tvTtsSection: TextView,
        val topBar: View,
        val bottomBar: View,
        val tvPageIndicator: View,
        val webView: WebView,
        val rootView: View,
        val handler: Handler,
        val context: Context,
        val applicationContext: Context,
        val lifecycleScope: LifecycleCoroutineScope,
    )

    interface State {
        // ---- Read-only (owned by Activity) ----
        val ttsController: ReaderTtsController?
        val epub: EpubBook?
        val spineIndex: Int
        val bookEntity: BookEntity?
        val bookId: Long
        val prefs: PrefsManager
        val isFinishing: Boolean
        val isDestroyed: Boolean

        // ---- Read-write (managed by this controller) ----
        var ttsOverlayVisible: Boolean
        var ttsOverlayRestoresChrome: Boolean
        var ttsSettingsSheet: Any?
        var ttsSleepTimer: Int?
        var ttsSettingsSavePending: Boolean
        var ttsHighlightedRange: Pair<Int, Int>?
        var chromeVisible: Boolean
    }

    interface Callbacks {
        fun getString(resId: Int, vararg args: Any): String
        fun clearReaderSelection()
        fun updateHistoryUi()
        fun updatePageIndicator()
        fun sectionLabel(): String
        fun overlayVisible(): Boolean
        fun themeColor(attr: Int): Int
        fun showSnackbar(resId: Int, duration: Int)
        fun showSnackbarMessage(message: String, duration: Int)
        fun requestNotificationPermission()
        fun goToSpine(spineIndex: Int)
        fun runOnUiThread(action: () -> Unit)
    }

    companion object {
        private const val TTS_SETTINGS_SAVE_DELAY_MS = 800L
    }

    /** Debounced writer for per-book TTS settings. */
    private val saveTtsSettingsRunnable = Runnable { saveTtsSettingsNow() }

    // ---- TTS playback ----

    fun startTtsForCurrentChapter() {
        val book = state.epub ?: return
        val item = book.spine.getOrNull(state.spineIndex) ?: return
        if (state.ttsController?.state == ReaderTtsController.State.UNAVAILABLE) {
            callbacks.showSnackbar(com.epubreader.app.R.string.tts_unavailable, Snackbar.LENGTH_LONG)
            return
        }
        updateTtsServiceState()
        // Resolve the first readable text position in the page that is actually
        // visible. The JavaScript walker mirrors ReaderTtsDocumentBuilder:
        // ignored elements are skipped and <br> contributes one source space.
        // This gives the controller the same raw character coordinate instead of
        // using a guessed screen point or falling back to chapter offset zero.
        config.webView.evaluateJavascript(
            """(function(){
                try{
                    var body=document.body;if(!body)return 0;
                    var ignored={HEAD:1,SCRIPT:1,STYLE:1,NOSCRIPT:1,SVG:1,MATH:1};
                    var walker=document.createTreeWalker(body,NodeFilter.SHOW_TEXT,null,false);
                    var raw=0,best=-1,bestTop=1e9;
                    function inIgnored(n){var p=n.parentElement;while(p){if(ignored[p.tagName])return true;p=p.parentElement;}return false;}
                    function visibleOffset(n){
                        var len=n.textContent?n.textContent.length:0;
                        for(var i=0;i<len;i++){
                            var r=document.createRange();r.setStart(n,i);r.setEnd(n,Math.min(i+1,len));
                            var rect=r.getBoundingClientRect();
                            if(rect.width>0&&rect.height>0&&rect.right>0&&rect.left<window.innerWidth&&rect.bottom>0&&rect.top<window.innerHeight){
                                var y=rect.top;
                                if(y<bestTop){bestTop=y;best=raw+i;}
                                break;
                            }
                        }
                        raw+=len;
                    }
                    while(walker.nextNode()){
                        var n=walker.currentNode;
                        if(inIgnored(n))continue;
                        visibleOffset(n);
                    }
                    // Recompute raw offsets while treating <br> exactly as the
                    // builder does, then return the earliest visible source char.
                    raw=0;best=-1;bestTop=1e9;
                    var w=document.createTreeWalker(body,NodeFilter.SHOW_ALL,null,false),n;
                    while(n=w.nextNode()){
                        if(n.nodeType===1){
                            if(ignored[n.tagName]){try{w.currentNode=n;w.nextNode();}catch(e){} }
                            if(n.tagName==='BR')raw++;
                            continue;
                        }
                        if(n.nodeType!==3||inIgnored(n))continue;
                        var len=n.textContent?n.textContent.length:0;
                        for(var i=0;i<len;i++){
                            var r=document.createRange();r.setStart(n,i);r.setEnd(n,Math.min(i+1,len));
                            var rect=r.getBoundingClientRect();
                            if(rect.width>0&&rect.height>0&&rect.right>0&&rect.left<window.innerWidth&&rect.bottom>0&&rect.top<window.innerHeight){
                                if(rect.top<bestTop){bestTop=rect.top;best=raw+i;}
                                break;
                            }
                        }
                        raw+=len;
                    }
                    return Math.max(0,best);
                }catch(e){return 0;}
            })();""",
        ) { result ->
            val offset = result?.trim()?.removeSurrounding("\"")?.toIntOrNull() ?: 0
            config.lifecycleScope.launch { state.ttsController?.speakChapter(book.file, item.href, offset) }
        }
    }

    // ------------------------------------------------------------- patch v37 tts

    /** SeekBar progress -> engine speech rate (0.5 + N * 0.05). */
    fun ttsRateFor(progress: Int): Float = 0.5f + progress * 0.05f

    /** SeekBar progress -> engine pitch (0.5 + N * 0.05). */
    fun ttsPitchFor(progress: Int): Float = 0.5f + progress * 0.05f

    /** Transport + status state for the dedicated TTS overlay. The overlay's
     *  own visibility is managed by [showTtsOverlay] / [hideTtsOverlay] (opened
     *  by the speaker icon, closed by the back / stop controls); this only
     *  refreshes the play/pause icon and the status line. */
    fun updateTtsControlsUi(playing: Boolean) {
        config.btnTtsPlayPause.setImageResource(if (playing) com.epubreader.app.R.drawable.ic_pause else com.epubreader.app.R.drawable.ic_play)
        config.btnTtsPlayPause.contentDescription = callbacks.getString(if (playing) com.epubreader.app.R.string.tts_pause else com.epubreader.app.R.string.tts_play)
        config.tvTtsStatus.text = when (state.ttsController?.state) {
            ReaderTtsController.State.PLAYING -> callbacks.getString(com.epubreader.app.R.string.tts_status_reading)
            ReaderTtsController.State.PAUSED -> callbacks.getString(com.epubreader.app.R.string.tts_status_paused)
            else -> callbacks.getString(com.epubreader.app.R.string.tts_status_reading)
        }
    }

    /** Shows the Read Aloud panel as the only reader chrome. The normal reader
     *  top bar, bottom timeline, history controls and page indicator are hidden
     *  while TTS is open; the EPUB page itself remains visible underneath. */
    fun showTtsOverlay() {
        callbacks.clearReaderSelection()
        state.ttsOverlayRestoresChrome = state.chromeVisible
        state.chromeVisible = false
        config.topBar.visibility = View.GONE
        config.bottomBar.visibility = View.GONE
        config.tvPageIndicator.visibility = View.GONE
        config.ttsOverlay.visibility = View.VISIBLE
        state.ttsOverlayVisible = true
        config.tvTtsBookTitle.text = state.epub?.metadata?.title?.ifBlank { null }
            ?: state.bookEntity?.title
            ?: callbacks.getString(com.epubreader.app.R.string.app_name)
        config.tvTtsSection.text = callbacks.sectionLabel()
        val playing = state.ttsController?.state == ReaderTtsController.State.PLAYING
        updateTtsControlsUi(playing)
        callbacks.updateHistoryUi()
    }

    /** Hides the Read Aloud panel and restores the reader chrome to the exact
     *  visibility state it had when TTS was opened. Read-aloud itself is not
     *  stopped here. */
    fun hideTtsOverlay() {
        config.ttsOverlay.visibility = View.GONE
        state.ttsOverlayVisible = false
        state.chromeVisible = state.ttsOverlayRestoresChrome
        config.topBar.visibility = if (state.chromeVisible) View.VISIBLE else View.GONE
        config.bottomBar.visibility = if (state.chromeVisible) View.VISIBLE else View.GONE
        config.tvPageIndicator.visibility =
            if (!state.chromeVisible && !callbacks.overlayVisible()) View.VISIBLE else View.GONE
        callbacks.updateHistoryUi()
        callbacks.updatePageIndicator()
    }

    /** Starts/stops the keep-alive foreground service with the media
     *  notification when background playback is enabled. The service stays
     *  alive while paused so the notification can offer Resume. */
    fun updateTtsServiceState() {
        val ttsState = state.ttsController?.state ?: ReaderTtsController.State.INITIALIZING
        val active = ttsState == ReaderTtsController.State.PLAYING || ttsState == ReaderTtsController.State.PAUSED
        if (active && state.prefs.ttsBackgroundPlayback) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                config.context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                callbacks.requestNotificationPermission()
            }
            val title = state.epub?.metadata?.title ?: callbacks.getString(com.epubreader.app.R.string.app_name)
            ReaderTtsService.start(config.context, state.bookId, title)
        } else {
            ReaderTtsService.stop(config.context)
        }
    }

    fun stopTtsCompletely() {
        state.ttsController?.stop()
        clearSpokenWordHighlight()
        ReaderTtsService.stop(config.context)
        config.tvTtsStatus.text = callbacks.getString(com.epubreader.app.R.string.action_read_aloud)
        hideTtsOverlay()
    }

    fun showSleepTimerMenu() {
        val minutes = intArrayOf(5, 10, 15, 30, 45, 60)
        val labels = minutes.map { callbacks.getString(com.epubreader.app.R.string.tts_sleep_minutes, it) }.toTypedArray()
        AlertDialog.Builder(config.context)
            .setTitle(com.epubreader.app.R.string.tts_sleep_timer)
            .setSingleChoiceItems(labels, -1) { dialog, which ->
                state.ttsController?.startSleepTimer(minutes[which])
                state.ttsSleepTimer = minutes[which]
                dialog.dismiss()
            }
            .setNeutralButton(com.epubreader.app.R.string.tts_sleep_off) { _, _ ->
                state.ttsController?.cancelSleepTimer()
                state.ttsSleepTimer = null
                updateTtsControlsUi(state.ttsController?.state == ReaderTtsController.State.PLAYING)
            }
            .setNegativeButton(com.epubreader.app.R.string.cancel, null)
            .show()
    }

    /** Read-aloud settings sub-screen (opened from the TTS overlay's tune
     *  button). Hosts the speed/pitch sliders, sleep timer, background-playback
     *  toggle and voice picker so the main overlay stays clean. */
    fun showTtsSettingsSheet() {
        val controller = state.ttsController ?: return
        val dialog = BottomSheetDialog(config.context)
        state.ttsSettingsSheet = dialog
        val density = config.context.resources.displayMetrics.density
        val pad = (20 * density).roundToInt()
        val root = LinearLayout(config.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val sectionGap = (12 * density).roundToInt()
        root.addView(TextView(config.context).apply {
            text = callbacks.getString(com.epubreader.app.R.string.tts_settings)
            textSize = 16f
            setTextColor(callbacks.themeColor(android.R.attr.textColorPrimary))
            setPadding(0, 0, 0, sectionGap)
        })

        fun sliderRow(labelRes: Int, value: Float, max: Int, progress: Int, onChange: (Float) -> Unit, onStop: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(config.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, sectionGap)
            }
            val labelRow = LinearLayout(config.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            labelRow.addView(TextView(config.context).apply {
                text = callbacks.getString(labelRes)
                textSize = 14f
                setTextColor(callbacks.themeColor(android.R.attr.textColorPrimary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val valueView = TextView(config.context).apply {
                text = String.format(java.util.Locale.US, "%.2fx", value)
                textSize = 14f
                setTextColor(callbacks.themeColor(android.R.attr.textColorSecondary))
            }
            labelRow.addView(valueView)
            row.addView(labelRow)
            val seek = SeekBar(config.context).apply {
                this.max = max
                this.progress = progress
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val v = if (labelRes == com.epubreader.app.R.string.tts_speed) ttsRateFor(p) else ttsPitchFor(p)
                        valueView.text = String.format(java.util.Locale.US, "%.2fx", v)
                        onChange(v)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {
                        onStop(sb?.progress ?: progress)
                    }
                })
            }
            row.addView(seek)
            return row
        }

        root.addView(sliderRow(
            com.epubreader.app.R.string.tts_speed, controller.speechRate, PrefsManager.TTS_SPEED_MAX,
            (((controller.speechRate - 0.5f) / 0.05f).roundToInt()).coerceIn(0, PrefsManager.TTS_SPEED_MAX),
            onChange = { v -> controller.speechRate = v; scheduleTtsSettingsSave() },
            onStop = { p -> state.prefs.ttsSpeedProgress = p; scheduleTtsSettingsSave() },
        ))
        root.addView(sliderRow(
            com.epubreader.app.R.string.tts_pitch, controller.pitch, PrefsManager.TTS_PITCH_MAX,
            (((controller.pitch - 0.5f) / 0.05f).roundToInt()).coerceIn(0, PrefsManager.TTS_PITCH_MAX),
            onChange = { v -> controller.pitch = v; scheduleTtsSettingsSave() },
            onStop = { p -> state.prefs.ttsPitchProgress = p; scheduleTtsSettingsSave() },
        ))

        val sleepButton = MaterialButton(config.context).apply {
            val remaining = controller.sleepTimerRemainingMs()
            text = if (remaining > 0L) callbacks.getString(com.epubreader.app.R.string.tts_sleep_remaining, (remaining / 60000L).toInt() + 1)
            else callbacks.getString(com.epubreader.app.R.string.tts_sleep_timer)
            setOnClickListener {
                showSleepTimerMenu()
                dialog.dismiss()
            }
        }
        root.addView(sleepButton)

        val backgroundSwitch = SwitchMaterial(config.context).apply {
            text = callbacks.getString(com.epubreader.app.R.string.tts_background_playback)
            isChecked = state.prefs.ttsBackgroundPlayback
            setOnCheckedChangeListener { _, checked ->
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    config.context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    callbacks.requestNotificationPermission()
                }
                state.prefs.ttsBackgroundPlayback = checked
                updateTtsServiceState()
            }
        }
        root.addView(backgroundSwitch)
        root.addView(TextView(config.context).apply {
            text = callbacks.getString(com.epubreader.app.R.string.tts_background_playback_summary)
            textSize = 12f
            setTextColor(callbacks.themeColor(android.R.attr.textColorSecondary))
            setPadding(0, 0, 0, sectionGap)
        })
        val voiceButton = MaterialButton(config.context).apply {
            text = callbacks.getString(com.epubreader.app.R.string.tts_voice)
            setOnClickListener { showVoicePicker() }
        }
        root.addView(voiceButton)
        dialog.setOnDismissListener { state.ttsSettingsSheet = null }
        dialog.setContentView(root)
        dialog.show()
    }

    /** Offline voices only (network-required voices are filtered out). */
    fun showVoicePicker() {
        val controller = state.ttsController ?: return
        val voices = controller.availableVoices()
        val labels = mutableListOf(callbacks.getString(com.epubreader.app.R.string.tts_voice_default))
        val values = mutableListOf<String?>(null)
        voices.forEach { voice ->
            labels += "${voice.locale.displayName} · ${voice.name}"
            values += voice.name
        }
        val checked = values.indexOf(controller.voiceName).coerceAtLeast(0)
        AlertDialog.Builder(config.context)
            .setTitle(com.epubreader.app.R.string.tts_voice)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                val wasPlaying = controller.state == ReaderTtsController.State.PLAYING
                controller.voiceName = values[which]
                scheduleTtsSettingsSave()
                // Patch v37 follow-up: if TTS is playing, pause it so the new
                // voice takes effect when the user presses play again. The
                // voice setter already applied the new voice to the engine;
                // pausing here ensures the current utterance stops.
                if (wasPlaying) {
                    controller.togglePauseResume()
                    updateTtsControlsUi(false)
                    callbacks.showSnackbar(com.epubreader.app.R.string.tts_voice_changed_pause, Snackbar.LENGTH_SHORT)
                }
                dialog.dismiss()
            }
            .setNegativeButton(com.epubreader.app.R.string.cancel, null)
            .show()
    }

    /** Loads per-book rate/pitch/voice from Room (falls back to app prefs).
     *  Patch v37: the sliders no longer live in the top bar (they moved into
     *  the settings sheet), so this only applies saved values to the controller;
     *  the sheet reads controller.speechRate / pitch each time it opens. */
    fun applyTtsSettings() {
        val controller = state.ttsController ?: return
        controller.bookLanguage = state.epub?.metadata?.language
        config.lifecycleScope.launch(Dispatchers.IO) {
            val saved = AppDatabase.get(config.applicationContext).ttsSettingsDao().getForBook(state.bookId)
            withContext(Dispatchers.Main) {
                if (state.isFinishing || state.isDestroyed) return@withContext
                if (saved != null) {
                    controller.speechRate = saved.speechRate
                    controller.pitch = saved.pitch
                    controller.voiceName = saved.voiceName
                } else {
                    controller.speechRate = ttsRateFor(state.prefs.ttsSpeedProgress)
                    controller.pitch = ttsPitchFor(state.prefs.ttsPitchProgress)
                    controller.voiceName = null
                }
            }
        }
    }

    /** Debounced persist of per-book TTS settings after slider/voice changes. */
    fun scheduleTtsSettingsSave() {
        config.handler.removeCallbacks(saveTtsSettingsRunnable)
        config.handler.postDelayed(saveTtsSettingsRunnable, TTS_SETTINGS_SAVE_DELAY_MS)
        state.ttsSettingsSavePending = true
    }

    fun saveTtsSettingsNow() {
        val controller = state.ttsController ?: return
        if (state.bookId < 0L) return
        val entity = TtsSettingsEntity(
            bookId = state.bookId,
            speechRate = controller.speechRate,
            pitch = controller.pitch,
            voiceName = controller.voiceName,
            updatedAt = System.currentTimeMillis(),
        )
        config.lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.get(config.applicationContext).ttsSettingsDao().upsert(entity)
        }
        state.ttsSettingsSavePending = false
    }

    /** Bimodal reading: tints the sentence being spoken and the exact word
     *  being read, auto-turning the page when the spoken word moves off-page.
     *
     *  Patch v37: the highlight is drawn with non-mutating overlay rectangles
     *  (absolutely-positioned divs in a fixed container) instead of wrapping the
     *  text in <span>s. surroundContents/extractContents mutate the EPUB content
     *  tree, which reflowed the page and shifted the layout every time a new
     *  word was spoken — the user explicitly asked for the content to stay put.
     *  Overlay rects are positioned over the text and never touch the DOM, so
     *  pagination, columns and reflow are all left exactly as the user sees
     *  them. */
    fun highlightSpokenWord(segment: ReaderTtsSegment, start: Int, end: Int) {
        if (state.isFinishing || state.isDestroyed) return
        val controller = state.ttsController ?: return
        if (controller.state != ReaderTtsController.State.PLAYING) return
        val color = ContextCompat.getColor(config.context, com.epubreader.app.R.color.tts_word_highlight)
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        val wordCss = String.format(java.util.Locale.US, "rgba(%d,%d,%d,0.55)", r, g, b)
        val sentenceCss = String.format(java.util.Locale.US, "rgba(%d,%d,%d,0.22)", r, g, b)
        val segmentJson = JSONObject.quote(segment.text)
        val blockIndex = segment.blockIndex
        val blockStart = segment.blockTextStart
        val blockEnd = segment.blockTextEnd
        val wordStart = start.coerceIn(0, segment.text.length)
        val wordEnd = end.coerceIn(wordStart, segment.text.length)
        state.ttsHighlightedRange = Pair(wordStart, wordEnd)
        config.webView.evaluateJavascript(
            """(function(){
                var spoken=$segmentJson,targetBlockIndex=$blockIndex,
                    sentenceStart=$blockStart,sentenceEnd=$blockEnd,
                    wordStart=$wordStart,wordEnd=$wordEnd,
                    wordColor='$wordCss',sentenceColor='$sentenceCss',doc=document,body=doc.body;
                if(!body)return;
                var c=doc.getElementById('livre-tts-hl');
                if(c){while(c.firstChild)c.removeChild(c.firstChild);}else{
                    c=doc.createElement('div');c.id='livre-tts-hl';
                    var cs=c.style;cs.position='fixed';cs.top='0';cs.left='0';
                    cs.width='100%';cs.height='100%';cs.pointerEvents='none';
                    cs.zIndex='2147483646';cs.overflow='hidden';body.appendChild(c);
                }
                var ignored={HEAD:1,SCRIPT:1,STYLE:1,NOSCRIPT:1,SVG:1,MATH:1};
                var blockTags={ADDRESS:1,ARTICLE:1,ASIDE:1,BLOCKQUOTE:1,DD:1,DIV:1,DL:1,DT:1,
                    FIGCAPTION:1,FIGURE:1,FOOTER:1,FORM:1,H1:1,H2:1,H3:1,H4:1,H5:1,H6:1,
                    HEADER:1,LI:1,MAIN:1,NAV:1,OL:1,P:1,PRE:1,SECTION:1,TABLE:1,TD:1,TH:1,TR:1,UL:1};
                function ignoredAncestor(el){while(el){if(ignored[el.tagName])return true;el=el.parentElement;}return false;}
                function hasBlockChild(el){
                    var kids=el.querySelectorAll('*');
                    for(var i=0;i<kids.length;i++)if(blockTags[kids[i].tagName]&&!ignoredAncestor(kids[i]))return true;
                    return false;
                }
                function normalizeWithMap(el){
                    var walker=doc.createTreeWalker(el,NodeFilter.SHOW_TEXT,null,false),
                        chars=[],map=[],n;
                    while(n=walker.nextNode()){
                        if(ignoredAncestor(n.parentElement))continue;
                        var t=n.textContent||'';
                        for(var i=0;i<t.length;i++){
                            var ch=t.charAt(i);
                            if(/\\s/.test(ch)){
                                if(chars.length&&chars[chars.length-1]!==' '){chars.push(' ');map.push({n:n,o:i});}
                            }else{chars.push(ch);map.push({n:n,o:i});}
                        }
                    }
                    while(chars.length&&chars[0]===' '){chars.shift();map.shift();}
                    while(chars.length&&chars[chars.length-1]===' '){chars.pop();map.pop();}
                    return {text:chars.join(''),map:map};
                }
                var blocks=[],all=body.querySelectorAll('*');
                for(var i=0;i<all.length;i++){
                    var el=all[i];
                    if(!blockTags[el.tagName]||ignoredAncestor(el)||hasBlockChild(el))continue;
                    var nm=normalizeWithMap(el);
                    if(nm.text)blocks.push({el:el,nm:nm});
                }
                var exact=[],spokenNorm=spoken.replace(/\\s+/g,' ').trim();
                for(var bi=0;bi<blocks.length;bi++){
                    if(blocks[bi].nm.text.indexOf(spokenNorm)>=0)exact.push(bi);
                }
                var chosen=-1;
                if(exact.length===1)chosen=exact[0];
                else if(exact.length>1){
                    // Prefer the structural block index when it is a valid exact match.
                    if(exact.indexOf(targetBlockIndex)>=0)chosen=targetBlockIndex;
                    else chosen=exact[0];
                }else if(blocks[targetBlockIndex]){
                    chosen=targetBlockIndex;
                }
                if(chosen<0||!blocks[chosen])return;
                var data=blocks[chosen].nm, text=data.text, pos=text.indexOf(spokenNorm);
                if(pos<0)return;
                // TTS offsets are relative to the normalized owning block. Clamp them
                // to the actual spoken sentence so whitespace normalization cannot
                // produce an invalid DOM Range.
                var sentenceAbsStart=Math.max(0,pos+sentenceStart);
                var sentenceAbsEnd=Math.min(text.length,pos+sentenceEnd);
                var wordAbsStart=Math.max(sentenceAbsStart,Math.min(sentenceAbsEnd,pos+wordStart));
                var wordAbsEnd=Math.max(wordAbsStart,Math.min(sentenceAbsEnd,pos+wordEnd));
                function point(at,endPoint){
                    if(!data.map.length)return null;
                    if(at>=data.map.length){var last=data.map[data.map.length-1];return [last.n,(last.n.textContent||'').length];}
                    var p=data.map[Math.max(0,at)];return [p.n,endPoint?Math.min((p.n.textContent||'').length,p.o+1):p.o];
                }
                function range(a,b){if(!a||!b)return null;try{var q=doc.createRange();q.setStart(a[0],a[1]);q.setEnd(b[0],b[1]);return q;}catch(e){return null;}}
                function draw(rng,color){
                    if(!rng)return null;var rects=rng.getClientRects(),last=null;
                    for(var i=0;i<rects.length;i++){var z=rects[i];if(z.width<=0||z.height<=0)continue;
                        var d=doc.createElement('div'),s=d.style;s.position='fixed';s.left=z.left+'px';s.top=z.top+'px';
                        s.width=z.width+'px';s.height=z.height+'px';s.backgroundColor=color;s.borderRadius='2px';c.appendChild(d);last=z;}
                    return last;
                }
                draw(range(point(sentenceAbsStart,false),point(sentenceAbsEnd,true)),sentenceColor);
                if(wordAbsStart>=wordAbsEnd)return;
                var rect=draw(range(point(wordAbsStart,false),point(wordAbsEnd,true)),wordColor);
                if(rect&&window.Caesura){if(rect.right>window.innerWidth-4)window.Caesura.nextPage(false);else if(rect.left<4)window.Caesura.prevPage(false);}
            })();""",
            null,
        )
    }

    fun clearSpokenWordHighlight() {
        if (state.isFinishing || state.isDestroyed) return
        state.ttsHighlightedRange = null
        // Patch v37: removes the non-mutating overlay container created in
        // highlightSpokenWord. No EPUB content was wrapped, so there is nothing
        // to unwrap — just drop the overlay divs.
        config.webView.evaluateJavascript(
            "(function(){var c=document.getElementById('livre-tts-hl');if(c&&c.parentNode)c.parentNode.removeChild(c);})();",
            null,
        )
    }

    // ---- Transport wiring ----

    /** Wires the TTS overlay transport buttons. Call from the Activity's
     *  chrome setup after the controller is created. */
    fun setupTransport() {
        config.btnTtsPlayPause.setOnClickListener { state.ttsController?.togglePauseResume() }
        config.btnTtsPrev.setOnClickListener {
            state.ttsController?.skipSentence(forward = false)
        }
        config.btnTtsNext.setOnClickListener {
            state.ttsController?.skipSentence(forward = true)
        }
        config.btnTtsStop.setOnClickListener { stopTtsCompletely() }
        config.btnTtsSettings.setOnClickListener { showTtsSettingsSheet() }
        config.btnTtsClose.setOnClickListener { hideTtsOverlay() }
    }

    /** Seeds the engine rate/pitch from app prefs as a fallback before the
     *  per-book Room settings load (applyTtsSettings). */
    fun seedEngineDefaults() {
        state.ttsController?.speechRate = ttsRateFor(state.prefs.ttsSpeedProgress)
        state.ttsController?.pitch = ttsPitchFor(state.prefs.ttsPitchProgress)
    }

    /** Called when the speaker button is tapped. Opens the overlay and starts
     *  playback if idle, or just brings the overlay back if already running. */
    fun onReadAloudClicked() {
        val ttsState = state.ttsController?.state
        if (ttsState == ReaderTtsController.State.PLAYING || ttsState == ReaderTtsController.State.PAUSED) {
            // Already running: bring the overlay back (it may have been
            // dismissed) rather than toggling pause from the top bar.
            showTtsOverlay()
        } else {
            showTtsOverlay()
            startTtsForCurrentChapter()
        }
    }

    /** Cancels any pending debounced save and stops the TTS service. Call from
     *  the Activity's onDestroy. */
    fun cleanup() {
        config.handler.removeCallbacks(saveTtsSettingsRunnable)
        state.ttsSettingsSavePending = false
    }
}
