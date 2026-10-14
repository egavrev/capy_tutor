package com.capytutor.core.session

import com.capytutor.core.pack.LessonPack
import com.capytutor.core.pack.PackIdea
import com.capytutor.core.safety.SafetyFilter
import com.capytutor.core.schema.AnswerReply
import com.capytutor.core.schema.CoverageStatus
import com.capytutor.core.schema.CoverageStatus.COVERED
import com.capytutor.core.schema.CoverageStatus.MISSED
import com.capytutor.core.schema.CoverageStatus.PARTIAL
import com.capytutor.core.schema.CoverageStatus.WRONG
import com.capytutor.core.schema.RecallIdea
import com.capytutor.core.schema.RecallReply
import com.capytutor.core.schema.SummaryReply
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

val testPack = LessonPack(
    title = "Apariția omului",
    lessonSha256 = "abc",
    ideas = listOf(
        PackIdea(
            "i1", 0, "Primii oameni au trăit în Africa.", listOf(listOf("Africa", "african"), listOf("oameni", "strămoși")),
            "Unde au trăit primii oameni?", "Este un continent foarte cald.", "Primii oameni au trăit în Africa, un continent cald.",
            "Pe ce continent au trăit primii oameni?", "în Africa",
        ),
        PackIdea(
            "i2", 1, "Lucy este o fosilă găsită în Etiopia.", listOf(listOf("Lucy", "Lusi"), listOf("Etiopia")),
            "Ce știi despre cea mai cunoscută fosilă?", "Gândește-te la o țară din estul Africii.",
            "Lucy e un schelet foarte vechi, găsit în Etiopia.", "Unde a fost găsită Lucy?", "în Etiopia",
        ),
        PackIdea(
            "i3", 2, "Focul îi încălzea pe oameni.", listOf(listOf("foc", "focul"), listOf("încălzea", "cald", "căldură")),
            "La ce le folosea focul oamenilor?", "Gândește-te la o seară rece de iarnă.",
            "Focul îi încălzea pe oameni când era frig.", "Ce făcea focul pentru oameni?", "îi încălzea",
        ),
    ),
)

/** Replays recorded replies and remembers what it was asked. */
class FakeAi : SessionAi {
    val recall = ArrayDeque<AiReply<RecallReply>>()
    val answers = ArrayDeque<AiReply<AnswerReply>>()
    val summaries = ArrayDeque<AiReply<SummaryReply>>()
    val sentTranscripts = mutableListOf<String>()
    val sentAnswers = mutableListOf<String>()

    override suspend fun assessRecall(pack: LessonPack, transcript: String): AiReply<RecallReply> {
        sentTranscripts += transcript
        return recall.removeFirst()
    }

    override suspend fun assessAnswer(pack: LessonPack, idea: PackIdea, question: String, answer: String, purpose: Purpose): AiReply<AnswerReply> {
        sentAnswers += answer
        return answers.removeFirst()
    }

    override suspend fun summarize(pack: LessonPack, state: SessionState): AiReply<SummaryReply> = summaries.removeFirst()
}

fun recallOf(vararg s: Pair<String, CoverageStatus>) =
    AiReply.Ok(RecallReply(s.map { RecallIdea(it.first, it.second, "", "") }, "Ai explicat clar unde au trăit primii oameni!"), 1, 2000)

fun answer(status: CoverageStatus, reply: String = "Aproape!") = AiReply.Ok(AnswerReply(status, reply), 1, 600)

class SessionEngineTest {

