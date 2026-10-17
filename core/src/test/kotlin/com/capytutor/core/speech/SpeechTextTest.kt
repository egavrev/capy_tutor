package com.capytutor.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Sentences as they appear in the lessons, and how the phone's voice should get them. */
class SpeechTextTest {
    private fun voice(s: String) = SpeechText.forVoice(s)

    @Test
    fun beforeAndAfterChrist() {
        assertEquals("În 44 înainte de Hristos, în urma unui complot", voice("În 44 î.H., în urma unui complot"))
        assertEquals("A fost finalizat în 550 înainte de Hristos. În interior", voice("A fost finalizat în 550 î.H. În interior"))
        assertEquals("în iarna anului 85 până la 86 după Hristos dacii", voice("în iarna anului 85–86 d.H. dacii"))
        assertEquals("anul 106 după Hristos.", voice("anul 106 d.Hr."))
        assertEquals("în 1000 înaintea erei noastre.", voice("în 1000 î.e.n."))
    }

    @Test
    fun rulersAndCenturies() {
        assertEquals("Ramses al doilea (1279 până la 1213 înainte de Hristos), avea", voice("Ramses al II-lea (1279–1213 î.H.), avea"))
        assertEquals("Faraonul Tutmes al treilea a domnit", voice("Faraonul Tutmes al III-lea a domnit"))
        assertEquals("Columna a fost restaurată în secolul al șaisprezecelea", voice("Columna a fost restaurată în sec. al XVI-lea"))
        assertEquals("în a doua jumătate a secolului al patrulea", voice("în a doua jumătate a sec. IV"))
        assertEquals("a cuprins secolul al patrulea până la al cincilea.", voice("a cuprins secolul IV–V."))
        assertEquals("secolele nouă și opt înainte de Hristos.", voice("secolele IX și VIII î.H."))
        assertEquals("La răscrucea mileniilor doi până la unu înainte de Hristos,", voice("La răscrucea mileniilor II–I î.H.,"))
        assertEquals("în mileniul întâi", voice("în mileniul I"))
        assertEquals("Dinastia a nouăsprezecea", voice("Dinastia a XIX-a"))
        assertEquals("regele persan, Darius al treilea, care", voice("regele persan, Darius III, care"))
    }

    @Test
    fun abbreviationsAndRanges() {
        assertEquals("Domnia lui Așoka (circa 273 până la 232 înainte de Hristos)", voice("Domnia lui Așoka (cca 273–232 î.H.)"))
        assertEquals("marșuri de peste 17.700 de kilometri, de", voice("marșuri de peste 17.700 km, de"))
        assertEquals("la 5 kilometri de mare", voice("la 5 km de mare"))
        assertEquals("rege etcetera. Scribii", voice("rege etc. Scribii"))
        assertEquals("i-a învățat logica.", voice("i-a învățat logica […]."))
    }

    @Test
    fun ordinaryTextIsLeftAlone() {
        val s = "Vitamina C, Planeta Marte și Imperiul Roman. Ion a venit; 10 °C iarna."
        assertEquals(s, voice(s))
    }

    @Test
    fun numbersInWords() {
        assertEquals(14, SpeechText.roman("XIV"))
        assertNull(SpeechText.roman("IIII"))
        assertNull(SpeechText.roman("CIVIL"))
        assertEquals("al doilea", SpeechText.masculineOrdinal(2))
        assertEquals("al optulea", SpeechText.masculineOrdinal(8))
        assertEquals("al unsprezecelea", SpeechText.masculineOrdinal(11))
        assertEquals("al douăzecilea", SpeechText.masculineOrdinal(20))
        assertEquals("al douăzeci și unulea", SpeechText.masculineOrdinal(21))
        assertEquals("a cincea", SpeechText.feminineOrdinal(5))
        assertEquals("a douăsprezecea", SpeechText.feminineOrdinal(12))
        assertEquals("douăzeci și trei", SpeechText.cardinal(23))
    }
}
