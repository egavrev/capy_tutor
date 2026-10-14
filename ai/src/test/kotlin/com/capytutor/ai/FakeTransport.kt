package com.capytutor.ai

import com.capytutor.ai.http.HttpResponse
import com.capytutor.ai.http.HttpTransport
import java.io.File

/** Replays canned responses and records what was sent. */
class FakeTransport(vararg responses: HttpResponse) : HttpTransport {
    private val queue = ArrayDeque(responses.toList())
    val sent = mutableListOf<Sent>()

    data class Sent(val url: String, val headers: Map<String, String>, val body: String, val fields: Map<String, String> = emptyMap())

    override suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): HttpResponse {
        sent += Sent(url, headers, body)
        return queue.removeFirst()
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
        sent += Sent(url, headers, "", fields)
        return queue.removeFirst()
    }
}

/** A Groq chat completion as it comes back on HTTP 200. */
fun groqOk(content: String, promptTokens: Int = 900, completionTokens: Int = 300): HttpResponse {
    val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    return HttpResponse(
        200,
        mapOf("content-type" to "application/json"),
        """{"id":"chatcmpl-1","object":"chat.completion","model":"openai/gpt-oss-120b",
           "choices":[{"index":0,"message":{"role":"assistant","content":"$escaped"},"finish_reason":"stop"}],
           "usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens,"total_tokens":${promptTokens + completionTokens}}}""",
    )
}

val groq429 = HttpResponse(
    429,
    mapOf("retry-after" to "7"),
    """{"error":{"message":"Rate limit reached for model `openai/gpt-oss-120b` on tokens per minute (TPM): Limit 8000, Used 7900, Requested 900. Please try again in 6.75s.","type":"tokens","code":"rate_limit_exceeded"}}""",
)

val groqJsonFailed = HttpResponse(
    400,
    emptyMap(),
    """{"error":{"message":"Failed to generate JSON. Please adjust your prompt.","type":"invalid_request_error","code":"json_validate_failed"}}""",
)
