package org.TheIza.fortrush.data.matchmaking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MatchGameSnapshotTest {
    @Test
    fun `parses the authoritative troops structures and result`() {
        val state = JSONObject(
            """{
                "sequence":8,"phase":"FINISHED","elapsedMatchMs":241000,"remainingMs":0,
                "player1":{"wallHealth":0,"castleHealth":220},
                "player2":{"wallHealth":0,"castleHealth":180},
                "troops":[{"id":"u-1-3","ownerPlayerNumber":1,"card":"espadachin","x":0.45,"y":0.52,"health":30,"maxHealth":60}],
                "towers":[{"slotId":"p1-slot-0","ownerPlayerNumber":1,"card":"arrow_tower"}],
                "projectiles":[{"id":"p-4","ownerPlayerNumber":2,"card":"cannon_tower","fromX":0.6,"fromY":0.5,"toX":0.5,"toY":0.5,"progress":0.75}],
                "result":{"winnerPlayerNumber":1,"reason":"SUDDEN_DEATH_CASTLE_HEALTH","elapsedMatchMs":243000,"player1WallHealth":0,"player2WallHealth":0,"player1CastleHealth":220,"player2CastleHealth":180},
                "viewerPlayerNumber":1
            }"""
        )

        val snapshot = state.gameSnapshot()

        assertNotNull(snapshot)
        assertEquals("FINISHED", snapshot!!.phase)
        assertEquals(1, snapshot.troops.size)
        assertEquals(0.45f, snapshot.troops.single().point.x, 0.0001f)
        assertEquals("arrow_tower", snapshot.towers.single().card)
        assertEquals(0.75f, snapshot.projectiles.single().progress, 0.0001f)
        assertEquals(1, snapshot.result?.winnerPlayerNumber)
        assertEquals(220f, snapshot.result?.player1CastleHealth ?: -1f, 0.0001f)
    }

    @Test
    fun `parses a draw result with no winner`() {
        val result = JSONObject(
            """{"winnerPlayerNumber":null,"reason":"SUDDEN_DEATH_DRAW","elapsedMatchMs":243000,"player1WallHealth":0,"player2WallHealth":0,"player1CastleHealth":200,"player2CastleHealth":200}"""
        ).resultEvent()
        assertNotNull(result)
        assertNull(result?.winnerPlayerNumber)
    }
}
