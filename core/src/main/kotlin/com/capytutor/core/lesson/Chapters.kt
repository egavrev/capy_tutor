package com.capytutor.core.lesson

/**
 * Orders textbook units the way the book does: "Unitatea II" before "Unitatea X", whatever the
 * file names or import order. Chapters without a number go last, alphabetically.
 */
object Chapters {
    private val roman = Regex("""(?i)\b(?:unitatea|capitolul|modulul)\s+([IVXLC]+|\d+)\b""")

    fun number(chapter: String?): Int? {
        val token = chapter?.let { roman.find(it)?.groupValues?.get(1) } ?: return null
        return token.toIntOrNull() ?: romanToInt(token.uppercase())
    }

    fun romanToInt(s: String): Int? {
        val v = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100)
        if (s.isEmpty() || s.any { it !in v }) return null
        var total = 0
        for (i in s.indices) {
            val cur = v.getValue(s[i])
            val next = if (i + 1 < s.length) v.getValue(s[i + 1]) else 0
            total += if (cur < next) -cur else cur
        }
        return total
    }

    /** Numbered units in order, then the rest by name. */
    val order: Comparator<String?> = compareBy<String?>({ number(it) ?: Int.MAX_VALUE }, { (it ?: "").lowercase() })
}
