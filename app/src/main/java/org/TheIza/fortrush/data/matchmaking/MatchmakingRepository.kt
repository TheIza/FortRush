package org.TheIza.fortrush.data.matchmaking

import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.TheIza.fortrush.BuildConfig
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface MatchmakingEvent {
    data class Waiting(val position: Int) : MatchmakingEvent
    data class Found(val matchId: String, val opponentNickname: String, val playerNumber: Int, val map: MatchMap) : MatchmakingEvent
    data class Countdown(val seconds: Int) : MatchmakingEvent
    data class Started(
        val matchId: String,
        val map: MatchMap,
        val economy: MatchEconomySnapshot,
        val state: MatchGameSnapshot
    ) : MatchmakingEvent
    data class GameState(val state: MatchGameSnapshot, val economy: MatchEconomySnapshot) : MatchmakingEvent
    data class TroopsDeployed(
        val actionId: String,
        val ownerPlayerNumber: Int,
        val card: String,
        val economy: MatchEconomySnapshot
    ) : MatchmakingEvent
    data class TowerBuilt(
        val actionId: String,
        val slotId: String,
        val ownerPlayerNumber: Int,
        val card: String,
        val economy: MatchEconomySnapshot
    ) : MatchmakingEvent
    data class ActionRejected(val actionId: String, val slotId: String, val reason: String) : MatchmakingEvent
    data class TowerSold(
        val actionId: String,
        val slotId: String,
        val ownerPlayerNumber: Int,
        val refund: Int,
        val economy: MatchEconomySnapshot
    ) : MatchmakingEvent
    data class MatchResult(val result: MatchGameResult) : MatchmakingEvent
    data object QueueCancelled : MatchmakingEvent
    data class Cancelled(val reason: String, val requeued: Boolean) : MatchmakingEvent
    data class Failure(val message: String) : MatchmakingEvent
}

enum class MatchTowerCard(val wireId: String, val cost: Int, val displayName: String) {
    ARROW_TOWER("arrow_tower", 100, "Torre de flechas"),
    CANNON_TOWER("cannon_tower", 130, "Torre de cañón")
}

enum class MatchTroopCard(val wireId: String, val cost: Int, val displayName: String, val imageResourceName: String) {
    ESPADACHIN("espadachin", 45, "Espadachín", "carta_espadachines"),
    ESCUDERO("escudero", 55, "Escudero", "carta_escuderos")
}

data class MatchIncomeBand(val fromElapsedMs: Long, val untilElapsedMs: Long, val coinsPerSecond: Int)

data class MatchBalanceConfig(
    val version: String,
    val matchDurationMs: Long,
    val startingCoins: Int,
    val maximumCoins: Int,
    val incomeBands: List<MatchIncomeBand>,
    val towerCosts: Map<String, Int>,
    val troopCosts: Map<String, Int> = emptyMap(),
    val towerRangeFractions: Map<String, Float> = emptyMap()
) {
    fun towerCost(card: MatchTowerCard): Int = towerCosts[card.wireId] ?: card.cost
    fun troopCost(card: MatchTroopCard): Int = troopCosts[card.wireId] ?: card.cost
    fun towerRangeFraction(card: MatchTowerCard): Float = towerRangeFractions[card.wireId] ?: 0f

    fun incomeEarnedThroughSecond(elapsedSecond: Long): Long {
        val second = elapsedSecond.coerceAtLeast(0L)
        return incomeBands.sumOf { band ->
            val from = band.fromElapsedMs / 1_000L
            val until = band.untilElapsedMs / 1_000L
            (minOf(second, until) - from).coerceAtLeast(0L) * band.coinsPerSecond
        }
    }

    fun incomeRateAt(elapsedMs: Long): Int =
        incomeBands.firstOrNull { elapsedMs >= it.fromElapsedMs && elapsedMs < it.untilElapsedMs }?.coinsPerSecond ?: 0
}

