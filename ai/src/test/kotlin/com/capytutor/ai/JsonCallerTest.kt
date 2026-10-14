package com.capytutor.ai

import com.capytutor.ai.groq.Groq
import com.capytutor.core.schema.Schemas
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonCallerTest {

    private val ids = listOf("i1", "i2")
    private val valid = """{"ideas":[{"id":"i1","status":"covered","evidence":"africa","note":""},{"id":"i2","status":"missed","evidence":"","note":""}],"praise":"Bravo pentru Africa!"}"""
    private val missingIdea = """{"ideas":[{"id":"i1","status":"covered"}],"praise":"Bravo!"}"""

    private fun caller(t: FakeTransport) = JsonCaller(Groq.chat("openai/gpt-oss-120b", { "k" }, t))

    private suspend fun JsonCaller.recall() =
        call("sys", "user", "assess_recall", 1500, 0.2) { Schemas.parseRecall(it, ids) }

    @Test
    fun validReplyOnFirstAttempt() = runBlocking {
        val r = caller(FakeTransport(groqOk(valid))).recall()
        assertTrue(r is JsonCall.Ok)
        assertEquals(1, r.attempts)
        assertEquals(900, r.inputTokens)
    }

    @Test
    fun invalidReplyIsRetriedOnceWithAReminder() = runBlocking {
        val t = FakeTransport(groqOk(missingIdea), groqOk(valid, 950, 310))
        val r = caller(t).recall()
        assertTrue(r is JsonCall.Ok)
        assertEquals(2, r.attempts)
        assertEquals(1850, r.inputTokens)
        assertEquals(610, r.outputTokens)
        assertTrue(t.sent[1].body.contains("missing ideas: i2"))
        assertEquals("missing ideas: i2", (r as JsonCall.Ok).retryReason)
    }

    @Test
    fun groqJsonValidationErrorIsRetriedToo() = runBlocking {
        val r = caller(FakeTransport(groqJsonFailed, groqOk(valid))).recall()
        assertTrue(r is JsonCall.Ok && r.attempts == 2)
    }

    @Test
    fun twoInvalidRepliesFail() = runBlocking {
        val r = caller(FakeTransport(groqOk("nu e json"), groqOk(missingIdea))).recall()
        assertTrue(r is JsonCall.Failed && r.attempts == 2)
    }

    @Test
    fun rateLimitIsReturnedWithoutRetry() = runBlocking {
        val t = FakeTransport(groq429)
        val r = caller(t).recall()
        assertEquals(JsonCall.RateLimited(7, 1, 0, 0), r)
        assertEquals(1, t.sent.size)
    }
}
