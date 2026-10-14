package com.capytutor.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.capytutor.R

/**
 * Capy Tutore theme. Dark mode follows the system setting (evening use).
 * Material components get a matching colour scheme; app-specific roles
 * (right, wrong, inactive, reward, mic...) live in CapyTheme.colors.
 */
@Composable
fun CapyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) CapyDarkColors else CapyLightColors
    val scheme = if (darkTheme) darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.soft, onPrimaryContainer = c.text,
        secondary = c.secondary, onSecondary = c.onSecondary,
        secondaryContainer = c.reward, onSecondaryContainer = c.onReward,
        tertiary = c.mic, onTertiary = c.onMic,
        error = c.onWrong, errorContainer = c.wrong, onErrorContainer = c.onWrong,
        background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text, onSurfaceVariant = c.textSecondary,
        outline = c.controlOutline, outlineVariant = c.line, scrim = c.scrim,
    ) else lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.soft, onPrimaryContainer = c.text,
        secondary = c.secondary, onSecondary = c.onSecondary,
        secondaryContainer = c.reward, onSecondaryContainer = c.onReward,
        tertiary = c.mic, onTertiary = c.onMic,
        error = c.onWrong, errorContainer = c.wrong, onErrorContainer = c.onWrong,
        background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text, onSurfaceVariant = c.textSecondary,
        outline = c.controlOutline, outlineVariant = c.line, scrim = c.scrim,
    )
    CompositionLocalProvider(LocalCapyColors provides c, LocalCapyTypography provides CapyType) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object CapyTheme {
    val colors: CapyColors
        @Composable @ReadOnlyComposable get() = LocalCapyColors.current
    val type: CapyTypography
        @Composable @ReadOnlyComposable get() = LocalCapyTypography.current
}

/**
 * Screen background: the striped floral paper in light mode (scaled to the
 * screen height, centred, never stretched), a plain plum background in dark mode.
 */
@Composable
fun CapyScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = CapyTheme.colors
    Box(modifier.fillMaxSize().background(c.background)) {
        if (!c.isDark) {
            Image(
                painter = painterResource(R.drawable.bg_floral_stripes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        content()
    }
}
