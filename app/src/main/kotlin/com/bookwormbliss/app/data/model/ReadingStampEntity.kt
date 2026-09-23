package com.bookwormbliss.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_stamps")
data class ReadingStampEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val type: ReadingStampType,
    val title: String,
    val note: String? = null,
    val timestamp: Long,
)
