package com.capytutor.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import com.capytutor.R
import com.capytutor.ui.theme.CapyColors
import com.capytutor.ui.theme.CapyDarkColors
import com.capytutor.ui.theme.CapyMotion
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The start intro (design brief "Capy Tutore – splash screen brief", ideas 3 and 4). Two scenes
 * that alternate on each start; both tell the app's story: a question appears, Capy discovers
 * something from history or geography, the question turns into the answer. Any tap skips it;
 * MainActivity leaves it out when the phone has animations turned off.
 *
 * Everything is drawn in one Canvas per scene, in fractions of a square "stage", so the scene
 * scales to any screen without dp values. Flat shapes only (no gradients, no shadows), theme
 * colours only, no flashing, no sound.
 */

enum class IntroScene(val wire: String) {
    /** Idea 3: Capy hosts the question show (K-pop stage and a ramen bowl). */
    SHOW("show"),

    /** Idea 4: night shift at the little museum (spooky-cute, ends warm). */
    MUSEUM("museum");

    companion object {
        /** Alternates by the number of earlier starts. */
        fun forStart(count: Int): IntroScene = entries[Math.floorMod(count, entries.size)]

        fun fromWire(s: String?): IntroScene? = entries.firstOrNull { it.wire == s }
    }
}

/**
 * Plays [scene] once, holds the last frame, fades out and calls [onDone]. [frame] freezes the
 * scene at that point (0..1) instead of playing: used by the screenshot tour.
 */
