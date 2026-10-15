package com.capytutor.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capytutor.R
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyMotion
import com.capytutor.ui.theme.CapyTheme

enum class MicLook {
    /** Ready: dark pink circle, mic icon. */
    IDLE,

    /** Recording / listening: pause icon and two slow pulsing rings. */
    RECORDING,

    /** Capy is busy with what was said: surface fill, mic border. */
    BUSY,

    /** No microphone permission: Linen, dashed outline. */
    NO_PERMISSION,
}

/** True when the system setting "Remove animations" is on: no pulse then. */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** The microphone (design system: 96 dp on the retelling screen, 48 dp inline). Tap to start, tap to stop. */
@Composable
fun MicButton(look: MicLook, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val c = CapyTheme.colors
    val size = if (small) CapyDimens.micButtonSmall else CapyDimens.micButton
    val iconSize = if (small) CapyDimens.icon else CapyDimens.micIcon
    val reduced = rememberReducedMotion()
    val description = when (look) {
        MicLook.IDLE -> "Vorbește"
        MicLook.RECORDING -> "Oprește, am terminat"
        MicLook.BUSY -> "Capy ascultă ce ai spus"
        MicLook.NO_PERMISSION -> "Permite microfonul"
    }
    val outer = if (small) size else CapyDimens.micRingOuter
    Box(modifier.size(outer), contentAlignment = Alignment.Center) {
        if (look == MicLook.RECORDING && !small) {
            val pulse = if (reduced) {
                1f
            } else {
                val t = rememberInfiniteTransition(label = "mic")
                val v by t.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(CapyMotion.MIC_PULSE_MS / 2, easing = LinearEasing), RepeatMode.Reverse),
                    label = "pulse",
                )
                v
            }
            Box(Modifier.size(CapyDimens.micRingOuter).scale(pulse).clip(CircleShape).background(c.micRing1))
            Box(Modifier.size(CapyDimens.micRingInner).scale(pulse).clip(CircleShape).background(c.micRing2))
        }
        val (bg, fg) = when (look) {
            MicLook.IDLE, MicLook.RECORDING -> c.mic to c.onMic
            MicLook.BUSY -> c.surface to c.mic
            MicLook.NO_PERMISSION -> c.inactive to c.onInactive
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .then(
                    when (look) {
                        MicLook.BUSY -> Modifier.border(BorderStroke(CapyDimens.micBorder, c.mic), CircleShape)
                        MicLook.NO_PERMISSION -> Modifier.dashedBorder(c.inactiveOutline, CapyDimens.strokeIcon, size / 2)
                        else -> Modifier
                    },
                )
                .clickable(enabled = look != MicLook.BUSY, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (look == MicLook.RECORDING) R.drawable.ic_capy_pause else R.drawable.ic_capy_mic),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
