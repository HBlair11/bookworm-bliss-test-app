package com.bookwormbliss.app.ui.screens.authorsseries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookwormbliss.app.data.repository.LibraryRepository
import com.bookwormbliss.app.ui.bookwormApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class NamedGroup(val name: String, val bookCount: Int, val coverPath: String?)

class AuthorsSeriesViewModel(repository: LibraryRepository) : ViewModel() {

    val authors: StateFlow<List<NamedGroup>> = repository.observeBooks()
        .map { books ->
            books.groupBy { it.author }
                .map { (name, list) -> NamedGroup(name, list.size, list.firstOrNull { it.coverPath != null }?.coverPath) }
                .sortedBy { it.name.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val series: StateFlow<List<NamedGroup>> = repository.observeBooks()
        .map { books ->
            books.filter { !it.series.isNullOrBlank() }
                .groupBy { it.series!! }
                .map { (name, list) -> NamedGroup(name, list.size, list.firstOrNull { it.coverPath != null }?.coverPath) }
                .sortedBy { it.name.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        val Factory = viewModelFactory {
            initializer { AuthorsSeriesViewModel(bookwormApp().libraryRepository) }
        }
    }
}
