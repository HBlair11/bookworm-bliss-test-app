package com.epubreader.app.core.epub

import com.epubreader.app.epub.EpubPaths
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipFile

/**
 * Parser for the EPUB container layer (META-INF/container.xml).
 *
 * The container is the outermost layer of an EPUB: it's a small XML
 * file that points to the root OPF (Package Document). This parser
 * extracts the OPF path and derives the base directory.
 *
 * This is deliberately separated from the package parser so each
 * step has a single responsibility and can be tested independently.
 */
object EpubContainerParser {

    /**
     * Parse the container.xml from an EPUB zip file.
     *
     * @param file The .epub file.
     * @return The parsed container, or null if META-INF/container.xml
     *         is missing or malformed.
     */
    fun parse(file: File): EpubContainer? {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("META-INF/container.xml")
                    ?: return null
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                parseContainerXml(file, xml)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse container.xml content. Exposed for testing.
     */
    fun parseContainerXml(file: File, xml: String): EpubContainer? {
        val parser = newParser()
        parser.setInput(xml.reader())

        var opfPath: String? = null
        var inRootfile = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "rootfile") {
                        inRootfile = true
                        val mediaType = parser.getAttributeValue(null, "media-type")
                        val fullPath = parser.getAttributeValue(null, "full-path")
                        if (fullPath != null && (mediaType == null || mediaType == "application/oebps-package+xml")) {
                            opfPath = fullPath
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "rootfile") inRootfile = false
                }
            }
            parser.next()
        }

        if (opfPath.isNullOrBlank()) return null

        val opfDir = EpubPaths.parentDir(opfPath)
        return EpubContainer(
            file = file,
            opfPath = opfPath,
            opfDir = opfDir,
        )
    }

    private fun newParser(): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
}
