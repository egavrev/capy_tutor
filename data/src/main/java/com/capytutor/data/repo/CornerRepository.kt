package com.capytutor.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.capytutor.core.reward.Corner
import com.capytutor.core.reward.CornerItem
import com.capytutor.core.reward.CornerShop
import com.capytutor.core.reward.Tap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cornerStore by preferencesDataStore(name = "capy_corner")

/** Bows earned (with the per-lesson shelf), what Capy's room holds, and the bows left to spend. */
data class CornerState(val rewards: Rewards, val corner: Corner) {
    val earned: Int get() = rewards.total
    val balance: Int get() = CornerShop.balance(rewards.total, corner)
}

/**
 * Capy's room (E12, new design): what she bought and what she took out of the room, kept on the
 * phone. Bows earned stay in the database ([RewardRepository]); spending is the prices of what
 * she owns, so nothing earned is ever lost.
 */
class CornerRepository(context: Context, private val rewards: RewardRepository) {
    private val store = context.applicationContext.cornerStore

    fun observe(): Flow<CornerState> = combine(rewards.observe(), store.data.map { it.corner() }) { r, c -> CornerState(r, c) }

    /** A tap on a thing in the shop grid: buy it, or take it out of the room / put it back. */
    suspend fun tap(item: CornerItem): Tap {
        val earned = rewards.observe().first().total
        var result: Tap = Tap.NotEnough(item, item.price)
        store.edit { p ->
            val (next, tap) = CornerShop.tap(p.corner(), item, earned)
            p[OWNED] = next.owned.map { it.name }.toSet()
            p[HIDDEN] = next.hidden.map { it.name }.toSet()
            result = tap
        }
        return result
    }

    /** Sets the room outright (debug demo only). */
    suspend fun set(corner: Corner) {
        store.edit { p ->
            p[OWNED] = corner.owned.map { it.name }.toSet()
            p[HIDDEN] = corner.hidden.map { it.name }.toSet()
        }
    }

    private fun Preferences.corner() = Corner(owned = items(this[OWNED]), hidden = items(this[HIDDEN]))

    private fun items(names: Set<String>?): Set<CornerItem> =
        names.orEmpty().mapNotNull { n -> CornerItem.entries.firstOrNull { it.name == n } }.toSet()

    private companion object {
        val OWNED = stringSetPreferencesKey("owned")
        val HIDDEN = stringSetPreferencesKey("hidden")
    }
}
