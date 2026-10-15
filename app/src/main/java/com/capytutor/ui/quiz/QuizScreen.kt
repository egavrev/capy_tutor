package com.capytutor.ui.quiz

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capytutor.R
import com.capytutor.core.quiz.QuizAnswer
import com.capytutor.core.quiz.QuizEngine
import com.capytutor.core.quiz.QuizGrader
import com.capytutor.core.quiz.QuizQuestion
import com.capytutor.core.quiz.QuizState
import com.capytutor.core.quiz.QuizType
import com.capytutor.ui.components.AnswerCard
import com.capytutor.ui.components.AnswerState
import com.capytutor.ui.components.BannerKind
import com.capytutor.ui.components.CapyBanner
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyPrimaryButton
import com.capytutor.ui.components.CapySecondaryButton
import com.capytutor.ui.components.CapyTopBar
import com.capytutor.ui.components.FeedbackKind
import com.capytutor.ui.components.FeedbackPanel
import com.capytutor.ui.components.SectionTitle
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

/** What each question type is called, and its icon. */
private fun QuizType.label() = when (this) {
    QuizType.CHOICE -> "Alege răspunsul"
    QuizType.TRUE_FALSE -> "Adevărat sau fals?"
    QuizType.ORDER -> "Pune în ordine"
    QuizType.MATCH -> "Potrivește perechile"
}

private fun QuizType.icon() = when (this) {
    QuizType.CHOICE -> R.drawable.ic_capy_list
    QuizType.TRUE_FALSE -> R.drawable.ic_capy_truefalse
    QuizType.ORDER -> R.drawable.ic_capy_timeline
    QuizType.MATCH -> R.drawable.ic_capy_match
}

/** E11 · Quiz: one question at a time, answered by tapping; "Verifică", then gentle feedback. No timer. */
@Composable
fun QuizScreen(vm: QuizViewModel, onClose: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        CapyTopBar(ui.title.ifBlank { "Quiz" }, onBack = onClose, modifier = Modifier.padding(horizontal = CapyDimens.screenMargin))
        val quiz = ui.quiz
        val s = ui.state
        when {
            ui.loading -> Unit
            ui.missing || quiz == null || s == null -> Column(Modifier.padding(CapyDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(CapyDimens.screenMargin)) {
                CapyBanner("Quiz-ul acestei lecții nu e pregătit încă. Un părinte îl poate pregăti.", BannerKind.NOT_YET)
                CapyPrimaryButton("Înapoi", onClick = onClose)
            }
            s.done -> Results(ui, s, onAgain = vm::again, onDone = onClose)
            else -> {
                val q = ui.question!!
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = CapyDimens.screenMargin),
                    verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap + CapyDimens.border * 4),
                ) {
                    QuizProgress(s.index, s.total)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                        Icon(painterResource(q.type.icon()), contentDescription = null, tint = CapyTheme.colors.secondary, modifier = Modifier.size(CapyDimens.icon))
                        Text("Întrebarea ${s.index + 1} din ${s.total} · ${q.type.label()}", style = CapyTheme.type.caption, color = CapyTheme.colors.textSecondary)
                    }
                    Text(q.prompt, style = CapyTheme.type.question, color = CapyTheme.colors.text, modifier = Modifier.semantics { heading() })
                    when (q) {
                        is QuizQuestion.Choice -> ChoiceBody(vm, ui, q, s)
                        is QuizQuestion.TrueFalse -> TrueFalseBody(vm, ui, q, s)
                        is QuizQuestion.Order -> OrderBody(vm, ui, q, s)
                        is QuizQuestion.Match -> MatchBody(vm, ui, q, s)
                    }
                    Spacer(Modifier.height(CapyDimens.itemGap))
                }
                if (s.checked) {
                    val right = s.results[s.index] == true
                    FeedbackPanel(
                        kind = if (right) FeedbackKind.RIGHT else FeedbackKind.NOT_YET,
                        text = if (right) "Corect! ${q.explanation}" else "Încă nu. Răspunsul este: ${QuizGrader.rightAnswerText(q)}. ${q.explanation}",
                        onContinue = vm::next,
                    )
                } else {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(CapyDimens.screenMargin)) {
                        CapyPrimaryButton("Verifică", enabled = ui.canCheck, onClick = vm::check)
                    }
                }
            }
        }
    }
}

/** 6 dp bar: answered share in `primary`, the rest `line`. */
@Composable
private fun QuizProgress(index: Int, total: Int) {
    val c = CapyTheme.colors
    Box(Modifier.fillMaxWidth().height(CapyDimens.progressBar).clip(CapyShapes.chip).background(c.line)) {
        if (total > 0) {
            Box(Modifier.fillMaxWidth((index + 1).toFloat() / total).height(CapyDimens.progressBar).clip(CapyShapes.chip).background(c.primary))
        }
    }
}

@Composable
private fun ChoiceBody(vm: QuizViewModel, ui: QuizUi, q: QuizQuestion.Choice, s: QuizState) {
    val chosen = (s.lastAnswer as? QuizAnswer.Choice)?.option
    for (i in QuizEngine.shown(q, s.seed, s.index)) {
        val state = when {
            s.checked && i == q.answer -> AnswerState.RIGHT
            s.checked && i == chosen -> AnswerState.NOT_YET
            s.checked -> AnswerState.DEFAULT
            ui.draft.choice == i -> AnswerState.SELECTED
            else -> AnswerState.DEFAULT
        }
        AnswerCard(q.options[i], state, onClick = if (s.checked) null else ({ vm.choose(i) }))
    }
}

