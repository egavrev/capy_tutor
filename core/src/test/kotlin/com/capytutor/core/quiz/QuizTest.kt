package com.capytutor.core.quiz

import com.capytutor.core.reward.Bows
import com.capytutor.core.reward.CornerItem
import com.capytutor.core.schema.Validated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QuizTest {
    private val ids = listOf("i1", "i2", "i3", "i4", "i5")

    private val sample = """
        Iată quiz-ul:
        ```json
        {"questions": [
          {"type": "choice", "ideas": ["i1"], "question": "Unde a fost găsită Lucy?", "options": ["În Kenya", "În Etiopia", "În Egipt"], "answer": "în etiopia", "explanation": "Lucy a fost găsită în Etiopia."},
          {"type": "truefalse", "ideas": ["i2"], "statement": "Primii oameni au trăit în Africa.", "answer": true, "explanation": "Da, în Africa."},
          {"type": "truefalse", "ideas": ["i3"], "statement": "Oamenii primitivi aveau unelte de fier.", "answer": false, "explanation": "Uneltele erau din piatră."},
          {"type": "order", "ideas": ["i2", "i4"], "question": "Pune în ordine, de la cel mai vechi:", "items": ["Paleolitic", "Mezolitic", "Neolitic"], "explanation": "Paleolitic, apoi Mezolitic, apoi Neolitic."},
          {"type": "match", "ideas": ["i5"], "question": "Potrivește:", "pairs": [{"left": "Lucy", "right": "Etiopia"}, {"left": "Neolitic", "right": "agricultura"}, {"left": "Paleolitic", "right": "vânătoarea"}], "explanation": "Fiecare cu perechea lui."}
        ]}
        ```
    """.trimIndent()

    private fun quiz(raw: String = sample): Quiz = when (val v = QuizSchema.parse(raw, "sha", ids)) {
        is Validated.Ok -> v.value
        is Validated.Invalid -> { fail(v.reason); error("unreachable") }
    }

    private fun invalid(raw: String): String = (QuizSchema.parse(raw, "sha", ids) as? Validated.Invalid)?.reason ?: error("should be invalid")

    @Test
    fun parsesAllFourTypes() {
        val q = quiz()
        assertEquals(listOf(QuizType.CHOICE, QuizType.TRUE_FALSE, QuizType.TRUE_FALSE, QuizType.ORDER, QuizType.MATCH), q.questions.map { it.type })
        assertEquals(1, (q.questions[0] as QuizQuestion.Choice).answer) // matched without case or diacritics
        assertEquals(false, (q.questions[2] as QuizQuestion.TrueFalse).answer)
    }

    @Test
    fun encodeRoundTrips() {
        val q = quiz()
        assertEquals(q, quiz(QuizSchema.encode(q)))
    }

    @Test
    fun rejectsBadQuizzes() {
        val q = quiz()
        val four = QuizSchema.encode(q.copy(questions = q.questions.take(4)))
        assertTrue(invalid(four).contains("5-10"))
        assertTrue(invalid(sample.replace("\"answer\": \"în etiopia\"", "\"answer\": \"În Maroc\"")).contains("not one of the options"))
        assertTrue(invalid(sample.replace("[\"i5\"]", "[\"i9\"]")).contains("unknown idea"))
        assertTrue(invalid(sample.replace("\"answer\": true", "\"answer\": \"da\"")).contains("true or false"))
        assertTrue(invalid(sample.replace("\"Mezolitic\"", "\"Paleolitic\"")).contains("repeat"))
        val onlyTrueFalse = """{"questions": [""" + (1..5).joinToString(",") {
            """{"type": "truefalse", "ideas": ["i1"], "statement": "Afirmația $it.", "answer": true, "explanation": "E."}"""
        } + "]}"
        assertTrue(invalid(onlyTrueFalse).contains("two question types"))
    }

    @Test
    fun gradesEveryType() {
        val q = quiz().questions
        assertTrue(QuizGrader.isRight(q[0], QuizAnswer.Choice(1)))
        assertFalse(QuizGrader.isRight(q[0], QuizAnswer.Choice(0)))
        assertTrue(QuizGrader.isRight(q[1], QuizAnswer.TrueFalse(true)))
        assertTrue(QuizGrader.isRight(q[3], QuizAnswer.Order(listOf(0, 1, 2))))
        assertFalse(QuizGrader.isRight(q[3], QuizAnswer.Order(listOf(1, 0, 2))))
        assertTrue(QuizGrader.isRight(q[4], QuizAnswer.Match(listOf(0, 1, 2))))
        assertFalse(QuizGrader.isRight(q[4], QuizAnswer.Match(listOf(0, 2, 1))))
        assertEquals("Paleolitic → Mezolitic → Neolitic", QuizGrader.rightAnswerText(q[3]))
        assertEquals("Fals", QuizGrader.rightAnswerText(q[2]))
    }

    @Test
    fun runsAQuizAndSurvivesARestart() {
        val q = quiz()
        var s = QuizEngine.start(q, seed = 42)
        s = QuizEngine.answer(q, s, QuizAnswer.Choice(1))
        assertTrue(s.checked)
        assertEquals(s, QuizEngine.answer(q, s, QuizAnswer.Choice(0))) // one answer per question
        s = QuizEngine.next(s)
        s = QuizEngine.answer(q, s, QuizAnswer.TrueFalse(false))
        assertEquals(s, QuizStateCodec.decode(QuizStateCodec.encode(s)))
        s = QuizEngine.next(s)
        s = QuizEngine.next(QuizEngine.answer(q, s, QuizAnswer.TrueFalse(false)))
        s = QuizEngine.next(QuizEngine.answer(q, s, QuizAnswer.Order(listOf(0, 1, 2))))
        s = QuizEngine.answer(q, s, QuizAnswer.Match(listOf(0, 1, 2)))
        assertEquals(s, QuizStateCodec.decode(QuizStateCodec.encode(s)))
        s = QuizEngine.next(s)
        assertTrue(s.done)
        assertEquals(4, s.correct)
        assertEquals(5, s.total)
        assertTrue(QuizGrader.isHappy(4, 5))
        assertFalse(QuizGrader.isHappy(3, 5))
    }

    @Test
    fun shuffleNeverShowsTheAnswerForOrderAndMatch() {
        val q = quiz().questions
        for (seed in 0L until 200L) {
            assertNotEquals(listOf(0, 1, 2), QuizEngine.shown(q[3], seed, 3))
            assertNotEquals(listOf(0, 1, 2), QuizEngine.shown(q[4], seed, 4))
            assertEquals(setOf(0, 1, 2), QuizEngine.shown(q[0], seed, 0).toSet())
        }
        assertEquals(QuizEngine.shown(q[3], 7, 3), QuizEngine.shown(q[3], 7, 3)) // stable across restarts
    }

    @Test
    fun bowsAreKindAndNeverTakenAway() {
        assertEquals(1, Bows.forSession(0, 10))
        assertEquals(1, Bows.forSession(4, 10))
        assertEquals(2, Bows.forSession(5, 10))
        assertEquals(3, Bows.forSession(8, 10))
        assertEquals(1, Bows.forSession(0, 0))
        assertEquals(3, Bows.forLesson(listOf(1, 3, 2)))
        assertEquals(0, Bows.forLesson(emptyList()))
        assertEquals(0, Bows.added(previousBest = 3, session = 1))
        assertEquals(1, Bows.added(previousBest = 2, session = 3))
        assertEquals(emptyList<CornerItem>(), CornerItem.unlocked(2))
        assertEquals(listOf(CornerItem.CUSHION), CornerItem.unlocked(3))
        assertEquals(CornerItem.CUP to 5, CornerItem.next(3))
        assertEquals(null, CornerItem.next(100))
    }
}
