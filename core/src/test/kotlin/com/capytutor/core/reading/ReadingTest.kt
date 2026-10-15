package com.capytutor.core.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTest {
    private val text = """
        Pământul este a treia planetă de la Soare. Are forma aproape a unei sfere, puțin turtită la poli.

        ### Mișcările Pământului

        Pământul se rotește în jurul axei sale. O rotație durează 24 de ore și produce ziua și noaptea! În același timp,
        Pământul se mișcă în jurul Soarelui; o mișcare completă durează aproximativ 365 de zile, adică un an.

        - Luna este **singurul** satelit natural al Pământului.
        - Vezi [harta](https://example.org) din manual.

        În sec. al XIX-lea, savantul C. Darwin a studiat viețuitoarele. Roma a fost fondată în 753 î.H. și a crescut repede.
    """.trimIndent()

    private val ideas = listOf(
        ReadingIdea("i1", "Pământul este a treia planetă de la Soare."),
        ReadingIdea("i2", "Rotația Pământului în jurul axei durează 24 de ore și produce ziua și noaptea.", listOf(listOf("24"), listOf("ziua", "zi"))),
        ReadingIdea("i3", "Mișcarea Pământului în jurul Soarelui durează aproximativ 365 de zile, adică un an."),
        ReadingIdea("i4", "Luna este singurul satelit natural al Pământului."),
        ReadingIdea("i5", "Vulcanii scot lavă fierbinte."),
    )

    @Test
    fun blocksAndSentencesWithoutMarkdown() {
        val blocks = Reading.blocks(text)
        assertEquals(listOf(BlockKind.PARAGRAPH, BlockKind.HEADING, BlockKind.PARAGRAPH, BlockKind.LIST_ITEM, BlockKind.LIST_ITEM, BlockKind.PARAGRAPH), blocks.map { it.kind })
        assertEquals("Mișcările Pământului", blocks[1].text)
        assertEquals(3, blocks[2].sentences.size) // a line break inside a paragraph does not split it
        assertEquals("Luna este singurul satelit natural al Pământului.", blocks[3].text)
        assertEquals("Vezi harta din manual.", blocks[4].text)
        // "sec.", "C." and "î.H." do not end a sentence.
        assertEquals(
            listOf("În sec. al XIX-lea, savantul C. Darwin a studiat viețuitoarele.", "Roma a fost fondată în 753 î.H. și a crescut repede."),
            blocks[5].sentences.map { it.text },
        )
    }

    @Test
    fun eachIdeaMarksItsBestSentence() {
        val blocks = Reading.build(text, ideas)
        val marked = blocks.flatMap { it.sentences }.filter { it.isKey }.associate { s -> s.ideaIds.single() to s.text }
        assertEquals("Pământul este a treia planetă de la Soare.", marked["i1"])
        assertEquals("O rotație durează 24 de ore și produce ziua și noaptea!", marked["i2"])
        assertTrue(marked["i3"]!!.contains("365 de zile"))
        assertEquals("Luna este singurul satelit natural al Pământului.", marked["i4"])
        assertTrue("an idea the text does not carry marks nothing", "i5" !in marked)
        assertEquals(4, blocks.sumOf { b -> b.sentences.count { it.isKey } })
    }
}
