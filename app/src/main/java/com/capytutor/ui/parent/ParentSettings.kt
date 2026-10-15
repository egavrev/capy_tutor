package com.capytutor.ui.parent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.core.RomanianText
import com.capytutor.data.db.UsageTotal
import com.capytutor.data.settings.ChildGender
import com.capytutor.ui.components.BannerKind
import com.capytutor.ui.components.CapyBanner
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyListRow
import com.capytutor.ui.components.CapySecondaryButton
import com.capytutor.ui.components.CapyTextButton
import com.capytutor.ui.components.CapyTextField
import com.capytutor.ui.components.ChoicePills
import com.capytutor.ui.setup.NewPin
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.launch

@Composable
fun ParentSettingsScreen(c: AppContainer, onBack: () -> Unit, onChangePin: () -> Unit) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.loaded) { if (s.loaded) name = s.childName }
    val col = CapyTheme.colors

    ParentPage("Setări", onBack) {
        saved?.let { CapyBanner(it, BannerKind.INFO) }
        CapyCard {
            Text("Copilul", style = CapyTheme.type.label, color = col.text)
            CapyTextField(name, { name = it.take(30) }, "Numele copilului")
            CapySecondaryButton("Salvează numele", enabled = name.isNotBlank() && name.trim() != s.childName) {
                scope.launch { c.settings.setChildName(name); saved = "Numele e salvat." }
            }
            Text("Capy scrie despre", style = CapyTheme.type.label, color = col.text)
            ChoicePills(ChildGender.entries.map { it to it.label }, s.gender, { g -> scope.launch { c.settings.setGender(g) } })
            Text("Doar cuvântul („o fată”, „un băiat”, „un copil”) ajunge la Groq, pentru rezumat. Numele, niciodată.", style = CapyTheme.type.caption, color = col.textSecondary)
        }
        CapyCard {
            Text("Cheia Groq", style = CapyTheme.type.label, color = col.text)
            Text(
                if (s.hasGroqKey) "Cheie salvată: …${s.groqKeyLast4}" else "Nicio cheie salvată. Fără ea, Capy nu poate verifica povestirea.",
                style = CapyTheme.type.body,
                color = if (s.hasGroqKey) col.text else col.textSecondary,
            )
            CapyTextField(key, { key = it.trim() }, if (s.hasGroqKey) "Cheie nouă" else "Cheia Groq API", secret = true, keyboardType = KeyboardType.Password)
            CapySecondaryButton("Salvează cheia", enabled = key.length >= 20) {
                scope.launch { c.settings.setGroqKey(key); key = ""; saved = "Cheia e salvată, criptată, doar pe telefon." }
            }
            if (s.hasGroqKey) {
                CapyTextButton("Șterge cheia") { scope.launch { c.settings.setGroqKey(""); saved = "Cheia a fost ștearsă." } }
            }
        }
        CapyCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.toggleable(value = s.voiceOn, role = Role.Switch) { on -> scope.launch { c.settings.setVoiceOn(on) } },
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Vocea lui Capy", style = CapyTheme.type.label, color = col.text)
                    Text("Lângă fiecare mesaj al lui Capy apare un difuzor: copilul îl apasă și Capy citește cu voce tare.", style = CapyTheme.type.caption, color = col.textSecondary)
                    if (s.voiceOn) {
                        val tts by c.tts.status.collectAsStateWithLifecycle()
                        if (tts == com.capytutor.voice.AndroidTts.Status.NO_ROMANIAN || tts == com.capytutor.voice.AndroidTts.Status.NO_ENGINE) {
                            Text("Telefonul nu are vocea română, așa că difuzorul nu apare. Vezi „Verificare telefon”.", style = CapyTheme.type.caption, color = col.onWrong)
                        }
                    }
                }
                Switch(
                    checked = s.voiceOn,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = col.onPrimary, checkedTrackColor = col.primary,
                        uncheckedThumbColor = col.controlOutline, uncheckedTrackColor = col.surface, uncheckedBorderColor = col.controlOutline,
                    ),
                )
            }
        }
        CapyListRow("Schimbă PIN-ul", leading = { RowIcon(R.drawable.ic_capy_lock) }, onClick = onChangePin)
    }
}

@Composable
fun ChangePinScreen(c: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    ParentPage("PIN nou", onBack) {
        NewPin(onSet = { pin -> scope.launch { c.settings.setPin(pin); onBack() } })
    }
}

@Composable
fun UsageScreen(c: AppContainer, onBack: () -> Unit) {
    val rows by remember { c.usage.observeToday() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val col = CapyTheme.colors
    ParentPage("Folosire azi", onBack) {
        val total = UsageTotal(
            "total", rows.sumOf { it.requests }, rows.sumOf { it.inputTokens }, rows.sumOf { it.outputTokens },
            rows.sumOf { it.audioSeconds }, rows.sumOf { it.rateLimitedCount },
        )
        CapyCard {
            Text("Azi, în total", style = CapyTheme.type.label, color = col.textSecondary)
            Text("${RomanianText.count(total.requests, "cerere", "cereri")} · ${RomanianText.count(total.inputTokens + total.outputTokens, "token", "tokeni")}", style = CapyTheme.type.title, color = col.text)
            Text(
                "Secunde audio: ${total.audioSeconds} · Pauze din cauza limitei: ${total.rateLimitedCount}",
                style = CapyTheme.type.caption, color = col.textSecondary,
            )
        }
        if (rows.isEmpty()) {
            Text("Nicio cerere azi.", style = CapyTheme.type.body, color = col.textSecondary)
        }
        rows.forEach { r ->
            CapyCard {
                Text(callTypeName(r.callType), style = CapyTheme.type.label, color = col.text)
                Text(
                    "${RomanianText.count(r.requests, "cerere", "cereri")} · ${RomanianText.count(r.inputTokens, "token trimis", "tokeni trimiși")} · ${r.outputTokens} primiți" +
                        if (r.rateLimitedCount > 0) " · ${r.rateLimitedCount} limitate" else "",
                    style = CapyTheme.type.body, color = col.textSecondary,
                )
            }
        }
        Text(
            "Planul gratuit Groq are limite pe minut și pe zi. Când sunt atinse, Capy face o pauză și sesiunea continuă mai târziu.",
            style = CapyTheme.type.caption, color = col.textSecondary,
        )
    }
}

private fun callTypeName(t: String) = when (t) {
    "enrich_ideas" -> "Pregătirea lecțiilor"
    "assess_recall" -> "Verificarea povestirii"
    "assess_answer" -> "Verificarea răspunsurilor"
    "summarize_session" -> "Rezumate"
    "transcribe" -> "Transcriere voce"
    else -> t
}
