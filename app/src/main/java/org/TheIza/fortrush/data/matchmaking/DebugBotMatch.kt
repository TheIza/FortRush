package org.TheIza.fortrush.data.matchmaking

import java.util.UUID
import kotlin.random.Random

private const val DEBUG_MAP_HEIGHT_PER_WIDTH = 16f / 9f
private const val DEBUG_MIDFIELD_NO_BUILD_HALF_HEIGHT = 0.035f
private const val DEBUG_WALL_NO_BUILD_MARGIN = 0.035f
private const val DEBUG_TOWER_PAD_HALF_HEIGHT = 0.04f
private const val DEBUG_MIN_SLOT_SEPARATION = 0.20f

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
    val minimumY = 0.5f + DEBUG_MIDFIELD_NO_BUILD_HALF_HEIGHT + DEBUG_TOWER_PAD_HALF_HEIGHT
    val maximumY = gateY - DEBUG_WALL_NO_BUILD_MARGIN - DEBUG_TOWER_PAD_HALF_HEIGHT
    check(maximumY > minimumY) { "No hay espacio para huecos de torre en este mapa de prueba." }

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
                    candidate.x !in 0.09f..0.91f || candidate.y !in minimumY..maximumY ||
                    debugDistanceToPath(candidate, fullPath) < 0.19f ||
                    kotlin.math.abs(candidate.y - gateY) * DEBUG_MAP_HEIGHT_PER_WIDTH < 0.146f ||
                    debugDistance(candidate, castle) < 0.25f
                ) continue
                add(candidate)
            }
        }
    }

    if (candidates.size < 4) return fallbackDebugSlots(gateY, fullPath, minimumY, maximumY)

    val shuffled = candidates.shuffled(random)
    val selected = mutableListOf<MatchPoint>()
    var visited = 0
    fun choose(startIndex: Int): Boolean {
        if (selected.size == 4) return true
        if (visited >= 10000) return false
        val occupied = selected.flatMap { listOf(it, rotateDebugPoint(it)) }
        for (index in startIndex until shuffled.size) {
            visited++
            val candidate = shuffled[index]
            val pair = listOf(candidate, rotateDebugPoint(candidate))
            if (debugDistance(pair[0], pair[1]) < DEBUG_MIN_SLOT_SEPARATION) continue
            if (pair.any { point -> occupied.any { debugDistance(point, it) < DEBUG_MIN_SLOT_SEPARATION } }) continue
            selected += candidate
            if (choose(index + 1)) return true
            selected.removeAt(selected.lastIndex)
            if (visited >= 10000) return false
        }
        return false
    }
    if (choose(0)) return selected.toList()
    return fallbackDebugSlots(gateY, fullPath, minimumY, maximumY)
}

private fun fallbackDebugSlots(
    gateY: Float,
    fullPath: List<MatchPoint>,
    minimumY: Float,
    maximumY: Float
): List<MatchPoint> {
    val upperRow = minimumY + 0.055f
    val lowerRow = maximumY - 0.055f
    val fallback = listOf(
        MatchPoint(0.105f, upperRow),
        MatchPoint(0.895f, upperRow),
        MatchPoint(0.105f, lowerRow),
        MatchPoint(0.895f, lowerRow)
    )
    check(fallback.all { point ->
        point.y in minimumY..maximumY &&
            debugDistanceToPath(point, fullPath) >= 0.19f &&
            kotlin.math.abs(point.y - gateY) * DEBUG_MAP_HEIGHT_PER_WIDTH >= 0.146f &&
            debugDistance(point, MatchPoint(0.5f, 0.932f)) >= 0.25f
    }) { "No se pudieron colocar huecos de prueba fuera de las zonas prohibidas." }
    return fallback
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
