package com.capytutor.core.speech

/**
 * Text as the phone's Romanian voice should hear it. Lessons are written the textbook way
 * ("în 44 î.H.", "Ramses al II-lea", "sec. al XVI-lea", "(1379–1362)"), which a speech engine
 * tends to spell letter by letter. This spells those out; the screen keeps the text as written.
 */
object SpeechText {

    private const val NOT_LETTER = """(?<![\p{L}\p{N}])"""
    private const val END = """(?![\p{L}\p{N}])"""
    private const val ROMAN = """[IVXLC]{1,7}"""

    fun forVoice(text: String): String {
        var s = text
        // Quotes cut from a source: "[…]" is not read.
        s = s.replace(Regex("""\[\s*(…|\.\.\.)\s*\]"""), "")
        // Before / after Christ, in every spelling the lessons use. When the abbreviation also ends
        // the sentence, its full stop stays, so the voice still pauses.
        s = abbreviation(s, """[îÎ]\.\s?Hr?\.""", "înainte de Hristos")
        s = abbreviation(s, """[dD]\.\s?Hr?\.""", "după Hristos")
        s = abbreviation(s, """[îÎ]\.\s?e\.\s?n\.""", "înaintea erei noastre")
        s = abbreviation(s, """e\.\s?n\.""", "era noastră")
        s = abbreviation(s, """etc\.""", "etcetera")
        // Abbreviations inside a sentence.
        s = s.replace(Regex(NOT_LETTER + """a\s+sec\.(?=\s)"""), "a secolului")
        s = s.replace(Regex(NOT_LETTER + """sec\.(?=\s)"""), "secolul")
        s = s.replace(Regex(NOT_LETTER + """(?:cca\.?|aprox\.)(?=\s)"""), "circa")
        s = s.replace(Regex("""(?<![\d.])(\d[\d.]*)\s?km$END""")) { m ->
            val n = m.groupValues[1].replace(".", "").toLongOrNull() ?: return@replace m.value
            val r = n % 100
            when {
                n == 1L -> "un kilometru"
                r == 0L || r >= 20 -> "${m.groupValues[1]} de kilometri"
                else -> "${m.groupValues[1]} kilometri"
            }
        }
        s = s.replace(Regex(NOT_LETTER + """nr\.\s?(?=\d)"""), "numărul ")
        // Two centuries or millennia: "secolul IV–V" → "secolul al patrulea până la al cincilea",
        // "secolele IX și VIII" → "secolele nouă și opt".
        s = s.replace(Regex("""(?i)${NOT_LETTER}(secol\p{L}*|mileni\p{L}*)\s+($ROMAN)(\s?[–—-]\s?|\s+și\s+)($ROMAN)$END(?!-)""")) { m ->
            val a = roman(m.groupValues[2])
            val b = roman(m.groupValues[4])
            val word = m.groupValues[1]
            val join = if (m.groupValues[3].contains("și")) "și" else "până la"
            when {
                a == null || b == null -> m.value
                word.endsWith("ul") || word.endsWith("ului") -> "$word ${masculineOrdinal(a)} $join ${masculineOrdinal(b)}"
                else -> "$word ${cardinal(a)} $join ${cardinal(b)}"
            }
        }
        // Ordinals: "al II-lea" → "al doilea", "a III-a" → "a treia".
        s = s.replace(Regex(NOT_LETTER + """(al|a)\s+($ROMAN)-(lea|a)$END""")) { m ->
            val n = roman(m.groupValues[2]) ?: return@replace m.value
            val feminine = m.groupValues[3] == "a"
            (if (feminine) feminineOrdinal(n) else masculineOrdinal(n)) ?: m.value
        }
        // "secolul V" → "secolul al cincilea", "mileniul I" → "mileniul întâi".
        s = s.replace(Regex("""(?i)${NOT_LETTER}(secolul|secolului|mileniul|mileniului)\s+($ROMAN)$END(?!-)""")) { m ->
            val n = roman(m.groupValues[2]) ?: return@replace m.value
            m.groupValues[1] + " " + (masculineOrdinal(n) ?: m.groupValues[2])
        }
        // A ruler's number: "Darius I", "Ramses II" (only right after a capitalised name).
        s = s.replace(Regex("""(?<=\p{Lu}\p{Ll}{1,20})\s($ROMAN)$END(?![-.]\p{L})""")) { m ->
            val n = roman(m.groupValues[1]) ?: return@replace m.value
            " " + (masculineOrdinal(n) ?: m.groupValues[1])
        }
        // Ranges between numbers: "(1379–1362)" → "1379 până la 1362".
        s = s.replace(Regex("""(?<=\d)\s?[–—]\s?(?=\d)"""), " până la ")
        s = s.replace(Regex("""(?<=\d)-(?=\d)"""), " până la ")
        return s.replace(Regex(""" {2,}"""), " ").replace(Regex(""" +(?=[.,;:!?])"""), "").trim()
    }

