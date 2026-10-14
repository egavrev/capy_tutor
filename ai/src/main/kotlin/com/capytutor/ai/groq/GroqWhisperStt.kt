package com.capytutor.ai.groq

import com.capytutor.ai.SpeechToText
import com.capytutor.ai.SttResult
import com.capytutor.ai.http.HttpTransport
import com.capytutor.ai.http.TransportException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Batch speech-to-text with Groq Whisper (HANDOFF.md §4.3):
 * `POST {baseUrl}/audio/transcriptions`, multipart, language ro, temperature 0.
 * Deleting the audio file afterwards is the caller's job (it owns the file).
 */
class GroqWhisperStt(
    private val model: String,
    private val apiKey: () -> String,
    private val transport: HttpTransport,
    private val baseUrl: String = Groq.DEFAULT_BASE_URL,
    private val timeoutMs: Long = 60_000,
) : SpeechToText {

    override val id: String = "groq/$model"

    override suspend fun transcribe(audio: File, languageTag: String): SttResult = transcribe(audio, languageTag, null)

    /** [prompt]: the lesson title and a few key terms improve recognition of names and dates. */
    suspend fun transcribe(audio: File, languageTag: String, prompt: String?): SttResult {
        val fields = buildMap {
            put("model", model)
            put("language", languageTag.substringBefore('-'))
            put("response_format", "json")
            put("temperature", "0")
            if (!prompt.isNullOrBlank()) put("prompt", prompt)
        }
        val response = try {
            transport.postMultipart(
                url = "${baseUrl.trimEnd('/')}/audio/transcriptions",
                headers = mapOf("Authorization" to "Bearer ${apiKey()}"),
                fields = fields,
                fileField = "file",
                file = audio,
                fileMediaType = mediaType(audio),
                timeoutMs = timeoutMs,
            )
        } catch (e: TransportException) {
            return SttResult.Failed(e.kind.reason)
        }
        return when {
            response.code == 200 -> try {
                val text = (Json.parseToJsonElement(response.body).jsonObject["text"] as? JsonPrimitive)?.content
                if (text == null) SttResult.Failed("no text in reply") else SttResult.Ok(text.trim())
            } catch (e: Exception) {
                SttResult.Failed("unreadable response")
            }
            response.code == 429 -> SttResult.RateLimited(ChatResponses.retryAfterSeconds(response))
            response.code == 401 || response.code == 403 -> SttResult.Failed("unauthorized (HTTP ${response.code}): check the Groq API key")
            else -> SttResult.Failed("HTTP ${response.code}")
        }
    }

    override fun toString(): String = "GroqWhisperStt($id)"

    private fun mediaType(f: File): String = when (f.extension.lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "mp3", "mpeg", "mpga" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }
}
