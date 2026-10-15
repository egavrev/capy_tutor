package com.capytutor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.res.painterResource
import com.capytutor.R
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

/**
 * Capy speaks from the left, with her face next to the bubble. With [onSpeak], a 48 dp speaker
 * button reads the text aloud; while it is read the bubble gets a 2 dp mic border.
 */
@Composable
fun CapyBubble(
    text: String,
    modifier: Modifier = Modifier,
    speaking: Boolean = false,
    onSpeak: (() -> Unit)? = null,
    avatar: Int = R.drawable.capy_face,
) {
    val c = CapyTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val max = maxWidth * CapyDimens.bubbleMaxWidth
        Row(verticalAlignment = Alignment.Bottom) {
            Image(
                painter = painterResource(avatar),
                contentDescription = null,
                modifier = Modifier.size(CapyDimens.capyBubbleAvatar),
            )
            Spacer(Modifier.width(CapyDimens.itemGap))
            Surface(
                color = c.surface,
                contentColor = c.text,
                shape = CapyShapes.capyBubble,
                border = if (speaking) BorderStroke(CapyDimens.strokeIcon, c.mic) else BorderStroke(CapyDimens.border, c.line),
                modifier = Modifier.widthIn(max = max),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text,
                        style = CapyTheme.type.body,
                        color = c.text,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(
                                start = CapyDimens.screenMargin,
                                end = if (onSpeak != null) CapyDimens.border else CapyDimens.screenMargin,
                                top = CapyDimens.itemGap + CapyDimens.border * 2,
                                bottom = CapyDimens.itemGap + CapyDimens.border * 2,
                            ),
                    )
                    if (onSpeak != null) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(CapyDimens.touchTarget)) {
                            Icon(
                                painterResource(if (speaking) R.drawable.ic_capy_pause else R.drawable.ic_capy_speaker),
                                contentDescription = if (speaking) "Oprește vocea lui Capy" else "Ascultă ce spune Capy",
                                tint = if (speaking) c.mic else c.textSecondary,
                                modifier = Modifier.size(CapyDimens.iconSmall),
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class ChildBubbleState { PLAIN, RIGHT, NOT_YET }

/** The child answers from the right. Right = pink + checkheart; not yet = Champagne + sprout. */
@Composable
fun ChildBubble(
    text: String,
    modifier: Modifier = Modifier,
    state: ChildBubbleState = ChildBubbleState.PLAIN,
    onDispute: (() -> Unit)? = null,
) {
    val c = CapyTheme.colors
    val (bg, fg) = when (state) {
        ChildBubbleState.PLAIN -> c.childBubble to c.text
        ChildBubbleState.RIGHT -> c.right to c.onRight
        ChildBubbleState.NOT_YET -> c.wrong to c.onWrong
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val max = maxWidth * CapyDimens.bubbleMaxWidth
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Surface(color = bg, contentColor = fg, shape = CapyShapes.childBubble, modifier = Modifier.widthIn(max = max)) {
                Row(
                    Modifier.padding(horizontal = CapyDimens.screenMargin, vertical = CapyDimens.itemGap + CapyDimens.border * 2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
                ) {
                    when (state) {
                        ChildBubbleState.RIGHT -> Icon(painterResource(R.drawable.ic_capy_checkheart), contentDescription = "Corect", tint = fg, modifier = Modifier.size(CapyDimens.iconSmall))
                        ChildBubbleState.NOT_YET -> Icon(painterResource(R.drawable.ic_capy_sprout), contentDescription = "Încă nu", tint = fg, modifier = Modifier.size(CapyDimens.iconSmall))
                        ChildBubbleState.PLAIN -> Unit
                    }
                    Text(text, style = CapyTheme.type.body, color = fg)
                }
            }
            if (onDispute != null) CapyTextButton("Cred că am dreptate", onClick = onDispute)
        }
    }
}
