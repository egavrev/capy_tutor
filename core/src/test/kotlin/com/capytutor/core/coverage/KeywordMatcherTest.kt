package com.capytutor.core.coverage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordMatcherTest {

    private val lucy = listOf(listOf("Lucy", "Lusi"), listOf("Etiopia"), listOf("fosilă", "fosile", "schelet"))

    @Test
    fun normalizeDropsDiacriticsPunctuationAndFiller() {
        assertEquals("primii oameni au trait in africa", KeywordMatcher.normalize("Păi, deci... ăăă primii oameni au trăit în Africa!"))
    }

    @Test
    fun allGroupsPresentMakesACandidate() {
        assertTrue(KeywordMatcher.isCandidateCovered("lusi e o fosila gasita in etiopia", lucy))
    }

    @Test
    fun missingGroupIsNotACandidate() {
        assertFalse(KeywordMatcher.isCandidateCovered("lusi e o fosila", lucy))
        assertEquals(2, KeywordMatcher.matchedGroups("lusi e o fosila", lucy))
    }

    @Test
    fun inflectedFormsMatchByStem() {
        assertTrue(KeywordMatcher.isCandidateCovered("scheletul a fost gasit in Etiopiei", listOf(listOf("Etiopia"), listOf("schelet"))))
    }

    @Test
    fun shortWordsMustMatchExactly() {
        // "an" must not match "ani" or "anotimp"
        assertFalse(KeywordMatcher.isCandidateCovered("anotimpuri", listOf(listOf("an"))))
        assertTrue(KeywordMatcher.isCandidateCovered("un an are 365 de zile", listOf(listOf("an"), listOf("365"))))
    }

    @Test
    fun multiWordFormsMatchInAnyOrder() {
        assertTrue(KeywordMatcher.isCandidateCovered("oamenii primii au trait in africa", listOf(listOf("primii oameni"), listOf("Africa"))))
    }

    @Test
    fun emptyGroupsAreNeverCovered() {
        assertFalse(KeywordMatcher.isCandidateCovered("orice", emptyList()))
    }
}

class RomanianNumbersTest {
    private fun n(s: String) = KeywordMatcher.normalize(s)

    @Test
    fun spokenNumbersBecomeDigits() {
        assertEquals("24 de ore", n("douăzeci și patru de ore"))
        assertEquals("365 de zile", n("trei sute șaizeci și cinci de zile"))
        assertEquals("71 la suta", n("șaptezeci și unu la sută"))
        assertEquals("100", n("o sută"))
        assertEquals("2026", n("două mii douăzeci și șase"))
        assertEquals("3 2 milioane de ani", n("trei virgulă doi milioane de ani"))
        assertEquals(n("3,2 milioane"), n("trei virgulă doi milioane"))
        assertEquals("2 si 3", n("doi și trei"))
        assertEquals("12", n("doisprezece"))
    }

    @Test
    fun digitsInLessonAndWordsInSpeechMatch() {
        assertTrue(KeywordMatcher.isCandidateCovered("se învârte în douăzeci și patru de ore", listOf(listOf("24 de ore"))))
        assertTrue(KeywordMatcher.isCandidateCovered("apa e șaptezeci și unu la sută", listOf(listOf("71%"))))
    }

    @Test
    fun revealedWordFindsAnswersInHints() {
        assertEquals("ecuator", KeywordMatcher.revealedWord("Se numește Ecuator", "Ecuator"))
        assertEquals("incalzea", KeywordMatcher.revealedWord("Îi încălzea și îi ținea departe de prădători.", "încălzea și apăra de animale"))
        assertEquals("2", KeywordMatcher.revealedWord("Acum două milioane și jumătate de ani", "2,5 milioane de ani"))
        assertEquals(null, KeywordMatcher.revealedWord("Este un continent cald, cu savane.", "în Africa"))
        assertEquals(null, KeywordMatcher.revealedWord("Începe cu litera P.", "paleolitic"))
    }
}
