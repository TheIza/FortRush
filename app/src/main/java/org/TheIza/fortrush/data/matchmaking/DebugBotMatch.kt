package org.TheIza.fortrush.data.matchmaking

import java.util.UUID
import kotlin.random.Random

private const val DEBUG_MAP_HEIGHT_PER_WIDTH = 16f / 9f

/**
 * Creates a local-only visual fixture for the one-device bot preview.
 * Online matches continue to render the exact geometry received from the server.
 */
fun createDebugBotMap(): MatchMap {
    val random = Random.Default
    val side = if (random.nextBoolean()) 1f else -1f
    val leftX = 0.32f + random.nextFloat() * 0.035f
    val rightX = 0.64f + random.nextFloat() * 0.035f
    val gateY = 0.85f + random.nextFloat() * 0.02f
    val firstRowY = gateY - (0.045f + random.nextFloat() * 0.035f)
    val secondRowY = 0.715f + random.nextFloat() * 0.01f
    val thirdRowY = 0.595f + random.nextFloat() * 0.01f
    val centerY = 0.5f + random.nextFloat() * 0.005f
    val firstX = if (side > 0f) rightX else leftX
    val secondX = if (side > 0f) leftX else rightX

    val lowerPath = listOf(
        MatchPoint(0.5f, 0.925f),
        MatchPoint(0.5f, gateY),
        MatchPoint(0.5f, gateY - 0.035f),
        MatchPoint(firstX, firstRowY),
        MatchPoint(firstX, secondRowY),
        MatchPoint(secondX, secondRowY),
        MatchPoint(secondX, thirdRowY),
        MatchPoint(firstX, thirdRowY),
        MatchPoint(0.5f, centerY),
        MatchPoint(0.5f, 0.5f)
    )
    val path = lowerPath + lowerPath.dropLast(1).asReversed().map(::rotateDebugPoint)
    val player1Slots = makeDebugSlots(lowerPath, path, gateY, random)

    return MatchMap(
        seed = "debug-${UUID.randomUUID()}",
        path = path,
        player1Slots = player1Slots,
        player2Slots = player1Slots.map(::rotateDebugPoint),
        player1WallGate = MatchPoint(0.5f, gateY),
        player2WallGate = MatchPoint(0.5f, 1f - gateY),
        player1Castle = MatchPoint(0.5f, 0.932f),
        player2Castle = MatchPoint(0.5f, 0.068f),
        decorations = listOf(
            MatchDecoration("tree", MatchPoint(0.02f, 0.05f), 0.8f),
            MatchDecoration("tree", MatchPoint(0.98f, 0.05f), 0.9f),
            MatchDecoration("tree", MatchPoint(0.02f, 0.95f), 0.9f),
            MatchDecoration("tree", MatchPoint(0.98f, 0.95f), 0.8f)
        )
    )
}

private fun makeDebugSlots(
    lowerPath: List<MatchPoint>,
    fullPath: List<MatchPoint>,
    gateY: Float,
    random: Random
): List<MatchPoint> {
    val route = lowerPath.drop(1)
    val cumulative = mutableListOf(0f)
    for (index in 1 until route.size) {
        cumulative += debugDistance(route[index - 1], route[index])
    }
    val total = cumulative.last()
    val firstDistance = 0.04f
    val lastDistance = total - 0.40f
    val available = lastDistance - firstDistance
    val candidateCount = maxOf(24, (available / 0.025f).toInt())
    val castle = MatchPoint(0.5f, 0.932f)
    val candidates = buildList {
        for (index in 0..candidateCount) {
            val distance = firstDistance + available * index / candidateCount
            val (point, tangent) = debugPointAtDistance(route, cumulative, distance)
            for (side in listOf(-1f, 1f)) {
                val candidate = debugOffsetPoint(
                    point,
                    tangent,
                    side,
                    0.225f + random.nextFloat() * 0.025f
                )
                if (
                    candidate.x !in 0.09f..0.91f || candidate.y !in 0.52f..0.96f ||
                    debugDistanceToPath(candidate, fullPath) < 0.19f ||
                    kotlin.math.abs(candidate.y - gateY) * DEBUG_MAP_HEIGHT_PER_WIDTH < 0.146f ||
                    debugDistance(candidate, castle) < 0.25f
                ) continue
                add(candidate)
            }
        }
    }

    if (candidates.size < 4) return fallbackDebugSlots(lowerPath)
    repeat(100) {
        val shuffled = candidates.shuffled(random)
        val slots = mutableListOf<MatchPoint>()
        for (candidate in shuffled) {
            if (slots.any { debugDistance(it, candidate) < 0.20f }) continue
            slots += candidate
            if (slots.size == 4) break
        }
        if (slots.size != 4) return@repeat
        val allSlots = slots + slots.map(::rotateDebugPoint)
        val separated = allSlots.indices.all { first ->
            (first + 1 until allSlots.size).all { second ->
                debugDistance(allSlots[first], allSlots[second]) >= 0.20f
            }
        }
        if (separated) return slots
    }
    return fallbackDebugSlots(lowerPath)
}

