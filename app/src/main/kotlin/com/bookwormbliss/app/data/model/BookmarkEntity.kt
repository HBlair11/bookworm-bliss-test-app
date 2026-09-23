package com.bookwormbliss.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val spineIndex: Int,
    val pageInChapter: Int,
    val chapterTitle: String,
    val snippet: String,
    val createdDate: Long,
    val note: String? = null,
)
