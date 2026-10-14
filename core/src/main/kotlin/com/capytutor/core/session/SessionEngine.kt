package com.capytutor.core.session

import com.capytutor.core.pack.LessonPack
import com.capytutor.core.pack.PackIdea
import com.capytutor.core.schema.CoverageStatus

/**
 * The session state machine (HANDOFF.md §5). Pure: no clock, no network, no storage.
 * Each step takes the current [SessionState] plus what happened and returns the next state,
 * what Capy says, and at most one [Request] for the AI.
 *
 * Fixed here, not left to the model: gaps one at a time in lesson order, at most 3 hint steps
 * per idea, the explanation only at step 3, "Nu știu" moves on without judging, no timers.
 */
class SessionEngine(private val pack: LessonPack) {

    private val ideas: Map<String, PackIdea> = pack.ideas.associateBy { it.id }
    private val order: List<String> = pack.ideas.sortedBy { it.order }.map { it.id }
    val hints: Map<String, SafeHints> = pack.ideas.associate { it.id to SafeHints.of(it) }

    fun start(): Update {
        val s = SessionState(
            lessonSha256 = pack.lessonSha256,
            phase = Phase.INTRO,
            results = order.map { IdeaResult(it) },
            mood = CapyMood.READING,
        )
        return say(s, CapyLines.intro(pack.title, order.size))
    }

    // ---- inputs from the child / UI ----------------------------------------------------------

    fun onInput(s0: SessionState, input: Input): Update {
        val s = if (input is Input.Say) s0.copy(turns = s0.turns + Turn(Speaker.CHILD, input.text, input.mode)) else s0

        if (s.paused != null) {
            return if (input == Input.Resume || input == Input.Continue) {
                val resumed = s.copy(paused = null, mood = CapyMood.THINKING)
                Update(resumed.addCapy(CapyLines.RESUMED), listOf(CapyLines.RESUMED), resumed.pending)
            } else {
                stay(s)
            }
        }
        if (s.pending != null) return stay(s) // waiting for the AI; the UI shows Capy thinking

        return when (s.phase) {
            Phase.INTRO -> when (input) {
                Input.Continue, is Input.Say -> startRetelling(s, (input as? Input.Say)?.text)
                else -> stay(s)
            }
            Phase.RETELLING -> retelling(s, input)
            Phase.COVERAGE -> if (input == Input.Continue) afterCoverage(s) else stay(s)
            Phase.HINTS -> hints(s, input)
            Phase.TEST -> test(s, input)
            Phase.SUMMARY -> if (input == Input.Continue) say(s.copy(phase = Phase.DONE, mood = CapyMood.HAPPY), CapyLines.DONE) else stay(s)
            Phase.ASSESSING, Phase.SUMMARIZING, Phase.DONE -> stay(s)
        }
    }

    // ---- results from the AI -----------------------------------------------------------------

    fun onAiResult(s0: SessionState, outcome: AiOutcome, callsUsed: Int = 0, tokensUsed: Int = 0): Update {
        val request = s0.pending ?: return stay(s0)
        val s = s0.copy(callsUsed = s0.callsUsed + callsUsed, tokensUsed = s0.tokensUsed + tokensUsed)

        if (outcome is AiOutcome.Unavailable) {
            // Keep the request; it is sent again on Resume. Nothing is lost.
            return say(s.copy(paused = outcome.reason, mood = CapyMood.SLEEPY), CapyLines.PAUSE)
        }
        val done = s.copy(pending = null)
        return when {
            request is Request.AssessRecall && outcome is AiOutcome.Recall -> recallAssessed(done, outcome)
            request is Request.JudgeAnswer && outcome is AiOutcome.Answer ->
                if (request.purpose == Purpose.HINT) hintJudged(done, request, outcome) else testJudged(done, request, outcome)
            request is Request.Summarize && outcome is AiOutcome.Summary -> summarized(done, outcome)
            else -> error("Outcome ${outcome::class.simpleName} does not answer ${request::class.simpleName}")
        }
    }

    // ---- retelling and coverage ----------------------------------------------------------------

    private fun startRetelling(s: SessionState, firstPiece: String?): Update {
        val r = s.copy(phase = Phase.RETELLING, mood = CapyMood.LISTENING, retelling = firstPiece?.trim().orEmpty())
        return say(r, CapyLines.ASK_RETELL)
    }

    private fun retelling(s: SessionState, input: Input): Update = when (input) {
        is Input.Say -> stay(s.copy(retelling = listOf(s.retelling, input.text.trim()).filter { it.isNotEmpty() }.joinToString(" ")))
        Input.FinishRetelling, Input.Continue ->
            if (s.retelling.isBlank()) {
                say(s, CapyLines.RETELL_EMPTY)
            } else {
                val r = s.copy(phase = Phase.ASSESSING, mood = CapyMood.THINKING)
                request(r, Request.AssessRecall(s.retelling))
            }
        // Nothing remembered: every idea is a gap, no model call needed.
        Input.DontKnow ->
            if (s.retelling.isBlank()) {
                val all = s.copy(
                    phase = Phase.COVERAGE,
                    results = s.results.map { it.copy(retellStatus = CoverageStatus.MISSED) },
                    gaps = order,
                    mood = CapyMood.ENCOURAGING,
                )
                say(all, CapyLines.RETELL_NOTHING, CapyLines.coverage(0, order.size))
            } else {
                onInput(s, Input.FinishRetelling)
            }
        else -> stay(s)
    }

