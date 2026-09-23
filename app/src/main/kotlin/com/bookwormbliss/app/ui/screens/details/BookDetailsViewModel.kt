package com.bookwormbliss.app.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.data.repository.LibraryRepository
import com.bookwormbliss.app.ui.bookwormApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailsViewModel(private val repository: LibraryRepository, bookId: String) : ViewModel() {

    val book: StateFlow<BookEntity?> = repository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleFavorite(book: BookEntity) = viewModelScope.launch { repository.toggleFavorite(book) }
    fun delete(book: BookEntity) = viewModelScope.launch { repository.deleteBook(book) }

    companion object {
        fun factory(bookId: String) = viewModelFactory {
            initializer { BookDetailsViewModel(bookwormApp().libraryRepository, bookId) }
        }
    }
}