@Composable
fun CapyIntro(scene: IntroScene, onDone: () -> Unit, frame: Float? = null) {
    val progress = remember { Animatable(frame ?: 0f) }
    val fade = remember { Animatable(1f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        if (frame != null) return@LaunchedEffect
        progress.animateTo(1f, tween(CapyMotion.INTRO_PLAY_MS, easing = LinearEasing))
        delay(CapyMotion.INTRO_HOLD_MS.toLong())
        fade.animateTo(0f, tween(CapyMotion.MEDIUM_MS))
        done()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Mai departe") { done() }
            .semantics { contentDescription = "Capy Tutore" },
    ) {
        when (scene) {
            IntroScene.SHOW -> ShowScene { progress.value }
            IntroScene.MUSEUM -> MuseumScene { progress.value }
        }
    }
}

// ---- Idea 3: Capy hosts the question show ---------------------------------------------------

@Composable
private fun ShowScene(progress: () -> Float) {
    val col = CapyTheme.colors
    val style = CapyTheme.type.display
    val tm = rememberTextMeasurer()
    val capy = ImageBitmap.imageResource(R.drawable.capy_encouraging)
    Canvas(Modifier.fillMaxSize()) {
        val p = progress()
        val st = stage()
        drawRect(col.soft)
        // The stage floor, where the name and the crowd stand.
        drawRect(col.line, topLeft = Offset(0f, st.y(0.86f)), size = Size(size.width, size.height - st.y(0.86f)))

        // 0–25 %: the pink spotlight opens over Capy's place.
        val spot = easeOut(seg(p, 0f, 0.25f))
        if (spot > 0f) {
            val cx = st.x(0.34f)
            val r = st.d(0.34f) * spot
            val cone = Path().apply {
                moveTo(cx - st.d(0.05f) * spot, 0f)
                lineTo(cx + st.d(0.05f) * spot, 0f)
                lineTo(cx + r, st.y(0.86f))
                lineTo(cx - r, st.y(0.86f))
                close()
            }
            drawPath(cone, col.highlight, alpha = 0.75f)
            drawOval(col.highlight, topLeft = Offset(cx - r, st.y(0.86f) - r * 0.16f), size = Size(2 * r, r * 0.32f))
        }

        // 85–100 %: one beat bounce for everything on stage.
        val beat = st.d(0.025f) * sin(PI.toFloat() * seg(p, 0.85f, 1f))

        translate(top = beat) {
            ramenBowl(st, col)

            // 55–85 %: steam rises from the broth and curls into a small globe.
            val rise = easeOut(seg(p, 0.55f, 0.74f))
            val steamFade = 1f - seg(p, 0.70f, 0.84f)
            if (rise > 0f && steamFade > 0f) {
                listOf(0.74f, 0.80f, 0.86f).forEachIndexed { i, x ->
                    val path = Path()
                    val bottom = st.y(0.70f)
                    val top = bottom - st.d(0.20f) * rise
                    var y = bottom
                    path.moveTo(st.x(x), y)
                    while (y > top) {
                        y -= st.d(0.01f)
                        val wave = sin((bottom - y) / st.d(0.05f) + i) * st.d(0.015f)
                        path.lineTo(st.x(x) + wave, y)
                    }
                    drawPath(path, col.textSecondary, alpha = 0.45f * steamFade, style = Stroke(st.d(0.012f), cap = StrokeCap.Round))
                }
            }
            val globeIn = backOut(seg(p, 0.66f, 0.85f))
            if (globeIn > 0f) {
                scale(globeIn, pivot = Offset(st.x(0.80f), st.y(0.47f))) {
                    globe(Offset(st.x(0.80f), st.y(0.47f)), st.d(0.085f), col)
                }
            }

            // 25–55 %: Capy pops up with a microphone.
            val pop = backOut(seg(p, 0.25f, 0.55f))
            if (pop > 0f) {
                val img = Rect(st.x(0.04f), st.y(0.26f), st.x(0.64f), st.y(0.86f))
                scale(pop, pivot = Offset(img.center.x, img.bottom)) {
                    drawCapy(capy, img)
                    // The mic sits in the raised paw, in front of the mouth.
                    fun at(fx: Float, fy: Float) = Offset(img.left + fx * img.width, img.top + fy * img.height)
                    drawLine(col.text, at(0.735f, 0.57f), at(0.765f, 0.44f), strokeWidth = img.width * 0.035f, cap = StrokeCap.Round)
                    drawCircle(col.mic, radius = img.width * 0.048f, center = at(0.772f, 0.41f))
                    drawCircle(col.onMic, radius = img.width * 0.048f, center = at(0.772f, 0.41f), alpha = 0.5f, style = Stroke(img.width * 0.008f))
                }
            }

            // 35–55 %: the speech bubble with a "?" floats up.
            val ask = easeOut(seg(p, 0.35f, 0.55f))
            if (ask > 0f) {
                translate(top = st.d(0.06f) * (1f - ask)) {
                    speechBubble(st, col, ask)
                    glyph(tm, style, "?", Offset(st.x(0.66f), st.y(0.22f)), st.d(0.12f), col.primary, ask)
                }
            }
        }

        // The crowd: light sticks sway at most twice, then their "?" tops turn into hearts.
        lightSticks(tm, style, p, col)

        // 85–100 %: the name.
        glyph(tm, style, "Capy Tutore", Offset(size.width / 2, st.y(1.02f)), st.d(0.09f), col.text, seg(p, 0.85f, 1f))
    }
}

/** A blush ramen bowl with a Greek-key rim (history), cream broth, noodles, an egg and chopsticks. */
private fun DrawScope.ramenBowl(st: Stage, col: CapyColors) {
    val bowl = Rect(st.x(0.65f), st.y(0.62f), st.x(0.95f), st.y(0.86f))
    // Chopsticks behind the broth.
    drawLine(col.subjectHistory, Offset(st.x(0.86f), st.y(0.73f)), Offset(st.x(0.97f), st.y(0.58f)), st.d(0.012f), cap = StrokeCap.Round)
    drawLine(col.subjectHistory, Offset(st.x(0.89f), st.y(0.735f)), Offset(st.x(0.995f), st.y(0.60f)), st.d(0.012f), cap = StrokeCap.Round)
    // Broth, noodles and half an egg.
    drawOval(col.wrong, topLeft = Offset(st.x(0.66f), st.y(0.715f)), size = Size(st.d(0.28f), st.d(0.05f)))
    for (i in 0..2) {
        val x = st.x(0.70f + i * 0.045f)
        drawArc(col.wrongAccent, 200f, 140f, false, topLeft = Offset(x, st.y(0.725f)), size = Size(st.d(0.05f), st.d(0.03f)), style = Stroke(st.d(0.006f), cap = StrokeCap.Round))
    }
    drawOval(col.surface, topLeft = Offset(st.x(0.81f), st.y(0.72f)), size = Size(st.d(0.075f), st.d(0.035f)))
    drawCircle(col.sunny, radius = st.d(0.013f), center = Offset(st.x(0.8475f), st.y(0.7375f)))
    // The bowl: the lower half of an oval.
    val body = Path().apply { arcTo(bowl, 0f, 180f, false); close() }
    drawPath(body, col.right)
    clipPath(body) {
        val band = Rect(bowl.left, st.y(0.745f), bowl.right, st.y(0.79f))
        drawRect(col.subjectHistory, band.topLeft, band.size)
        greekKey(band, col.surface, st.d(0.004f))
    }
    drawPath(body, col.onRight, alpha = 0.35f, style = Stroke(st.d(0.005f)))
}

/** A running Greek key (meander) along [band]. */
private fun DrawScope.greekKey(band: Rect, color: Color, stroke: Float) {
    val h = band.height * 0.62f
    val top = band.top + (band.height - h) / 2
    val u = h * 1.15f
    val path = Path()
    var x = band.left
    path.moveTo(x, top + h)
    while (x < band.right) {
        path.lineTo(x, top + h)
        path.lineTo(x, top)
        path.lineTo(x + u * 0.75f, top)
        path.lineTo(x + u * 0.75f, top + h * 0.7f)
        path.lineTo(x + u * 0.35f, top + h * 0.7f)
        path.lineTo(x + u * 0.35f, top + h * 0.35f)
        path.moveTo(x, top + h)
        path.lineTo(x + u, top + h)
        x += u
    }
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Square, join = StrokeJoin.Miter))
}

