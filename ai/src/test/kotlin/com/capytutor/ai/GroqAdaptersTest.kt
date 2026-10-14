package com.capytutor.ai

import com.capytutor.ai.groq.ChatResponses
import com.capytutor.ai.groq.Groq
import com.capytutor.ai.groq.GroqWhisperStt
import com.capytutor.ai.http.HttpResponse
import com.capytutor.ai.http.HttpTransport
import com.capytutor.ai.http.TransportException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GroqAdaptersTest {

    private val key = "gsk_test_secret_1234"

    @Test
    fun chatRequestHasJsonModeReasoningAndKeyOnlyInHeader() = runBlocking {
        val t = FakeTransport(groqOk("""{"ok":true}"""))
        val llm = Groq.chat("openai/gpt-oss-120b", { key }, t)
        val r = llm.completeJson("sys", "user", "test", maxTokens = 500, temperature = 0.2)
        assertEquals(LlmResult.Ok("""{"ok":true}""", 900, 300), r)

        val sent = t.sent.single()
        assertEquals("https://api.groq.com/openai/v1/chat/completions", sent.url)
        assertEquals("Bearer $key", sent.headers["Authorization"])
        assertFalse("key must not be in the body", sent.body.contains(key))
        assertTrue(sent.body.contains("\"response_format\":{\"type\":\"json_object\"}"))
        assertTrue(sent.body.contains("\"reasoning_effort\":\"low\""))
        assertTrue(sent.body.contains("\"max_completion_tokens\":500"))
        assertTrue(sent.body.contains("\"model\":\"openai/gpt-oss-120b\""))
        assertFalse(llm.toString().contains(key))
    }

    @Test
    fun otherModelsGetNoReasoningParams() = runBlocking {
        val t = FakeTransport(groqOk("{}"))
        Groq.chat("llama-x", { key }, t).completeJson("s", "u", "x", 100, 0.0)
        assertFalse(t.sent.single().body.contains("reasoning_effort"))
    }

    @Test
    fun rateLimitReadsRetryAfter() {
        assertEquals(LlmResult.RateLimited(7), ChatResponses.toLlmResult(groq429))
        val noHeader = groq429.copy(headers = emptyMap())
        assertEquals(LlmResult.RateLimited(7), ChatResponses.toLlmResult(noHeader)) // 6.75s from the message, rounded up
        val minutes = HttpResponse(429, emptyMap(), """{"error":{"message":"Please try again in 1m2.5s."}}""")
        assertEquals(LlmResult.RateLimited(63), ChatResponses.toLlmResult(minutes))
    }

    @Test
    fun errorsAreClassified() {
        val json = ChatResponses.toLlmResult(groqJsonFailed)
        assertTrue(json is LlmResult.Failed && json.reason.startsWith(ChatResponses.INVALID_JSON))
        val auth = ChatResponses.toLlmResult(HttpResponse(401, emptyMap(), """{"error":{"message":"Invalid API Key"}}"""))
        assertTrue(auth is LlmResult.Failed && !auth.retryable && auth.reason.contains("unauthorized"))
        val server = ChatResponses.toLlmResult(HttpResponse(503, emptyMap(), "upstream"))
        assertTrue(server is LlmResult.Failed && server.retryable)
        val empty = ChatResponses.toLlmResult(groqOk(""))
        assertTrue(empty is LlmResult.Failed && empty.reason.startsWith(ChatResponses.INVALID_JSON))
    }

    @Test
    fun timeoutBecomesRetryableFailure() = runBlocking {
        val t = object : HttpTransport by FakeTransport() {
            override suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): HttpResponse =
                throw TransportException(TransportException.Kind.TIMEOUT, "timeout")
        }
        val r = Groq.chat("openai/gpt-oss-120b", { key }, t).completeJson("s", "u", "x", 100, 0.2)
        assertEquals(LlmResult.Failed("timeout", retryable = true), r)
    }

    @Test
    fun whisperSendsRomanianJsonRequest() = runBlocking {
        val t = FakeTransport(HttpResponse(200, emptyMap(), """{"text":" primii oameni au trăit în Africa ","x_groq":{"id":"req_1"}}"""))
        val stt = GroqWhisperStt("whisper-large-v3-turbo", { key }, t)
        val r = stt.transcribe(File("rec.m4a"), "ro-RO", "Apariția omului: Lucy, Etiopia")
        assertEquals(SttResult.Ok("primii oameni au trăit în Africa"), r)
        val fields = t.sent.single().fields
        assertEquals("ro", fields["language"])
        assertEquals("whisper-large-v3-turbo", fields["model"])
        assertEquals("0", fields["temperature"])
        assertEquals("Apariția omului: Lucy, Etiopia", fields["prompt"])
        assertTrue(t.sent.single().url.endsWith("/audio/transcriptions"))
    }

    @Test
    fun whisperRateLimit() = runBlocking {
        val r = GroqWhisperStt("whisper-large-v3-turbo", { key }, FakeTransport(groq429)).transcribe(File("a.m4a"), "ro")
        assertEquals(SttResult.RateLimited(7), r)
    }
}
