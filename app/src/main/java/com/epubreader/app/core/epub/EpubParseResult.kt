package com.epubreader.app.core.epub

import com.epubreader.app.core.reader.ReaderDocument
import java.io.File

/**
 * The result of parsing an EPUB file.
 *
 * Wraps the parsed [EpubDocument] (if loadable) with a [status] and
 * structured [diagnostics]. Callers should always check [status] before
 * using [document].
 *
 * Example:
 * ```
 * val result = EpubContainerParser.parse(file)
 * when (result.status) {
 *     EpubParseStatus.VALID, EpubParseStatus.VALID_WITH_WARNINGS -> {
 *         val book = result.document!!
 *         // render the book
 *     }
 *     EpubParseStatus.RECOVERED -> {
 *         // show warnings but still load
 *         result.diagnostics.forEach { log(it.message) }
 *         val book = result.document!!
 *     }
 *     EpubParseStatus.INVALID -> {
 *         // show error to user
 *     }
 * }
 * ```
 */
data class EpubParseResult(
    val document: EpubDocument?,
    val status: EpubParseStatus,
    val diagnostics: List<EpubDiagnostic> = emptyList(),
) {
    /** True if the document can be loaded (status is not INVALID). */
    val isLoadable: Boolean
        get() = status.isLoadable && document != null

    /** Convenience: the document, or throws if invalid. */
    fun requireDocument(): EpubDocument =
        document ?: throw IllegalStateException("EPUB parse failed: ${diagnostics.joinToString { it.message }}")

    /** All error diagnostics. */
    val errors: List<EpubDiagnostic>
        get() = diagnostics.filter { it.isError }

    /** All warning diagnostics. */
    val warnings: List<EpubDiagnostic>
        get() = diagnostics.filter { it.isWarning }

    companion object {
        fun valid(document: EpubDocument): EpubParseResult =
            EpubParseResult(document, EpubParseStatus.VALID)

        fun validWithWarnings(document: EpubDocument, diagnostics: List<EpubDiagnostic>): EpubParseResult =
            EpubParseResult(document, EpubParseStatus.VALID_WITH_WARNINGS, diagnostics)

        fun recovered(document: EpubDocument, diagnostics: List<EpubDiagnostic>): EpubParseResult =
            EpubParseResult(document, EpubParseStatus.RECOVERED, diagnostics)

        fun invalid(diagnostics: List<EpubDiagnostic>): EpubParseResult =
            EpubParseResult(null, EpubParseStatus.INVALID, diagnostics)

        fun unsupported(document: EpubDocument?, diagnostics: List<EpubDiagnostic>): EpubParseResult =
            EpubParseResult(document, EpubParseStatus.UNSUPPORTED, diagnostics)
    }
}
