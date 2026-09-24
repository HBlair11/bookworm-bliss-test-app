package com.bookwormbliss.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.data.repository.LibraryRepository
import com.bookwormbliss.app.ui.bookwormApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthorSummary(val name: String, val bookCount: Int)
data class SeriesSummary(val name: String, val bookCount: Int)

data class HomeUiState(
    val heroBook: BookEntity? = null,
    val recentlyAdded: List<BookEntity> = emptyList(),
    val favorites: List<BookEntity> = emptyList(),
    val topAuthors: List<AuthorSummary> = emptyList(),
    val topSeries: List<SeriesSummary> = emptyList(),
    val isEmpty: Boolean = true,
)

class HomeViewModel(private val repository: LibraryRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeCurrentlyReading(),
        repository.observeBooks(),
    ) { reading, all ->
        val hero = reading.filter { it.progress < 0.98f }
            .maxByOrNull { it.lastOpenedDate ?: 0L }
            ?: all.firstOrNull()

        val authorCounts = all.groupBy { it.author }
            .map { (name, books) -> AuthorSummary(name, books.size) }
            .sortedByDescending { it.bookCount }
            .take(4)

        val seriesCounts = all.filter { !it.series.isNullOrBlank() }
            .groupBy { it.series!! }
            .map { (name, books) -> SeriesSummary(name, books.size) }
            .sortedByDescending { it.bookCount }
            .take(4)

        HomeUiState(
            heroBook = hero,
            recentlyAdded = all.sortedByDescending { it.addedDate }.take(6),
            favorites = all.filter { it.isFavorite }.take(4),
            topAuthors = authorCounts,
            topSeries = seriesCounts,
            isEmpty = all.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun toggleFavorite(book: BookEntity) = viewModelScope.launch { repository.toggleFavorite(book) }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(bookwormApp().libraryRepository) }
        }
    }
}