private fun DrawScope.speechBubble(st: Stage, col: CapyColors, alpha: Float) {
    val r = Rect(st.x(0.55f), st.y(0.14f), st.x(0.77f), st.y(0.30f))
    val tail = Path().apply {
        moveTo(st.x(0.58f), st.y(0.28f))
        lineTo(st.x(0.54f), st.y(0.36f))
        lineTo(st.x(0.64f), st.y(0.29f))
        close()
    }
    val shape = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(st.d(0.05f))))
    }
    drawPath(tail, col.surface, alpha = alpha)
    drawPath(tail, col.line, alpha = alpha, style = Stroke(st.d(0.006f), join = StrokeJoin.Round))
    drawPath(shape, col.surface, alpha = alpha)
    drawPath(shape, col.line, alpha = alpha, style = Stroke(st.d(0.006f)))
}

/** Seven light sticks along the bottom; tops alternate pink and lilac. */
private fun DrawScope.lightSticks(tm: TextMeasurer, style: TextStyle, p: Float, col: CapyColors) {
    val n = 7
    val w = size.width / n
    val r = min(w * 0.32f, size.height * 0.034f)
    val baseY = size.height * 0.985f
    val topY = size.height * 0.84f
    val sway = sin(2 * PI.toFloat() * 2 * seg(p, 0f, 0.85f)) * (1f - seg(p, 0.6f, 0.85f))
    val heart = easeOut(seg(p, 0.85f, 0.93f))
    for (i in 0 until n) {
        val x = w * (i + 0.5f)
        val c = if (i % 2 == 0) col.primary else col.subjectRomanian
        rotate(degrees = 9f * sway * if (i % 2 == 0) 1f else -1f, pivot = Offset(x, baseY)) {
            drawLine(col.textSecondary, Offset(x, baseY), Offset(x, topY + r), strokeWidth = r * 0.45f, cap = StrokeCap.Round)
            if (heart < 1f) {
                drawCircle(c, radius = r, center = Offset(x, topY), alpha = 1f - heart)
                glyph(tm, style, "?", Offset(x, topY), r * 1.5f, col.onPrimary, 1f - heart)
            }
            if (heart > 0f) drawPath(heartPath(Offset(x, topY), r * 2.3f), c, alpha = heart)
        }
    }
}

// ---- Idea 4: night shift at the little museum ------------------------------------------------

