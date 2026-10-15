package com.capytutor.ai

import com.capytutor.core.config.CoreConfig
import com.capytutor.core.config.Route
import com.capytutor.core.prompts.Prompts
import com.capytutor.core.schema.Validated
import java.time.LocalDate

/** One logical call, for the parent's "usage today" screen (HANDOFF.md §9, ProviderUsage). */
data class UsageRecord(
    val date: LocalDate,
    val provider: String,
    val model: String,
    val callType: String,
    val requests: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val rateLimited: Boolean,
    val outcome: String,
    /** Speech-to-text only: length of the recording sent. */
    val audioSeconds: Int = 0,
)

fun interface UsageSink {
    fun record(r: UsageRecord)
}

class InMemoryUsage : UsageSink {
    val records = mutableListOf<UsageRecord>()
    override fun record(r: UsageRecord) {
        records += r
    }
}

/**
 * Routes each call type to its providers, from configuration (HANDOFF.md §4.2). Tries them in
 * order: the next one is used only on a rate limit or a retryable failure. The MVP configures one
 * provider per call (decision 1), so a rate limit is returned to the caller, which pauses.
 * A reply that fails validation is retried once on the same model (JsonCaller).
 */
class ProviderRouter(
    private val providerFor: (Route) -> LlmProvider,
    private val usage: UsageSink = UsageSink { },
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val providers = mutableMapOf<Route, LlmProvider>()

    suspend fun <T> callJson(callType: String, user: String, parse: (String) -> Validated<T>): JsonCall<T> {
        var last: JsonCall<T>? = null
        for (route in CoreConfig.routes(callType)) {
            val llm = providers.getOrPut(route) { providerFor(route) }
            val r = JsonCaller(llm).call(
                system = Prompts.system(),
                user = user,
                schemaName = callType,
                maxTokens = CoreConfig.maxTokens(callType),
                temperature = CoreConfig.temperature(),
                parse = parse,
            )
            usage.record(
                UsageRecord(
                    date = today(),
                    provider = route.provider,
                    model = route.model,
                    callType = callType,
                    requests = r.attempts,
                    inputTokens = r.inputTokens,
                    outputTokens = r.outputTokens,
                    rateLimited = r is JsonCall.RateLimited,
                    outcome = when (r) {
                        is JsonCall.Ok -> "ok"
                        is JsonCall.RateLimited -> "rate_limited"
                        is JsonCall.Failed -> r.reason
                    },
                ),
            )
            when (r) {
                is JsonCall.Ok -> return r
                is JsonCall.Failed -> if (!r.retryable) return r
                is JsonCall.RateLimited -> Unit
            }
            last = r
        }
        return last ?: error("No provider configured for $callType")
    }
}
