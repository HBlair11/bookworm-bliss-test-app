package com.bookwormbliss.app.core.navigation

/**
 * App navigation destinations — the screens the user can navigate to.
 *
 * Navigation state belongs to the navigation layer, not scattered
 * throughout activities. This sealed class is the single source of
 * truth for all navigable destinations.
 *
 * Back stack semantics:
 *   Library → Book Details → Reader
 *   Back: Reader → Book Details → Library
 *
 * Scroll state is screen-owned: each list screen remembers its scroll
 * position. A deliberate "open" action (e.g. "Open Currently Reading")
 * refreshes to top, while a "return" action restores the previous position.
 */
sealed class NavDestination {
    /** Home screen — curated reading surface */
    object Home : NavDestination()
    /** Full library — sortable grid/list of all books */
    object Library : NavDestination()
    /** Currently reading shelf */
    object Reading : NavDestination()
    /** Favorites shelf */
    object Favorites : NavDestination()
    /** Finished books shelf */
    object Finished : NavDestination()
    /** To-be-read shelf */
    object ToBeRead : NavDestination()
    /** Authors list */
    object Authors : NavDestination()
    /** Series list */
    object Series : NavDestination()
    /** Collections list */
    object Collections : NavDestination()
    /** Folders / import sources */
    object Folders : NavDestination()
    /** Settings */
    object Settings : NavDestination()
    /** Reading statistics */
    object Stats : NavDestination()
    /** About / Privacy */
    object About : NavDestination()
    /** Vocabulary builder */
    object Vocabulary : NavDestination()

    /** Book details screen */
    data class BookDetails(val bookId: Long) : NavDestination()
    /** Author detail (books by a specific author) */
    data class AuthorDetail(val name: String) : NavDestination()
    /** Series detail (books in a specific series) */
    data class SeriesDetail(val name: String) : NavDestination()
    /** Reader (open a book) */
    data class Reader(val bookId: Long) : NavDestination()
    /** Search */
    object Search : NavDestination()
}

/**
 * Navigation action — how we got to a destination.
 * Determines scroll behavior: REFRESH_TO_TOP vs RESTORE_POSITION.
 */
enum class NavAction {
    /** User explicitly opened (e.g. tapped a shelf item) — refresh to top */
    OPEN,
    /** User returned (e.g. back from a detail) — restore previous scroll */
    RETURN,
    /** Deep link from outside the app */
    DEEP_LINK,
}
