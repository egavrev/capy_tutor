package com.capytutor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.capytutor.R
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

enum class ChipState { EMPTY, KNOWN, PRACTISE, CURRENT }

/** Idea chip: known = pink + checkheart, to practise = Champagne + sprout, empty = Linen dashed. */
@Composable
fun IdeaChip(text: String, state: ChipState, modifier: Modifier = Modifier) {
    val c = CapyTheme.colors
    val (bg, fg) = when (state) {
        ChipState.KNOWN -> c.right to c.onRight
        ChipState.PRACTISE -> c.wrong to c.onWrong
        ChipState.EMPTY -> c.inactive to c.onInactive
        ChipState.CURRENT -> c.surface to c.text
    }
    val stateWord = when (state) {
        ChipState.KNOWN -> "știută"
        ChipState.PRACTISE -> "de exersat"
        ChipState.EMPTY -> ""
        ChipState.CURRENT -> "acum"
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = CapyShapes.chip,
        border = if (state == ChipState.CURRENT) BorderStroke(CapyDimens.strokeIcon, c.reward) else null,
        modifier = modifier
            .heightIn(min = CapyDimens.chipHeight)
            .then(if (state == ChipState.EMPTY) Modifier.dashedBorder(c.inactiveOutline, CapyDimens.border, CapyDimens.chipHeight / 2) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = if (stateWord.isEmpty()) text else "$text, $stateWord" },
    ) {
        Row(
            Modifier.padding(horizontal = CapyDimens.screenMargin - CapyDimens.border * 4, vertical = CapyDimens.itemGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
        ) {
            val icon = when (state) {
                ChipState.KNOWN -> R.drawable.ic_capy_checkheart
                ChipState.PRACTISE -> R.drawable.ic_capy_sprout
                ChipState.CURRENT -> R.drawable.ic_capy_hint
                ChipState.EMPTY -> null
            }
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = if (state == ChipState.CURRENT) c.secondary else fg, modifier = Modifier.size(CapyDimens.iconSmall))
            }
            Text(text, style = CapyTheme.type.label, color = fg)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeaChips(chips: List<Pair<String, ChipState>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
        verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
    ) {
        chips.forEach { (text, state) -> IdeaChip(text, state) }
    }
}

/** The 4 steps of a session: Lecție · Povestește · Întrebări · Test. [current] is 0-based. */
@Composable
fun StepProgress(current: Int, modifier: Modifier = Modifier) {
    val c = CapyTheme.colors
    val steps = listOf("Lecție", "Povestește", "Întrebări", "Test")
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Pasul ${current + 1} din 4: ${steps[current]}" }) {
        Row(horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
            steps.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(CapyDimens.progressBar)
                        .clip(CapyShapes.chip)
                        .background(if (i <= current) c.primary else c.line),
                )
            }
        }
        Row(Modifier.padding(top = CapyDimens.itemGap / 2), horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
            steps.forEachIndexed { i, s ->
                Text(
                    s,
                    style = if (i == current) CapyTheme.type.caption.copy(fontWeight = FontWeight.Bold) else CapyTheme.type.caption,
                    color = if (i == current) c.text else c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 10 dp subject dot. */
@Composable
fun SubjectDot(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(modifier.size(CapyDimens.subjectDot).clip(CircleShape).background(color))
}