data class MatchEconomySnapshot(
    val startingCoins: Int,
    val incomePerSecond: Int,
    val startedAtServerMs: Long,
    val serverTimeMs: Long,
    val coinsSpent: Int,
    val coinsAtServerMs: Int? = null,
    val elapsedMatchMs: Long? = null,
    val balance: MatchBalanceConfig? = null
) {
    fun coinsAfterElapsed(elapsedRealtimeMs: Long): Int {
        if (balance != null && coinsAtServerMs != null && elapsedMatchMs != null) {
            val startSecond = elapsedMatchMs / 1_000L
            val endSecond = (elapsedMatchMs + elapsedRealtimeMs.coerceAtLeast(0L)) / 1_000L
            val earned = balance.incomeEarnedThroughSecond(endSecond) - balance.incomeEarnedThroughSecond(startSecond)
            if (coinsAtServerMs >= balance.maximumCoins) return coinsAtServerMs
            return (coinsAtServerMs.toLong() + earned).coerceAtMost(balance.maximumCoins.toLong()).toInt()
        }
        val elapsedAtSnapshot = (serverTimeMs - startedAtServerMs).coerceAtLeast(0L)
        val elapsed = elapsedAtSnapshot + elapsedRealtimeMs.coerceAtLeast(0L)
        val earned = elapsed / 1_000L * incomePerSecond
        return (startingCoins.toLong() + earned - coinsSpent).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    fun incomeRateAfterElapsed(elapsedRealtimeMs: Long): Int =
        balance?.incomeRateAt((elapsedMatchMs ?: 0L) + elapsedRealtimeMs.coerceAtLeast(0L)) ?: incomePerSecond
}

internal fun JSONObject.matchBalanceConfig(): MatchBalanceConfig? {
    val config = optJSONObject("balance") ?: return null
    val version = config.optString("version").takeIf(String::isNotBlank) ?: return null
    val matchDurationMs = config.optJSONObject("match")?.optLong("durationMs", -1L) ?: return null
    val economy = config.optJSONObject("economy") ?: return null
    val startingCoins = economy.optInt("startingCoins", -1)
    val maximumCoins = economy.optInt("maximumCoins", -1)
    val rawBands = economy.optJSONArray("incomeBands") ?: return null
    val bands = (0 until rawBands.length()).mapNotNull { index ->
        val band = rawBands.optJSONObject(index) ?: return@mapNotNull null
        val from = band.optLong("fromElapsedMs", -1L)
        val until = band.optLong("untilElapsedMs", -1L)
        val rate = band.optInt("coinsPerSecond", -1)
        if (from < 0L || until <= from || rate < 0) null else MatchIncomeBand(from, until, rate)
    }
    val towers = config.optJSONObject("cards")?.optJSONObject("towers") ?: return null
    val towerCosts = listOf(MatchTowerCard.ARROW_TOWER, MatchTowerCard.CANNON_TOWER).associate { card ->
        val cost = towers.optJSONObject(card.wireId)?.optInt("cost", -1) ?: -1
        card.wireId to cost
    }
    val towerRanges = listOf(MatchTowerCard.ARROW_TOWER, MatchTowerCard.CANNON_TOWER).associate { card ->
        val range = towers.optJSONObject(card.wireId)?.optDouble("rangeAsBoardWidth", Double.NaN)?.toFloat() ?: Float.NaN
        card.wireId to range
    }
    val troops = config.optJSONObject("cards")?.optJSONObject("troops") ?: return null
    val troopCosts = MatchTroopCard.entries.associate { card ->
        card.wireId to (troops.optJSONObject(card.wireId)?.optInt("cost", -1) ?: -1)
    }
    val bandsAreContiguous = bands.zipWithNext().all { (left, right) -> left.untilElapsedMs == right.fromElapsedMs }
    if (matchDurationMs <= 0L || startingCoins < 0 || maximumCoins < startingCoins ||
        bands.size != rawBands.length() || bands.isEmpty() || bands.first().fromElapsedMs != 0L ||
        bands.last().untilElapsedMs != matchDurationMs || !bandsAreContiguous || towerCosts.values.any { it <= 0 } ||
        troopCosts.values.any { it <= 0 } || towerRanges.values.any { !it.isFinite() || it <= 0f }) return null
    return MatchBalanceConfig(version, matchDurationMs, startingCoins, maximumCoins, bands, towerCosts, troopCosts, towerRanges)
}

internal fun JSONObject.economySnapshot(balance: MatchBalanceConfig? = null): MatchEconomySnapshot? {
    val economy = optJSONObject("economy") ?: return null
    val startingCoins = economy.optInt("startingCoins", -1)
    val incomePerSecond = economy.optInt("incomePerSecond", -1)
    val startedAtServerMs = economy.optLong("startedAtServerMs", -1L)
    val serverTimeMs = economy.optLong("serverTimeMs", -1L)
    val coinsSpent = economy.optInt("coinsSpent", -1)
    val balanceVersion = economy.optString("balanceVersion")
    val coinsAtServerMs = economy.optInt("coins", -1)
    val elapsedMatchMs = economy.optLong("elapsedMatchMs", -1L)
    if (startingCoins < 0 || incomePerSecond < 0 || startedAtServerMs < 0L ||
        serverTimeMs < startedAtServerMs || coinsSpent < 0) return null
    if (balance != null && (balanceVersion != balance.version || startingCoins != balance.startingCoins ||
            coinsAtServerMs < 0 || elapsedMatchMs < 0L ||
            economy.optInt("maximumCoins", -1) != balance.maximumCoins ||
            incomePerSecond != balance.incomeRateAt(elapsedMatchMs))) return null
    return MatchEconomySnapshot(
        startingCoins, incomePerSecond, startedAtServerMs, serverTimeMs, coinsSpent,
        coinsAtServerMs.takeIf { it >= 0 }, elapsedMatchMs.takeIf { it >= 0L }, balance
    )
}

data class MatchPoint(val x: Float, val y: Float)
data class MatchGameTroop(
    val id: String,
    val ownerPlayerNumber: Int,
    val card: String,
    val point: MatchPoint,
    val health: Float,
    val maxHealth: Float
)
data class MatchGameTower(val slotId: String, val ownerPlayerNumber: Int, val card: String)
data class MatchGameProjectile(
    val id: String,
    val ownerPlayerNumber: Int,
    val card: String,
    val from: MatchPoint,
    val to: MatchPoint,
    val progress: Float
)
data class MatchPlayerVitals(val wallHealth: Float, val castleHealth: Float)
data class MatchGameResult(
    val winnerPlayerNumber: Int?,
    val reason: String,
    val elapsedMatchMs: Long,
    val player1WallHealth: Float,
    val player2WallHealth: Float,
    val player1CastleHealth: Float,
    val player2CastleHealth: Float
)
data class MatchGameSnapshot(
    val sequence: Long,
    val phase: String,
    val elapsedMatchMs: Long,
    val remainingMs: Long,
    val player1: MatchPlayerVitals,
    val player2: MatchPlayerVitals,
    val troops: List<MatchGameTroop>,
    val towers: List<MatchGameTower>,
    val projectiles: List<MatchGameProjectile>,
    val result: MatchGameResult?,
    val viewerPlayerNumber: Int
)
data class MatchDecoration(val kind: String, val point: MatchPoint, val scale: Float)
data class MatchCoverageRange(val start: Float, val end: Float)
data class MatchTowerSlot(
    val id: String,
    val ownerPlayerNumber: Int,
    val slotIndex: Int,
    val value: Float,
    val coverage: List<MatchCoverageRange>
)
data class MatchMap(
    val seed: String,
    val path: List<MatchPoint>,
    val player1Slots: List<MatchPoint>,
    val player2Slots: List<MatchPoint>,
    val player1WallGate: MatchPoint,
    val player2WallGate: MatchPoint,
    val player1Castle: MatchPoint,
    val player2Castle: MatchPoint,
    val decorations: List<MatchDecoration>,
    val slotMetadata: List<MatchTowerSlot> = emptyList(),
    val pathWidthFraction: Float = 0.13f,
    val slotDiameterFraction: Float = 0.18f,
    val towerRangeFraction: Float = 0f,
    val pathCumulativeLengths: List<Float> = emptyList(),
    val pathTotalLength: Float = 0f
)

private fun JSONObject.gameResult(): MatchGameResult? {
    if (isNull("result")) return null
    val result = optJSONObject("result") ?: return null
    val winner = result.optInt("winnerPlayerNumber", -1).takeIf { it in 1..2 }
    return MatchGameResult(
        winnerPlayerNumber = winner,
        reason = result.optString("reason", "UNKNOWN"),
        elapsedMatchMs = result.optLong("elapsedMatchMs", 0L),
        player1WallHealth = result.optDouble("player1WallHealth", 0.0).toFloat(),
        player2WallHealth = result.optDouble("player2WallHealth", 0.0).toFloat(),
        player1CastleHealth = result.optDouble("player1CastleHealth", 0.0).toFloat(),
        player2CastleHealth = result.optDouble("player2CastleHealth", 0.0).toFloat()
    )
}

internal fun JSONObject.gameSnapshot(): MatchGameSnapshot? {
    val phase = optString("phase").takeIf(String::isNotBlank) ?: return null
    val first = optJSONObject("player1") ?: return null
    val second = optJSONObject("player2") ?: return null
    fun JSONObject.vitals() = MatchPlayerVitals(
        wallHealth = optDouble("wallHealth", 0.0).toFloat(),
        castleHealth = optDouble("castleHealth", 0.0).toFloat()
    )
    val troops = optJSONArray("troops") ?: return null
    val parsedTroops = (0 until troops.length()).mapNotNull { index ->
        val item = troops.optJSONObject(index) ?: return@mapNotNull null
        val owner = item.optInt("ownerPlayerNumber", 0)
        val point = item.matchPoint() ?: return@mapNotNull null
        val health = item.optDouble("health", Double.NaN).toFloat()
        val maxHealth = item.optDouble("maxHealth", Double.NaN).toFloat()
        if (owner !in 1..2 || !health.isFinite() || !maxHealth.isFinite() || maxHealth <= 0f) return@mapNotNull null
        MatchGameTroop(item.optString("id"), owner, item.optString("card"), point, health, maxHealth)
    }
    val towers = optJSONArray("towers") ?: return null
    val parsedTowers = (0 until towers.length()).mapNotNull { index ->
        val item = towers.optJSONObject(index) ?: return@mapNotNull null
        val owner = item.optInt("ownerPlayerNumber", 0)
        if (owner !in 1..2) null else MatchGameTower(item.optString("slotId"), owner, item.optString("card"))
    }
    val projectiles = optJSONArray("projectiles") ?: return null
    val parsedProjectiles = (0 until projectiles.length()).mapNotNull { index ->
        val item = projectiles.optJSONObject(index) ?: return@mapNotNull null
        val owner = item.optInt("ownerPlayerNumber", 0)
        val from = item.normalizedPoint("fromX", "fromY")
        val to = item.normalizedPoint("toX", "toY")
        if (owner !in 1..2 || from == null || to == null) null else MatchGameProjectile(
            item.optString("id"), owner, item.optString("card"), from, to,
            item.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
        )
    }
    val result = if (has("result") && !isNull("result")) gameResult() else null
    return MatchGameSnapshot(
        sequence = optLong("sequence", 0L),
        phase = phase,
        elapsedMatchMs = optLong("elapsedMatchMs", 0L),
        remainingMs = optLong("remainingMs", 0L),
        player1 = first.vitals(),
        player2 = second.vitals(),
        troops = parsedTroops,
        towers = parsedTowers,
        projectiles = parsedProjectiles,
        result = result,
        viewerPlayerNumber = optInt("viewerPlayerNumber", 0)
    )
}

internal fun JSONObject.resultEvent(): MatchGameResult? {
    val wrapper = JSONObject().put("result", this)
    return wrapper.gameResult()
}

private fun JSONObject.points(key: String): List<MatchPoint> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val point = array.optJSONObject(index) ?: return@mapNotNull null
        MatchPoint(point.optDouble("x", Double.NaN).toFloat(), point.optDouble("y", Double.NaN).toFloat())
    }.filter { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f }
}

