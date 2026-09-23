package com.bookwormbliss.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val bookTitle: String? = null,
    val startedAt: Long,
    val activeSeconds: Int = 0,
    val pagesRead: Int = 0,
    val chaptersRead: Int = 0,
    /** YYYY-MM-DD, used for streak/day aggregation. */
    val date: String,
)