    private fun recallAssessed(s: SessionState, r: AiOutcome.Recall): Update {
        val results = s.results.map { res ->
            val (status, evidence) = r.ideas[res.ideaId] ?: (CoverageStatus.MISSED to "")
            res.copy(
                retellStatus = status,
                evidence = evidence,
                outcome = if (status == CoverageStatus.COVERED) Outcome.RECALLED_ALONE else null,
            )
        }
        val gaps = order.filter { id -> results.first { it.ideaId == id }.retellStatus != CoverageStatus.COVERED }
        val known = order.size - gaps.size
        val c = s.copy(
            phase = Phase.COVERAGE,
            results = results,
            gaps = gaps,
            praise = r.praise,
            mood = if (known > 0) CapyMood.HAPPY else CapyMood.ENCOURAGING,
        )
        return say(c, *listOfNotNull(r.praise.takeIf { it.isNotBlank() }, CapyLines.coverage(known, order.size)).toTypedArray())
    }

    private fun afterCoverage(s: SessionState): Update =
        if (s.gaps.isEmpty()) startTest(s) else askHint(s.copy(phase = Phase.HINTS, gapIndex = 0, hintStep = HintStep.BROAD))

    // ---- hint ladder ---------------------------------------------------------------------------

    private fun hintQuestion(s: SessionState): String {
        val id = s.currentGap!!
        return when (s.hintStep) {
            HintStep.BROAD -> hints.getValue(id).broadQuestion
            HintStep.CLUE -> hints.getValue(id).clue
            HintStep.EXPLAIN -> ideas.getValue(id).explanation
        }
    }

    private fun askHint(s: SessionState, vararg before: String): Update {
        val id = s.currentGap!!
        val stepped = s.withResult(id) { it.copy(hintsUsed = maxOf(it.hintsUsed, s.hintStep.number)) }
        val ask = when (s.hintStep) {
            HintStep.BROAD -> listOf(hintQuestion(s))
            HintStep.CLUE -> listOf(hintQuestion(s))
            HintStep.EXPLAIN -> listOf(hintQuestion(s), CapyLines.SAY_IT_BACK)
        }
        return say(stepped.copy(mood = if (s.hintStep == HintStep.EXPLAIN) CapyMood.READING else CapyMood.NEUTRAL), *before, *ask.toTypedArray())
    }

    private fun hints(s: SessionState, input: Input): Update {
        val id = s.currentGap ?: return stay(s)
        return when (input) {
            is Input.Say -> request(
                s.copy(disputable = null, mood = CapyMood.THINKING),
                // At step 3 Capy's line *is* the answer, so the say-back is judged on its own.
                Request.JudgeAnswer(id, if (s.hintStep == HintStep.EXPLAIN) CapyLines.SAY_IT_BACK else hintQuestion(s), input.text, Purpose.HINT),
            )
            Input.DontKnow -> when (s.hintStep) {
                HintStep.BROAD -> askHint(s.copy(hintStep = HintStep.CLUE, disputable = null), CapyLines.NO_PROBLEM_CLUE)
                HintStep.CLUE -> askHint(s.copy(hintStep = HintStep.EXPLAIN, disputable = null))
                HintStep.EXPLAIN -> nextGap(s.withResult(id) { it.copy(outcome = Outcome.EXPLAINED) }, CapyLines.SKIP_SAY_BACK)
            }
            Input.Dispute -> s.disputable?.let { disputed ->
                // Accepted in the session, flagged for the parent (HANDOFF.md §5).
                val accepted = s.withResult(disputed) { it.copy(outcome = Outcome.RECALLED_WITH_HINT, childDisputed = true) }
                nextGap(accepted.copy(disputable = null), CapyLines.DISPUTE_NOTED)
            } ?: stay(s)
            else -> stay(s)
        }
    }

    private fun hintJudged(s: SessionState, req: Request.JudgeAnswer, a: AiOutcome.Answer): Update {
        val covered = a.status == CoverageStatus.COVERED
        return when (s.hintStep) {
            HintStep.BROAD, HintStep.CLUE ->
                if (covered) {
                    val done = s.withResult(req.ideaId) { it.copy(outcome = Outcome.RECALLED_WITH_HINT) }
                    nextGap(done.copy(mood = CapyMood.HAPPY), modelOr(a, CapyLines.RECALLED))
                } else {
                    val next = if (s.hintStep == HintStep.BROAD) HintStep.CLUE else HintStep.EXPLAIN
                    askHint(s.copy(hintStep = next, disputable = req.ideaId), CapyLines.NOT_YET_LOOK_TOGETHER)
                }
            HintStep.EXPLAIN -> {
                val done = s.withResult(req.ideaId) { it.copy(outcome = Outcome.EXPLAINED) }
                nextGap(done.copy(mood = if (covered) CapyMood.HAPPY else CapyMood.ENCOURAGING), if (covered) CapyLines.SAID_IT_BACK else CapyLines.TRIED_SAY_BACK)
            }
        }
    }