private fun JSONObject.matchPoint(): MatchPoint? {
    val x = optDouble("x", Double.NaN).toFloat()
    val y = optDouble("y", Double.NaN).toFloat()
    return if (x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) MatchPoint(x, y) else null
}

private fun JSONObject.nestedPoint(parent: String, key: String): MatchPoint? =
    optJSONObject(parent)?.optJSONObject(key)?.matchPoint()

private fun JSONObject.worldPoint(width: Float, height: Float): MatchPoint? {
    val x = optDouble("x", Double.NaN).toFloat() / width
    val y = optDouble("y", Double.NaN).toFloat() / height
    return if (x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) MatchPoint(x, y) else null
}

private fun JSONObject.worldPoint(parent: String, key: String, width: Float, height: Float): MatchPoint? =
    optJSONObject(parent)?.optJSONObject(key)?.worldPoint(width, height)

private fun JSONObject.normalizedPoint(xKey: String, yKey: String): MatchPoint? {
    val x = optDouble(xKey, Double.NaN).toFloat()
    val y = optDouble(yKey, Double.NaN).toFloat()
    return if (x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) MatchPoint(x, y) else null
}

private fun parseV2Map(json: JSONObject, pathObject: JSONObject): MatchMap? {
    val width = json.optDouble("width", Double.NaN).toFloat()
    val height = json.optDouble("height", Double.NaN).toFloat()
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
    val totalLength = pathObject.optDouble("totalLength", Double.NaN).toFloat()
    val pathArray = pathObject.optJSONArray("points") ?: return null
    val path = (0 until pathArray.length()).mapNotNull { index ->
        pathArray.optJSONObject(index)?.worldPoint(width, height)
    }
    if (path.size < 5 || !totalLength.isFinite() || totalLength <= 0f) return null
    val rawCumulative = pathObject.optJSONArray("cumLen") ?: return null
    val cumulative = (0 until rawCumulative.length()).map { rawCumulative.optDouble(it, Double.NaN).toFloat() }
    if (cumulative.size != path.size || cumulative.firstOrNull() != 0f || cumulative.lastOrNull() != totalLength ||
        cumulative.zipWithNext().any { (left, right) -> !left.isFinite() || right <= left }) return null

    val slotsArray = json.optJSONArray("slots") ?: return null
    val slotMetadata = mutableListOf<MatchTowerSlot>()
    val player1 = mutableListOf<MatchPoint>()
    val player2 = mutableListOf<MatchPoint>()
    val slotCounters = intArrayOf(0, 0)
    val ids = mutableSetOf<String>()
    for (index in 0 until slotsArray.length()) {
        val item = slotsArray.optJSONObject(index) ?: return null
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        if (!ids.add(id)) return null
        val owner = when (item.optString("owner")) {
            "player1" -> 1
            "player2" -> 2
            else -> return null
        }
        val point = item.worldPoint(width, height) ?: return null
        val value = item.optDouble("value", Double.NaN).toFloat()
        if (!value.isFinite() || value <= 0f) return null
        val coverageArray = item.optJSONArray("coverage") ?: return null
        val coverage = (0 until coverageArray.length()).map { coverageIndex ->
            val interval = coverageArray.optJSONArray(coverageIndex) ?: return null
            if (interval.length() != 2) return null
            val start = interval.optDouble(0, Double.NaN).toFloat()
            val end = interval.optDouble(1, Double.NaN).toFloat()
            if (!start.isFinite() || !end.isFinite() || start < 0f || end > totalLength || end <= start) return null
            MatchCoverageRange(start, end)
        }
        if (coverage.isEmpty()) return null
        val slotIndex = slotCounters[owner - 1]++
        if (owner == 1) player1 += point else player2 += point
        slotMetadata += MatchTowerSlot(id, owner, slotIndex, value, coverage)
    }
    if (player1.isEmpty() || player1.size != player2.size) return null

    val pathWidth = pathObject.optDouble("width", Double.NaN).toFloat()
    val towerRange = json.optJSONObject("meta")?.optDouble("towerRange", Double.NaN)?.toFloat() ?: Float.NaN
    val slotRadius = json.optJSONObject("meta")?.optDouble("slotRadius", Double.NaN)?.toFloat() ?: Float.NaN
    if (!pathWidth.isFinite() || pathWidth <= 0f || !towerRange.isFinite() || towerRange <= 0f ||
        !slotRadius.isFinite() || slotRadius <= 0f) return null

    val map = MatchMap(
        seed = json.optString("seed"),
        path = path,
        player1Slots = player1,
        player2Slots = player2,
        player1WallGate = json.worldPoint("wallGates", "player1", width, height) ?: MatchPoint(0.5f, 0.86f),
        player2WallGate = json.worldPoint("wallGates", "player2", width, height) ?: MatchPoint(0.5f, 0.14f),
        player1Castle = json.worldPoint("castlePositions", "player1", width, height) ?: MatchPoint(0.5f, 0.932f),
        player2Castle = json.worldPoint("castlePositions", "player2", width, height) ?: MatchPoint(0.5f, 0.068f),
        decorations = emptyList(),
        slotMetadata = slotMetadata,
        pathWidthFraction = (pathWidth / width).coerceIn(0.02f, 0.20f),
        slotDiameterFraction = (slotRadius * 2f / width).coerceIn(0.04f, 0.20f),
        towerRangeFraction = (towerRange / width).coerceIn(0.01f, 0.50f),
        pathCumulativeLengths = cumulative,
        pathTotalLength = totalLength
    )
    return map.copy(decorations = MapDecorationGenerator.generate(map))
}

