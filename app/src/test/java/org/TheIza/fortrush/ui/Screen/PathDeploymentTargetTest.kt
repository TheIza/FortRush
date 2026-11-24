package org.TheIza.fortrush.ui.Screen

import androidx.compose.ui.geometry.Offset
import org.TheIza.fortrush.data.matchmaking.MatchMap
import org.TheIza.fortrush.data.matchmaking.MatchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PathDeploymentTargetTest {
    private val map = MatchMap(
        seed = "test",
        path = listOf(MatchPoint(0.5f, 1f), MatchPoint(0.5f, 0.5f), MatchPoint(0.5f, 0f)),
        player1Slots = emptyList(),
        player2Slots = emptyList(),
        player1WallGate = MatchPoint(0.5f, 0.9f),
        player2WallGate = MatchPoint(0.5f, 0.1f),
        player1Castle = MatchPoint(0.5f, 0.98f),
        player2Castle = MatchPoint(0.5f, 0.02f),
        decorations = emptyList(),
        pathWidthFraction = 0.13f,
        pathCumulativeLengths = listOf(0f, 50f, 100f),
        pathTotalLength = 100f
    )

    @Test
    fun `a tap on either player's half maps to the server path direction`() {
        val playerOne = pathStationForTouch(map, Offset(100f, 350f), 1, 0f, 200f, 400f)
        val playerTwo = pathStationForTouch(map, Offset(100f, 350f), 2, 0f, 200f, 400f)
        assertEquals(12.5f, playerOne ?: -1f, 0.5f)
        assertEquals(87.5f, playerTwo ?: -1f, 0.5f)
    }

    @Test
    fun `central forbidden band and touches away from the road are rejected`() {
        assertNull(pathStationForTouch(map, Offset(100f, 200f), 1, 0f, 200f, 400f))
        assertNull(pathStationForTouch(map, Offset(180f, 350f), 1, 0f, 200f, 400f))
    }
}
