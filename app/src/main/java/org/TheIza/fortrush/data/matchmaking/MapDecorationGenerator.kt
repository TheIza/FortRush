package org.TheIza.fortrush.data.matchmaking

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Creates client-only map decoration from a dedicated deterministic random stream. */
internal object MapDecorationGenerator {
    private const val BOARD_ASPECT = 9f / 16f
    private const val TARGET_DECORATIONS = 72
    private const val MAX_ATTEMPTS = TARGET_DECORATIONS * 120
    private const val MIN_DECORATION_SPACING = 0.064f
    private const val ROAD_CLEARANCE = 0.086f
    private const val SLOT_CLEARANCE = 0.074f
    private const val CASTLE_CLEARANCE = 0.13f
    private const val WALL_CLEARANCE = 0.045f

    private data class ScreenPoint(val x: Float, val y: Float)

    fun generate(map: MatchMap): List<MatchDecoration> {
        if (map.path.size < 2) return emptyList()

        val random = Random(seedFor(map.seed))
        val slots = map.player1Slots + map.player2Slots
        val castles = listOf(map.player1Castle, map.player2Castle)
        val walls = listOf(map.player1WallGate, map.player2WallGate)
        val accepted = mutableListOf<MatchDecoration>()

        repeat(MAX_ATTEMPTS) {
            if (accepted.size >= TARGET_DECORATIONS) return accepted

            val candidate = samplePoint(random)
            if (candidate.x !in 0.025f..0.975f || candidate.y !in 0.025f..0.975f) return@repeat
            if (nearRoad(candidate, map.path, ROAD_CLEARANCE)) return@repeat
            if (slots.any { screenDistance(candidate, it) < SLOT_CLEARANCE }) return@repeat
            if (castles.any { screenDistance(candidate, it) < CASTLE_CLEARANCE }) return@repeat
            if (walls.any { kotlin.math.abs(candidate.y - it.y) < WALL_CLEARANCE }) return@repeat
            if (accepted.any { screenDistance(candidate, it.point) < MIN_DECORATION_SPACING }) return@repeat

            val edge = isEdgePoint(candidate)
            val kind = when {
                edge && random.nextFloat() < 0.58f -> "tree"
                random.nextFloat() < 0.34f -> "bush"
                random.nextFloat() < 0.55f -> "rock"
                else -> "flower"
            }
            val scale = 0.72f + random.nextFloat() * 0.56f
            accepted += MatchDecoration(kind, candidate, scale)
        }

        return accepted
    }

    private fun samplePoint(random: Random): MatchPoint {
        if (random.nextFloat() < 0.68f) {
            val along = 0.04f + random.nextFloat() * 0.92f
            val edgeBand = 0.035f + random.nextFloat() * 0.075f
            return when (random.nextInt(4)) {
                0 -> MatchPoint(along, edgeBand)
                1 -> MatchPoint(along, 1f - edgeBand)
                2 -> MatchPoint(edgeBand, along)
                else -> MatchPoint(1f - edgeBand, along)
            }
        }
        return MatchPoint(
            x = 0.11f + random.nextFloat() * 0.78f,
            y = 0.08f + random.nextFloat() * 0.84f
        )
    }

    private fun isEdgePoint(point: MatchPoint): Boolean =
        point.x < 0.12f || point.x > 0.88f || point.y < 0.12f || point.y > 0.88f

    private fun screenPoint(point: MatchPoint) = ScreenPoint(point.x * BOARD_ASPECT, point.y)

    private fun screenDistance(first: MatchPoint, second: MatchPoint): Float {
        val a = screenPoint(first)
        val b = screenPoint(second)
        return hypot(a.x - b.x, a.y - b.y)
    }

    private fun nearRoad(point: MatchPoint, path: List<MatchPoint>, clearance: Float): Boolean {
        val target = screenPoint(point)
        for (index in 0 until path.lastIndex) {
            val start = screenPoint(path[index])
            val end = screenPoint(path[index + 1])
            if (pointToSegmentDistance(target, start, end) < clearance) return true
        }
        return false
    }

    private fun pointToSegmentDistance(point: ScreenPoint, start: ScreenPoint, end: ScreenPoint): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-8f) return hypot(point.x - start.x, point.y - start.y)
        val projection = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared
        val amount = min(1f, max(0f, projection))
        return hypot(point.x - (start.x + amount * dx), point.y - (start.y + amount * dy))
    }

    private fun seedFor(seed: String): Long {
        var hash = -3750763034362895579L
        for (character in seed) {
            hash = hash xor character.code.toLong()
            hash *= 1099511628211L
        }
        return hash xor 0x6A09E667F3BCC909L
    }
}
