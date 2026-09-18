package com.epubreader.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 10 — Reading Nook journal entries.
 *
 * Translated from the web app's `BookJournalEntryEntity` (types.ts). A journal
 * entry is a free-form note the user writes about a book (Markdown/rich text on
 * the web; plain text with a title on Android for now). Shown in the Reading
 * Nook "Journal" tab and included in Full Backup & Restore.
 */
@Entity(
    tableName = "book_journal_entries",
    indices = [
        Index(value = ["book_id", "created_at"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BookJournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
