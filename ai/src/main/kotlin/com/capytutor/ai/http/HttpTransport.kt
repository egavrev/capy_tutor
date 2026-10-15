package com.capytutor.ai.http

import java.io.File
import java.io.IOException

/** Minimal HTTP surface the adapters need. OkHttp in production, a fake in tests. */
interface HttpTransport {
    suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): HttpResponse

    suspend fun postMultipart(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        fileField: String,
        file: File,
        fileMediaType: String,
        timeoutMs: Long,
    ): HttpResponse

    /** Plain GET, used by the phone check (is Groq reachable, is the key good). */
    suspend fun get(url: String, headers: Map<String, String>, timeoutMs: Long): HttpResponse =
        throw UnsupportedOperationException("GET not supported by ${this::class.simpleName}")
}

/** [headers] keys are lower case; one value per header. */
data class HttpResponse(val code: Int, val headers: Map<String, String>, val body: String)

/** The request never got an HTTP answer. */
class TransportException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind(val reason: String) { TIMEOUT("timeout"), NETWORK("offline") }
}
