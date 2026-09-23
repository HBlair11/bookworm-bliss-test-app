package com.bookwormbliss.app.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Native, offline EPUB parser — a Kotlin port of the web app's
 * src/services/epubParser.ts, using java.util.zip for archive access and
 * Jsoup (in XML mode for container/OPF/NCX, HTML mode for chapter markup)
 * in place of the browser's DOMParser. No network access, no WebView.
 */
object EpubParser {

    private data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String?)

    fun parse(file: File, fileName: String): ParsedEpubBook {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val entryByLowerName = entries.associateBy { it.name.replace('\\', '/').removePrefix("/").lowercase() }

            fun findEntry(targetPath: String): java.util.zip.ZipEntry? {
                if (targetPath.isBlank()) return null
                val normalized = targetPath.replace('\\', '/').removePrefix("/").trim()
                val decoded = runCatching { java.net.URLDecoder.decode(normalized, "UTF-8") }.getOrDefault(normalized)
                zip.getEntry(normalized)?.let { return it }
                zip.getEntry(decoded)?.let { return it }
                entryByLowerName[normalized.lowercase()]?.let { return it }
                entryByLowerName[decoded.lowercase()]?.let { return it }
                val filename = normalized.substringAfterLast('/').lowercase()
                return entries.firstOrNull { it.name.substringAfterLast('/').lowercase() == filename }
            }

            fun readText(entry: java.util.zip.ZipEntry): String =
                zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8)

            fun readBytes(entry: java.util.zip.ZipEntry): ByteArray =
                zip.getInputStream(entry).use { it.readBytes() }

            // 1. container.xml -> OPF path
            val containerEntry = findEntry("META-INF/container.xml")
                ?: entries.firstOrNull { it.name.lowercase().endsWith("container.xml") }
                ?: error("Invalid EPUB: META-INF/container.xml not found in archive")

            val containerDoc: Document = Jsoup.parse(readText(containerEntry), "", Parser.xmlParser())
            var opfPath = containerDoc.selectFirst("rootfile")?.attr("full-path")
            if (opfPath.isNullOrBlank()) {
                opfPath = entries.firstOrNull { it.name.lowercase().endsWith(".opf") }?.name
            }
            requireNotNull(opfPath) { "Invalid EPUB: OPF rootfile path not declared" }
            opfPath = opfPath!!.replace('\\', '/').removePrefix("/")
            val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""

            // 2. OPF
            val opfEntry = findEntry(opfPath) ?: error("Invalid EPUB: OPF file not found at $opfPath")
            val opfDoc: Document = Jsoup.parse(readText(opfEntry), "", Parser.xmlParser())

            fun metaText(vararg selectors: String): String? {
                for (sel in selectors) {
                    opfDoc.selectFirst(sel)?.text()?.trim()?.let { if (it.isNotEmpty()) return it }
                }
                return null
            }

            val title = metaText("metadata > title", "metadata > dc|title") ?: fileName.removeSuffix(".epub").removeSuffix(".EPUB")
            val author = metaText("metadata > creator", "metadata > dc|creator") ?: "Unknown Author"
            val description = metaText("metadata > description", "metadata > dc|description")
            val language = metaText("metadata > language", "metadata > dc|language") ?: "en"
            val publisher = metaText("metadata > publisher", "metadata > dc|publisher")
            val dateStr = metaText("metadata > date", "metadata > dc|date")
            val publishYear = dateStr?.take(4)?.toIntOrNull()

            val metaTags = opfDoc.select("metadata > meta")
            var series: String? = null
            var seriesIndex: Float? = null
            for (m in metaTags) {
                when (m.attr("name")) {
                    "calibre:series" -> m.attr("content").trim().takeIf { it.isNotEmpty() }?.let { series = it }
                    "calibre:series_index" -> m.attr("content").toFloatOrNull()?.let { seriesIndex = it }
                }
            }

            val subjects = opfDoc.select("metadata > subject, metadata > dc|subject").map { it.text().trim() }.filter { it.isNotEmpty() }
            val subjectTags = subjects.takeIf { it.isNotEmpty() }?.joinToString(", ")

            // Manifest
            val manifest = LinkedHashMap<String, ManifestItem>()
            for (el in opfDoc.select("manifest > item")) {
                val id = el.attr("id")
                val href = el.attr("href")
                if (id.isNotEmpty() && href.isNotEmpty()) {
                    manifest[id] = ManifestItem(id, href, el.attr("media-type"), el.attr("properties").ifEmpty { null })
                }
            }

            // 3. Cover extraction (strategies A-G, matching the web parser)
            var coverHref: String? = null
            for (m in metaTags) {
                if (m.attr("name") == "cover") {
                    val id = m.attr("content")
                    if (id.isNotEmpty() && manifest.containsKey(id)) { coverHref = manifest[id]!!.href; break }
                }
            }
            if (coverHref == null) {
                coverHref = manifest.values.firstOrNull { it.properties?.contains("cover-image") == true }?.href
            }
            if (coverHref == null) {
                coverHref = manifest.values.firstOrNull {
                    it.id.lowercase() in setOf("cover", "cover-image", "book-cover", "coverimage")
                }?.href
            }
            if (coverHref == null) {
                coverHref = manifest.values.firstOrNull {
                    it.mediaType.startsWith("image/") && (it.href.lowercase().contains("cover") || it.href.lowercase().contains("jacket"))
                }?.href
            }
            if (coverHref == null) {
                coverHref = opfDoc.selectFirst("guide > reference[type=cover], guide > reference[type=other.ms-coverimage-standard]")?.attr("href")
            }

            var coverEntry: java.util.zip.ZipEntry? = null
            if (coverHref != null) {
                val cleanHref = coverHref!!.substringBefore('#')
                if (Regex("(?i)\\.(x?html?|xml)$").containsMatchIn(cleanHref)) {
                    val coverHtmlPath = resolvePath(opfDir, cleanHref)
                    findEntry(coverHtmlPath)?.let { htmlEntry ->
                        val coverDoc = Jsoup.parse(readText(htmlEntry))
                        val img = coverDoc.selectFirst("img, image")
                        val src = img?.attr("src")?.ifEmpty { null }
                            ?: img?.attr("xlink:href")?.ifEmpty { null }
                            ?: img?.attr("href")?.ifEmpty { null }
                        if (src != null) {
                            val htmlDir = if (coverHtmlPath.contains('/')) coverHtmlPath.substringBeforeLast('/') + "/" else ""
                            coverEntry = findEntry(resolvePath(htmlDir, src))
                        }
                    }
                } else {
                    coverEntry = findEntry(resolvePath(opfDir, cleanHref))
                }
            }
            if (coverEntry == null) {
                coverEntry = entries.firstOrNull {
                    val lower = it.name.lowercase()
                    (lower.contains("cover") || lower.contains("jacket")) && Regex("(?i)\\.(jpe?g|png|webp)$").containsMatchIn(lower)
                }
            }
            if (coverEntry == null) {
                for (item in manifest.values) {
                    if (item.mediaType.startsWith("image/") && !item.href.lowercase().endsWith(".svg")) {
                        findEntry(resolvePath(opfDir, item.href))?.let { coverEntry = it }
                        if (coverEntry != null) break
                    }
                }
            }
            val coverBytes = coverEntry?.let { runCatching { readBytes(it) }.getOrNull() }

            // Spine
            var spineIds = opfDoc.select("spine > itemref").mapNotNull { it.attr("idref").ifEmpty { null } }
            if (spineIds.isEmpty()) {
                spineIds = manifest.values.filter { it.mediaType.contains("html") || it.mediaType.contains("xml") }.map { it.id }
            }

            // TOC (NCX)
            val tocEntries = mutableListOf<TocEntry>()
            val tocId = opfDoc.selectFirst("spine")?.attr("toc")
            var ncxHref = tocId?.let { manifest[it]?.href }
            if (ncxHref == null) {
                ncxHref = manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" || it.href.lowercase().endsWith(".ncx") }?.href
            }
            if (ncxHref != null) {
                findEntry(resolvePath(opfDir, ncxHref!!))?.let { ncxEntry ->
                    runCatching {
                        val ncxDoc = Jsoup.parse(readText(ncxEntry), "", Parser.xmlParser())
                        for (np in ncxDoc.select("navMap > navPoint")) {
                            val text = np.selectFirst("navLabel > text")?.text()?.trim()?.ifEmpty { null } ?: "Chapter"
                            val src = np.selectFirst("content")?.attr("src") ?: ""
                            val cleanSrc = src.substringBefore('#')
                            val spineIndex = spineIds.indexOfFirst { id ->
                                val item = manifest[id] ?: return@indexOfFirst false
                                val fullPath = resolvePath(opfDir, item.href)
                                fullPath.endsWith(cleanSrc) || item.href.endsWith(cleanSrc)
                            }
                            tocEntries += TocEntry(text, src, if (spineIndex >= 0) spineIndex else 0, 0)
                        }
                    }
                }
            }

            // 4. Chapters
            val chapters = mutableListOf<EpubChapter>()
            for (idref in spineIds) {
                val item = manifest[idref] ?: continue
                val chapterPath = resolvePath(opfDir, item.href)
                val chapterEntry = findEntry(chapterPath) ?: continue
                val raw = runCatching { readText(chapterEntry) }.getOrNull() ?: continue
                if (raw.isBlank()) continue

                val chapterDoc = Jsoup.parse(raw)
                val chapterTitle = chapterDoc.selectFirst("h1, h2, h3, title")?.text()?.trim()?.ifEmpty { null }
                    ?: "Chapter ${chapters.size + 1}"

                // Inline sibling images as base64 data URIs so chapters render fully offline.
                val chapterDir = if (chapterPath.contains('/')) chapterPath.substringBeforeLast('/') + "/" else ""
                for (img in chapterDoc.select("img, image")) {
                    val isSvgImage = img.tagName().equals("image", ignoreCase = true)
                    val srcAttr = if (isSvgImage) {
                        img.attr("xlink:href").ifEmpty { img.attr("href") }
                    } else img.attr("src")
                    if (srcAttr.isNotEmpty() && !srcAttr.startsWith("data:") && !srcAttr.startsWith("http")) {
                        val cleanSrc = srcAttr.substringBefore('#').substringBefore('?')
                        findEntry(resolvePath(chapterDir, cleanSrc))?.let { imgEntry ->
                            runCatching {
                                val bytes = readBytes(imgEntry)
                                val mime = mimeTypeFor(imgEntry.name)
                                val dataUri = "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                if (isSvgImage) img.attr("href", dataUri) else img.attr("src", dataUri)
                            }
                        }
                    }
                }

                val bodyHtml = chapterDoc.body()?.html() ?: raw
                val text = chapterDoc.body()?.text()?.replace(Regex("\\s+"), " ")?.trim() ?: ""
                if (bodyHtml.isBlank() && text.isBlank()) continue

                val spineIndex = chapters.size
                chapters += EpubChapter(
                    id = idref.ifEmpty { "ch-$spineIndex" },
                    href = item.href,
                    title = chapterTitle,
                    html = bodyHtml,
                    text = text,
                    spineIndex = spineIndex,
                )
                if (tocEntries.isEmpty()) {
                    tocEntries += TocEntry(chapterTitle, item.href, spineIndex, 0)
                }
            }

            // Fallback: scan for any HTML/XHTML in the archive.
            if (chapters.isEmpty()) {
                val htmlEntries = entries
                    .filter { !it.isDirectory && Regex("(?i)\\.(x?html?|xml)$").containsMatchIn(it.name) && !it.name.lowercase().contains("container.xml") }
                    .sortedBy { it.name }
                htmlEntries.forEachIndexed { i, entry ->
                    runCatching {
                        val raw = readText(entry)
                        val doc = Jsoup.parse(raw)
                        val text = doc.body()?.text()?.replace(Regex("\\s+"), " ")?.trim() ?: ""
                        val body = doc.body()?.html() ?: raw
                        if (body.isNotBlank()) {
                            chapters += EpubChapter(
                                id = "ch-fallback-$i",
                                href = entry.name,
                                title = doc.selectFirst("h1, h2, title")?.text()?.trim()?.ifEmpty { null } ?: "Section ${i + 1}",
                                html = body,
                                text = text,
                                spineIndex = i,
                            )
                        }
                    }
                }
            }

            val fileSize = file.length()
            val checksum = runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buf = ByteArray(65536)
                    val n = input.read(buf)
                    if (n > 0) digest.update(buf, 0, n)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrDefault("epub-$fileSize-${title.take(16)}")

            return ParsedEpubBook(
                title = title,
                author = author,
                description = description,
                publisher = publisher,
                language = language,
                publishYear = publishYear,
                coverBytes = coverBytes,
                series = series,
                seriesIndex = seriesIndex,
                subjectTags = subjectTags,
                chapters = chapters,
                toc = tocEntries,
                fileSize = fileSize,
                checksum = checksum,
                sourceFilename = fileName,
            )
        }
    }

    private fun resolvePath(baseDir: String, relativePath: String): String {
        if (relativePath.isBlank()) return ""
        val clean = relativePath.substringBefore('#').substringBefore('?').trim()
        if (clean.startsWith('/')) return clean.substring(1)
        val parts = (baseDir + clean).split('/')
        val resolved = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> {}
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved += part
            }
        }
        return resolved.joinToString("/")
    }

    private fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
}
