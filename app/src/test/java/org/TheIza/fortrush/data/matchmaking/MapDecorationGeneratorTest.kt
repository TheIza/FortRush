package org.TheIza.fortrush.data.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class MapDecorationGeneratorTest {
    @Test
    fun sameSeedAndGeometryProduceTheSameDecoration() {
        val map = sampleMap("decoration-seed")

        val first = MapDecorationGenerator.generate(map)
        val second = MapDecorationGenerator.generate(map)

        assertEquals(first, second)
        assertTrue("should create a useful number of decorations", first.size >= 30)
        assertTrue("should include edge vegetation", first.any { it.point.x < 0.12f || it.point.x > 0.88f })
        assertTrue("should include flowers", first.any { it.kind == "flower" })
    }

    @Test
    fun decorationAvoidsTheRoadSlotsWallsAndCastles() {
        val map = sampleMap("clearance-seed")
        val decorations = MapDecorationGenerator.generate(map)

        for (decoration in decorations) {
            assertTrue(decoration.point.x in 0.025f..0.975f)
            assertTrue(decoration.point.y in 0.025f..0.975f)
            assertTrue(distanceToPath(decoration.point, map.path) >= 0.085f)
            assertTrue((map.player1Slots + map.player2Slots).all { screenDistance(decoration.point, it) >= 0.074f })
            assertTrue(listOf(map.player1Castle, map.player2Castle).all { screenDistance(decoration.point, it) >= 0.13f })
            assertTrue(listOf(map.player1WallGate, map.player2WallGate).all { kotlin.math.abs(decoration.point.y - it.y) >= 0.045f })
        }
    }

    private fun sampleMap(seed: String) = MatchMap(
        seed = seed,
        path = listOf(
            MatchPoint(0.5f, 0.97f),
            MatchPoint(0.5f, 0.86f),
            MatchPoint(0.22f, 0.72f),
            MatchPoint(0.78f, 0.56f),
            MatchPoint(0.22f, 0.40f),
            MatchPoint(0.5f, 0.14f),
            MatchPoint(0.5f, 0.03f)
        ),
        player1Slots = listOf(MatchPoint(0.12f, 0.70f), MatchPoint(0.88f, 0.62f)),
        player2Slots = listOf(MatchPoint(0.88f, 0.30f), MatchPoint(0.12f, 0.38f)),
        player1WallGate = MatchPoint(0.5f, 0.86f),
        player2WallGate = MatchPoint(0.5f, 0.14f),
        player1Castle = MatchPoint(0.5f, 0.93f),
        player2Castle = MatchPoint(0.5f, 0.07f),
        decorations = emptyList()
    )

    private fun distanceToPath(point: MatchPoint, path: List<MatchPoint>): Float {
        var closest = Float.POSITIVE_INFINITY
        for (index in 0 until path.lastIndex) {
            closest = min(closest, pointToSegmentDistance(point, path[index], path[index + 1]))
        }
        return closest
    }

    private fun pointToSegmentDistance(point: MatchPoint, start: MatchPoint, end: MatchPoint): Float {
        val xScale = 9f / 16f
        val px = point.x * xScale
        val py = point.y
        val sx = start.x * xScale
        val sy = start.y
        val dx = (end.x - start.x) * xScale
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-8f) return hypot(px - sx, py - sy)
        val amount = ((px - sx) * dx + (py - sy) * dy) / lengthSquared
        val clamped = max(0f, min(1f, amount))
        return hypot(px - (sx + clamped * dx), py - (sy + clamped * dy))
    }

    private fun screenDistance(first: MatchPoint, second: MatchPoint): Float =
        hypot((first.x - second.x) * (9f / 16f), first.y - second.y)
}
