package com.capytutor.testbench

import com.capytutor.ai.JsonCall
import com.capytutor.ai.JsonCaller
import com.capytutor.ai.LlmProvider
import com.capytutor.core.config.CoreConfig
import com.capytutor.core.coverage.KeywordMatcher
import com.capytutor.core.lesson.Lesson
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.prompts.Prompts
import com.capytutor.core.safety.SafetyFilter
import com.capytutor.core.schema.RecallReply
import com.capytutor.core.schema.Schemas
import com.capytutor.core.schema.Validated
import kotlinx.coroutines.delay

/** What one logical call cost. Latency excludes time spent waiting out rate limits. */
data class CallRecord(
    val callType: String,
    val label: String,
    val latencyMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val attempts: Int,
    val rateLimitWaits: Int,
    val outcome: String,
)

data class EnrichOutcome(val pack: LessonPack?, val record: CallRecord, val error: String?)

data class AssessOutcome(
    val retelling: Retelling,
    val sentText: String,
    /** Keyword pass per idea id: matched groups / total groups. */
    val keywordMatched: Map<String, Pair<Int, Int>>,
    val reply: RecallReply?,
    val record: CallRecord,
    val error: String?,
) {
    fun keywordCandidate(id: String): Boolean = keywordMatched[id]?.let { (m, t) -> t > 0 && m == t } ?: false
}

/**
 * Runs the real prompts against the configured provider. Unlike the app (which pauses the
 * session), the bench simply waits out a rate limit and tries again, up to [maxRateLimitWaits].
 */
class Bench(
    private val llm: LlmProvider,
    private val filter: SafetyFilter,
    private val log: (String) -> Unit = ::println,
    private val maxRateLimitWaits: Int = 6,
) {
    val records = mutableListOf<CallRecord>()
    private val caller = JsonCaller(llm)

    suspend fun enrich(lesson: Lesson): EnrichOutcome {
        val ids = lesson.ideas.indices.map { lesson.ideaId(it) }
        val user = Prompts.render(
            Prompts.ENRICH_IDEAS,
            mapOf(
                "title" to lesson.title,
                "text" to lesson.text,
                "ideas" to lesson.ideas.mapIndexed { i, t -> "${lesson.ideaId(i)}: $t" }.joinToString("\n"),
            ),
        )
        val (result, record) = callJson(CoreConfig.ENRICH_IDEAS, lesson.title, user) { Schemas.parseEnrich(it, ids) }
        return when (result) {
            is JsonCall.Ok -> EnrichOutcome(LessonPack.build(lesson, result.value), record, null)
            else -> EnrichOutcome(null, record, describe(result))
        }
    }

    suspend fun assess(pack: LessonPack, retelling: Retelling): AssessOutcome {
        val sent = filter.clean(retelling.text)
        val keyword = pack.ideas.associate { idea ->
            idea.id to (KeywordMatcher.matchedGroups(sent, idea.keywords) to idea.keywords.size)
        }
        val ids = pack.ideas.map { it.id }
        val user = Prompts.render(
            Prompts.ASSESS_RECALL,
            mapOf(
                "title" to pack.title,
                "ideas" to Schemas.ideasForPrompt(pack.ideas.map { Triple(it.id, it.text, it.keywords) }),
                "transcript" to sent,
            ),
        )
        val (result, record) = callJson(CoreConfig.ASSESS_RECALL, retelling.id, user) { Schemas.parseRecall(it, ids) }
        return AssessOutcome(
            retelling = retelling,
            sentText = sent,
            keywordMatched = keyword,
            reply = (result as? JsonCall.Ok)?.value,
            record = record,
            error = if (result is JsonCall.Ok) null else describe(result),
        )
    }

    private suspend fun <T> callJson(
        callType: String,
        label: String,
        user: String,
        parse: (String) -> Validated<T>,
    ): Pair<JsonCall<T>, CallRecord> {
        var waits = 0
        var elapsedMs = 0L
        var inTok = 0
        var outTok = 0
        var attempts = 0
        while (true) {
            val start = System.nanoTime()
            val r = caller.call(
                system = Prompts.system(),
                user = user,
                schemaName = callType,
                maxTokens = CoreConfig.maxTokens(callType),
                temperature = CoreConfig.temperature(),
                parse = parse,
            )
            elapsedMs += (System.nanoTime() - start) / 1_000_000
            inTok += r.inputTokens
            outTok += r.outputTokens
            attempts += r.attempts
            if (r is JsonCall.RateLimited && waits < maxRateLimitWaits) {
                waits++
                val seconds = (r.retryAfterSeconds ?: 10) + 1
                log("  rate limited on $callType ($label), waiting ${seconds}s [$waits/$maxRateLimitWaits]")
                delay(seconds * 1000L)
                continue
            }
            val outcome = when (r) {
                is JsonCall.Ok -> if (r.attempts > 1) "ok after retry" else "ok"
                else -> describe(r)
            }
            val record = CallRecord(callType, label, elapsedMs, inTok, outTok, attempts, waits, outcome)
            records += record
            log("  $callType ($label): $outcome, ${elapsedMs} ms, $inTok in / $outTok out tokens")
            return r to record
        }
    }

    private fun describe(r: JsonCall<*>): String = when (r) {
        is JsonCall.Ok -> "ok"
        is JsonCall.RateLimited -> "rate limited (retry after ${r.retryAfterSeconds ?: "?"} s)"
        is JsonCall.Failed -> "failed: ${r.reason}"
    }
}
