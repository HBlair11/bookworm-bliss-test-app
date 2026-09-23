package com.bookwormbliss.app.epub

/** Mirrors TocEntry. */
data class TocEntry(
    val label: String,
    val href: String,
    val spineIndex: Int,
    val level: Int = 0,
)

/** Mirrors EpubChapter. `html` is a lightly-cleaned body fragment (see EpubParser); `text` is plain text. */
data class EpubChapter(
    val id: String,
    val href: String,
    val title: String,
    val html: String,
    val text: String,
    val spineIndex: Int,
)

/** Mirrors ParsedEpubBook. `coverBytes` is raw image bytes (written to a file by EpubImporter), not a data: URL. */
data class ParsedEpubBook(
    val title: String,
    val author: String,
    val description: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val publishYear: Int? = null,
    val coverBytes: ByteArray? = null,
    val series: String? = null,
    val seriesIndex: Float? = null,
    val subjectTags: String? = null,
    val chapters: List<EpubChapter>,
    val toc: List<TocEntry>,
    val fileSize: Long,
    val checksum: String,
    val sourceFilename: String,
)
