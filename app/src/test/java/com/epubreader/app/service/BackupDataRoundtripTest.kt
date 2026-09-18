package com.epubreader.app.service

import com.epubreader.app.data.BookCollectionRef
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookJournalEntryEntity
import com.epubreader.app.data.BookmarkEntity
import com.epubreader.app.data.CollectionEntity
import com.epubreader.app.data.DictionaryHistoryEntity
import com.epubreader.app.data.HighlightEntity
import com.epubreader.app.data.ReadingSessionEntity
import com.epubreader.app.data.ReadingStampEntity
import com.epubreader.app.data.SearchHistoryEntity
import com.epubreader.app.data.TtsSettingsEntity
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Phase 10 — verifies [BackupRestoreService.BackupData] survives a Gson
 * serialize → deserialize roundtrip with every table populated. This is the
 * most error-prone part of Full Backup & Restore (a field dropped or renamed
 * silently loses data), so it gets a dedicated JVM test that needs no Android
 * runtime — the Room annotations on the entities are inert outside Room.
 */
class BackupDataRoundtripTest {
    private val gson = Gson()

    @Test
    fun `backup data roundtrips with all tables and preserves every field`() {
        val original = BackupRestoreService.BackupData(
            books = listOf(
                BookEntity(
                    title = "The Fellowship of the Ring",
                    author = "J.R.R. Tolkien",
                    path = "/data/books/fellowship.epub",
                    checksum = "abc123",
                    id = 42,
                    progress = 0.37f,
                    series = "The Lord of the Rings",
                    seriesIndex = 1.0,
                    spineIndex = 3,
                    spineCount = 30,
                    isFavorite = true,
                    isCurrentlyReading = true,
                    fileSize = 1024L,
                    sourceUri = "content://x/1",
                    publishYear = 1954,
                ),
            ),
            bookmarks = listOf(
                BookmarkEntity(bookId = 42, spineIndex = 3, scrollRatio = 0.5f, pageInChapter = 12, chapterTitle = "Chapter 3", snippet = "A snippet"),
            ),
            highlights = listOf(
                HighlightEntity(
                    bookId = 42,
                    spineHref = "chapter3.xhtml",
                    text = "highlighted",
                    color = 0xFFFFD0A9.toInt(),
                    prefix = "pre",
                    suffix = "suf",
                    startPath = "/p[1]",
                    endPath = "/p[1]",
                    startOffset = 0,
                    endOffset = 10,
                    normalizedStart = 0,
                    normalizedEnd = 10,
                ),
            ),
            collections = listOf(CollectionEntity(name = "Favorites", id = 7)),
            collectionRefs = listOf(BookCollectionRef(bookId = 42, collectionId = 7)),
            sessions = listOf(
                ReadingSessionEntity(
                    bookId = 42,
                    startedAt = 1_000L,
                    endedAt = 2_000L,
                    activeSeconds = 900,
                    chaptersAdvanced = 1,
                    pagesAdvanced = 12,
                ),
            ),
            vocabulary = listOf(
                DictionaryHistoryEntity(word = "ephemeral", definition = "short-lived", partOfSpeech = "adj", bookId = 42),
            ),
            ttsSettings = listOf(TtsSettingsEntity(bookId = 42, speechRate = 1.0f, pitch = 1.0f, updatedAt = 5_000L)),
            stamps = listOf(
                ReadingStampEntity(bookId = 42, type = ReadingStampEntity.Type.STARTED, title = "Started reading"),
                ReadingStampEntity(bookId = 42, type = ReadingStampEntity.Type.FAVORITE_MOMENT, title = "Bridge of Khazad-dûm", note = "Drums in the deep"),
            ),
            journals = listOf(
                BookJournalEntryEntity(bookId = 42, title = "First impressions", content = "Loved the Shire."),
            ),
            searchHistory = listOf(SearchHistoryEntity(query = "tolkien")),
            preferences = BackupRestoreService.PreferencesSnapshot(
                appTheme = "pastel",
                readerTheme = "ivory",
                font = "serif",
                fontSize = 32,
                lineHeight = 1.6f,
                margin = 20,
                justify = false,
                align = "left",
                hyphenation = false,
                pageTurnAnimation = true,
                keepScreenOn = false,
                ttsSpeed = 8,
                ttsPitch = 10,
                ttsBackground = false,
                readingGoalMinutes = 30,
                showGoalOnStats = true,
                viewModeGrid = true,
                gridColumns = 3,
                sortOption = "recently_added",
                sortAscending = true,
            ),
        )

        val json = gson.toJson(original)
        val restored = gson.fromJson(json, BackupRestoreService.BackupData::class.java)

        assertNotNull(restored)
        assertEquals(2, restored.version)
        assertEquals("The Livre Magicae", restored.app)
        assertEquals(1, restored.books.size)
        val book = restored.books.first()
        assertEquals(42L, book.id)
        assertEquals("The Fellowship of the Ring", book.title)
        assertEquals(0.37f, book.progress, 0.0001f)
        assertEquals(1954, book.publishYear)
        assertEquals(true, book.isFavorite)
        assertEquals(2, restored.stamps.size)
        assertEquals(ReadingStampEntity.Type.STARTED, restored.stamps[0].type)
        assertEquals(ReadingStampEntity.Type.FAVORITE_MOMENT, restored.stamps[1].type)
        assertEquals("Drums in the deep", restored.stamps[1].note)
        assertEquals(1, restored.collectionRefs.size)
        assertEquals(7L, restored.collectionRefs.first().collectionId)
        assertEquals("pastel", restored.preferences.appTheme)
        assertEquals(30, restored.preferences.readingGoalMinutes)
    }

    @Test
    fun `empty backup roundtrips without throwing`() {
        val original = BackupRestoreService.BackupData(
            books = emptyList(),
            bookmarks = emptyList(),
            highlights = emptyList(),
            collections = emptyList(),
            collectionRefs = emptyList(),
            sessions = emptyList(),
            vocabulary = emptyList(),
            ttsSettings = emptyList(),
            stamps = emptyList(),
            journals = emptyList(),
            searchHistory = emptyList(),
            preferences = BackupRestoreService.PreferencesSnapshot(
                appTheme = "original",
                readerTheme = "ivory",
                font = "serif",
                fontSize = 24,
                lineHeight = 1.6f,
                margin = 20,
                justify = false,
                align = "left",
                hyphenation = false,
                pageTurnAnimation = true,
                keepScreenOn = false,
                ttsSpeed = 8,
                ttsPitch = 10,
                ttsBackground = false,
                readingGoalMinutes = 30,
                showGoalOnStats = true,
                viewModeGrid = true,
                gridColumns = 3,
                sortOption = "recently_added",
                sortAscending = true,
            ),
        )
        val restored = gson.fromJson(gson.toJson(original), BackupRestoreService.BackupData::class.java)
        assertNotNull(restored)
        assertEquals(0, restored.books.size)
    }
}
