package com.capytutor.testbench

import com.capytutor.core.RomanianText
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.session.CheckResult
import com.capytutor.core.session.HintStep
import com.capytutor.core.session.Input
import com.capytutor.core.session.InputMode
import com.capytutor.core.session.Outcome
import com.capytutor.core.session.Phase
import com.capytutor.core.session.SessionCodec
import com.capytutor.core.session.SessionRunner
import com.capytutor.core.session.SessionState
import com.capytutor.core.session.Speaker
import com.capytutor.core.session.Update
import kotlinx.coroutines.delay

/** Turns a typed line into what the child did. Shared by `play` and the session scripts. */
object TypedInput {
    fun parse(line: String): Input? {
        val t = line.trim()
        val folded = RomanianText.foldForMatching(t).trimEnd('.', '!')
        return when {
            t.isEmpty() -> Input.Continue
            folded in setOf("/hai", "/mai departe", "/continua", "/ok") -> Input.Continue
            folded in setOf("/gata", "gata, am terminat", "gata am terminat") -> Input.FinishRetelling
            folded in setOf("/nustiu", "/nu stiu", "nu stiu", "nu-mi amintesc", "nu mi amintesc") -> Input.DontKnow
            folded in setOf("/dreptate", "cred ca am dreptate") -> Input.Dispute
            folded == "/stop" -> null
            else -> Input.Say(t, InputMode.KEYBOARD)
        }
    }
}

/**
 * A scripted child for `session`. Lines are `key: what she says`, where the key names the
 * situation, so the script works whatever gaps the model finds:
 *
 *     retell: păi primii oameni au trăit în africa      (one line per piece; then "Gata")
 *     i3.broad: lusi e din etiopia                       (answer to step 1 of the hint ladder)
 *     i3.clue: ... / i3.explain: ...                     (steps 2 and 3; missing = "Nu știu")
 *     i3.test: în etiopia                                (the final test; missing = "Nu știu")
 *     i3.feedback: /dreptate                             (after the test feedback; missing = "Mai departe")
 */
class SessionScript(text: String) {
    private val entries: Map<String, List<String>> = text.lines()
        .map { it.substringBefore(" #").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && ':' in it }
        .groupBy({ it.substringBefore(':').trim().lowercase() }, { it.substringAfter(':').trim() })
    private var retellIndex = 0
    private val used = mutableSetOf<String>()

    fun next(s: SessionState): Input = when (s.phase) {
        Phase.INTRO, Phase.COVERAGE, Phase.SUMMARY -> Input.Continue
        Phase.RETELLING -> entries["retell"]?.getOrNull(retellIndex++)?.let { Input.Say(it, InputMode.VOICE) } ?: Input.FinishRetelling
        Phase.HINTS -> {
            val step = when (s.hintStep) { HintStep.BROAD -> "broad"; HintStep.CLUE -> "clue"; HintStep.EXPLAIN -> "explain" }
            say("${s.currentGap}.$step")
        }
        Phase.TEST -> {
            val id = s.results[s.testIndex].ideaId
            if (s.feedback != null) once("$id.feedback")?.let { TypedInput.parse(it) } ?: Input.Continue else say("$id.test")
        }
        Phase.ASSESSING, Phase.SUMMARIZING, Phase.DONE -> Input.Continue
    }

    private fun say(key: String): Input = once(key)?.let { TypedInput.parse(it) } ?: Input.DontKnow

    /** Each scripted answer is used once; asked again, the child says "Nu știu" / "Mai departe". */
    private fun once(key: String): String? = if (used.add(key)) entries[key]?.firstOrNull() else null
}

