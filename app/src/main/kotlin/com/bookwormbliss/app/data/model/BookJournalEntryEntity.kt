package com.bookwormbliss.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class BookJournalEntryEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
