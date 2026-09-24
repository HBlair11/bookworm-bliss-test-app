package com.bookwormbliss.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.data.repository.LibraryRepository
import com.bookwormbliss.app.ui.bookwormApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchFilter { ALL, TITLE, AUTHOR, SERIES }

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val history: List<String> = emptyList(),
    val results: List<BookEntity> = emptyList(),
)

class SearchViewModel(private val repository: LibraryRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SearchFilter.ALL)

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        filter,
        repository.observeBooks(),
        repository.observeSearchHistory(),
    ) { q, f, books, history ->
        val trimmed = q.trim().lowercase()
        val results = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            books.filter { book ->
                val matchTitle = book.title.lowercase().contains(trimmed)
                val matchAuthor = book.author.lowercase().contains(trimmed)
                val matchSeries = book.series?.lowercase()?.contains(trimmed) == true
                val matchTags = book.subjectTags?.lowercase()?.contains(trimmed) == true
                when (f) {
                    SearchFilter.TITLE -> matchTitle
                    SearchFilter.AUTHOR -> matchAuthor
                    SearchFilter.SERIES -> matchSeries
                    SearchFilter.ALL -> matchTitle || matchAuthor || matchSeries || matchTags
                }
            }
        }
        SearchUiState(query = q, filter = f, history = history, results = results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: SearchFilter) { filter.value = value }

    fun commitSearch() {
        val trimmed = query.value.trim()
        if (trimmed.isNotEmpty()) viewModelScope.launch { repository.addSearchTerm(trimmed) }
    }

    fun selectHistoryTerm(term: String) {
        query.value = term
        viewModelScope.launch { repository.addSearchTerm(term) }
    }

    fun deleteHistoryTerm(term: String) = viewModelScope.launch { repository.deleteSearchTerm(term) }
    fun clearHistory() = viewModelScope.launch { repository.clearSearchHistory() }

    companion object {
        val Factory = viewModelFactory {
            initializer { SearchViewModel(bookwormApp().libraryRepository) }
        }
    }
}
