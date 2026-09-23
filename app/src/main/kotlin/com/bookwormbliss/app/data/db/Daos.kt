package com.bookwormbliss.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.data.model.BookJournalEntryEntity
import com.bookwormbliss.app.data.model.BookmarkEntity
import com.bookwormbliss.app.data.model.DictionaryHistoryEntity
import com.bookwormbliss.app.data.model.HighlightEntity
import com.bookwormbliss.app.data.model.ReadingSessionEntity
import com.bookwormbliss.app.data.model.ReadingStampEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY modifiedDate DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE isCurrentlyReading = 1 ORDER BY lastOpenedDate DESC")
    fun observeCurrentlyReading(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY sortTitle ASC")
    fun observeFavorites(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE checksum = :checksum LIMIT 1")
    suspend fun findByChecksum(checksum: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdDate DESC")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdDate DESC")
    fun observeForBook(bookId: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(highlight: HighlightEntity)

    @Delete
    suspend fun delete(highlight: HighlightEntity)
}

@Dao
interface ReadingStampDao {
    @Query("SELECT * FROM reading_stamps WHERE bookId = :bookId ORDER BY timestamp DESC")
    fun observeForBook(bookId: String): Flow<List<ReadingStampEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stamp: ReadingStampEntity)

    @Delete
    suspend fun delete(stamp: ReadingStampEntity)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeForBook(bookId: String): Flow<List<BookJournalEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BookJournalEntryEntity)

    @Delete
    suspend fun delete(entry: BookJournalEntryEntity)
}

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAt DESC")
    fun observeForBook(bookId: String): Flow<List<ReadingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ReadingSessionEntity)
}

@Dao
interface DictionaryHistoryDao {
    @Query("SELECT * FROM dictionary_history ORDER BY lookedUpDate DESC")
    fun observeAll(): Flow<List<DictionaryHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DictionaryHistoryEntity)

    @Delete
    suspend fun delete(entry: DictionaryHistoryEntity)
}
