package com.capytutor.ai

import com.capytutor.ai.groq.Groq
import com.capytutor.ai.http.HttpResponse
import com.capytutor.ai.http.HttpTransport
import com.capytutor.ai.http.TransportException
import com.capytutor.core.config.CoreConfig
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.pack.PackIdea
import com.capytutor.core.schema.CoverageStatus
import com.capytutor.core.session.AiReply
import com.capytutor.core.session.PauseReason
import com.capytutor.core.session.Purpose
import com.capytutor.core.session.SessionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ModelSessionAiTest {

    private val pack = LessonPack(
        "Pământul", "sha",
        listOf(
            PackIdea("i1", 0, "Pământul este a treia planetă de la Soare.", listOf(listOf("a treia", "3"), listOf("Soare")), "q", "c", "e", "Al câtelea e Pământul de la Soare?", "a treia planetă"),
            PackIdea("i2", 1, "Luna este satelitul Pământului.", listOf(listOf("Luna")), "q", "c", "e", "Ce satelit are Pământul?", "Luna"),
        ),
    )

    private val usage = InMemoryUsage()
    private fun ai(t: HttpTransport) = ModelSessionAi(ProviderRouter({ Groq.forRoute(it, { "k" }, t) }, usage, { LocalDate.of(2026, 10, 14) }))

    @Test
    fun recallIsParsedAndCostIsCounted() = runBlocking {
        val t = FakeTransport(groqOk("""{"ideas":[{"id":"i1","status":"covered","evidence":"a treia","note":""},{"id":"i2","status":"missed","evidence":"","note":""}],"praise":"Bravo!"}""", 1000, 300))
        val r = ai(t).assessRecall(pack, "pamantul e a treia planeta")
        assertTrue(r is AiReply.Ok)
        r as AiReply.Ok
        assertEquals(CoverageStatus.COVERED, r.value.ideas[0].status)
        assertEquals(1300, r.tokens)
        assertTrue(t.sent.single().body.contains("pamantul e a treia planeta"))
        assertEquals("assess_recall", usage.records.single().callType)
        assertEquals("openai/gpt-oss-120b", usage.records.single().model)
    }

    @Test
    fun answerPromptCarriesTheIdeaQuestionAndPurpose() = runBlocking {
        val t = FakeTransport(groqOk("""{"status":"covered","reply":"Exact, Luna!"}"""))
        val r = ai(t).assessAnswer(pack, pack.ideas[1], "Ce satelit are Pământul?", "luna", Purpose.TEST)
        assertEquals(CoverageStatus.COVERED, (r as AiReply.Ok).value.status)
        val body = t.sent.single().body
        assertTrue(body.contains("Ce satelit are Pământul?"))
        assertTrue(body.contains("întrebarea de test"))
        assertTrue(body.contains("Lecția: Pământul"))
    }

    @Test
    fun rateLimitBecomesAPauseAndIsRecorded() = runBlocking {
        val r = ai(FakeTransport(groq429)).assessRecall(pack, "x")
        assertEquals(AiReply.Unavailable(PauseReason.RATE_LIMITED, 7, 1, 0), r)
        assertTrue(usage.records.single().rateLimited)
    }

    @Test
    fun offlineAndTimeoutBecomePauses() = runBlocking {
        fun failing(kind: TransportException.Kind) = object : HttpTransport by FakeTransport() {
            override suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): HttpResponse =
                throw TransportException(kind, kind.reason)
        }
        assertEquals(PauseReason.OFFLINE, (ai(failing(TransportException.Kind.NETWORK)).assessRecall(pack, "x") as AiReply.Unavailable).reason)
        assertEquals(PauseReason.FAILED, (ai(failing(TransportException.Kind.TIMEOUT)).assessRecall(pack, "x") as AiReply.Unavailable).reason)
    }

    @Test
    fun aSecondConfiguredProviderIsTriedOnARateLimit() = runBlocking {
        try {
            CoreConfig.set("assess_answer", "groq/model-a, groq/model-b")
            val t = FakeTransport(groq429, groqOk("""{"status":"partial","reply":"Aproape!"}"""))
            val r = ai(t).assessAnswer(pack, pack.ideas[0], "q", "a", Purpose.HINT)
            assertEquals(CoverageStatus.PARTIAL, (r as AiReply.Ok).value.status)
            assertTrue(t.sent[0].body.contains("\"model\":\"model-a\""))
            assertTrue(t.sent[1].body.contains("\"model\":\"model-b\""))
            assertEquals(listOf(true, false), usage.records.map { it.rateLimited })
        } finally {
            CoreConfig.clearOverrides()
        }
    }

    @Test
    fun sessionCallsUseTheShortTimeoutAndEnrichmentTheLongOne() {
        assertEquals(20_000L, CoreConfig.timeoutMs(CoreConfig.ASSESS_ANSWER))
        assertEquals(60_000L, CoreConfig.timeoutMs(CoreConfig.ENRICH_IDEAS))
    }

    @Test
    fun summaryResultsNeverContainTheChildsWords() = runBlocking {
        val t = FakeTransport(groqOk("""{"childSummary":"Bravo!","parentNote":"Recapitulați Luna."}"""))
        val state = SessionEngine(pack).start().state.copy(retelling = "eu Vali am spus ceva")
        val r = ai(t).summarize(pack, state)
        assertTrue(r is AiReply.Ok)
        assertFalse(t.sent.single().body.contains("Vali"))
    }
}
