package com.capytutor.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.capytutor.R
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

/**
 * Acasă. In M0 it only shows the themed empty state; M3 adds the lessons grouped by subject.
 * Sits inside CapyScreenBackground (floral paper in light mode, plain plum in dark mode).
 */
@Composable
fun HomeScreen(onParentMode: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = CapyDimens.screenMargin),
    ) {
        HomeTopBar(onParentMode = onParentMode)
        Spacer(Modifier.height(CapyDimens.blockGap))
        EmptyLessons()
    }
}

@Composable
private fun HomeTopBar(onParentMode: () -> Unit) {
    val c = CapyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CapyDimens.topBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.capy_face),
            contentDescription = null,
            modifier = Modifier.size(CapyDimens.capyFace),
        )
        Spacer(Modifier.width(CapyDimens.itemGap))
        Text(
            text = stringResource(R.string.home_greeting),
            style = CapyTheme.type.display,
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onParentMode,
            modifier = Modifier.size(CapyDimens.touchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_capy_lock),
                contentDescription = stringResource(R.string.home_parent_mode),
                tint = c.textSecondary,
                modifier = Modifier.size(CapyDimens.icon),
            )
        }
    }
}

@Composable
private fun EmptyLessons() {
    val c = CapyTheme.colors
    Surface(
        color = c.surface,
        contentColor = c.text,
        shape = CapyShapes.card,
        border = BorderStroke(CapyDimens.border, c.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CapyDimens.blockGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap),
        ) {
            Image(
                painter = painterResource(R.drawable.capy_reading),
                contentDescription = null,
                modifier = Modifier.size(CapyDimens.capyHero),
            )
            Text(
                text = stringResource(R.string.home_empty_title),
                style = CapyTheme.type.title,
                color = c.text,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.home_empty_body),
                style = CapyTheme.type.body,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
