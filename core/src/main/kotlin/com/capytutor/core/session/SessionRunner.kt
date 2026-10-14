package com.capytutor.core.session

import com.capytutor.core.coverage.KeywordMatcher
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.pack.PackIdea
import com.capytutor.core.safety.SafetyFilter
import com.capytutor.core.schema.AnswerReply
import com.capytutor.core.schema.CoverageStatus
import com.capytutor.core.schema.RecallReply
import com.capytutor.core.schema.SummaryReply

/** A model reply plus what it cost. */
sealed interface AiReply<out T> {
    data class Ok<T>(val value: T, val calls: Int, val tokens: Int) : AiReply<T>
    data class Unavailable(val reason: PauseReason, val retryAfterSeconds: Int?, val calls: Int = 0, val tokens: Int = 0) : AiReply<Nothing>
}

/** What the session needs from a model. Implemented in :ai (Groq); faked in tests. */
interface SessionAi {
    suspend fun assessRecall(pack: LessonPack, transcript: String): AiReply<RecallReply>
    suspend fun assessAnswer(pack: LessonPack, idea: PackIdea, question: String, answer: String, purpose: Purpose): AiReply<AnswerReply>
    suspend fun summarize(pack: LessonPack, state: SessionState): AiReply<SummaryReply>
}

/**
 * Judging one answer (HANDOFF.md §7): the free keyword pass first; the model only when the
 * keywords are inconclusive. Keyword groups already named in the question count as given,
 * so "în Etiopia" answers "Unde a fost găsită Lucy?".
 */
object AnswerJudge {
    fun keywordCovered(idea: PackIdea, question: String, answer: String): Boolean {
        val q = KeywordMatcher.normalize(question)
        val needed = idea.keywords.filterNot { KeywordMatcher.groupMatches(q, it) }
        return needed.isNotEmpty() && KeywordMatcher.isCandidateCovered(answer, needed)
    }
}

/**
 * Drives a session: feeds the child's inputs to the [SessionEngine] and carries out its requests
 * with [ai]. Everything the child wrote goes through the [SafetyFilter] before it is sent.
 * Stays within [limits]; past them, answers are judged by keywords only and the summary is
 * written locally.
 */
class SessionRunner(
    private val pack: LessonPack,
    private val ai: SessionAi,
    private val filter: SafetyFilter,
    private val limits: Limits = Limits(),
) {
    val engine = SessionEngine(pack)
    private val ideas = pack.ideas.associateBy { it.id }

    fun start(): Update = engine.start()

    /** Applies [input] and, if the engine asks for the AI, runs that request too. */
    suspend fun send(state: SessionState, input: Input): Update = settle(engine.onInput(state, input))

    /** After a pause: sends the pending request again. */
    suspend fun resume(state: SessionState): Update = send(state, Input.Resume)

    /**
     * After the app was killed while waiting for the AI (pending, not paused): sends the same
     * request again. Otherwise returns the state unchanged.
     */
    suspend fun recover(state: SessionState): Update =
        if (state.pending != null && state.paused == null) settle(Update(state, emptyList(), state.pending)) else Update(state, emptyList(), null)

    private suspend fun settle(first: Update): Update {
        var update = first
        val said = first.capySaid.toMutableList()
        while (update.request != null && update.state.paused == null) {
            val (outcome, calls, tokens) = execute(update.state, update.request!!)
            update = engine.onAiResult(update.state, outcome, calls, tokens)
            said += update.capySaid
        }
        return update.copy(capySaid = said)
    }

    private fun withinBudget(s: SessionState) = s.callsUsed < limits.maxCalls && s.tokensUsed < limits.maxTokens

    private suspend fun execute(s: SessionState, r: Request): Triple<AiOutcome, Int, Int> = when (r) {
        is Request.AssessRecall -> when (val reply = ai.assessRecall(pack, filter.clean(r.transcript))) {
            is AiReply.Ok -> Triple(
                AiOutcome.Recall(reply.value.ideas.associate { it.id to (it.status to it.evidence) }, reply.value.praise),
                reply.calls, reply.tokens,
            )
            is AiReply.Unavailable -> Triple(AiOutcome.Unavailable(reply.reason, reply.retryAfterSeconds), reply.calls, reply.tokens)
        }

        is Request.JudgeAnswer -> {
            val idea = ideas.getValue(r.ideaId)
            when {
                AnswerJudge.keywordCovered(idea, r.question, r.answer) ->
                    Triple(AiOutcome.Answer(CoverageStatus.COVERED, null, DecidedBy.KEYWORDS), 0, 0)
                !withinBudget(s) -> Triple(AiOutcome.Answer(null, null, DecidedBy.NOT_JUDGED), 0, 0)
                else -> when (val reply = ai.assessAnswer(pack, idea, filter.clean(r.question), filter.clean(r.answer), r.purpose)) {
                    is AiReply.Ok -> Triple(AiOutcome.Answer(reply.value.status, reply.value.reply, DecidedBy.MODEL), reply.calls, reply.tokens)
                    is AiReply.Unavailable -> Triple(AiOutcome.Unavailable(reply.reason, reply.retryAfterSeconds), reply.calls, reply.tokens)
                }
            }
        }

        Request.Summarize ->
            if (!withinBudget(s)) {
                Triple(AiOutcome.Summary(null, null), 0, 0)
            } else {
                when (val reply = ai.summarize(pack, s)) {
                    is AiReply.Ok -> Triple(AiOutcome.Summary(reply.value.childSummary, reply.value.parentNote), reply.calls, reply.tokens)
                    // The summary is not worth a pause: write it locally.
                    is AiReply.Unavailable -> Triple(AiOutcome.Summary(null, null), reply.calls, reply.tokens)
                }
            }
    }
}
