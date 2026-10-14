package md.capy.tutore.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import md.capy.tutore.R

/** Nunito, bundled in res/font (SIL Open Font License). Romanian ș ț with comma below are covered. */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

@Immutable
data class CapyTypography(
    val display: TextStyle,
    val title: TextStyle,
    val question: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Nunito, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

val CapyType = CapyTypography(
    display = style(28, 36, FontWeight.Bold),  // e.g. "8 din 10"
    title = style(22, 30, FontWeight.SemiBold),  // e.g. "Apariția omului"
    question = style(20, 28, FontWeight.SemiBold),  // e.g. "În ce țară a fost găsită Lucy?"
    body = style(17, 26, FontWeight.Normal),  // e.g. "Povestește-mi cu cuvintele tale tot ce ții minte din lecție."
    label = style(15, 20, FontWeight.SemiBold),  // e.g. "Hai să începem"
    caption = style(13, 18, FontWeight.Normal),  // e.g. "Pasul 3 din 4 · cca 5 minute"
)

val LocalCapyTypography = staticCompositionLocalOf { CapyType }
