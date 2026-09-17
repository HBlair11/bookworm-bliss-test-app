package com.epubreader.app.core.epub

import com.epubreader.app.epub.EpubPaths
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipFile

/**
 * Parser for EPUB navigation (table of contents).
 *
 * Supports both:
 *  - EPUB 3: nav.xhtml (HTML5 <nav> with <ol>/<li> structure)
 *  - EPUB 2: toc.ncx (XML with <navMap>/<navPoint> structure)
 *
 * Both are normalized into a flat list of [EpubNavEntry] with nesting
 * levels. The parser tries EPUB 3 nav first, then falls back to NCX.
 *
 * NOTE: This parser is experimental. It needs parity tests against
 * the existing EpubParserTest fixture before being wired into production.
 */
object EpubNavigationParser {

    /**
     * Parse navigation from an EPUB zip file.
     *
     * @param file       The .epub file.
     * @param manifest   The parsed manifest (to find the nav/ncx item).
     * @param spine      The parsed spine (to resolve hrefs).
     * @param opfDir     The OPF directory (base for relative hrefs).
     * @return The parsed navigation, or an empty navigation if no TOC found.
     */
    fun parse(
        file: File,
        manifest: EpubManifest,
        spine: EpubSpine,
        opfDir: String,
    ): EpubNavigation {
        // Try EPUB 3 nav first
        val navItem = manifest.navItem
        if (navItem != null) {
            val entries = parseNavXhtml(file, navItem.href, opfDir)
            if (entries.isNotEmpty()) return EpubNavigation(entries)
        }

        // Fall back to EPUB 2 NCX
        val ncxItem = manifest.items.values.firstOrNull {
            it.mediaType == "application/x-dtbncx+xml"
        }
        if (ncxItem != null) {
            val entries = parseNcx(file, ncxItem.href, opfDir)
            if (entries.isNotEmpty()) return EpubNavigation(entries)
        }

        return EpubNavigation(emptyList())
    }

    /**
     * Parse EPUB 3 nav.xhtml. Exposed for testing.
     */
    fun parseNavXhtml(file: File, href: String, opfDir: String): List<EpubNavEntry> {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(href) ?: return emptyList()
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                parseNavXml(xml, opfDir)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse EPUB 3 nav.xhtml XML content.
     *
     * The nav document uses <ol>/<li> nesting with <a> links inside <li>.
     * We track nesting level via <ol> depth, and for each <a> tag we
     * capture the href attribute FIRST (before consuming text), then
     * collect the text content until the matching </a>.
     */
    fun parseNavXml(xml: String, opfDir: String): List<EpubNavEntry> {
        val parser = newParser()
        parser.setInput(xml.reader())
        val entries = mutableListOf<EpubNavEntry>()
        var level = 0

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            val event = parser.eventType

            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "ol" -> level++
                        "a" -> {
                            // Read href attribute BEFORE consuming text content
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            val label = collectTextUntilEndTag(parser, "a")
                            if (label.isNotBlank() && level > 0) {
                                val resolved = if (href.isNotBlank())
                                    EpubPaths.resolve(opfDir, href) else ""
                                entries.add(EpubNavEntry(label.trim(), resolved, level - 1))
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "ol") {
                        level = (level - 1).coerceAtLeast(0)
                    }
                }
            }

            // Only advance if we didn't already consume tags via collectTextUntilEndTag
            if (parser.eventType == event) {
                parser.next()
            }
        }
        return entries
    }

    /**
     * Parse EPUB 2 NCX. Exposed for testing.
     */
    fun parseNcx(file: File, href: String, opfDir: String): List<EpubNavEntry> {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(href) ?: return emptyList()
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                parseNcxXml(xml, opfDir)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse EPUB 2 NCX XML content.
     *
     * NCX uses <navPoint> nesting with <navLabel>/<text> for labels
     * and <content src="..."> for targets.
     */
    fun parseNcxXml(xml: String, opfDir: String): List<EpubNavEntry> {
        val parser = newParser()
        parser.setInput(xml.reader())
        val entries = mutableListOf<EpubNavEntry>()
        var level = 0

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            val event = parser.eventType

            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "navPoint" -> level++
                        "text" -> {
                            val label = collectTextUntilEndTag(parser, "text")
                            // Store as pending label — we need to read the content src next
                            // We'll use a simple state machine: the next <content> tag's src
                            // pairs with this label at the current level
                            if (label.isNotBlank()) {
                                pendingLabel = label.trim()
                                pendingLevel = level - 1
                            }
                        }
                        "content" -> {
                            val src = parser.getAttributeValue(null, "src") ?: ""
                            if (pendingLabel != null) {
                                val resolved = if (src.isNotBlank())
                                    EpubPaths.resolve(opfDir, src) else ""
                                entries.add(EpubNavEntry(pendingLabel!!, resolved, pendingLevel))
                                pendingLabel = null
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "navPoint") {
                        level = (level - 1).coerceAtLeast(0)
                        pendingLabel = null
                    }
                }
            }

            if (parser.eventType == event) {
                parser.next()
            }
        }
        return entries
    }

    // NCX pending state (simple state machine for label → content pairing)
    private var pendingLabel: String? = null
    private var pendingLevel: Int = 0

    /**
     * Collect text content until we reach the matching end tag.
     * This properly handles mixed content and nested elements.
     * After calling this, the parser is positioned at the END_TAG.
     */
    private fun collectTextUntilEndTag(parser: XmlPullParser, tagName: String): String {
        val sb = StringBuilder()
        var depth = 1

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth == 0) return sb.toString().trim()
                }
            }
        }
        return sb.toString().trim()
    }

    private fun newParser(): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
}
