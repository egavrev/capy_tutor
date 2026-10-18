package com.capytutor.demo

import android.content.Context
import com.capytutor.AppContainer
import com.capytutor.core.coverage.KeywordMatcher
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.pack.PackIdea
import com.capytutor.core.reward.Corner
import com.capytutor.core.reward.CornerItem
import com.capytutor.core.schema.AnswerReply
import com.capytutor.core.schema.CoverageStatus
import com.capytutor.core.schema.RecallIdea
import com.capytutor.core.schema.RecallReply
import com.capytutor.core.schema.SummaryReply
import com.capytutor.core.session.AiReply
import com.capytutor.core.session.CheckResult
import com.capytutor.core.session.Purpose
import com.capytutor.core.session.SessionAi
import com.capytutor.core.session.SessionState
import com.capytutor.data.repo.ImportResult
import com.capytutor.data.settings.ChildGender
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Debug builds only. The CI screenshot tour starts the app with the intent extra `capy_demo`:
 * the app is set up with a sample child ("Ana", PIN 1234), the two sample lessons with the
 * test bench's generated questions and a prepared quiz each, and a keyword-only stand-in for the model. Nothing is sent
 * anywhere, so the tour needs no Groq key.
 */
object Demo {
    const val EXTRA = "capy_demo"

    /** Debug: `--es capy_corner design` fills Capy's room as in the design, for the screenshot tour. */
    const val CORNER = "capy_corner"
    const val NAME = "Ana"
    const val PIN = "1234"
    private val FILES = listOf("SAMPLE-aparitia-omului", "SAMPLE-pamantul")

    suspend fun seed(context: Context, c: AppContainer, lessons: Boolean) {
        c.demo = true
        val s = c.settings.settings.first { it.loaded }
        if (!s.setupDone) {
            c.settings.setChildName(NAME)
            c.settings.setGender(ChildGender.GIRL)
            c.settings.setPin(PIN)
            c.settings.finishSetup()
        }
        if (!lessons) return
        val existing = c.lessons.observeLessons().first()
        if (existing.isNotEmpty()) return
        for (name in FILES) {
            val md = context.assets.open("demo/$name.md").bufferedReader().readText()
            val pack = LessonPack.fromJson(context.assets.open("demo/$name.pack.json").bufferedReader().readText())
            val r = c.lessons.import(md, pack)
            if (r is ImportResult.Ok) {
                if (name == FILES.first()) c.lessons.setReady(r.lessonId, true)
                c.quizzes.importPrepared(r.lessonId, context.assets.open("demo/$name.quiz.json").bufferedReader().readText())
            }
        }
    }
}

/**
 * Capy's room as the design shows it: 107 bows earned, the pillow, plushies, lamp, plant, rug and
 * cup bought (45), so 62 left. The bows sit on lesson -1, which no screen shows.
 */
suspend fun Demo.seedCorner(c: AppContainer) {
    if (c.rewards.observe().first().total < 100) {
        c.rewards.addDemoBows(107)
    }
    c.corner.set(Corner(owned = setOf(CornerItem.PILLOW, CornerItem.PLUSH, CornerItem.LAMP, CornerItem.PLANT, CornerItem.RUG, CornerItem.CUP)))
}

/** Stands in for Whisper in the tour: the emulator has no voice, so it "hears" a fixed sentence. */
object DemoSpeechToText : com.capytutor.ai.SpeechToText {
    override val id: String = "demo/stt"
    const val HEARD = "Primii oameni au trăit în Africa, iar Lucy a fost găsită în Etiopia."

    override suspend fun transcribe(audio: java.io.File, languageTag: String): com.capytutor.ai.SttResult {
        delay(600)
        return com.capytutor.ai.SttResult.Ok(HEARD)
    }
}

/** Judges by keywords only, after a short pause so "Capy se gândește" can be seen. */
object DemoSessionAi : SessionAi {
    private const val THINK_MS = 600L

    /**
     * Renders every real prompt template, so the CI tour runs the prompt code on Android too
     * (a regex that Android rejects once crashed every real Groq call while the tour stayed green).
     */
    private fun realPrompts() {
        val P = com.capytutor.core.prompts.Prompts
        val values = listOf("answer", "child", "ideas", "idea", "purpose", "question", "results", "score", "text", "tiebreak", "title", "total", "transcript")
            .associateWith { "x" }
        P.system()
        listOf(P.ENRICH_IDEAS, P.ASSESS_RECALL, P.ASSESS_ANSWER, P.SUMMARIZE_SESSION, P.MAKE_QUIZ).forEach { P.render(it, values) }
    }

    override suspend fun assessRecall(pack: LessonPack, transcript: String): AiReply<RecallReply> {
        delay(THINK_MS)
        realPrompts()
        val ideas = pack.ideas.map { i ->
            val n = KeywordMatcher.matchedGroups(transcript, i.keywords)
            val status = when {
                n > 0 -> CoverageStatus.COVERED
                else -> CoverageStatus.MISSED
            }
            RecallIdea(i.id, status, "", "")
        }
        val praise = if (ideas.any { it.status == CoverageStatus.COVERED }) "Mi-a plăcut cum ai povestit!" else ""
        return AiReply.Ok(RecallReply(ideas, praise), calls = 1, tokens = 0)
    }

    override suspend fun assessAnswer(pack: LessonPack, idea: PackIdea, question: String, answer: String, purpose: Purpose): AiReply<AnswerReply> {
        delay(THINK_MS)
        realPrompts()
        val ok = KeywordMatcher.matchedGroups(answer, idea.keywords) > 0 ||
            KeywordMatcher.isCandidateCovered(answer, listOf(idea.checkAnswer.split(' ', ',').filter { it.length > 3 }))
        return AiReply.Ok(
            AnswerReply(if (ok) CoverageStatus.COVERED else CoverageStatus.MISSED, if (ok) "" else ""),
            calls = 1,
            tokens = 0,
        )
    }

    override suspend fun summarize(pack: LessonPack, state: SessionState): AiReply<SummaryReply> {
        delay(THINK_MS)
        val right = state.results.count { it.checkResult == CheckResult.CORRECT }
        return AiReply.Ok(
            SummaryReply(
                "Ai lucrat frumos azi! Ai răspuns bine la $right întrebări.",
                "Sesiune demonstrativă (fără Groq).",
            ),
            calls = 1,
            tokens = 0,
        )
    }
}
