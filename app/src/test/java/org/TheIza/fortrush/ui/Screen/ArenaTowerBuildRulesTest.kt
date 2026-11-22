package org.TheIza.fortrush.ui.Screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArenaTowerBuildRulesTest {
    @Test
    fun validBuildRequiresAnActiveMatchOwnFreeSlotAndEnoughCoins() {
        val result = assessArenaBuild(
            slotOwnerPlayerNumber = 1,
            localPlayerNumber = 1,
            isMatchActive = true,
            isSlotOccupied = false,
            hasEnoughCoins = true
        )

        assertTrue(result.allowed)
    }

    @Test
    fun eachInvalidBuildConditionHasAReason() {
        assertEquals(
            ArenaBuildBlockReason.INVALID_STATE,
            assessArenaBuild(1, 1, false, false, true).reason
        )
        assertEquals(
            ArenaBuildBlockReason.NOT_YOUR_SLOT,
            assessArenaBuild(2, 1, true, false, true).reason
        )
        assertEquals(
            ArenaBuildBlockReason.SLOT_OCCUPIED,
            assessArenaBuild(1, 1, true, true, true).reason
        )
        assertEquals(
            ArenaBuildBlockReason.NOT_ENOUGH_COINS,
            assessArenaBuild(1, 1, true, false, false).reason
        )
        assertEquals(
            ArenaBuildBlockReason.ON_COOLDOWN,
            assessArenaBuild(1, 1, true, false, true, cooldownRemainingMs = 100).reason
        )
    }
}
