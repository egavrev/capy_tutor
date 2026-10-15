package com.capytutor.ai

import com.capytutor.ai.groq.Groq
import com.capytutor.core.lesson.LessonParser
import com.capytutor.core.lesson.ParseResult
import com.capytutor.core.quiz.QuizType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuizMakerTest {
    private val lesson = (
        LessonParser.parse(
            """
            ---
            subject: Geografie
            title: Pământul
            ---
            # Pământul
            ${"Pământul este planeta pe care trăim, a treia de la Soare, și are un satelit numit Luna. ".repeat(8)}
            ## Idei cheie
            - Pământul este a treia planetă de la Soare.
            - Luna este satelitul natural al Pământului.
            - Pământul se rotește în jurul axei sale într-o zi.
            """.trimIndent(),
        ) as ParseResult.Ok
        ).lesson

    private val good = """{"questions":[
        {"type":"choice","ideas":["i1"],"question":"A câta planetă de la Soare este Pământul?","options":["A doua","A treia","A patra"],"answer":"A treia","explanation":"Pământul este a treia planetă."},
        {"type":"truefalse","ideas":["i2"],"statement":"Luna este satelitul Pământului.","answer":true,"explanation":"Da, Luna."},
        {"type":"truefalse","ideas":["i3"],"statement":"Pământul se rotește în jurul axei într-un an.","answer":false,"explanation":"Într-o zi."},
        {"type":"choice","ideas":["i2"],"question":"Cum se numește satelitul Pământului?","options":["Luna","Marte","Venus"],"answer":"Luna","explanation":"Luna."},
        {"type":"match","ideas":["i1","i2","i3"],"question":"Potrivește:","pairs":[{"left":"Luna","right":"satelit"},{"left":"Soarele","right":"stea"},{"left":"Pământul","right":"planetă"}],"explanation":"Fiecare cu perechea lui."}
    ]}"""

    private val usage = InMemoryUsage()
    private fun maker(t: FakeTransport) = QuizMaker(ProviderRouter({ Groq.forRoute(it, { "k" }, t) }, usage, { LocalDate.of(2026, 10, 15) }))

    @Test
    fun aValidReplyBecomesAQuiz() = runBlocking {
        val t = FakeTransport(groqOk(good))
        val r = maker(t).make(lesson)
        r as QuizResult.Ok
        assertEquals(5, r.quiz.questions.size)
        assertEquals(QuizType.MATCH, r.quiz.questions.last().type)
        assertEquals(lesson.sha256, r.quiz.lessonSha256)
        assertTrue(t.sent.single().body.contains("i3: Pământul se rotește"))
        assertEquals("make_quiz", usage.records.single().callType)
    }

    @Test
    fun aBadReplyIsRetriedOnce() = runBlocking {
        val bad = good.replace("\"answer\":\"A treia\"", "\"answer\":\"A cincea\"")
        val t = FakeTransport(groqOk(bad), groqOk(good))
        assertTrue(maker(t).make(lesson) is QuizResult.Ok)
        assertEquals(2, t.sent.size)
        assertTrue(t.sent[1].body.contains("not one of the options"))
    }

    @Test
    fun aRateLimitAsksToWait() = runBlocking {
        val r = maker(FakeTransport(groq429)).make(lesson) as QuizResult.Failed
        assertEquals(7, r.retryAfterSeconds)
        assertTrue(r.retryable)
    }
}
