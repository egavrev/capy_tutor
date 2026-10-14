package com.capytutor.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.core.lesson.Subject
import com.capytutor.data.db.LessonEntity
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyListRow
import com.capytutor.ui.components.CapyTopBar
import com.capytutor.ui.components.SectionTitle
import com.capytutor.ui.components.SubjectDot
import com.capytutor.ui.theme.CapyColors
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeLesson(val lesson: LessonEntity, val inProgress: Boolean)

data class HomeUi(val name: String = "", val groups: List<Pair<String, List<HomeLesson>>> = emptyList(), val loaded: Boolean = false)

class HomeViewModel(c: AppContainer) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(c.lessons.observeLessons(), c.sessions.observeOpenLessonIds(), c.settings.settings) { lessons, open, settings ->
        val openSet = open.toSet()
        HomeUi(
            name = settings.childName,
            groups = lessons.groupBy { it.subject }.toList().map { (subject, list) ->
                subject to list.map { HomeLesson(it, it.id in openSet) }
            },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}

/** The subject dot colour. Geografie and Științe have no colour of their own in the design system yet. */
fun subjectColor(c: CapyColors, label: String): Color = when (Subject.fromLabel(label)) {
    Subject.ISTORIE -> c.subjectHistory
    Subject.ROMANA -> c.subjectRomanian
    Subject.MATEMATICA -> c.subjectMath
    Subject.GEOGRAFIE -> c.secondary
    Subject.STIINTE, null -> c.controlOutline
}

/** Acasă: "Bună, Ana!", lessons grouped by subject, the lock for parent mode. */
@Composable
fun HomeScreen(vm: HomeViewModel, onOpenLesson: (Long) -> Unit, onParentMode: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = CapyTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        CapyTopBar(
            title = if (ui.name.isBlank()) stringResource(R.string.home_greeting) else "Bună, ${ui.name}!",
            showFace = true,
            modifier = Modifier.padding(horizontal = CapyDimens.screenMargin),
            trailing = {
                IconButton(onClick = onParentMode, modifier = Modifier.size(CapyDimens.touchTarget)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_capy_lock),
                        contentDescription = stringResource(R.string.home_parent_mode),
                        tint = c.textSecondary,
                        modifier = Modifier.size(CapyDimens.icon),
                    )
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = CapyDimens.screenMargin, end = CapyDimens.screenMargin, bottom = CapyDimens.blockGap),
            verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
        ) {
            if (ui.loaded && ui.groups.isEmpty()) {
                item { EmptyLessons() }
            }
            ui.groups.forEach { (subject, lessons) ->
                item(key = "s-$subject") { SectionTitle(subject) }
                items(lessons, key = { it.lesson.id }) { l ->
                    val ready = l.lesson.status == LessonEntity.READY
                    CapyListRow(
                        title = l.lesson.title,
                        caption = when {
                            !ready -> "de verificat de un părinte"
                            l.inProgress -> "Continuăm de unde am rămas"
                            else -> "Gata de povestit"
                        },
                        enabled = ready,
                        leading = { SubjectDot(subjectColor(c, subject)) },
                        onClick = { onOpenLesson(l.lesson.id) },
                    )
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun EmptyLessons() {
    val c = CapyTheme.colors
    CapyCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
            Image(painter = painterResource(R.drawable.capy_reading), contentDescription = null, modifier = Modifier.size(CapyDimens.capyHero))
            Text(stringResource(R.string.home_empty_title), style = CapyTheme.type.title, color = c.text, textAlign = TextAlign.Center)
            Text(stringResource(R.string.home_empty_body), style = CapyTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
        }
    }
}