internal fun parseMap(json: JSONObject): MatchMap? {
    json.optJSONObject("path")?.let { return parseV2Map(json, it) }
    val path = json.points("path")
    val slots = json.optJSONObject("towerSlots") ?: return null
    val player1 = slots.points("player1")
    val player2 = slots.points("player2")
    if (path.size < 5 || player1.size != 4 || player2.size != 4) return null
    val decorations = json.optJSONArray("decorations")?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val kind = item.optString("kind")
            if (kind !in setOf("tree", "bush", "rock", "flower")) return@mapNotNull null
            val point = item.matchPoint() ?: return@mapNotNull null
            val scale = item.optDouble("scale", 1.0).toFloat()
            if (!scale.isFinite() || scale !in 0.5f..1.5f) return@mapNotNull null
            MatchDecoration(kind, point, scale)
        }
    }.orEmpty()
    val map = MatchMap(
        seed = json.optString("seed"),
        path = path,
        player1Slots = player1,
        player2Slots = player2,
        player1WallGate = json.nestedPoint("wallGates", "player1") ?: MatchPoint(0.5f, 0.86f),
        player2WallGate = json.nestedPoint("wallGates", "player2") ?: MatchPoint(0.5f, 0.14f),
        player1Castle = json.nestedPoint("castlePositions", "player1") ?: MatchPoint(0.5f, 0.90f),
        player2Castle = json.nestedPoint("castlePositions", "player2") ?: MatchPoint(0.5f, 0.10f),
        decorations = decorations
    )
    return map.copy(decorations = MapDecorationGenerator.generate(map))
}

