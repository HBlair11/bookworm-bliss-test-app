package com.bookwormbliss.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Mirrors the web app's search-history list (AppDatabase.getSearchHistory in db.ts). */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val term: String,
    val lastUsed: Long,
)
