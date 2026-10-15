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

    @Test
    fun countsUseTheRightForm() {
        assertEquals("0 cereri", RomanianText.count(0, "cerere", "cereri"))
        assertEquals("1 cerere", RomanianText.count(1, "cerere", "cereri"))
        assertEquals("19 cereri", RomanianText.count(19, "cerere", "cereri"))
        assertEquals("20 de cereri", RomanianText.count(20, "cerere", "cereri"))
        assertEquals("101 cereri", RomanianText.count(101, "cerere", "cereri"))
        assertEquals("100 de tokeni", RomanianText.count(100, "token", "tokeni"))
        assertEquals("15234 de tokeni", RomanianText.count(15234, "token", "tokeni"))
    }
}
