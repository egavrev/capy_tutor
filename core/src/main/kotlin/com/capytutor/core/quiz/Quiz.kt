package com.capytutor.core.quiz

import com.capytutor.core.RomanianText

/*
 * E11 · Quiz: a short test with varied question types, built from a lesson's key ideas.
 * Graded on the phone, with no model call: every question has one checkable answer.
 */

enum class QuizType(val wire: String) {
    CHOICE("choice"), TRUE_FALSE("truefalse"), ORDER("order"), MATCH("match");

    companion object {
        fun fromWire(s: String): QuizType? = entries.firstOrNull { it.wire == s.trim().lowercase() }
    }
}

sealed interface QuizQuestion {
    val type: QuizType

    /** The key ideas this question checks (ids such as "i3"). */
    val ideaIds: List<String>

    /** The question (for true/false: the statement to judge). */
    val prompt: String

    /** Shown after answering, right or not yet. */
    val explanation: String

    data class Choice(
        override val ideaIds: List<String>,
        override val prompt: String,
        val options: List<String>,
        val answer: Int,
        override val explanation: String,
    ) : QuizQuestion {
        override val type get() = QuizType.CHOICE
    }

    data class TrueFalse(
        override val ideaIds: List<String>,
        override val prompt: String,
        val answer: Boolean,
        override val explanation: String,
    ) : QuizQuestion {
        override val type get() = QuizType.TRUE_FALSE
    }

    /** [items] are in the correct order (oldest first, first step first...). */
    data class Order(
        override val ideaIds: List<String>,
        override val prompt: String,
        val items: List<String>,
        override val explanation: String,
    ) : QuizQuestion {
        override val type get() = QuizType.ORDER
    }

    /** [pairs]: left → right, as they belong together. */
    data class Match(
        override val ideaIds: List<String>,
        override val prompt: String,
        val pairs: List<Pair<String, String>>,
        override val explanation: String,
    ) : QuizQuestion {
        override val type get() = QuizType.MATCH
    }
}

/** A lesson's quiz, tied to the lesson file it was made from. */
data class Quiz(val lessonSha256: String, val questions: List<QuizQuestion>)

/** What the child chose, in the original (unshuffled) indices of the question. */
sealed interface QuizAnswer {
    data class Choice(val option: Int) : QuizAnswer
    data class TrueFalse(val value: Boolean) : QuizAnswer

    /** Item indices in the order the child put them. */
    data class Order(val items: List<Int>) : QuizAnswer

    /** For each left index (position in the list), the right index the child paired it with. */
    data class Match(val rights: List<Int>) : QuizAnswer
}

object QuizGrader {
    fun isRight(q: QuizQuestion, a: QuizAnswer): Boolean = when (q) {
        is QuizQuestion.Choice -> a is QuizAnswer.Choice && a.option == q.answer
        is QuizQuestion.TrueFalse -> a is QuizAnswer.TrueFalse && a.value == q.answer
        is QuizQuestion.Order -> a is QuizAnswer.Order && a.items == q.items.indices.toList()
        is QuizQuestion.Match -> a is QuizAnswer.Match && a.rights == q.pairs.indices.toList()
    }

    /** The right answer in words, for "Încă nu. Răspunsul este: …". */
    fun rightAnswerText(q: QuizQuestion): String = when (q) {
        is QuizQuestion.Choice -> q.options[q.answer]
        is QuizQuestion.TrueFalse -> if (q.answer) "Adevărat" else "Fals"
        is QuizQuestion.Order -> q.items.joinToString(" → ")
        is QuizQuestion.Match -> q.pairs.joinToString("; ") { "${it.first} – ${it.second}" }
    }

    /** Capy is happy from 70 % (HANDOFF.md §3, the same bar as the session summary). */
    fun isHappy(correct: Int, total: Int): Boolean = total > 0 && correct * 10 >= total * 7

    internal fun fold(s: String) = RomanianText.foldForMatching(s).replace(Regex("\\s+"), " ").trim()
}
