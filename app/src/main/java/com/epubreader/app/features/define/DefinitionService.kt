package com.epubreader.app.features.define

import com.epubreader.app.data.DictionaryHistoryDao
import com.epubreader.app.data.DictionaryHistoryEntity
import com.epubreader.app.epub.DictionaryLookup
import com.epubreader.app.epub.DictionarySource
import java.util.Locale

/**
 * DefinitionService — Phase 7 feature service.
 *
 * Owns the Define feature's domain logic:
 *  - dictionary lifecycle, including automatic language switching (the
 *    active dictionary follows the EPUB's dc:language; switching closes the
 *    previous dictionary before opening the new one),
 *  - vocabulary history persistence (insert on first lookup, refresh
 *    timestamp/definition on re-lookup).
 *
 * Extracted from ReaderSelectionController (Phase 6 state) so the controller
 * only renders the definition card. The service is UI-free and depends on
 * the [DictionarySource] abstraction plus a Room DAO interface, which keeps
 * it unit-testable with in-memory fakes.
 *
 * @param historyDao    Room DAO for lookup history.
 * @param lookupFactory creates a [DictionarySource] for a language tag.
 * @param clock          timestamp source for history rows (injectable for tests).
 */
class DefinitionService(
    private val historyDao: DictionaryHistoryDao,
    private val lookupFactory: (String) -> DictionarySource,
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {

    private var activeLookup: DictionarySource? = null

    /**
     * Looks up [raw] using the dictionary for [language], reusing the active
     * dictionary when it already serves that language.
     *
     * History is NOT recorded here: the caller records via [recordLookup]
     * only when a definition card is actually shown, matching the original
     * behavior.
     */
    fun define(raw: String, language: String?): DictionaryLookup.Result =
        lookupFor(language).lookup(raw)

    /**
     * Persists a lookup in the vocabulary history. Re-lookups move the word
     * back to the top of the list (refresh) instead of duplicating rows.
     *
     * Prefers the dictionary's normalized match so punctuation variants
     * don't create odd history entries.
     */
    suspend fun recordLookup(raw: String, result: DictionaryLookup.Result, bookId: Long) {
        val definition = result.entries.firstOrNull()?.definition
        val partOfSpeech = result.entries.firstOrNull()?.partOfSpeech
        val word = (result.matchedWord ?: raw).trim().lowercase(Locale.US)
        if (word.isBlank()) return
        val existing = historyDao.find(word)
        if (existing == null) {
            historyDao.insert(
                DictionaryHistoryEntity(
                    word = word,
                    definition = definition,
                    partOfSpeech = partOfSpeech,
                    bookId = bookId,
                    lookedUpAt = clock(),
                ),
            )
        } else {
            historyDao.refresh(
                existing.id, definition, partOfSpeech, bookId, clock(),
            )
        }
    }

    /** Returns a dictionary matching [language], creating one when needed. */
    @Synchronized
    private fun lookupFor(language: String?): DictionarySource {
        activeLookup?.takeIf { it.matchesLanguage(language) }?.let { return it }
        val next = lookupFactory(language ?: DictionaryLookup.DEFAULT_LANGUAGE)
        activeLookup?.close()
        activeLookup = next
        return next
    }

    /** Releases the active dictionary, if any. */
    override fun close() {
        activeLookup?.close()
        activeLookup = null
    }
}
