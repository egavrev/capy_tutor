package com.capytutor.ui.corner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.core.reward.CornerItem
import com.capytutor.data.repo.Rewards
import com.capytutor.ui.components.BowCounter
import com.capytutor.ui.components.BowRow
import com.capytutor.ui.components.CapyBottomNav
import com.capytutor.ui.components.CapyCard
import com.capytutor.ui.components.CapyTopBar
import com.capytutor.ui.components.ChildTab
import com.capytutor.ui.components.SectionTitle
import com.capytutor.ui.components.bowsText
import com.capytutor.ui.components.dashedBorder
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CornerViewModel(c: AppContainer) : ViewModel() {
    val rewards: StateFlow<Rewards?> = c.rewards.observe().map<Rewards, Rewards?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Colțul lui Capy (E12): a quiet reward. The bows she earned, a shelf with every finished lesson,
 * and cozy things that appear in Capy's corner as the bows add up. Nothing is ever taken away.
 */
@Composable
fun CornerScreen(vm: CornerViewModel, onTab: (ChildTab) -> Unit) {
    val r by vm.rewards.collectAsStateWithLifecycle()
    val c = CapyTheme.colors
    val total = r?.total ?: 0
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).statusBarsPadding()) {
            CapyTopBar("Colțul lui Capy", modifier = Modifier.padding(horizontal = CapyDimens.screenMargin), trailing = { BowCounter(total) })
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = CapyDimens.screenMargin, end = CapyDimens.screenMargin, bottom = CapyDimens.blockGap),
                verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
            ) {
                item {
                    CapyCard {
                        Image(
                            painterResource(if (total > 0) R.drawable.capy_happy else R.drawable.capy_neutral),
                            contentDescription = null,
                            modifier = Modifier.size(CapyDimens.capyHero).align(Alignment.CenterHorizontally),
                        )
                        Text(
                            if (total == 0) "Aici ține Capy fundițele tale." else "Ai strâns ${bowsText(total)} pentru Capy!",
                            style = CapyTheme.type.title,
                            color = c.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            when (val next = CornerItem.next(total)) {
                                null -> "Colțul lui Capy e gata, cu tot ce-i trebuie. Mulțumesc!"
                                else -> "Încă ${bowsText(next.second)} și în colț apare: ${next.first.label.replaceFirstChar { it.lowercase() }}."
                            },
                            style = CapyTheme.type.body,
                            color = c.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item { SectionTitle("Lucrurile din colț") }
                item { ItemGrid(total) }
                item { SectionTitle("Raftul cu fundițe") }
                val shelf = r?.shelf.orEmpty()
                if (r != null && shelf.isEmpty()) {
                    item {
                        Text(
                            "Termină o lecție, iar Capy pune aici fundițele ei: una pentru că ai terminat-o, încă una sau două pentru răspunsurile bune.",
                            style = CapyTheme.type.body,
                            color = c.textSecondary,
                        )
                    }
                }
                items(shelf, key = { it.lessonId }) { s ->
                    CapyCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                            Text(s.title, style = CapyTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                            BowRow(s.bows, size = CapyDimens.icon)
                        }
                    }
                }
            }
        }
        CapyBottomNav(ChildTab.CORNER, onTab)
    }
}

private fun CornerItem.iconRes(): Int = when (this) {
    CornerItem.CUSHION -> R.drawable.ic_capy_cushion
    CornerItem.CUP -> R.drawable.ic_capy_cup
    CornerItem.PLANT -> R.drawable.ic_capy_plant
    CornerItem.LAMP -> R.drawable.ic_capy_lamp
    CornerItem.SHELF -> R.drawable.ic_capy_shelf
    CornerItem.WINDOW -> R.drawable.ic_capy_window
}

/** Two columns of corner things: unlocked = `soft` + icon in `primary`; still to come = `inactive`, dashed, with the bows needed. */
@Composable
private fun ItemGrid(total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
        CornerItem.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                pair.forEach { item -> ItemTile(item, total >= item.bows, item.iconRes(), Modifier.weight(1f)) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ItemTile(item: CornerItem, unlocked: Boolean, icon: Int, modifier: Modifier) {
    val c = CapyTheme.colors
    Surface(
        color = if (unlocked) c.soft else c.inactive,
        contentColor = if (unlocked) c.text else c.onInactive,
        shape = CapyShapes.card,
        border = if (unlocked) BorderStroke(CapyDimens.border, c.line) else null,
        modifier = modifier
            .heightIn(min = CapyDimens.answerCardMinHeight * 2)
            .then(if (!unlocked) Modifier.dashedBorder(c.inactiveOutline, CapyDimens.border, CapyDimens.radiusCard) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = if (unlocked) "${item.label}, în colțul lui Capy" else "${item.label}, apare la ${bowsText(item.bows)}"
            },
    ) {
        Column(
            Modifier.padding(CapyDimens.screenMargin - CapyDimens.itemGap / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap / 2),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = if (unlocked) c.primary else c.onInactive, modifier = Modifier.size(CapyDimens.icon + CapyDimens.itemGap))
            Text(item.label, style = CapyTheme.type.label, color = if (unlocked) c.text else c.onInactive, textAlign = TextAlign.Center)
            if (!unlocked) Text("la ${bowsText(item.bows)}", style = CapyTheme.type.caption, color = c.onInactive, textAlign = TextAlign.Center)
        }
    }
}
