package com.capytutor.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.core.RomanianText
import com.capytutor.core.session.CheckResult
import com.capytutor.core.session.Outcome
import com.capytutor.core.session.SessionCodec
import com.capytutor.data.db.IdeaResultEntity
import com.capytutor.data.db.SessionEntity
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.ChoicePills
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(val session: SessionEntity, val results: List<IdeaResultEntity>, val parentNote: String)

class HistoryViewModel(private val c: AppContainer, lessonId: Long) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<HistoryItem>> = combine(c.sessions.observeSessions(lessonId), refresh) { s, _ -> s }
        .mapLatest { sessions ->
            sessions.map { s ->
                val note = runCatching { SessionCodec.decode(s.state).parentNote }.getOrDefault("")
                HistoryItem(s, c.sessions.results(s.id).sortedBy { it.ideaKey.drop(1).toIntOrNull() ?: 0 }, note)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun decide(sessionId: Long, ideaKey: String, accept: Boolean) = viewModelScope.launch {
        c.sessions.decide(sessionId, ideaKey, accept)
        refresh.value++
    }
}

private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ro"))

/** Sessions for one lesson: score, per-idea results, disputed answers to accept or reject. */
@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val col = CapyTheme.colors
    ParentPage("Istoric", onBack) {
        if (items.isEmpty()) Text("Nicio sesiune încă.", style = CapyTheme.type.body, color = col.textSecondary)
        items.forEach { item ->
            val s = item.session
            CapyCard {
                Text(dateFormat.format(Date(s.startedAt)), style = CapyTheme.type.caption, color = col.textSecondary)
                Text(
                    if (s.endedAt != null) "${s.score} din ${s.total} la test" else "Neterminată",
                    style = CapyTheme.type.title,
                    color = col.text,
                )
                Text("${RomanianText.count(s.callsUsed, "cerere", "cereri")} · ${RomanianText.count(s.tokensUsed, "token", "tokeni")}", style = CapyTheme.type.caption, color = col.textSecondary)
                if (item.parentNote.isNotBlank()) Text(item.parentNote, style = CapyTheme.type.body, color = col.text)
                item.results.forEach { r -> ResultRow(r) { accept -> vm.decide(s.id, r.ideaKey, accept) } }
            }
        }
    }
}

@Composable
private fun ResultRow(r: IdeaResultEntity, onDecide: (Boolean) -> Unit) {
    val col = CapyTheme.colors
    val right = r.checkResult == CheckResult.CORRECT.name
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap / 2)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
            Icon(
                painterResource(if (right) R.drawable.ic_capy_checkheart else R.drawable.ic_capy_sprout),
                contentDescription = if (right) "Corect la test" else "Încă nu la test",
                tint = if (right) col.onRight else col.onWrong,
                modifier = Modifier.size(CapyDimens.iconSmall),
            )
            Column(Modifier.weight(1f)) {
                Text(r.ideaText, style = CapyTheme.type.body, color = col.text)
                Text(describe(r), style = CapyTheme.type.caption, color = col.textSecondary)
            }
        }
        if (r.childDisputed) {
            Text(
                when (r.parentDecision) {
                    "accepted" -> "Copilul a spus „Cred că am dreptate”. Ai acceptat."
                    "rejected" -> "Copilul a spus „Cred că am dreptate”. Nu ai acceptat."
                    else -> "Copilul a spus „Cred că am dreptate”. Ce zici?"
                },
                style = CapyTheme.type.label,
                color = col.text,
            )
            ChoicePills(
                listOf("accepted" to "Accept", "rejected" to "Nu accept"),
                r.parentDecision ?: "",
                { onDecide(it == "accepted") },
            )
        }
    }
}

private fun describe(r: IdeaResultEntity): String {
    val recall = when (r.outcome) {
        Outcome.RECALLED_ALONE.name -> "amintită fără ajutor"
        Outcome.RECALLED_WITH_HINT.name -> "amintită cu ${if (r.hintsUsed >= 2) "indiciu" else "o întrebare"}"
        Outcome.EXPLAINED.name -> "explicată de Capy"
        else -> "neterminată"
    }
    val test = when (r.checkResult) {
        CheckResult.CORRECT.name -> "corect la test"
        CheckResult.NOT_YET.name -> "încă nu la test"
        CheckResult.UNJUDGED.name -> "test neverificat"
        else -> "fără test"
    }
    return "$recall · $test"
}
