package com.capytutor.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.capytutor.core.speech.SpeechText
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
            // A queue (reading mode) shows the piece being read now.
            override fun onStart(utteranceId: String?) {
                queued[utteranceId]?.let { _speaking.value = it }
            }
            override fun onDone(utteranceId: String?) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        })
        return Status.READY
    }

    private fun finished(id: String?) {
        if (id == null || id == current) {
            _speaking.value = null
            queued.clear()
        }
    }

    /** Utterance id → text, for a queue started with [sayAll]. */
    private val queued = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    private var current: String? = null

    override fun isAvailable(): Boolean = _status.value == Status.READY

    /** Starts reading [text]; returns at once (the bubble watches [speaking]). */
    fun say(text: String) {
        if (!isAvailable()) return
        val id = "u${System.nanoTime()}"
        queued.clear()
        current = id
        _speaking.value = text
        tts.speak(SpeechText.forVoice(text), TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /**
     * Reads [texts] one after another (reading mode: paragraph by paragraph); [speaking] follows
     * the piece being read, so the screen can mark it. Long pieces are cut at sentence ends.
     */
    fun sayAll(texts: List<String>) {
        if (!isAvailable()) return
        val pieces = texts.flatMap(::cut).filter { it.isNotBlank() }
        if (pieces.isEmpty()) return
        queued.clear()
        val batch = System.nanoTime()
        pieces.forEachIndexed { i, piece ->
            val id = "q$batch-$i"
            queued[id] = piece
            if (i == pieces.lastIndex) current = id
            tts.speak(SpeechText.forVoice(piece), if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        _speaking.value = pieces.first()
    }

    /** The engine refuses text longer than its limit (about 4,000 characters). */
    private fun cut(text: String): List<String> {
        val max = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            val at = rest.lastIndexOf(". ", max).takeIf { it > 0 }?.plus(1) ?: max
            out += rest.substring(0, at).trim()
            rest = rest.substring(at)
        }
        out += rest.trim()
        return out
    }

    override suspend fun speak(text: String, languageTag: String) = say(text)

    override fun stop() {
        current = null
        queued.clear()
        _speaking.value = null
        tts.stop()
    }

    fun shutdown() = tts.shutdown()

    companion object {
        val ROMANIAN: Locale = Locale.forLanguageTag("ro-RO")
    }
}
