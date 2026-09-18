package com.epubreader.app

import com.epubreader.app.util.SystemBarController

import android.annotation.SuppressLint
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ActionMode
import android.view.MenuItem
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowManager
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.PopupWindow
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.math.roundToInt
import android.widget.SeekBar
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookmarkEntity
import com.epubreader.app.data.DictionaryHistoryEntity
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.data.TtsSettingsEntity
import com.epubreader.app.epub.ReaderSelectionLocator
import com.epubreader.app.databinding.ActivityReaderBinding
import com.epubreader.app.epub.DictionaryLookup
import com.epubreader.app.epub.EpubBook
import com.epubreader.app.epub.EpubResourceResolver
import com.epubreader.app.epub.ReaderPageMapping
import com.epubreader.app.epub.ReaderTtsController
import com.epubreader.app.epub.ReaderTtsSegment
import com.epubreader.app.features.annotation.BookmarkService
import com.epubreader.app.features.annotation.HighlightService
import com.epubreader.app.features.define.DefinitionService
import com.epubreader.app.features.search.ReaderSearchService
import com.epubreader.app.features.tts.ReaderTtsCoordinator
import com.epubreader.app.tts.ReaderTtsService
import com.epubreader.app.ui.BookmarkAdapter
import com.epubreader.app.ui.HighlightListAdapter
import com.epubreader.app.ui.ReaderSettingsActivity
import com.epubreader.app.ui.ReaderTheme
import com.epubreader.app.ui.reader.ReaderCssBuilder
import com.epubreader.app.ui.reader.ReaderChromeController
import com.epubreader.app.ui.reader.ReaderLocation
import com.epubreader.app.ui.reader.ReaderNavigationController
import com.epubreader.app.ui.reader.ReaderOverlayController
import com.epubreader.app.ui.reader.ReaderSelectionController
import com.epubreader.app.ui.reader.ReaderTtsUiController
import com.epubreader.app.ui.reader.ReaderPaginationScriptBuilder
import com.epubreader.app.ui.reader.ReaderProgressController
import com.epubreader.app.ui.reader.ReaderWebViewController
import com.epubreader.app.ui.SearchResultAdapter
import com.epubreader.app.ui.TocAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReaderActivity : AppCompatActivity() {


    private lateinit var binding: ActivityReaderBinding
    private lateinit var prefs: PrefsManager
    private var definitionPopup: PopupWindow? = null
    /** Active text-selection ActionMode (the floating Copy/Translate/… toolbar).
     *  Held so it can be dismissed when the user navigates away or taps the
     *  page. Patch v37. */
    private var currentSelectionActionMode: ActionMode? = null
    private var selectionToolbarPopup: PopupWindow? = null
    private var currentReaderSelection: ReaderSelectionLocator? = null
    /** Time-based guard: a tap that should NOT turn the page or toggle chrome
     *  (e.g. it landed on a highlight, or dismissed a text selection) sets this
     *  so the GestureDetector's onSingleTapConfirmed is ignored. Patch v37. */
    private var suppressReaderTapUntilMs = 0L
    /** Tracks an in-progress tap that is dismissing an active text selection.
     *  The entire gesture (DOWN → MOVE → UP) is consumed so it never reaches the
     *  GestureDetector. Patch v37. */
    private var consumingSelectionDismissTap = false
    // Phase 7 feature services: own annotation/dictionary/TTS feature logic
    // so the activity only wires screens together.
    private val bookmarkService by lazy { BookmarkService(db.bookmarkDao()) }
    private val highlightService by lazy { HighlightService(db.highlightDao()) }
    private val searchService by lazy { ReaderSearchService() }
    private val definitionService by lazy {
        DefinitionService(
            historyDao = db.dictionaryHistoryDao(),
            lookupFactory = { lang -> DictionaryLookup(applicationContext, lang) },
        )
    }

    /** Read-aloud coordinator (Phase 7): owns the engine + service lifecycle. */
    private lateinit var ttsCoordinator: ReaderTtsCoordinator

    /** The live engine, or null before/after the coordinator's lifetime —
     *  kept as a computed property so every existing call site keeps working. */
    private val ttsController: ReaderTtsController?
        get() = if (::ttsCoordinator.isInitialized) ttsCoordinator.controller else null
    private var readingSessionStartedAt: Long? = null
    private var readingSessionLastInteractionAt: Long = 0L

    /** Patch v37: POST_NOTIFICATIONS request for the read-aloud media notification. */
    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                updateTtsServiceState()
            }
        }

    /** Debounced writer for per-book TTS settings. */
    private val saveTtsSettingsRunnable = Runnable { saveTtsSettingsNow() }
    private var readingSessionActiveSeconds: Int = 0
    private var readingSessionStartSpine: Int = 0
    private var readingSessionStartPage: Int = 0

    // Patch 11 "Screen On" controller — keeps the screen awake for 10 minutes
    // beyond the system timeout while the reader is in the foreground.
    private lateinit var keepScreenOnController: com.epubreader.app.util.KeepScreenOnController
    private lateinit var db: AppDatabase
    private lateinit var webViewController: ReaderWebViewController
    private lateinit var progressController: ReaderProgressController
    private lateinit var chromeController: ReaderChromeController
    private lateinit var navigationController: ReaderNavigationController
    private lateinit var overlayController: ReaderOverlayController
    private lateinit var selectionController: ReaderSelectionController
    private lateinit var ttsUiController: ReaderTtsUiController
    private lateinit var renderer: com.epubreader.app.ui.reader.renderer.EpubWebViewRenderer

    private var bookId: Long = -1L
    private var bookEntity: BookEntity? = null
    private var epub: EpubBook? = null
    private var resolver: EpubResourceResolver? = null
    private var spineIndex: Int = 0
    private var currentScrollRatio: Float = 0f
    private var currentPageInChapter: Int = 0
    private var pagesInChapter: Int = 1
    private var pendingFragment: String? = null
    private var chromeVisible: Boolean = false
    private var ttsOverlayRestoresChrome: Boolean = false
    private var ttsOverlayVisible: Boolean = false
    private var ttsSettingsSheet: Any? = null
    private var ttsSleepTimer: Int? = null
    private var ttsSettingsSavePending: Boolean = false
    private var ttsHighlightedRange: Pair<Int, Int>? = null
    private var restoreRatio: Float? = null
    /** Exact in-chapter page to restore after a cross-spine page seek. This uses
     *  the same Caesura page index that the visible reader already uses. */
    private var pendingTargetPageInChapter: Int? = null

    /** spineIndex -> section label, derived from the embedded nav TOC (not toc.xhtml). */
    private var tocSectionMap: Map<Int, String> = emptyMap()

    /** ordered (spineIndex,label) sections for nearest-previous fallback. */
    private var tocSections: List<Pair<Int, String>> = emptyList()

    /** Per-chapter page counts measured by the measurement WebView (-1 = not measured yet). */
    private var chapterPageCounts: IntArray? = null

    /** Once every chapter's page count is measured, the bottom timeline switches
     *  from chapter-level (max = spine.size-1) to per-page (max = total pages-1),
     *  so dragging the seeker lands on the exact page instead of the chapter's
     *  first page. */
    private var perPageSeekerActive: Boolean = false

    private var userSeeking = false
    private var pendingSeekProgress: Int? = null
    /** Temporary reader navigation history. Only explicit navigation events are recorded;
     * normal page turns and layout restores are intentionally not. */
    private val backHistory = ArrayDeque<ReaderLocation>()
    private val forwardHistory = ArrayDeque<ReaderLocation>()
    private var restoringHistoryLocation = false
    /** Location associated with the current history cursor. Normal page turns never update this. */
    private var historyCursorLocation: ReaderLocation? = null
    /** Set only by explicit navigation that will resolve its final page after a chapter load. */
    private var pendingHistoryCursorAfterRestore = false

    /** Temporary semantic anchor used only while reader settings reflow the current chapter. */
    private data class ReflowAnchor(
        val text: String,
        val fallbackPage: Int,
    )

    private var pendingReflowAnchor: ReflowAnchor? = null
    /** Semantic bookmark anchor used when navigating bookmarks after reflow. */
    private var pendingBookmarkAnchor: String? = null
    private var pendingBookmarkFallbackPage: Int = 0
    private var pendingBookmarkIsWholePage: Boolean = false
    private var manualSeekTouch = false
    private var manualSeekFinished = false
    /** Exact page requested by the user. A stale WebView poll must not overwrite this
     *  location while the visible WebView is applying gotoPage(). */
    private var pendingExactSeekLocation: ReaderLocation? = null
    private var exactSeekUiLocation: ReaderLocation? = null


    @Volatile
    private var measuring: Boolean = false

    private var measuringIndex: Int = -1
    private var measuringLoaded: Int = -1

    @Volatile
    private var measureCancelled: Boolean = false

    private var measureGeneration: Int = 0
    private var activeMeasureGeneration: Int = 0
    private var expectedMeasureUrl: String? = null

    private var readerGeneration: Int = 0
    private var activeReaderGeneration: Int = 0

    private val restartMeasurementRunnable = Runnable {
        startMeasurement()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val progressPoller = object : Runnable {
        override fun run() {
            pollProgress(); handler.postDelayed(this, 1500)
        }
    }
    private val measureWatchdog = object : Runnable {
        override fun run() {
            if (!measuring || measureCancelled) return

            val index = measuringIndex
            val book = epub ?: return

            if (index !in book.spine.indices) {
                measuring = false
                expectedMeasureUrl = null
                updatePageIndicator()
                return
            }

            if (measuringLoaded != index) return

            chapterPageCounts?.let { counts ->
                if (index in counts.indices) {
                    counts[index] = 1
                }
            }

            measuringIndex = index + 1

            if (measuringIndex >= book.spine.size) {
                measuring = false
                expectedMeasureUrl = null
                updatePageIndicator()
            } else {
                loadForMeasurement(measuringIndex)
            }
        }
    }

    private val alphaFallback = object : Runnable {
        override fun run() {
            if (binding.webView.alpha == 0f) binding.webView.alpha = 1f
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PrefsManager(applicationContext)
        keepScreenOnController = com.epubreader.app.util.KeepScreenOnController(this, prefs)
        db = AppDatabase.get(applicationContext)
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Reader chrome is always dark, regardless of the app theme.
        SystemBarController.apply(this, forceDark = true)
        applyWindowTheme()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId < 0) {
            finish(); return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            db.bookDao().markOpened(bookId, System.currentTimeMillis())
        }

        setupWebView()
        setupMeasureWebView()
        setupProgressController()
        setupChromeController()
        setupNavigationController()
        setupOverlayController()
        setupSelectionController()
        setupTtsUiController()
        setupRenderer()
        ttsCoordinator = ReaderTtsCoordinator(applicationContext, object : ReaderTtsCoordinator.Listener {
            override fun onPlayingChanged(playing: Boolean) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    updateTtsControlsUi(playing)
                    updateTtsServiceState()
                }
            }

            override fun onChapterFinished() {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val next = epub?.let { it.spine.getOrNull(spineIndex + 1) }
                    if (next != null) {
                        goToSpine(spineIndex + 1)
                        handler.postDelayed({ startTtsForCurrentChapter() }, 450)
                    } else {
                        binding.tvTtsStatus.text = getString(R.string.action_read_aloud)
                    }
                }
            }

            override fun onWordRange(segment: ReaderTtsSegment, start: Int, end: Int) {
                // Word-level range callback. The controller reports offsets against
                // the full structural segment even when it is resuming a suffix.
                runOnUiThread { highlightSpokenWord(segment, start, end) }
            }

            override fun onSleepTick(remainingMs: Long) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    binding.tvTtsStatus.text = getString(R.string.tts_sleep_remaining, (remainingMs / 60000L).toInt() + 1)
                }
            }

            override fun onSleepFinished() {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Snackbar.make(binding.root, R.string.tts_sleep_finished, Snackbar.LENGTH_SHORT).show()
                }
            }

            override fun onSentenceHighlight(segment: ReaderTtsSegment) {
                // Sentence-level highlight is anchored to the structural TTS
                // segment, not found by searching the whole chapter for a string.
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    highlightSpokenWord(segment, 0, segment.text.length)
                }
            }
        })
        ttsCoordinator.start()
        setupChrome()
        setupOverlays()
        loadBook()
    }

    // ---------------------------------------------------------------- theme
    private fun applyWindowTheme() {
        val (bg, _) = readerColors()
        // The status bar and navigation bar are always solid black (per the
        // user's request), regardless of the reading theme. The window decor
        // and the root container are painted black so the area BEHIND the
        // system bars is black too; only the WebView (the book page) and the
        // static chrome bars use the reading/chrome colors.
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.setBackgroundColor(Color.BLACK)
        binding.root.setBackgroundColor(Color.BLACK)
        binding.webView.setBackgroundColor(bg)
        binding.measureWebView.setBackgroundColor(bg)
        binding.topBar.setBackgroundColor(readerSurface())
        binding.bottomBar.setBackgroundColor(readerSurface())
        binding.tocBookmarkOverlay.setBackgroundColor(readerSurface())
        binding.searchOverlay.setBackgroundColor(readerSurface())
        // Page indicator: no background pill — ink-colored text (contrasts with
        // the page) lightened by the view alpha so it blends like Kindle/ReadEra.
        binding.tvPageIndicator.setTextColor(inkColor())
    }

    /** Content-area colors (the EPUB page itself). Only this changes with the
     *  reading theme. Patch 17 (Addition #1): colors now come from the
     *  single-source-of-truth [ReaderTheme] registry (ReaderThemes.kt) —
     *  edit a theme's bg/ink there and it propagates here automatically. */
    private fun readerColors(): Pair<Int, String> {
        val t = ReaderTheme.byId(prefs.theme)
        return t.bgColor to t.inkHex
    }

    /** Reader chrome background — STATIC (eggplant) for every reading theme and
     *  for app day/night. Read from the color resource so palette changes in
     *  colors.xml propagate here (single source of truth). */
    private fun readerSurface(): Int = getColor(R.color.reader_chrome_bg)

    private fun inkColor(): Int = Color.parseColor(readerColors().second)

    private fun bottomGuardPx(): Int = if (prefs.pageBottomMargin) 56 else 0

    /** Patch 12: top reading margin — the vertical breathing room reserved ABOVE
     *  the page content so the first line of a chapter never sits flush against the
     *  top status bar. It mirrors the bottom guard so the two spaces are equal and
     *  symmetric; both are controlled by the single "Top & bottom margin" reader
     *  setting so they stay in sync. The reserved space is painted with the reading
     *  background color (white / sepia / black), NOT reader content. */
    private fun topGuardPx(): Int = if (prefs.pageBottomMargin) 56 else 0

    // ---------------------------------------------------------------- webview
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webViewController = ReaderWebViewController(
            webView = binding.webView,
            resolverProvider = { resolver },
            onNavigateToUrl = { url -> navigateToUrl(url) },
            onPageFinished = { onReaderPageFinished() },
            onHighlightTap = { id ->
                suppressReaderTap()
                runOnUiThread { showHighlightNoteSheet(id) }
            },
            getCurrentSpineHref = { epub?.spine?.getOrNull(spineIndex)?.href },
        )
        webViewController.attach()
    }

    /**
     * Called by [ReaderWebViewController] after Chromium finishes loading a
     * chapter and Caesura pagination is applied. The Activity orchestrates
     * cross-controller concerns: progress polling, fragment restore, and
     * highlight injection.
     */
    private fun onReaderPageFinished() {
        val generation = activeReaderGeneration

        handler.removeCallbacks(progressPoller)
        handler.post(progressPoller)

        handler.removeCallbacks(alphaFallback)

        handler.postDelayed({
            if (generation == activeReaderGeneration) {
                applyPendingFragmentOrRestore()
                // Inject existing highlights after the chapter content
                // is loaded and Caesura pagination is applied.
                injectHighlightsForChapter()
            }
        }, 140L)

        handler.postDelayed(alphaFallback, 1500L)
    }


    private fun setupChromeController() {
        chromeController = ReaderChromeController(
            config = ReaderChromeController.Config(
                topBar = binding.topBar,
                bottomBar = binding.bottomBar,
                tvPageIndicator = binding.tvPageIndicator,
                seekChapter = binding.seekChapter,
                window = window,
                keepScreenOn = prefs.keepScreenOn,
            ),
            state = object : ReaderChromeController.State {
                override var chromeVisible: Boolean
                    get() = this@ReaderActivity.chromeVisible
                    set(value) { this@ReaderActivity.chromeVisible = value }
                override var userSeeking: Boolean
                    get() = this@ReaderActivity.userSeeking
                    set(value) { this@ReaderActivity.userSeeking = value }
                override var pendingSeekProgress: Int?
                    get() = this@ReaderActivity.pendingSeekProgress
                    set(value) { this@ReaderActivity.pendingSeekProgress = value }
                override var manualSeekTouch: Boolean
                    get() = this@ReaderActivity.manualSeekTouch
                    set(value) { this@ReaderActivity.manualSeekTouch = value }
                override var manualSeekFinished: Boolean
                    get() = this@ReaderActivity.manualSeekFinished
                    set(value) { this@ReaderActivity.manualSeekFinished = value }
                override val overlayVisible: Boolean get() = this@ReaderActivity.overlayVisible()
                override val perPageSeekerActive: Boolean get() = this@ReaderActivity.perPageSeekerActive
                override val spineIndex: Int get() = this@ReaderActivity.spineIndex
                override val restoringHistoryLocation: Boolean get() = this@ReaderActivity.restoringHistoryLocation
            },
            callbacks = object : ReaderChromeController.Callbacks {
                override fun onPollProgress() = pollProgress()
                override fun onInvalidatePendingPolls() = progressController.invalidatePendingPolls()
                override fun onUpdatePageIndicator() = updatePageIndicator()
                override fun onUpdateHistoryUi() = updateHistoryUi()
                override fun onCaptureReaderLocation() = captureReaderLocation()
                override fun onLocationForAbsolutePage(absolute: Int) = locationForAbsolutePage(absolute)
                override fun onSameLocation(a: ReaderLocation, b: ReaderLocation) = sameLocation(a, b)
                override fun onPushHistory(location: ReaderLocation) = pushHistory(location)
                override fun onSeekToAbsolutePage(absolute: Int) = seekToAbsolutePage(absolute)
                override fun onGoToSpine(spine: Int) = goToSpine(spine)
            },
        )
    }




    private fun setupSelectionController() {
        selectionController = ReaderSelectionController(
            config = ReaderSelectionController.Config(
                webView = binding.webView,
                rootView = binding.root,
                layoutInflater = layoutInflater,
                handler = handler,
                applicationContext = applicationContext,
                definitionService = definitionService,
            ),
            state = object : ReaderSelectionController.State {
                override val epub: EpubBook? get() = this@ReaderActivity.epub
                override val isFinishing: Boolean get() = this@ReaderActivity.isFinishing
                override val isDestroyed: Boolean get() = this@ReaderActivity.isDestroyed
                override val bookId: Long get() = this@ReaderActivity.bookId
                override var currentSelectionActionMode: ActionMode?
                    get() = this@ReaderActivity.currentSelectionActionMode
                    set(value) { this@ReaderActivity.currentSelectionActionMode = value }
                override var selectionToolbarPopup: PopupWindow?
                    get() = this@ReaderActivity.selectionToolbarPopup
                    set(value) { this@ReaderActivity.selectionToolbarPopup = value }
                override var currentReaderSelection: ReaderSelectionLocator?
                    get() = this@ReaderActivity.currentReaderSelection
                    set(value) { this@ReaderActivity.currentReaderSelection = value }
                override var consumingSelectionDismissTap: Boolean
                    get() = this@ReaderActivity.consumingSelectionDismissTap
                    set(value) { this@ReaderActivity.consumingSelectionDismissTap = value }
                override var suppressReaderTapUntilMs: Long
                    get() = this@ReaderActivity.suppressReaderTapUntilMs
                    set(value) { this@ReaderActivity.suppressReaderTapUntilMs = value }
                override var definitionPopup: PopupWindow?
                    get() = this@ReaderActivity.definitionPopup
                    set(value) { this@ReaderActivity.definitionPopup = value }
            },
            callbacks = object : ReaderSelectionController.Callbacks {
                override fun captureCurrentSelection(onCaptured: ((ReaderSelectionLocator?) -> Unit)?) =
                    this@ReaderActivity.captureCurrentSelection(onCaptured)
                override fun getString(resId: Int, vararg args: Any) = this@ReaderActivity.getString(resId, *args)
                override fun startActivity(intent: Intent) = this@ReaderActivity.startActivity(intent)
                override fun resolveActivity(intent: Intent): Boolean = this@ReaderActivity.packageManager.resolveActivity(intent, 0) != null
                override fun addBookmarkFromSelection(selection: ReaderSelectionLocator) =
                    this@ReaderActivity.addBookmarkFromSelection(selection)
                override fun showHighlightColorPicker(selection: ReaderSelectionLocator?) =
                    this@ReaderActivity.showHighlightColorPicker(selection)
                override fun themeColor(attr: Int) = this@ReaderActivity.themeColor(attr)
                override fun launchIo(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
                    lifecycleScope.launch(Dispatchers.IO, block = block)
                }
            },
        )
    }

    private fun setupTtsUiController() {
        ttsUiController = ReaderTtsUiController(
            config = ReaderTtsUiController.Config(
                ttsOverlay = binding.ttsOverlay,
                btnTtsPlayPause = binding.btnTtsPlayPause,
                btnTtsPrev = binding.btnTtsPrev,
                btnTtsNext = binding.btnTtsNext,
                btnTtsStop = binding.btnTtsStop,
                btnTtsSettings = binding.btnTtsSettings,
                btnTtsClose = binding.btnTtsClose,
                tvTtsStatus = binding.tvTtsStatus,
                tvTtsBookTitle = binding.tvTtsBookTitle,
                tvTtsSection = binding.tvTtsSection,
                topBar = binding.topBar,
                bottomBar = binding.bottomBar,
                tvPageIndicator = binding.tvPageIndicator,
                webView = binding.webView,
                rootView = binding.root,
                handler = handler,
                context = this,
                applicationContext = applicationContext,
                lifecycleScope = lifecycleScope,
            ),
            state = object : ReaderTtsUiController.State {
                override val ttsController: ReaderTtsController? get() = this@ReaderActivity.ttsController
                override val epub: EpubBook? get() = this@ReaderActivity.epub
                override val spineIndex: Int get() = this@ReaderActivity.spineIndex
                override val bookEntity: BookEntity? get() = this@ReaderActivity.bookEntity
                override val bookId: Long get() = this@ReaderActivity.bookId
                override val prefs: PrefsManager get() = this@ReaderActivity.prefs
                override val isFinishing: Boolean get() = this@ReaderActivity.isFinishing
                override val isDestroyed: Boolean get() = this@ReaderActivity.isDestroyed
                override var ttsOverlayVisible: Boolean
                    get() = this@ReaderActivity.ttsOverlayVisible
                    set(value) { this@ReaderActivity.ttsOverlayVisible = value }
                override var ttsOverlayRestoresChrome: Boolean
                    get() = this@ReaderActivity.ttsOverlayRestoresChrome
                    set(value) { this@ReaderActivity.ttsOverlayRestoresChrome = value }
                override var ttsSettingsSheet: Any?
                    get() = this@ReaderActivity.ttsSettingsSheet
                    set(value) { this@ReaderActivity.ttsSettingsSheet = value }
                override var ttsSleepTimer: Int?
                    get() = this@ReaderActivity.ttsSleepTimer
                    set(value) { this@ReaderActivity.ttsSleepTimer = value }
                override var ttsSettingsSavePending: Boolean
                    get() = this@ReaderActivity.ttsSettingsSavePending
                    set(value) { this@ReaderActivity.ttsSettingsSavePending = value }
                override var ttsHighlightedRange: Pair<Int, Int>?
                    get() = this@ReaderActivity.ttsHighlightedRange
                    set(value) { this@ReaderActivity.ttsHighlightedRange = value }
                override var chromeVisible: Boolean
                    get() = this@ReaderActivity.chromeVisible
                    set(value) { this@ReaderActivity.chromeVisible = value }
            },
            callbacks = object : ReaderTtsUiController.Callbacks {
                override fun getString(resId: Int, vararg args: Any) = this@ReaderActivity.getString(resId, *args)
                override fun clearReaderSelection() = this@ReaderActivity.clearReaderSelection()
                override fun updateHistoryUi() = this@ReaderActivity.updateHistoryUi()
                override fun updatePageIndicator() = this@ReaderActivity.updatePageIndicator()
                override fun sectionLabel() = this@ReaderActivity.sectionLabel()
                override fun overlayVisible() = this@ReaderActivity.overlayVisible()
                override fun themeColor(attr: Int) = this@ReaderActivity.themeColor(attr)
                override fun showSnackbar(resId: Int, duration: Int) {
                    Snackbar.make(binding.root, resId, duration).show()
                }
                override fun showSnackbarMessage(message: String, duration: Int) {
                    Snackbar.make(binding.root, message, duration).show()
                }
                override fun requestNotificationPermission() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
                    }
                }
                override fun goToSpine(spineIndex: Int) = this@ReaderActivity.goToSpine(spineIndex)
                override fun runOnUiThread(action: () -> Unit) = this@ReaderActivity.runOnUiThread(action)
            },
        )
    }

    private fun setupOverlayController() {
        overlayController = ReaderOverlayController(
            config = ReaderOverlayController.Config(
                activity = this,
                webView = binding.webView,
                tocBookmarkOverlay = binding.tocBookmarkOverlay,
                searchOverlay = binding.searchOverlay,
                overlayContent = binding.overlayContent,
                overlayTabGroup = binding.overlayTabGroup,
                searchContent = binding.searchContent,
                searchEdit = binding.searchEdit,
                tvPageIndicator = binding.tvPageIndicator,
                topBar = binding.topBar,
                bottomBar = binding.bottomBar,
                root = binding.root,
                handler = handler,
                readerHistory = binding.readerHistory,
                bookmarkService = bookmarkService,
                highlightService = highlightService,
                searchService = searchService,
            ),
            state = object : ReaderOverlayController.State {
                override val epub: EpubBook? get() = this@ReaderActivity.epub
                override val bookId: Long get() = this@ReaderActivity.bookId
                override val bookEntity: BookEntity? get() = this@ReaderActivity.bookEntity
                override val spineIndex: Int get() = this@ReaderActivity.spineIndex
                override val currentPageInChapter: Int get() = this@ReaderActivity.currentPageInChapter
                override val currentScrollRatio: Float get() = this@ReaderActivity.currentScrollRatio
                override val pagesInChapter: Int get() = this@ReaderActivity.pagesInChapter
                override val restoringHistoryLocation: Boolean get() = this@ReaderActivity.restoringHistoryLocation
                override val chromeVisible: Boolean get() = this@ReaderActivity.chromeVisible
                override val overlayVisible: Boolean get() = this@ReaderActivity.overlayVisible()
                override val backHistory: ArrayDeque<ReaderLocation> get() = this@ReaderActivity.backHistory
                override val forwardHistory: ArrayDeque<ReaderLocation> get() = this@ReaderActivity.forwardHistory
                override val historyCursorLocation: ReaderLocation? get() = this@ReaderActivity.historyCursorLocation

                override var bookmarksTabActive: Boolean
                    get() = this@ReaderActivity.bookmarksTabActive
                    set(value) { this@ReaderActivity.bookmarksTabActive = value }
                override var activeOverlayTab: Int
                    get() = this@ReaderActivity.activeOverlayTab
                    set(value) { this@ReaderActivity.activeOverlayTab = value }
                override var tocRv: RecyclerView?
                    get() = this@ReaderActivity.tocRv
                    set(value) { this@ReaderActivity.tocRv = value }
                override var tocEmpty: TextView?
                    get() = this@ReaderActivity.tocEmpty
                    set(value) { this@ReaderActivity.tocEmpty = value }
                override var bookmarkRv: RecyclerView?
                    get() = this@ReaderActivity.bookmarkRv
                    set(value) { this@ReaderActivity.bookmarkRv = value }
                override var bookmarkEmpty: TextView?
                    get() = this@ReaderActivity.bookmarkEmpty
                    set(value) { this@ReaderActivity.bookmarkEmpty = value }
                override var bookmarkAdapter: BookmarkAdapter?
                    get() = this@ReaderActivity.bookmarkAdapter
                    set(value) { this@ReaderActivity.bookmarkAdapter = value }
                override var bookmarkObserverStarted: Boolean
                    get() = this@ReaderActivity.bookmarkObserverStarted
                    set(value) { this@ReaderActivity.bookmarkObserverStarted = value }
                override var highlightRv: RecyclerView?
                    get() = this@ReaderActivity.highlightRv
                    set(value) { this@ReaderActivity.highlightRv = value }
                override var highlightEmpty: TextView?
                    get() = this@ReaderActivity.highlightEmpty
                    set(value) { this@ReaderActivity.highlightEmpty = value }
                override var highlightObserverStarted: Boolean
                    get() = this@ReaderActivity.highlightObserverStarted
                    set(value) { this@ReaderActivity.highlightObserverStarted = value }
                override var searchRv: RecyclerView?
                    get() = this@ReaderActivity.searchRv
                    set(value) { this@ReaderActivity.searchRv = value }
                override var searchEmpty: TextView?
                    get() = this@ReaderActivity.searchEmpty
                    set(value) { this@ReaderActivity.searchEmpty = value }
                override var searchAdapter: SearchResultAdapter?
                    get() = this@ReaderActivity.searchAdapter
                    set(value) { this@ReaderActivity.searchAdapter = value }
                override var searchEdit: EditText?
                    get() = this@ReaderActivity.searchEdit
                    set(value) { this@ReaderActivity.searchEdit = value }
                override var pendingBookmarkAnchor: String?
                    get() = this@ReaderActivity.pendingBookmarkAnchor
                    set(value) { this@ReaderActivity.pendingBookmarkAnchor = value }
                override var pendingBookmarkFallbackPage: Int
                    get() = this@ReaderActivity.pendingBookmarkFallbackPage
                    set(value) { this@ReaderActivity.pendingBookmarkFallbackPage = value }
                override var pendingBookmarkIsWholePage: Boolean
                    get() = this@ReaderActivity.pendingBookmarkIsWholePage
                    set(value) { this@ReaderActivity.pendingBookmarkIsWholePage = value }
                override var pendingHighlightId: Long?
                    get() = this@ReaderActivity.pendingHighlightId
                    set(value) { this@ReaderActivity.pendingHighlightId = value }
                override var pendingHighlightHistoryLocation: ReaderLocation?
                    get() = this@ReaderActivity.pendingHighlightHistoryLocation
                    set(value) { this@ReaderActivity.pendingHighlightHistoryLocation = value }
                override var pendingFragment: String?
                    get() = this@ReaderActivity.pendingFragment
                    set(value) { this@ReaderActivity.pendingFragment = value }
                override var pendingTargetPageInChapter: Int?
                    get() = this@ReaderActivity.pendingTargetPageInChapter
                    set(value) { this@ReaderActivity.pendingTargetPageInChapter = value }
                override var restoreRatio: Float?
                    get() = this@ReaderActivity.restoreRatio
                    set(value) { this@ReaderActivity.restoreRatio = value }
                override var pendingHistoryCursorAfterRestore: Boolean
                    get() = this@ReaderActivity.pendingHistoryCursorAfterRestore
                    set(value) { this@ReaderActivity.pendingHistoryCursorAfterRestore = value }
                override var historyCursorLocationVar: ReaderLocation?
                    get() = this@ReaderActivity.historyCursorLocation
                    set(value) { this@ReaderActivity.historyCursorLocation = value }
                override var restoringHistoryLocationVar: Boolean
                    get() = this@ReaderActivity.restoringHistoryLocation
                    set(value) { this@ReaderActivity.restoringHistoryLocation = value }
            },
            callbacks = object : ReaderOverlayController.Callbacks {
                override fun loadChapter(index: Int) = this@ReaderActivity.loadChapter(index)
                override fun navigateToUrl(url: String) = this@ReaderActivity.navigateToUrl(url)
                override fun clearReaderSelection() = this@ReaderActivity.clearReaderSelection()
                override fun hideTtsOverlay() = this@ReaderActivity.hideTtsOverlay()
                override fun toggleChrome() = this@ReaderActivity.toggleChrome()
                override fun updateHistoryUi() = this@ReaderActivity.updateHistoryUi()
                override fun updatePageIndicator() = this@ReaderActivity.updatePageIndicator()
                override fun captureCurrentSelection(onCaptured: ((ReaderSelectionLocator?) -> Unit)?) =
                    this@ReaderActivity.captureCurrentSelection(onCaptured)
                override fun captureReaderLocation() = this@ReaderActivity.captureReaderLocation()
                override fun pushHistory(location: ReaderLocation) = this@ReaderActivity.pushHistory(location)
                override fun capturePageSnapshot(forward: Boolean) = this@ReaderActivity.capturePageSnapshot(forward)
                override fun applyPendingFragmentOrRestore() = this@ReaderActivity.applyPendingFragmentOrRestore()
                override fun pollProgress() = this@ReaderActivity.pollProgress()
                override fun sectionLabel() = this@ReaderActivity.sectionLabel()
                override fun themeColor(attr: Int) = this@ReaderActivity.themeColor(attr)
                override fun getString(resId: Int, vararg args: Any) = this@ReaderActivity.getString(resId, *args)
                override val lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope get() = this@ReaderActivity.lifecycleScope
            },
        )
    }

    private fun setupNavigationController() {
        navigationController = ReaderNavigationController(
            config = ReaderNavigationController.Config(
                webView = binding.webView,
                seekChapter = binding.seekChapter,
                snapshotView = binding.snapshotView,
                readerHistory = binding.readerHistory,
                readerHistoryBack = binding.readerHistoryBack,
                readerHistoryForward = binding.readerHistoryForward,
                handler = handler,
                pageTurnDurationMs = PAGE_TURN_DURATION_MS,
            ),
            state = object : ReaderNavigationController.State {
                override val epub: EpubBook? get() = this@ReaderActivity.epub
                override val spineIndex: Int get() = this@ReaderActivity.spineIndex
                override val currentPageInChapter: Int get() = this@ReaderActivity.currentPageInChapter
                override val currentScrollRatio: Float get() = this@ReaderActivity.currentScrollRatio
                override val chapterPageCounts: IntArray? get() = this@ReaderActivity.chapterPageCounts
                override val perPageSeekerActive: Boolean get() = this@ReaderActivity.perPageSeekerActive
                override val chromeVisible: Boolean get() = this@ReaderActivity.chromeVisible
                override val overlayVisible: Boolean get() = this@ReaderActivity.overlayVisible()
                override val restoringHistoryLocation: Boolean get() = this@ReaderActivity.restoringHistoryLocation
                override val backHistory: ArrayDeque<ReaderLocation> get() = this@ReaderActivity.backHistory
                override val forwardHistory: ArrayDeque<ReaderLocation> get() = this@ReaderActivity.forwardHistory
                override val historyCursorLocation: ReaderLocation? get() = this@ReaderActivity.historyCursorLocation
                override val pendingFragment: String? get() = this@ReaderActivity.pendingFragment
                override val pendingTargetPageInChapter: Int? get() = this@ReaderActivity.pendingTargetPageInChapter
                override val restoreRatio: Float? get() = this@ReaderActivity.restoreRatio
                override val pendingHistoryCursorAfterRestore: Boolean get() = this@ReaderActivity.pendingHistoryCursorAfterRestore
                override val pageTurnThrottleUntil: Long get() = this@ReaderActivity.pageTurnThrottleUntil
                override val snapshotForward: Boolean get() = this@ReaderActivity.snapshotForward
                override val snapshotAnimToken: Int get() = this@ReaderActivity.snapshotAnimToken

                override var spineIndexVar: Int
                    get() = this@ReaderActivity.spineIndex
                    set(value) { this@ReaderActivity.spineIndex = value }
                override var currentPageInChapterVar: Int
                    get() = this@ReaderActivity.currentPageInChapter
                    set(value) { this@ReaderActivity.currentPageInChapter = value }
                override var currentScrollRatioVar: Float
                    get() = this@ReaderActivity.currentScrollRatio
                    set(value) { this@ReaderActivity.currentScrollRatio = value }
                override var restoreRatioVar: Float?
                    get() = this@ReaderActivity.restoreRatio
                    set(value) { this@ReaderActivity.restoreRatio = value }
                override var pendingFragmentVar: String?
                    get() = this@ReaderActivity.pendingFragment
                    set(value) { this@ReaderActivity.pendingFragment = value }
                override var pendingTargetPageInChapterVar: Int?
                    get() = this@ReaderActivity.pendingTargetPageInChapter
                    set(value) { this@ReaderActivity.pendingTargetPageInChapter = value }
                override var pendingHistoryCursorAfterRestoreVar: Boolean
                    get() = this@ReaderActivity.pendingHistoryCursorAfterRestore
                    set(value) { this@ReaderActivity.pendingHistoryCursorAfterRestore = value }
                override var historyCursorLocationVar: ReaderLocation?
                    get() = this@ReaderActivity.historyCursorLocation
                    set(value) { this@ReaderActivity.historyCursorLocation = value }
                override var restoringHistoryLocationVar: Boolean
                    get() = this@ReaderActivity.restoringHistoryLocation
                    set(value) { this@ReaderActivity.restoringHistoryLocation = value }
                override var perPageSeekerActiveVar: Boolean
                    get() = this@ReaderActivity.perPageSeekerActive
                    set(value) { this@ReaderActivity.perPageSeekerActive = value }
                override var pageTurnThrottleUntilVar: Long
                    get() = this@ReaderActivity.pageTurnThrottleUntil
                    set(value) { this@ReaderActivity.pageTurnThrottleUntil = value }
                override var snapshotForwardVar: Boolean
                    get() = this@ReaderActivity.snapshotForward
                    set(value) { this@ReaderActivity.snapshotForward = value }
                override var snapshotAnimTokenVar: Int
                    get() = this@ReaderActivity.snapshotAnimToken
                    set(value) { this@ReaderActivity.snapshotAnimToken = value }
            },
            callbacks = object : ReaderNavigationController.Callbacks {
                override fun loadChapter(index: Int) = this@ReaderActivity.loadChapter(index)
                override fun pollProgress() = this@ReaderActivity.pollProgress()
                override fun invalidatePendingPolls() = progressController.invalidatePendingPolls()
                override fun updatePageIndicator() = this@ReaderActivity.updatePageIndicator()
                override fun updateOverallProgress() = this@ReaderActivity.updateOverallProgress()
                override fun updateSectionPages() = this@ReaderActivity.updateSectionPages()
                override fun updateHistoryUi() = this@ReaderActivity.updateHistoryUi()
                override fun syncSeekBarFromCurrentPage() = this@ReaderActivity.syncSeekBarFromCurrentPage()
                override fun currentAbsoluteBookPage() = this@ReaderActivity.currentAbsoluteBookPage()
                override fun clearReaderSelection() = this@ReaderActivity.clearReaderSelection()
                override fun stopTtsCompletely() = this@ReaderActivity.stopTtsCompletely()
                override fun readerBackgroundColor() = this@ReaderActivity.readerColors().first
                override fun getString(resId: Int, vararg args: Any) = this@ReaderActivity.getString(resId, *args)
                override val historyPageLabel: (ReaderLocation) -> String = { this@ReaderActivity.historyPageLabel(it) }
            },
        )
    }

    private fun setupProgressController() {
        progressController = ReaderProgressController(
            webView = binding.webView,
            tvPercent = binding.tvPercent,
            tvPageIndicator = binding.tvPageIndicator,
            tvPageInfo = binding.tvPageInfo,
            tvSectionPages = binding.tvSectionPages,
            handler = handler,
            lifecycleScope = lifecycleScope,
            db = db,
            bookId = bookId,
            getString = { resId, args -> getString(resId, *args) },
            getSpineIndex = { spineIndex },
            getEpub = { epub },
            getCurrentPageInChapter = { currentPageInChapter },
            getPagesInChapter = { pagesInChapter },
            getCurrentScrollRatio = { currentScrollRatio },
            getChapterPageCounts = { chapterPageCounts },
            getUserSeeking = { userSeeking },
            getPendingExactSeekLocation = { pendingExactSeekLocation },
            getExactSeekUiLocation = { exactSeekUiLocation },
            getOverlayVisible = { overlayVisible() },
            getTocSections = { tocSections },
            getSectionLabel = { sectionLabel() },
            setCurrentPageInChapter = { currentPageInChapter = it },
            setPagesInChapter = { pagesInChapter = it },
            setCurrentScrollRatio = { currentScrollRatio = it },
            setChapterPageCountsSpine = { idx, count -> chapterPageCounts?.let { c -> if (idx in c.indices) c[idx] = count } },
            clearExactSeekLocation = { pendingExactSeekLocation = null; exactSeekUiLocation = null },
            setExactSeekUiLocation = { exactSeekUiLocation = it },
            onSyncSeekBar = { syncSeekBarFromCurrentPage() },
        )
    }

    private fun captureCurrentSelection(onCaptured: ((ReaderSelectionLocator?) -> Unit)? = null) {
        // Route through the renderer contract. The renderer captures the
        // selection via the WebView bridge and converts it to the canonical
        // ReaderTextSelection. For backward compatibility with existing
        // callers that expect ReaderSelectionLocator, we also pass the
        // raw locator back through the webViewController.
        if (::renderer.isInitialized) {
            renderer.captureSelection { selection ->
                // Convert back to locator for legacy callers
                val locator = selection?.let { sel ->
                    ReaderSelectionLocator(
                        text = sel.text,
                        spineHref = sel.spineHref,
                        startPath = sel.startPath,
                        startOffset = sel.startOffset,
                        endPath = sel.endPath,
                        endOffset = sel.endOffset,
                        prefix = sel.prefix,
                        suffix = sel.suffix,
                        rectLeft = sel.rectLeft,
                        rectTop = sel.rectTop,
                        rectRight = sel.rectRight,
                        rectBottom = sel.rectBottom,
                    )
                }
                onCaptured?.invoke(locator)
            }
        } else {
            webViewController.captureCurrentSelection(onCaptured)
        }
    }

    /**
     * Initialize the [EpubWebViewRenderer] — the thin adapter that implements
     * the [ReaderRenderer] contract. This gives the UI a contract-based seam
     * to the rendering engine, decoupling it from direct WebView manipulation.
     */
    private fun setupRenderer() {
        renderer = com.epubreader.app.ui.reader.renderer.EpubWebViewRenderer(
            webView = binding.webView,
            webViewController = webViewController,
            navigationController = navigationController,
            epubProvider = { epub },
            spineIndexProvider = { spineIndex },
            pageCountsProvider = { chapterPageCounts },
            currentPageProvider = { currentPageInChapter },
            scrollRatioProvider = { currentScrollRatio },
            settingsProvider = { currentReaderSettings() },
            loadChapter = { index -> loadChapter(index) },
            applySettings = { settings -> applySettingsAndReload() },
            restorePosition = { position ->
                // Wire through the existing restore mechanism: set the
                // pending restore state, then load the target spine item.
                val epub = epub ?: return@EpubWebViewRenderer
                val target = position.clamped(epub.spine.size)
                if (target.spineIndex != spineIndex) {
                    restoreRatio = target.scrollRatio
                    loadChapter(target.spineIndex)
                } else {
                    webViewController.evaluateJavascript(
                        "if(window.Caesura){window.Caesura.gotoRatio(${target.scrollRatio},false);}"
                    )
                }
            },
        )
    }

    private fun currentReaderSettings(): com.epubreader.app.core.reader.ReaderSettings {
        return com.epubreader.app.core.reader.ReaderSettings(
            fontFamily = prefs.font,
            fontSize = prefs.fontSize,
            lineHeight = prefs.lineHeight,
            margin = prefs.margin,
            alignment = prefs.align,
            hyphenation = prefs.hyphenation,
            pageBottomGuard = prefs.pageBottomMargin,
            pageTurnAnimation = prefs.pageTurnAnimation,
            themeId = prefs.theme,
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMeasureWebView() {
        binding.measureWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            allowFileAccess = false
            allowContentAccess = false
        }
        binding.measureWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? =
                resolver?.intercept(request)

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                val webView = view ?: return

                if (measureCancelled || !measuring) return

                val generation = activeMeasureGeneration
                val index = measuringIndex

                if (!isExpectedMeasurementUrl(url, generation, index)) return

                webView.evaluateJavascript(
                    "if(window.Caesura){window.Caesura.apply();}"
                ) {
                    if (measureCancelled || !measuring) {
                        return@evaluateJavascript
                    }

                    if (generation != activeMeasureGeneration) {
                        return@evaluateJavascript
                    }

                    if (index != measuringIndex) {
                        return@evaluateJavascript
                    }

                    if (!isExpectedMeasurementUrl(url, generation, index)) {
                        return@evaluateJavascript
                    }

                    handler.postDelayed(
                        { readMeasuredCount(generation, index, url ?: return@postDelayed) },
                        120L
                    )
                }
            }
        }
    }

    private fun readMeasuredCount(
        generation: Int,
        index: Int,
        loadedUrl: String,
    ) {
        if (measureCancelled || !measuring) return
        if (generation != activeMeasureGeneration) return
        if (index != measuringIndex) return
        if (!isExpectedMeasurementUrl(loadedUrl, generation, index)) return

        binding.measureWebView.evaluateJavascript(
            "(function(){return window.Caesura ? window.Caesura.pageCount() : 1;})();"
        ) { result ->
            if (measureCancelled || !measuring) {
                return@evaluateJavascript
            }

            if (generation != activeMeasureGeneration) {
                return@evaluateJavascript
            }

            if (index != measuringIndex) {
                return@evaluateJavascript
            }

            if (!isExpectedMeasurementUrl(loadedUrl, generation, index)) {
                return@evaluateJavascript
            }

            handler.removeCallbacks(measureWatchdog)

            val count = result
                ?.trim('"')
                ?.toIntOrNull()
                ?.coerceAtLeast(1)
                ?: 1

            chapterPageCounts?.let { counts ->
                if (index in counts.indices) {
                    counts[index] = count
                }
            }

            measuringIndex = index + 1

            val book = epub
            if (book == null || measuringIndex >= book.spine.size) {
                measuring = false
                expectedMeasureUrl = null
                updatePageIndicator()
                enablePerPageSeeker()
                updateHistoryUi()
                persistScreenPageCounts()
            } else {
                loadForMeasurement(measuringIndex)
            }
        }
    }

    private fun isExpectedMeasurementUrl(
        url: String?,
        generation: Int,
        index: Int,
    ): Boolean {
        if (url.isNullOrBlank()) return false

        return url.contains("measure=$generation") &&
                url.contains("idx=$index")
    }

    private fun loadForMeasurement(index: Int) {
        val book = epub ?: return

        if (measureCancelled || !measuring) return

        if (index !in book.spine.indices) {
            measuring = false
            expectedMeasureUrl = null
            return
        }

        measuringLoaded = index

        val item = book.spine[index]
        val html = buildChapterHtml(item.href) ?: run {
            chapterPageCounts?.set(index, 1)
            measuringIndex = index + 1

            if (measuringIndex >= book.spine.size) {
                measuring = false
                expectedMeasureUrl = null
                updatePageIndicator()
                enablePerPageSeeker()
                updateHistoryUi()
                persistScreenPageCounts()
            } else {
                loadForMeasurement(measuringIndex)
            }
            return
        }

        val baseUrl = EpubResourceResolver.baseUrl(bookId, item.href)
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val measurementUrl =
            "$baseUrl${separator}measure=$activeMeasureGeneration&idx=$index"

        expectedMeasureUrl = measurementUrl

        handler.removeCallbacks(measureWatchdog)

        binding.measureWebView.loadDataWithBaseURL(
            measurementUrl,
            html,
            "text/html",
            "UTF-8",
            null
        )

        handler.postDelayed(measureWatchdog, 4000L)
    }

    /** Stable fingerprint for the exact reader layout used by screen-page counts.
     *  Width/height cover orientation/window changes; reader settings cover every
     *  value that can alter pagination. The EPUB checksum is already the book-level
     *  identity in Room, so it does not need to be duplicated here. */
    private fun screenPageLayoutKey(): String? {
        val width = binding.webView.width
        val height = binding.webView.height
        if (width <= 0 || height <= 0) return null
        return listOf(
            width,
            height,
            resources.displayMetrics.density,
            prefs.font,
            prefs.fontSize,
            prefs.lineHeight,
            prefs.margin,
            prefs.align,
            prefs.hyphenation,
            prefs.pageBottomMargin,
            topGuardPx(),
            bottomGuardPx(),
        ).joinToString("|")
    }

    private fun parseScreenPageMap(csv: String?, expectedSize: Int): IntArray? {
        if (csv.isNullOrBlank()) return null
        val values = csv.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (values.size != expectedSize || values.any { it < 1 }) return null
        return values.toIntArray()
    }

    private fun loadCachedScreenPageCounts(entity: BookEntity): Boolean {
        val key = screenPageLayoutKey() ?: return false
        if (entity.screenPageLayoutKey != key) return false
        val cached = parseScreenPageMap(entity.screenPageMapCsv, epub?.spine?.size ?: 0) ?: return false
        chapterPageCounts = cached
        perPageSeekerActive = cached.size > 1 && ReaderPageMapping.totalPages(cached) > 1
        if (perPageSeekerActive) {
            binding.seekChapter.max = ReaderPageMapping.totalPages(cached) - 1
            syncSeekBarFromCurrentPage()
            updatePageIndicator()
            updateSectionPages()
            updateHistoryUi()
        }
        return true
    }

    private fun persistScreenPageCounts() {
        if (bookId < 0L) return
        val key = screenPageLayoutKey() ?: return
        val counts = chapterPageCounts ?: return
        if (counts.isEmpty() || counts.any { it < 1 }) return
        val csv = counts.joinToString(",")
        lifecycleScope.launch(Dispatchers.IO) {
            db.bookDao().updateScreenPageMap(bookId, csv, key)
        }
    }

    /** Kick off background measurement of every chapter's page count. */
    private fun startMeasurement() {
        val book = epub ?: return
        if (book.spine.isEmpty()) return

        measureGeneration += 1
        activeMeasureGeneration = measureGeneration

        handler.removeCallbacks(measureWatchdog)

        if (chapterPageCounts == null || chapterPageCounts?.size != book.spine.size) {
            chapterPageCounts = IntArray(book.spine.size) { -1 }
        }

        measureCancelled = false
        measuring = true
        measuringIndex = 0
        measuringLoaded = -1
        expectedMeasureUrl = null

        binding.measureWebView.post {
            loadForMeasurement(0)
        }
    }

    private fun cancelMeasurement() {
        measureGeneration += 1
        activeMeasureGeneration = measureGeneration

        measureCancelled = true
        measuring = false
        measuringIndex = -1
        measuringLoaded = -1
        expectedMeasureUrl = null

        handler.removeCallbacks(measureWatchdog)
        handler.removeCallbacks(restartMeasurementRunnable)

        binding.measureWebView.stopLoading()
    }

    private fun startTtsForCurrentChapter() = ttsUiController.startTtsForCurrentChapter()

    // ------------------------------------------------------------- patch v37 tts

    /** SeekBar progress -> engine speech rate (0.5 + N * 0.05). */
    private fun ttsRateFor(progress: Int): Float = ttsUiController.ttsRateFor(progress)

    /** SeekBar progress -> engine pitch (0.5 + N * 0.05). */
    private fun ttsPitchFor(progress: Int): Float = ttsUiController.ttsPitchFor(progress)

    /** Transport + status state for the dedicated TTS overlay. The overlay's
     *  own visibility is managed by [showTtsOverlay] / [hideTtsOverlay] (opened
     *  by the speaker icon, closed by the back / stop controls); this only
     *  refreshes the play/pause icon and the status line. */
    private fun updateTtsControlsUi(playing: Boolean) = ttsUiController.updateTtsControlsUi(playing)

    /** Shows the Read Aloud panel as the only reader chrome. The normal reader
     *  top bar, bottom timeline, history controls and page indicator are hidden
     *  while TTS is open; the EPUB page itself remains visible underneath. */
    private fun showTtsOverlay() = ttsUiController.showTtsOverlay()

    /** Hides the Read Aloud panel and restores the reader chrome to the exact
     *  visibility state it had when TTS was opened. Read-aloud itself is not
     *  stopped here. */
    private fun hideTtsOverlay() = ttsUiController.hideTtsOverlay()

    /** Starts/stops the keep-alive foreground service with the media
     *  notification when background playback is enabled. The service stays
     *  alive while paused so the notification can offer Resume. */
    private fun updateTtsServiceState() = ttsUiController.updateTtsServiceState()

    private fun stopTtsCompletely() = ttsUiController.stopTtsCompletely()

    private fun showSleepTimerMenu() = ttsUiController.showSleepTimerMenu()

    /** Read-aloud settings sub-screen (opened from the TTS overlay's tune
     *  button). Hosts the speed/pitch sliders, sleep timer, background-playback
     *  toggle and voice picker so the main overlay stays clean. */
    private fun showTtsSettingsSheet() = ttsUiController.showTtsSettingsSheet()

    /** Offline voices only (network-required voices are filtered out). */
    private fun showVoicePicker() = ttsUiController.showVoicePicker()

    /** Loads per-book rate/pitch/voice from Room (falls back to app prefs).
     *  Patch v37: the sliders no longer live in the top bar (they moved into
     *  the settings sheet), so this only applies saved values to the controller;
     *  the sheet reads controller.speechRate / pitch each time it opens. */
    private fun applyTtsSettings() = ttsUiController.applyTtsSettings()

    /** Debounced persist of per-book TTS settings after slider/voice changes. */
    private fun scheduleTtsSettingsSave() = ttsUiController.scheduleTtsSettingsSave()

    private fun saveTtsSettingsNow() = ttsUiController.saveTtsSettingsNow()

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
    private fun highlightSpokenWord(segment: com.epubreader.app.epub.ReaderTtsSegment, start: Int, end: Int) =
        ttsUiController.highlightSpokenWord(segment, start, end)

    private fun clearSpokenWordHighlight() = ttsUiController.clearSpokenWordHighlight()

    private fun setupChrome() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnToc.setOnClickListener { showTocBookmarks() }
        binding.btnBookmarks.setOnClickListener { showTocBookmarks(selectBookmarks = true) }
        binding.btnHighlights.setOnClickListener { showTocBookmarks(selectHighlights = true) }
        binding.btnSearch.setOnClickListener { showSearchOverlay() }
        binding.btnSettings.setOnClickListener { showSettings() }
        // Patch v37: the TTS transport lives in its own full-screen overlay now
        // (see showTtsOverlay / hideTtsOverlay). The top bar's speaker button
        // only opens that overlay (or starts playback if idle) — it no longer
        // toggles pause, because pause lives in the overlay's own play/pause
        // button. The overlay's transport (play/pause, prev/next sentence, stop,
        // settings) is wired here. Speed + pitch sliders moved into
        // showTtsSettingsSheet so the overlay stays clean.
        binding.btnReadAloud.setOnClickListener {
            val state = ttsController?.state
            if (state == ReaderTtsController.State.PLAYING || state == ReaderTtsController.State.PAUSED) {
                // Already running: bring the overlay back (it may have been
                // dismissed) rather than toggling pause from the top bar.
                showTtsOverlay()
            } else {
                showTtsOverlay()
                startTtsForCurrentChapter()
            }
        }
        binding.btnTtsClose.setOnClickListener { hideTtsOverlay() }
        binding.btnTtsPlayPause.setOnClickListener { ttsController?.togglePauseResume() }
        binding.btnTtsPrev.setOnClickListener {
            ttsController?.skipSentence(forward = false)
        }
        binding.btnTtsNext.setOnClickListener {
            ttsController?.skipSentence(forward = true)
        }
        binding.btnTtsStop.setOnClickListener { stopTtsCompletely() }
        binding.btnTtsSettings.setOnClickListener { showTtsSettingsSheet() }

        // Seed the engine rate/pitch from app prefs as a fallback before the
        // per-book Room settings load (applyTtsSettings). The settings sheet is
        // the single place sliders are shown now.
        ttsController?.speechRate = ttsRateFor(prefs.ttsSpeedProgress)
        ttsController?.pitch = ttsPitchFor(prefs.ttsPitchProgress)
        binding.tvAddBookmark.setOnClickListener { addBookmark() }
        binding.readerHistoryBack.setOnClickListener { goBackInReaderHistory() }
        binding.readerHistoryForward.setOnClickListener { goForwardInReaderHistory() }
        binding.readerHistoryClear.setOnClickListener { clearReaderHistory() }
        updateHistoryUi()

        binding.seekChapter.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onStartTrackingTouch(sb: SeekBar?) {
                    userSeeking = true
                    manualSeekFinished = false
                    pendingSeekProgress = sb?.progress
                    progressController.invalidatePendingPolls()
                }

                override fun onProgressChanged(
                    sb: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    if (!fromUser) return
                    pendingSeekProgress = progress
                }

                override fun onStopTrackingTouch(sb: SeekBar?) {
                    if (manualSeekFinished) {
                        manualSeekFinished = false
                        return
                    }
                    finishSeek(sb?.progress ?: pendingSeekProgress)
                }
            }
        )

        // Let the platform SeekBar own the drag gesture. The only custom touch
        // handling is on ACTION_UP: correct the final thumb position from the
        // actual touch coordinate before the normal OnStopTrackingTouch callback
        // resolves the seek. This preserves the existing tap behavior and avoids
        // intercepting the drag stream or issuing repeated WebView navigations.
        binding.seekChapter.setOnTouchListener { view, event ->
            val sb = view as SeekBar
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    manualSeekTouch = true
                    userSeeking = true
                    manualSeekFinished = false
                    progressController.invalidatePendingPolls()
                    false
                }

                MotionEvent.ACTION_UP -> {
                    if (manualSeekTouch) {
                        val finalProgress = progressForSeekTouch(sb, event.x)
                        if (finalProgress != sb.progress) {
                            sb.progress = finalProgress
                        }
                        pendingSeekProgress = finalProgress
                    }
                    manualSeekTouch = false
                    false
                }

                MotionEvent.ACTION_CANCEL -> {
                    manualSeekTouch = false
                    pendingSeekProgress = null
                    userSeeking = false
                    false
                }

                else -> false
            }
        }



        val detector =
            android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    // Patch v37: don't turn the page or toggle chrome when a
                    // modal overlay (TOC / Bookmarks / Search) is open.
                    if (overlayVisible()) return true
                    // Patch v37: don't turn the page or toggle chrome when this
                    // tap landed on a highlight (the HighlightBridge already
                    // opened the note/delete sheet) or just dismissed a text
                    // selection.
                    if (shouldSuppressReaderTap()) return true
                    // Patch 16 (Issue #2): the user reported that tapping a link inside
                    // a TOC (rendered as html/xhtml content in the reader) registers
                    // BOTH the link click AND a page turn (left third -> back, right
                    // third -> forward) because the WebView touch listener returns
                    // false, so the WebView also processes the tap natively as a link
                    // click. Before deciding whether this tap is a page turn / chrome
                    // toggle, check whether it landed on a hyperlink; if it did, let the
                    // WebView handle it natively (navigateToUrl) and do NOT turn the
                    // page or toggle chrome. A tap on the slider/button area also lands
                    // here but should not jump pages either — so for any non-page region
                    // tap that hit an anchor, defer to the web view.
                    if (tappedLinkOnWebView(e)) return true
                    val w = binding.webView.width.toFloat()
                    if (w <= 0f) {
                        toggleChrome(); return true
                    }
                    // Patch v37: when the reader settings menu (chrome) is
                    // visible, a tap only hides it — it never turns the page.
                    if (chromeVisible) {
                        toggleChrome(); return true
                    }
                    when {
                        e.x < w / 3f -> turnPage(false)
                        e.x > w * 2f / 3f -> turnPage(true)
                        else -> toggleChrome()
                    }
                    return true
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    val start = e1 ?: return false
                    val dx = e2.x - start.x
                    val dy = e2.y - start.y
                    if (kotlin.math.abs(dx) < 80f) return false
                    if (kotlin.math.abs(dx) < kotlin.math.abs(dy)) return false
                    if (kotlin.math.abs(velocityX) < 500f) return false
                    if (dx < 0) turnPage(true) else turnPage(false)
                    return true
                }
            })
        binding.webView.setOnTouchListener { _, event ->
            // Do not call ActionMode.hide() from WebView touch dispatch. The native
            // ActionMode is owned by Chromium and hiding it during a selection touch
            // can crash on some Android versions. The Activity-level menu suppression
            // above keeps its action toolbar empty without disturbing selection.
            // Patch v37: a fresh tap that lands while a text selection is active
            // dismisses the selection. The entire gesture is consumed (never
            // reaches the GestureDetector) so it does NOT also turn the page or
            // toggle the reader chrome — the user explicitly asked that
            // unselecting text leave the page and chrome untouched.
            if (event.actionMasked == MotionEvent.ACTION_DOWN && currentSelectionActionMode != null) {
                consumingSelectionDismissTap = true
                suppressReaderTap()
                clearReaderSelection()
            }
            if (consumingSelectionDismissTap) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    consumingSelectionDismissTap = false
                }
                return@setOnTouchListener true
            }
            detector.onTouchEvent(event); false
        }
    }


    private fun progressForSeekTouch(sb: SeekBar, x: Float): Int =
        chromeController.progressForSeekTouch(sb, x)

    private fun finishSeek(target: Int?) {
        // Set pendingExactSeekLocation before navigation (for progress controller)
        // but DON'T mutate currentPageInChapter/currentScrollRatio yet — the
        // controller needs to capture the pre-seek location for history.
        val resolved = target ?: chromeController.sbProgressFallback()
        if (perPageSeekerActive && resolved != null) {
            val targetLocation = locationForAbsolutePage(resolved)
            pendingExactSeekLocation = targetLocation
            exactSeekUiLocation = pendingExactSeekLocation
        } else {
            pendingExactSeekLocation = null
        }
        // Controller captures current location, pushes history, navigates
        chromeController.finishSeek(target)
        // NOW mutate UI state to target (after controller captured pre-seek location)
        if (perPageSeekerActive && resolved != null) {
            val targetLocation = locationForAbsolutePage(resolved)
            targetLocation?.let { loc ->
                currentPageInChapter = loc.pageInChapter
                currentScrollRatio = loc.ratio
                historyCursorLocation = loc
                updatePageIndicator()
                updateOverallProgress()
            }
        }
        handler.postDelayed({ pollProgress() }, 150L)
    }

    private fun sbProgressFallback(): Int? = chromeController.sbProgressFallback()

    private fun captureReaderLocation(): ReaderLocation? = navigationController.captureReaderLocation()

    private fun locationForAbsolutePage(absolute: Int): ReaderLocation? = navigationController.locationForAbsolutePage(absolute)

    private fun sameLocation(a: ReaderLocation, b: ReaderLocation): Boolean = navigationController.sameLocation(a, b)

    private fun pushHistory(location: ReaderLocation) = navigationController.pushHistory(location)

    private fun goBackInReaderHistory() = navigationController.goBackInReaderHistory()

    private fun goForwardInReaderHistory() = navigationController.goForwardInReaderHistory()

    private fun clearReaderHistory() = navigationController.clearReaderHistory()

    private fun navigateToReaderLocation(location: ReaderLocation) = navigationController.navigateToReaderLocation(location)

    private fun historyPageLabel(location: ReaderLocation): String = navigationController.historyPageLabel(location)

    private fun updateHistoryUi() = navigationController.updateHistoryUi()
    // Patch 16 (Issue #2): returns true if the confirmed tap landed on a link
    // inside the WebView. hitTestResult is queried immediately (the touch is
    // still in the DOWN -> UP window when onSingleTapConfirmed fires), and an
    // SRC_ANCHOR / SRC_IMAGE_ANCHOR result is the explicit signal that the tap
    // is on a hyperlink. This lets the WebView's native click handling run for
    // the link while suppressing the page turn the gesture detector would
    // otherwise trigger. Without this, tapping a TOC entry navigates to the
    // link's destination AND also advances/retreats a page.
    private fun tappedLinkOnWebView(e: MotionEvent): Boolean = navigationController.tappedLinkOnWebView(e)

    private var pageTurnThrottleUntil = 0L

    /** Patch 17 (Addition #2): remembered turn direction for the snapshot slide,
     *  + a token that invalidates a stale dismiss end-action when a newer capture
     *  supersedes an in-flight slide (so the older cleanup never recycles the
     *  newer bitmap). */
    private var snapshotForward = true
    private var snapshotAnimToken = 0
    private fun turnPage(forward: Boolean) = navigationController.turnPage(forward)

    private fun performPageTurn(forward: Boolean) = navigationController.performPageTurn(forward)

    private fun toggleChrome() = chromeController.toggleChrome()

    // ---------------------------------------------------------------- load book
    private fun loadBook() {
        lifecycleScope.launch(Dispatchers.IO) {
            val entity = db.bookDao().getById(bookId) ?: return@launch
            bookEntity = entity
            val file = File(entity.path)
            if (!file.exists()) return@launch

            // Safe pipeline wiring via shared parse bridge (Audit Priority 6).
            // See EpubParseBridge for the parity-check strategy.
            val parsed = com.epubreader.app.core.epub.EpubParseBridge.parse(file)
                ?: return@launch
            epub = parsed
            // Keep the legacy spine count synchronized for reader compatibility.
            // Home uses the embedded navigation TOC location instead of a chapter count.
            if (entity.spineCount != parsed.spine.size) {
                db.bookDao().updateSpineCount(bookId, parsed.spine.size)
            }
            resolver = EpubResourceResolver(file)
            buildTocSectionMap(parsed)
            spineIndex = entity.spineIndex.coerceIn(0, parsed.spine.lastIndex)
            restoreRatio = entity.scrollRatio.takeIf { it > 0f }

            // Patch 7 behavior: page counts come from a REAL offscreen layout pass
            // (the measureWebView), not the ADE byte-map. The total therefore
            // reflects the current font / size / margins / line-height and changes
            // when you change reader settings — one screen page == one book page,
            // like Calibre / Readium. Counts start at -1 ("not measured yet") and
            // are filled in chapter-by-chapter in the background; the bottom
            // seeker switches to per-page once every chapter has been measured.
            // (Patch 8's EpubPageMap / page_map_csv instant-stable totals are no
            // longer used for the reader; the DB column is left in place so
            // existing installs don't need a schema downgrade.)
            chapterPageCounts = IntArray(parsed.spine.size) { -1 }

            withContext(Dispatchers.Main) {
                bindBookHeader(parsed)
                // Patch v37: per-book read-aloud settings (rate/pitch/voice)
                // plus the book's language for multilingual TTS + dictionary.
                applyTtsSettings()
                perPageSeekerActive = false
                binding.seekChapter.max = (parsed.spine.size - 1).coerceAtLeast(0)
                binding.seekChapter.progress = spineIndex
                loadChapter(spineIndex, resetRatio = false)
                binding.tvPageIndicator.visibility = View.VISIBLE

                // Exact rendered page totals are inherently layout-dependent: the
                // visible WebView knows the current chapter immediately, but the
                // total book page count requires every spine item to be paginated.
                // Reuse the persisted screen-page map when the layout fingerprint
                // matches; this makes app reopen / book reopen instantaneous. A new
                // book or a new layout still needs the one-time background pass.
                binding.webView.post {
                    val cached = loadCachedScreenPageCounts(entity)
                    if (!cached) {
                        startMeasurement()
                    }
                    updatePageIndicator()
                    updateSectionPages()
                }
                updatePageIndicator()
                updateSectionPages()
                beginReadingSession(System.currentTimeMillis())
            }
        }
    }

    private fun bindBookHeader(book: EpubBook) {
        val m = book.metadata
        binding.tvBookTitle.text = m.title.ifBlank { getString(R.string.reader_contents) }
        val authorSeries = buildString {
            append(m.authorString)
            val s = m.series
            if (!s.isNullOrBlank()) {
                append("  —  ").append(s)
                m.seriesIndex?.let { idx ->
                    val n = idx.toInt()
                    append(" #$n")
                }
            }
        }
        binding.tvAuthorSeries.text = authorSeries
        binding.tvOverlayTitle.text = m.title.ifBlank { getString(R.string.reader_contents) }
    }

    /** Build the spine -> section-label map from the embedded nav TOC (NOT toc.xhtml). */
    private fun buildTocSectionMap(book: EpubBook) {
        val bySpine = LinkedHashMap<Int, String>()
        for (e in book.toc) {
            val path = normalizeTocHref(e.href)
            if (path.isBlank()) continue
            val idx = book.spine.indexOfFirst { normalizeTocHref(it.href) == path }
            if (idx >= 0 && !bySpine.containsKey(idx)) bySpine[idx] = e.label.trim()
        }
        tocSectionMap = bySpine
        tocSections = bySpine.toList().sortedBy { it.first }
    }

    private fun normalizeTocHref(href: String): String =
        href.substringBefore('#').substringBefore('?').trimStart('/').trimEnd('/')

    private fun currentTocPosition(): Int {

        val book = epub
            ?: return RecyclerView.NO_POSITION

        var bestPosition =
            RecyclerView.NO_POSITION

        var bestSpineIndex =
            -1

        for (position in book.toc.indices) {

            val entry =
                book.toc[position]

            val tocPath =
                entry.href
                    .substringBefore('#')
                    .substringBefore('?')
                    .trimStart('/')

            val entrySpineIndex =
                book.spine.indexOfFirst {
                    it.href
                        .substringBefore('#')
                        .substringBefore('?')
                        .trimStart('/') == tocPath
                }

            if (
                entrySpineIndex >= 0 &&
                entrySpineIndex <= spineIndex &&
                (
                        entrySpineIndex > bestSpineIndex ||
                                (
                                        entrySpineIndex == bestSpineIndex &&
                                                position > bestPosition
                                        )
                        )
            ) {
                bestSpineIndex =
                    entrySpineIndex

                bestPosition =
                    position
            }
        }

        return bestPosition
    }

    private fun highlightCurrentTocEntry() {

        val recycler =
            tocRv
                ?: return

        val adapter =
            recycler.adapter
                    as? TocAdapter
                ?: return

        val position =
            currentTocPosition()

        adapter.setSelectedPosition(
            position
        )

        if (
            position == RecyclerView.NO_POSITION
        ) {
            return
        }

        recycler.post {

            if (
                position !in 0 until adapter.itemCount
            ) {
                return@post
            }

            val layoutManager =
                recycler.layoutManager
                        as? LinearLayoutManager
                    ?: return@post

            val offset =
                (
                        recycler.height * 0.35f
                        ).toInt()

            layoutManager.scrollToPositionWithOffset(
                position,
                offset
            )
        }
    }

    /** Persist only the embedded navigation TOC heading for the current reader location. */
    private fun persistCurrentTocLocation() {
        val location = tocSectionMap[spineIndex]
            ?: tocSections.lastOrNull { it.first <= spineIndex }?.second
            ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            db.bookDao().updateCurrentLocation(bookId, location)
        }
    }

    /** Section label for the current spine item (embedded nav, nearest-previous fallback). */
    private fun sectionLabel(): String {
        val idx = spineIndex
        tocSectionMap[idx]?.let { return it }
        // nearest previous toc entry
        val prev = tocSections.lastOrNull { it.first <= idx }?.second
        if (!prev.isNullOrBlank()) return prev
        // fallback: derive from filename or position
        val href = epub?.spine?.getOrNull(idx)?.href
        val base = href?.substringAfterLast('/')?.substringBeforeLast('.')?.replace('_', ' ')?.replace('-', ' ')
        return base?.replaceFirstChar { it.uppercase() } ?: getString(R.string.reader_chapter, idx + 1)
    }

    // ---------------------------------------------------------------- chapter rendering
    private fun loadChapter(index: Int, resetRatio: Boolean = true) {
        clearReaderSelection()
        val book = epub ?: return
        if (index !in book.spine.indices) return
        val crossing = index != spineIndex
        if (crossing) capturePageSnapshot(forward = index > spineIndex)  // keep the old page visible while the next loads
        spineIndex = index
        persistCurrentTocLocation()
        // While per-page seeking is active the seeker's max is total pages, so
        // don't reset progress to a raw spine index here — syncSeekBarFromCurrentPage()
        // (called via the poller / reveal) keeps it on the right absolute page.
        if (!perPageSeekerActive) binding.seekChapter.progress = index
        if (resetRatio) currentScrollRatio = 0f
        handler.removeCallbacks(alphaFallback)

        val item = book.spine[index]
        val html = buildChapterHtml(item.href) ?: return
        val baseUrl = EpubResourceResolver.baseUrl(bookId, item.href)
        // Hide until the restored page is set, to avoid the page-0 flash.
        // Incrementing the generation invalidates callbacks from an older chapter load.
        readerGeneration += 1
        activeReaderGeneration = readerGeneration

        binding.webView.alpha = 0f
        binding.webView.loadDataWithBaseURL(
            baseUrl,
            html,
            "text/html",
            "UTF-8",
            null
        )
        binding.tvSectionPages.text = sectionLabel()

        if (binding.tocBookmarkOverlay.visibility == View.VISIBLE) {
            highlightCurrentTocEntry()
        }

        updateOverallProgress()
        updatePageIndicator()
    }

    private fun buildChapterHtml(entryPath: String): String? {
        val res = resolver ?: return null
        val raw = res.resolve(entryPath)?.bufferedReader()?.use { it.readText() } ?: return null
        val css = buildReaderCss()
        val js = paginationJs(bottomGuardPx(), topGuardPx())
        val head = "$css$js"
        return if (raw.contains("</head>", ignoreCase = true)) {
            raw.replaceFirst("(?i)</head>".toRegex(), "$head</head>")
        } else if (raw.contains("<html", ignoreCase = true)) {
            raw.replaceFirst("(?i)<html".toRegex(), "<head>$head</head><html")
        } else {
            "<head>$head</head>$raw"
        }
    }

    private fun buildReaderCss(): String {
        val (bg, ink) = readerColors()
        return ReaderCssBuilder.build(
            bgColorInt = bg,
            inkHex = ink,
            themeId = prefs.theme,
            font = prefs.font,
            fontSize = prefs.fontSize,
            lineHeight = prefs.lineHeight,
            margin = prefs.margin,
            align = prefs.align,
            hyphenation = prefs.hyphenation,
        )
    }

    /** Pagination JS. Key fix for the "next-page sliver": columns are `column-gap = 2*margin`
     *  wide and each page advances by the full viewport width (innerWidth), so column N+1
     *  begins exactly at the right edge of the viewport and never peeks into page N.
     *  Bottom guard shrinks body height to reserve space for the page indicator.
     *
     *  Extracted to [ReaderPaginationScriptBuilder] (Phase 6). */
    private fun paginationJs(guardPx: Int, topGuardPx: Int): String =
        ReaderPaginationScriptBuilder.build(guardPx, topGuardPx)

    // ---------------------------------------------------------------- navigation
    private fun navigateToUrl(url: String) = navigationController.navigateToUrl(url)

    private fun goToSpine(index: Int) = navigationController.goToSpine(index)

    /** Switch the bottom timeline from chapter-level to per-page once every
     *  chapter has been measured. Until then the seeker stays chapter-level so a
     *  drag can never land on a chapter's first page by accident. */
    private fun enablePerPageSeeker() = navigationController.enablePerPageSeeker()

    private fun syncSeekBarFromCurrentPage() = navigationController.syncSeekBarFromCurrentPage()

    /** Dragging the whole-book seeker: resolve the absolute rendered page to an
     *  exact (spine, page) pair and use the same Caesura pagination API that the
     *  existing tap/TOC navigation already uses. */
    private fun seekToAbsolutePage(absolute: Int) = navigationController.seekToAbsolutePage(absolute)

    // ---------------------------------------------------------------- restore / flash
    /** Capture the currently visible page into the snapshot overlay so it stays
     *  on screen (above the reading WebView) while the next page/chapter loads and
     *  is positioned. Removed via [dismissPageSnapshot] once the target page is
     *  revealed. Best-effort: if capture fails, falls back to the previous
     *  alpha-hide behavior (no regression).
     *
     *  Patch 9: this is now used for EVERY page transition (in-chapter page turns,
     *  cross-chapter turns, TOC jumps, and seeker jumps), so it may be called in
     *  rapid succession. To avoid leaking a full-screen bitmap on every turn, the
     *  previous snapshot bitmap is recycled before the new one is installed.
     *
     *  Patch 17 (Addition #2): [forward] is remembered in [snapshotForward] so the
     *  slide-out direction is known at dismiss time even for paths that don't pass
     *  it explicitly (chapter restore, etc.). */
    private fun capturePageSnapshot(forward: Boolean = true) = navigationController.capturePageSnapshot(forward)

    /** Remove the captured page snapshot. Patch 17 (Addition #2):
     *  - Page Turn Animation ON  -> slide the old page out horizontally in the
     *    turn direction (forward = off the left edge, back = off the right edge)
     *    with a short alpha fade, reading like turning a physical page. Duration
     *    [PAGE_TURN_DURATION_MS] (longer than the old 220ms crossfade).
     *  - Page Turn Animation OFF -> clear instantly (no animation).
     *  [forward] overrides the remembered direction; null = use [snapshotForward].
     *  A token ([snapshotAnimToken]) guards the end-action so a slide that gets
     *  cancelled by a newer capture can never recycle the newer bitmap. */
    private fun dismissPageSnapshot(forward: Boolean? = null) = navigationController.dismissPageSnapshot(forward)

    /** Detach + recycle the snapshot bitmap and reset the ImageView transform.
     *  Shared by the animated and instant dismiss paths so cleanup is identical. */
    private fun clearSnapshot(v: ImageView) = navigationController.clearSnapshot(v)

    private fun applyPendingFragmentOrRestore() = navigationController.applyPendingFragmentOrRestore()

    // ---------------------------------------------------------------- progress + indicators
    private fun pollProgress() = progressController.pollProgress()

    private fun parseProgress(result: String?) = progressController.parseProgress(result)

    private fun updateOverallProgress() = progressController.updateOverallProgress()

    private fun persistProgressNow() = progressController.persistProgressNow()

    /** Whole-book current page / total page indicator (persistent + bottom-bar bold). */
    private fun updatePageIndicator() = progressController.updatePageIndicator()

    private fun setPageText(text: String) = progressController.setPageText(text)

    /** Current rendered page within the current spine, using the page count that
     *  the visible Caesura WebView reports for the active chapter. */
    private fun currentAbsoluteBookPage(): Int = progressController.currentAbsoluteBookPage()

    /**
     * Spine index range of the current TOC section: from the nearest preceding
     * embedded-nav TOC entry up to (but not including) the next TOC entry.
     * A TOC section can span several spine items, so the top-bar page count must
     * aggregate measured page counts across the whole section, not just the
     * current spine item.
     */
    private fun sectionSpineRange(): IntRange = progressController.sectionSpineRange()

    /** Top-bar row 4: section label + page X/Y within that section. */
    private fun updateSectionPages() = progressController.updateSectionPages()

    // ---------------------------------------------------------------- TOC + Bookmarks overlay
    private var tocRv: RecyclerView? = null
    private var tocEmpty: TextView? = null
    private var bookmarkRv: RecyclerView? = null
    private var bookmarkEmpty: TextView? = null
    private var bookmarkAdapter: BookmarkAdapter? = null
    private var bookmarkObserverStarted = false
    private var highlightRv: RecyclerView? = null
    private var highlightEmpty: TextView? = null
    private var highlightObserverStarted = false
    private var pendingHighlightId: Long? = null
    private var pendingHighlightHistoryLocation: ReaderLocation? = null
    private var activeOverlayTab: Int = 0

    /** Whether the Bookmarks tab is currently shown in the TOC overlay. The
     *  bookmark DB observer fires refreshBookmarkList asynchronously; without
     *  this guard it re-shows the "empty bookmarks" hint over the TOC tab. */
    private var bookmarksTabActive = false

    private fun showTocBookmarks(selectBookmarks: Boolean = false, selectHighlights: Boolean = false) =
        overlayController.showTocBookmarks(selectBookmarks, selectHighlights)

    private fun refreshBookmarkList() = overlayController.refreshBookmarkList()

    private fun applyOverlayTab(checkedId: Int) = overlayController.applyOverlayTab(checkedId)

    // ---------------------------------------------------------------- search overlay
    private var searchRv: RecyclerView? = null
    private var searchEmpty: TextView? = null
    private var searchAdapter: SearchResultAdapter? = null
    private var searchEdit: android.widget.EditText? = null

    private fun showSearchOverlay() = overlayController.showSearchOverlay()

    private fun performSearch(query: String) = overlayController.performSearch(query)

    private fun tocMap(): Map<String, String> = overlayController.tocMap()

    // ---------------------------------------------------------------- bookmarks
    private fun goToBookmark(b: BookmarkEntity) = overlayController.goToBookmark(b)

    private fun addBookmark() = overlayController.addBookmark()

    private fun addBookmarkFromSelection(selection: ReaderSelectionLocator) =
        overlayController.addBookmarkFromSelection(selection)

    // ---------------------------------------------------------------- overlay helpers / back
    private fun overlayVisible(): Boolean = overlayController.overlayVisible()

    private fun hideOverlays() = overlayController.hideOverlays()

    private fun setupOverlays() = overlayController.setupOverlays()

    @Deprecated("Use the OnBackPressedDispatcher.", ReplaceWith("onBackPressedDispatcher.onBackPressed()"))
    override fun onBackPressed() {
        if (binding.searchOverlay.visibility == View.VISIBLE) {
            hideOverlays(); return
        }
        if (binding.tocBookmarkOverlay.visibility == View.VISIBLE) {
            hideOverlays(); return
        }
        // Patch v37: if the Read Aloud overlay is open, back minimizes it
        // (playback keeps going — Stop is the button that ends read-aloud).
        if (binding.ttsOverlay.visibility == View.VISIBLE) {
            hideTtsOverlay(); return
        }
        if (chromeVisible) {
            toggleChrome(); return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // ---------------------------------------------------------------- settings
    /** Patch 17 (Issue #1): reader settings are now a full-screen Activity
     *  (ReaderSettingsActivity), not a BottomSheet. It writes changes to prefs as
     *  the user makes them and returns RESULT_OK if anything changed; we apply
     *  them ONCE here on return (window theme + chapter reload + re-measure).
     *  This avoids re-measuring page counts on every +/- tap. */
    private fun showSettings() {
        clearReaderSelection()
        settingsLauncher.launch(Intent(this, ReaderSettingsActivity::class.java))
    }

    private val settingsLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                captureReflowAnchor { anchor ->
                    pendingReflowAnchor = anchor
                    applySettingsAndReload()
                }
            }
        }

    private fun captureReflowAnchor(onCaptured: (ReflowAnchor?) -> Unit) {
        val fallbackPage = currentPageInChapter
        webViewController.evaluateJavascript(
            """(function(){
                if(!window.Caesura) return '';
                var x=(window.innerWidth||1)*0.25, y=(window.innerHeight||1)*0.5;
                var range=null;
                if(document.caretRangeFromPoint) range=document.caretRangeFromPoint(x,y);
                else if(document.caretPositionFromPoint){
                    var pos=document.caretPositionFromPoint(x,y);
                    if(pos){range=document.createRange();range.setStart(pos.offsetNode,pos.offset);range.collapse(true);}
                }
                if(!range) return JSON.stringify({text:'',page:window.Caesura.currentPage()});
                var node=range.startContainer;
                if(node&&node.nodeType!==3) node=node.firstChild;
                var block=node&&node.parentElement?node.parentElement:null;
                while(block&&block!==document.body&&!/^(P|LI|BLOCKQUOTE|H1|H2|H3|H4|H5|H6|DIV|TD|TH)$/i.test(block.tagName)) block=block.parentElement;
                if(!block) return JSON.stringify({text:'',page:window.Caesura.currentPage()});
                var walker=document.createTreeWalker(block,NodeFilter.SHOW_TEXT,null,false), offset=0, found=false;
                while(walker.nextNode()){
                    if(walker.currentNode===range.startContainer){offset+=range.startOffset;found=true;break;}
                    offset+=walker.currentNode.textContent.length;
                }
                var text=(block.textContent||'').replace(/\\s+/g,' ').trim();
                if(!found||!text) return JSON.stringify({text:'',page:window.Caesura.currentPage()});
                var left=Math.max(0,offset-55), right=Math.min((block.textContent||'').length,offset+65);
                var anchor=(block.textContent||'').slice(left,right).replace(/\\s+/g,' ').trim();
                return JSON.stringify({text:anchor,page:window.Caesura.currentPage()});
            })();""",
        ) { result ->
            val raw = runCatching { org.json.JSONTokener(result?.trim().orEmpty()).nextValue() as? String }.getOrNull()
            val json = runCatching { raw?.let { org.json.JSONObject(it) } }.getOrNull()
            val text = json?.optString("text").orEmpty()
            val page = json?.optInt("page", fallbackPage) ?: fallbackPage
            onCaptured(text.takeIf { it.isNotBlank() }?.let { ReflowAnchor(it, page.coerceAtLeast(0)) })
        }
    }

    private fun applySettingsAndReload() {
        applyWindowTheme()

        // Patch 7 behavior: reader settings (font family / size / line height /
        // margins / alignment) change the layout, so the per-chapter page counts
        // must be RE-MEASURED — the total can change when you bump the font size,
        // exactly like Calibre / Readium. Cancel any in-flight measurement, reset
        // every chapter to "not measured yet," drop the per-page seeker back to
        // chapter-level, reload the current chapter, and restart the offscreen
        // measurement pass for the new layout.
        cancelMeasurement()
        val spineSize = epub?.spine?.size ?: 0
        chapterPageCounts = IntArray(spineSize) { -1 }
        perPageSeekerActive = false
        if (spineSize > 0) {
            binding.seekChapter.max = (spineSize - 1).coerceAtLeast(0)
            binding.seekChapter.progress = spineIndex.coerceIn(0, spineSize - 1)
        }
        restoreRatio = if (pendingReflowAnchor == null) currentScrollRatio else null
        loadChapter(spineIndex, resetRatio = false)
        binding.webView.post {
            val entity = bookEntity
            val cached = entity != null && loadCachedScreenPageCounts(entity)
            if (!cached) {
                startMeasurement()
            }
            updatePageIndicator()
            updateSectionPages()
        }
        updatePageIndicator()
        updateSectionPages()
    }

    // ---------------------------------------------------------------- lifecycle
    override fun onPause() {
        recordReadingSession(System.currentTimeMillis())
        // Patch v37: keep read-aloud running when the user has enabled
        // background playback; otherwise preserve the old stop-on-pause
        // behavior.
        if (!prefs.ttsBackgroundPlayback) {
            ttsController?.stop()
            hideTtsOverlay()
        } else {
            updateTtsServiceState()
        }
        super.onPause()
        handler.removeCallbacks(progressPoller)
        handler.removeCallbacks(alphaFallback)
        updateOverallProgress()
        progressController.flushProgressNow()
        persistProgressNow()
        keepScreenOnController.onPause()
    }

    override fun onStop() {
        progressController.flushProgressNow()
        persistProgressNow()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (epub != null) beginReadingSession(System.currentTimeMillis())
        handler.post(progressPoller)
        updatePageIndicator()
        updateSectionPages()
        keepScreenOnController.onResume()
        // Patch v37: if a modal overlay (TOC / Bookmarks / Search) was open when
        // the activity was paused (e.g. phone closed), the system may restore
        // window visibility on resume. Force the reader chrome hidden so the
        // top/bottom bars don't appear alongside the overlay.
        if (overlayVisible()) {
            chromeVisible = false
            binding.topBar.visibility = View.GONE
            binding.bottomBar.visibility = View.GONE
            binding.tvPageIndicator.visibility = View.GONE
        }
        // Same for the TTS panel: if it's visible, keep the bottom bar hidden.
        if (binding.ttsOverlay.visibility == View.VISIBLE) {
            binding.bottomBar.visibility = View.GONE
        }
    }

    override fun onUserInteraction() {
        val now = System.currentTimeMillis()
        val previous = readingSessionLastInteractionAt
        if (readingSessionStartedAt != null && previous > 0L) {
            val gap = ((now - previous) / 1000L).coerceAtLeast(0L)
            if (gap <= SESSION_IDLE_GAP_SECONDS) readingSessionActiveSeconds += gap.toInt()
        }
        readingSessionLastInteractionAt = now
        super.onUserInteraction()
        keepScreenOnController.bump()
    }

    override fun onDestroy() {
        if (::renderer.isInitialized) renderer.release()
        cancelMeasurement()
        handler.removeCallbacks(progressPoller)
        handler.removeCallbacks(alphaFallback)
        resolver?.close()
        definitionPopup?.dismiss()
        definitionPopup = null
        ttsUiController.cleanup()
        ReaderTtsService.attach(null)
        ReaderTtsService.stop(applicationContext)
        binding.webView.destroy()
        binding.measureWebView.destroy()
        definitionService.close()
        searchService.cancel()
        ttsCoordinator.close()
        super.onDestroy()
    }

    private fun beginReadingSession(now: Long) {
        if (readingSessionStartedAt == null) {
            readingSessionStartedAt = now
            readingSessionLastInteractionAt = now
            readingSessionActiveSeconds = 0
            readingSessionStartSpine = spineIndex
            readingSessionStartPage = currentPageInChapter
        }
    }

    private fun recordReadingSession(now: Long) {
        val started = readingSessionStartedAt ?: return
        if (readingSessionLastInteractionAt > 0L) {
            val gap = ((now - readingSessionLastInteractionAt) / 1000L).coerceAtLeast(0L)
            if (gap <= SESSION_IDLE_GAP_SECONDS) readingSessionActiveSeconds += gap.toInt()
        }
        val seconds = readingSessionActiveSeconds
        if (seconds >= 10 && bookId >= 0L) {
            val session = com.epubreader.app.data.ReadingSessionEntity(
                bookId = bookId, startedAt = started, endedAt = now, activeSeconds = seconds,
                chaptersAdvanced = kotlin.math.abs(spineIndex - readingSessionStartSpine),
                pagesAdvanced = kotlin.math.abs(currentPageInChapter - readingSessionStartPage),
            )
            lifecycleScope.launch(Dispatchers.IO) { db.readingSessionDao().insert(session) }
        }
        readingSessionStartedAt = null
        readingSessionLastInteractionAt = 0L
        readingSessionActiveSeconds = 0
    }

    companion object {
        private const val SESSION_IDLE_GAP_SECONDS = 300L
        const val EXTRA_BOOK_ID = "book_id"
        private const val TTS_SETTINGS_SAVE_DELAY_MS = 800L

        /** Patch 19 (Addition #1): slide duration for the page-turn snapshot.
         *  Longer than the old 220ms crossfade so the slide reads as a page turn
         *  instead of a flicker. Tune this one number to speed up/slow down the
         *  animation app-wide. */
        const val PAGE_TURN_DURATION_MS = 340L
    }

    override fun onActionModeStarted(mode: ActionMode) = selectionController.onActionModeStarted(mode)

    override fun onActionModeFinished(mode: ActionMode) = selectionController.onActionModeFinished(mode)

    private fun showReaderSelectionToolbar() = selectionController.showReaderSelectionToolbar()

    /**
     * Lets the entire custom toolbar act as a draggable surface. A tap on an
     * action view still performs that action; once the touch moves beyond the
     * normal touch slop, it becomes a toolbar drag and the action is suppressed.
     * Movement is free in both directions and clamped to the app's existing
     * popup edge margin.
     */
    private fun installSelectionToolbarDrag(content: View, popup: PopupWindow) =
        selectionController.installSelectionToolbarDrag(content, popup)

    private fun positionSelectionToolbar(popup: PopupWindow, selection: ReaderSelectionLocator) =
        selectionController.positionSelectionToolbar(popup, selection)

    private fun toolbarX(selection: ReaderSelectionLocator): Int =
        selectionController.toolbarX(selection)

    private fun toolbarY(selection: ReaderSelectionLocator): Int =
        selectionController.toolbarY(selection)

    private fun dismissReaderSelectionToolbar(finishActionMode: Boolean) =
        selectionController.dismissReaderSelectionToolbar(finishActionMode)

    private fun copySelectedText() = selectionController.copySelectedText()

    private fun processSelectedText(action: String) =
        selectionController.processSelectedText(action)

    private fun webSearchSelectedText() = selectionController.webSearchSelectedText()

    private fun showSelectionMoreMenu(anchor: View, selection: ReaderSelectionLocator?) =
        selectionController.showSelectionMoreMenu(anchor, selection)

    /** Dismisses the active text-selection ActionMode (the floating toolbar with
     *  Copy / Define / Highlight / …) and clears the WebView selection ranges.
     *  Called before any navigation away from the reader page (TOC / Bookmarks /
     *  Highlights / Search / Settings / TTS overlay / chapter change) and on a
     *  fresh tap on the page, so the selection toolbar never lingers. */
    private fun clearReaderSelection() = selectionController.clearReaderSelection()

    /** Suppresses page-turn / chrome-toggle for a short window after a tap that
     *  should not change the page (highlight tap, selection dismissal). The
     *  GestureDetector's onSingleTapConfirmed fires ~200ms after ACTION_DOWN;
     *  a 700ms window comfortably covers it. Patch v37. */
    private fun suppressReaderTap(durationMs: Long = 700L) =
        selectionController.suppressReaderTap(durationMs)

    private fun shouldSuppressReaderTap(): Boolean = selectionController.shouldSuppressReaderTap()

    private fun showDefinition(selection: ReaderSelectionLocator?) =
        selectionController.showDefinition(selection)

    /**
     * Patch v37: contextual definition card anchored near the selection.
     *
     * Positioned above the selection when there is room, otherwise below it,
     * clamped to the screen so it never runs off either edge; falls back to a
     * bottom-anchored card when no selection rect is available. Suggestions
     * are re-lookable with a tap, and a hook hands the word off to any
     * external dictionary app via ACTION_PROCESS_TEXT.
     */
    private fun showDefinitionCard(raw: String, result: com.epubreader.app.epub.DictionaryLookup.Result, selection: ReaderSelectionLocator?) =
        selectionController.showDefinitionCard(raw, result, selection)

    private fun themeColor(attr: Int): Int = selectionController.themeColor(attr)

    // ---------------------------------------------------------------- highlights

    /** Shows a compact color picker bottom sheet for creating a highlight. */
    private fun showHighlightColorPicker(selection: ReaderSelectionLocator?) =
        overlayController.showHighlightColorPicker(selection)

    /** Saves a highlight to the database and injects it into the WebView. */
    private fun saveHighlight(selection: ReaderSelectionLocator, color: Int) =
        overlayController.saveHighlight(selection, color)

    /** Injects a single highlight into the WebView immediately after creation.
     *
     * Patch v37 anchoring strategy (most-specific first):
     *  1. Resolve the stored start element path and search for the text
     *     within that element only - this keeps a repeated phrase from
     *     matching an earlier occurrence elsewhere in the chapter.
     *  2. If the path cannot be resolved, search all chapter occurrences and
     *     score each candidate against the saved prefix and suffix context.
     */
    private fun injectHighlightIntoWebView(id: Long, text: String, color: Int, note: String?, spineHref: String, locator: ReaderSelectionLocator?) {
        if (locator == null) return
        overlayController.injectHighlightIntoWebView(
            id, text, locator.prefix, locator.suffix, color,
            locator.startPath, locator.endPath, locator.startOffset, locator.endOffset
        )
    }

    /**
     * Navigates from the Highlights tab to the exact rendered page containing
     * the selected highlight. A highlight is not an EPUB URL/fragment, so using
     * navigateToUrl() here can reload the same chapter and restore the current
     * page while still adding a history entry.
     */
    private fun goToHighlight(highlight: com.epubreader.app.data.HighlightEntity) =
        overlayController.goToHighlight(highlight)

    /** Loads all highlights for the current chapter and injects them into the WebView. */
    private fun injectHighlightsForChapter() = overlayController.injectHighlightsForChapter()

    /** Removes every rendered fragment belonging to one persisted highlight.
     *
     * A single logical selection may be decorated by multiple <mark> nodes when
     * it crosses EPUB text nodes or inline spans. Deletion must remove all of
     * those decoration nodes without changing the underlying EPUB text.
     */
    private fun removeHighlightDecorationsFromWebView(highlightId: Long) =
        overlayController.removeHighlightDecorationsFromWebView(highlightId)

    /** Shows a bottom sheet for viewing a highlight and adding/editing a note. */
    private fun showHighlightNoteSheet(highlightId: Long) = overlayController.showHighlightNoteSheet(highlightId)
}
