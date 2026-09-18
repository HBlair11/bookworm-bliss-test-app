package com.epubreader.app.features.define

import com.epubreader.app.epub.DictionaryLookup
import com.epubreader.app.epub.DictionarySource
import com.epubreader.app.features.FakeDictionaryHistoryDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefinitionServiceTest {

    /** Scriptable dictionary: records lookups and can be closed. */
    private class FakeDictionarySource(
        val language: String,
    ) : DictionarySource {
        var closed = false
        var lookups = 0

        override fun lookup(raw: String): DictionaryLookup.Result =
            DictionaryLookup.Result(
                entries = listOf(DictionaryLookup.Entry(raw.lowercase(), "noun", "a $raw definition")),
                matchedWord = raw.lowercase(),
                suggestions = emptyList(),
            ).also { lookups++ }

        override fun matchesLanguage(language: String?): Boolean =
            language == null || language.equals(this.language, ignoreCase = true)

        override fun close() {
            closed = true
        }
    }

    private val historyDao = FakeDictionaryHistoryDao()
    private var clock = 0L
    private val created = mutableListOf<FakeDictionarySource>()
    private val service = DefinitionService(historyDao, { lang ->
        FakeDictionarySource(lang).also { created.add(it) }
    }, clock = { clock })

    private fun result(word: String): DictionaryLookup.Result = DictionaryLookup.Result(
        entries = listOf(DictionaryLookup.Entry(word, "noun", "definition of $word")),
        matchedWord = word,
        suggestions = emptyList(),
    )

    @Test
    fun define_reusesDictionaryForSameLanguage() {
        service.define("alpha", "en")
        service.define("beta", "en")

        assertEquals(1, created.size)
        assertEquals(2, created[0].lookups)
        assertFalse(created[0].closed)
    }

    @Test
    fun define_nullLanguageMatchesDefaultDictionary() {
        service.define("alpha", null)
        service.define("beta", null)

        assertEquals(1, created.size)
    }

    @Test
    fun define_switchingLanguageClosesPreviousDictionary() {
        service.define("alpha", "en")
        service.define("beta", "fr")

        assertEquals(2, created.size)
        assertTrue(created[0].closed)
        assertFalse(created[1].closed)
        assertEquals("fr", created[1].language)
    }

    @Test
    fun close_closesActiveDictionary() {
        service.define("alpha", "en")
        service.close()
        assertTrue(created[0].closed)
        // A later define still works: a fresh dictionary is created.
        service.define("beta", "en")
        assertEquals(2, created.size)
    }

    @Test
    fun recordLookup_insertsNewWordWithNormalizedForm() = runTest {
        clock = 1000L

        service.recordLookup("  Serene!  ", result("serene"), bookId = 7L)

        val row = historyDao.rows.single()
        assertEquals("serene", row.word)
        assertEquals("definition of serene", row.definition)
        assertEquals("noun", row.partOfSpeech)
        assertEquals(7L, row.bookId)
        assertEquals(1000L, row.lookedUpAt)
    }

    @Test
    fun recordLookup_refreshesExistingWordInsteadOfDuplicating() = runTest {
        clock = 1000L
        service.recordLookup("serene", result("serene"), bookId = 1L)

        clock = 2000L
        service.recordLookup("serene", result("serene"), bookId = 2L)

        val row = historyDao.rows.single()
        assertEquals(2000L, row.lookedUpAt)
        assertEquals(2L, row.bookId)
    }

    @Test
    fun recordLookup_blankWordIsIgnored() = runTest {
        service.recordLookup("   ", result("   "), bookId = 1L)
        assertTrue(historyDao.rows.isEmpty())
    }

    @Test
    fun recordLookup_prefersMatchedWordOverRawSelection() = runTest {
        service.recordLookup("MICE", result("mouse"), bookId = 1L)
        assertEquals("mouse", historyDao.rows.single().word)
    }
}
