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
import com.capytutor.core.RomanianText
import com.capytutor.core.lesson.Chapters
import com.capytutor.data.db.LessonEntity
import com.capytutor.data.repo.ImportResult
import com.capytutor.ui.components.BannerKind
import com.capytutor.ui.components.CapyBanner
import com.capytutor.ui.components.CapyListRow
import com.capytutor.ui.components.CapySecondaryButton
import com.capytutor.ui.components.CapyCard
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
    val queue = c.questionQueue.state

    fun prepareAll() = c.questionQueue.start()
    fun stopPreparing() = c.questionQueue.stop()

    /** One lesson: imported and its questions generated at once, then opened for review. */
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

    /** Files from the picker: .md files and/or a .zip of them (a Drive folder download). */
    fun importUris(context: Context, uris: List<Uri>) {
        if (status.value.busy || uris.isEmpty()) return
        viewModelScope.launch {
            status.value = ImportStatus(busy = true, message = "Citesc fișierele…")
            val all = withContext(Dispatchers.IO) { uris.flatMap { readLessons(context, it) } }
            // "x.questions.json" next to "x.md": questions prepared in advance, no Groq needed.
            val questions = all.filter { it.first.endsWith(QUESTIONS_SUFFIX, ignoreCase = true) }
                .associate { it.first.dropLast(QUESTIONS_SUFFIX.length).lowercase() to it.second }
            val files = all.filterNot { it.first.endsWith(QUESTIONS_SUFFIX, ignoreCase = true) }
            when {
                files.isEmpty() -> status.value = ImportStatus(message = "Nu am găsit lecții .md în fișierele alese.")
                files.size == 1 && files[0].second != null && questions.isEmpty() -> {
                    status.value = ImportStatus()
                    importText(files[0].second!!)
                }
                else -> importMany(files, questions)
            }
        }
    }

    /** Many lessons: saved at once without Groq; questions are prepared afterwards, paced. */
    private suspend fun importMany(files: List<Pair<String, String?>>, questions: Map<String, String?> = emptyMap()) {
        var added = 0
        var withQuestions = 0
        var completed = 0
        val problems = mutableListOf<String>()
        for ((i, f) in files.withIndex()) {
            val (name, text) = f
            status.value = ImportStatus(busy = true, message = "Adaug lecțiile… ${i + 1} din ${files.size}")
            if (text == null) {
                problems += "$name: nu am putut citi fișierul"
                continue
            }
            val prepared = questions[name.removeSuffix(".md").lowercase()]
            val r = withContext(Dispatchers.IO) {
                if (prepared != null) c.lessons.importWithQuestions(text, prepared) else c.lessons.import(text, enrich = false)
            }
            when (r) {
                is ImportResult.Ok -> if (r.questionsAdded) completed++ else {
                    added++
                    if (prepared != null && r.enrichError == null) withQuestions++
                    r.enrichError?.let { problems += "$name: $it" }
                }
                is ImportResult.Rejected -> if (!r.message.contains("există deja")) problems += "$name: ${r.message}"
            }
        }
        val skipped = files.size - added - completed - problems.count { !it.contains("Întrebările pregătite") }
        val parts = buildList {
            if (added > 0 || completed == 0) add("Am adăugat ${RomanianText.count(added, "lecție", "lecții")}.")
            if (completed > 0) add("Am pus întrebările pregătite la ${RomanianText.count(completed, "lecție", "lecții")} care erau deja în aplicație.")
            if (withQuestions > 0) add("${RomanianText.count(withQuestions, "lecție are", "lecții au")} deja întrebările pregătite.")
            if (withQuestions + completed > 0) add("Verifică lecțiile cu întrebări pregătite și marchează-le gata.")
            if (skipped > 0) add("${RomanianText.count(skipped, "lecție era", "lecții erau")} deja în aplicație.")
            if (added > withQuestions) add("Pentru celelalte, apasă „Pregătește întrebările”.")
            if (problems.isNotEmpty()) add("Nu am putut adăuga:\n" + problems.joinToString("\n") { "• $it" })
        }
        status.value = ImportStatus(message = parts.joinToString(" "), ok = problems.isEmpty())
    }

    fun consumed() {
        status.value = status.value.copy(openLessonId = null)
    }

    companion object {
        private const val MAX_BYTES = 200_000

        private const val MAX_FILES = 300
        const val QUESTIONS_SUFFIX = ".questions.json"

        /** (file name, text or null when unreadable) for one picked file; a .zip gives every .md inside. */
        fun readLessons(context: Context, uri: Uri): List<Pair<String, String?>> {
            val name = displayName(context, uri) ?: "lecție"
            val isZip = name.endsWith(".zip", ignoreCase = true) ||
                context.contentResolver.getType(uri)?.contains("zip") == true
            if (!isZip) return listOf(name to readLesson(context, uri))
            return runCatching {
                val out = mutableListOf<Pair<String, String?>>()
                context.contentResolver.openInputStream(uri)?.use { raw ->
                    java.util.zip.ZipInputStream(raw).use { zip ->
                        while (out.size < MAX_FILES) {
                            val e = zip.nextEntry ?: break
                            val entry = e.name.substringAfterLast('/')
                            val wanted = entry.endsWith(".md", ignoreCase = true) || entry.endsWith(QUESTIONS_SUFFIX, ignoreCase = true)
                            if (e.isDirectory || !wanted || entry.startsWith(".") || entry.startsWith("TEMPLATE") || e.name.contains("__MACOSX")) continue
                            val bytes = zip.readNBytesCompat(MAX_BYTES + 1)
                            out += entry to (if (bytes.size > MAX_BYTES) null else bytes.toString(Charsets.UTF_8))
                        }
                    }
                }
                out.sortedBy { it.first }
            }.getOrElse { listOf(name to null) }
        }

        private fun displayName(context: Context, uri: Uri): String? = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

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
    val queue by vm.queue.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = CapyTheme.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importUris(context, uris) }
    val missing = lessons.count { !it.enriched }
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
                CapyPrimaryButton("Adaugă lecții (.md sau .zip)", loading = status.busy) {
                    picker.launch(arrayOf("text/*", "application/json", "application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }
            }
            status.message?.let { m ->
                item { CapyBanner(m, if (status.busy || status.ok) BannerKind.INFO else BannerKind.NOT_YET) }
            }
            if (missing > 0 || queue.running || queue.text != null) {
                item {
                    CapyCard {
                        Text(
                            if (missing > 0) "${RomanianText.count(missing, "lecție nu are", "lecții nu au")} încă întrebări." else "Toate lecțiile au întrebări.",
                            style = CapyTheme.type.body,
                            color = c.text,
                        )
                        queue.text?.let { Text(it, style = CapyTheme.type.caption, color = c.textSecondary) }
                        if (queue.running) {
                            CapySecondaryButton("Oprește", onClick = vm::stopPreparing)
                        } else if (missing > 0) {
                            CapySecondaryButton("Pregătește întrebările", onClick = vm::prepareAll)
                            Text(
                                "Câte o lecție pe rând, cu pauze când Groq cere. Planul gratuit ajunge pentru aproximativ 25–30 de lecții pe zi; restul continuă mâine.",
                                style = CapyTheme.type.caption,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }
            if (lessons.isEmpty()) {
                item { Text("Nicio lecție încă. Adaugă fișiere .md făcute după șablon, sau un .zip cu ele.", style = CapyTheme.type.body, color = c.textSecondary) }
            }
            lessonsByChapter(lessons).forEach { (header, list) ->
                item(key = "h-$header") { SectionTitle(header) }
                items(list, key = { it.id }) { l ->
                    CapyListRow(
                        title = l.title,
                        caption = when {
                            l.status == LessonEntity.READY -> "gata pentru copil"
                            !l.enriched -> "fără întrebări încă"
                            else -> "de verificat"
                        },
                        leading = { SubjectDot(subjectColor(c, l.subject)) },
                        onClick = { onLesson(l.id) },
                    )
                }
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

/** "Istorie · Unitatea I. Preistorie" → its lessons; units in the book's order, lessons by title. */
internal fun lessonsByChapter(lessons: List<LessonEntity>): List<Pair<String, List<LessonEntity>>> =
    lessons.groupBy { it.subject to it.chapter }
        .toList()
        .sortedWith(compareBy<Pair<Pair<String, String?>, List<LessonEntity>>> { it.first.first }.thenBy(Chapters.order) { it.first.second })
        .map { (key, list) ->
            val (subject, chapter) = key
            (if (chapter.isNullOrBlank()) subject else "$subject · $chapter") to list.sortedBy { it.title.lowercase() }
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
