package com.epubreader.app.features.tts

import android.content.Context
import com.epubreader.app.epub.ReaderTtsController
import com.epubreader.app.epub.ReaderTtsSegment
import com.epubreader.app.tts.ReaderTtsService

/**
 * ReaderTtsCoordinator — Phase 7 feature service.
 *
 * Coordinates the read-aloud feature's moving parts so neither the activity
 * nor the overlay UI has to own them:
 *  - the TTS engine ([ReaderTtsController]) lifecycle: creation, foreground
 *    service attachment ([ReaderTtsService.attach]), shutdown,
 *  - playback settings (speech rate / pitch) application,
 *  - engine event fan-out to a single [Listener] implemented by the host.
 *
 * Deliberately thin (lifecycle + wiring only): the visible-offset JS walk,
 * overlay UI, and per-book settings persistence remain in the reader UI
 * controllers that own them. The coordinator is the seam those controllers
 * plug into.
 *
 * The host keeps its existing `ttsController` accessors working by exposing
 * [controller] as a computed property over this coordinator.
 */
class ReaderTtsCoordinator(
    private val context: Context,
    private val listener: Listener,
) : AutoCloseable {

    /** Engine events surfaced to the host; all may arrive on a non-UI thread. */
    interface Listener {
        /** Playback started or paused/resumed ([playing] reflects the new state). */
        fun onPlayingChanged(playing: Boolean)

        /** The current chapter finished speaking. */
        fun onChapterFinished()

        /** Word-level range within [segment] is being spoken. */
        fun onWordRange(segment: ReaderTtsSegment, start: Int, end: Int)

        /** Sleep timer tick; [remainingMs] until it fires. */
        fun onSleepTick(remainingMs: Long)

        /** The sleep timer elapsed. */
        fun onSleepFinished()

        /** Sentence-level highlight anchored to [segment]. */
        fun onSentenceHighlight(segment: ReaderTtsSegment)
    }

    /** The live engine, or null before [start] / after [close]. */
    var controller: ReaderTtsController? = null
        private set

    /**
     * Creates (once) and returns the engine, attaching it to the foreground
     * TTS service so background playback keeps working.
     */
    fun start(): ReaderTtsController {
        controller?.let { return it }
        val created = ReaderTtsController(
            context,
            { playing -> listener.onPlayingChanged(playing) },
            { listener.onChapterFinished() },
            { segment, start, end -> listener.onWordRange(segment, start, end) },
            { remainingMs -> listener.onSleepTick(remainingMs) },
            { listener.onSleepFinished() },
            { segment -> listener.onSentenceHighlight(segment) },
        )
        controller = created
        ReaderTtsService.attach(created)
        return created
    }

    /** Applies speech rate and pitch to the engine (no-op before [start]). */
    fun applySettings(rate: Float, pitch: Float) {
        controller?.speechRate = rate
        controller?.pitch = pitch
    }

    /** Stops speaking (e.g. when the reader pauses without background playback). */
    fun stop() {
        controller?.stop()
    }

    /** Releases the engine. The host detaches/stops the foreground service. */
    override fun close() {
        controller?.close()
        controller = null
    }
}
