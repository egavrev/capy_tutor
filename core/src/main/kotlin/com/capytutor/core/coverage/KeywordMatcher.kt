package com.capytutor.core.coverage

import com.capytutor.core.RomanianText

/**
 * The free, on-phone keyword pass (HANDOFF.md §7.1).
 *
 * An idea's keywords are *groups*: each group is one concept with its equivalent forms
 * (synonyms, inflections), e.g. `[["Etiopia"], ["Lucy", "Lusi"], ["fosilă", "fosile"]]`.
 * A group matches when any of its forms appears in the text. All groups matching makes the
 * idea a *candidate* `covered`; only the LLM pass may decide missed, partial or wrong.
 *
 * Matching ignores case and diacritics, drops filler words, and accepts inflected endings by
 * comparing word stems (a form of 5+ letters matches any word starting with all but its last
 * two letters, so "Etiopia" also matches "Etiopiei").
 */
object KeywordMatcher {

    /** Filler a child says while thinking ("păi", "deci", "ăă"...). Already folded. */
    private val fillerWords = setOf("pai", "deci", "aa", "aaa", "aaaa", "ii", "iii", "mm", "mmm", "hm", "hmm", "adica", "gen", "na")
    private val fillerPhrases = listOf("si apoi", "si dupa aia", "dupa aia")

    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val elongated = Regex("(\\p{L})\\1{2,}")

    /** Lower case, no diacritics, no punctuation, no filler. Words separated by single spaces. */
    fun normalize(text: String): String {
        var s = RomanianText.foldForMatching(text).replace(nonWord, " ")
        for (p in fillerPhrases) s = " $s ".replace(" $p ", " ")
        return s.split(' ')
            .filter { it.isNotBlank() && it !in fillerWords && !it.matches(elongated) }
            .joinToString(" ")
    }

    fun groupMatches(normalizedText: String, group: List<String>): Boolean {
        val tokens = normalizedText.split(' ').filter { it.isNotEmpty() }
        return group.any { form -> formMatches(tokens, normalize(form)) }
    }

    /** True when every keyword group matches: the idea is a candidate `covered`. */
    fun isCandidateCovered(text: String, groups: List<List<String>>): Boolean {
        if (groups.isEmpty()) return false
        val normalized = normalize(text)
        return groups.all { groupMatches(normalized, it) }
    }

    /** How many groups match, for reports. */
    fun matchedGroups(text: String, groups: List<List<String>>): Int {
        val normalized = normalize(text)
        return groups.count { groupMatches(normalized, it) }
    }

    private fun formMatches(tokens: List<String>, form: String): Boolean {
        if (form.isBlank()) return false
        val words = form.split(' ')
        // Every word of the form must match some token (order-free, so "primii oameni" ≈ "oamenii primii").
        return words.all { w -> tokens.any { t -> wordMatches(t, w) } }
    }

    private fun wordMatches(token: String, word: String): Boolean {
        if (token == word) return true
        if (word.length < 5 || token.length < 4) return false
        val stem = word.take(maxOf(4, word.length - 2))
        return token.startsWith(stem)
    }
}
