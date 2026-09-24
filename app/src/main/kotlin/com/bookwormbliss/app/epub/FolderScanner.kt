package com.bookwormbliss.app.epub

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** One .epub file found while walking a watched folder. */
data class ScannedEpub(
    val uri: Uri,
    val displayName: String,
    val lastModified: Long,
)

/**
 * Walks a SAF folder tree (depth-first, all subfolders included) looking
 * for `.epub` files. Read-only — never modifies anything under the tree.
 */
object FolderScanner {

    fun scan(context: Context, treeUri: Uri): List<ScannedEpub> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<ScannedEpub>()
        walk(root, results)
        return results
    }

    private fun walk(dir: DocumentFile, out: MutableList<ScannedEpub>) {
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            when {
                child.isDirectory -> walk(child, out)
                child.isFile && child.name?.endsWith(".epub", ignoreCase = true) == true -> {
                    out += ScannedEpub(
                        uri = child.uri,
                        displayName = child.name ?: "book.epub",
                        lastModified = child.lastModified(),
                    )
                }
            }
        }
    }
}
