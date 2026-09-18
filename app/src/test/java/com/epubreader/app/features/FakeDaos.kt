package com.epubreader.app.features

import com.epubreader.app.data.BookmarkDao
import com.epubreader.app.data.BookmarkEntity
import com.epubreader.app.data.DictionaryHistoryDao
import com.epubreader.app.data.DictionaryHistoryEntity
import com.epubreader.app.data.HighlightDao
import com.epubreader.app.data.HighlightEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * In-memory fakes of the Room DAOs used by the Phase 7 feature services.
 *
 * They mimic the SQL semantics the services rely on:
 *  - bookmarks list ordering  : ORDER BY created_at DESC, id DESC
 *  - highlights list ordering  : ORDER BY created_at DESC
 *  - highlights per chapter    : ORDER BY normalized_start, created_at
 *  - insert                    : OnConflictStrategy.REPLACE (same id / same
 *                                unique key overwrites the stored row)
 *  - "near" ratio matches      : ABS(scroll_ratio - :ratio) < 0.01
 */
class FakeBookmarkDao : BookmarkDao {

    val rows = mutableListOf<BookmarkEntity>()
    private var nextId = 1L
    private val version = MutableStateFlow(0)

    private fun notifyChanged() {
        version.value = version.value + 1
    }

    private fun orderedForBook(bookId: Long) =
        rows.filter { it.bookId == bookId }
            .sortedWith(compareByDescending<BookmarkEntity> { it.createdAt }.thenByDescending { it.id })

    override fun observeForBook(bookId: Long): Flow<List<BookmarkEntity>> =
        version.map { orderedForBook(bookId) }

    override suspend fun insert(bookmark: BookmarkEntity): Long {
        val id = if (bookmark.id != 0L) bookmark.id else nextId++
        if (bookmark.id == 0L) nextId = maxOf(nextId, id + 1)
        val stored = rows.indexOfFirst { it.id == id }
        val row = bookmark.copy(id = id)
        if (stored >= 0) rows[stored] = row else rows.add(row)
        notifyChanged()
        return id
    }

    override suspend fun delete(bookmark: BookmarkEntity) {
        rows.removeAll { it.id == bookmark.id }
        notifyChanged()
    }

    override suspend fun deleteNear(bookId: Long, spineIndex: Int, ratio: Float) {
        rows.removeAll {
            it.bookId == bookId && it.spineIndex == spineIndex && abs(it.scrollRatio - ratio) < 0.01f
        }
        notifyChanged()
    }

    override suspend fun existsNear(bookId: Long, spineIndex: Int, ratio: Float): Boolean =
        rows.any {
            it.bookId == bookId && it.spineIndex == spineIndex && abs(it.scrollRatio - ratio) < 0.01f
        }

    override suspend fun existsNearWithSnippet(bookId: Long, spineIndex: Int, page: Int, snippet: String): Boolean =
        rows.any {
            it.bookId == bookId && it.spineIndex == spineIndex && it.pageInChapter == page && it.snippet == snippet
        }

    override suspend fun deleteNearWithSnippet(bookId: Long, spineIndex: Int, page: Int, snippet: String) {
        rows.removeAll {
            it.bookId == bookId && it.spineIndex == spineIndex && it.pageInChapter == page && it.snippet == snippet
        }
        notifyChanged()
    }

    override suspend fun findWholePage(bookId: Long, spineIndex: Int, page: Int, ratio: Float): BookmarkEntity? =
        rows.filter {
            it.bookId == bookId && it.spineIndex == spineIndex &&
                it.bookmarkType == BookmarkEntity.TYPE_WHOLE_PAGE &&
                ((it.pageInChapter >= 0 && it.pageInChapter == page) ||
                    (it.pageInChapter < 0 && abs(it.scrollRatio - ratio) < 0.01f))
        }.maxByOrNull { it.id }

    override suspend fun findWholePageBySnippet(bookId: Long, spineIndex: Int, snippet: String): BookmarkEntity? =
        rows.filter {
            it.bookId == bookId && it.spineIndex == spineIndex &&
                it.bookmarkType == BookmarkEntity.TYPE_WHOLE_PAGE && it.snippet == snippet
        }.maxByOrNull { it.id }

    override suspend fun findText(bookId: Long, spineIndex: Int, page: Int, snippet: String): BookmarkEntity? =
        rows.filter {
            it.bookId == bookId && it.spineIndex == spineIndex &&
                it.pageInChapter == page && it.bookmarkType == BookmarkEntity.TYPE_TEXT && it.snippet == snippet
        }.maxByOrNull { it.id }

    override suspend fun findTextBySnippet(bookId: Long, spineIndex: Int, snippet: String): BookmarkEntity? =
        rows.filter {
            it.bookId == bookId && it.spineIndex == spineIndex &&
                it.bookmarkType == BookmarkEntity.TYPE_TEXT && it.snippet == snippet
        }.maxByOrNull { it.id }

