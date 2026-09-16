package com.bookwormbliss.app

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bookwormbliss.app.data.BookEntity
import com.bookwormbliss.app.data.BookRepository
import com.bookwormbliss.app.data.PrefsManager
import com.bookwormbliss.app.databinding.ActivityReaderBinding
import com.bookwormbliss.app.epub.EpubParser
import com.bookwormbliss.app.epub.EpubResourceResolver
import com.bookwormbliss.app.reader.ReaderDocument
import com.bookwormbliss.app.reader.ReaderDocumentPosition
import com.bookwormbliss.app.reader.ReaderWebRenderer
import com.bookwormbliss.app.ui.ReaderSettingsActivity
import com.bookwormbliss.app.ui.ReaderTheme
import com.bookwormbliss.app.ui.TocAdapter
import com.bookwormbliss.app.util.KeepScreenOnController
import com.bookwormbliss.app.util.SystemBarController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * EPUB reading surface.
 *
 * Responsibilities are intentionally limited to UI orchestration and persistence:
 * load the format-specific document, ask the renderer to display it, expose TOC
 * and reader settings, and save/restore the canonical document position.
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val repository by lazy { BookRepository(applicationContext) }
    private val prefs by lazy { PrefsManager(applicationContext) }
    private val keepScreenOn by lazy { KeepScreenOnController(this, prefs) }

    private var book: BookEntity? = null
    private var document: ReaderDocument? = null
    private var resolver: EpubResourceResolver? = null
    private var renderer: ReaderWebRenderer? = null
    private var position = ReaderDocumentPosition(0, 0f)
    private var chapterPageCount = 1
    private var chromeVisible = false
    private var loading = false
    private var lastTapDownX = 0f
    private var lastTapDownY = 0f
    private var lastTapDownTime = 0L

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                renderPosition(position)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.tocOverlay.visibility == View.VISIBLE) {
                    hideToc()
                } else if (chromeVisible) {
                    setChromeVisible(false)
                } else {
                    finish()
                }
            }
        })

        setupChrome()
        setupToc()
        setupWebView()
        loadBook()
    }

    private fun setupChrome() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnToc.setOnClickListener { showToc() }
        binding.btnSettings.setOnClickListener {
            settingsLauncher.launch(android.content.Intent(this, ReaderSettingsActivity::class.java))
        }
        binding.btnPreviousPage.setOnClickListener { turnPage(false) }
        binding.btnNextPage.setOnClickListener { turnPage(true) }
        binding.seekChapter.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !loading) {
                    renderer?.setPositionRatio(progress / 1000f) { applied, count ->
                        position = position.copy(offsetRatio = applied)
                        chapterPageCount = count
                        updatePositionUi()
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
    }

    private fun setupToc() {
        val adapter = TocAdapter { entry ->
            val doc = document ?: return@TocAdapter
            val index = doc.epub.spineIndexForHref(entry.href.substringBefore('#'))
            if (index >= 0) {
                position = ReaderDocumentPosition(index, 0f)
                hideToc()
                renderPosition(position)
            }
        }
        binding.tocList.layoutManager = LinearLayoutManager(this)
        binding.tocList.adapter = adapter
        binding.btnTocBack.setOnClickListener { hideToc() }
    }

    private fun setupWebView() {
        binding.webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTapDownX = event.x
                    lastTapDownY = event.y
                    lastTapDownTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - lastTapDownTime
                    val moved = kotlin.math.hypot(
                        (event.x - lastTapDownX).toDouble(),
                        (event.y - lastTapDownY).toDouble()
                    )
                    if (elapsed < 300L && moved < 24f && binding.tocOverlay.visibility != View.VISIBLE) {
                        setChromeVisible(!chromeVisible)
                    }
                }
            }
            false
        }
    }

    private fun loadBook() {
        val id = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (id <= 0L) {
            finish()
            return
        }
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.getBook(id) }
            if (loaded == null) {
                finish()
                return@launch
            }
            book = loaded
            position = ReaderDocumentPosition(
                loaded.spineIndex,
                loaded.scrollRatio,
            ).normalized()
            binding.tvBookTitle.text = loaded.title
            binding.tvAuthorSeries.text = buildAuthorSeries(loaded)
            loadDocument(loaded)
        }
    }

    private suspend fun loadDocument(loaded: BookEntity) {
        val parsed = withContext(Dispatchers.IO) {
            runCatching { EpubParser().parse(java.io.File(loaded.path)) }.getOrNull()
        }
        if (parsed == null || parsed.spine.isEmpty()) {
            showError("This EPUB could not be rendered.")
            return
        }

        document = ReaderDocument(parsed)
        position = position.copy(spineIndex = position.spineIndex.coerceIn(0, parsed.spine.lastIndex))
        resolver = EpubResourceResolver(parsed.file)
        renderer = ReaderWebRenderer(binding.webView, loaded.id, resolver!!)
        (binding.tocList.adapter as? TocAdapter)?.submitList(parsed.toc)
        renderPosition(position)
    }

    private fun renderPosition(target: ReaderDocumentPosition) {
        val doc = document ?: return
        val currentBook = book ?: return
        val safe = doc.position(target.spineIndex, target.offsetRatio)
        position = safe
        loading = true
        renderer?.load(
            document = doc,
            position = safe,
            theme = ReaderTheme.byId(prefs.theme),
            marginDp = prefs.margin,
            fontSizeSp = prefs.fontSize,
            lineHeight = prefs.lineHeight,
            fontFamily = prefs.font,
            alignment = prefs.align,
            hyphenation = prefs.hyphenation,
        ) { spineIndex, requestedRatio ->
            position = ReaderDocumentPosition(spineIndex, requestedRatio)
            renderer?.setPositionRatio(requestedRatio) { applied, count ->
                loading = false
                position = position.copy(offsetRatio = applied)
                chapterPageCount = count
                updatePositionUi()
                persistPosition()
            }
        }
        binding.webView.setBackgroundColor(ReaderTheme.byId(prefs.theme).bgColor)
        binding.tvPageIndicator.setTextColor(ReaderTheme.byId(prefs.theme).inkColor)
        binding.tvPageIndicator.visibility = View.VISIBLE
        if (currentBook.isCurrentlyReading.not()) {
            lifecycleScope.launch(Dispatchers.IO) { repository.setCurrentlyReading(currentBook.id) }
        }
    }

    private fun turnPage(forward: Boolean) {
        renderer?.pageTurn(forward) { atBoundary ->
            if (!atBoundary) {
                renderer?.readPosition { ratio, page, count ->
                    position = position.copy(offsetRatio = ratio)
                    chapterPageCount = count
                    updatePositionUi()
                    persistPosition()
                }
                return@pageTurn
            }
            val nextIndex = position.spineIndex + if (forward) 1 else -1
            val doc = document ?: return@pageTurn
            if (nextIndex in 0 until doc.spineSize) {
                position = ReaderDocumentPosition(nextIndex, if (forward) 0f else 1f)
                renderPosition(position)
            }
        }
    }

    private fun updatePositionUi() {
        val doc = document ?: return
        val p = position.normalized()
        val page = ((p.offsetRatio * (chapterPageCount - 1)).roundToInt() + 1).coerceIn(1, chapterPageCount)
        val chapterLabel = doc.chapterLabel(p.spineIndex)
        binding.tvSectionPages.text = "$chapterLabel • $page / $chapterPageCount"
        binding.tvPageInfo.text = "Page $page / $chapterPageCount"
        binding.tvPercent.text = "${(wholeBookProgress(p) * 100f).roundToInt()}%"
        binding.seekChapter.progress = (p.offsetRatio * 1000f).roundToInt().coerceIn(0, 1000)
        binding.tvPageIndicator.text = "$page / $chapterPageCount"
        binding.tvPageIndicator.visibility = if (chromeVisible) View.GONE else View.VISIBLE
        (binding.tocList.adapter as? TocAdapter)?.setSelectedPosition(
            doc.epub.toc.indexOfFirst { doc.epub.spineIndexForHref(it.href.substringBefore('#')) == p.spineIndex }
        )
    }

    private fun wholeBookProgress(p: ReaderDocumentPosition): Float {
        val size = document?.spineSize?.coerceAtLeast(1) ?: 1
        return ((p.spineIndex + p.offsetRatio) / size).coerceIn(0f, 1f)
    }

    private fun persistPosition() {
        val currentBook = book ?: return
        val p = position.normalized()
        lifecycleScope.launch(Dispatchers.IO) {
            repository.updateReaderPosition(
                id = currentBook.id,
                progress = wholeBookProgress(p),
                spineIndex = p.spineIndex,
                scrollRatio = p.offsetRatio,
                location = document?.chapterLabel(p.spineIndex),
            )
        }
    }

    private fun setChromeVisible(visible: Boolean) {
        chromeVisible = visible
        binding.topBar.visibility = if (visible) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = if (visible) View.VISIBLE else View.GONE
        binding.tvPageIndicator.visibility = if (visible) View.GONE else View.VISIBLE
    }

    private fun showToc() {
        binding.tocOverlay.visibility = View.VISIBLE
        binding.topBar.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        binding.tvPageIndicator.visibility = View.GONE
        chromeVisible = false
    }

    private fun hideToc() {
        binding.tocOverlay.visibility = View.GONE
        setChromeVisible(true)
        updatePositionUi()
    }

    private fun showError(message: String) {
        loading = false
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun buildAuthorSeries(book: BookEntity): String {
        val author = book.author.ifBlank { getString(R.string.unknown_author) }
        val series = book.series?.trim().orEmpty()
        if (series.isBlank()) return author
        val index = book.seriesIndex?.let {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        }
        return if (index == null) "$author • $series" else "$author • $series #$index"
    }

    override fun onResume() {
        super.onResume()
        SystemBarController.apply(this)
        keepScreenOn.onResume()
    }

    override fun onPause() {
        persistCurrentPosition()
        keepScreenOn.onPause()
        super.onPause()
    }

    private fun persistCurrentPosition() {
        renderer?.readPosition { ratio, _, _ ->
            position = position.copy(offsetRatio = ratio)
            persistPosition()
        } ?: persistPosition()
    }

    override fun onDestroy() {
        renderer?.destroy()
        renderer = null
        resolver = null
        super.onDestroy()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        keepScreenOn.bump()
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }
}
