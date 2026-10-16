package com.capytutor.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.core.RomanianText
import com.capytutor.core.progress.Evidence
import com.capytutor.core.progress.IdeaStatus
import com.capytutor.core.progress.Mark
import com.capytutor.data.repo.Activity
import com.capytutor.data.repo.IdeaProgress
import com.capytutor.data.repo.LessonProgress
import com.capytutor.data.repo.ProgressOverview
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyListRow
import com.capytutor.ui.components.CapyTextButton
import com.capytutor.ui.components.ChipState
import com.capytutor.ui.components.IdeaChip
import com.capytutor.ui.components.SectionTitle
import com.capytutor.ui.components.SubjectDot
import com.capytutor.ui.home.subjectColor
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ro = Locale.forLanguageTag("ro")
private val dayFormat = SimpleDateFormat("d MMM", ro)
private val dayYearFormat = SimpleDateFormat("d MMM yyyy", ro)
private val timeFormat = SimpleDateFormat("HH:mm", ro)

/** "azi", "ieri", "14 oct.", or with the year when it is not this year. */
internal fun day(at: Long, now: Long = System.currentTimeMillis()): String {
    val a = Calendar.getInstance().apply { timeInMillis = at }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = a.get(Calendar.YEAR) == n.get(Calendar.YEAR)
    val days = n.get(Calendar.DAY_OF_YEAR) - a.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && days == 0 -> "azi"
        sameYear && days == 1 -> "ieri"
        sameYear -> dayFormat.format(Date(at))
        else -> dayYearFormat.format(Date(at))
    }
}

/** E15: loaded when the screen opens and again whenever it comes back to the front. */
class ProgressViewModel(private val c: AppContainer) : ViewModel() {
    private val _overview = MutableStateFlow<ProgressOverview?>(null)
    val overview: StateFlow<ProgressOverview?> = _overview

    fun refresh() = viewModelScope.launch {
        _overview.value = withContext(Dispatchers.IO) { c.progress.overview() }
    }
}

@Composable
private fun RefreshOnResume(vm: ProgressViewModel) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() } }
}

/** Progres: every lesson she worked on, what she knows and what to practise, then the history list. */
@Composable
fun ProgressScreen(vm: ProgressViewModel, childName: String, onBack: () -> Unit, onLesson: (Long) -> Unit) {
    RefreshOnResume(vm)
    val o by vm.overview.collectAsStateWithLifecycle()
    val col = CapyTheme.colors
    var showAll by remember { mutableStateOf(false) }
    ParentPage("Progres", onBack) {
        val overview = o ?: return@ParentPage
        if (overview.activities.isEmpty()) {
            Text("${childName.ifBlank { "Copilul" }} nu a terminat încă nicio lecție sau quiz. Rezultatele apar aici după prima lecție.", style = CapyTheme.type.body, color = col.textSecondary)
            return@ParentPage
        }
        CapyCard {
            Text(
                "${RomanianText.count(overview.lessons.size, "lecție lucrată", "lecții lucrate")} · ${RomanianText.count(overview.sessions, "sesiune", "sesiuni")} · ${RomanianText.count(overview.quizzes, "quiz", "quiz-uri")}",
                style = CapyTheme.type.body,
                color = col.text,
            )
            val known = overview.lessons.sumOf { it.known }
            val weak = overview.lessons.sumOf { it.weak }
            Text("Știe ${RomanianText.count(known, "idee", "idei")}; ${RomanianText.count(weak, "idee e", "idei sunt")} de exersat.", style = CapyTheme.type.body, color = col.text)
            Text("Ultima dată: ${day(overview.activities.first().at)}", style = CapyTheme.type.caption, color = col.textSecondary)
        }
        SectionTitle("Lecții")
        overview.lessons.forEach { l ->
            CapyListRow(
                title = l.title,
                caption = buildList {
                    add("știe ${l.known} din ${l.ideas.size}")
                    if (l.weak > 0) add("${l.weak} de exersat")
                    l.lastAt?.let { add(day(it)) }
                }.joinToString(" · "),
                leading = { SubjectDot(subjectColor(col, l.subject)) },
                onClick = { onLesson(l.lessonId) },
            )
        }
        SectionTitle("Istoric")
        val list = if (showAll) overview.activities else overview.activities.take(15)
        CapyCard { list.forEach { ActivityLine(it, withTitle = true) } }
        if (!showAll && overview.activities.size > list.size) {
            CapyTextButton("Arată tot istoricul (${overview.activities.size})") { showAll = true }
        }
    }
}

@Composable
private fun ActivityLine(a: Activity, withTitle: Boolean) {
    val col = CapyTheme.colors
    val what = if (a.kind == Activity.Kind.SESSION) "Lecție: ${a.correct} din ${a.total} la test" else "Quiz: ${a.correct} din ${a.total}"
    Column(Modifier.fillMaxWidth()) {
        Text("${day(a.at)}, ${timeFormat.format(Date(a.at))}", style = CapyTheme.type.caption, color = col.textSecondary)
        Text(if (withTitle) "${a.lessonTitle} · $what" else what, style = CapyTheme.type.body, color = col.text)
    }
}

