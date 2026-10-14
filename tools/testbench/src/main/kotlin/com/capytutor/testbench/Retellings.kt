package com.capytutor.testbench

import com.capytutor.core.RomanianText
import com.capytutor.core.schema.CoverageStatus

/** One sample retelling, optionally with the parent's expected mark for each idea. */
data class Retelling(
    val id: String,
    val label: String,
    val text: String,
    val expected: Map<String, CoverageStatus>,
)

/**
 * Retellings file format (see tools/testbench/samples/):
 *
 *     ## R1 · povestire completă
 *     expected: i1=covered i2=partial i3=missed
 *
 *     păi deci primii oameni au trăit în africa ...
 *
 * HTML comments are ignored. The text may span several lines.
 */
object Retellings {
    private val htmlComment = Regex("<!--[\\s\\S]*?-->")
    private val expectedPair = Regex("(i\\d+)\\s*=\\s*([a-zA-Z]+)")

    fun parse(markdown: String): List<Retelling> {
        val clean = RomanianText.normalizeDiacritics(markdown.replace("\r\n", "\n")).replace(htmlComment, "")
        val blocks = mutableListOf<Pair<String, MutableList<String>>>()
        for (line in clean.lines()) {
            if (line.startsWith("## ")) blocks += line.removePrefix("## ").trim() to mutableListOf()
            else blocks.lastOrNull()?.second?.add(line)
        }
        return blocks.map { (heading, lines) ->
            val id = heading.substringBefore(' ').trim()
            val label = heading.substringAfter(' ', "").trim().trimStart('·', '-', ':').trim()
            val expectedLine = lines.firstOrNull { it.trim().startsWith("expected:", ignoreCase = true) }
            val expected = expectedLine?.let { l ->
                expectedPair.findAll(l).associate { m ->
                    m.groupValues[1] to (CoverageStatus.fromWire(m.groupValues[2])
                        ?: error("$id: unknown expected status ${m.groupValues[2]}"))
                }
            }.orEmpty()
            val text = lines.filter { it !== expectedLine }.joinToString(" ") { it.trim() }.replace(Regex("\\s+"), " ").trim()
            require(text.isNotEmpty()) { "$id has no text" }
            Retelling(id, label, text, expected)
        }
    }
}