    override suspend fun getAll(): List<BookmarkEntity> =
        rows.sortedByDescending { it.createdAt }

    override suspend fun insertAll(bookmarks: List<BookmarkEntity>) {
        for (bookmark in bookmarks) {
            val id = if (bookmark.id != 0L) bookmark.id else nextId++
            if (bookmark.id == 0L) nextId = maxOf(nextId, id + 1)
            val stored = rows.indexOfFirst { it.id == id }
            val row = bookmark.copy(id = id)
            if (stored >= 0) rows[stored] = row else rows.add(row)
        }
        notifyChanged()
    }

    override suspend fun deleteAll() {
        rows.clear()
        notifyChanged()
    }
}

class FakeHighlightDao : HighlightDao {

    val rows = mutableListOf<HighlightEntity>()
    private var nextId = 1L
    private val version = MutableStateFlow(0)

    private fun notifyChanged() {
        version.value = version.value + 1
    }

    override fun observeForBook(bookId: Long): Flow<List<HighlightEntity>> =
        version.map { rows.filter { r -> r.bookId == bookId }.sortedByDescending { r -> r.createdAt } }

    override suspend fun getForChapter(bookId: Long, spineHref: String): List<HighlightEntity> =
        rows.filter { it.bookId == bookId && it.spineHref == spineHref }
            .sortedWith(compareBy<HighlightEntity> { it.normalizedStart }.thenBy { it.createdAt })

    override suspend fun insert(highlight: HighlightEntity): Long {
        val id = if (highlight.id != 0L) highlight.id else nextId++
        if (highlight.id == 0L) nextId = maxOf(nextId, id + 1)
        val stored = rows.indexOfFirst { it.id == id }
        val row = highlight.copy(id = id)
        if (stored >= 0) rows[stored] = row else rows.add(row)
        notifyChanged()
        return id
    }

    override suspend fun updateNote(id: Long, note: String?) {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) {
            rows[i] = rows[i].copy(note = note)
            notifyChanged()
        }
    }

    override suspend fun delete(highlight: HighlightEntity) {
        rows.removeAll { it.id == highlight.id }
        notifyChanged()
    }

    override suspend fun getAll(): List<HighlightEntity> =
        rows.sortedByDescending { it.createdAt }

    override suspend fun insertAll(highlights: List<HighlightEntity>) {
        for (highlight in highlights) {
            val id = if (highlight.id != 0L) highlight.id else nextId++
            if (highlight.id == 0L) nextId = maxOf(nextId, id + 1)
            val stored = rows.indexOfFirst { it.id == id }
            val row = highlight.copy(id = id)
            if (stored >= 0) rows[stored] = row else rows.add(row)
        }
        notifyChanged()
    }

    override suspend fun deleteAll() {
        rows.clear()
        notifyChanged()
    }
}

class FakeDictionaryHistoryDao : DictionaryHistoryDao {

    val rows = mutableListOf<DictionaryHistoryEntity>()
    /** Injectable clock so refresh-bumps are observable in tests. */
    var now: () -> Long = { System.currentTimeMillis() }
    private val version = MutableStateFlow(0)

    private fun notifyChanged() {
        version.value = version.value + 1
    }

    override fun observeAll(): Flow<List<DictionaryHistoryEntity>> =
        version.map { rows.sortedByDescending { r -> r.lookedUpAt } }

    override suspend fun getAll(): List<DictionaryHistoryEntity> =
        rows.sortedBy { it.lookedUpAt }

    override suspend fun find(word: String): DictionaryHistoryEntity? =
        rows.firstOrNull { it.word == word }

    override suspend fun insert(entry: DictionaryHistoryEntity) {
        // UNIQUE(word) + REPLACE: inserting an existing word overwrites its row.
        val id = if (entry.id != 0L) entry.id else (rows.maxOfOrNull { it.id } ?: 0L) + 1
        val stored = rows.indexOfFirst { it.id == id || it.word == entry.word }
        val row = entry.copy(id = id)
        if (stored >= 0) rows[stored] = row else rows.add(row)
        notifyChanged()
    }

    override suspend fun refresh(id: Long, definition: String?, partOfSpeech: String?, bookId: Long?, lookedUpAt: Long) {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) {
            rows[i] = rows[i].copy(
                definition = definition,
                partOfSpeech = partOfSpeech,
                bookId = bookId,
                lookedUpAt = lookedUpAt,
            )
            notifyChanged()
        }
    }

    override suspend fun delete(entry: DictionaryHistoryEntity) {
        rows.removeAll { it.id == entry.id }
        notifyChanged()
    }

    override suspend fun clear() {
        rows.clear()
        notifyChanged()
    }
}
