package com.epubreader.app.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStampDao {
    @Query("SELECT * FROM reading_stamps WHERE book_id = :bookId ORDER BY timestamp DESC")
    fun observeForBook(bookId: Long): Flow<List<ReadingStampEntity>>

    @Query("SELECT * FROM reading_stamps WHERE book_id = :bookId ORDER BY timestamp DESC")
    suspend fun getForBook(bookId: Long): List<ReadingStampEntity>

    @Query("SELECT * FROM reading_stamps ORDER BY timestamp DESC")
    suspend fun getAll(): List<ReadingStampEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stamp: ReadingStampEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stamps: List<ReadingStampEntity>)

    @Update
    suspend fun update(stamp: ReadingStampEntity)

    @Delete
    suspend fun delete(stamp: ReadingStampEntity)

    @Query("DELETE FROM reading_stamps WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM reading_stamps")
    suspend fun deleteAll()
}
