package com.capytutor.ai

import com.capytutor.core.RomanianText
import com.capytutor.core.config.CoreConfig
import com.capytutor.core.lesson.Lesson
import com.capytutor.core.prompts.Prompts
import com.capytutor.core.quiz.Quiz
import com.capytutor.core.quiz.QuizSchema

sealed interface QuizResult {
    data class Ok(val quiz: Quiz) : QuizResult

    /** [message] is plain Romanian, for the parent; same meaning as [EnrichResult.Failed]. */
    data class Failed(val message: String, val retryable: Boolean, val retryAfterSeconds: Int? = null) : QuizResult
}

/**
 * make_quiz (E11): one call, made once per lesson when no quiz was prepared in advance. The reply
 * is validated with [QuizSchema] (retried once on a bad reply); quizzes are graded on the phone.
 */
class QuizMaker(private val router: ProviderRouter) {

    suspend fun make(lesson: Lesson): QuizResult {
        val ids = lesson.ideas.indices.map { lesson.ideaId(it) }
        val user = Prompts.render(
            Prompts.MAKE_QUIZ,
            mapOf(
                "title" to lesson.title,
                "text" to lesson.text,
                "ideas" to lesson.ideas.mapIndexed { i, t -> "${lesson.ideaId(i)}: $t" }.joinToString("\n"),
            ),
        )
        return when (val r = router.callJson(CoreConfig.MAKE_QUIZ, user) { QuizSchema.parse(it, lesson.sha256, ids) }) {
            is JsonCall.Ok -> QuizResult.Ok(r.value)
            is JsonCall.RateLimited -> QuizResult.Failed(
                "Groq a primit prea multe cereri. Încearcă din nou peste ${RomanianText.count(r.retryAfterSeconds ?: 60, "secundă", "secunde")}.",
                retryable = true,
                retryAfterSeconds = r.retryAfterSeconds ?: 60,
            )
            is JsonCall.Failed -> QuizResult.Failed(
                when {
                    r.reason == "offline" -> "Nu e internet. Quiz-ul se pregătește mai târziu."
                    r.reason == "timeout" -> "Groq nu a răspuns la timp. Încearcă din nou."
                    r.reason.startsWith("unauthorized") -> "Cheia Groq nu e bună. Verific-o în Setări."
                    else -> "Nu am putut pregăti quiz-ul (${r.reason.take(80)}). Încearcă din nou."
                },
                retryable = r.retryable,
            )
        }
    }
}
