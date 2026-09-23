package com.bookwormbliss.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookwormbliss.app.data.prefs.PreferencesRepository
import com.bookwormbliss.app.data.prefs.ReaderPreferences
import com.bookwormbliss.app.ui.bookwormApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val preferences: PreferencesRepository) : ViewModel() {

    val prefs: StateFlow<ReaderPreferences> = preferences.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences())

    fun update(transform: (ReaderPreferences) -> ReaderPreferences) {
        viewModelScope.launch { preferences.update(transform) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(bookwormApp().preferencesRepository) }
        }
    }
}
