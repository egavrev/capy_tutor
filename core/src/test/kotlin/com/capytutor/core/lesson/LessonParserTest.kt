package com.capytutor.core.lesson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LessonParserTest {

    private val longText = (1..30).joinToString(" ") { "Primii oameni au trăit în Africa." }

    private fun lesson(ideas: String, front: String = "subject: Istorie\ntitle: Apariția omului") = """
        |---
        |$front
        |---
        |
        |# Apariția omului
        |
        |$longText
        |
        |## Idei cheie
        |
        |$ideas
        |
        |## Termeni
        |
        |- **fosilă**: urmă păstrată în piatră
    """.trimMargin()

    private fun ok(md: String): Lesson {
        val r = LessonParser.parse(md)
        assertTrue("expected Ok, got $r", r is ParseResult.Ok)
        return (r as ParseResult.Ok).lesson
    }

    private fun error(md: String): String {
        val r = LessonParser.parse(md)
        assertTrue("expected Error, got $r", r is ParseResult.Error)
        return (r as ParseResult.Error).message
    }

    @Test
    fun parsesFrontMatterTextIdeasAndTerms() {
        val l = ok(lesson("- Prima idee.\n- A doua idee.\n- A treia idee."))
        assertEquals(Subject.ISTORIE, l.subject)
        assertEquals("Apariția omului", l.title)
        assertEquals(listOf("Prima idee.", "A doua idee.", "A treia idee."), l.ideas)
        assertEquals(listOf(Term("fosilă", "urmă păstrată în piatră")), l.terms)
        assertTrue(l.text.startsWith("Primii oameni"))
        assertTrue("H1 must not be part of the text", !l.text.contains("# Apariția"))
        assertEquals(64, l.sha256.length)
    }

    @Test
    fun ideasWithoutListAreRejectedInRomanian() {
        val md = lesson("Primii oameni au apărut în Africa.") // no "- " in front
        assertTrue(error(md).contains("Idei cheie"))
    }

    @Test
    fun missingIdeasSectionIsRejected() {
        val md = "---\nsubject: Istorie\ntitle: T\n---\n\n$longText\n"
        assertTrue(error(md).startsWith("Lipsește lista „## Idei cheie”"))
    }

    @Test
    fun ideaCountMustBeBetween3And15() {
        assertTrue(error(lesson("- Una.\n- Două.")).contains("are 2 idei"))
        val sixteen = (1..16).joinToString("\n") { "- Idee $it." }
        assertTrue(error(lesson(sixteen)).contains("are 16 idei"))
        assertEquals(15, ok(lesson((1..15).joinToString("\n") { "- Idee $it." })).ideas.size)
    }

    @Test
    fun shortTextIsRejected() {
        val md = "---\nsubject: Istorie\ntitle: T\n---\n\nPrea scurt.\n\n## Idei cheie\n\n- A.\n- B.\n- C.\n"
        assertTrue(error(md).contains("prea scurt"))
    }

    @Test
    fun htmlCommentsAreIgnoredAndCedillasNormalised() {
        val md = lesson("<!-- comentariu - nu e idee -->\n- Ştiinţa e frumoasă.\n- B.\n- C.")
        val l = ok(md)
        assertEquals("Știința e frumoasă.", l.ideas.first())
        assertEquals(3, l.ideas.size)
    }

    @Test
    fun subjectCommentsInFrontMatterAreStripped() {
        val l = ok(lesson("- A.\n- B.\n- C.", front = "subject: Geografie          # Istorie | Geografie\ntitle: Pământul"))
        assertEquals(Subject.GEOGRAFIE, l.subject)
    }

    @Test
    fun unknownSubjectIsRejected() {
        assertTrue(error(lesson("- A.\n- B.\n- C.", front = "subject: Fizică\ntitle: T")).contains("Fizică"))
    }

    @Test
    fun titleFallsBackToFirstHeading() {
        val l = ok(lesson("- A.\n- B.\n- C.", front = "subject: Istorie"))
        assertEquals("Apariția omului", l.title)
    }

    @Test
    fun sampleLessonsInTheRepoParse() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "lessons").isDirectory }
        val samples = File(root, "lessons").walk().filter { it.name.startsWith("SAMPLE-") && it.extension == "md" }.toList()
        assertEquals(2, samples.size)
        for (f in samples) {
            val l = ok(f.readText())
            assertTrue("${f.name}: ${l.ideas.size} ideas", l.ideas.size in 3..15)
            assertTrue("${f.name}: ${l.wordCount} words", l.wordCount >= 100)
        }
    }
}
