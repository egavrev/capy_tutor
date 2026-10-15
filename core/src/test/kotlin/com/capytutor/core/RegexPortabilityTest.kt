package com.capytutor.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android compiles regexes with ICU, which is stricter than the desktop JVM these tests run on:
 * a literal "{" or "}" must be escaped unless it is a quantifier like {2,} (a bare "}}" crashed
 * every model call on the phone). This scans the Regex("…") literals in :core and :ai.
 */
class RegexPortabilityTest {

    private val literal = Regex("""Regex\(\s*"((?:[^"\\]|\\.)*)"""")
    private val quantifier = Regex("""\{\d+(,\d*)?\}""")

    /** Braces that are neither escaped nor part of a quantifier. */
    fun bareBraces(raw: String): List<Int> {
        val pattern = raw.replace(Regex("""\\[pP]\{[A-Za-z_]+\}"""), "P")
        val quantified = quantifier.findAll(pattern).flatMap { listOf(it.range.first, it.range.last) }.toSet()
        val bad = mutableListOf<Int>()
        var i = 0
        while (i < pattern.length) {
            val ch = pattern[i]
            if (ch == '\\') {
                i += 2
                continue
            }
            if ((ch == '{' || ch == '}') && i !in quantified) bad += i
            i++
        }
        return bad
    }

    @Test
    fun theCheckerItselfWorks() {
        assertTrue(bareBraces("""\{\{\s*(\w+)\s*}}""").isNotEmpty())
        assertTrue(bareBraces("""\{\{\s*(\w+)\s*\}\}""").isEmpty())
        assertTrue(bareBraces("""(\p{L})\1{2,}""").isEmpty())
        assertTrue(bareBraces("""\p{L}{2,4}""").isEmpty())
    }

    @Test
    fun everyRegexInTheCodeCompilesOnAndroid() {
        val roots = listOf(File("src/main/kotlin"), File("../ai/src/main/kotlin")).filter { it.isDirectory }
        assertTrue("source folders not found from ${File(".").absolutePath}", roots.isNotEmpty())
        val problems = roots.flatMap { root -> root.walk().filter { it.extension == "kt" }.toList() }.flatMap { f ->
            literal.findAll(f.readText()).mapNotNull { m ->
                // Kotlin string → regex text; "$" templates are left as they are (they hold escaped names).
                val pattern = m.groupValues[1].replace("\\\\", "\u0000").replace("\\\"", "\"").replace("\u0000", "\\")
                val bad = bareBraces(pattern)
                if (bad.isEmpty()) null else "${f.name}: $pattern"
            }.toList()
        }
        assertTrue("Escape { and } in these patterns (Android ICU):\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
