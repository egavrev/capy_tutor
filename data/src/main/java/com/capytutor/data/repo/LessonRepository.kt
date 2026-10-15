package com.capytutor.data.repo

import com.capytutor.ai.EnrichResult
import com.capytutor.ai.LessonEnricher
import com.capytutor.core.lesson.Lesson
import com.capytutor.core.lesson.LessonParser
import com.capytutor.core.lesson.ParseResult
import com.capytutor.core.lesson.Subject
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.pack.PackIdea
import com.capytutor.core.schema.Schemas
import com.capytutor.core.schema.Validated
import com.capytutor.data.db.CapyDatabase
import com.capytutor.data.db.KeyIdeaEntity
import com.capytutor.data.db.LessonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** One step of preparing questions for many lessons, paced by Groq's limits. */
sealed interface Prepare {
    data object Done : Prepare

    /** Groq's per-minute limit: try again after [seconds]. */
    data class Wait(val seconds: Int) : Prepare

    /** Daily limit, no key, no internet or another problem: stop the queue. [message] is Romanian. */
    data class Stop(val message: String) : Prepare
}

sealed interface ImportResult {
    /**
     * Imported; [enrichError] set when the questions could not be generated yet. [questionsAdded]:
     * the lesson was already in the app without questions, and only its prepared questions were added.
     */
    data class Ok(val lessonId: Long, val enrichError: String?, val questionsAdded: Boolean = false) : ImportResult

    /** Plain Romanian, shown to the parent as is. */
    data class Rejected(val message: String) : ImportResult
}

/**
 * Lessons: import a Markdown file (HANDOFF.md §6), generate hints and questions once, let the
 * parent review and edit them, then mark the lesson ready. Keeps the original file and its hash.
 */
