package com.capytutor.core.safety

import com.capytutor.core.RomanianText

/**
 * Runs on every piece of the child's text before it is sent anywhere (HANDOFF.md §7).
 * Replaces the child's name with "copilul" and removes anything that looks like an email,
 * a phone number or a street address.
 */
class SafetyFilter(childNames: Collection<String>) {

    private val nameRegexes: List<Regex> = childNames
        .map { it.trim() }
        .filter { it.length >= 2 }
        .map { name ->
            // Match the name with or without diacritics, plus Romanian case endings (Vali, Valiei, Valei).
            val folded = RomanianText.foldForMatching(name)
            val alternatives = setOf(Regex.escape(name), Regex.escape(folded)).joinToString("|")
            Regex("(?<![\\p{L}\\p{N}])(?:$alternatives)(?:ei|iei|ului|i)?(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        }

    fun clean(text: String): String {
        var s = text
        for (r in nameRegexes) s = r.replace(s, CHILD)
        s = email.replace(s, REMOVED)
        s = phoneCandidate.replace(s) { m -> if (m.value.count(Char::isDigit) >= 8) REMOVED else m.value }
        s = address.replace(s, REMOVED)
        return s
    }

    companion object {
        const val CHILD = "copilul"
        const val REMOVED = "[ascuns]"

        private val email = Regex("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}")
        private val phoneCandidate = Regex("\\+?\\d[\\d\\s().-]{6,}\\d")
        // Street word + up to 4 name words + optional number and flat. Over-removing a few words
        // of a child's sentence is fine; leaking an address is not.
        private val address = Regex(
            "(?<!\\p{L})(?:strada|str\\.|bulevardul|bd\\.|șoseaua|soseaua|șos\\.|sos\\.|aleea)" +
                "(?:\\s+(?!nr\\b)[\\p{L}\\-]+){1,4}(?:,?\\s*(?:nr\\.?\\s*)?\\d+[a-z]?)?(?:,?\\s*(?:ap\\.?|apartamentul)\\s*\\d+)?",
            RegexOption.IGNORE_CASE,
        )
    }
}
