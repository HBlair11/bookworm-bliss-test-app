package com.epubreader.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 10 — Reading Nook stamps.
 *
 * Translated from the web app's `ReadingStampEntity` (types.ts). A stamp is a
 * discrete reading milestone the user logs for a book: started, resumed,
 * finished, revisited, favorite_moment, memorable, or reread. Each stamp
 * carries a short title and an optional note and is shown in the Reading Nook
 * "Stamps" tab.
 *
 * Unlike the web app (string ids), the Android app uses auto-generated Long ids
 * following the convention of every other entity in this package.
 */
@Entity(
    tableName = "reading_stamps",
    indices = [
        Index(value = ["book_id", "timestamp"]),
        Index(value = ["book_id", "type"]),
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
data class ReadingStampEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    /** One of [Type]. Stored as TEXT; the catalog is small and fixed. */
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
) {
    /** Stamp type catalog — mirrors the web app's `ReadingStampType`. */
    object Type {
        const val STARTED = "started"
        const val RESUMED = "resumed"
        const val FINISHED = "finished"
        const val REVISITED = "revisited"
        const val FAVORITE_MOMENT = "favorite_moment"
        const val MEMORABLE = "memorable"
        const val REREAD = "reread"

        val ALL: List<String> = listOf(
            STARTED, RESUMED, FINISHED, REVISITED, FAVORITE_MOMENT, MEMORABLE, REREAD,
        )
    }
}