class LessonRepository(
    private val db: CapyDatabase,
    private val filesDir: File,
    private val enricher: LessonEnricher,
    private val hasGroqKey: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lessons = db.lessons()

    fun observeLessons(): Flow<List<LessonEntity>> = lessons.observeAll()
    fun observeLesson(id: Long): Flow<LessonEntity?> = lessons.observe(id)
    fun observeIdeas(lessonId: Long): Flow<List<KeyIdeaEntity>> = lessons.observeIdeas(lessonId)
    suspend fun lesson(id: Long): LessonEntity? = lessons.get(id)

    /**
     * [prepared]: hints and questions generated earlier (the debug demo uses the test bench's
     * packs), so no model call is made.
     */
    suspend fun import(markdown: String, prepared: LessonPack? = null, enrich: Boolean = true): ImportResult {
        val lesson = when (val r = LessonParser.parse(markdown)) {
            is ParseResult.Ok -> r.lesson
            is ParseResult.Error -> return ImportResult.Rejected(r.message)
        }
        lessons.byHash(lesson.sha256)?.let {
            return ImportResult.Rejected("Lecția „${it.title}” există deja.")
        }
        val dir = File(filesDir, "lessons").apply { mkdirs() }
        val file = File(dir, "${lesson.sha256}.md").apply { writeText(markdown) }
        val id = lessons.insert(
            LessonEntity(
                subject = lesson.subject.label,
                title = lesson.title,
                grade = lesson.grade,
                chapter = lesson.chapter,
                mdPath = file.path,
                mdHash = lesson.sha256,
                status = LessonEntity.DRAFT,
                enriched = false,
                createdAt = clock(),
            ),
        )
        // The parent's ideas are stored at once, so nothing is lost if generating fails.
        lessons.insertIdeas(
            lesson.ideas.mapIndexed { i, text ->
                KeyIdeaEntity(lessonId = id, ideaKey = lesson.ideaId(i), ord = i, text = text, keywords = "[]", broadQuestion = "", clue = "", explanation = "", checkQuestion = "", checkAnswer = "")
            },
        )
        if (prepared != null) {
            val ids = lesson.ideas.indices.map { lesson.ideaId(it) }.toSet()
            val ideas = prepared.ideas.filter { it.id in ids }
            if (ideas.size == ids.size) {
                lessons.replaceIdeas(id, ideas.map { it.toEntity(id) })
                lessons.get(id)?.let { lessons.update(it.copy(enriched = true)) }
                return ImportResult.Ok(id, null)
            }
        }
        if (!enrich) return ImportResult.Ok(id, null)
        return ImportResult.Ok(id, enrich(id, lesson))
    }

    /**
     * A lesson with its hints and questions prepared in advance (a `<lesson>.questions.json` next
     * to the `.md`, in the same format Groq returns). Checked with the same rules as a Groq reply;
     * when it does not match the lesson, the lesson is still saved and the problem reported.
     */
    suspend fun importWithQuestions(markdown: String, questionsJson: String): ImportResult {
        val lesson = (LessonParser.parse(markdown) as? ParseResult.Ok)?.lesson ?: return import(markdown, enrich = false)
        val ids = lesson.ideas.indices.map { lesson.ideaId(it) }
        return when (val v = Schemas.parseEnrich(questionsJson, ids)) {
            is Validated.Ok -> {
                val pack = LessonPack.build(lesson, v.value)
                val existing = lessons.byHash(lesson.sha256)
                if (existing != null && !existing.enriched) {
                    // Imported earlier without questions (and so never shown to the child): add them now.
                    lessons.replaceIdeas(existing.id, pack.ideas.map { it.toEntity(existing.id) })
                    lessons.update(existing.copy(enriched = true))
                    ImportResult.Ok(existing.id, null, questionsAdded = true)
                } else {
                    import(markdown, pack)
                }
            }
            is Validated.Invalid -> when (val r = import(markdown, enrich = false)) {
                is ImportResult.Ok -> r.copy(enrichError = "Întrebările pregătite nu se potrivesc cu lecția (${v.reason.take(120)}).")
                is ImportResult.Rejected -> r
            }
        }
    }

    suspend fun lesson(id: Long): LessonEntity? = lessons.get(id)

    /** The lesson in the app made from this Markdown file (same content), if any. */
    suspend fun idForMarkdown(markdown: String): Long? =
        (LessonParser.parse(markdown) as? ParseResult.Ok)?.lesson?.let { lessons.byHash(it.sha256)?.id }

    /** Lessons whose hints and questions are not generated yet, oldest first. */
    suspend fun withoutQuestions(): List<LessonEntity> = lessons.all().filter { !it.enriched }

    /**
     * Generates the questions for one lesson, for the paced queue in parent mode: a short Groq
     * limit means wait and try again, a long one (the daily limit) means stop for today.
     */
    suspend fun prepareQuestions(lessonId: Long): Prepare {
        if (!hasGroqKey()) return Prepare.Stop("Adaugă cheia Groq în Setări.")
        val entity = lessons.get(lessonId) ?: return Prepare.Done
        val lesson = (LessonParser.parse(File(entity.mdPath).readText()) as? ParseResult.Ok)?.lesson
            ?: return Prepare.Stop("Fișierul lecției „${entity.title}” nu mai poate fi citit.")
        return when (val r = runEnricher(lesson)) {
            is EnrichResult.Ok -> {
                save(lessonId, r)
                Prepare.Done
            }
            is EnrichResult.Failed -> {
                val wait = r.retryAfterSeconds
                when {
                    wait != null && wait <= MAX_WAIT_SECONDS -> Prepare.Wait(wait)
                    wait != null -> Prepare.Stop("Groq a ajuns la limita gratuită de azi. Continuă mâine; lecțiile pregătite până acum sunt salvate.")
                    else -> Prepare.Stop(r.message)
                }
            }
        }
    }

    /** Generates (again) the hints and questions. Returns an error message, or null when done. */
    suspend fun regenerate(lessonId: Long): String? {
        val entity = lessons.get(lessonId) ?: return "Lecția nu mai există."
        val lesson = (LessonParser.parse(File(entity.mdPath).readText()) as? ParseResult.Ok)?.lesson
            ?: return "Fișierul lecției nu mai poate fi citit."
        return enrich(lessonId, lesson)
    }

    private suspend fun enrich(lessonId: Long, lesson: Lesson): String? {
        if (!hasGroqKey()) return "Adaugă cheia Groq în Setări, apoi apasă „Generează întrebările”."
        return when (val r = runEnricher(lesson)) {
            is EnrichResult.Failed -> r.message
            is EnrichResult.Ok -> {
                save(lessonId, r)
                null
            }
        }
    }

    private suspend fun runEnricher(lesson: Lesson): EnrichResult = try {
        enricher.enrich(lesson)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: VirtualMachineError) {
        throw e
    } catch (e: Throwable) {
        // Never crash parent mode: the lesson is saved, the parent can try again.
        EnrichResult.Failed("Nu am putut pregăti întrebările (${e::class.simpleName}). Lecția e salvată; încearcă din nou.", retryable = true)
    }

    private suspend fun save(lessonId: Long, r: EnrichResult.Ok) {
        lessons.replaceIdeas(lessonId, r.pack.ideas.map { it.toEntity(lessonId) })
        lessons.get(lessonId)?.let { lessons.update(it.copy(enriched = true)) }
    }

    suspend fun updateIdea(idea: KeyIdeaEntity) = lessons.updateIdea(idea)

    suspend fun setReady(lessonId: Long, ready: Boolean) {
        lessons.get(lessonId)?.let { lessons.update(it.copy(status = if (ready) LessonEntity.READY else LessonEntity.DRAFT)) }
    }

    suspend fun delete(lessonId: Long) {
        lessons.get(lessonId)?.let { File(it.mdPath).delete() }
        lessons.delete(lessonId)
    }

    /** The lesson as the session engine needs it. */
    suspend fun pack(lessonId: Long): LessonPack? {
        val l = lessons.get(lessonId) ?: return null
        val ideas = lessons.ideas(lessonId)
        return LessonPack(l.title, l.mdHash, ideas.map { it.toPack() })
    }

    companion object {
        /** Longer than this, Groq's "try again in" is the daily limit: stop instead of waiting. */
        const val MAX_WAIT_SECONDS = 120

        fun subjectOf(label: String): Subject? = Subject.fromLabel(label)

        fun keywordsToText(json: String): String = decodeKeywords(json).joinToString("\n") { it.joinToString(" / ") }

        fun keywordsFromText(text: String): String = encodeKeywords(
            text.lines().map { line -> line.split('/').map { it.trim() }.filter { it.isNotEmpty() } }.filter { it.isNotEmpty() },
        )

        fun encodeKeywords(groups: List<List<String>>): String =
            JsonArray(groups.map { g -> JsonArray(g.map(::JsonPrimitive)) }).toString()

        fun decodeKeywords(json: String): List<List<String>> = runCatching {
            Json.parseToJsonElement(json).jsonArray.map { g -> g.jsonArray.map { it.jsonPrimitive.content } }
        }.getOrDefault(emptyList())
    }
}

fun PackIdea.toEntity(lessonId: Long) = KeyIdeaEntity(
    lessonId = lessonId, ideaKey = id, ord = order, text = text,
    keywords = LessonRepository.encodeKeywords(keywords),
    broadQuestion = broadQuestion, clue = clue, explanation = explanation,
    checkQuestion = checkQuestion, checkAnswer = checkAnswer,
)

fun KeyIdeaEntity.toPack() = PackIdea(
    id = ideaKey, order = ord, text = text,
    keywords = LessonRepository.decodeKeywords(keywords),
    broadQuestion = broadQuestion, clue = clue, explanation = explanation,
    checkQuestion = checkQuestion, checkAnswer = checkAnswer,
)
