package com.epubreader.app.core.epub

/**
 * A single manifest item from the OPF.
 *
 * The manifest lists every resource in the EPUB (XHTML, CSS, images,
 * fonts, etc.). Each item has an id, a resolved zip-entry path, a
 * media type, and optional properties (e.g. "nav", "cover-image").
 *
 * @property id          Unique id within the OPF manifest.
 * @property href        Resolved zip-entry path.
 * @property mediaType   MIME type (e.g. "application/xhtml+xml").
 * @property properties  Space-separated properties (e.g. "nav cover-image").
 */
data class EpubManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String?,
)

/**
 * The EPUB manifest — a map of all resources in the publication.
 */
data class EpubManifest(
    val items: Map<String, EpubManifestItem>,
) {
    /** Look up a manifest item by id. */
    fun byId(id: String): EpubManifestItem? = items[id]

    /** Find the item with the "nav" property (EPUB 3 navigation). */
    val navItem: EpubManifestItem?
        get() = items.values.firstOrNull { it.properties?.contains("nav") == true }

    /** Find the item with the "cover-image" property. */
    val coverImageItem: EpubManifestItem?
        get() = items.values.firstOrNull { it.properties?.contains("cover-image") == true }
}
