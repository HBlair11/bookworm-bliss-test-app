package com.bookwormbliss.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.UUID

/**
 * Thin wrapper around Android's on-device [TextToSpeech] engine (no network
 * call, no extra permission — TTS is a platform service). Reads one chapter
 * at a time, split into paragraph-sized utterances so pause/resume lands at
 * a sane boundary rather than mid-sentence.
 */
class ReaderTtsController(context: Context) {

    var isReady by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var currentParagraphIndex by mutableStateOf(0)
        private set

    private var paragraphs: List<String> = emptyList()
    private var onFinishedChapter: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) engineOrNull()?.language = Locale.getDefault()
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                val next = currentParagraphIndex + 1
                if (next < paragraphs.size) {
                    currentParagraphIndex = next
                    speakParagraph(next)
                } else {
                    isSpeaking = false
                    onFinishedChapter?.invoke()
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { isSpeaking = false }
        })
    }

    private fun engineOrNull(): TextToSpeech? = tts

    fun speakChapter(text: String, startParagraph: Int = 0, onFinished: () -> Unit = {}) {
        paragraphs = text.split(Regex("\\n{2,}|(?<=[.!?])\\s{2,}")).filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) paragraphs = listOf(text)
        onFinishedChapter = onFinished
        currentParagraphIndex = startParagraph.coerceIn(0, paragraphs.lastIndex)
        isSpeaking = true
        speakParagraph(currentParagraphIndex)
    }

    fun pause() {
        tts.stop()
        isSpeaking = false
    }

    fun stop() {
        tts.stop()
        isSpeaking = false
        currentParagraphIndex = 0
    }

    fun setSpeed(rate: Float) { tts.setSpeechRate(rate) }
    fun setPitch(pitch: Float) { tts.setPitch(pitch) }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun speakParagraph(index: Int) {
        val text = paragraphs.getOrNull(index) ?: return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }
}
