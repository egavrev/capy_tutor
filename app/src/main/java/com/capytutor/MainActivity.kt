package com.capytutor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.capytutor.demo.Demo
import com.capytutor.ui.CapyNavHost
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
        if (savedInstanceState == null) readShared(intent)
        setContent {
            CapyTheme {
                CapyScreenBackground {
                    if (ready.value) CapyNavHost(shared = shared.value, onSharedConsumed = { shared.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShared(intent)
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
