package com.bookwormbliss.app.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.bookwormbliss.app.BookwormBlissApp

/**
 * Every screen's ViewModel factory pulls the shared repositories from the
 * Application instance this way, so there is exactly one LibraryRepository
 * and one PreferencesRepository for the whole app (see BookwormBlissApp).
 */
fun CreationExtras.bookwormApp(): BookwormBlissApp =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application) as BookwormBlissApp
