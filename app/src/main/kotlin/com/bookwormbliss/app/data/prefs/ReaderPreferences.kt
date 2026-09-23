package com.bookwormbliss.app.data.prefs

import com.bookwormbliss.app.data.model.AlignOption
import com.bookwormbliss.app.data.model.FontId
import com.bookwormbliss.app.data.model.ReadingMode
import com.bookwormbliss.app.data.model.SortOption
import com.bookwormbliss.app.data.model.ThemeId

/**
 * Mirrors ReaderPreferences from the web app's types.ts. This is the single
 * source of truth for every reading/library display preference; it's
 * persisted via DataStore (see PreferencesRepository) and every screen that
 * needs any of these values observes the same Flow<ReaderPreferences> —
 * there is no per-screen copy of "font size" or "theme" state anywhere else.
 */
data class ReaderPreferences(
    val theme: ThemeId = ThemeId.IVORY,
    val font: FontId = FontId.SERIF,
    val fontSize: Int = 32, // 20..56
    val lineHeight: Float = 1.6f, // 1.0..2.6
    val margin: Int = 40, // 20..72
    val align: AlignOption = AlignOption.LEFT,
    val readingMode: ReadingMode = ReadingMode.VERTICAL,
    val hyphenation: Boolean = false,
    val pageBottomMargin: Boolean = true,
    val pageTurnAnimation: Boolean = true,
    val keepScreenOn: Boolean = true,
    val ttsSpeed: Float = 1.0f, // 0.5..3.0
    val ttsPitch: Float = 1.0f, // 0.5..2.0
    val ttsVoiceUri: String? = null,
    val viewModeGrid: Boolean = true,
    val gridColumns: Int = 3, // 2, 3, 4
    val sortOption: SortOption = SortOption.RECENTLY_READ,
    val sortAscending: Boolean = false,
    val readingGoalMinutesPerDay: Int? = 20,
    val showReadingGoal: Boolean = true,
)