@Composable
private fun MuseumScene(progress: () -> Float) {
    val col = CapyTheme.colors
    val night = CapyDarkColors
    val style = CapyTheme.type.display
    val tm = rememberTextMeasurer()
    val found = ImageBitmap.imageResource(R.drawable.capy_neutral)
    val waving = ImageBitmap.imageResource(R.drawable.capy_encouraging)
    Canvas(Modifier.fillMaxSize()) {
        val p = progress()
        val st = stage()
        val lampOn = easeOut(seg(p, 0.60f, 0.85f))

        // The hall as it looks with the light on.
        drawRect(col.soft)
        drawRect(col.line, topLeft = Offset(0f, st.y(0.86f)), size = Size(size.width, size.height - st.y(0.86f)))
        if (lampOn > 0f) {
            // Lamplight: a soft cone from the shade to the floor.
            val light = Path().apply {
                moveTo(st.x(0.41f), st.y(0.09f)); lineTo(st.x(0.59f), st.y(0.09f))
                lineTo(st.x(0.96f), st.y(0.86f)); lineTo(st.x(0.04f), st.y(0.86f)); close()
            }
            drawPath(light, col.highlight, alpha = 0.9f * lampOn)
            drawOval(col.highlight, topLeft = Offset(st.x(0.04f), st.y(0.83f)), size = Size(st.d(0.92f), st.d(0.06f)), alpha = lampOn)
        }
        wallMap(st, col)
        amphora(st, col)
        globe(Offset(st.x(0.86f), st.y(0.60f)), st.d(0.08f), col)
        drawLine(col.textSecondary, Offset(st.x(0.86f), st.y(0.68f)), Offset(st.x(0.86f), st.y(0.83f)), st.d(0.012f), cap = StrokeCap.Round)
        drawOval(col.textSecondary, topLeft = Offset(st.x(0.80f), st.y(0.825f)), size = Size(st.d(0.12f), st.d(0.035f)))

        // 30–80 %: a "?" shadow on the wall, gone once the light is on.
        val shadow = seg(p, 0.30f, 0.45f) * (1f - seg(p, 0.62f, 0.80f))
        if (shadow > 0f) glyph(tm, style, "?", Offset(st.x(0.69f), st.y(0.33f)), st.d(0.24f), col.secondary, 0.4f * shadow)

        // The lamp and its pull cord: Capy pulls it at 55–65 %.
        drawLine(col.textSecondary, Offset(st.x(0.5f), 0f), Offset(st.x(0.5f), st.y(0.02f)), st.d(0.006f))
        val pull = st.d(0.03f) * sin(PI.toFloat() * seg(p, 0.55f, 0.65f))
        drawLine(col.textSecondary, Offset(st.x(0.56f), st.y(0.08f)), Offset(st.x(0.56f), st.y(0.30f) + pull), st.d(0.005f))
        drawCircle(col.primary, radius = st.d(0.014f), center = Offset(st.x(0.56f), st.y(0.30f) + pull))
        val shade = Path().apply {
            moveTo(st.x(0.45f), st.y(0.02f)); lineTo(st.x(0.55f), st.y(0.02f))
            lineTo(st.x(0.59f), st.y(0.09f)); lineTo(st.x(0.41f), st.y(0.09f)); close()
        }
        drawArc(if (lampOn > 0f) col.sunny else col.inactiveOutline, 0f, 180f, true, topLeft = Offset(st.x(0.465f), st.y(0.065f)), size = Size(st.d(0.07f), st.d(0.05f)))
        drawPath(shade, col.primary)

        // Capy: found by the beam (eyes open), then waving at the globe once the light is on.
        val img = Rect(st.x(0.30f), st.y(0.40f), st.x(0.76f), st.y(0.86f))
        val wave = seg(p, 0.64f, 0.69f) // a quick swap reads as Capy's reaction, not a ghost
        if (wave < 1f) drawCapy(found, img, 1f - wave)
        if (wave > 0f) drawCapy(waving, img, wave)

        // The dark, with the flashlight beam cut out of it. It lifts as the lamp turns on (in dark
        // mode it stays dim: lamplight, not a white room).
        val dark = 0.93f - (0.93f - if (col.isDark) 0.40f else 0f) * lampOn
        if (dark > 0f) {
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawRect(night.background, alpha = dark)
            val beam = beamAt(p)
            val c = Offset(st.x(beam.x), st.y(beam.y))
            val r = st.d(beam.radius)
            val on = easeOut(seg(p, 0f, 0.08f))
            drawCircle(col.text, radius = r * 1.18f, center = c, alpha = 0.45f * on, blendMode = BlendMode.DstOut)
            drawCircle(col.text, radius = r, center = c, alpha = on, blendMode = BlendMode.DstOut)
            drawContext.canvas.restore()
        }

        // The lantern beside Capy gives its own light, so it sits above the dark.
        val lantern = Rect(st.x(0.245f), st.y(0.76f), st.x(0.305f), st.y(0.86f))
        drawCircle(col.sunny, radius = st.d(0.07f), center = lantern.center, alpha = 0.22f * (1f - lampOn))
        drawArc(col.textSecondary, 180f, 180f, false, topLeft = Offset(lantern.left + lantern.width * 0.2f, lantern.top - lantern.height * 0.25f), size = Size(lantern.width * 0.6f, lantern.height * 0.5f), style = Stroke(st.d(0.006f)))
        drawRoundRect(col.surface, topLeft = lantern.topLeft, size = lantern.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(st.d(0.012f)))
        drawRoundRect(col.textSecondary, topLeft = lantern.topLeft, size = lantern.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(st.d(0.012f)), style = Stroke(st.d(0.006f)))
        drawOval(col.sunny, topLeft = Offset(lantern.center.x - st.d(0.012f), lantern.center.y - st.d(0.022f)), size = Size(st.d(0.024f), st.d(0.044f)))

        // 80–100 %: the name.
        glyph(tm, style, "Capy Tutore", Offset(size.width / 2, st.y(1.02f)), st.d(0.09f), col.text, seg(p, 0.80f, 1f))
    }
}

