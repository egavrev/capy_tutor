package com.capytutor.ai

import com.capytutor.core.config.CoreConfig
import com.capytutor.core.pack.LessonPack
import java.io.File
import java.time.LocalDate

/** What happened to one recording. */
sealed interface Transcription {
    data class Text(val text: String) : Transcription

    /** Nothing was understood (silence, or a very short tap). */
    data object Empty : Transcription

    /**
     * Groq is rate limited or unreachable: use the phone's own recogniser for a while
     * (HANDOFF.md §4.4), or the keyboard when that is not available.
     */
    data class UseFallback(val reason: Reason, val retryAfterSeconds: Int?) : Transcription

    /** Something else went wrong (bad key, server error). [message] is Romanian, for the child's screen. */
    data class Failed(val message: String) : Transcription

    enum class Reason { RATE_LIMITED, OFFLINE }
}

/**
 * Sends one recording to Groq Whisper (decision 4) and **always deletes the audio file**,
 * whatever the outcome (HANDOFF.md §4.4, privacy). The Whisper prompt carries only the lesson
 * title and a few key terms, never the child's name.
 */
class VoiceTranscriber(
    private val stt: SpeechToText,
    private val usage: UsageSink = UsageSink { },
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun transcribe(audio: File, audioSeconds: Int, pack: LessonPack?): Transcription {
        try {
            if (!audio.isFile || audio.length() == 0L) return Transcription.Empty
            val prompt = pack?.let { whisperPrompt(it) }
            val result = if (stt is com.capytutor.ai.groq.GroqWhisperStt) stt.transcribe(audio, "ro", prompt) else stt.transcribe(audio, "ro")
            record(result, audioSeconds)
            return when (result) {
                is SttResult.Ok -> cleaned(result.text)?.let { Transcription.Text(it) } ?: Transcription.Empty
                is SttResult.RateLimited -> Transcription.UseFallback(Transcription.Reason.RATE_LIMITED, result.retryAfterSeconds)
                is SttResult.Failed -> when {
                    result.reason == "offline" || result.reason == "timeout" -> Transcription.UseFallback(Transcription.Reason.OFFLINE, null)
                    result.reason.startsWith("unauthorized") -> Transcription.Failed("Capy nu te poate asculta acum (cheia Groq). Scrie răspunsul.")
                    else -> Transcription.Failed("Capy nu a înțeles înregistrarea. Mai încearcă o dată sau scrie răspunsul.")
                }
            }
        } finally {
            audio.delete()
        }
    }

    private fun record(r: SttResult, seconds: Int) {
        val route = CoreConfig.route(CoreConfig.TRANSCRIBE)
        usage.record(
            UsageRecord(
                date = today(),
                provider = route.provider,
                model = route.model,
                callType = CoreConfig.TRANSCRIBE,
                requests = 1,
                inputTokens = 0,
                outputTokens = 0,
                rateLimited = r is SttResult.RateLimited,
                outcome = when (r) {
                    is SttResult.Ok -> "ok"
                    is SttResult.RateLimited -> "rate_limited"
                    is SttResult.Failed -> r.reason
                },
                audioSeconds = seconds,
            ),
        )
    }

    companion object {
        /** Whisper invents text on silence; these are its usual Romanian/English fillers. */
        private val hallucinations = setOf(
            "mulțumesc", "multumesc", "mulțumesc pentru vizionare", "mulțumesc pentru vizionare!",
            "subtitrare", "subtitrarea", "thank you", "thanks for watching", "you",
        )

        fun cleaned(text: String): String? {
            val t = text.trim().trim('"', '„', '”')
            if (t.isEmpty()) return null
            val folded = t.lowercase().trimEnd('.', '!', ' ')
            if (folded in hallucinations || hallucinations.any { folded == it.trimEnd('!') }) return null
            if (t.all { !it.isLetterOrDigit() }) return null
            return t
        }

        /** Lesson title plus the first word of a few keyword groups: helps with names and dates. */
        fun whisperPrompt(pack: LessonPack, maxTerms: Int = 12): String {
            val terms = pack.ideas.flatMap { i -> i.keywords.mapNotNull { it.firstOrNull() } }
                .filter { it.length > 2 }
                .distinct()
                .take(maxTerms)
            return (listOf(pack.title) + terms).joinToString(", ")
        }
    }
}
