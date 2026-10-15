package com.capytutor.ui.parent

import android.content.Intent
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.ai.groq.GroqCheck
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapySecondaryButton
import com.capytutor.ui.components.CapyTextButton
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyTheme
import com.capytutor.voice.AndroidSpeechStt.OnDevice
import com.capytutor.voice.AndroidTts
import com.capytutor.voice.hasMicPermission
import com.capytutor.voice.openAppSettings

private enum class Ok { YES, NO, WAIT }


@Composable
private fun CheckRow(title: String, detail: String, ok: Ok, action: (@Composable () -> Unit)? = null) {
    val c = CapyTheme.colors
    CapyCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap + CapyDimens.border * 4)) {
            Icon(
                painterResource(
                    when (ok) {
                        Ok.YES -> R.drawable.ic_capy_checkheart
                        Ok.NO -> R.drawable.ic_capy_sprout
                        Ok.WAIT -> R.drawable.ic_capy_clock
                    },
                ),
                contentDescription = when (ok) {
                    Ok.YES -> "Merge"
                    Ok.NO -> "Încă nu"
                    Ok.WAIT -> "Se verifică"
                },
                tint = when (ok) {
                    Ok.YES -> c.onRight
                    Ok.NO -> c.onWrong
                    Ok.WAIT -> c.textSecondary
                },
                modifier = Modifier.size(CapyDimens.icon),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap / 2)) {
                Text(title, style = CapyTheme.type.label, color = c.text)
                Text(detail, style = CapyTheme.type.body, color = c.textSecondary)
            }
        }
        action?.invoke()
    }
}

/** "Verificare telefon" (decision 3): what this phone can do for Capy. */
@Composable
fun PhoneCheckScreen(c: AppContainer, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var groq by remember { mutableStateOf<GroqCheck.Result?>(null) }
    var onDevice by remember { mutableStateOf<OnDevice?>(null) }
    val ttsStatus by c.tts.status.collectAsStateWithLifecycle()
    val mic = ctx.hasMicPermission()

    LaunchedEffect(round) {
        groq = null
        onDevice = null
        groq = GroqCheck.run(c.transport, c.settings.groqKey)
        onDevice = c.deviceStt.romanianOnDevice(refresh = true)
    }

    ParentPage("Verificare telefon", onBack) {
        CheckRow(
            "Telefonul",
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}",
            if (Build.VERSION.SDK_INT >= 26) Ok.YES else Ok.NO,
        )
        CheckRow(
            "Microfonul",
            if (mic) "Permis. Capy poate asculta." else "Nu e permis încă. Copilul e întrebat la prima apăsare pe microfon; îl poți permite și din setări.",
            if (mic) Ok.YES else Ok.NO,
        ) {
            if (!mic) CapyTextButton("Deschide setările aplicației") { ctx.openAppSettings() }
        }
        CheckRow(
            "Groq",
            when (groq) {
                null -> "Se verifică…"
                GroqCheck.Result.OK -> "Răspunde, iar cheia e bună."
                GroqCheck.Result.NO_KEY -> "Nu e nicio cheie. Adaug-o în Setări."
                GroqCheck.Result.BAD_KEY -> "Cheia nu e acceptată. Verific-o în Setări."
                GroqCheck.Result.RATE_LIMITED -> "Răspunde, dar limita gratuită e atinsă acum. Revine singur."
                GroqCheck.Result.OFFLINE -> "Nu răspunde: nu e internet sau Groq nu poate fi atins."
                GroqCheck.Result.ERROR -> "Răspunde cu o eroare. Mai încearcă peste puțin."
            },
            when (groq) {
                null -> Ok.WAIT
                GroqCheck.Result.OK, GroqCheck.Result.RATE_LIMITED -> Ok.YES
                else -> Ok.NO
            },
        )
        CheckRow(
            "Vocea lui Capy în română",
            when (ttsStatus) {
                AndroidTts.Status.LOADING -> "Se verifică…"
                AndroidTts.Status.READY -> "Vocea română e instalată."
                AndroidTts.Status.NO_ROMANIAN -> "Telefonul nu are vocea română. Instaleaz-o din setările de citire a textului (Google sau Samsung)."
                AndroidTts.Status.NO_ENGINE -> "Telefonul nu are un motor de citire a textului."
            },
            when (ttsStatus) {
                AndroidTts.Status.LOADING -> Ok.WAIT
                AndroidTts.Status.READY -> Ok.YES
                else -> Ok.NO
            },
        ) {
            when (ttsStatus) {
                AndroidTts.Status.READY -> CapySecondaryButton("Ascultă vocea") { c.tts.say("Bună! Eu sunt Capy. Povestește-mi ce ții minte.") }
                AndroidTts.Status.NO_ROMANIAN -> CapyTextButton("Instalează vocea") {
                    runCatching {
                        ctx.startActivity(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                else -> Unit
            }
        }
        // The phone's recogniser is only the backup: Groq does the listening (decision 4).
        val online = c.deviceStt.isAvailable()
        CheckRow(
            "Rezervă: recunoașterea vocală a telefonului",
            when {
                onDevice == null -> "Se verifică…"
                onDevice == OnDevice.INSTALLED -> "Româna e instalată pe telefon. Când Groq nu poate asculta, Capy ascultă cu telefonul, chiar și fără internet."
                online && onDevice == OnDevice.DOWNLOADABLE -> "Merge cu internet: când Groq e ocupat, Capy ascultă cu telefonul. Descarcă româna ca să meargă și fără internet."
                online -> "Merge cu internet: când Groq e ocupat, Capy ascultă cu telefonul. Fără internet, copilul scrie răspunsul."
                onDevice == OnDevice.DOWNLOADABLE -> "Se poate descărca. Fără ea, când Groq nu poate asculta, copilul scrie răspunsul."
                else -> "Telefonul nu are recunoaștere vocală. Când Groq nu poate asculta, copilul scrie răspunsul."
            },
            when {
                onDevice == null -> Ok.WAIT
                onDevice == OnDevice.INSTALLED || online -> Ok.YES
                else -> Ok.NO
            },
        ) {
            if (onDevice == OnDevice.DOWNLOADABLE && Build.VERSION.SDK_INT >= 33) {
                CapyTextButton("Descarcă limba română") {
                    runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx).triggerModelDownload(c.deviceStt.intent()) }
                }
            }
        }
        CapySecondaryButton("Verifică din nou") { round++ }
    }
}