class MatchmakingConnection internal constructor(private val socket: WebSocket) {
    fun cancelSearch() = send("queue.cancel")
    fun mapLoaded() = send("match.loaded")
    fun close() { socket.close(1000, "Client closed") }
    private fun send(type: String) {
        socket.send(JSONObject().put("type", type).put("requestId", UUID.randomUUID().toString()).toString())
    }

    fun buildTower(card: MatchTowerCard, slotId: String): String? {
        val actionId = UUID.randomUUID().toString()
        val sent = socket.send(
            JSONObject()
                .put("type", "build_tower")
                .put("card", card.wireId)
                .put("slotId", slotId)
                .put("actionId", actionId)
                .toString()
        )
        return actionId.takeIf { sent }
    }

    fun deployTroop(card: MatchTroopCard, position: Float): String? {
        val actionId = UUID.randomUUID().toString()
        val sent = socket.send(
            JSONObject().put("type", "deploy_troop").put("card", card.wireId)
                .put("position", position.toDouble()).put("actionId", actionId).toString()
        )
        return actionId.takeIf { sent }
    }

    fun sellTower(slotId: String): String? {
        val actionId = UUID.randomUUID().toString()
        val sent = socket.send(JSONObject().put("type", "sell_tower").put("slotId", slotId)
            .put("actionId", actionId).toString())
        return actionId.takeIf { sent }
    }

