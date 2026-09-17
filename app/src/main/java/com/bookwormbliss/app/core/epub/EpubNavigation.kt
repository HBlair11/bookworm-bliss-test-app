package com.bookwormbliss.app.core.epub

/**
 * A single entry in the table of contents.
 *
 * @property label  Display text for the TOC entry.
 * @property href   Target href (may contain a #fragment).
 * @property level  Nesting level (0 = top-level).
 */
data class EpubNavEntry(
    val label: String,
    val href: String,
    val level: Int = 0,
)

/**
 * EPUB navigation — the table of contents.
 *
 * Supports both EPUB 2 (NCX) and EPUB 3 (nav.xhtml) navigation
 * structures. The parser normalizes both into a flat list with
 * nesting levels.
 */
data class EpubNavigation(
    val entries: List<EpubNavEntry>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /** Map spine href → TOC label for the nearest preceding section. */
    fun sectionMap(spine: EpubSpine): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        if (entries.isEmpty()) return result
        for (i in spine.items.indices) {
            val href = spine.items[i].href
            val label = entries
                .filter { it.href.substringBefore('#') == href }
                .minByOrNull { it.level }
                ?.label
                ?: nearestPrecedingSection(i, spine)
            result[i] = label ?: ""
        }
        return result
    }

    private fun nearestPrecedingSection(spineIndex: Int, spine: EpubSpine): String? {
        if (spineIndex < 0 || spineIndex >= spine.size) return null
        val target = spine.items[spineIndex].href.substringBefore('#')
        var best: EpubNavEntry? = null
        for (entry in entries) {
            val entryHref = entry.href.substringBefore('#')
            val entrySpine = spine.indexForHref(entryHref)
            if (entrySpine in 0..spineIndex) {
                if (best == null || entry.level < best.level) best = entry
            }
        }
        return best?.label
    }
}
