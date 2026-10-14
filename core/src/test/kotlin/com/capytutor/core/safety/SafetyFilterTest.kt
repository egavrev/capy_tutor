package com.capytutor.core.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyFilterTest {

    private val filter = SafetyFilter(listOf("Vali", "Valeria"))

    @Test
    fun childNameBecomesCopilul() {
        assertEquals("eu copilul am citit lecția", filter.clean("eu Vali am citit lecția"))
        assertEquals("mama copilul zice", filter.clean("mama valeria zice"))
        assertEquals("caietul copilul", filter.clean("caietul Valiei"))
    }

    @Test
    fun nameInsideOtherWordsIsKept() {
        assertEquals("valiza e grea", filter.clean("valiza e grea"))
    }

    @Test
    fun emailAndPhoneAreRemoved() {
        val out = filter.clean("scrie la mama.pop@gmail.com sau sună la +373 69 123 456")
        assertFalse(out.contains("@"))
        assertFalse(out.contains("123"))
        assertTrue(out.contains(SafetyFilter.REMOVED))
    }

    @Test
    fun yearsAndLessonNumbersStay() {
        val text = "Lucy a fost găsită în 1974 și are 3,2 milioane de ani, 40 000 de kilometri"
        assertEquals(text, filter.clean(text))
    }

    @Test
    fun streetAddressIsRemoved() {
        val out = filter.clean("eu stau pe strada Ștefan cel Mare nr. 12 ap 5 lângă școală")
        assertFalse(out, out.contains("Ștefan"))
        assertFalse(out, out.contains("12"))
        assertTrue(out, out.endsWith("lângă școală"))
    }
}
