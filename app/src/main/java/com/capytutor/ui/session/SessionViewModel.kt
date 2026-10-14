package com.capytutor.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.session.AiOutcome
import com.capytutor.core.session.Input
import com.capytutor.core.session.PauseReason
import com.capytutor.core.session.Phase
import com.capytutor.core.session.SessionRunner
import com.capytutor.core.session.SessionState
import com.capytutor.core.session.Update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SessionUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val pack: LessonPack? = null,
    val state: SessionState? = null,
    /** Capy is waiting for the model. */
    val thinking: Boolean = false,
    val draft: String = "",
)

/**
 * One lesson session. The state is saved after every step, including *before* each model call
 * (with the request pending), so killing the app at any moment loses nothing: on reopen the
 * same request is sent again ([SessionRunner.recover]).
 */
class SessionViewModel(private val c: AppContainer, private val lessonId: Long) : ViewModel() {
    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui

    private var runner: SessionRunner? = null
    private var sessionId: Long? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val pack = withContext(Dispatchers.IO) { c.lessons.pack(lessonId) }
        if (pack == null || pack.ideas.isEmpty()) {
            _ui.update { it.copy(loading = false, missing = true) }
            return
        }
        val r = SessionRunner(pack, c.sessionAi, c.safetyFilter())
        runner = r
        val open = withContext(Dispatchers.IO) { c.sessions.open(lessonId) }?.takeIf { it.second.lessonSha256 == pack.lessonSha256 }
        if (open != null) {
            sessionId = open.first
            show(pack, open.second)
            if (open.second.pending != null && open.second.paused == null) {
                // Killed while Capy was thinking: ask again.
                ask { r.recover(open.second) }
            }
        } else {
            show(pack, r.start().state)
        }
    }

    private fun show(pack: LessonPack, s: SessionState) = _ui.update { it.copy(loading = false, pack = pack, state = s) }

    fun onDraft(text: String) = _ui.update { it.copy(draft = text) }

    fun sendDraft() {
        val text = _ui.value.draft.trim()
        if (text.isEmpty()) return
        _ui.update { it.copy(draft = "") }
        send(Input.Say(text))
    }

    /** "Gata, am terminat": a typed but unsent piece is sent first. */
    fun finishRetelling() {
        val text = _ui.value.draft.trim()
        _ui.update { it.copy(draft = "") }
        viewModelScope.launch {
            if (text.isNotEmpty()) apply(Input.Say(text))
            apply(Input.FinishRetelling)
        }
    }

    fun send(input: Input) {
        viewModelScope.launch { apply(input) }
    }

    private suspend fun apply(input: Input) {
        val r = runner ?: return
        val s = _ui.value.state ?: return
        if (_ui.value.thinking) return
        val first = r.engine.onInput(s, input)
        if (first.state == s) return
        commit(first.state)
        if (first.request != null && first.state.paused == null) ask { r.recover(first.state) }
    }

    /** Runs a model step with Capy thinking; any failure becomes a gentle pause, never a crash. */
    private suspend fun ask(step: suspend () -> Update) {
        _ui.update { it.copy(thinking = true) }
        val result = try {
            withContext(Dispatchers.IO) { step() }.state
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val s = _ui.value.state!!
            runner!!.engine.onAiResult(s, AiOutcome.Unavailable(PauseReason.FAILED, null)).state
        }
        _ui.update { it.copy(thinking = false) }
        commit(result)
    }

    private suspend fun commit(s: SessionState) {
        val pack = _ui.value.pack ?: return
        _ui.update { it.copy(state = s) }
        withContext(Dispatchers.IO + NonCancellable) {
            val id = sessionId ?: c.sessions.create(lessonId, s).also { sessionId = it }
            c.sessions.save(id, pack, s)
        }
    }

    val isDone: Boolean get() = _ui.value.state?.phase == Phase.DONE
}
