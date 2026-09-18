package com.epubreader.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookJournalDao {
    @Query("SELECT * FROM book_journal_entries WHERE book_id = :bookId ORDER BY created_at DESC")
    fun observeForBook(bookId: Long): Flow<List<BookJournalEntryEntity>>

    @Query("SELECT * FROM book_journal_entries WHERE book_id = :bookId ORDER BY created_at DESC")
    suspend fun getForBook(bookId: Long): List<BookJournalEntryEntity>

    @Query("SELECT * FROM book_journal_entries ORDER BY created_at DESC")
    suspend fun getAll(): List<BookJournalEntryEntity>

    @Query("SELECT * FROM book_journal_entries WHERE id = :id")
    suspend fun getById(id: Long): BookJournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BookJournalEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<BookJournalEntryEntity>)

    @Update
    suspend fun update(entry: BookJournalEntryEntity)

    @Delete
    suspend fun delete(entry: BookJournalEntryEntity)

    @Query("DELETE FROM book_journal_entries WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM book_journal_entries")
    suspend fun deleteAll()
}
