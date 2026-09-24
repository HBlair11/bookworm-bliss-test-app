package com.bookwormbliss.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mirrors BookEntity from the web app's src/types.ts. `contentPath` replaces
 * the web app's in-memory `rawContent` — the parsed chapter/TOC payload is
 * stored as a JSON file under app-private storage (see EpubImporter) rather
 * than inline in the database row.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sortTitle: String,
    val author: String,
    val sortAuthor: String,
    val coverPath: String? = null,
    val progress: Float = 0f, // 0.0..1.0
    val spineIndex: Int = 0,
    val scrollRatio: Float = 0f,
    val currentPageInChapter: Int = 0,
    val totalPagesInChapter: Int? = null,
    val series: String? = null,
    val seriesIndex: Float? = null,
    val language: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val identifier: String? = null,
    val publishYear: Int? = null,
    val subjectTags: String? = null,
    val metadataEdited: Boolean = false,
    val isCurrentlyReading: Boolean = false,
    val isFavorite: Boolean = false,
    val addedDate: Long,
    val modifiedDate: Long,
    val lastOpenedDate: Long? = null,
    val currentLocation: String? = null,
    val fileSize: Long = 0,
    val sourceFilename: String,
    val checksum: String,
    val spineCount: Int = 0,
    /** Path (under app-private storage) to the imported .epub and to its parsed content JSON. */
    val epubPath: String,
    val contentPath: String,
    /**
     * Set only for books that came from a watched folder (see
     * WatchedFolderEntity / FolderScanner) — the original SAF document URI
     * they were imported from, and its lastModified at import time. Rescans
     * use these two fields to detect a changed file and update this same
     * row in place rather than creating a duplicate. Null for books added
     * via the one-off "Import" file picker.
     */
    val sourceUri: String? = null,
    val sourceLastModified: Long? = null,
    val watchedFolderId: String? = null,
)
