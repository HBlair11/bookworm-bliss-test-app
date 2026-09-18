package com.epubreader.app.service

import android.content.Context
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.BookCollectionRef
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookJournalEntryEntity
import com.epubreader.app.data.BookmarkEntity
import com.epubreader.app.data.CollectionEntity
import com.epubreader.app.data.DictionaryHistoryEntity
import com.epubreader.app.data.HighlightEntity
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.data.ReadingSessionEntity
import com.epubreader.app.data.ReadingStampEntity
import com.epubreader.app.data.SearchHistoryEntity
import com.epubreader.app.data.TtsSettingsEntity
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 10 — Full Backup & Restore.
 *
 * Translated from the web app's `BackupDataV2` (types.ts). Exports every
 * user-owned table to a single JSON document and restores it with a
 * merge-or-replace mode selector, exactly as the web app's Settings → Full
 * Backup & Restore section does.
 *
 * The service owns ALL JSON/DAO orchestration so [com.epubreader.app.MainActivity]
 * only has to fire an ActivityResult launcher and hand the resulting URI here.
 *
 * Schema versioning: the JSON carries a `version` field (currently 2, matching
 * the web app). Future schema changes bump it and the importer branches on it.
 */
class BackupRestoreService(
    context: Context,
) {
    private val db: AppDatabase = AppDatabase.get(context.applicationContext)
    private val prefs: PrefsManager = PrefsManager(context.applicationContext)
    private val gson = Gson()

    /** Restore mode — mirrors the web app's merge/replace radio. */
    enum class RestoreMode {
        /** Keep existing rows; upsert incoming rows (newest wins on conflict). */
        MERGE,

        /** Wipe every backed-up table first, then insert the incoming rows. */
        REPLACE,
    }

    data class BackupData(
        @SerializedName("version") val version: Int = VERSION,
        @SerializedName("app") val app: String = APP_NAME,
        @SerializedName("exportedAt") val exportedAt: Long = System.currentTimeMillis(),
        @SerializedName("books") val books: List<BookEntity>,
        @SerializedName("bookmarks") val bookmarks: List<BookmarkEntity>,
        @SerializedName("highlights") val highlights: List<HighlightEntity>,
        @SerializedName("collections") val collections: List<CollectionEntity>,
        @SerializedName("collectionRefs") val collectionRefs: List<BookCollectionRef>,
        @SerializedName("sessions") val sessions: List<ReadingSessionEntity>,
        @SerializedName("vocabulary") val vocabulary: List<DictionaryHistoryEntity>,
        @SerializedName("ttsSettings") val ttsSettings: List<TtsSettingsEntity>,
        @SerializedName("stamps") val stamps: List<ReadingStampEntity>,
        @SerializedName("journals") val journals: List<BookJournalEntryEntity>,
        @SerializedName("searchHistory") val searchHistory: List<SearchHistoryEntity>,
        @SerializedName("preferences") val preferences: PreferencesSnapshot,
    )

    /** A serializable snapshot of the user-facing prefs worth backing up. */
    data class PreferencesSnapshot(
        @SerializedName("appTheme") val appTheme: String,
        @SerializedName("readerTheme") val readerTheme: String,
        @SerializedName("font") val font: String,
        @SerializedName("fontSize") val fontSize: Int,
        @SerializedName("lineHeight") val lineHeight: Float,
        @SerializedName("margin") val margin: Int,
        @SerializedName("justify") val justify: Boolean,
        @SerializedName("align") val align: String,
        @SerializedName("hyphenation") val hyphenation: Boolean,
        @SerializedName("pageTurnAnimation") val pageTurnAnimation: Boolean,
        @SerializedName("keepScreenOn") val keepScreenOn: Boolean,
        @SerializedName("ttsSpeed") val ttsSpeed: Int,
        @SerializedName("ttsPitch") val ttsPitch: Int,
        @SerializedName("ttsBackground") val ttsBackground: Boolean,
        @SerializedName("readingGoalMinutes") val readingGoalMinutes: Int,
        @SerializedName("showGoalOnStats") val showGoalOnStats: Boolean,
        @SerializedName("viewModeGrid") val viewModeGrid: Boolean,
        @SerializedName("gridColumns") val gridColumns: Int,
        @SerializedName("sortOption") val sortOption: String,
        @SerializedName("sortAscending") val sortAscending: Boolean,
    )

    /** Serialize the full backup document to a JSON string. */
    suspend fun export(): String = withContext(Dispatchers.IO) {
        val data = BackupData(
            books = db.bookDao().getAllBooks(),
            bookmarks = db.bookmarkDao().getAll(),
            highlights = db.highlightDao().getAll(),
            collections = db.collectionDao().getAll(),
            collectionRefs = db.collectionDao().getAllRefs(),
            sessions = db.readingSessionDao().getAll(),
            vocabulary = db.dictionaryHistoryDao().getAll(),
            ttsSettings = db.ttsSettingsDao().getAll(),
            stamps = db.readingStampDao().getAll(),
            journals = db.bookJournalDao().getAll(),
            searchHistory = db.searchHistoryDao().getAll(),
            preferences = snapshotPreferences(),
        )
        gson.toJson(data)
    }

    /** Deserialize and apply a backup document. Returns a short summary. */
    suspend fun importBackup(json: String, mode: RestoreMode): String = withContext(Dispatchers.IO) {
        val data = gson.fromJson(json, BackupData::class.java)
            ?: throw IllegalArgumentException("Invalid backup file")

        db.withTransaction {
            if (mode == RestoreMode.REPLACE) {
                // Order matters: children before parents so FK cascade doesn't
                // fight us, then re-seed parents.
                db.bookmarkDao().deleteAll()
                db.highlightDao().deleteAll()
                db.readingSessionDao().deleteAll()
                db.dictionaryHistoryDao().clear()
                db.ttsSettingsDao().deleteAll()
                db.readingStampDao().deleteAll()
                db.bookJournalDao().deleteAll()
                db.searchHistoryDao().deleteAll()
                db.collectionDao().deleteAllRefs()
                db.collectionDao().deleteAll()
                db.bookDao().deleteAll()
            }

            data.books.takeIf { it.isNotEmpty() }?.let { db.bookDao().insertAll(it) }
            data.bookmarks.takeIf { it.isNotEmpty() }?.let { db.bookmarkDao().insertAll(it) }
            data.highlights.takeIf { it.isNotEmpty() }?.let { db.highlightDao().insertAll(it) }
            data.collections.takeIf { it.isNotEmpty() }?.let { db.collectionDao().insertAll(it) }
            data.collectionRefs.takeIf { it.isNotEmpty() }?.let { db.collectionDao().addAll(it) }
            data.sessions.takeIf { it.isNotEmpty() }?.let { db.readingSessionDao().insertAll(it) }
            data.vocabulary.takeIf { it.isNotEmpty() }?.let {
                it.forEach { db.dictionaryHistoryDao().insert(it) }
            }
            data.ttsSettings.takeIf { it.isNotEmpty() }?.let { db.ttsSettingsDao().insertAll(it) }
            data.stamps.takeIf { it.isNotEmpty() }?.let { db.readingStampDao().insertAll(it) }
            data.journals.takeIf { it.isNotEmpty() }?.let { db.bookJournalDao().insertAll(it) }
            data.searchHistory.takeIf { it.isNotEmpty() }?.let {
                it.forEach { db.searchHistoryDao().upsert(it) }
            }
            applyPreferences(data.preferences)
        }

        buildString {
            append("${data.books.size} books, ")
            append("${data.bookmarks.size} bookmarks, ")
            append("${data.highlights.size} highlights, ")
            append("${data.stamps.size} stamps, ")
            append("${data.journals.size} journal entries")
        }
    }

    private fun snapshotPreferences(): PreferencesSnapshot = PreferencesSnapshot(
        appTheme = prefs.appTheme,
        readerTheme = prefs.theme,
        font = prefs.font,
        fontSize = prefs.fontSize,
        lineHeight = prefs.lineHeight,
        margin = prefs.margin,
        justify = prefs.justify,
        align = prefs.align,
        hyphenation = prefs.hyphenation,
        pageTurnAnimation = prefs.pageTurnAnimation,
        keepScreenOn = prefs.keepScreenOn,
        ttsSpeed = prefs.ttsSpeedProgress,
        ttsPitch = prefs.ttsPitchProgress,
        ttsBackground = prefs.ttsBackgroundPlayback,
        readingGoalMinutes = prefs.readingGoalMinutes,
        showGoalOnStats = prefs.showReadingGoalOnStats,
        viewModeGrid = prefs.viewModeGrid,
        gridColumns = prefs.gridColumns,
        sortOption = prefs.sortOption,
        sortAscending = prefs.sortAscending,
    )

    private fun applyPreferences(p: PreferencesSnapshot) {
        prefs.appTheme = p.appTheme
        prefs.theme = p.readerTheme
        prefs.font = p.font
        prefs.fontSize = p.fontSize
        prefs.lineHeight = p.lineHeight
        prefs.margin = p.margin
        prefs.justify = p.justify
        prefs.align = p.align
        prefs.hyphenation = p.hyphenation
        prefs.pageTurnAnimation = p.pageTurnAnimation
        prefs.keepScreenOn = p.keepScreenOn
        prefs.ttsSpeedProgress = p.ttsSpeed
        prefs.ttsPitchProgress = p.ttsPitch
        prefs.ttsBackgroundPlayback = p.ttsBackground
        prefs.readingGoalMinutes = p.readingGoalMinutes
        prefs.showReadingGoalOnStats = p.showGoalOnStats
        prefs.viewModeGrid = p.viewModeGrid
        prefs.gridColumns = p.gridColumns
        prefs.sortOption = p.sortOption
        prefs.sortAscending = p.sortAscending
    }

    companion object {
        const val VERSION = 2
        const val APP_NAME = "The Livre Magicae"
        const val MIME_TYPE = "application/json"
        const val FILE_SUFFIX = ".json"
    }
}
