package com.bookwormbliss.app.core.epub

import com.bookwormbliss.app.core.reader.ReaderDocument
import java.io.File

/**
 * A fully parsed EPUB document — the end product of the parse pipeline.
 *
 * Pipeline:
 *   EPUB file
 *     ↓
 *   EpubContainer (META-INF/container.xml)
 *     ↓
 *   EpubPackage (OPF: metadata + manifest + spine)
 *     ↓
 *   EpubNavigation (TOC: nav.xhtml or NCX)
 *     ↓
 *   Resource Graph (resolved hrefs)
 *     ↓
 *   EpubDocument
 *
 * This implements [ReaderDocument] so the renderer and UI can work
 * with it format-agnostically. EPUB-specific concepts (spine, manifest,
 * navigation) are accessible via this interface for EPUB-specific features.
 *
 * @property file         The source .epub file.
 * @property container    The parsed container (OPF path info).
 * @property metadata     Bibliographic metadata.
 * @property manifest     All resources in the publication.
 * @property spine        Ordered reading items.
 * @property navigation   Table of contents.
 * @property coverHref    Resolved href of the cover image (or null).
 * @property pageCounts   Per-spine page counts (measured or estimated).
 */
data class EpubDocument(
    val file: File,
    val container: EpubContainer,
    val metadata: EpubMetadata,
    val manifest: EpubManifest,
    val spine: EpubSpine,
    val navigation: EpubNavigation,
    val coverHref: String?,
    override val pageCounts: IntArray = IntArray(spine.size) { 1 },
) : ReaderDocument {

    override val title: String get() = metadata.title.ifBlank { file.nameWithoutExtension }
    override val author: String get() = metadata.authorString
    override val sourceFile: File get() = file
    override val spineSize: Int get() = spine.size

    override fun spineHref(index: Int): String =
        spine.itemAt(index)?.href ?: ""

    override fun resolveHref(href: String, baseDir: String): String =
        com.bookwormbliss.app.epub.EpubPaths.resolve(
            baseDir.ifBlank { container.opfDir },
            href,
        )

    override fun spineIndexForHref(href: String): Int =
        spine.indexForHref(href)

    /** Resolve the cover image href (from manifest or fallback). */
    fun coverEntryPath(): String? =
        coverHref ?: manifest.coverImageItem?.href

    /** The directory containing the OPF (base for relative hrefs). */
    val opfDir: String get() = container.opfDir
}
