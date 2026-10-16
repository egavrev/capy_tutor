package com.capytutor.data.repo

import com.capytutor.core.progress.Evidence
import com.capytutor.core.progress.IdeaStatus
import com.capytutor.core.progress.Mark
import com.capytutor.core.progress.Progress
import com.capytutor.core.quiz.QuizStateCodec
import com.capytutor.data.db.CapyDatabase

/** One idea of a lesson over time. */
data class IdeaProgress(val ideaKey: String, val order: Int, val text: String, val status: IdeaStatus, val history: List<Evidence>) {
    val lastAt: Long? get() = history.maxOfOrNull { it.at }
}

/** One thing the child did, for the history list. */
data class Activity(
    val at: Long,
    val lessonId: Long,
    val lessonTitle: String,
    val kind: Kind,
    val correct: Int,
    val total: Int,
    /** For sessions: opens the detailed history. */
    val sessionId: Long? = null,
) {
    enum class Kind { SESSION, QUIZ }
}

data class LessonProgress(
    val lessonId: Long,
    val title: String,
    val subject: String,
    val ideas: List<IdeaProgress>,
    val activities: List<Activity>,
) {
    val known: Int get() = ideas.count { it.status == IdeaStatus.KNOWN }
    val weak: Int get() = ideas.count { it.status == IdeaStatus.WEAK }
    val lastAt: Long? get() = activities.maxOfOrNull { it.at }
}

data class ProgressOverview(val lessons: List<LessonProgress>, val activities: List<Activity>) {
    val sessions: Int get() = activities.count { it.kind == Activity.Kind.SESSION }
    val quizzes: Int get() = activities.count { it.kind == Activity.Kind.QUIZ }
}

/**
 * E15 · What the child remembers over time, built from what is already saved: the per-idea
 * results of every finished session and the answers of every finished quiz.
 */
class ProgressRepository(private val db: CapyDatabase, private val quizzes: QuizRepository) {

    suspend fun overview(): ProgressOverview {
        val lessons = db.lessons().all().associateBy { it.id }
        val sessions = db.sessions().finished().filter { it.lessonId in lessons }
        val results = sessions.map { it.id }.chunked(500).flatMap { db.sessions().resultsFor(it) }.groupBy { it.sessionId }
        val attempts = db.quizzes().finishedAttempts().filter { it.lessonId in lessons }

        val marks = mutableMapOf<Pair<Long, String>, MutableList<Evidence>>()
        val activities = mutableListOf<Activity>()

        for (s in sessions) {
            val at = s.endedAt ?: s.startedAt
            val title = lessons.getValue(s.lessonId).title
            activities += Activity(at, s.lessonId, title, Activity.Kind.SESSION, s.score, s.total, s.id)
            for (r in results[s.id].orEmpty()) {
                val mark = Progress.sessionMark(r.outcome, r.checkResult, r.parentDecision) ?: continue
                marks.getOrPut(s.lessonId to r.ideaKey) { mutableListOf() } += Evidence(at, mark)
            }
        }

        // A quiz attempt marks the ideas of each question, when it belongs to the lesson's current quiz.
        val quizCache = mutableMapOf<Long, com.capytutor.core.quiz.Quiz?>()
        for (a in attempts) {
            val at = a.finishedAt ?: a.startedAt
            activities += Activity(at, a.lessonId, lessons.getValue(a.lessonId).title, Activity.Kind.QUIZ, a.correct, a.total)
            val quiz = quizCache.getOrPut(a.lessonId) { quizzes.quiz(a.lessonId) } ?: continue
            val state = runCatching { QuizStateCodec.decode(a.state) }.getOrNull() ?: continue
            if (state.lessonSha256 != quiz.lessonSha256 || state.total != quiz.questions.size) continue
            quiz.questions.forEachIndexed { i, q ->
                val right = state.results[i] ?: return@forEachIndexed
                for (idea in q.ideaIds) {
                    marks.getOrPut(a.lessonId to idea) { mutableListOf() } += Evidence(at, if (right) Mark.QUIZ_RIGHT else Mark.QUIZ_NOT_YET)
                }
            }
        }

        val worked = activities.map { it.lessonId }.toSet()
        val perLesson = worked.mapNotNull { id ->
            val lesson = lessons[id] ?: return@mapNotNull null
            val ideas = db.lessons().ideas(id).map { idea ->
                val history = marks[id to idea.ideaKey].orEmpty().sortedBy { it.at }
                IdeaProgress(idea.ideaKey, idea.ord, idea.text, Progress.status(history), history)
            }
            LessonProgress(id, lesson.title, lesson.subject, ideas, activities.filter { it.lessonId == id }.sortedByDescending { it.at })
        }.sortedByDescending { it.lastAt }

        return ProgressOverview(perLesson, activities.sortedByDescending { it.at })
    }

    suspend fun lesson(lessonId: Long): LessonProgress? = overview().lessons.firstOrNull { it.lessonId == lessonId }
}
