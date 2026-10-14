package com.capytutor.core

import java.text.Normalizer

/**
 * Romanian text helpers shared by the lesson parser and the coverage judge.
 *
 * Lessons may arrive with the old cedilla letters (ş ţ), which look almost the same but are
 * different characters. Everything the app stores or shows uses the comma-below forms (ș ț).
 */
object RomanianText {

    private val cedillaToComma = mapOf(
        'ş' to 'ș', 'Ş' to 'Ș',
        'ţ' to 'ț', 'Ţ' to 'Ț',
    )

    /** Replaces ş ţ Ş Ţ (cedilla) with ș ț Ș Ț (comma below). Everything else is unchanged. */
    fun normalizeDiacritics(text: String): String =
        buildString(text.length) {
            for (ch in text) append(cedillaToComma[ch] ?: ch)
        }

    /**
     * Lower-cases and removes all diacritics. Used **only for matching** (keyword pass),
     * never for text that is stored or shown.
     */
    fun foldForMatching(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(combiningMarks, "")

    private val combiningMarks = Regex("\\p{M}+")
}
