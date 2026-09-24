package com.bookwormbliss.app.ui.screens.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.data.model.BookmarkEntity
import com.bookwormbliss.app.data.prefs.ReaderPreferences
import com.bookwormbliss.app.data.repository.LibraryRepository
import com.bookwormbliss.app.epub.EpubChapter
import com.bookwormbliss.app.tts.ReaderTtsController
import com.bookwormbliss.app.ui.bookwormApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

data class ReaderUiState(
    val book: BookEntity? = null,
    val chapters: List<EpubChapter> = emptyList(),
    val currentIndex: Int = 0,
    val paragraphs: List<String> = emptyList(),
    /**
     * [paragraphs] grouped into screen-sized chunks for horizontal paging
     * mode (see ReaderPreferences.readingMode). Recomputed whenever the
     * chapter changes or a layout-affecting preference (font size, margin,
     * line height) changes. This is a character-count heuristic rather than
     * a true text-layout measurement, so page breaks are approximate, not
     * pixel-exact.
     */
    val pages: List<List<String>> = emptyList(),
    val currentPageIndex: Int = 0,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val prefs: ReaderPreferences = ReaderPreferences(),
    val isLoading: Boolean = true,
    val isTtsSpeaking: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showTocSheet: Boolean = false,
)

class ReaderViewModel(
    application: Application,
    private val bookId: String,
    private val repository: LibraryRepository,
    private val preferences: com.bookwormbliss.app.data.prefs.PreferencesRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val tts = ReaderTtsController(application)

    init {
        viewModelScope.launch {
            val book = repository.observeBook(bookId)
            val bookmarks = repository.observeBookmarks(bookId)
            val prefsFlow = preferences.preferencesFlow
            kotlinx.coroutines.flow.combine(book, bookmarks, prefsFlow) { b, bm, p -> Triple(b, bm, p) }
                .collect { (b, bm, p) ->
                    val state = _uiState.value
                    if (b != null && state.chapters.isEmpty()) {
                        val chapters = repository.chaptersFor(b)
                        val startIndex = b.spineIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                        val paragraphs = paragraphsFor(chapters.getOrNull(startIndex))
                        _uiState.value = state.copy(
                            book = b,
                            chapters = chapters,
                            currentIndex = startIndex,
                            paragraphs = paragraphs,
                            pages = pagesFor(paragraphs, p),
                            bookmarks = bm,
                            prefs = p,
                            isLoading = false,
                        )
                        repository.markOpened(b)
                    } else {
                        val layoutChanged = layoutAffectingPrefsChanged(state.prefs, p)
                        _uiState.value = state.copy(
                            book = b ?: state.book,
                            bookmarks = bm,
                            prefs = p,
                            pages = if (layoutChanged) pagesFor(state.paragraphs, p) else state.pages,
                            currentPageIndex = if (layoutChanged) 0 else state.currentPageIndex,
                        )
                    }
                }
        }
    }

    private fun layoutAffectingPrefsChanged(old: ReaderPreferences, new: ReaderPreferences): Boolean =
        old.fontSize != new.fontSize || old.margin != new.margin || old.lineHeight != new.lineHeight

    private fun paragraphsFor(chapter: EpubChapter?): List<String> {
        chapter ?: return emptyList()
        val doc = Jsoup.parse(chapter.html)
        val blocks = doc.select("p, h1, h2, h3, h4, h5, h6, blockquote, li")
        val paragraphs = blocks.map { it.text().trim() }.filter { it.isNotBlank() }
        return paragraphs.ifEmpty { chapter.text.split(Regex("\\n{2,}")).filter { it.isNotBlank() } }
    }

    /**
     * Buckets [paragraphs] into pages sized by a rough characters-per-screen
     * budget derived from font size and margin — bigger font/margins mean
     * fewer characters fit, so pages get shorter. A paragraph is never split
     * across two pages (each page is a whole number of paragraphs), and an
     * unusually long single paragraph still gets its own page rather than
     * being dropped.
     */
    private fun pagesFor(paragraphs: List<String>, prefs: ReaderPreferences): List<List<String>> {
        if (paragraphs.isEmpty()) return emptyList()
        val charsPerPage = charBudget(prefs)
        val pages = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var currentChars = 0
        for (p in paragraphs) {
            if (current.isNotEmpty() && currentChars + p.length > charsPerPage) {
                pages += current
                current = mutableListOf()
                currentChars = 0
            }
            current += p
            currentChars += p.length
        }
        if (current.isNotEmpty()) pages += current
        return pages
    }

    private fun charBudget(prefs: ReaderPreferences): Int {
        // Rough model: a phone screen has ~360dp*640dp of usable text area at
        // default settings; larger font size and margins both shrink how
        // much text fits. Tuned to feel reasonable across the font-size
        // slider's 20..56 range rather than derived from real measurement.
        val fontFactor = (32f / prefs.fontSize.coerceIn(20, 56)).coerceIn(0.5f, 1.8f)
        val marginFactor = (40f / prefs.margin.coerceIn(20, 72)).coerceIn(0.7f, 1.3f)
        val lineFactor = (1.6f / prefs.lineHeight.coerceIn(1f, 2.6f)).coerceIn(0.6f, 1.4f)
        val base = 900
        return (base * fontFactor * marginFactor * lineFactor).toInt().coerceIn(200, 2200)
    }

    fun goToChapter(index: Int) {
        val state = _uiState.value
        val clamped = index.coerceIn(0, (state.chapters.size - 1).coerceAtLeast(0))
        val paragraphs = paragraphsFor(state.chapters.getOrNull(clamped))
        _uiState.value = state.copy(
            currentIndex = clamped,
            paragraphs = paragraphs,
            pages = pagesFor(paragraphs, state.prefs),
            currentPageIndex = 0,
            showTocSheet = false,
        )
        persistProgress(clamped)
        tts.stop()
    }

    fun nextChapter() = goToChapter(_uiState.value.currentIndex + 1)
    fun previousChapter() = goToChapter(_uiState.value.currentIndex - 1)

    fun goToPage(pageIndex: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(currentPageIndex = pageIndex.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0)))
    }

    /** Called by the horizontal pager when the user swipes past the last/first page of a chapter. */
    fun advanceToNextChapterFromPager() {
        if (_uiState.value.currentIndex < _uiState.value.chapters.lastIndex) nextChapter()
    }

    fun goToPreviousChapterFromPager() {
        if (_uiState.value.currentIndex > 0) previousChapter()
    }

    private fun persistProgress(index: Int) {
        val book = _uiState.value.book ?: return
        val total = (_uiState.value.chapters.size - 1).coerceAtLeast(1)
        val progress = index.toFloat() / total
        viewModelScope.launch { repository.updateProgress(book, index, 0f, progress) }
    }

    fun toggleSettingsSheet(show: Boolean) { _uiState.value = _uiState.value.copy(showSettingsSheet = show) }
    fun toggleTocSheet(show: Boolean) { _uiState.value = _uiState.value.copy(showTocSheet = show) }

    fun updatePrefs(transform: (ReaderPreferences) -> ReaderPreferences) {
        viewModelScope.launch { preferences.update(transform) }
    }

    fun addBookmark() {
        val state = _uiState.value
        val book = state.book ?: return
        val chapter = state.chapters.getOrNull(state.currentIndex) ?: return
        viewModelScope.launch {
            repository.addBookmark(
                bookId = book.id,
                spineIndex = state.currentIndex,
                pageInChapter = state.currentPageIndex,
                chapterTitle = chapter.title,
                snippet = state.paragraphs.firstOrNull().orEmpty().take(140),
            )
        }
    }

    fun removeBookmark(bookmark: BookmarkEntity) = viewModelScope.launch { repository.removeBookmark(bookmark) }

    fun playTts() {
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(state.currentIndex) ?: return
        tts.setSpeed(state.prefs.ttsSpeed)
        tts.setPitch(state.prefs.ttsPitch)
        _uiState.value = state.copy(isTtsSpeaking = true)
        tts.speakChapter(chapter.text) {
            _uiState.value = _uiState.value.copy(isTtsSpeaking = false)
        }
    }

    fun pauseTts() {
        tts.pause()
        _uiState.value = _uiState.value.copy(isTtsSpeaking = false)
    }

    override fun onCleared() {
        tts.shutdown()
        super.onCleared()
    }

    companion object {
        fun factory(bookId: String) = viewModelFactory {
            initializer {
                val app = bookwormApp()
                ReaderViewModel(app, bookId, app.libraryRepository, app.preferencesRepository)
            }
        }
    }
}
