package com.capytutor.ai

import com.capytutor.ai.groq.ChatResponses
import com.capytutor.core.schema.Validated

/** Outcome of one logical call, possibly made of two attempts. Tokens are summed over attempts. */
sealed interface JsonCall<out T> {
    val attempts: Int
    val inputTokens: Int
    val outputTokens: Int

    data class Ok<T>(
        val value: T,
        val rawJson: String,
        override val attempts: Int,
        override val inputTokens: Int,
        override val outputTokens: Int,
    ) : JsonCall<T>

    data class RateLimited(
        val retryAfterSeconds: Int?,
        override val attempts: Int,
        override val inputTokens: Int,
        override val outputTokens: Int,
    ) : JsonCall<Nothing>

    data class Failed(
        val reason: String,
        val retryable: Boolean,
        override val attempts: Int,
        override val inputTokens: Int,
        override val outputTokens: Int,
    ) : JsonCall<Nothing>
}

/**
 * Calls the model for a JSON reply and validates it (HANDOFF.md §4.2): a reply that fails
 * validation (or is not JSON) is retried once on the same model with a short reminder.
 * Rate limits and network failures are returned as they are; the caller pauses.
 */
class JsonCaller(private val llm: LlmProvider) {

    suspend fun <T> call(
        system: String,
        user: String,
        schemaName: String,
        maxTokens: Int,
        temperature: Double,
        parse: (String) -> Validated<T>,
    ): JsonCall<T> {
        var inTok = 0
        var outTok = 0
        var prompt = user
        for (attempt in 1..MAX_ATTEMPTS) {
            val problem: String = when (val r = llm.completeJson(system, prompt, schemaName, maxTokens, temperature)) {
                is LlmResult.Ok -> {
                    inTok += r.inputTokens
                    outTok += r.outputTokens
                    when (val v = parse(r.json)) {
                        is Validated.Ok -> return JsonCall.Ok(v.value, r.json, attempt, inTok, outTok)
                        is Validated.Invalid -> v.reason
                    }
                }
                is LlmResult.RateLimited -> return JsonCall.RateLimited(r.retryAfterSeconds, attempt, inTok, outTok)
                is LlmResult.Failed ->
                    if (r.reason.startsWith(ChatResponses.INVALID_JSON)) r.reason
                    else return JsonCall.Failed(r.reason, r.retryable, attempt, inTok, outTok)
            }
            prompt = user + reminder(problem)
        }
        return JsonCall.Failed("${ChatResponses.INVALID_JSON}: still invalid after $MAX_ATTEMPTS attempts", true, MAX_ATTEMPTS, inTok, outTok)
    }

    companion object {
        const val MAX_ATTEMPTS = 2

        fun reminder(problem: String): String =
            "\n\nAtenție: răspunsul anterior nu a respectat schema ($problem). " +
                "Răspunde DOAR cu JSON valid, exact în forma cerută, fără alt text."
    }
}