    fun surrender(): String? {
        val actionId = UUID.randomUUID().toString()
        val sent = socket.send(JSONObject().put("type", "match.surrender")
            .put("actionId", actionId).toString())
        return actionId.takeIf { sent }
    }
}

class MatchmakingRepository {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    fun connect(onEvent: (MatchmakingEvent) -> Unit, onConnected: (MatchmakingConnection) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onEvent(MatchmakingEvent.Failure("Inicia sesión para buscar partida."))
            return
        }
        user.getIdToken(false).addOnSuccessListener { result ->
            val token = result.token
            if (token.isNullOrBlank()) {
                onEvent(MatchmakingEvent.Failure("No se pudo validar tu sesión."))
                return@addOnSuccessListener
            }
            val url = BuildConfig.FORT_RUSH_MATCHMAKING_BASE_URL
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
                .trimEnd('/') + "/ws"
            val request = Request.Builder().url(url).build()
            client.newWebSocket(request, object : WebSocketListener() {
                private var authenticated = false
                private var connectionProvided = false
                private var pendingMap: MatchMap? = null
                private var gameStartReceived = false
                private var activeBalance: MatchBalanceConfig? = null
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(JSONObject().put("type", "auth").put("token", token).toString())
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                    when (json.optString("type")) {
                        "heartbeat" -> webSocket.send(JSONObject().put("type", "heartbeat.ack").toString())
                        "auth.ok" -> if (!authenticated) {
                            authenticated = true
                            webSocket.send(JSONObject().put("type", "queue.join").put("requestId", UUID.randomUUID().toString()).toString())
                        }
                        "queue.waiting" -> {
                            if (!connectionProvided) {
                                connectionProvided = true
                                onConnected(MatchmakingConnection(webSocket))
                            }
                            onEvent(MatchmakingEvent.Waiting(json.optInt("position", 1)))
                        }
                        "queue.cancelled" -> onEvent(MatchmakingEvent.QueueCancelled)
                        "match.found" -> {
                            val map = json.optJSONObject("map")?.let(::parseMap)
                            if (map == null) onEvent(MatchmakingEvent.Failure("El servidor envió un mapa no válido."))
                            else {
                                pendingMap = map
                                onEvent(MatchmakingEvent.Found(json.optString("matchId"), json.optString("opponentNickname"), json.optInt("playerNumber"), map))
                            }
                        }
                        "match.countdown" -> onEvent(MatchmakingEvent.Countdown(json.optInt("seconds")))
                        "game_start" -> {
                            val map = json.optJSONObject("map")?.let(::parseMap)
                            val balance = json.matchBalanceConfig()
                            val economy = json.economySnapshot(balance)
                            val state = json.optJSONObject("state")?.gameSnapshot()
                            if (map == null) onEvent(MatchmakingEvent.Failure("El servidor inició la partida con un mapa no válido."))
                            else if (balance == null) onEvent(MatchmakingEvent.Failure("El servidor no envió la versión de balance de esta partida."))
                            else if (economy == null) onEvent(MatchmakingEvent.Failure("El servidor inició la partida sin datos de economía válidos."))
                            else if (state == null) onEvent(MatchmakingEvent.Failure("El servidor inició la partida sin el estado de combate."))
                            else {
                                activeBalance = balance
                                gameStartReceived = true
                                pendingMap = map
                                onEvent(MatchmakingEvent.Started(json.optString("matchId"), map, economy, state))
                            }
                        }
                        "match.started" -> if (!gameStartReceived) {
                            val map = pendingMap
                            if (map == null) onEvent(MatchmakingEvent.Failure("El servidor inició la partida sin un mapa cargado."))
                            else {
                                val now = System.currentTimeMillis()
                                onEvent(MatchmakingEvent.Started(
                                    json.optString("matchId"),
                                    map,
                                    MatchEconomySnapshot(150, 5, now, now, 0),
                                    MatchGameSnapshot(0, "IN_MATCH", 0, 240_000,
                                        MatchPlayerVitals(500f, 400f), MatchPlayerVitals(500f, 400f),
                                        emptyList(), emptyList(), emptyList(), null, json.optInt("playerNumber", 1))
                                ))
                            }
                        }
                        "match.state" -> {
                            val state = json.optJSONObject("state")?.gameSnapshot()
                            val economy = json.economySnapshot(activeBalance)
                            if (state != null && economy != null) onEvent(MatchmakingEvent.GameState(state, economy))
                        }
                        "match.result" -> json.resultEvent()?.let { onEvent(MatchmakingEvent.MatchResult(it)) }
                        "troops_deployed" -> {
                            val economy = json.economySnapshot(activeBalance)
                            val owner = json.optInt("ownerPlayerNumber", 0)
                            val actionId = json.optString("actionId")
                            if (economy != null && owner in 1..2 && actionId.isNotBlank()) {
                                onEvent(MatchmakingEvent.TroopsDeployed(actionId, owner, json.optString("card"), economy))
                            }
                        }
                        "tower_built" -> {
                            val economy = json.economySnapshot(activeBalance)
                            val owner = json.optInt("ownerPlayerNumber", 0)
                            val actionId = json.optString("actionId")
                            val slotId = json.optString("slotId")
                            val card = json.optString("card")
                            if (economy != null && owner in 1..2 && actionId.isNotBlank() && slotId.isNotBlank() &&
                                MatchTowerCard.entries.any { it.wireId == card }) {
                                onEvent(MatchmakingEvent.TowerBuilt(actionId, slotId, owner, card, economy))
                            }
                        }
                        "tower_sold" -> {
                            val economy = json.economySnapshot(activeBalance)
                            val owner = json.optInt("ownerPlayerNumber", 0)
                            val actionId = json.optString("actionId")
                            val slotId = json.optString("slotId")
                            if (economy != null && owner in 1..2 && actionId.isNotBlank() && slotId.isNotBlank()) {
                                onEvent(MatchmakingEvent.TowerSold(actionId, slotId, owner, json.optInt("refund", 0), economy))
                            }
                        }
                        "action_rejected" -> onEvent(MatchmakingEvent.ActionRejected(
                            actionId = json.optString("actionId"),
                            slotId = json.optString("slotId"),
                            reason = json.optString("reason", "UNKNOWN")
                        ))
                        "match.cancelled" -> onEvent(MatchmakingEvent.Cancelled(json.optString("reason"), json.optBoolean("requeued")))
                        "error" -> if (json.optString("code") != "CANCEL_NOT_ALLOWED") {
                            onEvent(MatchmakingEvent.Failure(json.optString("message", "No se pudo buscar partida.")))
                        }
                    }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onEvent(MatchmakingEvent.Failure("Se perdió la conexión con el servidor. Reintenta."))
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (code != 1000) onEvent(MatchmakingEvent.Failure("La conexión de emparejamiento se cerró. Reintenta."))
                }
            })
        }.addOnFailureListener {
            onEvent(MatchmakingEvent.Failure("No se pudo obtener la sesión. Reintenta."))
        }
    }
}
