package com.capytutor.core.progress

/*
 * E15 · Parent progress view: what the child remembers over time. Every finished session and
 * every quiz leaves one mark per key idea; an idea's status comes from its latest marks.
 */

/** One piece of evidence about one idea. */
enum class Mark(val ok: Boolean) {
    /** Remembered in the retelling, no help (and not contradicted by the test). */
    ALONE(true),

    /** Remembered with Capy's question or clue (and not contradicted by the test). */
    WITH_HINT(true),

    /** Capy had to explain it, or the test answer was "încă nu". */
    NOT_YET(false),

    QUIZ_RIGHT(true),
    QUIZ_NOT_YET(false),
}

data class Evidence(val at: Long, val mark: Mark)

enum class IdeaStatus { KNOWN, WEAK, UNSEEN }

object Progress {

    /**
     * The mark a finished session leaves for one idea, from the stored result (enum names as
     * saved in Room). A test answer the parent accepted counts as right. Null when the idea was
     * not reached (an unfinished session).
     */
    fun sessionMark(outcome: String?, checkResult: String?, parentDecision: String?): Mark? {
        val testSaysNotYet = checkResult == "NOT_YET" && parentDecision != "accepted"
        return when (outcome) {
            "RECALLED_ALONE" -> if (testSaysNotYet) Mark.NOT_YET else Mark.ALONE
            "RECALLED_WITH_HINT" -> if (testSaysNotYet) Mark.NOT_YET else Mark.WITH_HINT
            "EXPLAINED" -> Mark.NOT_YET
            else -> null
        }
    }

    /**
     * Known when the latest mark is good and the idea is not shaky (at most one "not yet" in the
     * last three); weak otherwise; unseen without any mark.
     */
    fun status(evidence: List<Evidence>): IdeaStatus {
        if (evidence.isEmpty()) return IdeaStatus.UNSEEN
        val recent = evidence.sortedBy { it.at }.takeLast(3)
        val latest = recent.last()
        return if (latest.mark.ok && recent.count { !it.mark.ok } <= 1) IdeaStatus.KNOWN else IdeaStatus.WEAK
    }
}
