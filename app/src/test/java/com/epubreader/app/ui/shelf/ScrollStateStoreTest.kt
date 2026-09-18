package com.epubreader.app.ui.shelf

import com.epubreader.app.ui.ShelfView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollStateStoreTest {

    private val store = ScrollStateStore()

    private fun anchor(
        pos: Int = 0,
        offset: Int = 0,
        clickedId: Long? = null,
        clickedPos: Int = -1,
        clickedOffset: Int = 0,
    ) = ScrollAnchor(pos, offset, clickedId, clickedPos, clickedOffset)

    // ------------------------------------------------------------ view keys

    @Test
    fun `view keys are unique per view and per detail name`() {
        assertEquals("Library", ScrollStateStore.viewKey(ShelfView.Library))
        assertEquals("AuthorsList", ScrollStateStore.viewKey(ShelfView.AuthorsList))
        assertEquals("author_detail:Le Guin", ScrollStateStore.viewKey(ShelfView.AuthorDetail("Le Guin")))
        assertEquals("series_detail:The Culture", ScrollStateStore.viewKey(ShelfView.SeriesDetail("The Culture")))
        assertTrue(
            ScrollStateStore.viewKey(ShelfView.AuthorDetail("A")) !=
                    ScrollStateStore.viewKey(ShelfView.AuthorDetail("B"))
        )
    }

    @Test
    fun `restore eligibility covers list views only`() {
        listOf(
            ShelfView.Reading, ShelfView.Library, ShelfView.AuthorsList,
            ShelfView.SeriesList, ShelfView.AuthorDetail("A"), ShelfView.SeriesDetail("S"),
        ).forEach { assertTrue("eligible: $it", ScrollStateStore.isRestoreEligible(it)) }

        listOf(
            ShelfView.Home, ShelfView.Favorites, ShelfView.Finished, ShelfView.ToBeRead,
            ShelfView.Collections, ShelfView.Folders, ShelfView.Settings,
            ShelfView.RecentlyAdded(emptyList()),
        ).forEach { assertFalse("not eligible: $it", ScrollStateStore.isRestoreEligible(it)) }
    }

    // ------------------------------------------------------------ capture

    @Test
    fun `capture ignores non-eligible views`() {
        store.capture(ShelfView.Home, anchor(pos = 7, offset = 12))
        store.queueRestore(ShelfView.Home)
        assertNull(store.pendingRestoreKey)
        // Nothing was stored → restore returns nothing.
        assertNull(
            store.restore(ShelfView.Home, isBookList = true, itemCount = 10) { -1 }
        )
    }

    // ------------------------------------------------------------ restore gating

    @Test
    fun `restore is a no-op without a queued key`() {
        store.capture(ShelfView.Library, anchor(pos = 3, offset = 40))
        val target = store.restore(ShelfView.Library, isBookList = true, itemCount = 10) { -1 }
        assertNull(target)
    }

    @Test
    fun `restore waits for the matching view and keeps the key meanwhile`() {
        store.capture(ShelfView.Library, anchor(pos = 3, offset = 40))
        store.queueRestore(ShelfView.Library)

        // A different view's emission must NOT consume the pending key.
        store.capture(ShelfView.AuthorsList, anchor(pos = 1, offset = 5))
        assertNull(
            store.restore(ShelfView.AuthorsList, isBookList = false, itemCount = 10) { -1 }
        )
        assertEquals("Library", store.pendingRestoreKey)

        // The right view consumes it.
        val target = store.restore(ShelfView.Library, isBookList = true, itemCount = 10) { -1 }
        assertNotNull(target)
        assertEquals(3, target!!.position)
        assertEquals(40, target.offset)
        assertNull(store.pendingRestoreKey)
    }

    @Test
    fun `restore keeps the key while the adapter is still empty`() {
        store.capture(ShelfView.Library, anchor(pos = 5, offset = 50))
        store.queueRestore(ShelfView.Library)
        assertNull(
            store.restore(ShelfView.Library, isBookList = true, itemCount = 0) { -1 }
        )
        assertEquals("Library", store.pendingRestoreKey)
    }

    @Test
    fun `restore clears the key when the anchor is missing`() {
        store.queueRestore(ShelfView.Library) // never captured
        assertNull(
            store.restore(ShelfView.Library, isBookList = true, itemCount = 10) { -1 }
        )
        assertNull(store.pendingRestoreKey)
    }

    @Test
    fun `clearPendingRestore cancels a queued restore`() {
        store.capture(ShelfView.Library, anchor(pos = 5, offset = 50))
        store.queueRestore(ShelfView.Library)
        store.clearPendingRestore()
        assertNull(
            store.restore(ShelfView.Library, isBookList = true, itemCount = 10) { -1 }
        )
    }

    // ------------------------------------------------------------ restore strategy

    @Test
    fun `row lists restore the exact first-visible position`() {
        store.capture(ShelfView.AuthorsList, anchor(pos = 4, offset = -17))
        store.queueRestore(ShelfView.AuthorsList)
        val target = store.restore(ShelfView.AuthorsList, isBookList = false, itemCount = 40) { -1 }
        assertNotNull(target)
        assertEquals(4, target!!.position)
        assertEquals(-17, target.offset)
    }

    @Test
    fun `book list restores exact position when the clicked book did not move`() {
        store.capture(ShelfView.Library, anchor(pos = 6, offset = 30, clickedId = 9L, clickedPos = 8, clickedOffset = 55))
        store.queueRestore(ShelfView.Library)
        val target = store.restore(ShelfView.Library, isBookList = true, itemCount = 40) { id ->
            if (id == 9L) 8 else -1 // same index as when captured
        }
        assertNotNull(target)
        assertEquals(6, target!!.position) // first-visible, not the clicked row
        assertEquals(30, target.offset)
    }

    @Test
    fun `book list relocates the clicked book by id when it moved`() {
        store.capture(ShelfView.Library, anchor(pos = 6, offset = 30, clickedId = 9L, clickedPos = 8, clickedOffset = 55))
        store.queueRestore(ShelfView.Library)
        val target = store.restore(ShelfView.Library, isBookList = true, itemCount = 40) { id ->
            if (id == 9L) 2 else -1 // the list re-sorted: the book moved up
        }
        assertNotNull(target)
        assertEquals(2, target!!.position)
        assertEquals(55, target.offset)
    }

    @Test
    fun `book list restores exact position when the clicked book vanished`() {
        store.capture(ShelfView.Library, anchor(pos = 6, offset = 30, clickedId = 9L, clickedPos = 8, clickedOffset = 55))
        store.queueRestore(ShelfView.Library)
        val target = store.restore(ShelfView.Library, isBookList = true, itemCount = 40) { -1 }
        assertNotNull(target)
        assertEquals(6, target!!.position)
        assertEquals(30, target.offset)
    }

    @Test
    fun `restore clamps the position to the new item count`() {
        store.capture(ShelfView.Library, anchor(pos = 30, offset = 10))
        store.queueRestore(ShelfView.Library)
        val target = store.restore(ShelfView.Library, isBookList = true, itemCount = 4) { -1 }
        assertNotNull(target)
        assertEquals(3, target!!.position)
    }

    @Test
    fun `anchors are stored per view so captures do not collide`() {
        store.capture(ShelfView.Library, anchor(pos = 10, offset = 11))
        store.capture(ShelfView.AuthorsList, anchor(pos = 2, offset = 22))
        store.queueRestore(ShelfView.AuthorsList)
        val target = store.restore(ShelfView.AuthorsList, isBookList = false, itemCount = 30) { -1 }
        assertEquals(2, target!!.position)
        assertEquals(22, target.offset)
    }
}
