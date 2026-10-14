package com.capytutor

import android.content.Context
import com.capytutor.ai.LessonEnricher
import com.capytutor.ai.ModelSessionAi
import com.capytutor.ai.ProviderRouter
import com.capytutor.ai.groq.Groq
import com.capytutor.ai.http.OkHttpTransport
import com.capytutor.core.safety.SafetyFilter
import com.capytutor.core.session.SessionAi
import com.capytutor.data.db.CapyDatabase
import com.capytutor.data.repo.LessonRepository
import com.capytutor.data.repo.SessionRepository
import com.capytutor.data.repo.UsageStore
import com.capytutor.data.settings.SettingsStore
import com.capytutor.demo.DemoSessionAi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-made dependency wiring (HANDOFF.md §4: DI by hand, keep it small). */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: CapyDatabase = CapyDatabase.open(context)
    val settings = SettingsStore(context, scope)
    val usage = UsageStore(db, scope)

    private val transport = OkHttpTransport()

    /** Every Groq call reads the key at call time, so a key added in Settings works at once. */
    val router = ProviderRouter(providerFor = { Groq.forRoute(it, { settings.groqKey }, transport) }, usage = usage)

    private val modelAi = ModelSessionAi(router, childForm = { settings.settings.value.gender.promptForm })

    val lessons = LessonRepository(db, context.filesDir, LessonEnricher(router), hasGroqKey = { settings.groqKey.isNotBlank() })
    val sessions = SessionRepository(db)

    /** Debug builds only: the CI screenshot tour runs without Groq (see demo/Demo.kt). */
    @Volatile
    var demo: Boolean = false

    val sessionAi: SessionAi get() = if (demo) DemoSessionAi else modelAi

    /** Replaces the child's name with "copilul" in everything she writes, before any call. */
    fun safetyFilter(): SafetyFilter = SafetyFilter(listOf(settings.settings.value.childName).filter { it.isNotBlank() })

    init {
        scope.launch { settings.load() }
    }
}
