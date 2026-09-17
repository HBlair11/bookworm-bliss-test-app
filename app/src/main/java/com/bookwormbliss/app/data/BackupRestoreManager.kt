package com.bookwormbliss.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Full library backup and restore as JSON.
 *
 * Exports all books, bookmarks, highlights, reading sessions, collections
 * (with their book-collection refs), dictionary history, and TTS settings
 * from the Room database into a single JSON file. Import supports two modes:
 * - [ImportMode.MERGE]: add new books, update existing books by checksum
 *   (matching [BookEntity.checksum]); child rows (bookmarks, highlights, etc.)
 *   are re-parented to the new/existing book id.
 * - [ImportMode.REPLACE]: clear every table, then insert all imported rows.
 *
 * Preferences (SharedPreferences via [PrefsManager]) are exported as a flat
 * key-value map under the "preferences" key and restored on import.
 */
class BackupRestoreManager(
    private val context: Context,
    private val database: AppDatabase,
    private val prefsManager: PrefsManager,
) {

    /** Progress callback invoked during long-running export/import operations. */
    interface ProgressCallback {
        /**
         * @param phase human-readable label, e.g. "Exporting books"
         * @param current 0-based index of the item being processed
         * @param total total number of items in this phase
         */
        fun onProgress(phase: String, current: Int, total: Int)

        /** Called once when the operation completes (success or failure). */
        fun onComplete(success: Boolean, message: String) {}
    }

    enum class ImportMode { MERGE, REPLACE }

    companion object {
        private const val BACKUP_VERSION = 2
        private const val BACKUP_DIR = "backups"
        private const val BACKUP_PREFIX = "bookwormbliss_backup_"
        private const val BACKUP_SUFFIX = ".json"

        private const val PREFS_BACKUP_NAME = "epub_prefs"
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    /**
     * Export the full library to a JSON file in the app's private storage.
     *
     * @param callback optional progress listener
     * @return the [File] the backup was written to
     */
    suspend fun exportToFile(callback: ProgressCallback? = null): File = withContext(Dispatchers.IO) {
        val json = buildExportJson(callback)
        val backupDir = File(context.filesDir, BACKUP_DIR).apply { if (!exists()) mkdirs() }
        val timestamp = System.currentTimeMillis()
        val backupFile = File(backupDir, "$BACKUP_PREFIX$timestamp$BACKUP_SUFFIX")
        backupFile.writeText(json.toString(2))
        callback?.onComplete(true, "Export complete: ${backupFile.name}")
        backupFile
    }

    /** Build the complete export JSON object from the database. */
    private suspend fun buildExportJson(callback: ProgressCallback? = null): JSONObject {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        // Books
        val books = database.bookDao().getAllBooks()
        val booksArray = JSONArray()
        books.forEachIndexed { i, book ->
            callback?.onProgress("Exporting books", i, books.size)
            booksArray.put(bookToJson(book))
        }
        root.put("books", booksArray)

        // Bookmarks — fetch all from the database via direct cursor
        val allBookmarks = getAllBookmarks()
        val bookmarksArray = JSONArray()
        allBookmarks.forEachIndexed { i, bm ->
            callback?.onProgress("Exporting bookmarks", i, allBookmarks.size)
            bookmarksArray.put(bookmarkToJson(bm))
        }
        root.put("bookmarks", bookmarksArray)

        // Highlights
        val allHighlights = getAllHighlights()
        val highlightsArray = JSONArray()
        allHighlights.forEachIndexed { i, hl ->
            callback?.onProgress("Exporting highlights", i, allHighlights.size)
            highlightsArray.put(highlightToJson(hl))
        }
        root.put("highlights", highlightsArray)

        // Reading sessions
        val allSessions = getAllReadingSessions()
        val sessionsArray = JSONArray()
        allSessions.forEachIndexed { i, s ->
            callback?.onProgress("Exporting sessions", i, allSessions.size)
            sessionsArray.put(readingSessionToJson(s))
        }
        root.put("sessions", sessionsArray)

        // Collections + refs
        val allCollections = getAllCollections()
        val allRefs = getAllCollectionRefs()
        val collectionsArray = JSONArray()
        allCollections.forEachIndexed { i, col ->
            callback?.onProgress("Exporting collections", i, allCollections.size)
            collectionsArray.put(collectionToJson(col))
        }
        root.put("collections", collectionsArray)

        val collectionRefsArray = JSONArray()
        allRefs.forEachIndexed { i, ref ->
            callback?.onProgress("Exporting collection refs", i, allRefs.size)
            collectionRefsArray.put(collectionRefToJson(ref))
        }
        root.put("collection_refs", collectionRefsArray)

        // Dictionary history
        val allDict = database.dictionaryHistoryDao().getAll()
        val dictArray = JSONArray()
        allDict.forEachIndexed { i, entry ->
            callback?.onProgress("Exporting dictionary history", i, allDict.size)
            dictArray.put(dictionaryHistoryToJson(entry))
        }
        root.put("dictionary_history", dictArray)

        // TTS settings — query all via raw SQL since the DAO only supports per-book lookups
        val allTts = getAllTtsSettings()
        val ttsArray = JSONArray()
        allTts.forEachIndexed { i, tts ->
            callback?.onProgress("Exporting TTS settings", i, allTts.size)
            ttsArray.put(ttsSettingsToJson(tts))
        }
        root.put("tts_settings", ttsArray)

        // Preferences
        val prefsJson = exportPreferences()
        root.put("preferences", prefsJson)

        return root
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    /**
     * Import a library backup from a JSON [file].
     *
     * @param mode [ImportMode.MERGE] to add/update without removing existing
     *   data, or [ImportMode.REPLACE] to wipe all tables first.
     * @param callback optional progress listener
     */
    suspend fun importFromFile(file: File, mode: ImportMode, callback: ProgressCallback? = null) = withContext(Dispatchers.IO) {
        try {
            val rawText = file.readText()
            val json = JSONObject(rawText)
            val version = json.optInt("version", 1)

            if (mode == ImportMode.REPLACE) {
                clearAllTables()
            }

            // Books — build a map from original book id to (possibly new) book id
            val bookIdMap = mutableMapOf<Long, Long>()
            val booksArray = json.optJSONArray("books") ?: JSONArray()
            for (i in 0 until booksArray.length()) {
                callback?.onProgress("Importing books", i, booksArray.length())
                val bookObj = booksArray.getJSONObject(i)
                val originalId = bookObj.optLong("id", 0)
                val book = jsonToBook(bookObj)
                val newId = if (mode == ImportMode.MERGE) {
                    // Match by checksum; if found, update; otherwise insert
                    val existing = database.bookDao().getByChecksum(book.checksum)
                    if (existing != null) {
                        database.bookDao().update(book.copy(id = existing.id))
                        existing.id
                    } else {
                        database.bookDao().insert(book.copy(id = 0))
                    }
                } else {
                    database.bookDao().insert(book.copy(id = 0))
                }
                if (originalId != 0L) {
                    bookIdMap[originalId] = newId
                }
            }

            // Bookmarks
            val bookmarksArray = json.optJSONArray("bookmarks") ?: JSONArray()
            for (i in 0 until bookmarksArray.length()) {
                callback?.onProgress("Importing bookmarks", i, bookmarksArray.length())
                val bmObj = bookmarksArray.getJSONObject(i)
                val originalBookId = bmObj.optLong("book_id", 0)
                val mappedBookId = bookIdMap[originalBookId] ?: originalBookId
                if (mappedBookId == 0L) continue
                val bookmark = jsonToBookmark(bmObj, mappedBookId)
                database.bookmarkDao().insert(bookmark.copy(id = 0))
            }

            // Highlights
            val highlightsArray = json.optJSONArray("highlights") ?: JSONArray()
            for (i in 0 until highlightsArray.length()) {
                callback?.onProgress("Importing highlights", i, highlightsArray.length())
                val hlObj = highlightsArray.getJSONObject(i)
                val originalBookId = hlObj.optLong("book_id", 0)
                val mappedBookId = bookIdMap[originalBookId] ?: originalBookId
                if (mappedBookId == 0L) continue
                val highlight = jsonToHighlight(hlObj, mappedBookId)
                database.highlightDao().insert(highlight.copy(id = 0))
            }

            // Reading sessions
            val sessionsArray = json.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until sessionsArray.length()) {
                callback?.onProgress("Importing sessions", i, sessionsArray.length())
                val sObj = sessionsArray.getJSONObject(i)
                val originalBookId = sObj.optLong("book_id", 0)
                val mappedBookId = bookIdMap[originalBookId] ?: originalBookId
                if (mappedBookId == 0L) continue
                val session = jsonToReadingSession(sObj, mappedBookId)
                database.readingSessionDao().insert(session.copy(id = 0))
            }

            // Collections + refs
            val collectionsArray = json.optJSONArray("collections") ?: JSONArray()
            val collectionIdMap = mutableMapOf<Long, Long>()
            for (i in 0 until collectionsArray.length()) {
                callback?.onProgress("Importing collections", i, collectionsArray.length())
                val colObj = collectionsArray.getJSONObject(i)
                val originalColId = colObj.optLong("id", 0)
                val colName = colObj.optString("name")
                val existingColId = database.collectionDao().getIdByName(colName)
                val newColId = if (existingColId != null) {
                    existingColId
                } else {
                    database.collectionDao().insert(
                        CollectionEntity(name = colName, createdAt = colObj.optLong("created_at", System.currentTimeMillis()))
                    )
                }
                if (originalColId != 0L) {
                    collectionIdMap[originalColId] = newColId
                }
            }

            val refsArray = json.optJSONArray("collection_refs") ?: JSONArray()
            for (i in 0 until refsArray.length()) {
                callback?.onProgress("Importing collection refs", i, refsArray.length())
                val refObj = refsArray.getJSONObject(i)
                val originalBookId = refObj.optLong("book_id", 0)
                val originalColId = refObj.optLong("collection_id", 0)
                val mappedBookId = bookIdMap[originalBookId] ?: originalBookId
                val mappedColId = collectionIdMap[originalColId] ?: originalColId
                if (mappedBookId == 0L || mappedColId == 0L) continue
                database.collectionDao().addBook(BookCollectionRef(mappedBookId, mappedColId))
            }

            // Dictionary history
            val dictArray = json.optJSONArray("dictionary_history") ?: JSONArray()
            for (i in 0 until dictArray.length()) {
                callback?.onProgress("Importing dictionary history", i, dictArray.length())
                val dObj = dictArray.getJSONObject(i)
                val entry = jsonToDictionaryHistory(dObj)
                val existing = database.dictionaryHistoryDao().find(entry.word)
                if (existing != null && mode == ImportMode.MERGE) {
                    database.dictionaryHistoryDao().refresh(
                        existing.id, entry.definition, entry.partOfSpeech, entry.bookId, entry.lookedUpAt
                    )
                } else {
                    database.dictionaryHistoryDao().insert(entry.copy(id = 0))
                }
            }

            // TTS settings
            val ttsArray = json.optJSONArray("tts_settings") ?: JSONArray()
            for (i in 0 until ttsArray.length()) {
                callback?.onProgress("Importing TTS settings", i, ttsArray.length())
                val tObj = ttsArray.getJSONObject(i)
                val originalBookId = tObj.optLong("book_id", 0)
                val mappedBookId = bookIdMap[originalBookId] ?: originalBookId
                if (mappedBookId == 0L) continue
                val tts = jsonToTtsSettings(tObj, mappedBookId)
                database.ttsSettingsDao().upsert(tts)
            }

            // Preferences
            val prefsObj = json.optJSONObject("preferences")
            if (prefsObj != null) {
                importPreferences(prefsObj)
            }

            callback?.onComplete(true, "Import complete")
        } catch (e: Exception) {
            callback?.onComplete(false, "Import failed: ${e.message}")
            throw IOException("Failed to import backup", e)
        }
    }

    // ------------------------------------------------------------------
    // Table clearing for REPLACE mode
    // ------------------------------------------------------------------

    private suspend fun clearAllTables() {
        // Clear in dependency order: children first, then parents.
        // Using direct execSQL on the writable database avoids the need for
        // runBlocking inside a transaction.
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM bookmarks")
            db.execSQL("DELETE FROM highlights")
            db.execSQL("DELETE FROM reading_sessions")
            db.execSQL("DELETE FROM book_collection_ref")
            db.execSQL("DELETE FROM collections")
            db.execSQL("DELETE FROM dictionary_history")
            db.execSQL("DELETE FROM tts_settings")
            db.execSQL("DELETE FROM books")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------------------
    // Raw queries for bulk fetch (the DAOs don't expose "get all" for
    // every table — bookmarks, highlights, reading sessions, collections,
    // collection refs, and TTS settings)
    // ------------------------------------------------------------------

    private suspend fun getAllBookmarks(): List<BookmarkEntity> {
        val cursor = database.openHelper.readableDatabase.query("SELECT * FROM bookmarks ORDER BY id")
        return cursor.use {
            val result = mutableListOf<BookmarkEntity>()
            while (it.moveToNext()) {
                result.add(BookmarkEntity(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    bookId = it.getLong(it.getColumnIndexOrThrow("book_id")),
                    spineIndex = it.getInt(it.getColumnIndexOrThrow("spine_index")),
                    scrollRatio = it.getFloat(it.getColumnIndexOrThrow("scroll_ratio")),
                    pageInChapter = it.getInt(it.getColumnIndexOrThrow("page_in_chapter")),
                    chapterTitle = it.getString(it.getColumnIndexOrThrow("chapter_title")) ?: "",
                    snippet = it.getString(it.getColumnIndexOrThrow("snippet")) ?: "",
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                    bookmarkType = it.getInt(it.getColumnIndexOrThrow("bookmark_type")),
                ))
            }
            result
        }
    }

    private suspend fun getAllHighlights(): List<HighlightEntity> {
        val cursor = database.openHelper.readableDatabase.query("SELECT * FROM highlights ORDER BY id")
        return cursor.use {
            val result = mutableListOf<HighlightEntity>()
            while (it.moveToNext()) {
                result.add(HighlightEntity(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    bookId = it.getLong(it.getColumnIndexOrThrow("book_id")),
                    spineHref = it.getString(it.getColumnIndexOrThrow("spine_href")) ?: "",
                    text = it.getString(it.getColumnIndexOrThrow("text")) ?: "",
                    note = it.getString(it.getColumnIndexOrThrow("note")),
                    color = it.getInt(it.getColumnIndexOrThrow("color")),
                    prefix = it.getString(it.getColumnIndexOrThrow("prefix")) ?: "",
                    suffix = it.getString(it.getColumnIndexOrThrow("suffix")) ?: "",
                    startPath = it.getString(it.getColumnIndexOrThrow("start_path")) ?: "",
                    endPath = it.getString(it.getColumnIndexOrThrow("end_path")) ?: "",
                    startOffset = it.getInt(it.getColumnIndexOrThrow("start_offset")),
                    endOffset = it.getInt(it.getColumnIndexOrThrow("end_offset")),
                    normalizedStart = it.getInt(it.getColumnIndexOrThrow("normalized_start")),
                    normalizedEnd = it.getInt(it.getColumnIndexOrThrow("normalized_end")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                ))
            }
            result
        }
    }

    private suspend fun getAllReadingSessions(): List<ReadingSessionEntity> {
        val cursor = database.openHelper.readableDatabase.query("SELECT * FROM reading_sessions ORDER BY id")
        return cursor.use {
            val result = mutableListOf<ReadingSessionEntity>()
            while (it.moveToNext()) {
                result.add(ReadingSessionEntity(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    bookId = it.getLong(it.getColumnIndexOrThrow("book_id")),
                    startedAt = it.getLong(it.getColumnIndexOrThrow("started_at")),
                    endedAt = it.getLong(it.getColumnIndexOrThrow("ended_at")),
                    activeSeconds = it.getInt(it.getColumnIndexOrThrow("active_seconds")),
                    chaptersAdvanced = it.getInt(it.getColumnIndexOrThrow("chapters_advanced")),
                    pagesAdvanced = it.getInt(it.getColumnIndexOrThrow("pages_advanced")),
                ))
            }
            result
        }
    }

    private suspend fun getAllCollections(): List<CollectionEntity> {
        val cursor = database.openHelper.readableDatabase.query("SELECT * FROM collections ORDER BY id")
        return cursor.use {
            val result = mutableListOf<CollectionEntity>()
            while (it.moveToNext()) {
                result.add(CollectionEntity(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                ))
            }
            result
        }
    }

    private suspend fun getAllCollectionRefs(): List<BookCollectionRef> {
        val cursor = database.openHelper.readableDatabase.query("SELECT * FROM book_collection_ref")
        return cursor.use {
            val result = mutableListOf<BookCollectionRef>()
            while (it.moveToNext()) {
                result.add(BookCollectionRef(
                    bookId = it.getLong(it.getColumnIndexOrThrow("book_id")),
                    collectionId = it.getLong(it.getColumnIndexOrThrow("collection_id")),
                ))
            }
            result
        }
    }

    private suspend fun getAllTtsSettings(): List<TtsSettingsEntity> {
        val cursor = database.openHelper.readableDatabase.query("SELECT * FROM tts_settings")
        return cursor.use {
            val result = mutableListOf<TtsSettingsEntity>()
            while (it.moveToNext()) {
                result.add(TtsSettingsEntity(
                    bookId = it.getLong(it.getColumnIndexOrThrow("book_id")),
                    speechRate = it.getFloat(it.getColumnIndexOrThrow("speech_rate")),
                    pitch = it.getFloat(it.getColumnIndexOrThrow("pitch")),
                    voiceName = it.getString(it.getColumnIndexOrThrow("voice_name")),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at")),
                ))
            }
            result
        }
    }

    // ------------------------------------------------------------------
    // JSON serialization
    // ------------------------------------------------------------------

    private fun bookToJson(book: BookEntity): JSONObject = JSONObject().apply {
        put("id", book.id)
        put("title", book.title)
        put("author", book.author)
        put("path", book.path)
        put("cover_path", book.coverPath ?: JSONObject.NULL)
        put("progress", book.progress.toDouble())
        put("series", book.series ?: JSONObject.NULL)
        put("series_index", book.seriesIndex ?: JSONObject.NULL)
        put("language", book.language ?: JSONObject.NULL)
        put("publisher", book.publisher ?: JSONObject.NULL)
        put("description", book.description ?: JSONObject.NULL)
        put("identifier", book.identifier ?: JSONObject.NULL)
        put("publish_year", book.publishYear ?: JSONObject.NULL)
        put("subject_tags", book.subjectTags ?: JSONObject.NULL)
        put("metadata_edited", book.metadataEdited)
        put("is_currently_reading", book.isCurrentlyReading)
        put("added_date", book.addedDate)
        put("modified_date", book.modifiedDate)
        put("last_opened_date", book.lastOpenedDate ?: JSONObject.NULL)
        put("current_location", book.currentLocation ?: JSONObject.NULL)
        put("file_size", book.fileSize)
        put("spine_index", book.spineIndex)
        put("spine_count", book.spineCount)
        put("chapter_count", book.chapterCount)
        put("chapter_index", book.chapterIndex)
        put("scroll_ratio", book.scrollRatio.toDouble())
        put("checksum", book.checksum)
        put("sort_title", book.sortTitle)
        put("sort_author", book.sortAuthor)
        put("is_favorite", book.isFavorite)
        put("source_uri", book.sourceUri ?: JSONObject.NULL)
        put("source_filename", book.sourceFilename ?: JSONObject.NULL)
        put("source_last_modified", book.sourceLastModified)
        put("page_map_csv", book.pageMapCsv ?: JSONObject.NULL)
        put("screen_page_map_csv", book.screenPageMapCsv ?: JSONObject.NULL)
        put("screen_page_layout_key", book.screenPageLayoutKey ?: JSONObject.NULL)
    }

    private fun jsonToBook(obj: JSONObject): BookEntity = BookEntity(
        id = 0, // always insert fresh
        title = obj.optString("title", ""),
        author = obj.optString("author", ""),
        path = obj.optString("path", ""),
        coverPath = obj.optString("cover_path", null as String?),
        progress = obj.optDouble("progress", 0.0).toFloat(),
        series = obj.optString("series", null as String?),
        seriesIndex = obj.optDouble("series_index", Double.NaN).let { if (it.isNaN()) null else it },
        language = obj.optString("language", null as String?),
        publisher = obj.optString("publisher", null as String?),
        description = obj.optString("description", null as String?),
        identifier = obj.optString("identifier", null as String?),
        publishYear = obj.optInt("publish_year", -1).let { if (it == -1) null else it },
        subjectTags = obj.optString("subject_tags", null as String?),
        metadataEdited = obj.optBoolean("metadata_edited", false),
        isCurrentlyReading = obj.optBoolean("is_currently_reading", false),
        addedDate = obj.optLong("added_date", System.currentTimeMillis()),
        modifiedDate = obj.optLong("modified_date", System.currentTimeMillis()),
        lastOpenedDate = obj.optLong("last_opened_date", -1).let { if (it == -1L) null else it },
        currentLocation = obj.optString("current_location", null as String?),
        fileSize = obj.optLong("file_size", 0),
        spineIndex = obj.optInt("spine_index", 0),
        spineCount = obj.optInt("spine_count", 0),
        chapterCount = obj.optInt("chapter_count", 0),
        chapterIndex = obj.optInt("chapter_index", 0),
        scrollRatio = obj.optDouble("scroll_ratio", 0.0).toFloat(),
        checksum = obj.optString("checksum", ""),
        sortTitle = obj.optString("sort_title", obj.optString("title", "")),
        sortAuthor = obj.optString("sort_author", obj.optString("author", "")),
        isFavorite = obj.optBoolean("is_favorite", false),
        sourceUri = obj.optString("source_uri", null as String?),
        sourceFilename = obj.optString("source_filename", null as String?),
        sourceLastModified = obj.optLong("source_last_modified", 0),
        pageMapCsv = obj.optString("page_map_csv", null as String?),
        screenPageMapCsv = obj.optString("screen_page_map_csv", null as String?),
        screenPageLayoutKey = obj.optString("screen_page_layout_key", null as String?),
    )

    private fun bookmarkToJson(bm: BookmarkEntity): JSONObject = JSONObject().apply {
        put("id", bm.id)
        put("book_id", bm.bookId)
        put("spine_index", bm.spineIndex)
        put("scroll_ratio", bm.scrollRatio.toDouble())
        put("page_in_chapter", bm.pageInChapter)
        put("chapter_title", bm.chapterTitle)
        put("snippet", bm.snippet)
        put("created_at", bm.createdAt)
        put("bookmark_type", bm.bookmarkType)
    }

    private fun jsonToBookmark(obj: JSONObject, bookId: Long): BookmarkEntity = BookmarkEntity(
        id = 0,
        bookId = bookId,
        spineIndex = obj.optInt("spine_index", 0),
        scrollRatio = obj.optDouble("scroll_ratio", 0.0).toFloat(),
        pageInChapter = obj.optInt("page_in_chapter", -1),
        chapterTitle = obj.optString("chapter_title", ""),
        snippet = obj.optString("snippet", ""),
        createdAt = obj.optLong("created_at", System.currentTimeMillis()),
        bookmarkType = obj.optInt("bookmark_type", BookmarkEntity.TYPE_WHOLE_PAGE),
    )

    private fun highlightToJson(hl: HighlightEntity): JSONObject = JSONObject().apply {
        put("id", hl.id)
        put("book_id", hl.bookId)
        put("spine_href", hl.spineHref)
        put("text", hl.text)
        put("note", hl.note ?: JSONObject.NULL)
        put("color", hl.color)
        put("prefix", hl.prefix)
        put("suffix", hl.suffix)
        put("start_path", hl.startPath)
        put("end_path", hl.endPath)
        put("start_offset", hl.startOffset)
        put("end_offset", hl.endOffset)
        put("normalized_start", hl.normalizedStart)
        put("normalized_end", hl.normalizedEnd)
        put("created_at", hl.createdAt)
    }

    private fun jsonToHighlight(obj: JSONObject, bookId: Long): HighlightEntity = HighlightEntity(
        id = 0,
        bookId = bookId,
        spineHref = obj.optString("spine_href", ""),
        text = obj.optString("text", ""),
        note = obj.optString("note", null as String?),
        color = obj.optInt("color", 0),
        prefix = obj.optString("prefix", ""),
        suffix = obj.optString("suffix", ""),
        startPath = obj.optString("start_path", ""),
        endPath = obj.optString("end_path", ""),
        startOffset = obj.optInt("start_offset", 0),
        endOffset = obj.optInt("end_offset", 0),
        normalizedStart = obj.optInt("normalized_start", 0),
        normalizedEnd = obj.optInt("normalized_end", 0),
        createdAt = obj.optLong("created_at", System.currentTimeMillis()),
    )

    private fun readingSessionToJson(s: ReadingSessionEntity): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("book_id", s.bookId)
        put("started_at", s.startedAt)
        put("ended_at", s.endedAt)
        put("active_seconds", s.activeSeconds)
        put("chapters_advanced", s.chaptersAdvanced)
        put("pages_advanced", s.pagesAdvanced)
    }

    private fun jsonToReadingSession(obj: JSONObject, bookId: Long): ReadingSessionEntity = ReadingSessionEntity(
        id = 0,
        bookId = bookId,
        startedAt = obj.optLong("started_at", System.currentTimeMillis()),
        endedAt = obj.optLong("ended_at", System.currentTimeMillis()),
        activeSeconds = obj.optInt("active_seconds", 0),
        chaptersAdvanced = obj.optInt("chapters_advanced", 0),
        pagesAdvanced = obj.optInt("pages_advanced", 0),
    )

    private fun collectionToJson(col: CollectionEntity): JSONObject = JSONObject().apply {
        put("id", col.id)
        put("name", col.name)
        put("created_at", col.createdAt)
    }

    private fun collectionRefToJson(ref: BookCollectionRef): JSONObject = JSONObject().apply {
        put("book_id", ref.bookId)
        put("collection_id", ref.collectionId)
    }

    private fun dictionaryHistoryToJson(entry: DictionaryHistoryEntity): JSONObject = JSONObject().apply {
        put("id", entry.id)
        put("word", entry.word)
        put("definition", entry.definition ?: JSONObject.NULL)
        put("part_of_speech", entry.partOfSpeech ?: JSONObject.NULL)
        put("book_id", entry.bookId ?: JSONObject.NULL)
        put("looked_up_at", entry.lookedUpAt)
    }

    private fun jsonToDictionaryHistory(obj: JSONObject): DictionaryHistoryEntity = DictionaryHistoryEntity(
        id = 0,
        word = obj.optString("word", ""),
        definition = obj.optString("definition", null as String?),
        partOfSpeech = obj.optString("part_of_speech", null as String?),
        bookId = if (obj.isNull("book_id")) null else obj.optLong("book_id", 0).let { if (it == 0L) null else it },
        lookedUpAt = obj.optLong("looked_up_at", System.currentTimeMillis()),
    )

    private fun ttsSettingsToJson(tts: TtsSettingsEntity): JSONObject = JSONObject().apply {
        put("book_id", tts.bookId)
        put("speech_rate", tts.speechRate.toDouble())
        put("pitch", tts.pitch.toDouble())
        put("voice_name", tts.voiceName ?: JSONObject.NULL)
        put("updated_at", tts.updatedAt)
    }

    private fun jsonToTtsSettings(obj: JSONObject, bookId: Long): TtsSettingsEntity = TtsSettingsEntity(
        bookId = bookId,
        speechRate = obj.optDouble("speech_rate", 1.0).toFloat(),
        pitch = obj.optDouble("pitch", 1.0).toFloat(),
        voiceName = obj.optString("voice_name", null as String?),
        updatedAt = obj.optLong("updated_at", System.currentTimeMillis()),
    )

    // ------------------------------------------------------------------
    // SharedPreferences export/import
    // ------------------------------------------------------------------

    private fun exportPreferences(): JSONObject {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_BACKUP_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        prefs.all.forEach { (key, value) ->
            json.put(key, value)
        }
        return json
    }

    private fun importPreferences(json: JSONObject) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_BACKUP_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                else -> editor.putString(key, value.toString())
            }
        }
        editor.apply()
    }
}
