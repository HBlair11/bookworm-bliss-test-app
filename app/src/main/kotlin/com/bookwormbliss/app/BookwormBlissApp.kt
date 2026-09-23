package com.bookwormbliss.app

import android.app.Application
import com.bookwormbliss.app.data.db.AppDatabase
import com.bookwormbliss.app.data.prefs.PreferencesRepository
import com.bookwormbliss.app.data.repository.LibraryRepository
import com.bookwormbliss.app.epub.EpubImporter

/**
 * Composition root. Everything here is created once and handed down through
 * Compose (see MainActivity) — there is no service locator / DI framework,
 * which keeps a project this size easy to follow, but it does mean this
 * class is the one place that should ever construct a repository.
 */
class BookwormBlissApp : Application() {

    lateinit var libraryRepository: LibraryRepository
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        val importer = EpubImporter(this)
        libraryRepository = LibraryRepository(db, importer)
        preferencesRepository = PreferencesRepository(this)
    }
}
