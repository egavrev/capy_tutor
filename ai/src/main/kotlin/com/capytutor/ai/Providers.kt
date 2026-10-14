package com.capytutor.ai

import java.io.File

/*
 * All AI sits behind these three interfaces (HANDOFF.md §4.1). Implementations
 * (Groq chat, Groq Whisper, Android speech and voice) arrive in milestones M2 and M4.
 */

interface LlmProvider {
    /** For example "groq/openai/gpt-oss-120b". */
    val id: String

    suspend fun completeJson(
        system: String,
        user: String,
        schemaName: String,
        maxTokens: Int = 800,
        temperature: Double = 0.2,
    ): LlmResult
}

sealed interface LlmResult {
    data class Ok(val json: String, val inputTokens: Int, val outputTokens: Int) : LlmResult
    data class RateLimited(val retryAfterSeconds: Int?) : LlmResult
    data class Failed(val reason: String, val retryable: Boolean) : LlmResult
}

interface SpeechToText {
    val id: String

    /** Batch transcription of one recording (Groq Whisper). */
    suspend fun transcribe(audio: File, languageTag: String = "ro"): SttResult
}

sealed interface SttResult {
    data class Ok(val text: String) : SttResult
    data class RateLimited(val retryAfterSeconds: Int?) : SttResult
    data class Failed(val reason: String) : SttResult
}

interface TextToSpeech {
    val id: String
    fun isAvailable(): Boolean
    suspend fun speak(text: String, languageTag: String = "ro-RO")
    fun stop()
}