    private val ai = FakeAi()
    private val runner = SessionRunner(testPack, ai, SafetyFilter(listOf("Vali")))

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    @Test
    fun fullSessionFollowsTheHintLadderAndTheTest() = run {
        var u = runner.start()
        assertEquals(Phase.INTRO, u.state.phase)

        u = runner.send(u.state, Input.Continue)
        assertEquals(listOf(CapyLines.ASK_RETELL), u.capySaid)
        u = runner.send(u.state, Input.Say("păi primii oameni au trăit în africa"))
        u = runner.send(u.state, Input.Say("și aveau foc"))
        ai.recall += recallOf("i1" to COVERED, "i2" to MISSED, "i3" to PARTIAL)
        u = runner.send(u.state, Input.FinishRetelling)
        assertEquals("păi primii oameni au trăit în africa și aveau foc", ai.sentTranscripts.single())
        assertEquals(Phase.COVERAGE, u.state.phase)
        assertEquals(listOf("i2", "i3"), u.state.gaps)
        assertTrue(u.capySaid.last().contains("1 din 3"))

        // Gap i2, step 1: a vague answer goes to the model, which says "partial" -> clue.
        u = runner.send(u.state, Input.Continue)
        assertEquals("Ce știi despre cea mai cunoscută fosilă?", u.capySaid.last())
        ai.answers += answer(PARTIAL)
        u = runner.send(u.state, Input.Say("e o fosila veche"))
        assertEquals(HintStep.CLUE, u.state.hintStep)
        assertEquals(listOf(CapyLines.NOT_YET_LOOK_TOGETHER, "Gândește-te la o țară din estul Africii."), u.capySaid)

        // Step 2: keywords settle it (no model call): recalled with a hint, on to gap i3.
        u = runner.send(u.state, Input.Say("lusi a fost gasita in etiopia"))
        assertEquals(1, ai.sentAnswers.size)
        assertEquals(Outcome.RECALLED_WITH_HINT, u.state.result("i2").outcome)
        assertEquals(2, u.state.result("i2").hintsUsed)
        assertEquals("La ce le folosea focul oamenilor?", u.capySaid.last())

        // Gap i3: "Nu știu" twice -> clue, then the explanation (only now) and say-back.
        u = runner.send(u.state, Input.DontKnow)
        assertEquals(HintStep.CLUE, u.state.hintStep)
        assertFalse(u.capySaid.any { it.contains("îi încălzea pe oameni când era frig") })
        u = runner.send(u.state, Input.DontKnow)
        assertEquals(HintStep.EXPLAIN, u.state.hintStep)
        assertEquals(listOf("Focul îi încălzea pe oameni când era frig.", CapyLines.SAY_IT_BACK), u.capySaid)
        u = runner.send(u.state, Input.Say("focul le dadea caldura"))
        assertEquals(Outcome.EXPLAINED, u.state.result("i3").outcome)
        assertEquals(3, u.state.result("i3").hintsUsed)

        // Test.
        assertEquals(Phase.TEST, u.state.phase)
        assertEquals("Pe ce continent au trăit primii oameni?", u.capySaid.last())
        u = runner.send(u.state, Input.Say("in africa"))
        assertEquals(CheckResult.CORRECT, u.state.feedback?.result)
        u = runner.send(u.state, Input.Continue)
        ai.answers += answer(WRONG, "Hai să ne uităm împreună.")
        u = runner.send(u.state, Input.Say("in egipt"))
        assertEquals(CapyLines.notYetAnswer("în Etiopia"), u.capySaid.single())
        u = runner.send(u.state, Input.Dispute)
        assertTrue(u.state.result("i2").childDisputed)
        assertEquals(CheckResult.CORRECT, u.state.result("i2").checkResult)
        u = runner.send(u.state, Input.Continue)
        u = runner.send(u.state, Input.DontKnow)
        assertEquals(CheckResult.NOT_YET, u.state.result("i3").checkResult)

        ai.summaries += AiReply.Ok(SummaryReply("Ai lucrat foarte frumos azi!", "De recapitulat: focul."), 1, 700)
        u = runner.send(u.state, Input.Continue)
        assertEquals(Phase.SUMMARY, u.state.phase)
        assertEquals(listOf("2 din 3", "Ai lucrat foarte frumos azi!"), u.capySaid)
        assertEquals(CapyMood.ENCOURAGING, u.state.mood) // 2/3 < 70 %
        assertEquals("De recapitulat: focul.", u.state.parentNote)
        assertEquals(4, u.state.callsUsed) // recall + 2 answers + summary
        u = runner.send(u.state, Input.Continue)
        assertEquals(Phase.DONE, u.state.phase)
    }

