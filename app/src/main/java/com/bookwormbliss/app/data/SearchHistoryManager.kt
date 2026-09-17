package com.bookwormbliss.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Search query history. Each row is one user search query, with an optional
 * [bookId] when the search was scoped to a single book (null for global
 * library searches). Re-searching the same query updates [timestamp] rather
 * than creating a duplicate row.
 */
@Entity(
    tableName = "search_history",
    indices = [androidx.room.Index(value = ["query"], unique = true)]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "query") val query: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    /** Book id when the search was book-scoped; null for global library searches. */
    @ColumnInfo(name = "book_id") val bookId: String? = null,
)

@Dao
interface SearchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity): Long

    @Query("UPDATE search_history SET timestamp = :timestamp, book_id = :bookId WHERE query = :query")
    suspend fun updateTimestamp(query: String, timestamp: Long, bookId: String?)

    @Query("SELECT id FROM search_history WHERE query = :query LIMIT 1")
    suspend fun findByQuery(query: String): Long?

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    @Query("DELETE FROM search_history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM search_history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThanDays(cutoffMillis: Long)
}

/**
 * Wraps [SearchHistoryDao] with coroutine-friendly suspend functions and
 * implements deduplication logic: inserting a query that already exists
 * updates the timestamp instead of creating a duplicate row.
 *
 * The entity and DAO are defined here but NOT yet registered in [AppDatabase].
 * A separate migration step will add them to the @Database annotation and bump
 * the schema version.
 */
class SearchHistoryManager(
    private val dao: SearchHistoryDao,
) {
    /**
     * Insert (or refresh) a search query. If [query] already exists, the row's
     * timestamp is updated and [bookId] is overwritten; otherwise a new row is
     * inserted.
     */
    suspend fun addQuery(query: String, bookId: String? = null) = withContext(Dispatchers.IO) {
        val existingId = dao.findByQuery(query)
        if (existingId != null) {
            dao.updateTimestamp(query, System.currentTimeMillis(), bookId)
        } else {
            dao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis(), bookId = bookId))
        }
    }

    /** Returns the most recent [limit] search queries (default 20), newest first. */
    suspend fun getRecent(limit: Int = 20): List<SearchHistoryEntity> = withContext(Dispatchers.IO) {
        dao.getRecent(limit)
    }

    /** Deletes all search history rows. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    /**
     * Deletes search history entries older than [days] days from the current
     * time. Entries exactly [days] days old are kept; only strictly older rows
     * are removed.
     */
    suspend fun deleteOlderThan(days: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days.toLong() * 24L * 60L * 60L * 1000L
        dao.deleteOlderThan(cutoff)
    }
}