class LessonProgressViewModel(private val c: AppContainer, private val lessonId: Long) : ViewModel() {
    private val _lesson = MutableStateFlow<LessonProgress?>(null)
    val lesson: StateFlow<LessonProgress?> = _lesson
    var loaded = MutableStateFlow(false)

    fun refresh() = viewModelScope.launch {
        _lesson.value = withContext(Dispatchers.IO) { c.progress.lesson(lessonId) }
        loaded.value = true
    }
}

/** One lesson: each key idea known, to practise or not seen yet, with a dated mark per attempt. */
@Composable
fun LessonProgressScreen(vm: LessonProgressViewModel, onBack: () -> Unit, onSessions: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() } }
    val l by vm.lesson.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val col = CapyTheme.colors
    ParentPage(l?.title ?: "Progres", onBack) {
        val lesson = l
        if (lesson == null) {
            if (loaded) Text("Nicio lecție terminată încă.", style = CapyTheme.type.body, color = col.textSecondary)
            return@ParentPage
        }
        CapyCard {
            Text("Știe ${lesson.known} din ${lesson.ideas.size} idei cheie.", style = CapyTheme.type.title, color = col.text)
            val unseen = lesson.ideas.count { it.status == IdeaStatus.UNSEEN }
            Text(
                buildList {
                    if (lesson.weak > 0) add("${lesson.weak} de exersat")
                    if (unseen > 0) add("$unseen neverificate încă")
                    lesson.lastAt?.let { add("ultima dată ${day(it)}") }
                }.joinToString(" · "),
                style = CapyTheme.type.body,
                color = col.textSecondary,
            )
        }
        val groups = listOf(
            IdeaStatus.WEAK to "De exersat",
            IdeaStatus.KNOWN to "Știe",
            IdeaStatus.UNSEEN to "Neverificate încă",
        )
        groups.forEach { (status, title) ->
            val ideas = lesson.ideas.filter { it.status == status }.sortedBy { it.order }
            if (ideas.isEmpty()) return@forEach
            SectionTitle(title)
            ideas.forEach { IdeaCard(it) }
        }
        SectionTitle("Istoric")
        CapyCard { lesson.activities.forEach { ActivityLine(it, withTitle = false) } }
        CapyTextButton("Detaliile sesiunilor și răspunsurile contestate", onClick = onSessions)
    }
}

@Composable
private fun IdeaCard(idea: IdeaProgress) {
    val col = CapyTheme.colors
    CapyCard {
        IdeaChip(
            when (idea.status) {
                IdeaStatus.KNOWN -> "Știe"
                IdeaStatus.WEAK -> "De exersat"
                IdeaStatus.UNSEEN -> "Neverificată"
            },
            when (idea.status) {
                IdeaStatus.KNOWN -> ChipState.KNOWN
                IdeaStatus.WEAK -> ChipState.PRACTISE
                IdeaStatus.UNSEEN -> ChipState.EMPTY
            },
        )
        Text("${idea.order + 1}. ${idea.text}", style = CapyTheme.type.body, color = col.text)
        if (idea.history.isNotEmpty()) MarkTrail(idea.history.takeLast(MAX_MARKS))
    }
}

private const val MAX_MARKS = 6

/** The last few attempts, oldest first: a heart for remembered, a sprout for "not yet", with the day. */
@Composable
private fun MarkTrail(history: List<Evidence>) {
    val col = CapyTheme.colors
    val described = history.joinToString("; ") { "${day(it.at)}: ${it.mark.words()}" }
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Încercări: $described" },
        horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
        verticalAlignment = Alignment.Top,
    ) {
        history.forEach { e ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(if (e.mark.ok) R.drawable.ic_capy_checkheart else R.drawable.ic_capy_sprout),
                    contentDescription = null,
                    tint = if (e.mark.ok) col.onRight else col.onWrong,
                    modifier = Modifier.size(CapyDimens.iconSmall),
                )
                Text(day(e.at), style = CapyTheme.type.caption, color = col.textSecondary)
                Text(e.mark.short(), style = CapyTheme.type.caption, color = col.textSecondary)
            }
        }
    }
}

private fun Mark.short() = when (this) {
    Mark.ALONE -> "fără ajutor"
    Mark.WITH_HINT -> "cu indiciu"
    Mark.NOT_YET -> "încă nu"
    Mark.QUIZ_RIGHT -> "quiz"
    Mark.QUIZ_NOT_YET -> "quiz"
}

private fun Mark.words() = when (this) {
    Mark.ALONE -> "și-a amintit fără ajutor"
    Mark.WITH_HINT -> "și-a amintit cu un indiciu"
    Mark.NOT_YET -> "încă nu"
    Mark.QUIZ_RIGHT -> "corect la quiz"
    Mark.QUIZ_NOT_YET -> "încă nu la quiz"
}
