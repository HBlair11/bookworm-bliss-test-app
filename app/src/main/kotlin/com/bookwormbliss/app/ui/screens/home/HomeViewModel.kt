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

data class HomeUiState(
    val currentlyReading: List<BookEntity> = emptyList(),
    val recentlyAdded: List<BookEntity> = emptyList(),
    val isEmpty: Boolean = true,
)

class HomeViewModel(repository: LibraryRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeCurrentlyReading(),
        repository.observeBooks(),
    ) { reading, all ->
        HomeUiState(
            currentlyReading = reading,
            recentlyAdded = all.sortedByDescending { it.addedDate }.take(10),
            isEmpty = all.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(bookwormApp().libraryRepository) }
        }
    }
}
