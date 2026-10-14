package com.capytutor.core.coverage

/**
 * Turns spoken Romanian numbers into digits so the keyword pass can compare them with lesson
 * numbers: "douazeci si patru" → 24, "trei sute saizeci si cinci" → 365,
 * "trei virgula doi" → "3 2" (same tokens as "3,2" after punctuation is removed).
 *
 * Works on tokens that are already lower case and without diacritics. Words like "milioane"
 * stay as words; only the number in front of them is converted.
 */
object RomanianNumbers {

    private val units = mapOf(
        "zero" to 0, "unu" to 1, "una" to 1, "un" to 1, "doi" to 2, "doua" to 2, "trei" to 3, "patru" to 4,
        "cinci" to 5, "sase" to 6, "sapte" to 7, "opt" to 8, "noua" to 9,
    )
    private val teens = mapOf(
        "zece" to 10, "unsprezece" to 11, "doisprezece" to 12, "douasprezece" to 12, "treisprezece" to 13,
        "paisprezece" to 14, "patrusprezece" to 14, "cincisprezece" to 15, "saisprezece" to 16,
        "saptesprezece" to 17, "optsprezece" to 18, "nouasprezece" to 19,
    )
    private val tens = mapOf(
        "douazeci" to 20, "treizeci" to 30, "patruzeci" to 40, "cincizeci" to 50, "saizeci" to 60,
        "saptezeci" to 70, "optzeci" to 80, "nouazeci" to 90,
    )

    fun convert(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val parsed = parse(tokens, i)
            if (parsed == null) {
                out += tokens[i]
                i++
                continue
            }
            out += parsed.first.toString()
            i = parsed.second
            // "trei virgula doi" → "3 2"
            if (tokens.getOrNull(i) == "virgula") {
                val decimal = parse(tokens, i + 1)
                if (decimal != null) {
                    out += decimal.first.toString()
                    i = decimal.second
                }
            }
        }
        return out
    }

    /** Parses a number starting at [start]: (value, index after it), or null if there is none. */
    private fun parse(t: List<String>, start: Int): Pair<Int, Int>? {
        var i = start
        var total = 0
        var any = false

        fun below100(): Int? {
            val w = t.getOrNull(i) ?: return null
            teens[w]?.let { i++; return it }
            tens[w]?.let { ten ->
                i++
                if (t.getOrNull(i) == "si") units[t.getOrNull(i + 1)]?.takeIf { it in 1..9 }?.let { u -> i += 2; return ten + u }
                return ten
            }
            units[w]?.let { i++; return it }
            return null
        }

        fun below1000(): Int? {
            val save = i
            // "o suta", "doua sute"
            val hundredsCount = when {
                t.getOrNull(i) == "o" && t.getOrNull(i + 1) == "suta" -> { i += 2; 1 }
                units[t.getOrNull(i)] != null && t.getOrNull(i + 1) in setOf("suta", "sute") -> {
                    val c = units.getValue(t[i]); i += 2; c
                }
                else -> 0
            }
            val rest = below100()
            if (hundredsCount == 0 && rest == null) { i = save; return null }
            return hundredsCount * 100 + (rest ?: 0)
        }

        val first = below1000()
        if (first != null) {
            any = true
            if (t.getOrNull(i) in setOf("mii", "mie")) {
                total += first * 1000
                i++
                below1000()?.let { total += it }
            } else {
                total += first
            }
        } else if (t.getOrNull(i) == "o" && t.getOrNull(i + 1) == "mie") {
            any = true
            total = 1000
            i += 2
            below1000()?.let { total += it }
        }
        return if (any) total to i else null
    }
}
