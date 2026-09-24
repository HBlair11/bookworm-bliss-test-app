package com.bookwormbliss.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.data.model.BookJournalEntryEntity
import com.bookwormbliss.app.data.model.BookmarkEntity
import com.bookwormbliss.app.data.model.DictionaryHistoryEntity
import com.bookwormbliss.app.data.model.HighlightEntity
import com.bookwormbliss.app.data.model.ReadingSessionEntity
import com.bookwormbliss.app.data.model.ReadingStampEntity
import com.bookwormbliss.app.data.model.SearchHistoryEntity
import com.bookwormbliss.app.data.model.WatchedFolderEntity

/**
 * The single source of truth for every persisted fact about the user's
 * library. All reads/writes should go through LibraryRepository, which
 * wraps this database plus the file-based EPUB/cover storage — screens and
 * ViewModels should never touch AppDatabase or the DAOs directly.
 *
 * Pre-release schema: bumping `version` uses destructive fallback rather
 * than a migration path, since there's no installed base to preserve yet.
 * Add a real Migration once this ships.
 */
@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        ReadingStampEntity::class,
        BookJournalEntryEntity::class,
        ReadingSessionEntity::class,
        DictionaryHistoryEntity::class,
        WatchedFolderEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun readingStampDao(): ReadingStampDao
    abstract fun journalDao(): JournalDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun dictionaryHistoryDao(): DictionaryHistoryDao
    abstract fun watchedFolderDao(): WatchedFolderDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bookworm-bliss.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
