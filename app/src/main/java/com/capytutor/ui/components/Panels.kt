package com.capytutor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.capytutor.R
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

/** A content card on the floral paper: surface + 1 dp line, 16 dp radius, no shadow. */
@Composable
fun CapyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = CapyTheme.colors
    Surface(
        color = c.surface,
        contentColor = c.text,
        shape = CapyShapes.card,
        border = BorderStroke(CapyDimens.border, c.line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(CapyDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap), content = content)
    }
}

/** Top bar: optional back arrow, Capy's face or a title, optional trailing icon. */
@Composable
fun CapyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showFace: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = CapyTheme.colors
    Row(modifier.fillMaxWidth().height(CapyDimens.topBarHeight), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(CapyDimens.touchTarget)) {
                Icon(painterResource(R.drawable.ic_capy_back), contentDescription = "Înapoi", tint = c.text, modifier = Modifier.size(CapyDimens.icon))
            }
            Spacer(Modifier.width(CapyDimens.itemGap / 2))
        }
        if (showFace) {
            Image(painterResource(R.drawable.capy_face), contentDescription = null, modifier = Modifier.size(CapyDimens.capyFace))
            Spacer(Modifier.width(CapyDimens.itemGap))
        }
        Text(
            title,
            style = if (showFace) CapyTheme.type.display else CapyTheme.type.title,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

enum class BannerKind { INFO, NOT_YET, OFFLINE }

/** Banners: info = rewardBanner, not yet / problem = Champagne + sprout, offline = soft. */
@Composable
fun CapyBanner(
    text: String,
    kind: BannerKind,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = CapyTheme.colors
    val (bg, fg, icon) = when (kind) {
        BannerKind.INFO -> Triple(c.rewardBanner, c.text, R.drawable.ic_capy_bow)
        BannerKind.NOT_YET -> Triple(c.wrong, c.onWrong, R.drawable.ic_capy_sprout)
        BannerKind.OFFLINE -> Triple(c.soft, c.text, R.drawable.ic_capy_offline)
    }
    Surface(color = bg, contentColor = fg, shape = CapyShapes.card, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(CapyDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(CapyDimens.icon))
                Text(text, style = CapyTheme.type.body, color = fg, modifier = Modifier.weight(1f))
            }
            if (actionText != null && onAction != null) {
                CapySecondaryButton(actionText, onClick = onAction)
            }
        }
    }
}

/** A tappable row on a card: icon, title, optional caption, chevron. 60 dp. */
@Composable
fun CapyListRow(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailingText: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = CapyTheme.colors
    val bg: Color = if (enabled) c.surface else c.inactive
    val fg: Color = if (enabled) c.text else c.onInactive
    Surface(
        color = bg,
        contentColor = fg,
        shape = CapyShapes.card,
        border = if (enabled) BorderStroke(CapyDimens.border, c.line) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CapyDimens.lessonRowHeight)
            .then(if (!enabled) Modifier.dashedBorder(c.inactiveOutline, CapyDimens.border, CapyDimens.radiusCard) else Modifier)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier.padding(horizontal = CapyDimens.screenMargin, vertical = CapyDimens.itemGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap + CapyDimens.border * 4),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = CapyTheme.type.body, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (caption != null) Text(caption, style = CapyTheme.type.caption, color = if (enabled) c.textSecondary else c.onInactive)
            }
            if (trailingText != null) Text(trailingText, style = CapyTheme.type.label, color = if (enabled) c.link else c.onInactive)
            if (onClick != null && enabled) {
                Icon(painterResource(R.drawable.ic_capy_chevron), contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(CapyDimens.icon))
            }
        }
    }
}

/** Section heading on the floral paper. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = CapyTheme.type.label, color = CapyTheme.colors.textSecondary, modifier = modifier.padding(top = CapyDimens.itemGap))
}