@Composable
private fun TrueFalseBody(vm: QuizViewModel, ui: QuizUi, q: QuizQuestion.TrueFalse, s: QuizState) {
    val chosen = (s.lastAnswer as? QuizAnswer.TrueFalse)?.value
    for (value in listOf(true, false)) {
        val state = when {
            s.checked && value == q.answer -> AnswerState.RIGHT
            s.checked && value == chosen -> AnswerState.NOT_YET
            s.checked -> AnswerState.DEFAULT
            ui.draft.truth == value -> AnswerState.SELECTED
            else -> AnswerState.DEFAULT
        }
        AnswerCard(if (value) "Adevărat" else "Fals", state, onClick = if (s.checked) null else ({ vm.judge(value) }))
    }
}

@Composable
private fun OrderBody(vm: QuizViewModel, ui: QuizUi, q: QuizQuestion.Order, s: QuizState) {
    if (s.checked) {
        // The child's order, each place marked right or not yet.
        val order = (s.lastAnswer as? QuizAnswer.Order)?.items.orEmpty()
        order.forEachIndexed { place, item ->
            AnswerCard(q.items[item], if (item == place) AnswerState.RIGHT else AnswerState.NOT_YET, badge = place + 1, role = Role.Button)
        }
        return
    }
    Hint("Atinge-le pe rând, în ordinea bună. Atinge din nou ca să schimbi.")
    for (i in QuizEngine.shown(q, s.seed, s.index)) {
        val place = ui.draft.placed.indexOf(i)
        AnswerCard(
            q.items[i],
            if (place >= 0) AnswerState.PLACED else AnswerState.DEFAULT,
            badge = if (place >= 0) place + 1 else null,
            leadingIcon = R.drawable.ic_capy_drag,
            role = Role.Button,
            onClick = { vm.tapItem(i) },
        )
    }
}

@Composable
private fun MatchBody(vm: QuizViewModel, ui: QuizUi, q: QuizQuestion.Match, s: QuizState) {
    if (s.checked) {
        val rights = (s.lastAnswer as? QuizAnswer.Match)?.rights.orEmpty()
        q.pairs.forEachIndexed { left, (l, _) ->
            val r = rights.getOrNull(left) ?: return@forEachIndexed
            AnswerCard(
                "$l – ${q.pairs[r].second}",
                if (r == left) AnswerState.RIGHT else AnswerState.NOT_YET,
                badge = left + 1,
                role = Role.Button,
            )
        }
        return
    }
    Hint("Atinge un rând de sus, apoi perechea lui de jos. Atinge din nou ca să schimbi.")
    q.pairs.forEachIndexed { left, (l, _) ->
        val state = when {
            ui.draft.pickedLeft == left -> AnswerState.SELECTED
            left in ui.draft.pairs -> AnswerState.PLACED
            else -> AnswerState.DEFAULT
        }
        AnswerCard(l, state, badge = left + 1, role = Role.Button, onClick = { vm.tapLeft(left) })
    }
    SectionTitle("Perechile")
    for (r in QuizEngine.shown(q, s.seed, s.index)) {
        val owner = ui.draft.pairs.entries.firstOrNull { it.value == r }?.key
        AnswerCard(
            q.pairs[r].second,
            if (owner != null) AnswerState.PLACED else AnswerState.DEFAULT,
            badge = owner?.let { it + 1 },
            leadingIcon = R.drawable.ic_capy_link,
            role = Role.Button,
            onClick = { vm.tapRight(r) },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = CapyTheme.type.caption, color = CapyTheme.colors.textSecondary)
}

@Composable
private fun ColumnScope.Results(ui: QuizUi, s: QuizState, onAgain: () -> Unit, onDone: () -> Unit) {
    val c = CapyTheme.colors
    val quiz = ui.quiz ?: return
    val happy = QuizGrader.isHappy(s.correct, s.total)
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = CapyDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap + CapyDimens.border * 4),
    ) {
        CapyCard {
            Image(
                painterResource(if (happy) R.drawable.capy_happy else R.drawable.capy_encouraging),
                contentDescription = null,
                modifier = Modifier.size(CapyDimens.capyHero).align(Alignment.CenterHorizontally),
            )
            Text("${s.correct} din ${s.total}", style = CapyTheme.type.display, color = c.text, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                if (happy) "Bravo! Știi lecția foarte bine." else "Ai lucrat frumos. Mai încercăm când vrei, fără grabă.",
                style = CapyTheme.type.body,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        quiz.questions.forEachIndexed { i, q ->
            AnswerCard(q.prompt, if (s.results[i] == true) AnswerState.RIGHT else AnswerState.NOT_YET, role = Role.Button)
        }
        Spacer(Modifier.height(CapyDimens.itemGap))
    }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(CapyDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
    ) {
        CapySecondaryButton("Refac testul", onClick = onAgain)
        CapyPrimaryButton("Gata", onClick = onDone)
    }
}
