package com.capytutor.core.reward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerShopTest {

    @Test
    fun buyingSpendsBowsAndPutsTheThingInTheRoom() {
        val (c, tap) = CornerShop.tap(Corner(), CornerItem.PILLOW, earned = 5)
        assertEquals(Tap.Bought(CornerItem.PILLOW), tap)
        assertTrue(c.placed(CornerItem.PILLOW))
        assertEquals(2, CornerShop.balance(5, c))
    }

    @Test
    fun notEnoughBowsChangesNothingAndSaysHowManyAreMissing() {
        val start = Corner(owned = setOf(CornerItem.PILLOW))
        val (c, tap) = CornerShop.tap(start, CornerItem.LAMP, earned = 10)
        assertEquals(Tap.NotEnough(CornerItem.LAMP, 13), tap)
        assertEquals(start, c)
    }

    @Test
    fun anOwnedThingGoesOutOfTheRoomAndBackForFree() {
        val owned = Corner(owned = setOf(CornerItem.CUP))
        val (out, t1) = CornerShop.tap(owned, CornerItem.CUP, earned = 4)
        assertEquals(Tap.PutAway(CornerItem.CUP), t1)
        assertFalse(out.placed(CornerItem.CUP))
        assertEquals(0, CornerShop.balance(4, out))
        val (back, t2) = CornerShop.tap(out, CornerItem.CUP, earned = 4)
        assertEquals(Tap.Placed(CornerItem.CUP), t2)
        assertTrue(back.placed(CornerItem.CUP))
        assertEquals(0, CornerShop.balance(4, back))
    }

    @Test
    fun theBalanceNeverGoesBelowZero() {
        val c = Corner(owned = setOf(CornerItem.WINDOW, CornerItem.LAMP))
        assertEquals(40, CornerShop.spent(c))
        assertEquals(0, CornerShop.balance(10, c))
    }

    @Test
    fun theRoomListsThingsInShopOrder() {
        val c = Corner(owned = setOf(CornerItem.WINDOW, CornerItem.PILLOW, CornerItem.RUG), hidden = setOf(CornerItem.RUG))
        assertEquals(listOf(CornerItem.PILLOW, CornerItem.WINDOW), c.inRoom)
    }
}
