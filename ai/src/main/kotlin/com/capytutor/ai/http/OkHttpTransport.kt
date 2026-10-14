package com.capytutor.ai.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** OkHttp transport. No logging interceptor: requests carry the API key. */
class OkHttpTransport(private val client: OkHttpClient = OkHttpClient()) : HttpTransport {

    override suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): HttpResponse {
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody(JSON))
            .build()
        return execute(request, timeoutMs)
    }

    override suspend fun postMultipart(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        fileField: String,
        file: File,
        fileMediaType: String,
        timeoutMs: Long,
    ): HttpResponse {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .apply { fields.forEach { (k, v) -> addFormDataPart(k, v) } }
            .addFormDataPart(fileField, file.name, file.asRequestBody(fileMediaType.toMediaType()))
            .build()
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(multipart)
            .build()
        return execute(request, timeoutMs)
    }

    private suspend fun execute(request: Request, timeoutMs: Long): HttpResponse = withContext(Dispatchers.IO) {
        // One budget for the whole call. OkHttp's default 10 s read timeout would otherwise cut off
        // a model that thinks for longer than 10 s before sending the first byte.
        val call = client.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(minOf(timeoutMs, 15_000L), TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
        try {
            call.execute().use { r ->
                HttpResponse(
                    code = r.code,
                    headers = r.headers.names().associate { it.lowercase() to (r.header(it) ?: "") },
                    body = r.body?.string().orEmpty(),
                )
            }
        } catch (e: InterruptedIOException) {
            throw TransportException(TransportException.Kind.TIMEOUT, "timeout after $timeoutMs ms")
        } catch (e: IOException) {
            throw TransportException(TransportException.Kind.NETWORK, e.javaClass.simpleName)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
