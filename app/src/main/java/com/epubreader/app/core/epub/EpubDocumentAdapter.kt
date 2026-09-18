package com.epubreader.app.core.epub

import com.epubreader.app.epub.EpubBook
import com.epubreader.app.epub.EpubMetadata as LegacyEpubMetadata
import com.epubreader.app.epub.ManifestItem
import com.epubreader.app.epub.SpineItem
import com.epubreader.app.epub.TocEntry

/**
 * Adapter that converts a core-layer [EpubDocument] to a legacy [EpubBook].
 *
 * This enables the [EpubParsePipeline] to be wired into production without
 * rewriting every consumer of [EpubBook]. The pipeline produces structured
 * diagnostics and a clean [EpubDocument]; this adapter bridges back to the
 * legacy model that ReaderActivity, EpubResourceResolver, and feature
 * services already consume.
 *
 * The mapping is 1:1 — every field in [EpubBook] has a direct counterpart
 * in [EpubDocument]. No data is lost or synthesized.
 */
object EpubDocumentAdapter {

    /**
     * Convert an [EpubDocument] to a legacy [EpubBook].
     *
     * @param document The parsed EPUB document from the pipeline.
     * @return An equivalent [EpubBook] for legacy consumers.
     */
    fun toLegacyBook(document: EpubDocument): EpubBook {
        val legacyMetadata = LegacyEpubMetadata().apply {
            title = document.metadata.title
            authors.addAll(document.metadata.authors)
            language = document.metadata.language
            publisher = document.metadata.publisher
            description = document.metadata.description
            identifiers.addAll(document.metadata.identifiers)
            subjects.addAll(document.metadata.subjects)
            series = document.metadata.series
            seriesIndex = document.metadata.seriesIndex
            publishDate = document.metadata.publishDate
        }

        val legacyManifest: Map<String, ManifestItem> = document.manifest.items
            .mapValues { (_, item) ->
                ManifestItem(
                    id = item.id,
                    href = item.href,
                    mediaType = item.mediaType,
                    properties = item.properties,
                )
            }

        val legacySpine: List<SpineItem> = document.spine.items.map { item ->
            SpineItem(
                idref = item.idref,
                href = item.href,
                mediaType = item.mediaType,
                linear = item.linear,
            )
        }

        val legacyToc: List<TocEntry> = document.navigation.entries.map { entry ->
            TocEntry(
                label = entry.label,
                href = entry.href,
                level = entry.level,
            )
        }

        return EpubBook(
            file = document.file,
            metadata = legacyMetadata,
            manifest = legacyManifest,
            spine = legacySpine,
            toc = legacyToc,
            coverHref = document.coverEntryPath(),
            opfDir = document.opfDir,
        )
    }
}
