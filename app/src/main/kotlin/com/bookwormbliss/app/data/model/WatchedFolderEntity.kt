package com.bookwormbliss.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A SAF folder the user picked via "Import Folder" that Bookworm Bliss
 * re-scans on demand for .epub files. `uri` is the persisted-permission
 * tree URI (see FolderScanner / ImportFolderViewModel).
 */
@Entity(tableName = "watched_folders")
data class WatchedFolderEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val displayName: String,
    val addedDate: Long,
    val lastScanDate: Long? = null,
    val lastScanAdded: Int = 0,
    val lastScanUpdated: Int = 0,
)
