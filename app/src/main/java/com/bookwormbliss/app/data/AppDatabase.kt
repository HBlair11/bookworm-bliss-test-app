package com.bookwormbliss.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Clean Bookworm Bliss foundation database.
 *
 * The app uses a new application id, so this schema intentionally starts at
 * version 1 instead of carrying the old reader's feature migrations forward.
 */
@Database(
    entities = [BookEntity::class, CollectionEntity::class, BookCollectionRef::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bookworm_bliss.db",
                ).build().also { instance = it }
            }
    }
}
