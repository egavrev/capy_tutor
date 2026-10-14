package com.capytutor.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.data.settings.ChildGender
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyPrimaryButton
import com.capytutor.ui.components.CapyTextButton
import com.capytutor.ui.components.CapyTextField
import com.capytutor.ui.components.CapyTopBar
import com.capytutor.ui.components.ChoicePills
import com.capytutor.ui.components.PinPad
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SetupStep { WELCOME, CHILD, PIN, KEY }

/** First launch, for the parent: the child's name, a PIN for parent mode, the Groq key (optional). */
@Composable
fun SetupScreen(c: AppContainer, onDone: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(SetupStep.WELCOME) }
    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf(ChildGender.UNSAID) }
    var key by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = CapyDimens.screenMargin)) {
        CapyTopBar(
            title = "Capy Tutore",
            showFace = true,
            onBack = when (step) {
                SetupStep.WELCOME -> null
                SetupStep.CHILD -> ({ step = SetupStep.WELCOME })
                SetupStep.PIN -> ({ step = SetupStep.CHILD })
                SetupStep.KEY -> null
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CapyDimens.blockGap),
        ) {
            when (step) {
                SetupStep.WELCOME -> {
                    CapyCard {
                        Image(painterResource(R.drawable.capy_happy), contentDescription = null, modifier = Modifier.size(CapyDimens.capyHero).align(Alignment.CenterHorizontally))
                        Text("Bun venit!", style = CapyTheme.type.title, color = CapyTheme.colors.text, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text(
                            "Mai întâi, câteva lucruri pentru părinte: numele copilului, un PIN pentru modul părinte și cheia Groq.",
                            style = CapyTheme.type.body, color = CapyTheme.colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CapyPrimaryButton("Să începem") { step = SetupStep.CHILD }
                }
                SetupStep.CHILD -> {
                    CapyCard {
                        Text("Cum se numește copilul?", style = CapyTheme.type.question, color = CapyTheme.colors.text)
                        CapyTextField(name, { name = it.take(30) }, "Numele copilului")
                        Text("Numele rămâne doar pe telefon. Capy îl folosește doar ca să salute.", style = CapyTheme.type.caption, color = CapyTheme.colors.textSecondary)
                    }
                    CapyCard {
                        Text("Capy scrie despre", style = CapyTheme.type.label, color = CapyTheme.colors.text)
                        ChoicePills(ChildGender.entries.map { it to it.label }, gender, { gender = it })
                        Text(
                            "Doar ca să scrie corect rezumatul pentru părinte („a lucrat singură”). Numele nu pleacă niciodată de pe telefon.",
                            style = CapyTheme.type.caption, color = CapyTheme.colors.textSecondary,
                        )
                    }
                    CapyPrimaryButton("Mai departe", enabled = name.isNotBlank()) { step = SetupStep.PIN }
                }
                SetupStep.PIN -> NewPin(onSet = { pin ->
                    scope.launch {
                        c.settings.setChildName(name)
                        c.settings.setGender(gender)
                        c.settings.setPin(pin)
                        step = SetupStep.KEY
                    }
                })
                SetupStep.KEY -> {
                    CapyCard {
                        Text("Cheia Groq", style = CapyTheme.type.question, color = CapyTheme.colors.text)
                        CapyTextField(key, { key = it.trim() }, "Cheia Groq API", secret = true, keyboardType = KeyboardType.Password)
                        Text(
                            "Capy o folosește ca să înțeleagă ce povestește copilul. Se păstrează criptată, doar pe telefon. O poți adăuga și mai târziu, în Setări.",
                            style = CapyTheme.type.caption, color = CapyTheme.colors.textSecondary,
                        )
                    }
                    CapyPrimaryButton("Gata", enabled = key.length >= 20) {
                        scope.launch {
                            c.settings.setGroqKey(key)
                            c.settings.finishSetup()
                            onDone()
                        }
                    }
                    CapyTextButton("Mai târziu", modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        scope.launch {
                            c.settings.finishSetup()
                            onDone()
                        }
                    }
                }
            }
            Spacer(Modifier.height(CapyDimens.blockGap))
        }
    }
}

/** Choose a 4-digit PIN, then type it again. */
@Composable
fun NewPin(onSet: (String) -> Unit) {
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            delay(150)
            val f = first
            when {
                f == null -> { first = pin; pin = ""; mismatch = false }
                f == pin -> onSet(pin)
                else -> { mismatch = true; first = null; pin = "" }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(CapyDimens.screenMargin)) {
        Text(
            if (first == null) "Alege un PIN de 4 cifre pentru modul părinte" else "Scrie PIN-ul încă o dată",
            style = CapyTheme.type.question, color = CapyTheme.colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        PinPad(
            pin = pin,
            onPinChange = { pin = it; if (it.isNotEmpty()) mismatch = false },
            wrong = mismatch,
            message = if (mismatch) "PIN-urile nu se potrivesc. Mai încearcă o dată." else "Copilul nu îl vede. Ține-l minte.",
        )
    }
}
