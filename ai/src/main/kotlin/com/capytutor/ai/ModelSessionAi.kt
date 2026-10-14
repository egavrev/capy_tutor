package com.capytutor.ai

import com.capytutor.core.config.CoreConfig
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.pack.PackIdea
import com.capytutor.core.prompts.Prompts
import com.capytutor.core.schema.AnswerReply
import com.capytutor.core.schema.RecallReply
import com.capytutor.core.schema.Schemas
import com.capytutor.core.schema.SummaryReply
import com.capytutor.core.session.AiReply
import com.capytutor.core.session.CheckResult
import com.capytutor.core.session.Outcome
import com.capytutor.core.session.PauseReason
import com.capytutor.core.session.Purpose
import com.capytutor.core.session.SessionAi
import com.capytutor.core.session.SessionState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The session's model calls (assess_recall, assess_answer, summarize_session) through the
 * [ProviderRouter]. Callers pass text that already went through the safety filter.
 * Sends ideas and the child's words, never the whole lesson (HANDOFF.md §8 budget).
 */
class ModelSessionAi(private val router: ProviderRouter) : SessionAi {

    override suspend fun assessRecall(pack: LessonPack, transcript: String): AiReply<RecallReply> {
        val user = Prompts.render(
            Prompts.ASSESS_RECALL,
            mapOf(
                "title" to pack.title,
                "ideas" to Schemas.ideasForPrompt(pack.ideas.map { Triple(it.id, it.text, it.keywords) }),
                "transcript" to transcript,
            ),
        )
        val ids = pack.ideas.map { it.id }
        return router.callJson(CoreConfig.ASSESS_RECALL, user) { Schemas.parseRecall(it, ids) }.toReply()
    }

    override suspend fun assessAnswer(pack: LessonPack, idea: PackIdea, question: String, answer: String, purpose: Purpose): AiReply<AnswerReply> {
        val ideaJson = buildJsonObject {
            put("text", idea.text)
            put("keywords", JsonArray(idea.keywords.flatten().distinct().map(::JsonPrimitive)))
            put("expectedAnswer", idea.checkAnswer)
        }.toString()
        val user = Prompts.render(
            Prompts.ASSESS_ANSWER,
            mapOf(
                "title" to pack.title,
                "idea" to ideaJson,
                "purpose" to when (purpose) {
                    Purpose.HINT -> "Copilul încearcă să-și amintească această idee, cu ajutorul lui Capy."
                    Purpose.TEST -> "Este întrebarea de test pentru această idee."
                },
                "question" to question,
                "answer" to answer,
                "tiebreak" to when (purpose) {
                    // In the hint ladder "partial" just means one more gentle hint.
                    Purpose.HINT -> "Când ezitezi între \"covered\" și \"partial\", alege \"partial\"."
                    // In the test a short correct answer counts.
                    Purpose.TEST -> "Când răspunsul e corect dar scurt, alege \"covered\"."
                },
            ),
        )
        return router.callJson(CoreConfig.ASSESS_ANSWER, user) { Schemas.parseAnswer(it) }.toReply()
    }

    override suspend fun summarize(pack: LessonPack, state: SessionState): AiReply<SummaryReply> {
        val byId = pack.ideas.associateBy { it.id }
        val results = buildJsonArray {
            for (r in state.results) add(
                buildJsonObject {
                    put("idea", byId[r.ideaId]?.text.orEmpty())
                    put("retell", r.retellStatus?.wire ?: "missed")
                    put(
                        "outcome",
                        when (r.outcome) {
                            Outcome.RECALLED_ALONE -> "recalledAlone"
                            Outcome.RECALLED_WITH_HINT -> "recalledWithHint"
                            Outcome.EXPLAINED -> "explained"
                            null -> "unknown"
                        },
                    )
                    put(
                        "test",
                        when (r.checkResult) {
                            CheckResult.CORRECT -> "correct"
                            CheckResult.NOT_YET -> "notYet"
                            CheckResult.UNJUDGED, null -> "unjudged"
                        },
                    )
                    put("disputed", r.childDisputed)
                },
            )
        }.toString()
        val user = Prompts.render(
            Prompts.SUMMARIZE_SESSION,
            mapOf("title" to pack.title, "score" to state.score.toString(), "total" to state.results.size.toString(), "results" to results),
        )
        return router.callJson(CoreConfig.SUMMARIZE_SESSION, user) { Schemas.parseSummary(it) }.toReply()
    }

    private fun <T> JsonCall<T>.toReply(): AiReply<T> = when (this) {
        is JsonCall.Ok -> AiReply.Ok(value, attempts, inputTokens + outputTokens)
        is JsonCall.RateLimited -> AiReply.Unavailable(PauseReason.RATE_LIMITED, retryAfterSeconds, attempts, inputTokens + outputTokens)
        is JsonCall.Failed -> AiReply.Unavailable(
            if (reason == "offline") PauseReason.OFFLINE else PauseReason.FAILED,
            null,
            attempts,
            inputTokens + outputTokens,
        )
    }
}
