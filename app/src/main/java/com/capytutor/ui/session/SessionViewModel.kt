package com.capytutor.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.ai.Transcription
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.session.AiOutcome
import com.capytutor.core.session.Input
import com.capytutor.core.session.InputMode
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

/** The microphone, as the child sees it. */
enum class MicState { IDLE, RECORDING, LISTENING_ON_PHONE, TRANSCRIBING }

data class SessionUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val pack: LessonPack? = null,
    val state: SessionState? = null,
    /** Capy is waiting for the model. */
    val thinking: Boolean = false,
    val draft: String = "",
    /** How the draft was made: typed, Whisper, or the phone's recogniser (stored with each turn). */
    val draftMode: InputMode = InputMode.KEYBOARD,
    val mic: MicState = MicState.IDLE,
    /** A short line under the mic ("Nu am auzit nimic…"). */
    val voiceNote: String? = null,
    /** The child chose the keyboard on the retelling screen. */
    val keyboard: Boolean = false,
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

    /** Typing into an empty box makes it a keyboard answer; correcting a transcript keeps it a voice answer. */
    fun onDraft(text: String) = _ui.update {
        it.copy(draft = text, draftMode = if (it.draft.isBlank()) InputMode.KEYBOARD else it.draftMode)
    }

    fun showKeyboard() = _ui.update { it.copy(keyboard = true) }

    fun sendDraft() {
        val text = _ui.value.draft.trim()
        if (text.isEmpty()) return
        val mode = _ui.value.draftMode
        _ui.update { it.copy(draft = "", draftMode = InputMode.KEYBOARD, voiceNote = null) }
        send(Input.Say(text, mode))
    }

    /** "Gata, am terminat": a typed but unsent piece is sent first. */
    fun finishRetelling() {
        val text = _ui.value.draft.trim()
        val mode = _ui.value.draftMode
        _ui.update { it.copy(draft = "", draftMode = InputMode.KEYBOARD, voiceNote = null) }
        viewModelScope.launch {
            if (text.isNotEmpty()) apply(Input.Say(text, mode))
            apply(Input.FinishRetelling)
        }
    }

    // ---- voice (HANDOFF.md §4.4) ---------------------------------------------------------------

    /** Until then the phone's own recogniser is used (Groq rate limited or offline). */
    private var phoneUntil = 0L

    /** Tap on the mic: start, or stop and transcribe. */
    fun micTap() {
        when (_ui.value.mic) {
            MicState.IDLE -> if (System.currentTimeMillis() < phoneUntil) listenOnPhone() else startRecording()
            MicState.RECORDING -> viewModelScope.launch { stopAndTranscribe(maxReached = false) }
            MicState.LISTENING_ON_PHONE -> c.deviceStt.stop()
            MicState.TRANSCRIBING -> Unit
        }
    }

    private fun startRecording() {
        c.stopVoice()
        val ok = c.recorder.start(onMaxReached = { viewModelScope.launch { stopAndTranscribe(maxReached = true) } })
        _ui.update {
            if (ok) it.copy(mic = MicState.RECORDING, voiceNote = null)
            else it.copy(voiceNote = "Microfonul nu merge acum. Scrie răspunsul.", keyboard = true)
        }
    }

    private suspend fun stopAndTranscribe(maxReached: Boolean) {
        if (_ui.value.mic != MicState.RECORDING) return
        val rec = c.recorder.stop()
        if (rec == null) {
            _ui.update { it.copy(mic = MicState.IDLE, voiceNote = "Nu am auzit nimic. Apasă microfonul și vorbește.") }
            return
        }
        _ui.update { it.copy(mic = MicState.TRANSCRIBING) }
        val (file, seconds) = rec
        val result = try {
            withContext(Dispatchers.IO) { c.transcriber.transcribe(file, seconds, _ui.value.pack) }
        } catch (e: CancellationException) {
            file.delete()
            throw e
        } catch (e: Exception) {
            file.delete()
            Transcription.Failed("Capy nu a înțeles înregistrarea. Mai încearcă o dată sau scrie răspunsul.")
        }
        when (result) {
            is Transcription.Text -> heard(result.text, InputMode.VOICE, if (maxReached) "Am ascultat 3 minute. Apasă din nou microfonul ca să continui." else null)
            Transcription.Empty -> _ui.update { it.copy(mic = MicState.IDLE, voiceNote = "Nu am auzit nimic. Apasă microfonul și vorbește.") }
            is Transcription.Failed -> _ui.update { it.copy(mic = MicState.IDLE, voiceNote = result.message, keyboard = true) }
            is Transcription.UseFallback -> {
                if (c.deviceStt.isAvailable()) {
                    val wait = maxOf(result.retryAfterSeconds ?: 0, 60) * 1000L
                    phoneUntil = System.currentTimeMillis() + if (result.reason == Transcription.Reason.OFFLINE) 2 * 60_000L else wait
                    _ui.update { it.copy(mic = MicState.IDLE, voiceNote = "Capy te ascultă acum cu telefonul. Apasă microfonul și spune din nou.") }
                } else {
                    _ui.update { it.copy(mic = MicState.IDLE, voiceNote = "Capy nu te poate asculta acum. Scrie răspunsul.", keyboard = true) }
                }
            }
        }
    }

    private fun listenOnPhone() {
        c.stopVoice()
        _ui.update { it.copy(mic = MicState.LISTENING_ON_PHONE, voiceNote = null) }
        viewModelScope.launch {
            val text = c.deviceStt.listenOnce()
            when {
                text == null -> {
                    phoneUntil = 0L
                    _ui.update { it.copy(mic = MicState.IDLE, voiceNote = "Capy nu te poate asculta acum. Scrie răspunsul.", keyboard = true) }
                }
                text.isBlank() -> _ui.update { it.copy(mic = MicState.IDLE, voiceNote = "Nu am auzit nimic. Apasă microfonul și vorbește.") }
                else -> heard(text, InputMode.VOICE_FALLBACK, null)
            }
        }
    }

    /** The transcript goes into the answer box, where the child can correct it before sending. */
    private fun heard(text: String, mode: InputMode, note: String?) = _ui.update {
        val joined = listOf(it.draft.trim(), text.trim()).filter { t -> t.isNotEmpty() }.joinToString(" ")
        it.copy(mic = MicState.IDLE, draft = joined, draftMode = if (it.draft.isBlank()) mode else it.draftMode, voiceNote = note)
    }

    /** The app went to the background: a recording is dropped, never sent. */
    fun onBackground() {
        when (_ui.value.mic) {
            MicState.RECORDING -> {
                c.recorder.cancel()
                _ui.update { it.copy(mic = MicState.IDLE, voiceNote = "Înregistrarea s-a oprit. Apasă microfonul ca să începi din nou.") }
            }
            MicState.LISTENING_ON_PHONE -> c.deviceStt.stop()
            else -> Unit
        }
        c.stopVoice()
    }

    fun speak(text: String) {
        if (c.tts.speaking.value == text) c.stopVoice() else c.tts.say(text)
    }

    override fun onCleared() {
        if (c.recorder.isRecording) c.recorder.cancel()
        c.stopVoice()
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
