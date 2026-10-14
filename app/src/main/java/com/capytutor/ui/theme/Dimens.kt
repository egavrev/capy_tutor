package com.capytutor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing and sizes (8 dp grid). */
object CapyDimens {
    val grid = 8.dp
    val screenMargin = 16.dp
    val blockGap = 24.dp
    val itemGap = 8.dp
    val radiusCard = 16.dp
    val radiusButton = 24.dp
    val radiusBubble = 24.dp
    val radiusSmall = 12.dp
    val buttonHeight = 56.dp
    val buttonHeightSmall = 48.dp
    val touchTarget = 48.dp
    val micButton = 96.dp
    val micButtonSmall = 48.dp
    val icon = 24.dp
    val iconSmall = 18.dp
    val topBarHeight = 76.dp
    val bottomNavHeight = 72.dp
    val answerCardMinHeight = 56.dp
    val chipHeight = 40.dp
    val strokeIcon = 2.dp
    /** 1 dp borders on cards and bubbles (`line` colour). */
    val border = 1.dp
    /** Capy's face in top bars. */
    val capyFace = 48.dp
    /** Capy illustration in empty states and big moments. */
    val capyHero = 160.dp
    /** Step progress bar height. */
    val progressBar = 6.dp
    /** Subject dot in lesson rows. */
    val subjectDot = 10.dp
    /** PIN dots. */
    val pinDot = 16.dp
    /** Lesson row. */
    val lessonRowHeight = 60.dp
    /** Capy's face next to a bubble. */
    val capyBubbleAvatar = 40.dp
    /** Bubbles use at most this share of the screen width. */
    const val bubbleMaxWidth = 0.85f
}

/** Shapes: cards 16 dp, big buttons and bubbles 24 dp, chips are pills. */
object CapyShapes {
    val card = RoundedCornerShape(16.dp)
    val small = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(24.dp)
    val chip = RoundedCornerShape(percent = 50)
    /** Capy speaks from the left: tail corner bottom-left. */
    val capyBubble = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
    /** The child answers from the right: tail corner bottom-right. */
    val childBubble = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 8.dp, bottomStart = 24.dp)
    val bottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/** Motion: 200-300 ms ease-out; the mic pulse is slow (2 s) so it never feels urgent. */
object CapyMotion {
    const val SHORT_MS = 200
    const val MEDIUM_MS = 300
    const val MIC_PULSE_MS = 2000
}
