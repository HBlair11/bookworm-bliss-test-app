package com.bookwormbliss.app.ui.screens.library

import android.net.Uri
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

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val isImporting: Boolean = false,
    val importError: String? = null,
)

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val importError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeBooks(),
        importing,
        importError,
    ) { books, isImporting, error ->
        LibraryUiState(
            books = books.sortedWith(compareBy { it.sortTitle }),
            isImporting = isImporting,
            importError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

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

    companion object {
        val Factory = viewModelFactory {
            initializer { LibraryViewModel(bookwormApp().libraryRepository) }
        }
    }
}