    @Test
    fun rateLimitPausesAndResumesAfterBeingSavedAndRestored() = run {
        var u = runner.send(runner.start().state, Input.Continue)
        u = runner.send(u.state, Input.Say("primii oameni in africa"))
        ai.recall += AiReply.Unavailable(PauseReason.RATE_LIMITED, 7)
        u = runner.send(u.state, Input.FinishRetelling)
        assertEquals(PauseReason.RATE_LIMITED, u.state.paused)
        assertEquals(CapyLines.PAUSE, u.capySaid.last())
        assertEquals(CapyMood.SLEEPY, u.state.mood)

        // The app is killed here; the saved state comes back.
        val restored = SessionCodec.decode(SessionCodec.encode(u.state))
        assertEquals(u.state, restored)

        // Inputs other than "Continuăm" do nothing while paused.
        assertEquals(restored, runner.send(restored, Input.Say("hei")).state.copy(turns = restored.turns))

        ai.recall += recallOf("i1" to COVERED, "i2" to COVERED, "i3" to COVERED)
        u = runner.resume(restored)
        assertNull(u.state.paused)
        assertEquals(Phase.COVERAGE, u.state.phase)
        assertEquals(CapyLines.RESUMED, u.capySaid.first())
        assertEquals(2, ai.sentTranscripts.size) // the same request, sent again
    }

    @Test
    fun keywordCoveredAnswerNeedsNoModelEvenWhenOffline() = run {
        var u = runner.send(runner.start().state, Input.Continue)
        u = runner.send(u.state, Input.Say("nu mai stiu nimic despre lucy"))
        ai.recall += recallOf("i1" to MISSED, "i2" to MISSED, "i3" to MISSED)
        u = runner.send(u.state, Input.FinishRetelling)
        u = runner.send(u.state, Input.Continue)
        // No answers queued: a model call would fail the test.
        u = runner.send(u.state, Input.Say("strămoșii noștri au trăit în Africa"))
        assertEquals(Outcome.RECALLED_WITH_HINT, u.state.result("i1").outcome)
    }

    @Test
    fun pastTheBudgetAnswersAreKeywordOnlyAndTheSummaryIsLocal() = run {
        val tight = SessionRunner(testPack, ai, SafetyFilter(emptyList()), Limits(maxCalls = 1))
        var u = tight.send(tight.start().state, Input.Continue)
        u = tight.send(u.state, Input.Say("focul"))
        ai.recall += recallOf("i1" to COVERED, "i2" to COVERED, "i3" to PARTIAL)
        u = tight.send(u.state, Input.FinishRetelling)
        u = tight.send(u.state, Input.Continue)
        u = tight.send(u.state, Input.Say("ceva vag")) // inconclusive, no budget: treated as not yet
        assertEquals(HintStep.CLUE, u.state.hintStep)
        u = tight.send(u.state, Input.DontKnow)
        u = tight.send(u.state, Input.DontKnow) // skip say-back -> test
        u = tight.send(u.state, Input.Say("nu sunt sigur"))
        assertEquals(CheckResult.UNJUDGED, u.state.feedback?.result)
        repeat(2) { u = tight.send(u.state, Input.Continue); u = tight.send(u.state, Input.DontKnow) }
        u = tight.send(u.state, Input.Continue)
        assertEquals(Phase.SUMMARY, u.state.phase)
        assertEquals(CapyLines.summaryFallback(0, 3), u.state.childSummary)
        assertEquals(1, u.state.callsUsed)
        assertTrue(ai.sentAnswers.isEmpty())
    }

    @Test
    fun nothingRememberedSkipsTheModelAndMakesEveryIdeaAGap() = run {
        var u = runner.send(runner.start().state, Input.Continue)
        u = runner.send(u.state, Input.DontKnow)
        assertEquals(Phase.COVERAGE, u.state.phase)
        assertEquals(listOf("i1", "i2", "i3"), u.state.gaps)
        assertTrue(ai.sentTranscripts.isEmpty())
    }

    @Test
    fun emptyRetellingAsksForOneThing() = run {
        var u = runner.send(runner.start().state, Input.Continue)
        u = runner.send(u.state, Input.FinishRetelling)
        assertEquals(Phase.RETELLING, u.state.phase)
        assertEquals(listOf(CapyLines.RETELL_EMPTY), u.capySaid)
    }

    @Test
    fun childNameNeverReachesTheModel() = run {
        var u = runner.send(runner.start().state, Input.Continue)
        u = runner.send(u.state, Input.Say("eu Vali am citit despre africa, sunați la 069 123 456"))
        ai.recall += recallOf("i1" to COVERED, "i2" to MISSED, "i3" to MISSED)
        runner.send(u.state, Input.FinishRetelling)
        val sent = ai.sentTranscripts.single()
        assertFalse(sent, sent.contains("Vali"))
        assertFalse(sent, sent.contains("123"))
        assertTrue(sent.contains("copilul"))
    }

