package com.bookwormbliss.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Thin application-facing repository over the library database. */
class BookRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val books = db.bookDao()
    private val collections = db.collectionDao()

    fun observeBooks(): Flow<List<BookEntity>> = books.observeAll()
    fun observeHomeRecentlyAdded(): Flow<List<BookEntity>> = books.observeHomeRecentlyAdded()
    fun observeCurrentlyReading(): Flow<List<BookEntity>> = books.observeCurrentlyReading()
    fun observeFavorites(): Flow<List<BookEntity>> = books.observeFavorites()
    fun observeFinished(): Flow<List<BookEntity>> = books.observeFinished()
    fun observeToBeRead(): Flow<List<BookEntity>> = books.observeToBeRead()
    fun observeLastOpened(): Flow<BookEntity?> = books.observeLastOpened()
    fun observeByIds(ids: List<Long>): Flow<List<BookEntity>> =
        if (ids.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else books.observeByIds(ids)

    fun observeAuthors(): Flow<List<GroupedRow>> = books.observeAuthors()
    fun observeSeries(): Flow<List<GroupedRow>> = books.observeSeries()
    fun observeByAuthor(author: String): Flow<List<BookEntity>> = books.observeByAuthor(author)
    fun observeBySeries(series: String): Flow<List<BookEntity>> = books.observeBySeries(series)

    suspend fun getBook(id: Long): BookEntity? = books.getById(id)
    suspend fun getByChecksum(checksum: String): BookEntity? = books.getByChecksum(checksum)
    suspend fun markOpened(id: Long) = books.markOpened(id, System.currentTimeMillis())
    suspend fun updateReaderPosition(
        id: Long,
        progress: Float,
        spineIndex: Int,
        scrollRatio: Float,
        location: String?,
    ) = books.updateReaderPosition(
        id, progress, spineIndex, scrollRatio, location, System.currentTimeMillis()
    )

    suspend fun setCurrentlyReading(id: Long) = books.setCurrentlyReading(id)
    suspend fun clearCurrentlyReading(id: Long) = books.clearCurrentlyReading(id)
    suspend fun setFavorite(id: Long, favorite: Boolean) = books.setFavorite(id, favorite)
    suspend fun deleteBook(book: BookEntity) = books.delete(book)

    fun observeCollections(): Flow<List<CollectionEntity>> = collections.observeAll()
    fun observeBooksInCollection(id: Long): Flow<List<BookEntity>> =
        collections.observeBooksInCollection(id)
    fun observeCollectionsForBook(bookId: Long): Flow<List<CollectionEntity>> =
        collections.observeCollectionsForBook(bookId)

    suspend fun createCollection(name: String): Long {
        val normalized = name.trim()
        collections.getIdByName(normalized)?.let { return it }
        return collections.insert(CollectionEntity(name = normalized))
    }

    suspend fun deleteCollection(collection: CollectionEntity) = collections.delete(collection)
    suspend fun renameCollection(id: Long, name: String) = collections.rename(id, name)
    suspend fun addBookToCollection(bookId: Long, collectionId: Long) =
        collections.addBook(BookCollectionRef(bookId, collectionId))
    suspend fun removeBookFromCollection(bookId: Long, collectionId: Long) =
        collections.removeBook(bookId, collectionId)
}
