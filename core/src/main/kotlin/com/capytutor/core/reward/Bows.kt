package com.capytutor.core.reward

/*
 * E12 · Capy's corner: a quiet reward for a finished lesson. The child collects bows (fundițe),
 * 1–3 per finished lesson. Nothing is ever taken away: a lesson keeps its best result, there are
 * no streaks, and corner items unlock as the total grows (they are not bought).
 */
object Bows {
    const val MAX_PER_LESSON = 3

    /** Bows for a finished session: 1 for finishing, 2 from half the test right, 3 from 80 %. */
    fun forSession(correct: Int, total: Int): Int = when {
        total <= 0 -> 1
        correct * 10 >= total * 8 -> 3
        correct * 2 >= total -> 2
        else -> 1
    }

    /** A lesson's bows: its best session (redoing a lesson can only add bows). */
    fun forLesson(sessions: List<Int>): Int = (sessions.maxOrNull() ?: 0).coerceIn(0, MAX_PER_LESSON)

    /** Bows a new session adds to the total: only the part above the lesson's best so far. */
    fun added(previousBest: Int, session: Int): Int = (session.coerceAtMost(MAX_PER_LESSON) - previousBest).coerceAtLeast(0)
}

/** Things in Capy's corner, unlocked by the total number of bows. Icons are `ic_capy_<icon>`. */
enum class CornerItem(val icon: String, val label: String, val bows: Int) {
    CUSHION("cushion", "Pernuța lui Capy", 3),
    CUP("cup", "Cana cu ceai", 8),
    PLANT("plant", "O plantă verde", 15),
    LAMP("lamp", "Lampa de citit", 25),
    SHELF("shelf", "Raftul cu cărți", 40),
    WINDOW("window", "Fereastra cu flori", 60);

    companion object {
        fun unlocked(total: Int): List<CornerItem> = entries.filter { total >= it.bows }

        /** The next item and how many bows it still needs, or null when everything is unlocked. */
        fun next(total: Int): Pair<CornerItem, Int>? = entries.firstOrNull { total < it.bows }?.let { it to it.bows - total }
    }
}
