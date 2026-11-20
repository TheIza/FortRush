package org.TheIza.fortrush.data.matchmaking

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchmakingMapParserTest {
    @Test
    fun parsesCompleteVersionTwoMapForBothPlayers() {
        val map = parseMap(versionTwoPayload())

        assertNotNull(map)
        requireNotNull(map)
        assertEquals("parser-seed", map.seed)
        assertEquals(5, map.path.size)
        assertEquals(2, map.player1Slots.size)
        assertEquals(2, map.player2Slots.size)
        assertEquals(MatchPoint(0.5f, 0.86f), map.player1WallGate)
        assertEquals(MatchPoint(0.5f, 0.14f), map.player2WallGate)
        assertEquals(0.08f, map.pathWidthFraction, 0.0001f)
        assertEquals(0.08f, map.slotDiameterFraction, 0.0001f)
        assertEquals(0.3f, map.towerRangeFraction, 0.0001f)
        assertEquals(listOf(0, 1), map.slotMetadata.filter { it.ownerPlayerNumber == 1 }.map { it.slotIndex })
        assertEquals(listOf(0, 1), map.slotMetadata.filter { it.ownerPlayerNumber == 2 }.map { it.slotIndex })
        assertEquals(listOf(MatchCoverageRange(100f, 240f)), map.slotMetadata.first().coverage)
        assertTrue("client-only decoration should be derived", map.decorations.isNotEmpty())
    }

    @Test
    fun rejectsMalformedVersionTwoGeometry() {
        val payload = versionTwoPayload()
        payload.put("width", 0)

        assertEquals(null, parseMap(payload))
    }

    private fun versionTwoPayload(): JSONObject {
        val pathPoints = JSONArray()
            .put(point(500, 1740))
            .put(point(500, 1450))
            .put(point(280, 1000))
            .put(point(500, 350))
            .put(point(500, 60))
        val slots = JSONArray()
            .put(slot("p1-a", "player1", 200, 1300, 5.5, 100, 240))
            .put(slot("p1-b", "player1", 750, 1100, 4.0, 450, 600))
            .put(slot("p2-a", "player2", 800, 700, 5.5, 1560, 1700))
            .put(slot("p2-b", "player2", 250, 900, 4.0, 1200, 1350))
        return JSONObject()
            .put("seed", "parser-seed")
            .put("width", 1000)
            .put("height", 1800)
            .put("path", JSONObject()
                .put("points", pathPoints)
                .put("cumLen", JSONArray().put(0).put(290).put(790).put(1500).put(1790))
                .put("totalLength", 1790)
                .put("width", 80))
            .put("slots", slots)
            .put("meta", JSONObject().put("towerRange", 300).put("slotRadius", 40))
            .put("wallGates", JSONObject()
                .put("player1", point(500, 1548))
                .put("player2", point(500, 252)))
            .put("castlePositions", JSONObject()
                .put("player1", point(500, 1677.6))
                .put("player2", point(500, 122.4)))
    }

    private fun point(x: Number, y: Number) = JSONObject().put("x", x).put("y", y)

    private fun slot(
        id: String,
        owner: String,
        x: Int,
        y: Int,
        value: Double,
        coverageStart: Int,
        coverageEnd: Int
    ) = JSONObject()
        .put("id", id)
        .put("owner", owner)
        .put("x", x)
        .put("y", y)
        .put("value", value)
        .put("coverage", JSONArray().put(JSONArray().put(coverageStart).put(coverageEnd)))
}
