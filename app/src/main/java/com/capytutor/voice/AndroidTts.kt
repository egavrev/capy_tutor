package com.capytutor.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Capy's voice (decision 7): Android TextToSpeech in Romanian, off by default; a speaker button
 * on each Capy bubble reads it aloud. Nothing is sent anywhere.
 */
class AndroidTts(context: Context) : com.capytutor.ai.TextToSpeech {
    enum class Status { LOADING, READY, NO_ROMANIAN, NO_ENGINE }

    override val id: String = "android-tts"

    private val _status = MutableStateFlow(Status.LOADING)
    val status: StateFlow<Status> = _status

    /** The text being read now, so its bubble can show the reading border. */
    private val _speaking = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = _speaking

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { code ->
        _status.value = if (code != TextToSpeech.SUCCESS) Status.NO_ENGINE else setRomanian()
    }

    private fun setRomanian(): Status {
        val r = tts.setLanguage(ROMANIAN)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return Status.NO_ROMANIAN
        tts.setSpeechRate(0.95f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        })
        return Status.READY
    }

    private fun finished(id: String?) {
        if (id == null || id == current) _speaking.value = null
    }

    @Volatile
    private var current: String? = null

    override fun isAvailable(): Boolean = _status.value == Status.READY

    /** Starts reading [text]; returns at once (the bubble watches [speaking]). */
    fun say(text: String) {
        if (!isAvailable()) return
        val id = "u${System.nanoTime()}"
        current = id
        _speaking.value = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    override suspend fun speak(text: String, languageTag: String) = say(text)

    override fun stop() {
        current = null
        _speaking.value = null
        tts.stop()
    }

    fun shutdown() = tts.shutdown()

    companion object {
        val ROMANIAN: Locale = Locale.forLanguageTag("ro-RO")
    }
}
