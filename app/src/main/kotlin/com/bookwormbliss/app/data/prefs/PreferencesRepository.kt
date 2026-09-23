package com.bookwormbliss.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bookwormbliss.app.data.model.AlignOption
import com.bookwormbliss.app.data.model.FontId
import com.bookwormbliss.app.data.model.ReadingMode
import com.bookwormbliss.app.data.model.SortOption
import com.bookwormbliss.app.data.model.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reader_preferences")

/**
 * The only place ReaderPreferences is read from or written to disk. Every
 * ViewModel that needs a preference collects [preferencesFlow] (or the
 * whole app wraps it once at the top and passes it down) instead of keeping
 * its own copy, so changing a setting on one screen (e.g. font size in the
 * reader) is instantly reflected anywhere else that cares (e.g. Settings).
 */
class PreferencesRepository(private val context: Context) {

    val preferencesFlow: Flow<ReaderPreferences> = context.dataStore.data.map { p ->
        ReaderPreferences(
            theme = p[Keys.THEME]?.let { runCatching { ThemeId.valueOf(it) }.getOrNull() } ?: ThemeId.IVORY,
            font = p[Keys.FONT]?.let { runCatching { FontId.valueOf(it) }.getOrNull() } ?: FontId.SERIF,
            fontSize = p[Keys.FONT_SIZE] ?: 32,
            lineHeight = p[Keys.LINE_HEIGHT] ?: 1.6f,
            margin = p[Keys.MARGIN] ?: 40,
            align = p[Keys.ALIGN]?.let { runCatching { AlignOption.valueOf(it) }.getOrNull() } ?: AlignOption.LEFT,
            readingMode = p[Keys.READING_MODE]?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() } ?: ReadingMode.VERTICAL,
            hyphenation = p[Keys.HYPHENATION] ?: false,
            pageBottomMargin = p[Keys.PAGE_BOTTOM_MARGIN] ?: true,
            pageTurnAnimation = p[Keys.PAGE_TURN_ANIM] ?: true,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            ttsSpeed = p[Keys.TTS_SPEED] ?: 1.0f,
            ttsPitch = p[Keys.TTS_PITCH] ?: 1.0f,
            ttsVoiceUri = p[Keys.TTS_VOICE_URI],
            viewModeGrid = p[Keys.VIEW_MODE_GRID] ?: true,
            gridColumns = p[Keys.GRID_COLUMNS] ?: 3,
            sortOption = p[Keys.SORT_OPTION]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() } ?: SortOption.RECENTLY_READ,
            sortAscending = p[Keys.SORT_ASCENDING] ?: false,
            readingGoalMinutesPerDay = p[Keys.READING_GOAL],
            showReadingGoal = p[Keys.SHOW_READING_GOAL] ?: true,
        )
    }

    suspend fun update(transform: (ReaderPreferences) -> ReaderPreferences) {
        val latest = toPrefs(context.dataStore.data.first())
        val next = transform(latest)
        context.dataStore.edit { p ->
            p[Keys.THEME] = next.theme.name
            p[Keys.FONT] = next.font.name
            p[Keys.FONT_SIZE] = next.fontSize
            p[Keys.LINE_HEIGHT] = next.lineHeight
            p[Keys.MARGIN] = next.margin
            p[Keys.ALIGN] = next.align.name
            p[Keys.READING_MODE] = next.readingMode.name
            p[Keys.HYPHENATION] = next.hyphenation
            p[Keys.PAGE_BOTTOM_MARGIN] = next.pageBottomMargin
            p[Keys.PAGE_TURN_ANIM] = next.pageTurnAnimation
            p[Keys.KEEP_SCREEN_ON] = next.keepScreenOn
            p[Keys.TTS_SPEED] = next.ttsSpeed
            p[Keys.TTS_PITCH] = next.ttsPitch
            next.ttsVoiceUri?.let { p[Keys.TTS_VOICE_URI] = it }
            p[Keys.VIEW_MODE_GRID] = next.viewModeGrid
            p[Keys.GRID_COLUMNS] = next.gridColumns
            p[Keys.SORT_OPTION] = next.sortOption.name
            p[Keys.SORT_ASCENDING] = next.sortAscending
            next.readingGoalMinutesPerDay?.let { p[Keys.READING_GOAL] = it }
            p[Keys.SHOW_READING_GOAL] = next.showReadingGoal
        }
    }

    private fun toPrefs(p: androidx.datastore.preferences.core.Preferences): ReaderPreferences = ReaderPreferences(
        theme = p[Keys.THEME]?.let { runCatching { ThemeId.valueOf(it) }.getOrNull() } ?: ThemeId.IVORY,
        font = p[Keys.FONT]?.let { runCatching { FontId.valueOf(it) }.getOrNull() } ?: FontId.SERIF,
        fontSize = p[Keys.FONT_SIZE] ?: 32,
        lineHeight = p[Keys.LINE_HEIGHT] ?: 1.6f,
        margin = p[Keys.MARGIN] ?: 40,
        align = p[Keys.ALIGN]?.let { runCatching { AlignOption.valueOf(it) }.getOrNull() } ?: AlignOption.LEFT,
        readingMode = p[Keys.READING_MODE]?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() } ?: ReadingMode.VERTICAL,
        hyphenation = p[Keys.HYPHENATION] ?: false,
        pageBottomMargin = p[Keys.PAGE_BOTTOM_MARGIN] ?: true,
        pageTurnAnimation = p[Keys.PAGE_TURN_ANIM] ?: true,
        keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
        ttsSpeed = p[Keys.TTS_SPEED] ?: 1.0f,
        ttsPitch = p[Keys.TTS_PITCH] ?: 1.0f,
        ttsVoiceUri = p[Keys.TTS_VOICE_URI],
        viewModeGrid = p[Keys.VIEW_MODE_GRID] ?: true,
        gridColumns = p[Keys.GRID_COLUMNS] ?: 3,
        sortOption = p[Keys.SORT_OPTION]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() } ?: SortOption.RECENTLY_READ,
        sortAscending = p[Keys.SORT_ASCENDING] ?: false,
        readingGoalMinutesPerDay = p[Keys.READING_GOAL],
        showReadingGoal = p[Keys.SHOW_READING_GOAL] ?: true,
    )

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = intPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val MARGIN = intPreferencesKey("margin")
        val ALIGN = stringPreferencesKey("align")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val HYPHENATION = booleanPreferencesKey("hyphenation")
        val PAGE_BOTTOM_MARGIN = booleanPreferencesKey("page_bottom_margin")
        val PAGE_TURN_ANIM = booleanPreferencesKey("page_turn_animation")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_VOICE_URI = stringPreferencesKey("tts_voice_uri")
        val VIEW_MODE_GRID = booleanPreferencesKey("view_mode_grid")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val READING_GOAL = intPreferencesKey("reading_goal_minutes")
        val SHOW_READING_GOAL = booleanPreferencesKey("show_reading_goal")
    }
}
