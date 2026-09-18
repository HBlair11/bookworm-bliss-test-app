package com.epubreader.app.core.epub

import com.epubreader.app.epub.EpubBook
import com.epubreader.app.epub.EpubParser
import java.io.File

/**
 * Shared safe-parse bridge between the structured [EpubParsePipeline] and
 * the legacy [EpubParser].
 *
 * The legacy parser remains the rendering source of truth. The pipeline
 * runs alongside it for diagnostics and future migration. The pipeline
 * result is only used when it produces a loadable document AND the spine
 * count matches the legacy parse (parity check) — otherwise the legacy
 * book is used. This ensures the pipeline's experimental navigation
 * parser can't break the reader.
 *
 * Used by both [com.epubreader.app.ReaderActivity.loadBook] and
 * [com.epubreader.app.epub.EpubImporter].
 */
object EpubParseBridge {

    /**
     * Parse an EPUB file safely: run both the pipeline and legacy parser,
     * return the pipeline result if it passes parity, otherwise the legacy book.
     *
     * @param file The .epub file to parse.
     * @return The parsed [EpubBook], or null if both parsers fail.
     */
    fun parse(file: File): EpubBook? {
        val legacyBook = try {
            EpubParser().parse(file)
        } catch (_: Exception) {
            null
        }

        val pipelineResult = try {
            EpubParsePipeline.parse(file)
        } catch (_: Exception) {
            null
        }

        // Use the pipeline book only if it's loadable AND the spine
        // count matches the legacy parse (parity check). Otherwise
        // fall back to the legacy book.
        return if (
            pipelineResult != null &&
            pipelineResult.isLoadable &&
            pipelineResult.document != null &&
            legacyBook != null &&
            pipelineResult.document.spine.size == legacyBook.spine.size
        ) {
            EpubDocumentAdapter.toLegacyBook(pipelineResult.document)
        } else {
            legacyBook
        }
    }
}
