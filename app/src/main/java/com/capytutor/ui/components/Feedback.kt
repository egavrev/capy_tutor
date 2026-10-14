package com.capytutor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.capytutor.R
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

enum class FeedbackKind { RIGHT, NOT_YET, NEUTRAL }

/**
 * The test feedback sheet, docked at the bottom: right = pink "Corect!" + checkheart,
 * not yet = Champagne "Încă nu. Răspunsul este: …" + sprout. Then "Mai departe".
 */
@Composable
fun FeedbackPanel(
    kind: FeedbackKind,
    text: String,
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
    onDispute: (() -> Unit)? = null,
) {
    val c = CapyTheme.colors
    val (bg, fg) = when (kind) {
        FeedbackKind.RIGHT -> c.right to c.onRight
        FeedbackKind.NOT_YET -> c.wrong to c.onWrong
        FeedbackKind.NEUTRAL -> c.rewardBanner to c.text
    }
    Surface(color = bg, contentColor = fg, shape = CapyShapes.bottomSheet, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.navigationBarsPadding().padding(CapyDimens.blockGap),
            verticalArrangement = Arrangement.spacedBy(CapyDimens.screenMargin),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap + CapyDimens.border * 4),
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                val icon = when (kind) {
                    FeedbackKind.RIGHT -> R.drawable.ic_capy_checkheart
                    FeedbackKind.NOT_YET -> R.drawable.ic_capy_sprout
                    FeedbackKind.NEUTRAL -> R.drawable.ic_capy_hint
                }
                Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(CapyDimens.icon))
                Text(text, style = CapyTheme.type.question, color = fg, modifier = Modifier.weight(1f))
            }
            CapyPrimaryButton("Mai departe", onClick = onContinue)
            if (onDispute != null) {
                CapyTextButton("Cred că am dreptate", modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onDispute)
            }
        }
    }
}
