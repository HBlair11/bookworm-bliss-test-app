package com.bookwormbliss.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookwormbliss.app.data.repository.LibraryRepository
import com.bookwormbliss.app.ui.bookwormApp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val totalBooks: Int = 0,
    val finishedBooks: Int = 0,
    val currentlyReading: Int = 0,
    val totalMinutesRead: Int = 0,
    val currentStreakDays: Int = 0,
    val hasAnyData: Boolean = false,
)

class StatsViewModel(repository: LibraryRepository) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observeBooks(),
        repository.observeSessions(),
    ) { books, sessions ->
        val totalMinutes = sessions.sumOf { it.activeSeconds } / 60
        val sessionDates = sessions.map { it.date }.toSet()
        StatsUiState(
            totalBooks = books.size,
            finishedBooks = books.count { it.progress >= 0.98f },
            currentlyReading = books.count { it.isCurrentlyReading },
            totalMinutesRead = totalMinutes,
            currentStreakDays = currentStreak(sessionDates),
            hasAnyData = books.isNotEmpty() || sessions.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    private fun currentStreak(sessionDates: Set<String>): Int {
        if (sessionDates.isEmpty()) return 0
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        var day = LocalDate.now()
        var streak = 0
        // A streak counts back from today; if today has no session yet, the
        // streak can still be "alive" through yesterday.
        if (!sessionDates.contains(day.format(formatter))) {
            day = day.minusDays(1)
        }
        while (sessionDates.contains(day.format(formatter))) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StatsViewModel(bookwormApp().libraryRepository) }
        }
    }
}
