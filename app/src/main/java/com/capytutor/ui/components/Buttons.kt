package com.capytutor.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

/** Dashed outline for disabled and locked things (design system: inactive = dashed + lighter text). */
fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = drawBehind {
    val stroke = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(width.toPx() * 6, width.toPx() * 4)))
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(radius.toPx()))
}

@Composable
private fun CapyFilledButton(
    text: String,
    container: Color,
    pressedContainer: Color,
    content: Color,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = CapyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape: Shape = CapyShapes.button
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = shape,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) pressedContainer else container,
            contentColor = content,
            disabledContainerColor = if (loading) container else c.inactive,
            disabledContentColor = if (loading) content else c.onInactive,
        ),
        elevation = null,
        modifier = modifier
            .fillMaxWidth()
            .height(CapyDimens.buttonHeight)
            .then(if (!enabled && !loading) Modifier.dashedBorder(c.inactiveOutline, CapyDimens.border, CapyDimens.radiusButton) else Modifier),
    ) {
        Text(if (loading) "• • •" else text, style = CapyTheme.type.label)
    }
}

@Composable
fun CapyPrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val c = CapyTheme.colors
    CapyFilledButton(text, c.primary, c.primaryPressed, c.onPrimary, enabled, loading, modifier, onClick)
}

@Composable
fun CapySecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = CapyTheme.colors
    CapyFilledButton(text, c.secondary, c.secondaryPressed, c.onSecondary, enabled, false, modifier, onClick)
}

@Composable
fun CapyTextButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = CapyTheme.colors
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = CapyShapes.chip,
        colors = ButtonDefaults.textButtonColors(contentColor = c.link, disabledContentColor = c.onInactive),
        modifier = modifier.heightIn(min = CapyDimens.buttonHeightSmall).clip(CapyShapes.chip),
    ) {
        Text(text, style = CapyTheme.type.label)
    }
}
