package com.bookwormbliss.app.core.epub

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class EpubParser(private val context: Context) {
    fun parse(uri: Uri): EpubResult {
        val diagnostics = mutableListOf<EpubDiagnostic>()
        val source = File(context.filesDir, "imports/${System.currentTimeMillis()}_${safeName(uri.lastPathSegment ?: "book.epub")}")
        source.parentFile?.mkdirs()
        try { context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(source).use { output -> input.copyTo(output) }
            } ?: return EpubResult(EpubParseStatus.INVALID, listOf(EpubDiagnostic("Unable to read the selected EPUB.", true)), null)
        } catch (e: Exception) { return EpubResult(EpubParseStatus.INVALID, listOf(EpubDiagnostic("Unable to copy EPUB: ${e.message}", true)), null) }
        return parseFile(source)
    }

    fun parseFile(source: File): EpubResult {
        val diagnostics = mutableListOf<EpubDiagnostic>()
        val root = File(context.filesDir, "books/${source.nameWithoutExtension}_${source.length()}")
        if (root.exists()) root.deleteRecursively(); root.mkdirs()
        try {
            ZipFile(source).use { zip ->
                if (zip.getEntry("mimetype") == null) diagnostics += EpubDiagnostic("Missing EPUB mimetype; attempting recovery.")
                zip.entries().asSequence().forEach { e ->
                    val safe = File(root, e.name).canonicalFile
                    if (!safe.path.startsWith(root.canonicalPath + File.separator)) throw SecurityException("Unsafe ZIP entry: ${e.name}")
                    if (e.isDirectory) safe.mkdirs() else { safe.parentFile?.mkdirs(); zip.getInputStream(e).use { input -> FileOutputStream(safe).use { input.copyTo(it) } } }
                }
            }
            val container = File(root, "META-INF/container.xml")
            if (!container.exists()) return invalid("Missing META-INF/container.xml", diagnostics)
            val opfPath = xpathText(container, "//*[local-name()='rootfile'][1]/@full-path") ?: return invalid("Unable to locate OPF package document", diagnostics)
            val opf = File(root, opfPath)
            if (!opf.exists()) return invalid("OPF not found: $opfPath", diagnostics)
            val base = opf.parentFile ?: root
            val doc = db(opf)
            val metadata = doc.getElementsByTagNameNS("*", "metadata").item(0)
            fun meta(name: String): String? { val n = doc.getElementsByTagNameNS("*", name).item(0); return n?.textContent?.trim()?.takeIf { it.isNotEmpty() } }
            val title = meta("title") ?: "Untitled"
            val author = meta("creator") ?: "Unknown Author"
            val description = meta("description"); val publisher = meta("publisher"); val language = meta("language"); val identifier = meta("identifier")
            val year = meta("date")?.take(4)?.toIntOrNull()
            val manifest = linkedMapOf<String, Pair<String,String>>()
            val mi = doc.getElementsByTagNameNS("*", "item")
            for (i in 0 until mi.length) { val n=mi.item(i) as Element; manifest[n.getAttribute("id")]=n.getAttribute("href") to n.getAttribute("media-type") }
            val spineIds = mutableListOf<String>(); val si=doc.getElementsByTagNameNS("*", "itemref")
            for (i in 0 until si.length) { val n=si.item(i) as Element; spineIds += n.getAttribute("idref") }
            val chapters = spineIds.mapIndexedNotNull { index, id ->
                val pair=manifest[id] ?: return@mapIndexedNotNull null
                val f=resolve(base,pair.first); if (!f.exists()) { diagnostics += EpubDiagnostic("Missing spine resource: ${pair.first}"); return@mapIndexedNotNull null }
                val href=relativeHref(opf.parentFile!!,f,root); val html=f.readText(Charsets.UTF_8)
                EpubChapter(id, href, chapterTitle(html, index), html, index)
            }
            if (chapters.isEmpty()) return invalid("No readable spine documents were found", diagnostics)
            val toc = buildToc(doc, manifest, base, root, chapters, diagnostics)
            val cover = findCover(doc, manifest, base, root)
            val status = if (diagnostics.isEmpty()) EpubParseStatus.VALID else EpubParseStatus.VALID_WITH_WARNINGS
            return EpubResult(status, diagnostics, EpubDocument(title, author, description, publisher, language, identifier, year, cover?.absolutePath, chapters, toc, root.absolutePath, source.name))
        } catch (e: SecurityException) { return invalid(e.message ?: "Unsafe EPUB", diagnostics) }
          catch (e: Exception) { return invalid("EPUB parsing failed: ${e.message}", diagnostics) }
    }

    private fun buildToc(opf: org.w3c.dom.Document, manifest: Map<String,Pair<String,String>>, base: File, root: File, chapters: List<EpubChapter>, d: MutableList<EpubDiagnostic>): List<EpubTocEntry> {
        val navId = manifest.entries.firstOrNull { it.value.second.contains("nav") }?.key
        val navHref = navId?.let { manifest[it]?.first }
        if (navHref != null) {
            val navFile = resolve(base, navHref)
            if (navFile.exists()) {
                try {
                    val x = db(navFile)
                    val links = x.getElementsByTagNameNS("*", "a")
                    val entries = (0 until links.length).mapNotNull { i ->
                        val a = links.item(i) as Element
                        val href = a.getAttribute("href")
                        val target = href.substringBefore('#')
                        val idx = chapters.indexOfFirst { it.href.substringBefore('#').endsWith(target.substringAfterLast('/')) }
                        if (idx >= 0) EpubTocEntry(a.textContent.trim(), chapters[idx].href, idx, 0) else null
                    }
                    if (entries.isNotEmpty()) return entries
                } catch (_: Exception) { d += EpubDiagnostic("Navigation document could not be fully parsed.") }
            }
        }

        // EPUB 2 fallback: locate the NCX referenced by spine toc="...".
        val spineNodes = opf.getElementsByTagNameNS("*", "spine")
        val tocId = if (spineNodes.length > 0) (spineNodes.item(0) as Element).getAttribute("toc").takeIf { it.isNotBlank() } else null
        val ncxHref = tocId?.let { manifest[it]?.first }
        if (ncxHref != null) {
            val ncxFile = resolve(base, ncxHref)
            if (ncxFile.exists()) {
                try {
                    val x = db(ncxFile)
                    val navPoints = x.getElementsByTagNameNS("*", "navPoint")
                    val entries = (0 until navPoints.length).mapNotNull { i ->
                        val point = navPoints.item(i) as Element
                        val label = point.getElementsByTagNameNS("*", "text").item(0)?.textContent?.trim().orEmpty()
                        val content = point.getElementsByTagNameNS("*", "content").item(0) as? Element
                        val href = content?.getAttribute("src").orEmpty()
                        val target = href.substringBefore('#')
                        val idx = chapters.indexOfFirst { it.href.substringBefore('#').endsWith(target.substringAfterLast('/')) }
                        if (idx >= 0 && label.isNotBlank()) EpubTocEntry(label, chapters[idx].href, idx, 0) else null
                    }
                    if (entries.isNotEmpty()) return entries
                } catch (_: Exception) { d += EpubDiagnostic("EPUB 2 NCX navigation could not be fully parsed.") }
            }
        }
        return chapters.map { EpubTocEntry(it.title, it.href, it.spineIndex, 0) }
    }
    private fun findCover(doc: org.w3c.dom.Document, manifest: Map<String,Pair<String,String>>, base: File, root: File): File? {
        val meta=doc.getElementsByTagNameNS("*","meta"); var coverId:String?=null
        for(i in 0 until meta.length){val e=meta.item(i) as Element; if(e.getAttribute("name").equals("cover",true)) coverId=e.getAttribute("content")}
        val href=coverId?.let{manifest[it]?.first} ?: manifest.values.firstOrNull{it.second.startsWith("image/") }?.first
        return href?.let{resolve(base,it).takeIf(File::exists)}
    }
    private fun chapterTitle(html:String,index:Int):String { val m=Regex("<h[1-6][^>]*>(.*?)</h[1-6]>",RegexOption.IGNORE_CASE).find(html); return m?.groupValues?.get(1)?.replace(Regex("<[^>]+>"),"")?.trim()?.ifEmpty{null} ?: "Chapter ${index+1}" }
    private fun resolve(base:File, href:String)=File(base,href.substringBefore('#').replace("/",File.separator)).canonicalFile
    private fun relativeHref(base:File,f:File,root:File)=f.relativeTo(root).path.replace(File.separatorChar,'/')
    private fun db(file:File)=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(file)
    private fun xpathText(file:File,expr:String):String?=try{ val d=db(file); val x=javax.xml.xpath.XPathFactory.newInstance().newXPath(); x.evaluate(expr,d).trim().takeIf{it.isNotEmpty()} }catch(_:Exception){null}
    private fun invalid(msg:String,d:List<EpubDiagnostic>)=EpubResult(EpubParseStatus.INVALID,d+EpubDiagnostic(msg,true),null)
    private fun safeName(s:String)=s.replace(Regex("[^A-Za-z0-9._-]"),"_")
}
