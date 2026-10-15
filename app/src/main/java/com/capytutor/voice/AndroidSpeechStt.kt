package com.capytutor.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The phone's own recogniser, used only when Groq is rate limited or offline (decision 4).
 * Live: the child speaks while it listens; it stops by itself after a pause. Prefers on-device
 * recognition, so nothing leaves the phone.
 */
class AndroidSpeechStt(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private fun onDevice(): Boolean = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
    }

    /** What the child said, "" when nothing was understood, null when the recogniser failed. */
    suspend fun listenOnce(): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val r = try {
                if (onDevice() && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
            } catch (e: Exception) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            recognizer = r
            fun finish(value: String?) {
                if (recognizer === r) recognizer = null
                r.destroy()
                if (cont.isActive) cont.resume(value)
            }
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty())
                }

                override fun onError(error: Int) {
                    finish(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "" else null)
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            cont.invokeOnCancellation {
                r.cancel()
                r.destroy()
            }
            try {
                r.startListening(intent())
            } catch (e: Exception) {
                finish(null)
            }
        }
    }

    /** The child tapped the mic again: stop listening and return what was heard. */
    fun stop() {
        recognizer?.stopListening()
    }

    companion object {
        const val LANGUAGE = "ro-RO"
    }
}
