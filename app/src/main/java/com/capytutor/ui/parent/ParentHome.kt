package com.capytutor.ui.parent

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.data.db.LessonEntity
import com.capytutor.data.repo.ImportResult
import com.capytutor.ui.components.BannerKind
import com.capytutor.ui.components.CapyBanner
import com.capytutor.ui.components.CapyListRow
import com.capytutor.ui.components.CapyPrimaryButton
import com.capytutor.ui.components.CapyTopBar
import com.capytutor.ui.components.PinPad
import com.capytutor.ui.components.SectionTitle
import com.capytutor.ui.components.SubjectDot
import com.capytutor.ui.home.subjectColor
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Parent mode behind the PIN. */
@Composable
fun PinGateScreen(c: AppContainer, onBack: () -> Unit, onOpen: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            delay(150)
            if (c.settings.checkPin(pin)) onOpen() else { wrong = true; pin = "" }
        }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = CapyDimens.screenMargin).verticalScroll(rememberScrollState())) {
        CapyTopBar("Mod părinte", onBack = onBack)
        Text("Scrie PIN-ul", style = CapyTheme.type.question, color = CapyTheme.colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = CapyDimens.screenMargin))
        PinPad(
            pin = pin,
            onPinChange = { pin = it; if (it.isNotEmpty()) wrong = false },
            wrong = wrong,
            message = if (wrong) "PIN-ul nu e cel bun. Mai încearcă o dată." else null,
        )
    }
}

data class ImportStatus(val busy: Boolean = false, val message: String? = null, val ok: Boolean = false, val openLessonId: Long? = null)

class ParentHomeViewModel(private val c: AppContainer) : ViewModel() {
    val lessons: StateFlow<List<LessonEntity>> = c.lessons.observeLessons().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val status = MutableStateFlow(ImportStatus())

    fun importText(markdown: String) {
        if (status.value.busy) return
        status.value = ImportStatus(busy = true, message = "Capy citește lecția și pregătește întrebările…")
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { c.lessons.import(markdown) }
            status.value = when (r) {
                is ImportResult.Rejected -> ImportStatus(message = r.message)
                is ImportResult.Ok -> ImportStatus(
                    message = r.enrichError?.let { "Lecția e salvată. $it" } ?: "Lecția e salvată. Verifică ideile și întrebările, apoi marcheaz-o gata.",
                    ok = r.enrichError == null,
                    openLessonId = r.lessonId,
                )
            }
        }
    }

    fun importUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { readLesson(context, uri) }
            if (text == null) status.value = ImportStatus(message = "Nu am putut citi fișierul. Alege un fișier .md cu lecția.") else importText(text)
        }
    }

    fun consumed() {
        status.value = status.value.copy(openLessonId = null)
    }

    companion object {
        private const val MAX_BYTES = 200_000

        fun readLesson(context: Context, uri: Uri): String? = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) null else bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()

        private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() < n) {
                val r = read(buf, 0, minOf(buf.size, n - out.size()))
                if (r < 0) break
                out.write(buf, 0, r)
            }
            return out.toByteArray()
        }
    }
}

@Composable
fun ParentHomeScreen(
    vm: ParentHomeViewModel,
    shared: String?,
    onSharedConsumed: () -> Unit,
    onBack: () -> Unit,
    onLesson: (Long) -> Unit,
    onSettings: () -> Unit,
    onUsage: () -> Unit,
    onPhoneCheck: () -> Unit,
) {
    val lessons by vm.lessons.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = CapyTheme.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importUri(context, uri) }
    LaunchedEffect(shared) {
        if (shared != null) {
            vm.importText(shared)
            onSharedConsumed()
        }
    }
    LaunchedEffect(status.openLessonId) {
        status.openLessonId?.let { vm.consumed(); onLesson(it) }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        CapyTopBar("Mod părinte", onBack = onBack, modifier = Modifier.padding(horizontal = CapyDimens.screenMargin))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = CapyDimens.screenMargin, end = CapyDimens.screenMargin, bottom = CapyDimens.blockGap),
            verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
        ) {
            item {
                CapyPrimaryButton("Adaugă o lecție (.md)", loading = status.busy) {
                    picker.launch(arrayOf("text/*", "application/octet-stream"))
                }
            }
            status.message?.let { m ->
                item { CapyBanner(m, if (status.busy || status.ok) BannerKind.INFO else BannerKind.NOT_YET) }
            }
            item { SectionTitle("Lecții") }
            if (lessons.isEmpty()) {
                item { Text("Nicio lecție încă. Adaugă un fișier .md făcut după șablon.", style = CapyTheme.type.body, color = c.textSecondary) }
            }
            items(lessons, key = { it.id }) { l ->
                CapyListRow(
                    title = l.title,
                    caption = listOf(
                        l.subject,
                        when {
                            l.status == LessonEntity.READY -> "gata pentru copil"
                            !l.enriched -> "fără întrebări încă"
                            else -> "de verificat"
                        },
                    ).joinToString(" · "),
                    leading = { SubjectDot(subjectColor(c, l.subject)) },
                    onClick = { onLesson(l.id) },
                )
            }
            item { SectionTitle("Telefon") }
            item {
                CapyListRow("Setări", caption = "Nume, PIN, cheia Groq, vocea lui Capy", leading = { RowIcon(R.drawable.ic_capy_sliders) }, onClick = onSettings)
            }
            item {
                CapyListRow("Folosire azi", caption = "Cereri, tokeni și secunde audio Groq", leading = { RowIcon(R.drawable.ic_capy_chart) }, onClick = onUsage)
            }
            item {
                CapyListRow("Verificare telefon", caption = "Microfon, Groq, vocea română", leading = { RowIcon(R.drawable.ic_capy_mic) }, onClick = onPhoneCheck)
            }
        }
    }
}

@Composable
internal fun RowIcon(res: Int) {
    Icon(painterResource(res), contentDescription = null, tint = CapyTheme.colors.secondary, modifier = Modifier.size(CapyDimens.icon))
}

/** Simple scrolling page used by the smaller parent screens. */
@Composable
internal fun ParentPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        CapyTopBar(title, onBack = onBack, modifier = Modifier.padding(horizontal = CapyDimens.screenMargin))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = CapyDimens.screenMargin, end = CapyDimens.screenMargin, bottom = CapyDimens.blockGap),
            verticalArrangement = Arrangement.spacedBy(CapyDimens.screenMargin),
            horizontalAlignment = Alignment.Start,
            content = content,
        )
    }
}
