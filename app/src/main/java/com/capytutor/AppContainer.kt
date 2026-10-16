package com.capytutor

import android.content.Context
import com.capytutor.ai.LessonEnricher
import com.capytutor.ai.ModelSessionAi
import com.capytutor.ai.ProviderRouter
import com.capytutor.ai.QuizMaker
import com.capytutor.ai.VoiceTranscriber
import com.capytutor.ai.groq.GroqWhisperStt
import com.capytutor.ai.groq.Groq
import com.capytutor.ai.http.OkHttpTransport
import com.capytutor.core.safety.SafetyFilter
import com.capytutor.core.session.SessionAi
import com.capytutor.data.db.CapyDatabase
import com.capytutor.data.repo.LessonRepository
import com.capytutor.data.repo.QuizRepository
import com.capytutor.data.repo.RewardRepository
import com.capytutor.data.repo.SessionRepository
import com.capytutor.data.repo.UsageStore
import com.capytutor.data.settings.SettingsStore
import com.capytutor.core.config.CoreConfig
import com.capytutor.demo.DemoSessionAi
import com.capytutor.demo.DemoSpeechToText
import com.capytutor.voice.AndroidSpeechStt
import com.capytutor.voice.AndroidTts
import com.capytutor.voice.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-made dependency wiring (HANDOFF.md §4: DI by hand, keep it small). */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: CapyDatabase = CapyDatabase.open(context)
    val settings = SettingsStore(context, scope)
    val usage = UsageStore(db, scope)

    val transport = OkHttpTransport()

    /** Every Groq call reads the key at call time, so a key added in Settings works at once. */
    val router = ProviderRouter(providerFor = { Groq.forRoute(it, { settings.groqKey }, transport) }, usage = usage)

    private val modelAi = ModelSessionAi(router, childForm = { settings.settings.value.gender.promptForm })

    val lessons = LessonRepository(db, context.filesDir, LessonEnricher(router), hasGroqKey = { settings.groqKey.isNotBlank() })
    val sessions = SessionRepository(db)
    val quizzes = QuizRepository(db, QuizMaker(router), hasGroqKey = { settings.groqKey.isNotBlank() })
    val rewards = RewardRepository(db)
    val progress = com.capytutor.data.repo.ProgressRepository(db, quizzes)

    /** Prepares questions, then quizzes, for many lessons within Groq's free limits (parent mode). */
    val questionQueue = com.capytutor.ui.parent.QuestionQueue(lessons, quizzes, scope)

    /** Debug builds only: the CI screenshot tour runs without Groq (see demo/Demo.kt). */
    @Volatile
    var demo: Boolean = false

    val sessionAi: SessionAi get() = if (demo) DemoSessionAi else modelAi

    // ---- voice (M4) ----------------------------------------------------------------------------

    private val whisper by lazy {
        val route = CoreConfig.route(CoreConfig.TRANSCRIBE)
        GroqWhisperStt(route.model, { settings.groqKey }, transport, timeoutMs = CoreConfig.timeoutMs(CoreConfig.TRANSCRIBE).coerceAtLeast(30_000))
    }

    /** Sends a recording to Groq Whisper and always deletes it. */
    val transcriber: VoiceTranscriber get() = VoiceTranscriber(if (demo) DemoSpeechToText else whisper, usage)

    val recorder = AudioRecorder(context)
    val deviceStt = AndroidSpeechStt(context)

    /** Started on first use: binding to the speech engine takes a moment. */
    private val ttsLazy = lazy { AndroidTts(context) }
    val tts: AndroidTts by ttsLazy

    /** Stops Capy reading aloud, without starting the speech engine just for that. */
    fun stopVoice() {
        if (ttsLazy.isInitialized()) tts.stop()
    }

    /** Replaces the child's name with "copilul" in everything she writes, before any call. */
    fun safetyFilter(): SafetyFilter = SafetyFilter(listOf(settings.settings.value.childName).filter { it.isNotBlank() })

    init {
        scope.launch { settings.load() }
        // A recording left behind by a killed app is never sent: delete it.
        recorder.clearLeftovers()
    }
}