private class Beam(val x: Float, val y: Float, val radius: Float)

/** The flashlight: over the wall map, the amphora and the globe, then it rests on Capy and widens. */
private fun beamAt(p: Float): Beam {
    val stops = listOf(
        0.00f to Beam(0.21f, 0.20f, 0.17f),
        0.10f to Beam(0.14f, 0.70f, 0.17f),
        0.20f to Beam(0.86f, 0.62f, 0.17f),
        0.30f to Beam(0.53f, 0.60f, 0.19f),
        0.42f to Beam(0.55f, 0.52f, 0.27f),
    )
    if (p >= stops.last().first) return stops.last().second
    val i = stops.indexOfLast { it.first <= p }
    val (a, from) = stops[i]
    val (b, to) = stops[i + 1]
    val t = easeInOut((p - a) / (b - a))
    return Beam(lerp(from.x, to.x, t), lerp(from.y, to.y, t), lerp(from.radius, to.radius, t))
}

/** A folded wall map with a pin (geography). */
private fun DrawScope.wallMap(st: Stage, col: CapyColors) {
    val r = Rect(st.x(0.05f), st.y(0.10f), st.x(0.37f), st.y(0.30f))
    drawRect(col.surface, r.topLeft, r.size)
    drawOval(col.secondary, topLeft = Offset(st.x(0.08f), st.y(0.13f)), size = Size(st.d(0.10f), st.d(0.07f)), alpha = 0.45f)
    drawOval(col.secondary, topLeft = Offset(st.x(0.20f), st.y(0.18f)), size = Size(st.d(0.13f), st.d(0.08f)), alpha = 0.45f)
    for (f in listOf(1f / 3, 2f / 3)) {
        val x = r.left + r.width * f
        drawLine(col.line, Offset(x, r.top), Offset(x, r.bottom), st.d(0.005f))
    }
    drawRect(col.line, r.topLeft, r.size, style = Stroke(st.d(0.006f)))
    drawLine(col.textSecondary, Offset(st.x(0.27f), st.y(0.21f)), Offset(st.x(0.27f), st.y(0.18f)), st.d(0.005f))
    drawCircle(col.primary, radius = st.d(0.014f), center = Offset(st.x(0.27f), st.y(0.175f)))
}

/** An old clay amphora on the floor (history). */
private fun DrawScope.amphora(st: Stage, col: CapyColors) {
    val cx = st.x(0.14f)
    fun x(f: Float) = cx + st.d(f)
    val body = Path().apply {
        moveTo(x(-0.025f), st.y(0.60f))
        lineTo(x(0.025f), st.y(0.60f))
        lineTo(x(0.022f), st.y(0.65f))
        cubicTo(x(0.10f), st.y(0.67f), x(0.10f), st.y(0.80f), x(0.035f), st.y(0.85f))
        lineTo(x(0.045f), st.y(0.86f))
        lineTo(x(-0.045f), st.y(0.86f))
        lineTo(x(-0.035f), st.y(0.85f))
        cubicTo(x(-0.10f), st.y(0.80f), x(-0.10f), st.y(0.67f), x(-0.022f), st.y(0.65f))
        close()
    }
    drawArc(col.subjectHistory, 90f, 180f, false, topLeft = Offset(x(-0.065f), st.y(0.615f)), size = Size(st.d(0.05f), st.d(0.06f)), style = Stroke(st.d(0.01f)))
    drawArc(col.subjectHistory, -90f, 180f, false, topLeft = Offset(x(0.015f), st.y(0.615f)), size = Size(st.d(0.05f), st.d(0.06f)), style = Stroke(st.d(0.01f)))
    drawPath(body, col.subjectHistory)
    clipPath(body) {
        drawRect(col.surface, topLeft = Offset(x(-0.1f), st.y(0.725f)), size = Size(st.d(0.2f), st.d(0.018f)), alpha = 0.6f)
    }
}

