package com.bookwormbliss.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent library record for one imported EPUB.
 *
 * Reader position is intentionally format-specific and compact:
 * [spineIndex] identifies the EPUB spine item and [scrollRatio] identifies the
 * horizontal document position within that rendered item. [currentLocation]
 * is display-only chapter/section text for the library UI.
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["checksum"], unique = true),
        Index(value = ["source_uri"], unique = true),
    ],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val path: String,
    @ColumnInfo(name = "cover_path") val coverPath: String? = null,
    val progress: Float = 0f,
    val series: String? = null,
    @ColumnInfo(name = "series_index") val seriesIndex: Double? = null,
    val language: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val identifier: String? = null,
    @ColumnInfo(name = "publish_year") val publishYear: Int? = null,
    @ColumnInfo(name = "subject_tags") val subjectTags: String? = null,
    @ColumnInfo(name = "metadata_edited") val metadataEdited: Boolean = false,
    @ColumnInfo(name = "is_currently_reading") val isCurrentlyReading: Boolean = false,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "added_date") val addedDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "modified_date") val modifiedDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_opened_date") val lastOpenedDate: Long? = null,
    @ColumnInfo(name = "current_location") val currentLocation: String? = null,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0L,
    @ColumnInfo(name = "spine_index") val spineIndex: Int = 0,
    @ColumnInfo(name = "spine_count") val spineCount: Int = 0,
    @ColumnInfo(name = "scroll_ratio") val scrollRatio: Float = 0f,
    val checksum: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String = title,
    @ColumnInfo(name = "sort_author") val sortAuthor: String = author,
    @ColumnInfo(name = "source_uri") val sourceUri: String? = null,
    @ColumnInfo(name = "source_filename") val sourceFilename: String? = null,
    @ColumnInfo(name = "source_last_modified") val sourceLastModified: Long = 0L,
)
