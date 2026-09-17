package com.bookwormbliss.app.core.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Handler
import android.os.Looper
import com.bookwormbliss.app.core.epub.EpubParser
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EpubLibraryImporter(private val context: Context) {
    data class Outcome(val added: List<Book>, val duplicates: Int, val failures: List<String>)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun importUris(uris: List<Uri>, onFinished: (Outcome) -> Unit) = executor.execute {
        val outcome = process(uris)
        Handler(Looper.getMainLooper()).post { onFinished(outcome) }
    }

    fun scanTree(treeUri: Uri, onFinished: (Outcome) -> Unit) = executor.execute {
        val uris = mutableListOf<Uri>()
        collectEpubs(treeUri, uris)
        val outcome = process(uris)
        Handler(Looper.getMainLooper()).post { onFinished(outcome) }
    }

    fun shutdown() { executor.shutdownNow() }

    private fun process(uris: List<Uri>): Outcome {
        val added = mutableListOf<Book>()
        var duplicates = 0
        val failures = mutableListOf<String>()
        val store = BookStore(context)
        val parser = EpubParser(context)
        for (uri in uris.distinct()) {
            try {
                val checksum = sha256(uri)
                if (store.findByChecksum(checksum) != null) { duplicates++; continue }
                val result = parser.parse(uri)
                val document = result.document
                if (document == null) {
                    failures += "${displayName(uri)}: ${result.diagnostics.lastOrNull()?.message ?: "Invalid EPUB"}"
                    continue
                }
                if (store.findByIdentity(document.identifier, document.title, document.author) != null) { duplicates++; continue }
                val now = System.currentTimeMillis()
                val book = Book(
                    id = "book_${now}_${added.size}", title = document.title.ifBlank { displayName(uri).removeSuffix(".epub") },
                    author = document.author.ifBlank { "Unknown Author" }, coverPath = document.coverPath,
                    description = document.description, publisher = document.publisher, language = document.language,
                    year = document.publishYear, identifier = document.identifier, checksum = checksum,
                    progress = 0f, spineIndex = 0, pageIndex = 0, pageCount = 1,
                    isFavorite = false, isCurrentlyReading = false, addedDate = now, lastOpened = 0L,
                    sourceFilename = document.sourceFilename, extractedRoot = document.extractedRoot
                )
                store.save(book); added += book
            } catch (e: Exception) {
                failures += "${displayName(uri)}: ${e.message ?: "Import failed"}"
            }
        }
        return Outcome(added, duplicates, failures)
    }

    private fun collectEpubs(tree: Uri, out: MutableList<Uri>) {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        context.contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val id = c.getString(idCol)
                val name = c.getString(nameCol) ?: ""
                val mime = c.getString(mimeCol) ?: ""
                val child = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) collectEpubs(child, out)
                else if (name.endsWith(".epub", true) || mime.equals("application/epub+zip", true)) out += child
            }
        }
    }

    private fun displayName(uri: Uri): String = context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: (uri.lastPathSegment ?: "book.epub")

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read EPUB" }
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n <= 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
