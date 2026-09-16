package com.bookwormbliss.app.core.epub

import com.bookwormbliss.app.core.document.ReaderDocument

enum class EpubParseStatus { VALID, VALID_WITH_WARNINGS, RECOVERED, UNSUPPORTED, INVALID }
data class EpubDiagnostic(val message: String, val fatal: Boolean = false)
data class EpubChapter(val id: String, val href: String, val title: String, val html: String, val spineIndex: Int)
data class EpubTocEntry(val label: String, val href: String, val spineIndex: Int, val level: Int)
data class EpubResult(val status: EpubParseStatus, val diagnostics: List<EpubDiagnostic>, val document: EpubDocument?)
data class EpubDocument(
    override val title: String, override val author: String, val description: String?, val publisher: String?,
    val language: String?, val identifier: String?, val publishYear: Int?, val coverPath: String?,
    val chapters: List<EpubChapter>, val toc: List<EpubTocEntry>, val extractedRoot: String, val sourceFilename: String
) : ReaderDocument { override val spineCount get() = chapters.size }