    private fun nextGap(s: SessionState, vararg before: String): Update {
        val next = s.gapIndex + 1
        return if (next < s.gaps.size) {
            askHint(s.copy(gapIndex = next, hintStep = HintStep.BROAD), *before, CapyLines.NEXT_IDEA)
        } else {
            startTest(s, *before)
        }
    }

    // ---- final test ----------------------------------------------------------------------------

    private fun startTest(s: SessionState, vararg before: String): Update {
        val t = s.copy(phase = Phase.TEST, testIndex = 0, feedback = null, disputable = null, mood = CapyMood.NEUTRAL)
        return say(t, *before, CapyLines.TEST_START, ideas.getValue(order[0]).checkQuestion)
    }

    private fun test(s: SessionState, input: Input): Update {
        val id = order[s.testIndex]
        val idea = ideas.getValue(id)
        if (s.feedback != null) {
            return when (input) {
                Input.Continue -> nextQuestion(s)
                Input.Dispute -> if (s.disputable == id) {
                    val accepted = s.withResult(id) { it.copy(checkResult = CheckResult.CORRECT, childDisputed = true) }
                    say(accepted.copy(disputable = null, feedback = s.feedback.copy(result = CheckResult.CORRECT)), CapyLines.DISPUTE_NOTED)
                } else {
                    stay(s)
                }
                else -> stay(s)
            }
        }
        return when (input) {
            is Input.Say -> request(s.copy(mood = CapyMood.THINKING), Request.JudgeAnswer(id, idea.checkQuestion, input.text, Purpose.TEST))
            Input.DontKnow -> feedback(s, id, CheckResult.NOT_YET, CapyLines.notYetAnswer(idea.checkAnswer), disputable = false)
            else -> stay(s)
        }
    }

    private fun testJudged(s: SessionState, req: Request.JudgeAnswer, a: AiOutcome.Answer): Update {
        val idea = ideas.getValue(req.ideaId)
        return when (a.status) {
            CoverageStatus.COVERED ->
                feedback(s, req.ideaId, CheckResult.CORRECT, listOfNotNull(CapyLines.CORRECT, a.reply?.takeIf { it.isNotBlank() }).joinToString(" "), disputable = false)
            null -> feedback(s, req.ideaId, CheckResult.UNJUDGED, CapyLines.unjudged(idea.checkAnswer), disputable = false)
            else -> feedback(s, req.ideaId, CheckResult.NOT_YET, CapyLines.notYetAnswer(idea.checkAnswer), disputable = true)
        }
    }

    private fun feedback(s: SessionState, id: String, result: CheckResult, text: String, disputable: Boolean): Update {
        val f = s.withResult(id) { it.copy(checkResult = result) }.copy(
            feedback = Feedback(id, result, text),
            disputable = if (disputable) id else null,
            mood = if (result == CheckResult.CORRECT) CapyMood.HAPPY else CapyMood.ENCOURAGING,
        )
        return say(f, text)
    }

    private fun nextQuestion(s: SessionState): Update {
        val next = s.testIndex + 1
        val cleared = s.copy(feedback = null, disputable = null, mood = CapyMood.NEUTRAL)
        return if (next < order.size) {
            say(cleared.copy(testIndex = next), ideas.getValue(order[next]).checkQuestion)
        } else {
            request(cleared.copy(phase = Phase.SUMMARIZING, mood = CapyMood.THINKING), Request.Summarize)
        }
    }

    // ---- summary -------------------------------------------------------------------------------

    private fun summarized(s: SessionState, a: AiOutcome.Summary): Update {
        val total = order.size
        val child = a.childSummary?.takeIf { it.isNotBlank() } ?: CapyLines.summaryFallback(s.score, total)
        val happy = s.score * 10 >= total * 7 // 70 %
        val done = s.copy(
            phase = Phase.SUMMARY,
            childSummary = child,
            parentNote = a.parentNote.orEmpty(),
            mood = if (happy) CapyMood.HAPPY else CapyMood.ENCOURAGING,
        )
        return say(done, CapyLines.score(s.score, total), child)
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun modelOr(a: AiOutcome.Answer, fallback: String) =
        a.reply?.takeIf { it.isNotBlank() && a.decidedBy == DecidedBy.MODEL } ?: fallback

    private fun SessionState.addCapy(vararg lines: String) = copy(turns = turns + lines.map { Turn(Speaker.CAPY, it) })

    private fun say(s: SessionState, vararg lines: String): Update {
        // "Uite un indiciu:" and the clue belong in one bubble.
        val merged = mutableListOf<String>()
        for (l in lines) if (merged.lastOrNull()?.endsWith(":") == true) merged[merged.lastIndex] += " $l" else merged += l
        return Update(s.addCapy(*merged.toTypedArray()), merged, null)
    }

    private fun request(s: SessionState, r: Request) = Update(s.copy(pending = r), emptyList(), r)

    private fun stay(s: SessionState) = Update(s, emptyList(), null)
}
