package com.epubreader.app.core.epub

/**
 * EPUB metadata — bibliographic information from the OPF.
 *
 * This is the format-specific metadata model. It maps to the more
 * generic [com.epubreader.app.core.reader.ReaderDocument] metadata
 * but keeps EPUB-specific fields that don't apply to other formats.
 *
 * @property title          Book title.
 * @property authors        List of author names.
 * @property language       Language code (e.g. "en").
 * @property publisher      Publisher name.
 * @property description    Description / synopsis.
 * @property identifiers    List of identifiers (ISBN, UUID, etc.).
 * @property subjects       List of subject/genre tags.
 * @property series         Series name (from calibre/meta property).
 * @property seriesIndex    Position within the series.
 * @property publishDate    Publication date string.
 */
data class EpubMetadata(
    var title: String = "",
    val authors: MutableList<String> = mutableListOf(),
    var language: String? = null,
    var publisher: String? = null,
    var description: String? = null,
    val identifiers: MutableList<String> = mutableListOf(),
    val subjects: MutableList<String> = mutableListOf(),
    var series: String? = null,
    var seriesIndex: Double? = null,
    var publishDate: String? = null,
) {
    val authorString: String
        get() = authors.joinToString(", ").ifBlank { "Unknown Author" }
}
