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
