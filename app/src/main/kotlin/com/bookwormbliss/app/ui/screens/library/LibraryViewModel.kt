package com.bookwormbliss.app.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.data.model.SortOption
import com.bookwormbliss.app.data.prefs.PreferencesRepository
import com.bookwormbliss.app.data.prefs.ReaderPreferences
import com.bookwormbliss.app.data.repository.LibraryRepository
import com.bookwormbliss.app.ui.bookwormApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val prefs: ReaderPreferences = ReaderPreferences(),
    val isImporting: Boolean = false,
    val importError: String? = null,
)

/** Which slice of the library this screen instance shows — reused for Library, Reading, Favorites, Author/Series detail. */
enum class LibraryScope { ALL, CURRENTLY_READING, FAVORITES, BY_AUTHOR, BY_SERIES }

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val preferences: PreferencesRepository,
    private val scope: LibraryScope,
    private val filterValue: String? = null,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val importError = MutableStateFlow<String?>(null)

    private val booksFlow: Flow<List<BookEntity>> = when (scope) {
        LibraryScope.ALL -> repository.observeBooks()
        LibraryScope.CURRENTLY_READING -> repository.observeCurrentlyReading()
        LibraryScope.FAVORITES -> repository.observeFavorites()
        LibraryScope.BY_AUTHOR -> repository.observeByAuthor(filterValue.orEmpty())
        LibraryScope.BY_SERIES -> repository.observeBySeries(filterValue.orEmpty())
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksFlow,
        preferences.preferencesFlow,
        importing,
        importError,
    ) { books, prefs, isImporting, error ->
        LibraryUiState(
            books = sortBooks(books, prefs),
            prefs = prefs,
            isImporting = isImporting,
            importError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    private fun sortBooks(books: List<BookEntity>, prefs: ReaderPreferences): List<BookEntity> {
        val sorted = when (prefs.sortOption) {
            SortOption.RECENTLY_READ -> books.sortedByDescending { it.lastOpenedDate ?: 0L }
            SortOption.RECENTLY_ADDED -> books.sortedByDescending { it.addedDate }
            SortOption.TITLE -> books.sortedBy { it.sortTitle }
            SortOption.AUTHOR -> books.sortedBy { it.sortAuthor }
            SortOption.SERIES -> books.sortedBy { it.series ?: it.sortTitle }
            SortOption.PROGRESS -> books.sortedByDescending { it.progress }
        }
        return if (prefs.sortAscending) sorted.reversed() else sorted
    }

    fun importEpubs(uris: List<Uri>, resolveName: (Uri) -> String?) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            importError.value = null
            for (uri in uris) {
                val result = repository.importEpub(uri, resolveName(uri))
                if (result.isFailure) {
                    importError.value = result.exceptionOrNull()?.message
                }
            }
            importing.value = false
        }
    }

    fun dismissError() { importError.value = null }
    fun toggleFavorite(book: BookEntity) = viewModelScope.launch { repository.toggleFavorite(book) }
    fun updatePrefs(transform: (ReaderPreferences) -> ReaderPreferences) = viewModelScope.launch { preferences.update(transform) }

    companion object {
        fun factory(scope: LibraryScope, filterValue: String? = null) = viewModelFactory {
            initializer {
                val app = bookwormApp()
                LibraryViewModel(app.libraryRepository, app.preferencesRepository, scope, filterValue)
            }
        }
    }
}