/** Runs a session, printing it as a chat, and keeps a log for the report. */
class SessionDriver(
    private val runner: SessionRunner,
    private val pack: LessonPack,
    private val print: (String) -> Unit = ::println,
    private val save: (SessionState) -> Unit = {},
    /** Scripted runs print what the child says; in `play` you just typed it. */
    private val echoChild: Boolean = true,
) {
    data class Step(val input: Input, val capy: List<String>, val millis: Long, val calls: Int, val tokens: Int, val paused: Boolean)

    val steps = mutableListOf<Step>()
    var pauses = 0
        private set

    fun begin(resumeFrom: SessionState?): SessionState {
        if (resumeFrom != null) {
            print("(sesiune reluată: ${resumeFrom.phase})")
            return resumeFrom
        }
        val u = runner.start()
        u.capySaid.forEach { print("Capy: $it") }
        save(u.state)
        return u.state
    }

    /** One child action. [waitOnPause]: wait out a rate limit and resume (scripted runs). */
    suspend fun step(state: SessionState, input: Input, waitOnPause: Boolean): SessionState {
        if (input is Input.Say) { if (echoChild) print("Copilul: ${input.text}") }
        else if (state.paused == null) print("Copilul: [${label(input)}]")
        val start = System.nanoTime()
        var u: Update = runner.send(state, input)
        log(input, u, state, start)
        while (waitOnPause && u.state.paused != null) {
            pauses++
            val wait = 15
            print("   (pauză: ${u.state.paused}; aștept ${wait}s și continui)")
            delay(wait * 1000L)
            val before = u.state
            val t = System.nanoTime()
            u = runner.resume(before)
            log(Input.Resume, u, before, t)
        }
        save(u.state)
        return u.state
    }

    private fun log(input: Input, u: Update, before: SessionState, start: Long) {
        val ms = (System.nanoTime() - start) / 1_000_000
        val calls = u.state.callsUsed - before.callsUsed
        u.capySaid.forEach { print("Capy: $it") }
        if (calls > 0) print("   ($calls ${if (calls == 1) "apel" else "apeluri"} la model, ${ms} ms)")
        steps += Step(input, u.capySaid, ms, calls, u.state.tokensUsed - before.tokensUsed, u.state.paused != null)
        // The state must survive a save and a restore at every step (M3 stores it).
        check(SessionCodec.decode(SessionCodec.encode(u.state)) == u.state) { "Session state did not survive a save/restore" }
    }

    fun label(i: Input) = when (i) {
        Input.Continue -> "Mai departe"
        Input.FinishRetelling -> "Gata, am terminat"
        Input.DontKnow -> "Nu știu"
        Input.Dispute -> "Cred că am dreptate"
        Input.Resume -> "Continuăm"
        is Input.Say -> i.text
    }

    fun report(final: SessionState, title: String): String = buildString {
        append(Report.header("Session: $title"))
        val ideas = pack.ideas.associateBy { it.id }
        appendLine("**${final.score} din ${final.results.size}** · ${final.callsUsed} model calls · ${final.tokensUsed} tokens · $pauses pauses · phase `${final.phase}`")
        appendLine()
        appendLine("Budget per session: 25 calls and 40,000 tokens (HANDOFF §8).")
        appendLine()
        appendLine("## Results per idea")
        appendLine()
        appendLine("| Idea | Retelling | Outcome | Hints | Test | Disputed |")
        appendLine("|---|---|---|---|---|---|")
        for (r in final.results) {
            val text = ideas[r.ideaId]?.text.orEmpty().let { if (it.length > 50) it.take(50) + "…" else it }
            appendLine(
                "| ${r.ideaId} ${text.replace("|", "\\|")} | ${r.retellStatus?.wire ?: "–"} | ${outcome(r.outcome)} | ${r.hintsUsed} | " +
                    "${check(r.checkResult)} | ${if (r.childDisputed) "yes" else ""} |",
            )
        }
        appendLine()
        val replaced = runner.engine.hints.filterValues { it.broadReplaced || it.clueReplaced }
        if (replaced.isNotEmpty()) {
            appendLine("Hints replaced on the phone because they revealed the answer: " +
                replaced.entries.joinToString { (id, h) -> listOfNotNull(if (h.broadReplaced) "$id question" else null, if (h.clueReplaced) "$id clue" else null).joinToString() })
            appendLine()
        }
        appendLine("## Parent note")
        appendLine()
        appendLine(final.parentNote.ifBlank { "–" })
        appendLine()
        appendLine("## Conversation")
        appendLine()
        val thinking = steps.filter { it.calls > 0 }
        for (t in final.turns) {
            val who = if (t.speaker == Speaker.CAPY) "**Capy**" else "*Copilul*"
            appendLine("- $who: ${t.text}")
        }
        appendLine()
        appendLine("## Model calls")
        appendLine()
        appendLine("| After | Calls | Tokens | Time |")
        appendLine("|---|---|---|---|")
        for (s in thinking) appendLine("| ${label(s.input).take(40).replace("|", "\\|")} | ${s.calls} | ${s.tokens} | ${s.millis} ms |")
    }

    private fun outcome(o: Outcome?) = when (o) {
        Outcome.RECALLED_ALONE -> "alone"
        Outcome.RECALLED_WITH_HINT -> "with a hint"
        Outcome.EXPLAINED -> "explained"
        null -> "–"
    }

    private fun check(c: CheckResult?) = when (c) {
        CheckResult.CORRECT -> "correct"
        CheckResult.NOT_YET -> "not yet"
        CheckResult.UNJUDGED -> "unjudged"
        null -> "–"
    }
}