private fun fallbackDebugSlots(lowerPath: List<MatchPoint>): List<MatchPoint> =
    (3..6).mapIndexed { slotIndex, pointIndex ->
        val previous = lowerPath[pointIndex - 1]
        val next = lowerPath[pointIndex + 1]
        val dx = next.x - previous.x
        val dy = (next.y - previous.y) * DEBUG_MAP_HEIGHT_PER_WIDTH
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
        val tangent = MatchPoint(dx / length, dy / length)
        val preferredSide = if (slotIndex % 2 == 0) 1f else -1f
        val preferred = debugOffsetPoint(lowerPath[pointIndex], tangent, preferredSide, 0.235f)
        if (preferred.y > 0.52f && preferred.x in 0.09f..0.91f) {
            preferred
        } else {
            debugOffsetPoint(lowerPath[pointIndex], tangent, -preferredSide, 0.235f)
        }
    }

private fun debugPointAtDistance(
    points: List<MatchPoint>,
    cumulative: List<Float>,
    distance: Float
): Pair<MatchPoint, MatchPoint> {
    val bounded = distance.coerceIn(0f, cumulative.last())
    var index = 0
    while (index < cumulative.lastIndex - 1 && cumulative[index + 1] < bounded) index++
    val segmentLength = cumulative[index + 1] - cumulative[index]
    val progress = if (segmentLength > 0f) (bounded - cumulative[index]) / segmentLength else 0f
    val start = points[index]
    val end = points[index + 1]
    val dx = end.x - start.x
    val dy = (end.y - start.y) * DEBUG_MAP_HEIGHT_PER_WIDTH
    val tangentLength = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
    return MatchPoint(
        start.x + dx * progress,
        start.y + (end.y - start.y) * progress
    ) to MatchPoint(dx / tangentLength, dy / tangentLength)
}

private fun debugOffsetPoint(
    point: MatchPoint,
    tangent: MatchPoint,
    side: Float,
    offset: Float
) = MatchPoint(
    x = point.x - tangent.y * offset * side,
    y = point.y + tangent.x * offset * side / DEBUG_MAP_HEIGHT_PER_WIDTH
)

private fun debugDistance(first: MatchPoint, second: MatchPoint): Float {
    val dx = first.x - second.x
    val dy = (first.y - second.y) * DEBUG_MAP_HEIGHT_PER_WIDTH
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun debugDistanceToPath(point: MatchPoint, path: List<MatchPoint>): Float =
    (0 until path.lastIndex).minOf { index ->
        val start = path[index]
        val end = path[index + 1]
        val px = point.x
        val py = point.y * DEBUG_MAP_HEIGHT_PER_WIDTH
        val sx = start.x
        val sy = start.y * DEBUG_MAP_HEIGHT_PER_WIDTH
        val dx = end.x - sx
        val dy = end.y * DEBUG_MAP_HEIGHT_PER_WIDTH - sy
        val lengthSquared = dx * dx + dy * dy
        val projection = if (lengthSquared == 0f) 0f else {
            (((px - sx) * dx + (py - sy) * dy) / lengthSquared).coerceIn(0f, 1f)
        }
        val offsetX = px - (sx + projection * dx)
        val offsetY = py - (sy + projection * dy)
        kotlin.math.sqrt(offsetX * offsetX + offsetY * offsetY)
    }

private fun rotateDebugPoint(point: MatchPoint) = MatchPoint(1f - point.x, 1f - point.y)