    /** Spells out [pattern]; a full stop that also ended the sentence is kept. */
    private fun abbreviation(text: String, pattern: String, words: String): String {
        val ending = Regex(NOT_LETTER + pattern + """(?=\s+\p{Lu}|\s*$)""")
        return text.replace(ending, "$words.").replace(Regex(NOT_LETTER + pattern), words)
    }

    /** "XIV" → 14; null when it is not a well-formed numeral up to 399. */
    fun roman(s: String): Int? {
        val v = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100)
        var total = 0
        for (i in s.indices) {
            val cur = v[s[i]] ?: return null
            val next = if (i + 1 < s.length) v[s[i + 1]] ?: return null else 0
            total += if (cur < next) -cur else cur
        }
        return total.takeIf { it in 1..399 && toRoman(it) == s }
    }

    private fun toRoman(n: Int): String {
        val parts = listOf(100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var rest = n
        return buildString { for ((value, sym) in parts) while (rest >= value) { append(sym); rest -= value } }
    }

    private val units = listOf("", "unu", "doi", "trei", "patru", "cinci", "șase", "șapte", "opt", "nouă")
    private val teens = listOf("zece", "unsprezece", "doisprezece", "treisprezece", "paisprezece", "cincisprezece", "șaisprezece", "șaptesprezece", "optsprezece", "nouăsprezece")
    private val tens = listOf("", "", "douăzeci", "treizeci", "patruzeci", "cincizeci", "șaizeci", "șaptezeci", "optzeci", "nouăzeci")

    /** 1..99 in words, as counted ("doi", "douăzeci și unu"). */
    fun cardinal(n: Int): String = when {
        n in 1..9 -> units[n]
        n in 10..19 -> teens[n - 10]
        n in 20..99 -> tens[n / 10] + if (n % 10 == 0) "" else " și " + units[n % 10]
        else -> n.toString()
    }

    /** "al doilea", "al unsprezecelea", "al douăzecilea"; 1 → "întâi" (as in "secolul I"). */
    fun masculineOrdinal(n: Int): String? = when {
        n == 1 -> "întâi"
        n in 2..99 -> "al " + ordinalStem(n) + "lea"
        else -> null
    }

    /** "a doua", "a treia", "a unsprezecea", "a douăzecea"; 1 → "întâi". */
    fun feminineOrdinal(n: Int): String? = when {
        n == 1 -> "întâi"
        n in 2..9 -> "a " + feminineUnit(n)
        n in 10..19 -> "a " + teens[n - 10].replace("doisprezece", "douăsprezece").dropLast(1) + "ea"
        n in 20..99 && n % 10 == 0 -> "a " + tens[n / 10].dropLast(1) + "ea"
        n in 21..99 -> "a " + tens[n / 10] + " și " + if (n % 10 == 1) "una" else feminineUnit(n % 10)
        else -> null
    }

    private fun feminineUnit(d: Int): String =
        listOf("", "una", "doua", "treia", "patra", "cincea", "șasea", "șaptea", "opta", "noua")[d]

    private fun ordinalStem(n: Int): String {
        val last = n % 10
        return when {
            n in 10..19 -> teens[n - 10].dropLast(1) + "e" // "unsprezece" + "lea"
            n >= 20 && last == 0 -> tens[n / 10] // "douăzeci" + "lea"
            n >= 20 -> tens[n / 10] + " și " + stemOf(last)
            else -> stemOf(last)
        }
    }

    private fun stemOf(d: Int): String = when (d) {
        1 -> "unu"
        2 -> "doi"
        3 -> "trei"
        4 -> "patru"
        5 -> "cinci"
        6 -> "șase"
        7 -> "șapte"
        8 -> "optu"
        9 -> "nouă"
        else -> ""
    }
}
