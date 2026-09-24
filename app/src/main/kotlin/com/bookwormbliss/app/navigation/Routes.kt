package com.bookwormbliss.app.navigation

import android.net.Uri

/** Every navigable destination in the app — the single source of truth for route strings. */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val READING = "reading"
    const val FAVORITES = "favorites"
    const val AUTHORS = "authors"
    const val SERIES = "series"
    const val READING_NOOK = "reading_nook"
    const val STATS = "stats"
    const val VOCABULARY = "vocabulary"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val FOLDERS = "folders"

    const val BOOK_DETAILS = "book_details/{bookId}"
    fun bookDetails(bookId: String) = "book_details/$bookId"

    const val READER = "reader/{bookId}"
    fun reader(bookId: String) = "reader/$bookId"

    const val AUTHOR_DETAIL = "author_detail/{authorName}"
    fun authorDetail(name: String) = "author_detail/${Uri.encode(name)}"

    const val SERIES_DETAIL = "series_detail/{seriesName}"
    fun seriesDetail(name: String) = "series_detail/${Uri.encode(name)}"

    const val ARG_BOOK_ID = "bookId"
    const val ARG_AUTHOR_NAME = "authorName"
    const val ARG_SERIES_NAME = "seriesName"
}
