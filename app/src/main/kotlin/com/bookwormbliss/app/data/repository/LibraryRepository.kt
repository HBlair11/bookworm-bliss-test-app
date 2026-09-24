package com.bookwormbliss.app.data.repository

import android.content.Context
import android.net.Uri
import com.bookwormbliss.app.data.db.AppDatabase
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.data.model.BookJournalEntryEntity
import com.bookwormbliss.app.data.model.BookmarkEntity
import com.bookwormbliss.app.data.model.DictionaryHistoryEntity
import com.bookwormbliss.app.data.model.HighlightEntity
import com.bookwormbliss.app.data.model.ReadingSessionEntity
import com.bookwormbliss.app.data.model.ReadingStampEntity
import com.bookwormbliss.app.data.model.SearchHistoryEntity
import com.bookwormbliss.app.data.model.WatchedFolderEntity
import com.bookwormbliss.app.epub.EpubChapter
import com.bookwormbliss.app.epub.EpubContentStore
import com.bookwormbliss.app.epub.EpubImporter
import com.bookwormbliss.app.epub.FolderScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The single point of contact between UI/ViewModels and persisted data
 * (Room + on-disk EPUB/cover/content files). Screens should depend on this
 * class, never on AppDatabase or EpubImporter directly.
 */
class LibraryRepository(
    private val db: AppDatabase,
    private val importer: EpubImporter,
) {
    // --- Books -----------------------------------------------------------

    fun observeBooks(): Flow<List<BookEntity>> = db.bookDao().observeAll()
    fun observeBook(id: String): Flow<BookEntity?> = db.bookDao().observeById(id)
    fun observeCurrentlyReading(): Flow<List<BookEntity>> = db.bookDao().observeCurrentlyReading()
    fun observeFavorites(): Flow<List<BookEntity>> = db.bookDao().observeFavorites()
    fun observeByAuthor(author: String): Flow<List<BookEntity>> = db.bookDao().observeByAuthor(author)
    fun observeBySeries(series: String): Flow<List<BookEntity>> = db.bookDao().observeBySeries(series)

    suspend fun importEpub(uri: Uri, displayName: String?): Result<BookEntity> = withContext(Dispatchers.IO) {
        val result = importer.import(uri, displayName)
        val book = result.book
        if (book == null) {
            return@withContext Result.failure(IllegalStateException(result.error ?: "Import failed"))
        }
        val existing = db.bookDao().findByChecksum(book.checksum)
        if (existing != null) {
            return@withContext Result.success(existing)
        }
        db.bookDao().upsert(book)
        Result.success(book)
    }

    suspend fun chaptersFor(book: BookEntity): List<EpubChapter> = withContext(Dispatchers.IO) {
        EpubContentStore.readChapters(File(book.contentPath))
    }

    suspend fun toggleFavorite(book: BookEntity) = withContext(Dispatchers.IO) {
        db.bookDao().update(book.copy(isFavorite = !book.isFavorite, modifiedDate = System.currentTimeMillis()))
    }

    suspend fun updateProgress(
        book: BookEntity,
        spineIndex: Int,
        scrollRatio: Float,
        progress: Float,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.bookDao().update(
            book.copy(
                spineIndex = spineIndex,
                scrollRatio = scrollRatio,
                progress = progress.coerceIn(0f, 1f),
                isCurrentlyReading = progress < 0.999f,
                lastOpenedDate = now,
                modifiedDate = now,
            ),
        )
    }

    suspend fun markOpened(book: BookEntity) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.bookDao().update(book.copy(isCurrentlyReading = true, lastOpenedDate = now, modifiedDate = now))
    }

    suspend fun updateMetadata(book: BookEntity) = withContext(Dispatchers.IO) {
        db.bookDao().update(book.copy(metadataEdited = true, modifiedDate = System.currentTimeMillis()))
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        db.bookDao().delete(book)
        runCatching { File(book.epubPath).delete() }
        runCatching { File(book.contentPath).delete() }
        book.coverPath?.let { runCatching { File(it).delete() } }
    }

    // --- Bookmarks ---------------------------------------------------------

    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>> = db.bookmarkDao().observeForBook(bookId)

    suspend fun addBookmark(bookId: String, spineIndex: Int, pageInChapter: Int, chapterTitle: String, snippet: String) =
        withContext(Dispatchers.IO) {
            db.bookmarkDao().upsert(
                BookmarkEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    spineIndex = spineIndex,
                    pageInChapter = pageInChapter,
                    chapterTitle = chapterTitle,
                    snippet = snippet,
                    createdDate = System.currentTimeMillis(),
                ),
            )
        }

    suspend fun removeBookmark(bookmark: BookmarkEntity) = withContext(Dispatchers.IO) { db.bookmarkDao().delete(bookmark) }

    // --- Highlights ----------------------------------------------------

    fun observeHighlights(bookId: String): Flow<List<HighlightEntity>> = db.highlightDao().observeForBook(bookId)

    suspend fun addHighlight(highlight: HighlightEntity) = withContext(Dispatchers.IO) { db.highlightDao().upsert(highlight) }

    suspend fun removeHighlight(highlight: HighlightEntity) = withContext(Dispatchers.IO) { db.highlightDao().delete(highlight) }

    // --- Reading stamps / journal / sessions / vocabulary ---------------

    fun observeStamps(bookId: String): Flow<List<ReadingStampEntity>> = db.readingStampDao().observeForBook(bookId)
    suspend fun addStamp(stamp: ReadingStampEntity) = withContext(Dispatchers.IO) { db.readingStampDao().upsert(stamp) }

    fun observeJournal(bookId: String): Flow<List<BookJournalEntryEntity>> = db.journalDao().observeForBook(bookId)
    suspend fun saveJournalEntry(entry: BookJournalEntryEntity) = withContext(Dispatchers.IO) { db.journalDao().upsert(entry) }

    fun observeSessions(): Flow<List<ReadingSessionEntity>> = db.readingSessionDao().observeAll()
    suspend fun logSession(session: ReadingSessionEntity) = withContext(Dispatchers.IO) { db.readingSessionDao().upsert(session) }

    fun observeVocabulary(): Flow<List<DictionaryHistoryEntity>> = db.dictionaryHistoryDao().observeAll()
    suspend fun addVocabularyEntry(entry: DictionaryHistoryEntity) = withContext(Dispatchers.IO) { db.dictionaryHistoryDao().upsert(entry) }

    // --- Search history ---------------------------------------------------

    fun observeSearchHistory(): Flow<List<String>> = db.searchHistoryDao().observeRecent()

    suspend fun addSearchTerm(term: String) = withContext(Dispatchers.IO) {
        db.searchHistoryDao().upsert(SearchHistoryEntity(term, System.currentTimeMillis()))
    }

    suspend fun deleteSearchTerm(term: String) = withContext(Dispatchers.IO) { db.searchHistoryDao().delete(term) }
    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) { db.searchHistoryDao().clear() }

    // --- Watched folders / folder import -----------------------------------

    fun observeWatchedFolders(): Flow<List<WatchedFolderEntity>> = db.watchedFolderDao().observeAll()

    suspend fun addWatchedFolder(uri: String, displayName: String): WatchedFolderEntity = withContext(Dispatchers.IO) {
        val folder = WatchedFolderEntity(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = displayName,
            addedDate = System.currentTimeMillis(),
        )
        db.watchedFolderDao().upsert(folder)
        folder
    }

    suspend fun removeWatchedFolder(folder: WatchedFolderEntity) = withContext(Dispatchers.IO) {
        db.watchedFolderDao().delete(folder)
    }

    data class FolderScanResult(val added: Int, val updated: Int, val unchanged: Int, val failed: Int)

    /**
     * Re-walks [folder]'s tree for .epub files. A file whose SAF URI matches
     * an existing book and whose lastModified hasn't changed is left alone
     * entirely; a changed or brand-new file is (re-)imported. Books that
     * previously came from this folder but are no longer found there are
     * left in the library rather than silently deleted — the user removes
     * them explicitly if they want them gone.
     */
    suspend fun scanFolder(context: Context, folder: WatchedFolderEntity): FolderScanResult = withContext(Dispatchers.IO) {
        val importer = EpubImporter(context)
        var added = 0
        var updated = 0
        var unchanged = 0
        var failed = 0

        val found = runCatching { FolderScanner.scan(context, Uri.parse(folder.uri)) }.getOrDefault(emptyList())
        for (file in found) {
            val existing = db.bookDao().findBySourceUri(file.uri.toString())
            if (existing != null && existing.sourceLastModified == file.lastModified) {
                unchanged++
                continue
            }
            val result = importer.import(
                uri = file.uri,
                displayName = file.displayName,
                sourceUri = file.uri.toString(),
                sourceLastModified = file.lastModified,
                watchedFolderId = folder.id,
                existing = existing,
            )
            val book = result.book
            if (book == null) {
                failed++
                continue
            }
            db.bookDao().upsert(book)
            if (existing != null) updated++ else added++
        }

        db.watchedFolderDao().upsert(
            folder.copy(
                lastScanDate = System.currentTimeMillis(),
                lastScanAdded = added,
                lastScanUpdated = updated,
            ),
        )
        FolderScanResult(added, updated, unchanged, failed)
    }
}