// ---- Shared pieces ----------------------------------------------------------------------------

/** The square the scene is laid out in: full width on a phone, upper part of the screen. */
private class Stage(val left: Float, val top: Float, val side: Float) {
    fun x(f: Float) = left + f * side
    fun y(f: Float) = top + f * side
    fun d(f: Float) = f * side
}

private fun DrawScope.stage(): Stage {
    val s = min(size.width, size.height * 0.56f)
    return Stage((size.width - s) / 2, size.height * 0.18f, s)
}

/** A small globe (geography): meridians and two soft continents. */
private fun DrawScope.globe(c: Offset, r: Float, col: CapyColors) {
    drawCircle(col.surface, radius = r, center = c)
    val ball = Path().apply { addOval(Rect(c, r)) }
    clipPath(ball) {
        drawOval(col.secondary, topLeft = Offset(c.x - r * 0.75f, c.y - r * 0.55f), size = Size(r * 0.8f, r * 0.6f), alpha = 0.5f)
        drawOval(col.secondary, topLeft = Offset(c.x + r * 0.05f, c.y + r * 0.05f), size = Size(r * 0.7f, r * 0.55f), alpha = 0.5f)
    }
    val line = Stroke(r * 0.07f)
    drawOval(col.secondary, topLeft = Offset(c.x - r * 0.45f, c.y - r), size = Size(r * 0.9f, 2 * r), alpha = 0.6f, style = line)
    drawLine(col.secondary, Offset(c.x - r, c.y), Offset(c.x + r, c.y), r * 0.07f, alpha = 0.6f)
    drawCircle(col.secondary, radius = r, center = c, style = Stroke(r * 0.09f))
}

private fun DrawScope.drawCapy(image: ImageBitmap, into: Rect, alpha: Float = 1f) {
    drawImage(
        image,
        dstOffset = IntOffset(into.left.roundToInt(), into.top.roundToInt()),
        dstSize = IntSize(into.width.roundToInt(), into.height.roundToInt()),
        alpha = alpha,
    )
}

/** Text centred on [center], [height] px tall, in the app font. */
private fun DrawScope.glyph(tm: TextMeasurer, style: TextStyle, text: String, center: Offset, height: Float, color: Color, alpha: Float) {
    if (alpha <= 0f) return
    val layout = tm.measure(text, style.copy(fontSize = (height * 0.8f).toSp(), lineHeight = TextUnit.Unspecified))
    drawText(
        layout,
        color = color,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
        alpha = alpha,
    )
}

private fun heartPath(c: Offset, w: Float): Path = Path().apply {
    val h = w * 0.9f
    val top = c.y - h / 2
    moveTo(c.x, top + h * 0.3f)
    cubicTo(c.x, top, c.x - w * 0.5f, top, c.x - w * 0.5f, top + h * 0.3f)
    cubicTo(c.x - w * 0.5f, top + h * 0.6f, c.x, top + h * 0.8f, c.x, top + h)
    cubicTo(c.x, top + h * 0.8f, c.x + w * 0.5f, top + h * 0.6f, c.x + w * 0.5f, top + h * 0.3f)
    cubicTo(c.x + w * 0.5f, top, c.x, top, c.x, top + h * 0.3f)
    close()
}

private fun seg(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun easeOut(t: Float): Float = 1f - (1f - t).pow(3)

private fun easeInOut(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

/** Ease-out with a small overshoot (about 10 %): the "pop". */
private fun backOut(t: Float): Float {
    if (t <= 0f) return 0f
    val c1 = 1.70158f
    val c3 = c1 + 1
    return 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2)
}
