package com.capytutor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.capytutor.R
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme

/** Text field in the Capy style: surface fill, line border, primary when focused. */
@Composable
fun CapyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onIme: (() -> Unit)? = null,
) {
    val c = CapyTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = CapyTheme.type.caption) },
        textStyle = CapyTheme.type.body,
        singleLine = singleLine,
        minLines = minLines,
        shape = CapyShapes.small,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = if (secret || keyboardType != KeyboardType.Text) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = !secret,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onIme?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedBorderColor = c.primary,
            unfocusedBorderColor = c.line,
            focusedLabelColor = c.primary,
            unfocusedLabelColor = c.textSecondary,
            cursorColor = c.primary,
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The child's answer box: type, then the round send button. The mic arrives with M4. */
@Composable
fun AnswerInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Scrie aici…",
    enabled: Boolean = true,
) {
    val c = CapyTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = CapyTheme.type.body, color = c.textSecondary) },
            textStyle = CapyTheme.type.body,
            enabled = enabled,
            maxLines = 5,
            shape = CapyShapes.button,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.surface,
                unfocusedContainerColor = c.surface,
                disabledContainerColor = c.inactive,
                focusedBorderColor = c.primary,
                unfocusedBorderColor = c.line,
                disabledBorderColor = c.inactiveOutline,
                cursorColor = c.primary,
                focusedTextColor = c.text,
                unfocusedTextColor = c.text,
            ),
            modifier = Modifier.weight(1f).heightIn(min = CapyDimens.buttonHeight),
        )
        val canSend = enabled && value.isNotBlank()
        IconButton(
            onClick = onSend,
            enabled = canSend,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = c.primary, contentColor = c.onPrimary,
                disabledContainerColor = c.inactive, disabledContentColor = c.onInactive,
            ),
            modifier = Modifier.size(CapyDimens.buttonHeight),
        ) {
            Icon(painterResource(R.drawable.ic_capy_send), contentDescription = "Trimite", modifier = Modifier.size(CapyDimens.icon))
        }
    }
}

/**
 * PIN pad for parent mode: 4 dots and a 3×4 keypad. Wrong PIN = Champagne panel with
 * wrongAccent dots and a gentle line; no shake.
 */
@Composable
fun PinPad(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    wrong: Boolean = false,
    message: String? = null,
) {
    val c = CapyTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CapyDimens.screenMargin)) {
        Surface(
            color = if (wrong) c.wrong else c.surface,
            shape = CapyShapes.card,
            border = if (wrong) null else BorderStroke(CapyDimens.border, c.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(CapyDimens.screenMargin), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CapyDimens.screenMargin),
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${pin.length} din 4 cifre" },
                ) {
                    repeat(4) { i ->
                        val filled = i < pin.length
                        val color = if (wrong) c.wrongAccent else c.primary
                        Box(
                            Modifier
                                .size(CapyDimens.pinDot)
                                .clip(CircleShape)
                                .then(if (filled || wrong) Modifier.background(color) else Modifier.border(CapyDimens.strokeIcon, c.controlOutline, CircleShape)),
                        )
                    }
                }
                if (message != null) {
                    Text(message, style = CapyTheme.type.body, color = if (wrong) c.onWrong else c.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CapyDimens.screenMargin)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Box(Modifier.size(CapyDimens.buttonHeight + CapyDimens.blockGap))
                        "⌫" -> PinKey(onClick = { if (pin.isNotEmpty()) onPinChange(pin.dropLast(1)) }, description = "Șterge") {
                            Icon(painterResource(R.drawable.ic_capy_backspace), contentDescription = null, tint = c.text, modifier = Modifier.size(CapyDimens.icon))
                        }
                        else -> PinKey(onClick = { if (pin.length < 4) onPinChange(pin + key) }, description = key) {
                            Text(key, style = CapyTheme.type.display, color = c.text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    val c = CapyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(CapyDimens.buttonHeight + CapyDimens.blockGap)
            .clip(CircleShape)
            .background(if (pressed) c.soft else c.background)
            .border(CapyDimens.border, c.line, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Two or three options as a row of pills (gender, yes/no). */
@Composable
fun <T> ChoicePills(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = CapyTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Surface(
                color = if (on) c.primary else c.surface,
                contentColor = if (on) c.onPrimary else c.text,
                shape = CapyShapes.chip,
                border = if (on) null else BorderStroke(CapyDimens.border, c.controlOutline),
                modifier = Modifier
                    .weight(1f)
                    .height(CapyDimens.buttonHeightSmall)
                    .clip(CapyShapes.chip)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap / 2)) {
                        if (on) Icon(painterResource(R.drawable.ic_capy_check), contentDescription = null, modifier = Modifier.size(CapyDimens.iconSmall))
                        Text(label, style = CapyTheme.type.label)
                    }
                }
            }
        }
    }
}
