package com.capytutor.core.session

import com.capytutor.core.RomanianText
import com.capytutor.core.coverage.KeywordMatcher
import com.capytutor.core.pack.PackIdea

/**
 * The hint ladder must not give the answer before step 3 (HANDOFF.md §3). The generated broad
 * question and clue usually respect that, but not always (test bench, M1). This checks each one
 * on the phone and replaces a hint that contains a word of the answer with a safe one.
 */
data class SafeHints(val broadQuestion: String, val clue: String, val broadReplaced: Boolean, val clueReplaced: Boolean) {
    companion object {
        fun of(idea: PackIdea): SafeHints {
            val broadLeaks = KeywordMatcher.revealedWord(idea.broadQuestion, idea.checkAnswer) != null
            val checkLeaks = KeywordMatcher.revealedWord(idea.checkQuestion, idea.checkAnswer) != null
            val broad = when {
                !broadLeaks -> idea.broadQuestion
                !checkLeaks -> idea.checkQuestion
                else -> GENERIC_BROAD
            }
            val clueLeaks = KeywordMatcher.revealedWord(idea.clue, idea.checkAnswer) != null
            val clue = if (clueLeaks) clozeClue(idea, broad) ?: letterClue(idea.checkAnswer) else idea.clue
            return SafeHints(broad, clue, broadLeaks, clueLeaks)
        }

        /**
         * A fill-in-the-blank of the idea: the words of every keyword group not already named in
         * [asked] become "…". "Anotimpurile apar pentru că … Pământului este …." Null when
         * nothing (or everything) would be blanked.
         */
        fun clozeClue(idea: PackIdea, asked: String): String? {
            val q = KeywordMatcher.normalize(asked)
            val hidden = idea.keywords.filterNot { KeywordMatcher.groupMatches(q, it) }
                .flatten()
                .flatMap { KeywordMatcher.normalize(it).split(' ') }
                .filter { it.length >= 3 || (it.isNotEmpty() && it.all(Char::isDigit)) }
                .toSet()
            if (hidden.isEmpty()) return null
            var words = 0
            var blanks = 0
            val text = Regex("[\\p{L}\\p{N}]+(?:,[0-9]+)?%?|[^\\p{L}\\p{N}]+").findAll(idea.text).joinToString("") { m ->
                val tok = m.value
                if (!tok.first().isLetterOrDigit()) return@joinToString tok
                words++
                val parts = KeywordMatcher.normalize(tok).split(' ').filter { it.isNotEmpty() }
                if (parts.any { p -> hidden.any { h -> KeywordMatcher.sameWord(p, h) } }) { blanks++; "…" } else tok
            }
            if (blanks == 0 || blanks == words) return null
            return "Completează: „" + text.trim().replace(Regex("…(?:\\s+…)+"), "…") + "”"
        }

        const val GENERIC_BROAD = "Ce îți mai amintești din lecție despre partea asta?"

        /** "Răspunsul începe cu litera E." or, for a number, "Răspunsul conține un număr." */
        fun letterClue(answer: String): String {
            val words = answer.split(Regex("[\\s,.;:!?„”\"()]+")).filter { it.isNotBlank() }
            if (words.any { w -> w.any(Char::isDigit) }) return "Răspunsul conține un număr. Gândește-te la el."
            val content = words.firstOrNull { w -> RomanianText.foldForMatching(w).let { it.length >= 4 && it !in skip } }
                ?: words.firstOrNull()
                ?: return GENERIC_CLUE
            return "Primul cuvânt important din răspuns începe cu litera „${content.first().uppercaseChar()}”."
        }

        const val GENERIC_CLUE = "Gândește-te la ce ai citit în lecție despre asta."
        private val skip = setOf("este", "sunt", "care", "pentru", "aproximativ", "aproape", "adica", "foarte", "acum", "doar")
    }
}
