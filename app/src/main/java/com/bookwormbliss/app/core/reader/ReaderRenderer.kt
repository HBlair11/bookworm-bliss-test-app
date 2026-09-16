package com.bookwormbliss.app.core.reader

import com.bookwormbliss.app.core.document.ReaderDocument
import com.bookwormbliss.app.core.location.ReaderPosition

interface ReaderRenderer {
    fun load(document: ReaderDocument)
    fun nextPage()
    fun previousPage()
    fun currentPosition(): ReaderPosition
    fun restore(position: ReaderPosition)
    fun applySettings(settings: ReaderSettings)
    fun currentPage(): Int
    fun pageCount(): Int
}

data class ReaderSettings(
    val theme: String = "alabaster", val font: String = "serif", val fontSize: Int = 26,
    val lineHeight: Float = 1.6f, val margin: Int = 24, val align: String = "left",
    val readingMode: String = "horizontal"
)
