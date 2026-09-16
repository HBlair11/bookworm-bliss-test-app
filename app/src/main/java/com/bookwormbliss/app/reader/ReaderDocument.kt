package com.bookwormbliss.app.reader

import com.bookwormbliss.app.epub.EpubBook

/** Format-specific reader document model exposed to the rendering layer. */
class ReaderDocument(
    val epub: EpubBook,
) {
    val spineSize: Int get() = epub.spine.size

    fun chapter(index: Int) = epub.spine.getOrNull(index)

    fun chapterLabel(index: Int): String =
        epub.toc.firstOrNull { epub.spineIndexForHref(it.href.substringBefore('#')) == index }?.label
            ?: chapter(index)?.href?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank { null }
            ?: "Section ${index + 1}"

    fun position(index: Int, ratio: Float): ReaderDocumentPosition =
        ReaderDocumentPosition(index.coerceIn(0, (spineSize - 1).coerceAtLeast(0)), ratio).normalized()
}
