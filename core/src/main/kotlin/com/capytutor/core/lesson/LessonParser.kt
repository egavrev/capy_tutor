package com.capytutor.core.lesson

import com.capytutor.core.RomanianText
import java.security.MessageDigest

sealed interface ParseResult {
    data class Ok(val lesson: Lesson) : ParseResult

    /** [message] is plain Romanian, shown to the parent as is. */
    data class Error(val message: String) : ParseResult
}

/**
 * Parses a lesson Markdown file (HANDOFF.md §6, template in lessons/TEMPLATE-lectie.md).
 *
 * Rules: title required, at least [MIN_WORDS] words of lesson text, and a `## Idei cheie`
 * list with [MIN_IDEAS]..[MAX_IDEAS] items written by the parent. Ideas are never extracted
 * automatically. HTML comments are ignored; cedilla ş ţ become comma-below ș ț.
 */
object LessonParser {
    const val MIN_WORDS = 100
    const val MIN_IDEAS = 3
    const val MAX_IDEAS = 15

    private val htmlComment = Regex("<!--[\\s\\S]*?-->")
    private val listItem = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+(.*)$")
    private val bold = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__")
    private val wordWithLetter = Regex("\\p{L}")

    fun parse(markdown: String): ParseResult {
        val sha = sha256(markdown)
        val clean = RomanianText.normalizeDiacritics(
            markdown.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n'),
        ).replace(htmlComment, "")

        val (meta, body) = splitFrontMatter(clean)
        val lines = body.lines()

        // Title: front matter first, then the first "# " heading.
        val h1Index = lines.indexOfFirst { it.startsWith("# ") }
        val title = meta["title"]?.takeIf { it.isNotBlank() }
            ?: lines.getOrNull(h1Index)?.removePrefix("# ")?.trim()
        if (title.isNullOrBlank()) {
            return ParseResult.Error("Lecția nu are titlu. Adaugă sus „title: …” sau un rând care începe cu „# ”.")
        }

        val subjectLabel = meta["subject"]
        val subject = subjectLabel?.let { Subject.fromLabel(it) }
            ?: return ParseResult.Error(
                if (subjectLabel.isNullOrBlank()) {
                    "Lipsește materia. Adaugă sus „subject: Istorie” (sau Română, Matematică, Științe, Geografie)."
                } else {
                    "Materia „$subjectLabel” nu e cunoscută. Folosește Istorie, Română, Matematică, Științe sau Geografie."
                },
            )

        // Sections start at "## ". Lesson text = everything before the first one (minus the H1).
        val sections = linkedMapOf<String, MutableList<String>>()
        val textLines = mutableListOf<String>()
        var current: MutableList<String>? = null
        lines.forEachIndexed { i, line ->
            when {
                line.startsWith("## ") -> {
                    current = mutableListOf<String>().also { sections[sectionKey(line)] = it }
                }
                current != null -> current!!.add(line)
                i != h1Index -> textLines.add(line)
            }
        }

        val text = textLines.joinToString("\n").trim().replace(Regex("\n{3,}"), "\n\n")
        val words = countWords(text)
        if (words < MIN_WORDS) {
            return ParseResult.Error("Textul lecției e prea scurt: are $words cuvinte, trebuie cel puțin $MIN_WORDS.")
        }

        val ideaLines = sections[IDEAS_KEY]
            ?: return ParseResult.Error(
                "Lipsește lista „## Idei cheie”. Scrie ideile cheie ale lecției, câte una pe rând, cu „- ” în față.",
            )
        val ideas = ideaLines.mapNotNull { listItem.find(it)?.groupValues?.get(1) }
            .map { stripBold(it).trim() }
            .filter { it.isNotEmpty() }
        if (ideas.isEmpty()) {
            return ParseResult.Error(
                "Lista „## Idei cheie” e goală. Scrie ideile cheie ale lecției, câte una pe rând, cu „- ” în față.",
            )
        }
        if (ideas.size !in MIN_IDEAS..MAX_IDEAS) {
            return ParseResult.Error(
                "Lista „Idei cheie” are ${ideas.size} ${if (ideas.size == 1) "idee" else "idei"}. Trebuie între $MIN_IDEAS și $MAX_IDEAS.",
            )
        }

        val terms = sections[TERMS_KEY].orEmpty()
            .mapNotNull { listItem.find(it)?.groupValues?.get(1) }
            .mapNotNull { parseTerm(it) }

        return ParseResult.Ok(
            Lesson(
                subject = subject,
                grade = meta["grade"]?.toIntOrNull(),
                chapter = meta["chapter"]?.takeIf { it.isNotBlank() },
                title = title,
                language = meta["language"]?.takeIf { it.isNotBlank() } ?: "ro",
                text = text,
                ideas = ideas,
                terms = terms,
                sha256 = sha,
            ),
        )
    }

    fun countWords(text: String): Int =
        text.split(Regex("\\s+")).count { wordWithLetter.containsMatchIn(it) }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private const val IDEAS_KEY = "idei cheie"
    private const val TERMS_KEY = "termeni"

    private fun sectionKey(heading: String): String =
        RomanianText.foldForMatching(heading.removePrefix("## ").trim().trimEnd(':'))
            .replace(Regex("\\s+"), " ")

    /** Front matter between two "---" lines at the top. Values may carry "# comments". */
    private fun splitFrontMatter(text: String): Pair<Map<String, String>, String> {
        val lines = text.trimStart('\n').lines()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap<String, String>() to text
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return emptyMap<String, String>() to text
        val meta = lines.subList(1, end + 1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val key = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).replace(Regex("\\s+#.*$"), "").trim().trim('"', '\'')
            key to value
        }.toMap()
        return meta to lines.drop(end + 2).joinToString("\n")
    }

    private fun stripBold(s: String): String = s.replace(bold) { it.groupValues[1].ifEmpty { it.groupValues[2] } }

    private fun parseTerm(item: String): Term? {
        val plain = stripBold(item)
        val colon = plain.indexOf(':')
        if (colon <= 0) return null
        val term = plain.substring(0, colon).trim()
        val def = plain.substring(colon + 1).trim()
        return if (term.isEmpty() || def.isEmpty()) null else Term(term, def)
    }
}
