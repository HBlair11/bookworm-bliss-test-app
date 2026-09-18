package com.epubreader.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searched_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY searched_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SearchHistoryEntity>

    @Query("SELECT * FROM search_history ORDER BY searched_at DESC")
    suspend fun getAll(): List<SearchHistoryEntity>

    /**
     * Insert (or refresh) a query. On conflict the row's searched_at is bumped
     * so re-submitted queries float to the top of recent searches.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SearchHistoryEntity>)

    @Query("DELETE FROM search_history")
    suspend fun deleteAll()
}
