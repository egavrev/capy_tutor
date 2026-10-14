package com.capytutor.ai.groq

import com.capytutor.ai.LlmProvider
import com.capytutor.ai.LlmResult
import com.capytutor.ai.http.HttpResponse
import com.capytutor.ai.http.HttpTransport
import com.capytutor.ai.http.TransportException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.ceil

/**
 * An OpenAI-compatible chat endpoint (Groq: `POST {baseUrl}/chat/completions`).
 * Always asks for JSON mode; the caller still validates the reply against its schema.
 */
class OpenAiCompatibleLlm(
    private val providerName: String,
    private val model: String,
    private val baseUrl: String,
    private val apiKey: () -> String,
    private val transport: HttpTransport,
    private val timeoutMs: Long = 20_000,
    /** Extra request fields, e.g. reasoning_effort for gpt-oss on Groq. */
    private val extraParams: Map<String, JsonElement> = emptyMap(),
) : LlmProvider {

    override val id: String = "$providerName/$model"

    override suspend fun completeJson(
        system: String,
        user: String,
        schemaName: String,
        maxTokens: Int,
        temperature: Double,
    ): LlmResult {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                addJsonObject { put("role", "user"); put("content", user) }
            }
            putJsonObject("response_format") { put("type", "json_object") }
            put("temperature", temperature)
            put("max_completion_tokens", maxTokens)
            extraParams.forEach { (k, v) -> put(k, v) }
        }
        val response = try {
            transport.postJson(
                url = "${baseUrl.trimEnd('/')}/chat/completions",
                headers = mapOf("Authorization" to "Bearer ${apiKey()}"),
                body = body.toString(),
                timeoutMs = timeoutMs,
            )
        } catch (e: TransportException) {
            return LlmResult.Failed(e.kind.reason, retryable = true)
        }
        return ChatResponses.toLlmResult(response)
    }

    override fun toString(): String = "OpenAiCompatibleLlm($id)" // never print the key
}

/** Groq specifics. Model ids come from configuration. */
object Groq {
    const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"

    fun chat(
        model: String,
        apiKey: () -> String,
        transport: HttpTransport,
        baseUrl: String = DEFAULT_BASE_URL,
        timeoutMs: Long = 20_000,
        reasoningEffort: String? = "low",
    ): OpenAiCompatibleLlm {
        val extra = buildMap<String, JsonElement> {
            if (model.startsWith("openai/gpt-oss") && reasoningEffort != null) {
                put("reasoning_effort", JsonPrimitive(reasoningEffort))
                put("include_reasoning", JsonPrimitive(false))
            }
        }
        return OpenAiCompatibleLlm("groq", model, baseUrl, apiKey, transport, timeoutMs, extra)
    }

    /** A Groq model for one configured route, with that call type's timeout and reasoning effort. */
    fun forRoute(route: com.capytutor.core.config.Route, apiKey: () -> String, transport: HttpTransport): OpenAiCompatibleLlm {
        require(route.provider == "groq") { "Not a Groq route: ${route.id}" }
        val cfg = com.capytutor.core.config.CoreConfig
        return chat(
            model = route.model,
            apiKey = apiKey,
            transport = transport,
            baseUrl = cfg.get("groq.baseUrl") ?: DEFAULT_BASE_URL,
            timeoutMs = cfg.timeoutMs(route.callType),
            reasoningEffort = cfg.reasoningEffort(route.callType, route.provider),
        )
    }
}

/** Turns an HTTP answer into an [LlmResult]. Pure, so it is tested with recorded replies. */
object ChatResponses {
    /** Reason prefix for replies that were not valid JSON: worth one retry with a reminder. */
    const val INVALID_JSON = "invalid_json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun toLlmResult(r: HttpResponse): LlmResult = when {
        r.code == 200 -> parseOk(r.body)
        r.code == 429 -> LlmResult.RateLimited(retryAfterSeconds(r))
        r.code == 400 && errorField(r.body, "code") == "json_validate_failed" ->
            LlmResult.Failed("$INVALID_JSON: the model did not produce valid JSON", retryable = true)
        r.code == 401 || r.code == 403 -> LlmResult.Failed("unauthorized (HTTP ${r.code}): check the Groq API key", retryable = false)
        r.code >= 500 -> LlmResult.Failed("server error (HTTP ${r.code})", retryable = true)
        else -> LlmResult.Failed("HTTP ${r.code}: ${errorField(r.body, "message")?.take(200) ?: "no details"}", retryable = false)
    }

    private fun parseOk(body: String): LlmResult = try {
        val root = json.parseToJsonElement(body).jsonObject
        val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.let { (it as? JsonPrimitive)?.content }
        val usage = root["usage"] as? JsonObject
        val inTok = usage?.get("prompt_tokens")?.jsonPrimitive?.int ?: 0
        val outTok = usage?.get("completion_tokens")?.jsonPrimitive?.int ?: 0
        if (content.isNullOrBlank()) {
            LlmResult.Failed("$INVALID_JSON: empty reply", retryable = true)
        } else {
            LlmResult.Ok(content, inTok, outTok)
        }
    } catch (e: Exception) {
        LlmResult.Failed("unreadable response: ${e.javaClass.simpleName}", retryable = true)
    }

    /** `retry-after` header (seconds), else Groq's "Please try again in 7.66s" / "1m2.5s" / "450ms". */
    fun retryAfterSeconds(r: HttpResponse): Int? {
        r.headers["retry-after"]?.trim()?.toDoubleOrNull()?.let { return ceil(it).toInt().coerceAtLeast(1) }
        val msg = errorField(r.body, "message") ?: return null
        val m = Regex("try again in\\s+(?:(\\d+)m)?(?:([\\d.]+)s)?(?:([\\d.]+)ms)?").find(msg) ?: return null
        val minutes = m.groupValues[1].toDoubleOrNull() ?: 0.0
        val seconds = m.groupValues[2].toDoubleOrNull() ?: 0.0
        val millis = m.groupValues[3].toDoubleOrNull() ?: 0.0
        val total = minutes * 60 + seconds + millis / 1000
        return if (total > 0) ceil(total).toInt().coerceAtLeast(1) else null
    }

    private fun errorField(body: String, field: String): String? = try {
        val err = json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
        (err?.get(field) as? JsonPrimitive)?.content
    } catch (e: Exception) {
        null
    }
}
