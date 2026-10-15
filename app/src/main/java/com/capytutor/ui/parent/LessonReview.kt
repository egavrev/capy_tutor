package com.capytutor.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.data.db.KeyIdeaEntity
import com.capytutor.data.db.LessonEntity
import com.capytutor.data.repo.LessonRepository
import com.capytutor.ui.components.BannerKind
import com.capytutor.ui.components.CapyBanner
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyListRow
import com.capytutor.ui.components.CapyPrimaryButton
import com.capytutor.ui.components.CapySecondaryButton
import com.capytutor.ui.components.CapyTextButton
import com.capytutor.ui.components.CapyTextField
import com.capytutor.ui.components.CapyTopBar
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.capytutor.core.RomanianText
import com.capytutor.core.quiz.Quiz
import com.capytutor.core.quiz.QuizGrader
import com.capytutor.core.quiz.QuizType
import com.capytutor.data.repo.QuizRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LessonReviewViewModel(private val c: AppContainer, val lessonId: Long) : ViewModel() {
    val lesson: StateFlow<LessonEntity?> = c.lessons.observeLesson(lessonId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ideas: StateFlow<List<KeyIdeaEntity>> = c.lessons.observeIdeas(lessonId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    /** E11: the lesson's quiz, read back through the same checks as when it was saved. */
    val quiz: StateFlow<QuizView> = c.quizzes.observeRaw(lessonId)
        .map { raw -> QuizView(loaded = true, quiz = raw?.let { c.quizzes.quiz(lessonId) }, source = raw?.source) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuizView())

    fun makeQuiz() {
        if (busy.value) return
        busy.value = true
        message.value = "Capy pregătește quiz-ul…"
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { c.quizzes.regenerate(lessonId) }
            message.value = err ?: "Quiz-ul e gata. Îl găsești mai jos."
            busy.value = false
        }
    }

    fun regenerate() {
        if (busy.value) return
        busy.value = true
        message.value = "Capy pregătește întrebările…"
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { c.lessons.regenerate(lessonId) }
            message.value = err ?: "Întrebările sunt gata. Verifică-le mai jos."
            busy.value = false
        }
    }

    fun save(idea: KeyIdeaEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) { c.lessons.updateIdea(idea) }
        message.value = "Ideea ${idea.ord + 1} e salvată."
    }

    fun setReady(ready: Boolean) = viewModelScope.launch { withContext(Dispatchers.IO) { c.lessons.setReady(lessonId, ready) } }

    fun delete(done: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) { c.lessons.delete(lessonId) }
        done()
    }
}

/** Review and edit the key ideas and everything generated for them, then mark the lesson ready. */
@Composable
fun LessonReviewScreen(vm: LessonReviewViewModel, onBack: () -> Unit, onHistory: () -> Unit) {
    val lesson by vm.lesson.collectAsStateWithLifecycle()
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val quiz by vm.quiz.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val col = CapyTheme.colors
    val l = lesson
    val complete = ideas.isNotEmpty() && ideas.all { it.checkQuestion.isNotBlank() && it.checkAnswer.isNotBlank() && it.broadQuestion.isNotBlank() }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        CapyTopBar(l?.title ?: "Lecția", onBack = onBack, modifier = Modifier.padding(horizontal = CapyDimens.screenMargin))
        if (l == null) return@Column
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = CapyDimens.screenMargin, end = CapyDimens.screenMargin, bottom = CapyDimens.blockGap),
            verticalArrangement = Arrangement.spacedBy(CapyDimens.screenMargin),
        ) {
            item {
                CapyCard {
                    Text("${l.subject}${l.grade?.let { " · clasa a $it-a" }.orEmpty()}${l.chapter?.let { " · $it" }.orEmpty()}", style = CapyTheme.type.caption, color = col.textSecondary)
                    Text(
                        when {
                            l.status == LessonEntity.READY -> "Lecția e gata pentru copil."
                            !l.enriched -> "Întrebările nu sunt generate încă."
                            else -> "Verifică ideile și întrebările, apoi marchează lecția gata."
                        },
                        style = CapyTheme.type.body,
                        color = col.text,
                    )
                    if (l.status == LessonEntity.READY) {
                        CapySecondaryButton("Pune înapoi la verificat") { vm.setReady(false) }
                    } else {
                        CapyPrimaryButton("Lecția e gata", enabled = complete && !busy) { vm.setReady(true) }
                    }
                    CapySecondaryButton(if (l.enriched) "Generează din nou întrebările" else "Generează întrebările", enabled = !busy) { vm.regenerate() }
                }
            }
            message?.let { m -> item { CapyBanner(m, if (busy || m.endsWith("salvată.") || m.contains(" e gata") || m.contains(" sunt gata")) BannerKind.INFO else BannerKind.NOT_YET) } }
            if (l.enriched) {
                item {
                    Text(
                        "Verifică mai ales întrebarea de test: să nu conțină deja răspunsul și să aibă un singur răspuns scurt.",
                        style = CapyTheme.type.caption,
                        color = col.textSecondary,
                    )
                }
            }
            items(ideas, key = { it.id }) { idea -> IdeaEditor(idea, enabled = !busy, onSave = vm::save) }
            item { QuizCard(quiz, enabled = !busy && l.enriched, onMake = vm::makeQuiz) }
            item { CapyListRow("Istoric", caption = "Sesiunile și răspunsurile copilului", leading = { RowIcon(R.drawable.ic_capy_history) }, onClick = onHistory) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CapyTextButton(if (confirmDelete) "Apasă din nou ca să ștergi lecția" else "Șterge lecția") {
                        if (confirmDelete) vm.delete(onBack) else confirmDelete = true
                    }
                }
            }
        }
    }
}

