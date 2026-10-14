package com.capytutor.ui.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capytutor.R
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.schema.CoverageStatus
import com.capytutor.core.session.CapyMood
import com.capytutor.core.session.CheckResult
import com.capytutor.core.session.Input
import com.capytutor.core.session.Outcome
import com.capytutor.core.session.PauseReason
import com.capytutor.core.session.Phase
import com.capytutor.core.session.SessionState
import com.capytutor.core.session.Speaker
import com.capytutor.ui.components.AnswerInput
import com.capytutor.ui.components.BannerKind
import com.capytutor.ui.components.CapyBanner
import com.capytutor.ui.components.CapyBubble
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyPrimaryButton
import com.capytutor.ui.components.CapyTextButton
import com.capytutor.ui.components.ChildBubble
import com.capytutor.ui.components.ChildBubbleState
import com.capytutor.ui.components.ChipState
import com.capytutor.ui.components.FeedbackKind
import com.capytutor.ui.components.FeedbackPanel
import com.capytutor.ui.components.IdeaChips
import com.capytutor.ui.components.StepProgress
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyTheme

@Composable
fun SessionScreen(vm: SessionViewModel, onClose: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.state
    val pack = ui.pack
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().height(CapyDimens.topBarHeight).padding(horizontal = CapyDimens.screenMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pack?.title.orEmpty(),
                style = CapyTheme.type.title,
                color = CapyTheme.colors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(CapyDimens.touchTarget)) {
                Icon(painterResource(R.drawable.ic_capy_close), contentDescription = "Închide (se salvează)", tint = CapyTheme.colors.text, modifier = Modifier.size(CapyDimens.icon))
            }
        }
        when {
            ui.missing -> Missing(onClose)
            s == null || pack == null -> Unit
            else -> {
                StepProgress(step(s.phase), Modifier.padding(horizontal = CapyDimens.screenMargin))
                Spacer(Modifier.height(CapyDimens.itemGap))
                when (s.phase) {
                    Phase.INTRO -> Intro(pack, Modifier.weight(1f))
                    Phase.SUMMARY, Phase.DONE -> Summary(pack, s, Modifier.weight(1f))
                    else -> Chat(pack, s, ui.thinking, Modifier.weight(1f))
                }
                BottomArea(vm, ui, s, onClose)
            }
        }
    }
}

private fun step(p: Phase) = when (p) {
    Phase.INTRO -> 0
    Phase.RETELLING, Phase.ASSESSING -> 1
    Phase.COVERAGE, Phase.HINTS -> 2
    Phase.TEST, Phase.SUMMARIZING, Phase.SUMMARY, Phase.DONE -> 3
}

@Composable
private fun Missing(onClose: () -> Unit) {
    Column(Modifier.padding(CapyDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(CapyDimens.blockGap)) {
        CapyBanner("Lecția nu mai e aici. Un părinte o poate adăuga din nou.", BannerKind.NOT_YET)
        CapyPrimaryButton("Înapoi acasă", onClick = onClose)
    }
}

@Composable
private fun Intro(pack: LessonPack, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = CapyDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(CapyDimens.blockGap),
    ) {
        Spacer(Modifier.height(CapyDimens.itemGap))
        CapyCard {
            Image(
                painterResource(R.drawable.capy_reading),
                contentDescription = null,
                modifier = Modifier.size(CapyDimens.capyHero).align(Alignment.CenterHorizontally),
            )
            Text("Lecția e gata", style = CapyTheme.type.caption, color = CapyTheme.colors.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(pack.title, style = CapyTheme.type.title, color = CapyTheme.colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                "Are ${pack.ideas.size} idei cheie. Povestește-mi ce ții minte, apoi le căutăm împreună pe celelalte.",
                style = CapyTheme.type.body,
                color = CapyTheme.colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CapyDimens.itemGap))
            IdeaChips(pack.ideas.indices.map { "Ideea ${it + 1}" to ChipState.EMPTY })
        }
    }
}

@Composable
private fun Chat(pack: LessonPack, s: SessionState, thinking: Boolean, modifier: Modifier) {
    val list = rememberLazyListState()
    val lastChild = s.turns.indexOfLast { it.speaker == Speaker.CHILD }
    val extra = (if (s.phase == Phase.COVERAGE) 1 else 0) + (if (thinking || s.pending != null && s.paused == null) 1 else 0)
    val count = s.turns.size + extra
    LaunchedEffect(count, s.feedback) { if (count > 0) list.animateScrollToItem(count - 1) }
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CapyDimens.screenMargin, vertical = CapyDimens.itemGap),
        verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap + CapyDimens.border * 4),
    ) {
        itemsIndexed(s.turns) { i, t ->
            if (t.speaker == Speaker.CAPY) {
                CapyBubble(t.text)
            } else {
                val fb = s.feedback
                val state = when {
                    i != lastChild || s.phase != Phase.TEST || fb == null -> ChildBubbleState.PLAIN
                    fb.result == CheckResult.CORRECT -> ChildBubbleState.RIGHT
                    fb.result == CheckResult.NOT_YET -> ChildBubbleState.NOT_YET
                    else -> ChildBubbleState.PLAIN
                }
                ChildBubble(t.text, state = state)
            }
        }
        if (s.phase == Phase.COVERAGE) {
            item {
                CapyCard {
                    Text("Ce ai ținut minte", style = CapyTheme.type.label, color = CapyTheme.colors.textSecondary)
                    val texts = pack.ideas.associate { it.id to it.text }
                    IdeaChips(
                        s.results.map { r ->
                            texts[r.ideaId].orEmpty() to if (r.retellStatus == CoverageStatus.COVERED) ChipState.KNOWN else ChipState.PRACTISE
                        },
                    )
                }
            }
        }
        if (thinking || (s.pending != null && s.paused == null)) {
            item { CapyBubble("Capy se gândește…") }
        }
    }
}

