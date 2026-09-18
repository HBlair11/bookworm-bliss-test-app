package com.epubreader.app.features.search

import com.epubreader.app.epub.EpubSearchEngine
import com.epubreader.app.epub.SpineItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSearchServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val spine = listOf(
        SpineItem(idref = "c1", href = "text/chapter1.xhtml", mediaType = "application/xhtml+xml"),
        SpineItem(idref = "c2", href = "text/chapter2.xhtml", mediaType = "application/xhtml+xml"),
    )

    private val tocTitles = mapOf(
        "text/chapter1.xhtml" to "The Beginning",
        "text/chapter2.xhtml" to "The End",
    )

    /** Minimal but real epub zip: the engine reads it with ZipFile.
     *  Cached per test — TemporaryFolder rejects duplicate file names. */
    private var cachedEpub: File? = null
    private fun epub(): File =
        cachedEpub ?: tmp.newFile("book.epub").also { file ->
            ZipOutputStream(FileOutputStream(file)).use { zip ->
                fun put(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
                put(
                    "text/chapter1.xhtml",
                    "<html><body><p>It was the best of times, it was the worst of times.</p></body></html>",
                )
                put(
                    "text/chapter2.xhtml",
                    "<html><body><p>The quick brown fox jumps over the lazy dog.</p></body></html>",
                )
            }
            cachedEpub = file
        }

    @Test
    fun searchNow_findsMatchesAcrossChapters() = runTest {
        val service = ReaderSearchService()
        val results = service.searchNow(epub(), spine, tocTitles, "the")

        // "the" appears in chapter 1 (3x) and chapter 2 (2x, incl. "The").
        assertTrue(results.isNotEmpty())
        assertEquals(setOf(0, 1), results.map { it.chapterIndex }.toSet())
        assertEquals("The Beginning", results.first { it.chapterIndex == 0 }.chapterTitle)
    }

    @Test
    fun searchNow_blankQueryReturnsEmpty() = runTest {
        val service = ReaderSearchService()
        assertTrue(service.searchNow(epub(), spine, tocTitles, "   ").isEmpty())
    }

    @Test
    fun search_blankQueryDeliversEmptyResults() = runTest(dispatchTimeoutMs = 10_000) {
        val service = ReaderSearchService(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            resultDispatcher = StandardTestDispatcher(testScheduler),
        )
        var delivered: List<EpubSearchEngine.Result>? = null
        service.search(
            scope = this, file = epub(), spine = spine, tocTitles = tocTitles,
            query = "  ",
        ) { delivered = it }
        advanceUntilIdle()
        assertEquals(0, delivered?.size)
    }

    @Test
    fun search_latestWins_supersededSearchNeverDelivers() = runTest(dispatchTimeoutMs = 10_000) {
        val io = StandardTestDispatcher(testScheduler)
        val main = StandardTestDispatcher(testScheduler)
        val service = ReaderSearchService(
            engineFactory = { EpubSearchEngine(it) },
            ioDispatcher = io,
            resultDispatcher = main,
        )
        var delivered = mutableListOf<List<EpubSearchEngine.Result>>()

        service.search(this, epub(), spine, tocTitles, "the") { delivered.add(it) }
        // Superseded before the engine even starts running.
        service.search(this, epub(), spine, tocTitles, "fox") { delivered.add(it) }
        advanceUntilIdle()

        // Only the latest query's results arrive.
        assertEquals(1, delivered.size)
        assertTrue(delivered[0].all { r -> r.snippet.contains("fox") })
    }

    @Test
    fun search_cancel_invalidatesPendingResults() = runTest(dispatchTimeoutMs = 10_000) {
        val io = UnconfinedTestDispatcher(testScheduler)
        val resultDelivery = StandardTestDispatcher(testScheduler)
        val service = ReaderSearchService(
            engineFactory = { EpubSearchEngine(it) },
            ioDispatcher = io,
            resultDispatcher = resultDelivery,
        )
        var delivered = 0

        // With an unconfined io dispatcher the blocking search completes
        // immediately, but the result delivery is queued behind
        // resultDelivery — exactly the race the generation token guards.
        service.search(this, epub(), spine, tocTitles, "the") { delivered++ }
        // The user hides the search overlay before delivery runs.
        service.cancel()
        advanceUntilIdle()

        assertEquals(0, delivered)
    }
}
