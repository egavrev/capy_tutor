package com.capytutor.core.reward

/*
 * E12 · Capy's corner: a quiet reward for a finished lesson. The child collects bows (fundițe),
 * 1–3 per finished lesson. Nothing earned is ever taken away: a lesson keeps its best result and
 * there are no streaks. She spends bows on things for Capy's room (see [CornerShop]); a thing
 * once bought stays hers.
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

/**
 * Things for Capy's room, in the order of the shop grid, with their price in bows. [say] is how
 * the room describes it to a screen reader ("Camera lui Capy: …").
 */
enum class CornerItem(val label: String, val price: Int, val say: String) {
    PILLOW("Pernă", 3, "o pernă uriașă cu fundițe pe care stă Capy"),
    PLUSH("Plușuri", 10, "doi marshmallow de pluș, un ursuleț în halat și o pernă-nod"),
    SHELF("Raft", 10, "un raft agățat cu fundițe"),
    LAMP("Lampă", 20, "o veioză cu dantelă și fundițe pe noptieră"),
    PLANT("Plantă", 2, "o plantă în ghiveci"),
    RUG("Covor", 6, "un covor-iepuraș"),
    FOOD("Gustare", 7, "o prăjitură"),
    CUP("Cană", 4, "o cană cu fundiță"),
    STICKERS("Stickere", 8, "o tablă cu stickere"),
    POSTERS("Postere", 15, "un poster roz și un poster Geografia cu harta lumii"),
    WINDOW("Fereastră", 20, "o fereastră"),
}

/** What she owns, and which of those she took out of the room for now. */
data class Corner(val owned: Set<CornerItem> = emptySet(), val hidden: Set<CornerItem> = emptySet()) {
    fun placed(item: CornerItem): Boolean = item in owned && item !in hidden
    val inRoom: List<CornerItem> get() = CornerItem.entries.filter(::placed)
}

/** What a tap on a thing did. */
sealed interface Tap {
    data class Bought(val item: CornerItem) : Tap
    data class Placed(val item: CornerItem) : Tap
    data class PutAway(val item: CornerItem) : Tap

    /** Not enough bows yet: [missing] more are needed. Nothing changes. */
    data class NotEnough(val item: CornerItem, val missing: Int) : Tap
}

/**
 * Capy's shop. Bows to spend = all bows earned minus the prices of what she bought, never below
 * zero. Tapping a thing she can afford buys it and puts it in the room; tapping a thing she owns
 * takes it out of the room or puts it back (it stays hers, the bows are not returned).
 */
object CornerShop {
    fun spent(corner: Corner): Int = corner.owned.sumOf { it.price }

    fun balance(earned: Int, corner: Corner): Int = (earned - spent(corner)).coerceAtLeast(0)

    fun tap(corner: Corner, item: CornerItem, earned: Int): Pair<Corner, Tap> = when {
        item in corner.owned && item in corner.hidden -> corner.copy(hidden = corner.hidden - item) to Tap.Placed(item)
        item in corner.owned -> corner.copy(hidden = corner.hidden + item) to Tap.PutAway(item)
        balance(earned, corner) >= item.price -> corner.copy(owned = corner.owned + item, hidden = corner.hidden - item) to Tap.Bought(item)
        else -> corner to Tap.NotEnough(item, item.price - balance(earned, corner))
    }
}
