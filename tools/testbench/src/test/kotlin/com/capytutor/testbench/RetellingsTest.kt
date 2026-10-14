package com.capytutor.testbench

import com.capytutor.core.schema.CoverageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RetellingsTest {

    @Test
    fun parsesIdsLabelsExpectedMarksAndText() {
        val md = """
            # Povestiri
            <!-- comentariu -->
            ## R1 · completă
            expected: i1=covered i2=partial
            păi deci primii oameni
            au trăit în africa

            ## R2 - greșită
            expected: i1=wrong i2=missed
            în america
        """.trimIndent()
        val r = Retellings.parse(md)
        assertEquals(2, r.size)
        assertEquals("R1", r[0].id)
        assertEquals("completă", r[0].label)
        assertEquals("păi deci primii oameni au trăit în africa", r[0].text)
        assertEquals(mapOf("i1" to CoverageStatus.COVERED, "i2" to CoverageStatus.PARTIAL), r[0].expected)
        assertEquals("greșită", r[1].label)
    }

    @Test
    fun sampleRetellingFilesHaveFiveRetellingsWithMarksForEveryIdea() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "lessons").isDirectory }
        val files = File(root, "tools/testbench/samples").listFiles { f -> f.name.endsWith(".retellings.md") }.orEmpty()
        assertEquals(2, files.size)
        for (f in files) {
            val rs = Retellings.parse(f.readText())
            assertEquals(f.name, 5, rs.size)
            for (r in rs) assertTrue("${f.name} ${r.id} marks ${r.expected.size} ideas", r.expected.size == 8)
        }
    }
}
