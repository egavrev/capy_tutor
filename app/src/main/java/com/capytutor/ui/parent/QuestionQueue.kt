package com.capytutor.ui.parent

import com.capytutor.core.RomanianText
import com.capytutor.data.repo.LessonRepository
import com.capytutor.data.repo.Prepare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Prepares hints and questions for many lessons, one at a time, within Groq's free limits
 * (about 8,000 tokens a minute and 200,000 a day; one lesson takes several thousand). Waits when
 * Groq asks to, stops at the daily limit. Runs app-wide, so the parent can keep using parent mode.
 */
class QuestionQueue(private val lessons: LessonRepository, private val scope: CoroutineScope) {

    data class State(
        val running: Boolean = false,
        val done: Int = 0,
        val left: Int = 0,
        val current: String? = null,
        val waitSeconds: Int? = null,
        /** The last thing to tell the parent (finished, stopped, problem). */
        val message: String? = null,
    ) {
        val text: String?
            get() = when {
                running && waitSeconds != null -> "Groq cere o pauză. Continui peste ${RomanianText.count(waitSeconds, "secundă", "secunde")}… (gata: $done, rămase: $left)"
                running -> "Pregătesc întrebările pentru „${current.orEmpty()}”… (gata: $done, rămase: $left)"
                else -> message
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var done = 0
            _state.value = State(running = true)
            while (isActive) {
                val todo = lessons.withoutQuestions()
                val next = todo.firstOrNull()
                if (next == null) {
                    _state.value = State(message = if (done == 0) "Toate lecțiile au deja întrebări." else "Gata: am pregătit întrebările pentru ${RomanianText.count(done, "lecție", "lecții")}. Verifică-le, apoi marchează-le gata.")
                    return@launch
                }
                _state.value = State(running = true, done = done, left = todo.size, current = next.title)
                when (val r = lessons.prepareQuestions(next.id)) {
                    Prepare.Done -> done++
                    is Prepare.Wait -> {
                        for (s in r.seconds + 2 downTo 1) {
                            _state.value = _state.value.copy(waitSeconds = s)
                            delay(1_000)
                        }
                        _state.value = _state.value.copy(waitSeconds = null)
                    }
                    is Prepare.Stop -> {
                        _state.value = State(done = done, left = todo.size, message = r.message + if (done > 0) " (Am pregătit ${RomanianText.count(done, "lecție", "lecții")}.)" else "")
                        return@launch
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = State(message = "Oprit. Poți continua oricând.")
    }
}
