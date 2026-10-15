package com.capytutor.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Capy Tutore semantic colours. Read them in UI code as CapyTheme.colors.xxx. */
@Immutable
data class CapyColors(
    val background: Color,  // Screen background, under the floral paper
    val surface: Color,  // Cards, bubbles, inputs
    val text: Color,  // Primary text (Plum cocoa)
    val textSecondary: Color,  // Hints, metadata (Dusty mauve)
    val primary: Color,  // Primary actions (Rose)
    val onPrimary: Color,  // Content on primary
    val primaryPressed: Color,  // Primary, pressed
    val secondary: Color,  // Secondary actions (Mauve)
    val onSecondary: Color,  // Content on secondary
    val secondaryPressed: Color,  // Secondary, pressed
    val link: Color,  // Text buttons, links
    val right: Color,  // Right answer (Blush pink)
    val onRight: Color,  // Content on right (Raspberry)
    val rightAccent: Color,  // Bow fill on right
    val wrong: Color,  // Wrong / not yet (Champagne)
    val onWrong: Color,  // Content on wrong (Toffee)
    val wrongAccent: Color,  // Wrong PIN dots
    val inactive: Color,  // Disabled / locked (Linen)
    val onInactive: Color,  // Content on inactive
    val inactiveOutline: Color,  // Inactive outline (Deep linen)
    val reward: Color,  // Bow counter, reward (Cotton candy)
    val onReward: Color,  // Content on reward
    val rewardBanner: Color,  // Reward / info banner
    val mic: Color,  // Microphone (Dark pink)
    val onMic: Color,  // Icon on microphone
    val line: Color,  // Dividers, borders
    val soft: Color,  // Selected tint, active nav pill
    val childBubble: Color,  // Child chat bubble (Lilac pink)
    val highlight: Color,  // Key sentences in reading mode (E14)
    val controlOutline: Color,  // Radio rings, empty slots
    val subjectHistory: Color,  // Subject dot: History
    val subjectRomanian: Color,  // Subject dot: Romanian
    val subjectMath: Color,  // Subject dot: Math
    val scrim: Color,  // Scrim behind bottom sheets
    val micRing1: Color,  // Mic pulse, outer ring
    val micRing2: Color,  // Mic pulse, inner ring
    val isDark: Boolean,
)

val CapyLightColors = CapyColors(
    background = Color(0xFFFEFAF7),
    surface = Color(0xFFFFFDFE),
    text = Color(0xFF4A2F3A),
    textSecondary = Color(0xFF73505C),
    primary = Color(0xFFA8456E),
    onPrimary = Color(0xFFFFFFFF),
    primaryPressed = Color(0xFF8C3A5E),
    secondary = Color(0xFF8F5A70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryPressed = Color(0xFF74475A),
    link = Color(0xFF8C3A5E),
    right = Color(0xFFFBD9E4),
    onRight = Color(0xFF8E2A55),
    rightAccent = Color(0xFFF29BB8),
    wrong = Color(0xFFF7E7CE),
    onWrong = Color(0xFF6E4A1E),
    wrongAccent = Color(0xFF9E7A40),
    inactive = Color(0xFFFAF0E6),
    onInactive = Color(0xFF7E6B60),
    inactiveOutline = Color(0xFFE2CFBE),
    reward = Color(0xFFF6BFD0),
    onReward = Color(0xFF4A2F3A),
    rewardBanner = Color(0xFFFDE8EF),
    mic = Color(0xFFE75480),
    onMic = Color(0xFFFFFFFF),
    line = Color(0xFFF1DCE3),
    soft = Color(0xFFFCE7EE),
    childBubble = Color(0xFFF1E3EE),
    highlight = Color(0xFFF9D3E0),
    controlOutline = Color(0xFFCDA7B6),
    subjectHistory = Color(0xFFB7814A),
    subjectRomanian = Color(0xFF8D7CC4),
    subjectMath = Color(0xFFA8456E),
    scrim = Color(0x524A2F3A),
    micRing1 = Color(0x29E75480),
    micRing2 = Color(0x4DE75480),
    isDark = false,
)

val CapyDarkColors = CapyColors(
    background = Color(0xFF21171B),
    surface = Color(0xFF2E2227),
    text = Color(0xFFF6E6EB),
    textSecondary = Color(0xFFCDAFBA),
    primary = Color(0xFFF2A2BF),
    onPrimary = Color(0xFF21171B),
    primaryPressed = Color(0xFFE58DB0),
    secondary = Color(0xFFD9A5BA),
    onSecondary = Color(0xFF21171B),
    secondaryPressed = Color(0xFFC98FA6),
    link = Color(0xFFF2A2BF),
    right = Color(0xFF4A2234),
    onRight = Color(0xFFF7B6CD),
    rightAccent = Color(0xFFC25A84),
    wrong = Color(0xFF3B3022),
    onWrong = Color(0xFFF3DDB8),
    wrongAccent = Color(0xFFD9BC8A),
    inactive = Color(0xFF3A302B),
    onInactive = Color(0xFFA08F86),
    inactiveOutline = Color(0xFF4D4139),
    reward = Color(0xFFEBA2BC),
    onReward = Color(0xFF21171B),
    rewardBanner = Color(0xFF432634),
    mic = Color(0xFFE75480),
    onMic = Color(0xFF21171B),
    line = Color(0xFF46323B),
    soft = Color(0xFF3D2531),
    childBubble = Color(0xFF3E2A3F),
    highlight = Color(0xFF5A2E43),
    controlOutline = Color(0xFF7A5F69),
    subjectHistory = Color(0xFFD9A57A),
    subjectRomanian = Color(0xFFB4A6E0),
    subjectMath = Color(0xFFF2A2BF),
    scrim = Color(0x80000000),
    micRing1 = Color(0x26E75480),
    micRing2 = Color(0x47E75480),
    isDark = true,
)

val LocalCapyColors = staticCompositionLocalOf { CapyLightColors }
