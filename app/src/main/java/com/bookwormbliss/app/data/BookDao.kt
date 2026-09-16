package com.bookwormbliss.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY sort_title COLLATE NOCASE")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY id DESC LIMIT 6")
    fun observeHomeRecentlyAdded(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE checksum = :checksum")
    suspend fun getByChecksum(checksum: String): BookEntity?

    @Query("SELECT * FROM books WHERE source_uri = :uri LIMIT 1")
    suspend fun getBySourceUri(uri: String): BookEntity?

    @Query("SELECT * FROM books ORDER BY id DESC")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE source_filename = :name ORDER BY id DESC")
    suspend fun getAllBySourceFilename(name: String): List<BookEntity>

    @Query("""
        SELECT id, source_uri, source_filename, file_size, source_last_modified, path
        FROM books
        WHERE source_filename IS NOT NULL AND source_filename != ''
    """)
    suspend fun getSourceFingerprints(): List<SourceFingerprint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("""
        UPDATE books
        SET source_uri = :sourceUri,
            source_filename = :sourceFilename,
            file_size = :fileSize,
            source_last_modified = :sourceLastModified
        WHERE id = :id
    """)
    suspend fun updateSourceIdentity(
        id: Long,
        sourceUri: String,
        sourceFilename: String?,
        fileSize: Long,
        sourceLastModified: Long,
    )

    @Query("UPDATE books SET last_opened_date = :lastOpened, is_currently_reading = 1 WHERE id = :id")
    suspend fun markOpened(id: Long, lastOpened: Long)

    @Query("""
        UPDATE books
        SET progress = :progress,
            spine_index = :spineIndex,
            scroll_ratio = :scrollRatio,
            current_location = :location,
            last_opened_date = :lastOpened,
            is_currently_reading = 1
        WHERE id = :id
    """)
    suspend fun updateReaderPosition(
        id: Long,
        progress: Float,
        spineIndex: Int,
        scrollRatio: Float,
        location: String?,
        lastOpened: Long,
    )

    @Query("UPDATE books SET is_currently_reading = 1 WHERE id = :id")
    suspend fun setCurrentlyReading(id: Long)

    @Query("UPDATE books SET is_currently_reading = 0 WHERE id = :id")
    suspend fun clearCurrentlyReading(id: Long)

    @Query("UPDATE books SET is_favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("SELECT * FROM books WHERE is_currently_reading = 1 ORDER BY last_opened_date DESC, id DESC")
    fun observeCurrentlyReading(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE last_opened_date IS NOT NULL ORDER BY last_opened_date DESC LIMIT 1")
    fun observeLastOpened(): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE is_favorite = 1 ORDER BY sort_title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE progress >= 0.90 ORDER BY last_opened_date DESC")
    fun observeFinished(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE last_opened_date IS NULL ORDER BY added_date DESC")
    fun observeToBeRead(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id IN (:ids) ORDER BY added_date DESC, sort_title COLLATE NOCASE")
    fun observeByIds(ids: List<Long>): Flow<List<BookEntity>>

    @Query("SELECT author AS name, COUNT(*) AS count FROM books WHERE author != '' GROUP BY author ORDER BY author COLLATE NOCASE")
    fun observeAuthors(): Flow<List<GroupedRow>>

    @Query("SELECT series AS name, COUNT(*) AS count FROM books WHERE series IS NOT NULL AND series != '' GROUP BY series ORDER BY series COLLATE NOCASE")
    fun observeSeries(): Flow<List<GroupedRow>>

    @Query("SELECT * FROM books WHERE author = :author ORDER BY series_index IS NULL, series_index, sort_title COLLATE NOCASE")
    fun observeByAuthor(author: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE series = :series ORDER BY series_index IS NULL, series_index, sort_title COLLATE NOCASE")
    fun observeBySeries(series: String): Flow<List<BookEntity>>
}
