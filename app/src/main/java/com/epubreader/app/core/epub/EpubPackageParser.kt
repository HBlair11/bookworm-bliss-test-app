package com.epubreader.app.core.epub

import com.epubreader.app.epub.EpubPaths
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipFile

/**
 * Parser for the EPUB package layer (the OPF file).
 *
 * The OPF (Open Packaging Format) contains:
 *  - Metadata (title, author, language, etc.)
 *  - Manifest (all resources)
 *  - Spine (reading order)
 *
 * This parser extracts all three into the domain models. It's
 * separated from the container parser and navigation parser so
 * each has a single responsibility.
 */
object EpubPackageParser {

    data class PackageResult(
        val metadata: EpubMetadata,
        val manifest: EpubManifest,
        val spine: EpubSpine,
        val coverHref: String?,
    )

    /**
     * Parse the OPF from an EPUB zip file.
     *
     * @param container The parsed container (knows the OPF path).
     * @return The parsed package, or null if the OPF is missing/malformed.
     */
    fun parse(container: EpubContainer): PackageResult? {
        return try {
            ZipFile(container.file).use { zip ->
                val entry = zip.getEntry(container.opfPath)
                    ?: return null
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                parseOpfXml(xml, container.opfDir)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse OPF XML content. Exposed for testing.
     */
    fun parseOpfXml(xml: String, opfDir: String): PackageResult? {
        val parser = newParser()
        parser.setInput(xml.reader())

        val metadata = EpubMetadata()
        val manifestItems = mutableMapOf<String, EpubManifestItem>()
        val spineItems = mutableListOf<EpubSpineItem>()
        var coverHref: String? = null

        var section = ""
        var currentElement = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentElement = parser.name
                    when (parser.name) {
                        "metadata" -> section = "metadata"
                        "manifest" -> section = "manifest"
                        "spine" -> section = "spine"
                    }
                    when (section) {
                        "metadata" -> parseMetadataTag(parser, metadata)
                        "manifest" -> {
                            val item = parseManifestItem(parser, opfDir)
                            if (item != null) {
                                manifestItems[item.id] = item
                                if (item.properties?.contains("cover-image") == true) {
                                    coverHref = item.href
                                }
                            }
                        }
                        "spine" -> {
                            val item = parseSpineItem(parser, manifestItems)
                            if (item != null) spineItems.add(item)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "metadata" || parser.name == "manifest" || parser.name == "spine") {
                        section = ""
                    }
                    currentElement = ""
                }
            }
            parser.next()
        }

        // Fallback cover detection
        if (coverHref == null) {
            coverHref = manifestItems.values.firstOrNull {
                it.mediaType.startsWith("image/") && (it.id.contains("cover", ignoreCase = true))
            }?.href
        }

        return PackageResult(
            metadata = metadata,
            manifest = EpubManifest(manifestItems),
            spine = EpubSpine(spineItems),
            coverHref = coverHref,
        )
    }

    private fun parseMetadataTag(parser: XmlPullParser, metadata: EpubMetadata) {
        when (parser.name) {
            "title" -> {
                val text = parser.nextText()
                if (text.isNotBlank()) metadata.title = text.trim()
            }
            "creator" -> {
                val text = parser.nextText()
                if (text.isNotBlank()) metadata.authors.add(text.trim())
            }
            "language" -> {
                val text = parser.nextText()
                if (text.isNotBlank()) metadata.language = text.trim()
            }
            "publisher" -> {
                val text = parser.nextText()
                if (text.isNotBlank()) metadata.publisher = text.trim()
            }
            "description" -> {
                val text = parser.nextText()
                if (text.isNotBlank()) metadata.description = text.trim()
            }
            "identifier" -> {
                val text = parser.nextText()
                if (text.isNotBlank()) metadata.identifiers.add(text.trim())
            }
            "subject" -> {
                val text = parser.nextText()
                if (text.isNotBlank()) metadata.subjects.add(text.trim())
            }
            "date" -> {
                val text = parser.nextText()
                if (text.isNotBlank()) metadata.publishDate = text.trim()
            }
            "meta" -> {
                val name = parser.getAttributeValue(null, "name")
                val content = parser.getAttributeValue(null, "content")
                val refines = parser.getAttributeValue(null, "refines")
                if (refines == null && content != null) {
                    when (name) {
                        "calibre:series", "series" -> metadata.series = content
                        "calibre:series_index", "series_index" -> metadata.seriesIndex = content.toDoubleOrNull()
                    }
                }
            }
        }
    }

    private fun parseManifestItem(parser: XmlPullParser, opfDir: String): EpubManifestItem? {
        if (parser.name != "item") return null
        val id = parser.getAttributeValue(null, "id") ?: return null
        val href = parser.getAttributeValue(null, "href") ?: return null
        val mediaType = parser.getAttributeValue(null, "media-type") ?: "application/octet-stream"
        val properties = parser.getAttributeValue(null, "properties")
        val resolved = EpubPaths.resolve(opfDir, href)
        return EpubManifestItem(id, resolved, mediaType, properties)
    }

    private fun parseSpineItem(parser: XmlPullParser, manifest: Map<String, EpubManifestItem>): EpubSpineItem? {
        if (parser.name != "itemref") return null
        val idref = parser.getAttributeValue(null, "idref") ?: return null
        val linear = parser.getAttributeValue(null, "linear")?.let { it != "no" } ?: true
        val manifestItem = manifest[idref] ?: return null
        return EpubSpineItem(
            idref = idref,
            href = manifestItem.href,
            mediaType = manifestItem.mediaType,
            linear = linear,
        )
    }

    private fun newParser(): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
}