@Composable
private fun Summary(pack: LessonPack, s: SessionState, modifier: Modifier) {
    val c = CapyTheme.colors
    val happy = s.mood == CapyMood.HAPPY
    val texts = pack.ideas.associate { it.id to it.text }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = CapyDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(CapyDimens.screenMargin),
    ) {
        CapyCard {
            Image(
                painterResource(if (happy) R.drawable.capy_happy else R.drawable.capy_encouraging),
                contentDescription = null,
                modifier = Modifier.size(CapyDimens.capyHero).align(Alignment.CenterHorizontally),
            )
            Text("${s.score} din ${s.results.size}", style = CapyTheme.type.display, color = c.text, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("răspunsuri bune la test", style = CapyTheme.type.caption, color = c.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (s.childSummary.isNotBlank()) {
                Text(s.childSummary, style = CapyTheme.type.body, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        val alone = s.results.filter { it.outcome == Outcome.RECALLED_ALONE }
        val hint = s.results.filter { it.outcome == Outcome.RECALLED_WITH_HINT }
        val explained = s.results.filter { it.outcome == Outcome.EXPLAINED }
        if (alone.isNotEmpty()) OutcomeGroup("Ți-ai amintit fără ajutor", alone.map { texts[it.ideaId].orEmpty() }, ChipState.KNOWN)
        if (hint.isNotEmpty()) OutcomeGroup("Ți-ai amintit cu un indiciu", hint.map { texts[it.ideaId].orEmpty() }, ChipState.KNOWN)
        if (explained.isNotEmpty()) OutcomeGroup("Le-am descoperit împreună", explained.map { texts[it.ideaId].orEmpty() }, ChipState.PRACTISE)
        Spacer(Modifier.height(CapyDimens.itemGap))
    }
}

@Composable
private fun OutcomeGroup(title: String, ideas: List<String>, state: ChipState) {
    CapyCard {
        Text(title, style = CapyTheme.type.label, color = CapyTheme.colors.textSecondary)
        IdeaChips(ideas.map { it to state })
    }
}

@Composable
private fun BottomArea(vm: SessionViewModel, ui: SessionUi, s: SessionState, onClose: () -> Unit) {
    val pad = Modifier.fillMaxWidth().navigationBarsPadding().padding(CapyDimens.screenMargin)
    if (s.paused != null) {
        Column(pad) {
            CapyBanner(
                if (s.paused == PauseReason.OFFLINE) "Nu e internet acum. Totul e salvat; continuăm când revine." else "Capy e puțin obosit. Totul e salvat; continuăm când vrei.",
                if (s.paused == PauseReason.OFFLINE) BannerKind.OFFLINE else BannerKind.INFO,
                actionText = "Continuăm",
                onAction = { vm.send(Input.Resume) },
            )
        }
        return
    }
    if (ui.thinking || s.pending != null) return
    when (s.phase) {
        Phase.INTRO -> Column(pad) { CapyPrimaryButton("Hai să începem") { vm.send(Input.Continue) } }
        Phase.RETELLING -> Column(pad, verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
            AnswerInput(ui.draft, vm::onDraft, vm::sendDraft, placeholder = "Scrie ce ții minte…")
            CapyPrimaryButton("Gata, am terminat", enabled = s.retelling.isNotBlank() || ui.draft.isNotBlank(), onClick = vm::finishRetelling)
            if (s.retelling.isBlank()) {
                CapyTextButton("Nu-mi amintesc nimic", modifier = Modifier.align(Alignment.CenterHorizontally)) { vm.send(Input.DontKnow) }
            }
        }
        Phase.COVERAGE -> Column(pad) {
            CapyPrimaryButton(if (s.gaps.isEmpty()) "Hai la test" else "Hai să le căutăm împreună") { vm.send(Input.Continue) }
        }
        Phase.HINTS -> Column(pad, verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
            AnswerInput(ui.draft, vm::onDraft, vm::sendDraft)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CapyTextButton("Nu-mi amintesc") { vm.send(Input.DontKnow) }
                if (s.disputable != null) CapyTextButton("Cred că am dreptate") { vm.send(Input.Dispute) }
            }
        }
        Phase.TEST -> {
            val f = s.feedback
            if (f != null) {
                FeedbackPanel(
                    kind = when (f.result) {
                        CheckResult.CORRECT -> FeedbackKind.RIGHT
                        CheckResult.NOT_YET -> FeedbackKind.NOT_YET
                        CheckResult.UNJUDGED -> FeedbackKind.NEUTRAL
                    },
                    text = f.text,
                    onContinue = { vm.send(Input.Continue) },
                    onDispute = if (s.disputable == f.ideaId) ({ vm.send(Input.Dispute) }) else null,
                )
            } else {
                Column(pad, verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                    AnswerInput(ui.draft, vm::onDraft, vm::sendDraft)
                    CapyTextButton("Nu știu", modifier = Modifier.align(Alignment.CenterHorizontally)) { vm.send(Input.DontKnow) }
                }
            }
        }
        Phase.SUMMARY -> Column(pad) { CapyPrimaryButton("Gata pe azi") { vm.send(Input.Continue) } }
        Phase.DONE -> Column(pad) { CapyPrimaryButton("Înapoi acasă", onClick = onClose) }
        Phase.ASSESSING, Phase.SUMMARIZING -> Unit
    }
}
