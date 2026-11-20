package org.TheIza.fortrush.ui.Screen

import org.TheIza.fortrush.data.matchmaking.MatchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArenaSlotPickingTest {
    @Test
    fun tappingOpponentSlotDoesNotReturnAnySlot() {
        val slots = listOf(
            ArenaSlotTarget("own-slot", ownerPlayerNumber = 1, x = 20f, y = 20f, touchRadius = 10f),
            ArenaSlotTarget("opponent-slot", ownerPlayerNumber = 2, x = 100f, y = 100f, touchRadius = 10f)
        )

        assertNull(pickOwnSlot(MatchPoint(100f, 100f), slots, localPlayer = 1))
    }

    @Test
    fun tappingOwnSlotReturnsIt() {
        val own = ArenaSlotTarget("own-slot", ownerPlayerNumber = 2, x = 100f, y = 100f, touchRadius = 10f)
        val opponent = ArenaSlotTarget("opponent-slot", ownerPlayerNumber = 1, x = 100f, y = 100f, touchRadius = 10f)

        assertEquals(own, pickOwnSlot(MatchPoint(100f, 100f), listOf(opponent, own), localPlayer = 2))
    }
}
