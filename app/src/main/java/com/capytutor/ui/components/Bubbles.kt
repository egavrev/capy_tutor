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

/** Capy speaks from the left, with her face next to the bubble. */
@Composable
fun CapyBubble(text: String, modifier: Modifier = Modifier) {
    val c = CapyTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val max = maxWidth * CapyDimens.bubbleMaxWidth
        Row(verticalAlignment = Alignment.Bottom) {
            Image(
                painter = painterResource(R.drawable.capy_face),
                contentDescription = null,
                modifier = Modifier.size(CapyDimens.capyBubbleAvatar),
            )
            Spacer(Modifier.width(CapyDimens.itemGap))
            Surface(
                color = c.surface,
                contentColor = c.text,
                shape = CapyShapes.capyBubble,
                border = BorderStroke(CapyDimens.border, c.line),
                modifier = Modifier.widthIn(max = max),
            ) {
                Text(
                    text,
                    style = CapyTheme.type.body,
                    color = c.text,
                    modifier = Modifier.padding(horizontal = CapyDimens.screenMargin, vertical = CapyDimens.itemGap + CapyDimens.border * 2),
                )
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
