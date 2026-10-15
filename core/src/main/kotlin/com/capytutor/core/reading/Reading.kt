package com.capytutor.core.reading

import com.capytutor.core.coverage.KeywordMatcher

/*
 * E14 · Reading mode: the lesson as a clean page to read before retelling, with the sentence
 * that carries each key idea highlighted. Everything here runs on the phone, with no model call.
 */

/** One sentence; [ideaIds] are the key ideas it carries (empty for ordinary sentences). */
data class ReadingSentence(val text: String, val ideaIds: List<String> = emptyList()) {
    val isKey: Boolean get() = ideaIds.isNotEmpty()
}

enum class BlockKind { HEADING, PARAGRAPH, LIST_ITEM }

data class ReadingBlock(val kind: BlockKind, val sentences: List<ReadingSentence>) {
    /** The block as one piece of text, for reading aloud. */
    val text: String get() = sentences.joinToString(" ") { it.text }
}

/** A key idea as the reading view needs it: its text and, when generated, its keyword groups. */
data class ReadingIdea(val id: String, val text: String, val keywords: List<List<String>> = emptyList())

object Reading {

    /** Builds the page: blocks of sentences, with each idea's best sentence marked. */
    fun build(lessonText: String, ideas: List<ReadingIdea>): List<ReadingBlock> {
        val blocks = blocks(lessonText)
        val all = blocks.flatMapIndexed { b, block -> block.sentences.indices.map { s -> b to s } }
        val marks = mutableMapOf<Pair<Int, Int>, MutableList<String>>()
        for (idea in ideas) {
            val best = all.map { pos -> pos to score(idea, blocks[pos.first].sentences[pos.second].text) }
                .filter { it.second.good }
                .maxByOrNull { it.second.value }
                ?: continue
            marks.getOrPut(best.first) { mutableListOf() } += idea.id
        }
        return blocks.mapIndexed { b, block ->
            block.copy(sentences = block.sentences.mapIndexed { s, sentence -> sentence.copy(ideaIds = marks[b to s].orEmpty()) })
        }
    }

    // ---- matching ---------------------------------------------------------------------------------

    internal data class Score(val value: Double, val good: Boolean)

    private val stopWords = setOf(
        "care", "este", "sunt", "pentru", "acest", "aceasta", "aceste", "acesta", "aceștia", "dintre", "despre", "prin",
        "spre", "fost", "avea", "aveau", "erau", "foarte", "doar", "unde", "când", "cand", "după", "dupa", "până", "pana",
        "numit", "numită", "numita", "numiți", "numiti", "acum", "apoi", "astfel", "fiind", "toate", "toți", "toti",
    ).map(KeywordMatcher::normalize).toSet()

    private fun contentWords(text: String): List<String> =
        KeywordMatcher.normalize(text).split(' ')
            .filter { (it.length >= 4 || it.any(Char::isDigit)) && it !in stopWords }
            .distinct()

    /**
     * How well [sentence] carries [idea]: the share of the idea's content words found in it
     * (allowing Romanian endings), plus its keyword groups. Good when most of the idea is there.
     */
    internal fun score(idea: ReadingIdea, sentence: String): Score {
        val want = contentWords(idea.text)
        if (want.isEmpty()) return Score(0.0, false)
        val have = contentWords(sentence)
        val found = want.count { w -> have.any { KeywordMatcher.sameWord(it, w) } }
        val groups = if (idea.keywords.isEmpty()) 0 else KeywordMatcher.matchedGroups(sentence, idea.keywords)
        val share = found.toDouble() / want.size
        val groupShare = if (idea.keywords.isEmpty()) 0.0 else groups.toDouble() / idea.keywords.size
        val value = share + 0.5 * groupShare
        val good = (found >= 2 && share >= 0.4) || (found >= 1 && groupShare >= 0.99 && idea.keywords.isNotEmpty())
        return Score(value, good)
    }

    // ---- text -------------------------------------------------------------------------------------

    /** Paragraphs, headings and list items, with Markdown marks removed. */
    fun blocks(text: String): List<ReadingBlock> {
        val out = mutableListOf<ReadingBlock>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotBlank()) out += ReadingBlock(BlockKind.PARAGRAPH, sentences(paragraph.toString()))
            paragraph.clear()
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                line.startsWith("#") -> {
                    flush()
                    out += ReadingBlock(BlockKind.HEADING, listOf(ReadingSentence(inline(line.trimStart('#').trim()))))
                }
                listItem.matches(line) -> {
                    flush()
                    out += ReadingBlock(BlockKind.LIST_ITEM, sentences(listItem.find(line)!!.groupValues[1]))
                }
                line.startsWith("![") || line == "---" || line == "***" -> flush()
                else -> paragraph.append(if (paragraph.isEmpty()) "" else " ").append(line.removePrefix(">").trim())
            }
        }
        flush()
        return out.filter { b -> b.sentences.any { it.text.isNotBlank() } }
    }

    private val listItem = Regex("^(?:[-*+]|\\d+[.)])\\s+(.*)$")
    private val image = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
    private val link = Regex("\\[([^\\]]+)\\]\\([^)]*\\)")
    private val emphasis = Regex("(\\*\\*|__|\\*|_)(\\S(?:.*?\\S)?)\\1")

    /** Plain text: no **bold**, *italics*, links or images. */
    fun inline(s: String): String =
        s.replace(image, "").replace(link, "$1").replace(emphasis, "$2").replace(Regex("\\s+"), " ").trim()

    /** Words that end with a dot without ending the sentence ("î.H.", "sec.", "nr."), folded, without the last dot. */
    private val abbreviations = setOf("î.h", "d.h", "i.h", "î.e.n", "e.n", "sec", "nr", "ex", "cca", "pag", "p", "art", "mil", "mld", "dr", "sf", "st", "etc")

    /** Splits a paragraph into sentences at . ! ? … followed by a space, except after abbreviations and initials. */
    fun sentences(paragraph: String): List<ReadingSentence> {
        val text = inline(paragraph)
        val out = mutableListOf<ReadingSentence>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '.' || ch == '!' || ch == '?' || ch == '…') {
                var end = i + 1
                while (end < text.length && text[end] in ".!?…”\"»)") end++
                val nextIsSpace = end < text.length && text[end].isWhitespace()
                if (nextIsSpace && ch == '.' && isAbbreviation(text, i)) {
                    i = end
                    continue
                }
                if (nextIsSpace || end >= text.length) {
                    text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let { out += ReadingSentence(it) }
                    start = end
                }
                i = end
                continue
            }
            i++
        }
        text.substring(start).trim().takeIf { it.isNotEmpty() }?.let { out += ReadingSentence(it) }
        return out
    }

    private fun isAbbreviation(text: String, dot: Int): Boolean {
        var s = dot
        while (s > 0 && !text[s - 1].isWhitespace() && text[s - 1] != '(') s--
        val word = text.substring(s, dot).lowercase()
        if (word.length == 1 && word[0].isLetter()) return true // an initial: "C. Darwin"
        return word.trimEnd('.') in abbreviations
    }
}
