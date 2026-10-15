package com.capytutor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capytutor.R
import com.capytutor.core.reward.Bows
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

/** "1 fundiță", "2 fundițe", "20 de fundițe". */
fun bowsText(n: Int): String = com.capytutor.core.RomanianText.count(n, "fundiță", "fundițe")

/** Bow row on a lesson: up to 3 bows, earned filled, the rest empty. "2 din 3 fundițe". */
@Composable
fun BowRow(earned: Int, modifier: Modifier = Modifier, max: Int = Bows.MAX_PER_LESSON, size: androidx.compose.ui.unit.Dp = CapyDimens.iconSmall) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$earned din $max fundițe" },
        horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap / 4),
    ) {
        repeat(max) { i ->
            Image(
                painterResource(if (i < earned) R.drawable.ic_capy_bow_earned else R.drawable.ic_capy_bow_empty),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Bow counter: a `reward` pill with the bow and the total, 48 dp tall; opens Capy's corner. */
@Composable
fun BowCounter(total: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = CapyTheme.colors
    Surface(
        color = c.reward,
        contentColor = c.onReward,
        shape = CapyShapes.chip,
        modifier = modifier
            .heightIn(min = CapyDimens.touchTarget)
            .clip(CapyShapes.chip)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "${bowsText(total)}. Colțul lui Capy" },
    ) {
        Row(
            Modifier.padding(horizontal = CapyDimens.screenMargin - CapyDimens.itemGap / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap / 2),
        ) {
            Box(Modifier.heightIn(min = CapyDimens.touchTarget), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_capy_bow_earned), contentDescription = null, modifier = Modifier.size(CapyDimens.icon))
            }
            Text("$total", style = CapyTheme.type.label, color = c.onReward)
        }
    }
}

enum class ChildTab(val label: String, val icon: Int) {
    HOME("Acasă", R.drawable.ic_capy_home),
    REVIEW("Recapitulare", R.drawable.ic_capy_review),
    CORNER("Colțul lui Capy", R.drawable.ic_capy_corner),
}

/** Bottom navigation, 72 dp: `surface` + top line; the active tab is a `soft` pill with a `primary` icon. */
@Composable
fun CapyBottomNav(selected: ChildTab, onSelect: (ChildTab) -> Unit) {
    val c = CapyTheme.colors
    Surface(color = c.surface) {
        androidx.compose.foundation.layout.Column {
            androidx.compose.material3.HorizontalDivider(thickness = CapyDimens.border, color = c.line)
            NavigationBar(containerColor = c.surface, tonalElevation = CapyDimens.border * 0, modifier = Modifier.heightIn(min = CapyDimens.bottomNavHeight)) {
                ChildTab.entries.forEach { tab ->
                    val on = tab == selected
                    androidx.compose.material3.NavigationBarItem(
                        selected = on,
                        onClick = { if (!on) onSelect(tab) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(CapyDimens.icon)) },
                        label = {
                            Text(
                                tab.label,
                                style = if (on) CapyTheme.type.label else CapyTheme.type.caption,
                                maxLines = 1,
                            )
                        },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = c.primary,
                            selectedTextColor = c.text,
                            indicatorColor = c.soft,
                            unselectedIconColor = c.textSecondary,
                            unselectedTextColor = c.textSecondary,
                        ),
                    )
                }
            }
        }
    }
}
