package com.bookwormbliss.app.epub

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists a book's parsed chapters + table of contents as a small JSON file
 * under app-private storage (one file per book, named by book id). This is
 * the on-disk half of a book's "content" — BookEntity.contentPath points at
 * the file this class reads/writes. Kept deliberately dependency-free
 * (org.json ships with Android) rather than pulling in kotlinx.serialization
 * for what is a very small, simple schema.
 */
object EpubContentStore {

    fun write(file: File, chapters: List<EpubChapter>, toc: List<TocEntry>) {
        val root = JSONObject()
        val chaptersArr = JSONArray()
        chapters.forEach { c ->
            chaptersArr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("href", c.href)
                    .put("title", c.title)
                    .put("html", c.html)
                    .put("text", c.text)
                    .put("spineIndex", c.spineIndex),
            )
        }
        val tocArr = JSONArray()
        toc.forEach { t ->
            tocArr.put(
                JSONObject()
                    .put("label", t.label)
                    .put("href", t.href)
                    .put("spineIndex", t.spineIndex)
                    .put("level", t.level),
            )
        }
        root.put("chapters", chaptersArr)
        root.put("toc", tocArr)
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }

    fun readChapters(file: File): List<EpubChapter> {
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText())
        val arr = root.optJSONArray("chapters") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            EpubChapter(
                id = o.getString("id"),
                href = o.getString("href"),
                title = o.getString("title"),
                html = o.getString("html"),
                text = o.getString("text"),
                spineIndex = o.getInt("spineIndex"),
            )
        }
    }

    fun readToc(file: File): List<TocEntry> {
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText())
        val arr = root.optJSONArray("toc") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TocEntry(
                label = o.getString("label"),
                href = o.getString("href"),
                spineIndex = o.getInt("spineIndex"),
                level = o.optInt("level", 0),
            )
        }
    }
}
