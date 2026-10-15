package com.capytutor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.capytutor.R
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

/** Answer card states (design system: Answer card, Order / match card). */
enum class AnswerState { DEFAULT, SELECTED, PLACED, RIGHT, NOT_YET, DISABLED }

/**
 * One answer, at least 56 dp. Default: `surface` + line + radio ring. Selected: `soft` + 2 dp
 * `primary`. Placed (order/match): `soft` + a number badge. Right: `right` + checkheart.
 * Not yet: `wrong` + sprout. Disabled: `inactive` with a dashed outline. Colour never carries
 * the meaning alone: right and not yet also say it to TalkBack.
 */
@Composable
fun AnswerCard(
    text: String,
    state: AnswerState,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    leadingIcon: Int? = null,
    detail: String? = null,
    role: Role = Role.RadioButton,
    onClick: (() -> Unit)? = null,
) {
    val c = CapyTheme.colors
    val (bg, fg) = when (state) {
        AnswerState.DEFAULT -> c.surface to c.text
        AnswerState.SELECTED, AnswerState.PLACED -> c.soft to c.text
        AnswerState.RIGHT -> c.right to c.onRight
        AnswerState.NOT_YET -> c.wrong to c.onWrong
        AnswerState.DISABLED -> c.inactive to c.onInactive
    }
    val border: BorderStroke? = when (state) {
        AnswerState.DEFAULT -> BorderStroke(CapyDimens.border, c.line)
        AnswerState.SELECTED -> BorderStroke(CapyDimens.strokeIcon, c.primary)
        else -> null
    }
    val said = when (state) {
        AnswerState.RIGHT -> "corect"
        AnswerState.NOT_YET -> "încă nu"
        AnswerState.SELECTED -> "ales"
        AnswerState.PLACED -> badge?.let { "numărul $it" }
        else -> null
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = CapyShapes.card,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CapyDimens.answerCardMinHeight)
            .then(if (state == AnswerState.DISABLED) Modifier.dashedBorder(c.inactiveOutline, CapyDimens.border, CapyDimens.radiusCard) else Modifier)
            .clip(CapyShapes.card)
            .then(if (onClick != null) Modifier.clickable(enabled = state != AnswerState.DISABLED, role = role, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                selected = state == AnswerState.SELECTED || state == AnswerState.PLACED
                if (said != null) stateDescription = said
            },
    ) {
        Row(
            Modifier.padding(horizontal = CapyDimens.screenMargin, vertical = CapyDimens.itemGap + CapyDimens.border * 4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap + CapyDimens.border * 4),
        ) {
            when {
                badge != null -> NumberBadge(badge)
                state == AnswerState.RIGHT -> StateIcon(R.drawable.ic_capy_checkheart, fg)
                state == AnswerState.NOT_YET -> StateIcon(R.drawable.ic_capy_sprout, fg)
                leadingIcon != null -> StateIcon(leadingIcon, c.secondary)
                role == Role.RadioButton -> RadioRing(state == AnswerState.SELECTED)
                else -> Unit
            }
            Column(Modifier.weight(1f)) {
                Text(text, style = CapyTheme.type.body, color = fg)
                if (detail != null) Text(detail, style = CapyTheme.type.caption, color = fg)
            }
            if (badge != null && (state == AnswerState.RIGHT || state == AnswerState.NOT_YET)) {
                StateIcon(if (state == AnswerState.RIGHT) R.drawable.ic_capy_checkheart else R.drawable.ic_capy_sprout, fg)
            }
        }
    }
}

@Composable
private fun StateIcon(icon: Int, tint: Color) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(CapyDimens.icon))
}

@Composable
private fun RadioRing(on: Boolean) {
    val c = CapyTheme.colors
    Box(
        Modifier
            .size(CapyDimens.icon)
            .border(CapyDimens.strokeIcon, if (on) c.primary else c.controlOutline, CircleShape)
            .padding(CapyDimens.itemGap / 2 + CapyDimens.border),
    ) {
        if (on) Box(Modifier.size(CapyDimens.icon).clip(CircleShape).background(c.primary))
    }
}

/** A number in a `primary` circle: the place given to an item (order) or a pair (match). */
@Composable
fun NumberBadge(n: Int, modifier: Modifier = Modifier) {
    val c = CapyTheme.colors
    Box(
        modifier
            .size(CapyDimens.icon + CapyDimens.itemGap / 2)
            .clip(CircleShape)
            .background(c.primary)
            .semantics { contentDescription = "$n" },
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = CapyTheme.type.label, color = c.onPrimary)
    }
}
