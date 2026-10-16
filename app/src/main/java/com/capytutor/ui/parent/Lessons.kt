package com.capytutor.ui.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.core.RomanianText
import com.capytutor.data.db.LessonEntity
import com.capytutor.ui.components.CapyListRow
import com.capytutor.ui.components.CapyTextButton
import com.capytutor.ui.components.CapyTextField
import com.capytutor.ui.components.CapyTopBar
import com.capytutor.ui.components.ChoicePills
import com.capytutor.ui.components.SubjectDot
import com.capytutor.ui.home.subjectColor
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which lessons a parent lessons list shows (E21). */
enum class LessonFilter(val wire: String, val title: String, val pill: String) {
    ALL("toate", "Toate lecțiile", "Toate"),
    TO_CHECK("de-verificat", "De verificat", "De verificat"),
    APPROVED("aprobate", "Lecții aprobate", "Aprobate");

    fun keeps(l: LessonEntity): Boolean = when (this) {
        ALL -> true
        TO_CHECK -> l.status != LessonEntity.READY
        APPROVED -> l.status == LessonEntity.READY
    }

    companion object {
        fun fromWire(s: String?): LessonFilter = entries.firstOrNull { it.wire == s } ?: ALL
    }
}

class LessonsViewModel(private val c: AppContainer) : ViewModel() {
    val lessons: StateFlow<List<LessonEntity>> = c.lessons.observeLessons().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val quizIds: StateFlow<List<Long>> = c.quizzes.observeLessonIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Approves every lesson of a unit that already has its questions. */
    fun approve(ids: List<Long>) = viewModelScope.launch {
        withContext(Dispatchers.IO) { ids.forEach { c.lessons.setReady(it, true) } }
    }
}

/** The status line under a lesson, in the parent lists. */
internal fun lessonCaption(l: LessonEntity, hasQuiz: Boolean): String = buildList {
    add(
        when {
            l.status == LessonEntity.READY -> "aprobată, o vede copilul"
            !l.enriched -> "fără întrebări încă"
            else -> "de verificat"
        },
    )
    if (l.enriched && !hasQuiz) add("fără quiz")
}.joinToString(" · ")

/**
 * Level 2 of parent mode (E21): the lessons, filtered (all / to check / approved), searchable,
 * grouped by unit. Units fold to one line, so 50 lessons stay easy to move through.
 */
@Composable
fun LessonsScreen(vm: LessonsViewModel, start: LessonFilter, onBack: () -> Unit, onLesson: (Long) -> Unit) {
    val all by vm.lessons.collectAsStateWithLifecycle()
    val quizIds by vm.quizIds.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(start) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val col = CapyTheme.colors

    val q = RomanianText.foldForMatching(query.trim())
    val shown = all.filter { filter.keeps(it) && (q.isEmpty() || RomanianText.foldForMatching(it.title).contains(q)) }
    val groups = lessonsByChapter(shown)
    // With a search, or a single unit, everything is unfolded.
    val unfoldAll = q.isNotEmpty() || groups.size == 1

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        CapyTopBar(filter.title, onBack = onBack, modifier = Modifier.padding(horizontal = CapyDimens.screenMargin))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = CapyDimens.screenMargin, end = CapyDimens.screenMargin, bottom = CapyDimens.blockGap),
            verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
        ) {
            item {
                ChoicePills(LessonFilter.entries.map { it to it.pill }, filter, { filter = it; confirm = null })
            }
            if (all.size > 8) {
                item { CapyTextField(query, { query = it }, "Caută o lecție") }
            }
            item {
                Text(
                    when {
                        all.isEmpty() -> "Nicio lecție încă. Adaug-o din „Adaugă lecții”."
                        shown.isEmpty() && q.isNotEmpty() -> "Nicio lecție nu se potrivește."
                        shown.isEmpty() && filter == LessonFilter.APPROVED -> "Nicio lecție aprobată încă. Copilul vede doar lecțiile aprobate."
                        shown.isEmpty() -> "Toate lecțiile sunt aprobate."
                        else -> RomanianText.count(shown.size, "lecție", "lecții") + " în " + RomanianText.count(groups.size, "unitate", "unități") + "."
                    },
                    style = CapyTheme.type.caption,
                    color = col.textSecondary,
                )
            }
            groups.forEach { (header, list) ->
                val unfolded = unfoldAll || header in open
                item(key = "h-$header") {
                    UnitHeader(
                        header,
                        caption = buildList {
                            add(RomanianText.count(list.size, "lecție", "lecții"))
                            val approved = list.count { it.status == LessonEntity.READY }
                            if (filter != LessonFilter.APPROVED && approved > 0) add("$approved aprobate")
                            val toCheck = list.count { it.status != LessonEntity.READY && it.enriched }
                            if (filter != LessonFilter.APPROVED && toCheck > 0) add("$toCheck de verificat")
                        }.joinToString(" · "),
                        unfolded = unfolded,
                        onToggle = if (unfoldAll) null else ({ open = if (header in open) open - header else open + header }),
                    )
                }
                if (unfolded) {
                    items(list, key = { it.id }) { l ->
                        CapyListRow(
                            title = l.title,
                            caption = lessonCaption(l, l.id in quizIds),
                            leading = { SubjectDot(subjectColor(col, l.subject)) },
                            onClick = { onLesson(l.id) },
                        )
                    }
                    // A whole unit at once, once its questions are ready (the parent can still review each).
                    val approvable = list.filter { it.status != LessonEntity.READY && it.enriched }
                    if (filter == LessonFilter.TO_CHECK && approvable.size > 1) {
                        item(key = "a-$header") {
                            CapyTextButton(
                                if (confirm == header) "Apasă din nou: aprobă ${RomanianText.count(approvable.size, "lecție", "lecții")}"
                                else "Aprobă toate din unitate (${approvable.size})",
                            ) {
                                if (confirm == header) {
                                    confirm = null
                                    vm.approve(approvable.map { it.id })
                                } else {
                                    confirm = header
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A unit: one tappable line that folds and unfolds its lessons. */
@Composable
private fun UnitHeader(title: String, caption: String, unfolded: Boolean, onToggle: (() -> Unit)?) {
    val col = CapyTheme.colors
    Surface(
        color = col.soft,
        contentColor = col.text,
        shape = CapyShapes.card,
        border = BorderStroke(CapyDimens.border, col.line),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CapyDimens.touchTarget)
            .padding(top = CapyDimens.itemGap / 2)
            .clip(CapyShapes.card)
            .then(if (onToggle != null) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier)
            .semantics(mergeDescendants = true) {
                heading()
                if (onToggle != null) stateDescription = if (unfolded) "deschisă" else "închisă"
            },
    ) {
        Row(
            Modifier.padding(horizontal = CapyDimens.screenMargin, vertical = CapyDimens.itemGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = CapyTheme.type.label, color = col.text)
                Text(caption, style = CapyTheme.type.caption, color = col.textSecondary)
            }
            if (onToggle != null) {
                Icon(
                    painterResource(R.drawable.ic_capy_chevron),
                    contentDescription = null,
                    tint = col.textSecondary,
                    modifier = Modifier.size(CapyDimens.icon).rotate(if (unfolded) 270f else 90f),
                )
            }
        }
    }
}
