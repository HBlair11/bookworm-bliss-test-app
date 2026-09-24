package com.bookwormbliss.app.epub

import android.content.Context
import android.net.Uri
import com.bookwormbliss.app.data.model.BookEntity
import java.io.File
import java.util.UUID

/**
 * Copies a user-picked EPUB (via Storage Access Framework — the app never
 * requests broad storage permissions) into app-private storage, parses it
 * with [EpubParser], writes its cover + parsed content to disk, and builds
 * the [BookEntity] row for it. Nothing here ever touches the network.
 */
class EpubImporter(private val context: Context) {

    private val booksDir get() = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir get() = File(context.filesDir, "covers").apply { mkdirs() }
    private val contentDir get() = File(context.filesDir, "content").apply { mkdirs() }

    data class ImportResult(val book: BookEntity?, val error: String?)

    /**
     * @param existing When non-null, the parsed content REPLACES this book's
     *   files and metadata but keeps its id (and therefore its bookmarks,
     *   highlights, favorite flag, progress, added date) — this is the path
     *   a folder rescan takes for a file that changed on disk. When null, a
     *   brand-new [BookEntity] with a fresh id is created.
     */
    fun import(
        uri: Uri,
        displayName: String?,
        sourceUri: String? = null,
        sourceLastModified: Long? = null,
        watchedFolderId: String? = null,
        existing: BookEntity? = null,
    ): ImportResult {
        val id = existing?.id ?: UUID.randomUUID().toString()
        val fileName = displayName?.takeIf { it.isNotBlank() } ?: "$id.epub"

        val epubFile = File(booksDir, "$id.epub")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                epubFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return ImportResult(null, "Couldn't open the selected file.")

            val parsed = EpubParser.parse(epubFile, fileName)

            // Replace any previous cover file for this id (extension may differ run to run).
            existing?.coverPath?.let { runCatching { File(it).delete() } }
            val coverPath = parsed.coverBytes?.let { bytes ->
                val coverFile = File(coversDir, "$id.jpg")
                coverFile.writeBytes(bytes)
                coverFile.absolutePath
            }

            val contentFile = File(contentDir, "$id.json")
            EpubContentStore.write(contentFile, parsed.chapters, parsed.toc)

            val now = System.currentTimeMillis()
            val book = if (existing != null) {
                existing.copy(
                    title = parsed.title,
                    sortTitle = sortKey(parsed.title),
                    author = parsed.author,
                    sortAuthor = sortKey(parsed.author),
                    coverPath = coverPath,
                    series = parsed.series,
                    seriesIndex = parsed.seriesIndex,
                    language = parsed.language,
                    publisher = parsed.publisher,
                    description = parsed.description,
                    publishYear = parsed.publishYear,
                    subjectTags = parsed.subjectTags,
                    modifiedDate = now,
                    fileSize = parsed.fileSize,
                    checksum = parsed.checksum,
                    spineCount = parsed.chapters.size,
                    epubPath = epubFile.absolutePath,
                    contentPath = contentFile.absolutePath,
                    sourceUri = sourceUri ?: existing.sourceUri,
                    sourceLastModified = sourceLastModified ?: existing.sourceLastModified,
                    watchedFolderId = watchedFolderId ?: existing.watchedFolderId,
                    // Chapter boundaries may have shifted with the new content,
                    // so the old spine position isn't trustworthy — reset it,
                    // but keep the progress percentage as a rough signpost.
                    spineIndex = 0,
                    scrollRatio = 0f,
                )
            } else {
                BookEntity(
                    id = id,
                    title = parsed.title,
                    sortTitle = sortKey(parsed.title),
                    author = parsed.author,
                    sortAuthor = sortKey(parsed.author),
                    coverPath = coverPath,
                    progress = 0f,
                    spineIndex = 0,
                    scrollRatio = 0f,
                    currentPageInChapter = 0,
                    series = parsed.series,
                    seriesIndex = parsed.seriesIndex,
                    language = parsed.language,
                    publisher = parsed.publisher,
                    description = parsed.description,
                    publishYear = parsed.publishYear,
                    subjectTags = parsed.subjectTags,
                    metadataEdited = false,
                    isCurrentlyReading = false,
                    isFavorite = false,
                    addedDate = now,
                    modifiedDate = now,
                    fileSize = parsed.fileSize,
                    sourceFilename = parsed.sourceFilename,
                    checksum = parsed.checksum,
                    spineCount = parsed.chapters.size,
                    epubPath = epubFile.absolutePath,
                    contentPath = contentFile.absolutePath,
                    sourceUri = sourceUri,
                    sourceLastModified = sourceLastModified,
                    watchedFolderId = watchedFolderId,
                )
            }
            ImportResult(book, null)
        } catch (t: Throwable) {
            if (existing == null) epubFile.delete()
            ImportResult(null, t.message ?: "Couldn't read that file — is it a valid EPUB?")
        }
    }

    private fun sortKey(value: String): String {
        val stripped = value.trim().replace(Regex("(?i)^(the|a|an)\\s+"), "")
        return stripped.lowercase()
    }
}
