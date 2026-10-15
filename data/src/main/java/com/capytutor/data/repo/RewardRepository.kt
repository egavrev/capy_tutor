package com.capytutor.data.repo

import com.capytutor.core.reward.Bows
import com.capytutor.data.db.CapyDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One lesson on Capy's bow shelf. */
data class ShelfLesson(val lessonId: Long, val title: String, val bows: Int, val lastAt: Long)

data class Rewards(val total: Int, val shelf: List<ShelfLesson>) {
    fun bowsFor(lessonId: Long): Int = shelf.firstOrNull { it.lessonId == lessonId }?.bows ?: 0
}

/** E12 · Bows: earned at the end of a session (SessionRepository), shown on the shelf and the lesson rows. */
class RewardRepository(db: CapyDatabase) {
    private val bows = db.bows()

    fun observe(): Flow<Rewards> = bows.observeAll().map { rows ->
        val shelf = rows.groupBy { it.lessonId }.map { (id, list) ->
            ShelfLesson(id, list.maxBy { it.at }.lessonTitle, Bows.forLesson(list.map { it.bows }), list.maxOf { it.at })
        }.sortedByDescending { it.lastAt }
        Rewards(rows.sumOf { it.added }, shelf)
    }
}
