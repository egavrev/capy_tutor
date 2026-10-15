package com.capytutor.data.repo

import com.capytutor.ai.QuizMaker
import com.capytutor.ai.QuizResult
import com.capytutor.core.lesson.LessonParser
import com.capytutor.core.lesson.ParseResult
import com.capytutor.core.quiz.Quiz
import com.capytutor.core.quiz.QuizSchema
import com.capytutor.core.quiz.QuizState
import com.capytutor.core.quiz.QuizStateCodec
import com.capytutor.core.schema.Validated
import com.capytutor.data.db.CapyDatabase
import com.capytutor.data.db.QuizAttemptEntity
import com.capytutor.data.db.QuizEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** A lesson's best finished quiz. */
data class QuizBest(val correct: Int, val total: Int)

/**
 * E11 · Quizzes: one per lesson, prepared in advance (`<lesson>.quiz.json`) or made once by Groq,
 * validated against the lesson's ideas, then played and graded on the phone. Attempts are saved
 * after every answer, so a quiz survives the app being killed.
 */
class QuizRepository(
    private val db: CapyDatabase,
    private val maker: QuizMaker,
    private val hasGroqKey: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val quizzes = db.quizzes()
    private val lessons = db.lessons()

    fun observeLessonIds(): Flow<List<Long>> = quizzes.observeLessonIds()
    fun observeRaw(lessonId: Long): Flow<QuizEntity?> = quizzes.observe(lessonId)

    /** Best finished attempt per lesson (highest share right, then the latest). */
    fun observeBest(): Flow<Map<Long, QuizBest>> = quizzes.observeFinished().map { rows ->
        rows.groupBy { it.lessonId }.mapValues { (_, list) ->
            val best = list.maxBy { if (it.total == 0) 0.0 else it.correct.toDouble() / it.total }
            QuizBest(best.correct, best.total)
        }
    }

    /** The lesson's quiz, checked against its current ideas; null when there is none (or it no longer fits). */
    suspend fun quiz(lessonId: Long): Quiz? {
        val row = quizzes.get(lessonId) ?: return null
        val lesson = lessons.get(lessonId) ?: return null
        val ids = lessons.ideas(lessonId).map { it.ideaKey }
        return (QuizSchema.parse(row.json, lesson.mdHash, ids) as? Validated.Ok)?.value
    }

    /** A prepared `.quiz.json` for a lesson already in the app. Returns a Romanian problem, or null when saved. */
    suspend fun importPrepared(lessonId: Long, json: String): String? {
        val lesson = lessons.get(lessonId) ?: return "Lecția nu mai există."
        val ids = lessons.ideas(lessonId).map { it.ideaKey }
        return when (val v = QuizSchema.parse(json, lesson.mdHash, ids)) {
            is Validated.Ok -> {
                save(lessonId, v.value, SOURCE_PREPARED)
                null
            }
            is Validated.Invalid -> "Quiz-ul pregătit nu se potrivește cu lecția (${v.reason.take(120)})."
        }
    }

    /** Ready lessons without a quiz, oldest first: the paced queue makes them after the questions. */
    suspend fun lessonsWithoutQuiz(): List<Long> {
        val have = quizzes.lessonIds().toSet()
        return lessons.all().filter { it.enriched && it.id !in have }.map { it.id }
    }

    /** Makes one lesson's quiz with Groq, for the paced queue (same rules as [LessonRepository.prepareQuestions]). */
    suspend fun prepare(lessonId: Long): Prepare {
        if (!hasGroqKey()) return Prepare.Stop("Adaugă cheia Groq în Setări.")
        val entity = lessons.get(lessonId) ?: return Prepare.Done
        val lesson = (LessonParser.parse(File(entity.mdPath).readText()) as? ParseResult.Ok)?.lesson
            ?: return Prepare.Stop("Fișierul lecției „${entity.title}” nu mai poate fi citit.")
        val r = try {
            maker.make(lesson)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: Throwable) {
            QuizResult.Failed("Nu am putut pregăti quiz-ul (${e::class.simpleName}). Încearcă din nou.", retryable = true)
        }
        return when (r) {
            is QuizResult.Ok -> {
                save(lessonId, r.quiz, SOURCE_GROQ)
                Prepare.Done
            }
            is QuizResult.Failed -> {
                val wait = r.retryAfterSeconds
                when {
                    wait != null && wait <= LessonRepository.MAX_WAIT_SECONDS -> Prepare.Wait(wait)
                    wait != null -> Prepare.Stop("Groq a ajuns la limita gratuită de azi. Continuă mâine; ce e gata rămâne salvat.")
                    else -> Prepare.Stop(r.message)
                }
            }
        }
    }

    /** Makes the quiz again with Groq (parent's "Refă quiz-ul"). Returns an error message, or null when done. */
    suspend fun regenerate(lessonId: Long): String? = when (val p = prepare(lessonId)) {
        Prepare.Done -> null
        is Prepare.Wait -> "Groq a primit prea multe cereri. Încearcă din nou peste ${p.seconds} de secunde."
        is Prepare.Stop -> p.message
    }

    private suspend fun save(lessonId: Long, quiz: Quiz, source: String) {
        quizzes.upsert(QuizEntity(lessonId, QuizSchema.encode(quiz), source, clock()))
        quizzes.dropOpenAttempts(lessonId)
    }

    // ---- attempts --------------------------------------------------------------------------------

    /** The unfinished attempt, if any, when it belongs to the current quiz. */
    suspend fun openAttempt(lessonId: Long, quiz: Quiz): Pair<Long, QuizState>? {
        val row = quizzes.openAttempt(lessonId) ?: return null
        val s = runCatching { QuizStateCodec.decode(row.state) }.getOrNull() ?: return null
        return if (s.lessonSha256 == quiz.lessonSha256 && s.total == quiz.questions.size) row.id to s else null
    }

    suspend fun startAttempt(lessonId: Long, s: QuizState): Long = quizzes.insertAttempt(
        QuizAttemptEntity(lessonId = lessonId, startedAt = clock(), finishedAt = null, state = QuizStateCodec.encode(s), correct = 0, total = s.total),
    )

    suspend fun saveAttempt(id: Long, s: QuizState) {
        val row = quizzes.attempt(id) ?: return
        quizzes.updateAttempt(
            row.copy(
                state = QuizStateCodec.encode(s),
                correct = s.correct,
                total = s.total,
                finishedAt = if (s.done) row.finishedAt ?: clock() else null,
            ),
        )
    }

    companion object {
        const val SOURCE_PREPARED = "prepared"
        const val SOURCE_GROQ = "groq"
    }
}
