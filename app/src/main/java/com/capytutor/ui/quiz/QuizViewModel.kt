package com.capytutor.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.core.quiz.Quiz
import com.capytutor.core.quiz.QuizAnswer
import com.capytutor.core.quiz.QuizEngine
import com.capytutor.core.quiz.QuizQuestion
import com.capytutor.core.quiz.QuizState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the child is building for the current question, before "Verifică". Not saved. */
data class Draft(
    val choice: Int? = null,
    val truth: Boolean? = null,
    /** Order: item indices in the order tapped. */
    val placed: List<Int> = emptyList(),
    /** Match: left index → right index. */
    val pairs: Map<Int, Int> = emptyMap(),
    val pickedLeft: Int? = null,
)

data class QuizUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val title: String = "",
    val quiz: Quiz? = null,
    val state: QuizState? = null,
    val draft: Draft = Draft(),
) {
    val question: QuizQuestion? get() = state?.takeIf { !it.done }?.let { quiz?.questions?.get(it.index) }

    /** "Verifică" is enabled once the answer is complete. */
    val canCheck: Boolean
        get() = when (val q = question) {
            is QuizQuestion.Choice -> draft.choice != null
            is QuizQuestion.TrueFalse -> draft.truth != null
            is QuizQuestion.Order -> draft.placed.size == q.items.size
            is QuizQuestion.Match -> draft.pairs.size == q.pairs.size
            null -> false
        }
}

/** E11: one quiz attempt, saved after every answer (resumes after the app is killed). */
class QuizViewModel(private val c: AppContainer, private val lessonId: Long) : ViewModel() {
    private val _ui = MutableStateFlow(QuizUi())
    val ui: StateFlow<QuizUi> = _ui
    private var attemptId: Long? = null

    /** Saves one step at a time, so a fast double tap never starts two attempts. */
    private val saving = kotlinx.coroutines.sync.Mutex()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val (title, quiz) = withContext(Dispatchers.IO) { c.lessons.lesson(lessonId)?.title to c.quizzes.quiz(lessonId) }
        if (quiz == null) {
            _ui.update { it.copy(loading = false, missing = true, title = title.orEmpty()) }
            return
        }
        val open = withContext(Dispatchers.IO) { c.quizzes.openAttempt(lessonId, quiz) }
        attemptId = open?.first
        _ui.update { QuizUi(loading = false, title = title.orEmpty(), quiz = quiz, state = open?.second ?: QuizEngine.start(quiz)) }
    }

    fun choose(option: Int) = edit { it.copy(choice = option) }

    fun judge(value: Boolean) = edit { it.copy(truth = value) }

    /** Order: tap to give the next number; tap a numbered item to take it out. */
    fun tapItem(item: Int) = edit { d ->
        d.copy(placed = if (item in d.placed) d.placed - item else d.placed + item)
    }

    /** Match: tap a left item, then its partner on the right. Tapping a paired item frees it. */
    fun tapLeft(left: Int) = edit { d ->
        when {
            left in d.pairs -> d.copy(pairs = d.pairs - left, pickedLeft = left)
            d.pickedLeft == left -> d.copy(pickedLeft = null)
            else -> d.copy(pickedLeft = left)
        }
    }

    fun tapRight(right: Int) = edit { d ->
        val owner = d.pairs.entries.firstOrNull { it.value == right }?.key
        val picked = d.pickedLeft ?: d.pairs.keys.let { paired -> (_ui.value.question as? QuizQuestion.Match)?.pairs?.indices?.firstOrNull { it !in paired } }
        when {
            owner != null -> d.copy(pairs = d.pairs - owner)
            picked != null -> d.copy(pairs = d.pairs + (picked to right), pickedLeft = null)
            else -> d
        }
    }

    private fun edit(f: (Draft) -> Draft) {
        val s = _ui.value.state ?: return
        if (s.checked || s.done) return
        _ui.update { it.copy(draft = f(it.draft)) }
    }

    fun check() {
        val ui = _ui.value
        val quiz = ui.quiz ?: return
        val s = ui.state ?: return
        if (!ui.canCheck) return
        val answer = when (val q = ui.question) {
            is QuizQuestion.Choice -> QuizAnswer.Choice(ui.draft.choice!!)
            is QuizQuestion.TrueFalse -> QuizAnswer.TrueFalse(ui.draft.truth!!)
            is QuizQuestion.Order -> QuizAnswer.Order(ui.draft.placed)
            is QuizQuestion.Match -> QuizAnswer.Match(q.pairs.indices.map { ui.draft.pairs.getValue(it) })
            null -> return
        }
        commit(QuizEngine.answer(quiz, s, answer))
    }

    fun next() {
        val s = _ui.value.state ?: return
        _ui.update { it.copy(draft = Draft()) }
        commit(QuizEngine.next(s))
    }

    /** "Refac testul": a new attempt with a new shuffle. */
    fun again() {
        val quiz = _ui.value.quiz ?: return
        attemptId = null
        _ui.update { it.copy(draft = Draft()) }
        commit(QuizEngine.start(quiz))
    }

    private fun commit(s: QuizState) {
        _ui.update { it.copy(state = s) }
        viewModelScope.launch {
            withContext(Dispatchers.IO + NonCancellable) {
                saving.lock()
                try {
                    val id = attemptId ?: c.quizzes.startAttempt(lessonId, s).also { attemptId = it }
                    c.quizzes.saveAttempt(id, s)
                } finally {
                    saving.unlock()
                }
            }
        }
    }
}
