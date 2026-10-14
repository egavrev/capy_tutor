package com.capytutor.ai

import com.capytutor.core.config.CoreConfig
import com.capytutor.core.lesson.Lesson
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.prompts.Prompts
import com.capytutor.core.schema.Schemas

sealed interface EnrichResult {
    data class Ok(val pack: LessonPack, val tokens: Int) : EnrichResult

    /** [message] is plain Romanian, for the parent. */
    data class Failed(val message: String, val retryable: Boolean) : EnrichResult
}

/**
 * enrich_ideas at lesson import (HANDOFF.md §3.1): one call that adds keywords, a broad question,
 * a clue, an explanation and a check question to each of the parent's key ideas.
 */
class LessonEnricher(private val router: ProviderRouter) {

    suspend fun enrich(lesson: Lesson): EnrichResult {
        val ids = lesson.ideas.indices.map { lesson.ideaId(it) }
        val user = Prompts.render(
            Prompts.ENRICH_IDEAS,
            mapOf(
                "title" to lesson.title,
                "text" to lesson.text,
                "ideas" to lesson.ideas.mapIndexed { i, t -> "${lesson.ideaId(i)}: $t" }.joinToString("\n"),
            ),
        )
        return when (val r = router.callJson(CoreConfig.ENRICH_IDEAS, user) { Schemas.parseEnrich(it, ids) }) {
            is JsonCall.Ok -> EnrichResult.Ok(LessonPack.build(lesson, r.value), r.inputTokens + r.outputTokens)
            is JsonCall.RateLimited -> EnrichResult.Failed(
                "Groq a primit prea multe cereri. Încearcă din nou peste ${r.retryAfterSeconds ?: 60} de secunde.",
                retryable = true,
            )
            is JsonCall.Failed -> EnrichResult.Failed(
                when {
                    r.reason == "offline" -> "Nu e internet. Lecția e salvată; generează întrebările mai târziu."
                    r.reason == "timeout" -> "Groq nu a răspuns la timp. Încearcă din nou."
                    r.reason.startsWith("unauthorized") -> "Cheia Groq nu e bună. Verific-o în Setări."
                    else -> "Nu am putut pregăti întrebările (${r.reason.take(80)}). Încearcă din nou."
                },
                retryable = r.retryable,
            )
        }
    }
}
