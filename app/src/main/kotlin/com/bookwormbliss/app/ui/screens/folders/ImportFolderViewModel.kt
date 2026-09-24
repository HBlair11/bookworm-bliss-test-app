package com.bookwormbliss.app.ui.screens.folders

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookwormbliss.app.data.model.WatchedFolderEntity
import com.bookwormbliss.app.data.repository.LibraryRepository
import com.bookwormbliss.app.ui.bookwormApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportFolderUiState(
    val folders: List<WatchedFolderEntity> = emptyList(),
    val scanningFolderId: String? = null,
    val lastResultMessage: String? = null,
)

class ImportFolderViewModel(
    application: Application,
    private val repository: LibraryRepository,
) : AndroidViewModel(application) {

    private val scanningFolderId = MutableStateFlow<String?>(null)
    private val lastResultMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ImportFolderUiState> = combine(
        repository.observeWatchedFolders(),
        scanningFolderId,
        lastResultMessage,
    ) { folders, scanning, message ->
        ImportFolderUiState(folders = folders.sortedByDescending { it.addedDate }, scanningFolderId = scanning, lastResultMessage = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImportFolderUiState())

    /** Persists SAF permission for [uri], registers it, then runs the first scan — all off the main thread. */
    fun addFolder(uri: Uri, displayName: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val folder = repository.addWatchedFolder(uri.toString(), displayName)
            rescan(folder)
        }
    }

    fun rescan(folder: WatchedFolderEntity) {
        viewModelScope.launch {
            scanningFolderId.value = folder.id
            val result = repository.scanFolder(getApplication<Application>(), folder)
            scanningFolderId.value = null
            lastResultMessage.value = buildString {
                append("${folder.displayName}: ")
                append("${result.added} added, ${result.updated} updated")
                if (result.unchanged > 0) append(", ${result.unchanged} unchanged")
                if (result.failed > 0) append(", ${result.failed} failed")
            }
        }
    }

    fun rescanAll() {
        viewModelScope.launch {
            uiState.value.folders.forEach { rescan(it) }
        }
    }

    fun removeFolder(folder: WatchedFolderEntity) = viewModelScope.launch { repository.removeWatchedFolder(folder) }

    fun dismissMessage() { lastResultMessage.value = null }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = bookwormApp()
                ImportFolderViewModel(app, app.libraryRepository)
            }
        }
    }
}
