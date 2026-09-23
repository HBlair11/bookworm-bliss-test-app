package com.bookwormbliss.app.ui.screens.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
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
                    if (b != null && _uiState.value.chapters.isEmpty()) {
                        val chapters = repository.chaptersFor(b)
                        val startIndex = b.spineIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                        _uiState.value = _uiState.value.copy(
                            book = b,
                            chapters = chapters,
                            currentIndex = startIndex,
                            paragraphs = paragraphsFor(chapters.getOrNull(startIndex)),
                            bookmarks = bm,
                            prefs = p,
                            isLoading = false,
                        )
                        repository.markOpened(b)
                    } else {
                        _uiState.value = _uiState.value.copy(book = b ?: _uiState.value.book, bookmarks = bm, prefs = p)
                    }
                }
        }
    }

    private fun paragraphsFor(chapter: EpubChapter?): List<String> {
        chapter ?: return emptyList()
        val doc = Jsoup.parse(chapter.html)
        val blocks = doc.select("p, h1, h2, h3, h4, h5, h6, blockquote, li")
        val paragraphs = blocks.map { it.text().trim() }.filter { it.isNotBlank() }
        return paragraphs.ifEmpty { chapter.text.split(Regex("\\n{2,}")).filter { it.isNotBlank() } }
    }

    fun goToChapter(index: Int) {
        val state = _uiState.value
        val clamped = index.coerceIn(0, (state.chapters.size - 1).coerceAtLeast(0))
        _uiState.value = state.copy(currentIndex = clamped, paragraphs = paragraphsFor(state.chapters.getOrNull(clamped)), showTocSheet = false)
        persistProgress(clamped)
        tts.stop()
    }

    fun nextChapter() = goToChapter(_uiState.value.currentIndex + 1)
    fun previousChapter() = goToChapter(_uiState.value.currentIndex - 1)

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
                pageInChapter = 0,
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
