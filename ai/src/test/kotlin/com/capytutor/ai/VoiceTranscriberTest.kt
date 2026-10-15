package com.capytutor.ai

import com.capytutor.ai.groq.GroqWhisperStt
import com.capytutor.ai.http.HttpResponse
import com.capytutor.ai.http.HttpTransport
import com.capytutor.ai.http.TransportException
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.pack.PackIdea
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceTranscriberTest {
    private val pack = LessonPack(
        "Apariția omului",
        "sha",
        listOf(PackIdea("i1", 0, "Lucy a fost găsită în Etiopia.", listOf(listOf("Lucy"), listOf("Etiopia", "etiopiană")), "", "", "", "", "")),
    )

    private fun audio(): File = File.createTempFile("rec", ".m4a").apply { writeBytes(ByteArray(64) { 1 }) }

    private fun whisper(t: HttpTransport) = GroqWhisperStt("whisper-large-v3-turbo", { "test-key" }, t)

    private class Throwing(private val kind: TransportException.Kind) : HttpTransport {
        override suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Long) = error("unused")
        override suspend fun postMultipart(url: String, headers: Map<String, String>, fields: Map<String, String>, fileField: String, file: File, fileMediaType: String, timeoutMs: Long): HttpResponse =
            throw TransportException(kind, "test")
    }

    @Test
    fun textComesBackAndTheAudioIsDeleted() = runBlocking {
        val f = audio()
        val usage = InMemoryUsage()
        val t = FakeTransport(HttpResponse(200, emptyMap(), """{"text":" Lucy a fost găsită în Etiopia. "}"""))
        val r = VoiceTranscriber(whisper(t), usage).transcribe(f, 7, pack)
        assertEquals(Transcription.Text("Lucy a fost găsită în Etiopia."), r)
        assertFalse(f.exists())
        assertEquals(7, usage.records.single().audioSeconds)
        assertEquals("transcribe", usage.records.single().callType)
    }

    @Test
    fun theWhisperPromptHasTheTitleAndTermsButNoName() = runBlocking {
        val t = FakeTransport(HttpResponse(200, emptyMap(), """{"text":"ceva"}"""))
        VoiceTranscriber(whisper(t)).transcribe(audio(), 3, pack)
        val prompt = t.sent.single().fields.getValue("prompt")
        assertEquals("Apariția omului, Lucy, Etiopia", prompt)
        assertEquals("ro", t.sent.single().fields["language"])
    }

    @Test
    fun rateLimitMeansFallbackAndStillDeletes() = runBlocking {
        val f = audio()
        val t = FakeTransport(HttpResponse(429, mapOf("retry-after" to "12"), "{}"))
        val r = VoiceTranscriber(whisper(t)).transcribe(f, 5, pack)
        assertEquals(Transcription.UseFallback(Transcription.Reason.RATE_LIMITED, 12), r)
        assertFalse(f.exists())
    }

    @Test
    fun offlineMeansFallbackAndStillDeletes() = runBlocking {
        val f = audio()
        val r = VoiceTranscriber(whisper(Throwing(TransportException.Kind.NETWORK))).transcribe(f, 5, pack)
        assertEquals(Transcription.UseFallback(Transcription.Reason.OFFLINE, null), r)
        assertFalse(f.exists())
    }

    @Test
    fun badKeyIsAGentleFailureAndStillDeletes() = runBlocking {
        val f = audio()
        val r = VoiceTranscriber(whisper(FakeTransport(HttpResponse(401, emptyMap(), "{}")))).transcribe(f, 5, pack)
        assertTrue(r is Transcription.Failed)
        assertFalse((r as Transcription.Failed).message.contains("Greșit"))
        assertFalse(f.exists())
    }

    @Test
    fun silenceHallucinationsAreEmpty() = runBlocking {
        val f = audio()
        val t = FakeTransport(HttpResponse(200, emptyMap(), """{"text":"Mulțumesc pentru vizionare!"}"""))
        assertEquals(Transcription.Empty, VoiceTranscriber(whisper(t)).transcribe(f, 2, pack))
        assertFalse(f.exists())
    }

    @Test
    fun emptyFileIsNotSentAndIsDeleted() = runBlocking {
        val f = File.createTempFile("rec", ".m4a")
        val t = FakeTransport()
        assertEquals(Transcription.Empty, VoiceTranscriber(whisper(t)).transcribe(f, 0, pack))
        assertTrue(t.sent.isEmpty())
        assertFalse(f.exists())
    }
}
