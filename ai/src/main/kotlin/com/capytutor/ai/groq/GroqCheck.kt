package com.capytutor.ai.groq

import com.capytutor.ai.http.HttpTransport
import com.capytutor.ai.http.TransportException

/** For the parent's "Verificare telefon": is Groq reachable and is the key accepted? No tokens used. */
object GroqCheck {
    enum class Result { NO_KEY, OK, BAD_KEY, RATE_LIMITED, OFFLINE, ERROR }

    suspend fun run(transport: HttpTransport, apiKey: String, baseUrl: String = Groq.DEFAULT_BASE_URL): Result {
        if (apiKey.isBlank()) return Result.NO_KEY
        val r = try {
            transport.get("${baseUrl.trimEnd('/')}/models", mapOf("Authorization" to "Bearer $apiKey"), 15_000)
        } catch (e: TransportException) {
            return Result.OFFLINE
        } catch (e: UnsupportedOperationException) {
            return Result.ERROR
        }
        return when (r.code) {
            200 -> Result.OK
            401, 403 -> Result.BAD_KEY
            429 -> Result.RATE_LIMITED
            else -> Result.ERROR
        }
    }
}
