package com.bookwormbliss.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val spineIndex: Int,
    val pageInChapter: Int,
    val text: String,
    val color: HighlightColor,
    val note: String? = null,
    val createdDate: Long,
    val prefix: String? = null,
    val suffix: String? = null,
    val normalizedOffset: Int? = null,
)
