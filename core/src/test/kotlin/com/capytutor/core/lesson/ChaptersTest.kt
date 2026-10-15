package com.capytutor.core.lesson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChaptersTest {
    @Test
    fun romanUnitsAreOrderedLikeTheBook() {
        val chapters = listOf(
            "Unitatea VII. Declinul lumii antice",
            "Unitatea II. Epoca Antică. Orientul Antic",
            null,
            "Unitatea I. Preistorie",
            "Unitatea IV. Grecia Antică",
            "Unitatea VI. Dacii și romanii",
        )
        assertEquals(
            listOf(
                "Unitatea I. Preistorie",
                "Unitatea II. Epoca Antică. Orientul Antic",
                "Unitatea IV. Grecia Antică",
                "Unitatea VI. Dacii și romanii",
                "Unitatea VII. Declinul lumii antice",
                null,
            ),
            chapters.sortedWith(Chapters.order),
        )
    }

    @Test
    fun numbers() {
        assertEquals(9, Chapters.number("Unitatea IX. Ceva"))
        assertEquals(3, Chapters.number("Capitolul 3"))
        assertNull(Chapters.number("Recapitulare"))
        assertEquals(14, Chapters.romanToInt("XIV"))
    }
}
