package com.capytutor.core.lesson

/** The subjects a lesson can belong to (HANDOFF.md §6). */
enum class Subject(val label: String) {
    ISTORIE("Istorie"),
    ROMANA("Română"),
    MATEMATICA("Matematică"),
    STIINTE("Științe"),
    GEOGRAFIE("Geografie");

    companion object {
        /** Matches the label ignoring case and diacritics ("Romana" finds Română). */
        fun fromLabel(label: String): Subject? {
            val wanted = com.capytutor.core.RomanianText.foldForMatching(label.trim())
            return entries.firstOrNull { com.capytutor.core.RomanianText.foldForMatching(it.label) == wanted }
        }
    }
}

data class Term(val term: String, val definition: String)

/** A parsed lesson file. Text is already normalised to comma-below ș ț. */
data class Lesson(
    val subject: Subject,
    val grade: Int?,
    val chapter: String?,
    val title: String,
    val language: String,
    val text: String,
    /** The parent's own key ideas, in lesson order. */
    val ideas: List<String>,
    val terms: List<Term>,
    /** SHA-256 of the original file, hex. Re-importing a changed file rebuilds the pack. */
    val sha256: String,
) {
    val wordCount: Int get() = LessonParser.countWords(text)

    /** Stable ids in lesson order: i1, i2, ... */
    fun ideaId(index: Int): String = "i${index + 1}"
}
