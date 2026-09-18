package com.epubreader.app.features.search

import com.epubreader.app.epub.EpubSearchEngine
import com.epubreader.app.epub.SpineItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ReaderSearchService — Phase 7 feature service.
 *
 * Owns the in-book search feature's execution logic so the reader UI only
 * renders results:
 *  - runs [EpubSearchEngine] off the main thread,
 *  - enforces latest-wins semantics (a new query supersedes any in-flight
 *    one), and
 *  - delivers results on the caller's dispatcher.
 *
 * Cancellation has two layers, because the underlying engine is blocking:
 * the in-flight coroutine is cancelled AND a generation token guards the
 * result delivery — a stale search that already finished its blocking zip
 * scan can never deliver into a newer query's result list.
 *
 * @param engineFactory creates the search engine for a book file. Injectable
 *   so tests can substitute a controllable engine.
 * @param ioDispatcher where the blocking search runs.
 * @param resultDispatcher where [onResults] is invoked (main by default).
 */
class ReaderSearchService(
    private val engineFactory: (File) -> EpubSearchEngine = ::EpubSearchEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val resultDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {

    private var activeJob: Job? = null

    /** Bumped whenever a newer search starts or the service is cancelled;
     *  volatile so a background completion can safely re-read it. */
    @Volatile
    private var generation: Long = 0L

    /**
     * One-shot search that suspends until results are ready. Use when the
     * caller already owns a coroutine and the cancellation policy.
     */
    suspend fun searchNow(
        file: File,
        spine: List<SpineItem>,
        tocTitles: Map<String, String>,
        query: String,
    ): List<EpubSearchEngine.Result> = withContext(ioDispatcher) {
        engineFactory(file).search(spine, tocTitles, query)
    }

    /**
     * Latest-wins search within [scope]: cancels any in-flight search and
     * guarantees [onResults] reflects only the most recent query.
     */
    fun search(
        scope: CoroutineScope,
        file: File,
        spine: List<SpineItem>,
        tocTitles: Map<String, String>,
        query: String,
        onResults: (List<EpubSearchEngine.Result>) -> Unit,
    ) {
        activeJob?.cancel()
        val myGeneration = ++generation
        activeJob = scope.launch(ioDispatcher) {
            val results = engineFactory(file).search(spine, tocTitles, query)
            withContext(resultDispatcher) {
                // Generation guard: a search that raced past cancellation
                // must not overwrite a newer query's results.
                if (generation == myGeneration) onResults(results)
            }
        }
    }

    /** Cancels the in-flight search, if any, and invalidates its results. */
    fun cancel() {
        generation++
        activeJob?.cancel()
        activeJob = null
    }
}
