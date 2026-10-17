package com.capytutor.ui.reading

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.core.lesson.Term
import com.capytutor.core.reading.BlockKind
import com.capytutor.core.reading.Reading
import com.capytutor.core.reading.ReadingBlock
import com.capytutor.core.reading.ReadingIdea
import com.capytutor.ui.components.BannerKind
import com.capytutor.ui.components.CapyBanner
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyPrimaryButton
import com.capytutor.ui.components.CapySecondaryButton
import com.capytutor.ui.components.CapyTopBar
import com.capytutor.ui.components.SectionTitle
import com.capytutor.ui.components.StepProgress
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme
import com.capytutor.voice.AndroidTts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReadingUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val title: String = "",
    val caption: String = "",
    val blocks: List<ReadingBlock> = emptyList(),
    val terms: List<Term> = emptyList(),
    val keyCount: Int = 0,
)

/** E14: the lesson to read before retelling, its key sentences marked, read aloud on request. */
class ReadingViewModel(private val c: AppContainer, lessonId: Long) : ViewModel() {
    private val _ui = MutableStateFlow(ReadingUi())
    val ui: StateFlow<ReadingUi> = _ui

    init {
        viewModelScope.launch {
            val (lesson, pack) = withContext(Dispatchers.IO) { c.lessons.parsed(lessonId) to c.lessons.pack(lessonId) }
            if (lesson == null) {
                _ui.value = ReadingUi(loading = false, missing = true)
                return@launch
            }
            // The parent may have edited the ideas in review: use the saved ones, with their keywords.
            val ideas = pack?.ideas?.map { ReadingIdea(it.id, it.text, it.keywords) }
                ?: lesson.ideas.mapIndexed { i, t -> ReadingIdea(lesson.ideaId(i), t) }
            val blocks = withContext(Dispatchers.Default) { Reading.build(lesson.text, ideas) }
            _ui.value = ReadingUi(
                loading = false,
                title = lesson.title,
                caption = listOfNotNull(lesson.subject.label, lesson.chapter).joinToString(" · "),
                blocks = blocks,
                terms = lesson.terms,
                keyCount = blocks.sumOf { b -> b.sentences.count { it.isKey } },
            )
        }
    }

    fun readAll() = c.tts.sayAll(_ui.value.blocks.map { it.text })

    fun read(block: ReadingBlock) {
        if (c.tts.speaking.value == block.text) c.stopVoice() else c.tts.say(block.text)
    }

    fun stop() = c.stopVoice()

    override fun onCleared() = c.stopVoice()
}

@Composable
fun ReadingScreen(c: AppContainer, vm: ReadingViewModel, onBack: () -> Unit, onDone: (() -> Unit)?) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val col = CapyTheme.colors
    val ttsStatus by c.tts.status.collectAsStateWithLifecycle()
    val speaking by c.tts.speaking.collectAsStateWithLifecycle()
    val canRead = ttsStatus == AndroidTts.Status.READY
    DisposableEffect(Unit) { onDispose { vm.stop() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        CapyTopBar(ui.title.ifBlank { "Lecția" }, onBack = onBack, modifier = Modifier.padding(horizontal = CapyDimens.screenMargin))
        if (ui.loading) return@Column
        if (ui.missing) {
            Column(Modifier.padding(CapyDimens.screenMargin)) {
                CapyBanner("Lecția nu mai poate fi citită aici. Un părinte o poate adăuga din nou.", BannerKind.NOT_YET)
            }
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = CapyDimens.screenMargin, end = CapyDimens.screenMargin, bottom = CapyDimens.blockGap),
            verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap + CapyDimens.border * 4),
        ) {
            if (onDone != null) item { StepProgress(current = 0) }
            item {
                CapyCard {
                    if (ui.caption.isNotBlank()) Text(ui.caption, style = CapyTheme.type.caption, color = col.textSecondary)
                    Text(
                        if (ui.keyCount > 0) "Citește lecția în liniște. Propozițiile colorate și îngroșate sunt ideile cheie: pe ele le vom căuta împreună." else "Citește lecția în liniște, fără grabă.",
                        style = CapyTheme.type.body,
                        color = col.text,
                    )
                    if (canRead) {
                        if (speaking != null) {
                            CapySecondaryButton("Oprește citirea", onClick = vm::stop)
                        } else {
                            CapySecondaryButton("Ascultă lecția", onClick = vm::readAll)
                        }
                    }
                }
            }
            itemsIndexed(ui.blocks) { _, block -> Block(block, reading = speaking == block.text, onRead = if (canRead && block.kind != BlockKind.HEADING) ({ vm.read(block) }) else null) }
            if (ui.terms.isNotEmpty()) {
                item { SectionTitle("Termeni") }
                item {
                    CapyCard {
                        ui.terms.forEach { t ->
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.term) }
                                    append(": ")
                                    append(t.definition)
                                },
                                style = CapyTheme.type.body,
                                color = col.text,
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(CapyDimens.screenMargin)) {
            // Before the retelling: on to it. Read again later (from Capy's questions or a quiz):
            // back to the same place.
            if (onDone != null) CapyPrimaryButton("Am citit, hai să povestesc", onClick = onDone)
            else CapySecondaryButton("Înapoi la Capy", onClick = onBack)
        }
    }
}

/**
 * A paragraph on a card, key sentences in `soft` and bold (colour never carries the meaning
 * alone). While it is read aloud it gets the 2 dp `mic` border, like a Capy bubble.
 */
@Composable
private fun Block(block: ReadingBlock, reading: Boolean, onRead: (() -> Unit)?) {
    val col = CapyTheme.colors
    if (block.kind == BlockKind.HEADING) {
        Text(block.text, style = CapyTheme.type.title, color = col.text, modifier = Modifier.padding(top = CapyDimens.itemGap).semantics { heading() })
        return
    }
    val keys = block.sentences.count { it.isKey }
    Surface(
        color = col.surface,
        contentColor = col.text,
        shape = CapyShapes.card,
        border = if (reading) BorderStroke(CapyDimens.strokeIcon, col.mic) else BorderStroke(CapyDimens.border, col.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = CapyDimens.screenMargin, top = CapyDimens.itemGap, bottom = CapyDimens.itemGap), verticalAlignment = Alignment.Top) {
            Text(
                buildAnnotatedString {
                    if (block.kind == BlockKind.LIST_ITEM) append("•  ")
                    block.sentences.forEachIndexed { i, s ->
                        if (i > 0) append(" ")
                        if (s.isKey) {
                            withStyle(SpanStyle(background = col.highlight, fontWeight = FontWeight.SemiBold)) { append(s.text) }
                        } else {
                            append(s.text)
                        }
                    }
                },
                style = CapyTheme.type.body,
                color = col.text,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = CapyDimens.itemGap / 2, end = if (onRead == null) CapyDimens.screenMargin else CapyDimens.border * 0)
                    .semantics {
                        if (keys > 0) contentDescription = block.text + if (keys == 1) " (are o idee cheie)" else " (are $keys idei cheie)"
                    },
            )
            if (onRead != null) {
                IconButton(onClick = onRead, modifier = Modifier.size(CapyDimens.touchTarget)) {
                    Icon(
                        painterResource(if (reading) R.drawable.ic_capy_pause else R.drawable.ic_capy_speaker),
                        contentDescription = if (reading) "Oprește citirea" else "Citește cu voce tare",
                        tint = if (reading) col.mic else col.secondary,
                        modifier = Modifier.size(CapyDimens.icon),
                    )
                }
            }
        }
    }
}
