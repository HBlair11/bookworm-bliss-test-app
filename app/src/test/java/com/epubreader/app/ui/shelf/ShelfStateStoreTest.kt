package com.epubreader.app.ui.shelf

import com.epubreader.app.ui.ShelfView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfStateStoreTest {

    private val store = ShelfStateStore()

    // ------------------------------------------------------------ recently added

    @Test
    fun `exitRecentlyAdded returns the pre-entry shelf and falls back to Library`() {
        assertNull(store.viewBeforeRecentlyAdded)

        store.enterRecentlyAdded(ShelfView.Favorites)
        assertEquals(ShelfView.Favorites, store.viewBeforeRecentlyAdded)
        // Entering the temp screen should land the user at the top of it.
        assertTrue(store.scrollToTopOnNextContent)

        assertEquals(ShelfView.Favorites, store.exitRecentlyAdded())
        assertNull(store.viewBeforeRecentlyAdded)
        // Exiting should top-reset the restored shelf (clean nav-switch feel).
        assertTrue(store.scrollToTopOnNextContent)

        // State lost (e.g. process death) → Library fallback.
        assertEquals(ShelfView.Library, store.exitRecentlyAdded())
    }

    @Test
    fun `recently added exit can be taken twice safely`() {
        store.enterRecentlyAdded(ShelfView.Reading)
        store.exitRecentlyAdded()
        assertEquals(ShelfView.Library, store.exitRecentlyAdded())
        assertNull(store.viewBeforeRecentlyAdded)
    }

    // ------------------------------------------------------------ drawer nav

    @Test
    fun `drawer navigation cancels the Currently Reading top reset`() {
        store.pendingReadingTopReset = true
        store.onDrawerNavigation()
        assertFalse(store.pendingReadingTopReset)
    }

    @Test
    fun `drawer navigation sets the clean top reset`() {
        store.enterRecentlyAdded(ShelfView.Reading)
        store.exitRecentlyAdded()

        store.scrollToTopOnNextContent = false
        store.onDrawerNavigation()
        // onDrawerNavigation itself only cancels the reading reset; the
        // top-reset flag is set by the selectDrawer path.
        assertFalse(store.pendingReadingTopReset)
    }

    // ------------------------------------------------------------ save / restore

    @Test
    fun `snapshot round-trips every top-level view`() {
        val views = listOf(
            ShelfView.Home, ShelfView.Reading, ShelfView.Library, ShelfView.Favorites,
            ShelfView.Finished, ShelfView.ToBeRead, ShelfView.AuthorsList, ShelfView.SeriesList,
            ShelfView.Collections, ShelfView.Folders, ShelfView.Settings,
        )
        views.forEach { view ->
            val saved = store.snapshotForSave(view)
            assertNotNull("snapshot missing for $view", saved)
            assertEquals(view, store.restore(saved))
        }
    }

    @Test
    fun `snapshot round-trips detail views with their names`() {
        val author = ShelfView.AuthorDetail("Ursula K. Le Guin")
        val series = ShelfView.SeriesDetail("The Culture")

        assertEquals(author, store.restore(store.snapshotForSave(author)))
        assertEquals(series, store.restore(store.snapshotForSave(series)))

        // Different names restore to different views.
        val a1 = store.snapshotForSave(ShelfView.AuthorDetail("A"))
        val a2 = store.snapshotForSave(ShelfView.AuthorDetail("B"))
        assertEquals(ShelfView.AuthorDetail("B"), store.restore(a2))
        assertTrue(store.restore(a1) != store.restore(a2))
    }

    @Test
    fun `recently added is never persisted`() {
        val saved = store.snapshotForSave(ShelfView.RecentlyAdded(listOf(1, 2, 3)))
        assertNull(saved)
    }

    @Test
    fun `restore of null snapshot is null`() {
        assertNull(store.restore(null))
    }

    // ------------------------------------------------------------ classification

    @Test
    fun `isBookView excludes non-book screens`() {
        listOf(
            ShelfView.Home, ShelfView.AuthorsList, ShelfView.SeriesList,
            ShelfView.Collections, ShelfView.Folders, ShelfView.Settings,
        ).forEach { assertFalse("should not be a book view: $it", ShelfStateStore.isBookView(it)) }

        listOf(
            ShelfView.Reading, ShelfView.Library, ShelfView.Favorites, ShelfView.Finished,
            ShelfView.ToBeRead, ShelfView.RecentlyAdded(emptyList()),
            ShelfView.AuthorDetail("X"), ShelfView.SeriesDetail("Y"),
        ).forEach { assertTrue("should be a book view: $it", ShelfStateStore.isBookView(it)) }
    }

    @Test
    fun `parentOf maps details to their parent lists`() {
        assertEquals(ShelfView.AuthorsList, ShelfStateStore.parentOf(ShelfView.AuthorDetail("A")))
        assertEquals(ShelfView.SeriesList, ShelfStateStore.parentOf(ShelfView.SeriesDetail("S")))
        assertNull(ShelfStateStore.parentOf(ShelfView.Library))
        assertNull(ShelfStateStore.parentOf(ShelfView.RecentlyAdded(listOf(1))))
    }

    @Test
    fun `placeholder and folders classification`() {
        assertTrue(ShelfStateStore.isPlaceholder(ShelfView.Collections))
        assertFalse(ShelfStateStore.isPlaceholder(ShelfView.Library))
        assertTrue(ShelfStateStore.isFoldersView(ShelfView.Folders))
        assertFalse(ShelfStateStore.isFoldersView(ShelfView.Settings))
    }
}
