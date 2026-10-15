package com.capytutor.ui.recap

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.data.db.LessonEntity
import com.capytutor.data.repo.QuizBest
import com.capytutor.ui.components.BowCounter
import com.capytutor.ui.components.CapyBottomNav
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyListRow
import com.capytutor.ui.components.CapyTopBar
import com.capytutor.ui.components.ChildTab
import com.capytutor.ui.components.SectionTitle
import com.capytutor.ui.components.SubjectDot
import com.capytutor.ui.home.subjectColor
import com.capytutor.ui.parent.lessonsByChapter
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RecapLesson(val lesson: LessonEntity, val best: QuizBest?)

data class RecapUi(val groups: List<Pair<String, List<RecapLesson>>> = emptyList(), val bows: Int = 0, val loaded: Boolean = false)

class RecapViewModel(c: AppContainer) : ViewModel() {
    val ui: StateFlow<RecapUi> = combine(c.lessons.observeLessons(), c.quizzes.observeLessonIds(), c.quizzes.observeBest(), c.rewards.observe()) { lessons, withQuiz, best, rewards ->
        val ids = withQuiz.toSet()
        val ready = lessons.filter { it.status == LessonEntity.READY && it.id in ids }
        RecapUi(
            groups = lessonsByChapter(ready).map { (h, list) -> h to list.map { RecapLesson(it, best[it.id]) } },
            bows = rewards.total,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUi())
}

/** Recapitulare (E11): every ready lesson that has a quiz; play it as often as she likes. */
@Composable
fun RecapScreen(vm: RecapViewModel, onQuiz: (Long) -> Unit, onTab: (ChildTab) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = CapyTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).statusBarsPadding()) {
            CapyTopBar(
                "Recapitulare",
                modifier = Modifier.padding(horizontal = CapyDimens.screenMargin),
                trailing = { BowCounter(ui.bows, onClick = { onTab(ChildTab.CORNER) }) },
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = CapyDimens.screenMargin, end = CapyDimens.screenMargin, bottom = CapyDimens.blockGap),
                verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
            ) {
                if (ui.loaded && ui.groups.isEmpty()) {
                    item {
                        CapyCard {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                                Image(painterResource(R.drawable.capy_sleepy), contentDescription = null, modifier = Modifier.size(CapyDimens.capyHero))
                                Text("Încă nu e nimic de recapitulat", style = CapyTheme.type.title, color = c.text, textAlign = TextAlign.Center)
                                Text("Quiz-urile apar aici după ce un părinte le pregătește.", style = CapyTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else if (ui.loaded) {
                    item { Text("Alege o lecție și răspunde la câteva întrebări. Fără grabă.", style = CapyTheme.type.body, color = c.textSecondary) }
                }
                ui.groups.forEach { (header, lessons) ->
                    item(key = "h-$header") { SectionTitle(header) }
                    items(lessons, key = { it.lesson.id }) { r ->
                        CapyListRow(
                            title = r.lesson.title,
                            caption = r.best?.let { "Cel mai bun rezultat: ${it.correct} din ${it.total}" } ?: "de recapitulat",
                            leading = { SubjectDot(subjectColor(c, r.lesson.subject)) },
                            onClick = { onQuiz(r.lesson.id) },
                        )
                    }
                }
            }
        }
        CapyBottomNav(ChildTab.REVIEW, onTab)
    }
}
