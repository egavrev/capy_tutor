package com.capytutor.core.session

import com.capytutor.core.schema.CoverageStatus

/*
 * The session as plain data (HANDOFF.md §5). Everything here is serialisable (SessionCodec),
 * so a session survives the app being killed or paused and resumes in the same place.
 */

enum class Phase {
    /** Lesson ready screen; waits for "Hai să începem". */
    INTRO,

    /** The child retells; text arrives in one or more pieces until "Gata, am terminat". */
    RETELLING,

    /** Waiting for assess_recall. */
    ASSESSING,

    /** Idea chips and Capy's praise; waits for "Hai să le căutăm împreună" / "Hai la test". */
    COVERAGE,

    /** Hint ladder, one gap at a time, in lesson order. */
    HINTS,

    /** One question per key idea, in lesson order. */
    TEST,

    /** Waiting for summarize_session. */
    SUMMARIZING,

    /** "N din M"; waits for "Gata pe azi". */
    SUMMARY,
    DONE,
}

/** The three hint steps for one gap. The answer is only given at [EXPLAIN]. */
enum class HintStep(val number: Int) { BROAD(1), CLUE(2), EXPLAIN(3) }

enum class Outcome { RECALLED_ALONE, RECALLED_WITH_HINT, EXPLAINED }

enum class CheckResult { CORRECT, NOT_YET, UNJUDGED }

enum class InputMode { VOICE, VOICE_FALLBACK, KEYBOARD }

enum class Speaker { CAPY, CHILD }

/** Matches the Capy PNGs in the design system. */
enum class CapyMood { NEUTRAL, LISTENING, THINKING, READING, HAPPY, ENCOURAGING, SLEEPY }

enum class PauseReason { RATE_LIMITED, OFFLINE, FAILED }

data class Turn(val speaker: Speaker, val text: String, val inputMode: InputMode? = null)

data class IdeaResult(
    val ideaId: String,
    val retellStatus: CoverageStatus? = null,
    val evidence: String = "",
    val outcome: Outcome? = null,
    /** Hint steps shown for this idea (0 when recalled alone). */
    val hintsUsed: Int = 0,
    val checkResult: CheckResult? = null,
    /** "Cred că am dreptate": accepted in the session, flagged for the parent. */
    val childDisputed: Boolean = false,
)

/** The test feedback bottom sheet. */
data class Feedback(val ideaId: String, val result: CheckResult, val text: String)

/** What the engine needs from the AI next. The runner executes it and reports back. */
sealed interface Request {
    data class AssessRecall(val transcript: String) : Request

    data class JudgeAnswer(
        val ideaId: String,
        val question: String,
        val answer: String,
        val purpose: Purpose,
    ) : Request

    data object Summarize : Request
}

enum class Purpose { HINT, TEST }

/** What the child (or the UI) did. */
sealed interface Input {
    data class Say(val text: String, val mode: InputMode = InputMode.KEYBOARD) : Input

    /** "Gata, am terminat". */
    data object FinishRetelling : Input

    /** "Nu știu" / "Nu-mi amintesc": next step, no judging. */
    data object DontKnow : Input

    /** "Hai să începem", "Hai să le căutăm împreună", "Mai departe", "Gata pe azi". */
    data object Continue : Input

    /** "Cred că am dreptate" on a not-yet answer. */
    data object Dispute : Input

    /** "Continuăm" after a pause. */
    data object Resume : Input
}

/** How an answer was judged. */
enum class DecidedBy { KEYWORDS, MODEL, NOT_JUDGED }

/** What came back for a [Request]. */
sealed interface AiOutcome {
    data class Recall(val ideas: Map<String, Pair<CoverageStatus, String>>, val praise: String) : AiOutcome

    /** [status] null = could not be judged (budget used up and keywords inconclusive). */
    data class Answer(val status: CoverageStatus?, val reply: String?, val decidedBy: DecidedBy) : AiOutcome

    /** [childSummary]/[parentNote] null = written locally (no model call). */
    data class Summary(val childSummary: String?, val parentNote: String?) : AiOutcome

    data class Unavailable(val reason: PauseReason, val retryAfterSeconds: Int?) : AiOutcome
}

data class SessionState(
    val lessonSha256: String,
    val phase: Phase,
    val results: List<IdeaResult>,
    val retelling: String = "",
    val praise: String = "",
    /** Ideas to work on with hints, in lesson order. */
    val gaps: List<String> = emptyList(),
    val gapIndex: Int = 0,
    val hintStep: HintStep = HintStep.BROAD,
    val testIndex: Int = 0,
    val feedback: Feedback? = null,
    /** Set right after a not-yet answer: "Cred că am dreptate" applies to this idea. */
    val disputable: String? = null,
    val pending: Request? = null,
    val paused: PauseReason? = null,
    val mood: CapyMood = CapyMood.NEUTRAL,
    val callsUsed: Int = 0,
    val tokensUsed: Int = 0,
    val childSummary: String = "",
    val parentNote: String = "",
    val turns: List<Turn> = emptyList(),
) {
    fun result(id: String): IdeaResult = results.first { it.ideaId == id }

    fun withResult(id: String, f: (IdeaResult) -> IdeaResult): SessionState =
        copy(results = results.map { if (it.ideaId == id) f(it) else it })

    val score: Int get() = results.count { it.checkResult == CheckResult.CORRECT }
    val currentGap: String? get() = gaps.getOrNull(gapIndex)
}

/** The result of one engine step: the new state, Capy's new lines, and maybe a request. */
data class Update(val state: SessionState, val capySaid: List<String>, val request: Request?)

data class Limits(val maxCalls: Int = 25, val maxTokens: Int = 40_000)
