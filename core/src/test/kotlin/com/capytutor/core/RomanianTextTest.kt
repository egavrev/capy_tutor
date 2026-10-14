package com.capytutor.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RomanianTextTest {

    @Test
    fun cedillaFormsBecomeCommaBelow() {
        assertEquals("Științe și ținuturi", RomanianText.normalizeDiacritics("Ştiinţe şi ţinuturi"))
    }

    @Test
    fun commaBelowTextIsUnchanged() {
        val text = "Apariția omului în Africa, acum 2,5 milioane de ani."
        assertEquals(text, RomanianText.normalizeDiacritics(text))
    }

    @Test
    fun foldingRemovesDiacriticsAndCase() {
        assertEquals("aparitia omului in africa", RomanianText.foldForMatching("Apariția Omului în Africa"))
        assertEquals("stiinte", RomanianText.foldForMatching("Ştiinţe"))
    }
}