data class QuizView(val loaded: Boolean = false, val quiz: Quiz? = null, val source: String? = null)

/** The lesson's quiz, read-only: each question with its right answer. Made again with Groq on request. */
@Composable
private fun QuizCard(v: QuizView, enabled: Boolean, onMake: () -> Unit) {
    val col = CapyTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    if (!v.loaded) return
    CapyCard {
        Text("Quiz (Recapitulare)", style = CapyTheme.type.caption, color = col.textSecondary)
        val q = v.quiz
        if (q == null) {
            Text(
                "Lecția nu are încă quiz. Adaugă un fișier „.quiz.json” lângă lecție, sau lasă-l pe Capy să-l pregătească.",
                style = CapyTheme.type.body,
                color = col.text,
            )
            CapySecondaryButton("Pregătește quiz-ul", enabled = enabled, onClick = onMake)
            return@CapyCard
        }
        val kinds = q.questions.groupingBy { it.type }.eachCount().entries.joinToString(", ") { (t, n) ->
            "$n " + when (t) {
                QuizType.CHOICE -> "cu variante"
                QuizType.TRUE_FALSE -> "adevărat/fals"
                QuizType.ORDER -> "de ordonat"
                QuizType.MATCH -> "de potrivit"
            }
        }
        Text(
            "${RomanianText.count(q.questions.size, "întrebare", "întrebări")}: $kinds. " +
                if (v.source == QuizRepository.SOURCE_PREPARED) "Pregătit dintr-un fișier." else "Pregătit de Capy cu Groq.",
            style = CapyTheme.type.body,
            color = col.text,
        )
        if (open) {
            q.questions.forEachIndexed { i, question ->
                Text("${i + 1}. ${question.prompt}", style = CapyTheme.type.label, color = col.text)
                Text("Răspuns: ${QuizGrader.rightAnswerText(question)}", style = CapyTheme.type.body, color = col.textSecondary)
            }
            CapyTextButton("Închide", modifier = Modifier.align(Alignment.Start)) { open = false }
        } else {
            CapyTextButton("Vezi întrebările", modifier = Modifier.align(Alignment.Start)) { open = true }
        }
        CapyTextButton(if (confirm) "Apasă din nou: Capy face alt quiz cu Groq" else "Refă quiz-ul", enabled = enabled, modifier = Modifier.align(Alignment.Start)) {
            if (confirm) { confirm = false; onMake() } else confirm = true
        }
    }
}

@Composable
private fun IdeaEditor(idea: KeyIdeaEntity, enabled: Boolean, onSave: (KeyIdeaEntity) -> Unit) {
    val col = CapyTheme.colors
    var text by rememberSaveable(idea) { mutableStateOf(idea.text) }
    var keywords by rememberSaveable(idea) { mutableStateOf(LessonRepository.keywordsToText(idea.keywords)) }
    var broad by rememberSaveable(idea) { mutableStateOf(idea.broadQuestion) }
    var clue by rememberSaveable(idea) { mutableStateOf(idea.clue) }
    var explanation by rememberSaveable(idea) { mutableStateOf(idea.explanation) }
    var question by rememberSaveable(idea) { mutableStateOf(idea.checkQuestion) }
    var answer by rememberSaveable(idea) { mutableStateOf(idea.checkAnswer) }
    var open by rememberSaveable(idea.id) { mutableStateOf(false) }
    val edited = idea.copy(
        text = text.trim(),
        keywords = LessonRepository.keywordsFromText(keywords),
        broadQuestion = broad.trim(),
        clue = clue.trim(),
        explanation = explanation.trim(),
        checkQuestion = question.trim(),
        checkAnswer = answer.trim(),
    )
    val dirty = edited != idea
    LaunchedEffect(idea) { if (idea.checkQuestion.isBlank()) open = true }

    CapyCard {
        Text("Ideea ${idea.ord + 1}", style = CapyTheme.type.caption, color = col.textSecondary)
        Text(idea.text, style = CapyTheme.type.body, color = col.text)
        if (!open) {
            Text("Test: ${idea.checkQuestion.ifBlank { "—" }}", style = CapyTheme.type.label, color = col.text)
            Text("Răspuns: ${idea.checkAnswer.ifBlank { "—" }}", style = CapyTheme.type.body, color = col.textSecondary)
            CapyTextButton("Vezi și editează tot", modifier = Modifier.align(Alignment.Start)) { open = true }
        } else {
            val next = ImeAction.Next
            CapyTextField(text, { text = it }, "Ideea (scrisă de părinte)", singleLine = false, imeAction = next)
            CapyTextField(keywords, { keywords = it }, "Cuvinte-cheie: un grup pe rând, variante cu /", singleLine = false, minLines = 2, imeAction = ImeAction.Default)
            CapyTextField(broad, { broad = it }, "Pasul 1: întrebare largă", singleLine = false, imeAction = next)
            CapyTextField(clue, { clue = it }, "Pasul 2: indiciu (fără răspuns)", singleLine = false, imeAction = next)
            CapyTextField(explanation, { explanation = it }, "Pasul 3: explicația lui Capy", singleLine = false, imeAction = next)
            CapyTextField(question, { question = it }, "Întrebarea de test", singleLine = false, imeAction = next)
            CapyTextField(answer, { answer = it }, "Răspunsul așteptat", singleLine = false)
            CapySecondaryButton("Salvează ideea", enabled = enabled && dirty && edited.text.isNotBlank()) { onSave(edited) }
            CapyTextButton("Închide", modifier = Modifier.align(Alignment.Start)) { open = false }
        }
    }
}
