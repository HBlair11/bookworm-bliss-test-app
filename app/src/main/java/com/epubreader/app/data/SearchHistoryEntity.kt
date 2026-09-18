package com.epubreader.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 10 — search history.
 *
 * Persists every non-blank query the user submits on the Search screen so the
 * app can surface recent searches (and so Settings → Maintenance can clear
 * them). A UNIQUE index on the normalized query makes re-submitted queries
 * bump their timestamp instead of duplicating rows.
 *
 * This is the Android counterpart of the web app's in-memory search history —
 * here it is durable so it survives process death and can be cleared/exported.
 */
@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"], unique = true)],
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "query") val query: String,
    @ColumnInfo(name = "searched_at") val searchedAt: Long = System.currentTimeMillis(),
)
