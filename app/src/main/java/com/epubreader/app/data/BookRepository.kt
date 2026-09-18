package com.epubreader.app.data

import android.content.Context
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow

class BookRepository(
    context: Context,
) {
    private val db = AppDatabase.get(context)
    private val bookDao = db.bookDao()
    private val bookmarkDao = db.bookmarkDao()
    private val highlightDao = db.highlightDao()
    private val collectionDao = db.collectionDao()
    private val readingSessionDao = db.readingSessionDao()
    private val readingStampDao = db.readingStampDao()
    private val bookJournalDao = db.bookJournalDao()
    private val searchHistoryDao = db.searchHistoryDao()

    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()

    fun observeHomeRecentlyAdded(): Flow<List<BookEntity>> = bookDao.observeHomeRecentlyAdded()

    fun observeCurrentlyReading(): Flow<List<BookEntity>> = bookDao.observeCurrentlyReading()

    suspend fun setCurrentlyReading(id: Long) = bookDao.setCurrentlyReading(id)

    suspend fun markOpened(id: Long) = bookDao.markOpened(id, System.currentTimeMillis())

    suspend fun updateCurrentLocation(id: Long, location: String?) = bookDao.updateCurrentLocation(id, location)

    suspend fun clearCurrentlyReading(id: Long) = bookDao.clearCurrentlyReading(id)

    fun observeFavorites(): Flow<List<BookEntity>> = bookDao.observeFavorites()

    fun observeFinished(): Flow<List<BookEntity>> = bookDao.observeFinished()

    fun observeToBeRead(): Flow<List<BookEntity>> = bookDao.observeToBeRead()

    fun observeLastOpened(): Flow<BookEntity?> = bookDao.observeLastOpened()

    // Patch 16 (Issue #3): books for the "Recently Added" temp screen.
    fun observeByIds(ids: List<Long>): Flow<List<BookEntity>> =
        if (ids.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else bookDao.observeByIds(ids)

    suspend fun setFavorite(
        id: Long,
        fav: Boolean,
    ) = bookDao.setFavorite(id, fav)

    fun observeAuthors(): Flow<List<GroupedRow>> = bookDao.observeAuthors()

    fun observeSeries(): Flow<List<GroupedRow>> = bookDao.observeSeries()

    fun observeByAuthor(author: String): Flow<List<BookEntity>> = bookDao.observeByAuthor(author)

    fun observeBySeries(series: String): Flow<List<BookEntity>> = bookDao.observeBySeries(series)

    fun search(query: String): Flow<List<BookEntity>> = bookDao.search(query)

    suspend fun getBook(id: Long): BookEntity? = bookDao.getById(id)

    suspend fun getByChecksum(checksum: String): BookEntity? = bookDao.getByChecksum(checksum)

    suspend fun updateProgress(
        id: Long,
        progress: Float,
        spineIndex: Int,
        scrollRatio: Float,
    ) {
        bookDao.updateProgress(id, progress, spineIndex, scrollRatio, System.currentTimeMillis())
    }

    suspend fun deleteBook(book: BookEntity) = bookDao.delete(book)

    // ---- Bookmarks ----
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>> = bookmarkDao.observeForBook(bookId)

    // ---- Highlights ----
    fun observeHighlights(bookId: Long): Flow<List<HighlightEntity>> = highlightDao.observeForBook(bookId)

    suspend fun getHighlightsForChapter(bookId: Long, spineHref: String): List<HighlightEntity> =
        highlightDao.getForChapter(bookId, spineHref)

    suspend fun addHighlight(highlight: HighlightEntity): Long = highlightDao.insert(highlight)

    suspend fun updateHighlightNote(id: Long, note: String?) = highlightDao.updateNote(id, note)

    suspend fun deleteHighlight(highlight: HighlightEntity) = highlightDao.delete(highlight)

    suspend fun addBookmark(b: BookmarkEntity): Long = bookmarkDao.insert(b)

    suspend fun deleteBookmark(b: BookmarkEntity) = bookmarkDao.delete(b)

    suspend fun bookmarkExistsNear(
        bookId: Long,
        spineIndex: Int,
        ratio: Float,
    ): Boolean = bookmarkDao.existsNear(bookId, spineIndex, ratio)


    // ---- Reading statistics ----
    suspend fun addReadingSession(session: ReadingSessionEntity) = readingSessionDao.insert(session)
    suspend fun activeSecondsSince(since: Long): Int = readingSessionDao.activeSecondsSince(since)
    suspend fun chaptersSince(since: Long): Int = readingSessionDao.chaptersSince(since)
    suspend fun pagesSince(since: Long): Int = readingSessionDao.pagesSince(since)
    suspend fun activeDays(): List<String> = readingSessionDao.activeDays()

    // ---- Collections ----
    fun observeCollections(): Flow<List<CollectionEntity>> = collectionDao.observeAll()

    fun observeBooksInCollection(id: Long): Flow<List<BookEntity>> = collectionDao.observeBooksInCollection(id)

    fun observeCollectionsForBook(bookId: Long): Flow<List<CollectionEntity>> =
        collectionDao.observeCollectionsForBook(bookId)

    suspend fun createCollection(name: String): Long {
        collectionDao.getIdByName(name)?.let { return it }
        return collectionDao.insert(CollectionEntity(name = name.trim()))
    }

    suspend fun deleteCollection(c: CollectionEntity) = collectionDao.delete(c)

    suspend fun renameCollection(
        id: Long,
        name: String,
    ) = collectionDao.rename(id, name)

    suspend fun addBookToCollection(
        bookId: Long,
        collectionId: Long,
    ) = collectionDao.addBook(BookCollectionRef(bookId, collectionId))

    suspend fun removeBookFromCollection(
        bookId: Long,
        collectionId: Long,
    ) = collectionDao.removeBook(bookId, collectionId)

    // ---- Phase 10: Reading Nook (stamps + journal) ----

    fun observeStamps(bookId: Long): Flow<List<ReadingStampEntity>> = readingStampDao.observeForBook(bookId)

    suspend fun getStamps(bookId: Long): List<ReadingStampEntity> = readingStampDao.getForBook(bookId)

    suspend fun addStamp(stamp: ReadingStampEntity): Long = readingStampDao.insert(stamp)

    suspend fun updateStamp(stamp: ReadingStampEntity) = readingStampDao.update(stamp)

    suspend fun deleteStamp(stamp: ReadingStampEntity) = readingStampDao.delete(stamp)

    fun observeJournal(bookId: Long): Flow<List<BookJournalEntryEntity>> = bookJournalDao.observeForBook(bookId)

    suspend fun getJournal(bookId: Long): List<BookJournalEntryEntity> = bookJournalDao.getForBook(bookId)

    suspend fun getJournalEntry(id: Long): BookJournalEntryEntity? = bookJournalDao.getById(id)

    suspend fun addJournalEntry(entry: BookJournalEntryEntity): Long = bookJournalDao.insert(entry)

    suspend fun updateJournalEntry(entry: BookJournalEntryEntity) = bookJournalDao.update(entry)

    suspend fun deleteJournalEntry(entry: BookJournalEntryEntity) = bookJournalDao.delete(entry)

    // ---- Phase 10: Search history ----

    fun observeRecentSearches(limit: Int): Flow<List<SearchHistoryEntity>> = searchHistoryDao.observeRecent(limit)

    suspend fun getRecentSearches(limit: Int): List<SearchHistoryEntity> = searchHistoryDao.getRecent(limit)

    suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchHistoryDao.upsert(SearchHistoryEntity(query = trimmed, searchedAt = System.currentTimeMillis()))
    }

    suspend fun clearSearchHistory() = searchHistoryDao.deleteAll()

    // ---- Phase 10: Full Backup & Restore (raw DAO access for the service) ----

    /** Direct DAO access for [com.epubreader.app.service.BackupRestoreService]. */
    val rawBookDao get() = bookDao
    val rawBookmarkDao get() = bookmarkDao
    val rawHighlightDao get() = highlightDao
    val rawReadingSessionDao get() = readingSessionDao
    val rawDictionaryHistoryDao get() = db.dictionaryHistoryDao()
    val rawReadingStampDao get() = readingStampDao
    val rawBookJournalDao get() = bookJournalDao
    val rawSearchHistoryDao get() = searchHistoryDao
    val rawCollectionDao get() = collectionDao
    val rawDatabase get() = db
}
