package com.epubreader.app.core.epub

/**
 * A spine entry — an ordered reading item.
 *
 * The spine defines the linear reading order of the publication.
 * Each entry references a manifest item by idref.
 *
 * @property idref     Manifest item id this spine entry references.
 * @property href      Resolved zip-entry path.
 * @property mediaType MIME type.
 * @property linear    Whether this item is part of the linear reading order.
 */
data class EpubSpineItem(
    val idref: String,
    val href: String,
    val mediaType: String,
    val linear: Boolean = true,
)

/**
 * The EPUB spine — the ordered list of reading items.
 */
data class EpubSpine(
    val items: List<EpubSpineItem>,
) {
    val size: Int get() = items.size

    /** Get the spine item at a given index, or null. */
    fun itemAt(index: Int): EpubSpineItem? =
        items.getOrNull(index)

    /** Find the spine index for a given href (-1 if not found). */
    fun indexForHref(href: String): Int {
        val target = normalize(href)
        return items.indexOfFirst { normalize(it.href) == target }.let { if (it < 0) -1 else it }
    }

    private fun normalize(href: String): String {
        var h = href
        val frag = h.indexOf('#')
        if (frag >= 0) h = h.substring(0, frag)
        return h.trimEnd('/')
    }
}