    @Test
    fun disputeInTheHintLadderAcceptsAndMovesOn() = run {
        var u = runner.send(runner.start().state, Input.Continue)
        u = runner.send(u.state, Input.Say("africa"))
        ai.recall += recallOf("i1" to COVERED, "i2" to MISSED, "i3" to COVERED)
        u = runner.send(u.state, Input.FinishRetelling)
        u = runner.send(u.state, Input.Continue)
        ai.answers += answer(MISSED)
        u = runner.send(u.state, Input.Say("o fosila din asia"))
        u = runner.send(u.state, Input.Dispute)
        assertTrue(u.state.result("i2").childDisputed)
        assertEquals(Phase.TEST, u.state.phase)
    }

    @Test
    fun allRememberedGoesStraightToTheTest() = run {
        var u = runner.send(runner.start().state, Input.Continue)
        u = runner.send(u.state, Input.Say("tot"))
        ai.recall += recallOf("i1" to COVERED, "i2" to COVERED, "i3" to COVERED)
        u = runner.send(u.state, Input.FinishRetelling)
        assertTrue(u.capySaid.last().contains("toate ideile"))
        u = runner.send(u.state, Input.Continue)
        assertEquals(Phase.TEST, u.state.phase)
        assertTrue(u.state.results.all { it.outcome == Outcome.RECALLED_ALONE })
    }
}

class SafeHintsTest {
    @Test
    fun hintsThatRevealTheAnswerAreReplaced() {
        val leaky = testPack.ideas[0].copy(broadQuestion = "Primii oameni au trăit în Africa?", clue = "Se numește Africa.")
        val h = SafeHints.of(leaky)
        assertEquals("Pe ce continent au trăit primii oameni?", h.broadQuestion)
        assertEquals("Completează: „Primii oameni au trăit în ….”", h.clue)
        assertTrue(h.broadReplaced && h.clueReplaced)
    }

    @Test
    fun safeHintsAreKept() {
        val h = SafeHints.of(testPack.ideas[1])
        assertEquals(testPack.ideas[1].clue, h.clue)
        assertFalse(h.clueReplaced)
    }

    @Test
    fun clozeBlanksTheDetailsNotAlreadyAsked() {
        val lucy = PackIdea(
            "i3", 2, "Lucy este o fosilă găsită în Etiopia, veche de 3,2 milioane de ani.",
            listOf(listOf("Lucy", "Lusi"), listOf("Etiopia"), listOf("3,2 milioane")), "q", "c", "e", "q", "a",
        )
        assertEquals("Completează: „… este o fosilă găsită în …, veche de … de ani.”", SafeHints.clozeClue(lucy, "Ce știi despre cea mai cunoscută fosilă?"))
        assertEquals("Completează: „Lucy este o fosilă găsită în …, veche de … de ani.”", SafeHints.clozeClue(lucy, "Ce știi despre Lucy?"))
        val seasons = PackIdea(
            "i6", 5, "Anotimpurile apar pentru că axa Pământului este înclinată.",
            listOf(listOf("anotimpuri"), listOf("axa", "axă"), listOf("înclinată", "înclinare")), "q", "c", "e", "q", "a",
        )
        assertEquals("Completează: „Anotimpurile apar pentru că … Pământului este ….”", SafeHints.clozeClue(seasons, "De ce apar anotimpurile pe Pământ?"))
    }

    @Test
    fun numbersGetANumberClue() {
        assertEquals("Răspunsul conține un număr. Gândește-te la el.", SafeHints.letterClue("24 de ore"))
    }
}

class AnswerJudgeTest {
    @Test
    fun groupsNamedInTheQuestionCountAsGiven() {
        val lucy = testPack.ideas[1]
        assertTrue(AnswerJudge.keywordCovered(lucy, "Unde a fost găsită Lucy?", "în etiopia"))
        assertFalse(AnswerJudge.keywordCovered(lucy, "Unde a fost găsită Lucy?", "în egipt"))
        assertFalse(AnswerJudge.keywordCovered(lucy, "Ce știi despre cea mai cunoscută fosilă?", "în etiopia"))
    }
}
