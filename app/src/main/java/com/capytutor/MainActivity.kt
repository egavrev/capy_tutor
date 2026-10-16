package com.capytutor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.capytutor.demo.Demo
import com.capytutor.ui.CapyNavHost
import com.capytutor.ui.intro.CapyIntro
import com.capytutor.ui.intro.IntroScene
import com.capytutor.ui.parent.ParentHomeViewModel
import com.capytutor.ui.theme.CapyScreenBackground
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** A lesson shared from another app, waiting for parent mode. */
    private val shared = mutableStateOf<String?>(null)

    /** False while the debug demo is being seeded. */
    private val ready = mutableStateOf(true)

    /** The start intro over the app; null when there is none (or it is done). */
    private val intro = mutableStateOf<IntroScene?>(null)

    /** True for the few ms while the start counter is read, so the app does not flash first. */
    private val introWaiting = mutableStateOf(false)

    /** Debug only: the intro frozen at this point, for the screenshot tour. */
    private var introFrame: Float? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG && intent.hasExtra(Demo.EXTRA)) {
            val withLessons = intent.getStringExtra(Demo.EXTRA) != "setup"
            container.demo = true
            if (intent.getStringExtra(Demo.EXTRA) != "fresh") {
                ready.value = false
                lifecycleScope.launch {
                    Demo.seed(this@MainActivity, container, lessons = withLessons)
                    ready.value = true
                }
            }
        }
        if (savedInstanceState == null) {
            readShared(intent)
            startIntro()
        }
        setContent {
            CapyTheme {
                CapyScreenBackground {
                    if (ready.value) CapyNavHost(shared = shared.value, onSharedConsumed = { shared.value = null })
                    intro.value?.let { scene -> CapyIntro(scene, onDone = { intro.value = null }, frame = introFrame) }
                    if (introWaiting.value) Box(Modifier.fillMaxSize().background(CapyTheme.colors.soft))
                }
            }
        }
    }

    /**
     * The intro at a cold start: the two scenes take turns. Left out when the phone has
     * animations turned off, and in the debug demo (the screenshot tour asks for it by name).
     */
    private fun startIntro() {
        if (BuildConfig.DEBUG && intent.hasExtra(EXTRA_INTRO)) {
            val (name, at) = intent.getStringExtra(EXTRA_INTRO).orEmpty().split(":").let { it[0] to it.getOrNull(1) }
            intro.value = IntroScene.fromWire(name) ?: IntroScene.SHOW
            introFrame = at?.toFloatOrNull()?.coerceIn(0f, 1f)
            return
        }
        val animationsOff = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        if (animationsOff || (BuildConfig.DEBUG && intent.hasExtra(Demo.EXTRA))) return
        introWaiting.value = true
        lifecycleScope.launch {
            val starts = runCatching { container.settings.nextIntro() }.getOrDefault(0)
            intro.value = IntroScene.forStart(starts)
            introWaiting.value = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShared(intent)
    }

    private companion object {
        /** Debug: `--es capy_intro show:0.5` shows that scene frozen halfway. */
        const val EXTRA_INTRO = "capy_intro"
    }

    private fun readShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        lifecycleScope.launch {
            shared.value = when {
                uri != null -> withContext(Dispatchers.IO) { ParentHomeViewModel.readLesson(this@MainActivity, uri) }
                !text.isNullOrBlank() -> text
                else -> null
            }
        }
    }
}
