package org.TheIza.fortrush.ui.Screen

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.TheIza.fortrush.R
import org.TheIza.fortrush.data.matchmaking.MatchDecoration
import org.TheIza.fortrush.data.matchmaking.MatchEconomySnapshot
import org.TheIza.fortrush.data.matchmaking.MatchGameResult
import org.TheIza.fortrush.data.matchmaking.MatchGameSnapshot
import org.TheIza.fortrush.data.matchmaking.MatchGameTroop
import org.TheIza.fortrush.data.matchmaking.MatchMap
import org.TheIza.fortrush.data.matchmaking.MatchPoint
import org.TheIza.fortrush.data.matchmaking.MatchTroopCard
import org.TheIza.fortrush.data.matchmaking.MatchTowerCard

enum class ArenaTowerKind { ARROW, CANNON }

private const val ARENA_TOWER_WIDTH_FRACTION = 0.14f

data class ArenaTowerPlacement(
    val ownerPlayerNumber: Int,
    val slotIndex: Int,
    val kind: ArenaTowerKind,
    val slotId: String
)

private data class ArenaSlotVisual(
    val ownerPlayerNumber: Int,
    val slotIndex: Int,
    val point: MatchPoint,
    val id: String
)

private data class ArenaDebugTroopPreview(
    val id: String,
    val card: String,
    val ownerPlayerNumber: Int,
    val pathDistance: Float
)

/** Local visual-only formation; it does not change the authoritative combat simulation. */
internal fun debugTroopPreviewPositions(
    card: MatchTroopCard,
    position: Float,
    totalLength: Float,
    playerNumber: Int
): List<Float> {
    if (!position.isFinite() || !totalLength.isFinite() || totalLength <= 0f || playerNumber !in 1..2) return emptyList()
    val safeHalf = totalLength * 0.475f
    val minPosition = if (playerNumber == 1) 0f else totalLength - safeHalf
    val maxPosition = if (playerNumber == 1) safeHalf else totalLength
    if (position !in minPosition..maxPosition) return emptyList()

    val count = if (card == MatchTroopCard.ESPADACHIN) 3 else 2
    val visualSpacing = 24f
    return List(count) { index ->
        val offset = (index - (count - 1) / 2f) * visualSpacing
        (position + offset).coerceIn(minPosition, maxPosition)
    }
}

/**
 * Shared map geometry is server generated; this screen only scales, rotates, and draws it.
 */
@Composable
fun BattleArenaScreen(
    map: MatchMap,
    playerNumber: Int,
    opponentNickname: String,
    gameState: MatchGameSnapshot? = null,
    matchResult: MatchGameResult? = null,
    destroyedWallOwners: Set<Int> = emptySet(),
    towerPlacements: List<ArenaTowerPlacement> = emptyList(),
    pendingSlotIds: Set<String> = emptySet(),
    economy: MatchEconomySnapshot? = null,
    buildError: String? = null,
    isMatchActive: Boolean = false,
    onBuildTower: (MatchTowerCard, String) -> Boolean = { _, _ -> false },
    onDeployTroop: (MatchTroopCard, Float) -> Boolean = { _, _ -> false },
    onSellTower: (String) -> Boolean = { false },
    onSurrender: () -> Unit = {},
    onReturnHome: () -> Unit = {},
    pendingTroopCards: Set<String> = emptySet(),
    onClearBuildError: () -> Unit = {},
    isDebugBotMode: Boolean = false,
    onExitDebug: (() -> Unit)? = null
) {
    val opponentPlayerNumber = if (playerNumber == 1) 2 else 1
    var selectedSlotId by remember(map.seed, playerNumber) { mutableStateOf<String?>(null) }
    var selectedTowerCard by remember(map.seed, playerNumber) { mutableStateOf<MatchTowerCard?>(null) }
    var selectedTroopCard by remember(map.seed, playerNumber) { mutableStateOf<MatchTroopCard?>(null) }
    var debugTroopPreviews by remember(map.seed, playerNumber) { mutableStateOf(emptyList<ArenaDebugTroopPreview>()) }
    var draggedTowerCard by remember(map.seed, playerNumber) { mutableStateOf<MatchTowerCard?>(null) }
    var dragPointerRoot by remember(map.seed, playerNumber) { mutableStateOf<Offset?>(null) }
    var dragTargetRoot by remember(map.seed, playerNumber) { mutableStateOf<Offset?>(null) }
    var dragTargetSlotId by remember(map.seed, playerNumber) { mutableStateOf<String?>(null) }
    var dragTargetValid by remember(map.seed, playerNumber) { mutableStateOf(false) }
    var localFeedback by remember(map.seed, playerNumber) { mutableStateOf<String?>(null) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var arenaCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var blueprintReturn by remember { mutableStateOf<BlueprintReturn?>(null) }
    val returnX = remember { Animatable(0f) }
    val returnY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val economyReceivedAt = remember(economy) { SystemClock.elapsedRealtime() }
    var economyTick by remember(map.seed) { mutableLongStateOf(0L) }
    var gameClockTick by remember(map.seed) { mutableLongStateOf(0L) }
    var previousGameState by remember(map.seed) { mutableStateOf<MatchGameSnapshot?>(null) }
    var latestGameState by remember(map.seed) { mutableStateOf<MatchGameSnapshot?>(null) }
    var previousSnapshotAtMs by remember(map.seed) { mutableLongStateOf(0L) }
    var latestSnapshotAtMs by remember(map.seed) { mutableLongStateOf(0L) }
    val animationFrame = remember(map.seed) { mutableLongStateOf(0L) }
    var showSurrenderConfirmation by remember(map.seed) { mutableStateOf(false) }
    var showSellConfirmation by remember(map.seed) { mutableStateOf<String?>(null) }
    LaunchedEffect(economy) {
        while (economy != null) {
            delay(250)
            economyTick += 1L
        }
    }
    LaunchedEffect(map.seed, gameState) {
        gameClockTick = 0L
        while (gameState != null && gameState.phase != "FINISHED") {
            delay(250)
            gameClockTick += 1L
        }
    }
    // Refresh from the received content as well: legacy servers repeated sequence
    // while troops and health changed, leaving the Canvas on the initial empty state.
    LaunchedEffect(map.seed, gameState) {
        val snapshot = gameState ?: return@LaunchedEffect
        previousGameState = latestGameState
        previousSnapshotAtMs = latestSnapshotAtMs
        latestGameState = snapshot
        latestSnapshotAtMs = SystemClock.elapsedRealtime()
    }
    LaunchedEffect(map.seed, gameState?.phase) {
        if (gameState?.phase == "IN_MATCH" || gameState?.phase == "SUDDEN_DEATH") {
            while (true) {
                delay(16)
                animationFrame.longValue += 1L
            }
        }
    }
    val currentCoins = remember(economy, economyTick) {
        economy?.coinsAfterElapsed(SystemClock.elapsedRealtime() - economyReceivedAt) ?: 0
    }
    fun towerCost(card: MatchTowerCard): Int = economy?.balance?.towerCost(card) ?: card.cost
    fun troopCost(card: MatchTroopCard): Int = economy?.balance?.troopCost(card) ?: card.cost
    val localVitals = if (playerNumber == 1) gameState?.player1 else gameState?.player2
    val opponentVitals = if (playerNumber == 1) gameState?.player2 else gameState?.player1
    val remainingForClock = ((gameState?.remainingMs ?: 240_000L) - gameClockTick * 250L).coerceAtLeast(0L)
    val displayedResult = matchResult ?: gameState?.result
    LaunchedEffect(blueprintReturn) {
        val returning = blueprintReturn ?: return@LaunchedEffect
        returnX.snapTo(returning.from.x)
        returnY.snapTo(returning.from.y)
        coroutineScope {
            launch { returnX.animateTo(returning.to.x, spring(dampingRatio = 0.72f, stiffness = 500f)) }
            launch { returnY.animateTo(returning.to.y, spring(dampingRatio = 0.72f, stiffness = 500f)) }
        }
        blueprintReturn = null
    }

    fun updateDragTarget(card: MatchTowerCard, cardCoordinates: LayoutCoordinates, cardPoint: Offset) {
        val root = rootCoordinates ?: return
        val arena = arenaCoordinates ?: return
        val rootPoint = runCatching { root.localPositionOf(cardCoordinates, cardPoint) }.getOrNull() ?: return
        dragPointerRoot = rootPoint
        val arenaPoint = runCatching { arena.localPositionOf(root, rootPoint) }.getOrNull() ?: return
        val canvasWidth = arena.size.width.toFloat()
        val canvasHeight = arena.size.height.toFloat()
        val boardWidth = minOf(canvasWidth, canvasHeight * 9f / 16f)
        val snapRadius = maxOf(boardWidth * map.slotDiameterFraction * 1.5f, with(density) { 32.dp.toPx() })
        val targets = arenaSlotTargets(map, playerNumber, canvasWidth, canvasHeight, snapRadius)
        val target = pickOwnSlot(MatchPoint(arenaPoint.x, arenaPoint.y), targets, playerNumber)
        dragTargetSlotId = target?.id
        if (target == null) {
            dragTargetRoot = null
            dragTargetValid = false
            return
        }
        val occupied = towerPlacements.any { it.slotId == target.id } || target.id in pendingSlotIds
        val eligibility = assessArenaBuild(
            slotOwnerPlayerNumber = target.ownerPlayerNumber,
            localPlayerNumber = playerNumber,
            isMatchActive = isMatchActive,
            isSlotOccupied = occupied,
            hasEnoughCoins = currentCoins >= towerCost(card)
        )
        dragTargetValid = eligibility.allowed
        dragTargetRoot = runCatching { root.localPositionOf(arena, Offset(target.x, target.y)) }.getOrNull()
    }

    fun tryBuild(card: MatchTowerCard, slotId: String): Boolean {
        val slot = map.slotMetadata.firstOrNull { it.id == slotId }
        val owner = slot?.ownerPlayerNumber ?: when {
            slotId.startsWith("p1-") || slotId.startsWith("player1-") -> 1
            slotId.startsWith("p2-") || slotId.startsWith("player2-") -> 2
            else -> null
        }
        val occupied = towerPlacements.any { it.slotId == slotId } || slotId in pendingSlotIds
        val eligibility = assessArenaBuild(
            slotOwnerPlayerNumber = owner,
            localPlayerNumber = playerNumber,
            isMatchActive = isMatchActive,
            isSlotOccupied = occupied,
            hasEnoughCoins = currentCoins >= towerCost(card)
        )
        if (!eligibility.allowed) {
            localFeedback = when (eligibility.reason) {
                ArenaBuildBlockReason.INVALID_STATE -> "La partida no está en curso."
                ArenaBuildBlockReason.NOT_YOUR_SLOT -> "Solo puedes construir en tus huecos."
                ArenaBuildBlockReason.SLOT_OCCUPIED -> "Ese hueco ya tiene una torre o una construcción pendiente."
                ArenaBuildBlockReason.NOT_ENOUGH_COINS -> "No tienes monedas suficientes para esa torre."
                ArenaBuildBlockReason.ON_COOLDOWN -> "Espera un momento antes de construir otra torre."
                null -> "No se puede construir en ese hueco."
            }
            return false
        }
        if (onBuildTower(card, slotId)) {
            selectedTowerCard = null
            selectedTroopCard = null
            selectedSlotId = null
            localFeedback = null
            return true
        } else {
            localFeedback = "No se pudo enviar la construcción. Comprueba la conexión."
            return false
        }
    }

    fun finishDrag(card: MatchTowerCard, cardCoordinates: LayoutCoordinates) {
        val slotId = dragTargetSlotId
        val droppedOnValidSlot = slotId != null && dragTargetValid
        val submitted = slotId?.takeIf { dragTargetValid }?.let { tryBuild(card, it) } ?: false
        if (!droppedOnValidSlot) {
            localFeedback = when {
                !isMatchActive -> "La partida no está en curso."
                slotId == null -> "Suelta la torre sobre uno de tus huecos."
                towerPlacements.any { it.slotId == slotId } || slotId in pendingSlotIds -> "Ese hueco ya está ocupado."
                currentCoins < towerCost(card) -> "No tienes monedas suficientes para esa torre."
                else -> "No se puede construir en ese hueco."
            }
        }
        if (!submitted) {
            val center = Offset(cardCoordinates.size.width / 2f, cardCoordinates.size.height / 2f)
            val home = rootCoordinates?.let { root -> runCatching { root.localPositionOf(cardCoordinates, center) }.getOrNull() }
            val from = dragTargetRoot ?: dragPointerRoot
            if (from != null && home != null) blueprintReturn = BlueprintReturn(card, from, home)
        }
        draggedTowerCard = null
        dragPointerRoot = null
        dragTargetRoot = null
        dragTargetSlotId = null
        dragTargetValid = false
        selectedTowerCard = null
        selectedTroopCard = null
    }

    fun cancelDrag(card: MatchTowerCard, cardCoordinates: LayoutCoordinates) {
        val center = Offset(cardCoordinates.size.width / 2f, cardCoordinates.size.height / 2f)
        val home = rootCoordinates?.let { root -> runCatching { root.localPositionOf(cardCoordinates, center) }.getOrNull() }
        val from = dragTargetRoot ?: dragPointerRoot
        if (from != null && home != null) blueprintReturn = BlueprintReturn(card, from, home)
        draggedTowerCard = null
        dragPointerRoot = null
        dragTargetRoot = null
        dragTargetSlotId = null
        dragTargetValid = false
        selectedTowerCard = null
        selectedTroopCard = null
    }

    val rejectionText = buildError ?: localFeedback ?: selectedTowerCard?.let {
        "${it.displayName} seleccionada · toca un hueco propio libre"
    } ?: selectedTroopCard?.let { "${it.displayName} seleccionada · toca el camino en tu zona" }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF17232D))
            .onGloballyPositioned { rootCoordinates = it }
    ) {
        Column(Modifier.fillMaxSize()) {
            ArenaOpponentStatus(
                nickname = opponentNickname,
                playerNumber = opponentPlayerNumber,
                castleHealth = opponentVitals?.castleHealth,
                wallHealth = opponentVitals?.wallHealth,
                phase = gameState?.phase,
                remainingMs = remainingForClock,
                onExitDebug = if (isDebugBotMode) onExitDebug else null,
                onSurrender = if (!isDebugBotMode && gameState?.phase == "IN_MATCH") {
                    { showSurrenderConfirmation = true }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )

            ArenaMapCanvas(
                map = map,
                playerNumber = playerNumber,
                gameState = latestGameState,
                previousGameState = previousGameState,
                previousSnapshotAtMs = previousSnapshotAtMs,
                latestSnapshotAtMs = latestSnapshotAtMs,
                animationFrame = animationFrame,
                destroyedWallOwners = gameState?.let { state ->
                    buildSet {
                        if (state.player1.wallHealth <= 0f) add(1)
                        if (state.player2.wallHealth <= 0f) add(2)
                    }
                } ?: destroyedWallOwners,
                towerPlacements = towerPlacements,
                pendingSlotIds = pendingSlotIds,
                selectedSlotId = selectedSlotId,
                selectedTowerCard = selectedTowerCard,
                draggedTowerCard = draggedTowerCard,
                selectedTroopCard = selectedTroopCard,
                debugTroopPreviews = debugTroopPreviews,
                dragTargetSlotId = dragTargetSlotId,
                dragTargetValid = dragTargetValid,
                canBuild = isMatchActive,
                coins = currentCoins,
                arrowTowerRangeFraction = economy?.balance?.towerRangeFraction(MatchTowerCard.ARROW_TOWER)
                    ?: map.towerRangeFraction,
                cannonTowerRangeFraction = economy?.balance?.towerRangeFraction(MatchTowerCard.CANNON_TOWER)
                    ?: map.towerRangeFraction * 0.8f,
                onTapOwnSlot = { slotId ->
                    val card = selectedTowerCard
                    if (card != null) {
                        tryBuild(card, slotId)
                        onClearBuildError()
                    }
                    else if (towerPlacements.any { it.slotId == slotId && it.ownerPlayerNumber == playerNumber }) {
                        selectedSlotId = slotId
                        localFeedback = null
                    } else {
                        selectedSlotId = null
                    }
                },
                onTapPath = { position ->
                    val card = selectedTroopCard
                    if (card == null) {
                        localFeedback = "Selecciona una carta de tropa y toca el camino."
                    } else if (pendingTroopCards.contains(card.wireId)) {
                        localFeedback = "El servidor está confirmando ese despliegue."
                    } else if (isMatchActive && onDeployTroop(card, position)) {
                        if (isDebugBotMode) {
                            val positions = debugTroopPreviewPositions(card, position, map.pathTotalLength, playerNumber)
                            val firstId = debugTroopPreviews.size
                            debugTroopPreviews = debugTroopPreviews + positions.mapIndexed { index, pathDistance ->
                                ArenaDebugTroopPreview(
                                    id = "debug-${map.seed}-$firstId-$index",
                                    card = card.wireId,
                                    ownerPlayerNumber = playerNumber,
                                    pathDistance = pathDistance
                                )
                            }
                        }
                        selectedTroopCard = null
                        selectedTowerCard = null
                        localFeedback = if (isDebugBotMode) "Tropa colocada · vista de prueba, sin combate." else "Tropa enviada al camino…"
                    } else {
                        localFeedback = "No se pudo desplegar. Comprueba la fase y tus monedas."
                    }
                },
                onInvalidPathTouch = {
                    if (selectedTroopCard != null) localFeedback = "Toca el tramo verde del camino, en tu mitad."
                },
                onPositioned = { arenaCoordinates = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics {
                        contentDescription = "Arena aleatoria compartida, " +
                            "${map.player1Slots.size + map.player2Slots.size} huecos de torre. " +
                        "Selecciona una tropa y toca el camino en tu mitad para desplegarla. " +
                        "Para torres, toca una carta y luego un hueco propio libre, o mantenla pulsada y arrástrala. " +
                            "Vista del jugador $playerNumber"
                    }
            )

            ArenaCardHand(
                modifier = Modifier.fillMaxWidth(),
                coins = currentCoins,
                incomePerSecond = economy?.incomeRateAfterElapsed(SystemClock.elapsedRealtime() - economyReceivedAt) ?: 5,
                ownWallHealth = localVitals?.wallHealth,
                ownCastleHealth = localVitals?.castleHealth,
                phase = gameState?.phase,
                towerCosts = MatchTowerCard.entries.associate { it.wireId to towerCost(it) },
                troopCosts = MatchTroopCard.entries.associate { it.wireId to troopCost(it) },
                selectedTowerCard = selectedTowerCard,
                selectedTroopCard = selectedTroopCard,
                draggedTowerCard = draggedTowerCard,
                message = rejectionText,
                onTowerCardTap = { card ->
                    selectedTowerCard = if (selectedTowerCard == card) null else card
                    selectedTroopCard = null
                    selectedSlotId = null
                    localFeedback = null
                    onClearBuildError()
                },
                onTroopCardTap = { card ->
                    selectedTroopCard = if (selectedTroopCard == card) null else card
                    selectedTowerCard = null
                    selectedSlotId = null
                    localFeedback = null
                    onClearBuildError()
                },
                onDragStart = { card, coordinates, point ->
                    selectedTowerCard = null
                    selectedTroopCard = null
                    selectedSlotId = null
                    draggedTowerCard = card
                    updateDragTarget(card, coordinates, point)
                },
                onDragMove = ::updateDragTarget,
                onDragEnd = ::finishDrag,
                onDragCancel = ::cancelDrag
            )
        }

        val activeCard = draggedTowerCard
        val activePosition = if (dragTargetSlotId != null) dragTargetRoot else dragPointerRoot
        if (activeCard != null && activePosition != null) {
            TowerBlueprintOverlay(activeCard, activePosition, dragTargetValid, Modifier.zIndex(2f))
        } else if (blueprintReturn != null) {
            TowerBlueprintOverlay(
                blueprintReturn!!.card,
                Offset(returnX.value, returnY.value),
                isValid = false,
                modifier = Modifier.zIndex(2f)
            )
        }
        val selectedOwnedTower = selectedSlotId?.takeIf { slotId ->
            towerPlacements.any { it.slotId == slotId && it.ownerPlayerNumber == playerNumber }
        }
        if (selectedOwnedTower != null && isMatchActive) {
            Box(
                Modifier.align(Alignment.Center).zIndex(3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xF01B2633))
                    .border(1.dp, Color(0xFFFFD34F), RoundedCornerShape(12.dp))
                    .clickable { showSellConfirmation = selectedOwnedTower }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("Vender torre", color = Color(0xFFFFE27A), fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
        if (showSurrenderConfirmation) {
            ArenaConfirmPanel(
                title = "¿Rendirse?",
                message = "Tu rival ganará la partida.",
                confirmText = "Rendirse",
                onCancel = { showSurrenderConfirmation = false },
                onConfirm = { showSurrenderConfirmation = false; onSurrender() }
            )
        }
        showSellConfirmation?.let { slotId ->
            val tower = towerPlacements.firstOrNull { it.slotId == slotId }
            val refund = tower?.let { placement ->
                val card = if (placement.kind == ArenaTowerKind.ARROW) MatchTowerCard.ARROW_TOWER else MatchTowerCard.CANNON_TOWER
                (towerCost(card) * 0.5f).toInt()
            } ?: 0
            ArenaConfirmPanel(
                title = "Vender torre",
                message = "Recibirás $refund monedas y liberarás el hueco.",
                confirmText = "Vender",
                onCancel = { showSellConfirmation = null },
                onConfirm = {
                    showSellConfirmation = null
                    localFeedback = if (onSellTower(slotId)) "Venta enviada al servidor…" else "No se pudo enviar la venta."
                }
            )
        }
        displayedResult?.let { result ->
            ArenaResultPanel(result, playerNumber, opponentNickname, onReturnHome)
        }
    }
}

private data class BlueprintReturn(val card: MatchTowerCard, val from: Offset, val to: Offset)

@Composable
private fun ArenaOpponentStatus(
    nickname: String,
    playerNumber: Int,
    castleHealth: Float?,
    wallHealth: Float?,
    phase: String?,
    remainingMs: Long,
    onExitDebug: (() -> Unit)?,
    onSurrender: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val playerColor = if (playerNumber == 1) Color(0xFF367DD0) else Color(0xFFC74336)
    Row(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xE91C2734))
            .border(1.dp, Color(0xFF8D9AA5), RoundedCornerShape(15.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(playerColor),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.icono_castillo),
                contentDescription = null,
                modifier = Modifier.size(33.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = nickname.ifBlank { "Jugador" },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ArenaLifeMeter("Castillo", castleHealth, 400f, playerColor, Modifier.weight(1f))
                ArenaLifeMeter(
                    "Muro", wallHealth, 500f,
                    if ((wallHealth ?: 500f) > 0f) Color(0xFF65BF79) else Color(0xFFE05C54),
                    Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Image(
            painter = painterResource(R.drawable.icono_tiempo),
            contentDescription = null,
            modifier = Modifier.size(25.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (phase == "SUDDEN_DEATH") "M.S." else {
                val seconds = (remainingMs + 999L) / 1_000L
                "%d:%02d".format(seconds / 60L, seconds % 60L)
            },
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        if (onExitDebug != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF43505B))
                    .clickable(role = Role.Button, onClick = onExitDebug)
                    .semantics { contentDescription = "Salir del modo de prueba" },
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        if (onSurrender != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7E3B32))
                    .clickable(role = Role.Button, onClick = onSurrender)
                    .semantics { contentDescription = "Rendirse" },
                contentAlignment = Alignment.Center
            ) {
                Text("⚑", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ArenaLifeMeter(
    label: String,
    health: Float?,
    maximum: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val current = (health ?: maximum).coerceIn(0f, maximum)
    Column(
        modifier.padding(bottom = 4.dp).semantics {
            contentDescription = "$label ${current.toInt()} de ${maximum.toInt()} de vida"
        }
    ) {
        Text(
            "$label ${current.toInt()}/${maximum.toInt()}",
            color = Color(0xFFE6EAF0), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Clip
        )
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color(0xFF151B22))) {
            Box(
                Modifier.fillMaxWidth((current / maximum).coerceIn(0f, 1f))
                    .fillMaxHeight().clip(CircleShape).background(color)
            )
        }
    }
}

@Composable
private fun ArenaConfirmPanel(
    title: String,
    message: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).zIndex(5f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(0.84f).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF283647))
                .border(2.dp, Color(0xFFC79A57), RoundedCornerShape(16.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = Color(0xFFFFDF8A), fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(message, color = Color.White, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArenaDialogButton("Volver", Color(0xFF76483A), onCancel)
                ArenaDialogButton(confirmText, Color(0xFF9E322B), onConfirm)
            }
        }
    }
}

@Composable
private fun ArenaResultPanel(
    result: MatchGameResult,
    playerNumber: Int,
    opponentNickname: String,
    onReturnHome: () -> Unit
) {
    val title = when (result.winnerPlayerNumber) {
        null -> "¡Empate!"
        playerNumber -> "¡Victoria!"
        else -> "Derrota"
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xD9000000)).zIndex(6f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(0.86f).clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF273546))
                .border(3.dp, Color(0xFFC79A57), RoundedCornerShape(18.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, color = if (result.winnerPlayerNumber == playerNumber) Color(0xFFFFD65C) else Color.White,
                fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(
                "Tu castillo: ${healthForPlayer(result, playerNumber).toInt()} · " +
                    "Castillo de ${opponentNickname.ifBlank { "Jugador" }}: ${healthForPlayer(result, if (playerNumber == 1) 2 else 1).toInt()}",
                color = Color.White, fontSize = 14.sp
            )
            when {
                result.profileRewardSettled -> {
                    val reward = result.profileRewardCoins ?: 0
                    Text(
                        if (reward > 0) "+$reward monedas añadidas a tu perfil" else "No has ganado monedas esta vez",
                        color = Color(0xFFFFD66E), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                else -> Text(
                    "Guardando la recompensa en tu perfil…",
                    color = Color(0xFFFFD66E), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            ArenaDialogButton("Volver a Inicio", Color(0xFFB87925), onReturnHome)
        }
    }
}

private fun healthForPlayer(result: MatchGameResult, player: Int): Float =
    if (player == 1) result.player1CastleHealth else result.player2CastleHealth

@Composable
private fun ArenaDialogButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(9.dp)).background(color)
            .border(1.dp, Color(0xFFFFD786), RoundedCornerShape(9.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

private data class ArenaHandCardModel(
    val imageResource: Int,
    val description: String,
    val towerCard: MatchTowerCard? = null,
    val troopCard: MatchTroopCard? = null
)

@Composable
private fun ArenaCardHand(
    modifier: Modifier = Modifier,
    coins: Int,
    incomePerSecond: Int,
    ownWallHealth: Float?,
    ownCastleHealth: Float?,
    phase: String?,
    towerCosts: Map<String, Int>,
    troopCosts: Map<String, Int>,
    selectedTowerCard: MatchTowerCard?,
    selectedTroopCard: MatchTroopCard?,
    draggedTowerCard: MatchTowerCard?,
    message: String?,
    onTowerCardTap: (MatchTowerCard) -> Unit,
    onTroopCardTap: (MatchTroopCard) -> Unit,
    onDragStart: (MatchTowerCard, LayoutCoordinates, Offset) -> Unit,
    onDragMove: (MatchTowerCard, LayoutCoordinates, Offset) -> Unit,
    onDragEnd: (MatchTowerCard, LayoutCoordinates) -> Unit,
    onDragCancel: (MatchTowerCard, LayoutCoordinates) -> Unit
) {
    val cards = listOf(
        ArenaHandCardModel(R.drawable.carta_espadachines, "Carta de Espadachín", troopCard = MatchTroopCard.ESPADACHIN),
        ArenaHandCardModel(R.drawable.carta_escuderos, "Carta de Escudero", troopCard = MatchTroopCard.ESCUDERO),
        ArenaHandCardModel(R.drawable.carta_torre_flechas, "Carta de Torre de flechas", MatchTowerCard.ARROW_TOWER),
        ArenaHandCardModel(R.drawable.carta_torre_canon, "Carta de Torre de cañón", MatchTowerCard.CANNON_TOWER)
    )
    Column(
        modifier
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Color(0xFF1B2633))
            .border(
                width = 1.dp,
                color = Color(0xFF556574),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            )
            .padding(top = 5.dp, bottom = 7.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(27.dp)
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.moneda),
                contentDescription = "Monedas",
                modifier = Modifier.size(22.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(7.dp))
            Text(coins.toString(), color = Color(0xFFFFE27A), fontWeight = FontWeight.Black, fontSize = 16.sp)
            Spacer(Modifier.width(9.dp))
            Text("+$incomePerSecond/s", color = Color(0xFFD4DBE2), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArenaLifeMeter(
                "Tu muro", ownWallHealth, 500f,
                if ((ownWallHealth ?: 500f) > 0f) Color(0xFF65BF79) else Color(0xFFE05C54),
                Modifier.weight(1f)
            )
            ArenaLifeMeter("Tu castillo", ownCastleHealth, 400f, Color(0xFF4E9BFF), Modifier.weight(1f))
        }
        if (phase == "SUDDEN_DEATH") {
            Text(
                "MUERTE SÚBITA",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
                color = Color(0xFFFF927B), fontWeight = FontWeight.Black, fontSize = 10.sp,
                maxLines = 1
            )
        }
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                color = if (message.startsWith("No se pudo") || message.contains("suficientes") || message.startsWith("La partida")) Color(0xFFFFB5A9) else Color(0xFFEBD18C),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            cards.forEach { card ->
                ArenaHandCard(
                    model = card,
                    modifier = Modifier.weight(1f).aspectRatio(0.72f),
                    price = card.towerCard?.let { towerCosts[it.wireId] }
                        ?: card.troopCard?.let { troopCosts[it.wireId] },
                    selected = (card.towerCard != null && selectedTowerCard == card.towerCard) ||
                        (card.troopCard != null && selectedTroopCard == card.troopCard),
                    isDragging = card.towerCard != null && draggedTowerCard == card.towerCard,
                    onTap = {
                        card.towerCard?.let(onTowerCardTap)
                        card.troopCard?.let(onTroopCardTap)
                    },
                    onDragStart = { tower, coordinates, point -> onDragStart(tower, coordinates, point) },
                    onDragMove = onDragMove,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArenaHandCard(
    model: ArenaHandCardModel,
    modifier: Modifier,
    price: Int?,
    selected: Boolean,
    isDragging: Boolean,
    onTap: () -> Unit,
    onDragStart: (MatchTowerCard, LayoutCoordinates, Offset) -> Unit,
    onDragMove: (MatchTowerCard, LayoutCoordinates, Offset) -> Unit,
    onDragEnd: (MatchTowerCard, LayoutCoordinates) -> Unit,
    onDragCancel: (MatchTowerCard, LayoutCoordinates) -> Unit
) {
    var coordinates by remember(model.description) { mutableStateOf<LayoutCoordinates?>(null) }
    val currentCoordinates by rememberUpdatedState(coordinates)
    val towerCard = model.towerCard
    var cardModifier = modifier.semantics {
            val cardName = towerCard?.displayName ?: model.troopCard?.displayName ?: model.description
            val fallbackCost = towerCard?.cost ?: model.troopCard?.cost ?: 0
            contentDescription = if (model.troopCard != null) {
                "$cardName, cuesta ${price ?: fallbackCost} monedas. Toca para seleccionar y después toca tu mitad del camino."
            } else {
                "$cardName, cuesta ${price ?: fallbackCost} monedas. Toca para seleccionar un hueco propio o mantenla pulsada y arrástrala."
            }
        }
    cardModifier = cardModifier.combinedClickable(role = Role.Button, onClick = onTap)
    if (towerCard != null) {
        cardModifier = cardModifier
            .onGloballyPositioned { coordinates = it }
            .pointerInput(towerCard) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point -> currentCoordinates?.let { onDragStart(towerCard, it, point) } },
                    onDrag = { change, _ ->
                        change.consume()
                        currentCoordinates?.let { onDragMove(towerCard, it, change.position) }
                    },
                    onDragEnd = { currentCoordinates?.let { onDragEnd(towerCard, it) } },
                    onDragCancel = { currentCoordinates?.let { onDragCancel(towerCard, it) } }
                )
            }
    }
    Box(cardModifier, contentAlignment = Alignment.Center) {
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .border(2.dp, Color(0xFFFFD34F), RoundedCornerShape(13.dp))
            )
        }
        Image(
            painter = painterResource(R.drawable.marco_carta),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Image(
            painter = painterResource(model.imageResource),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 9.dp)
                .then(if (isDragging) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier),
            contentScale = ContentScale.Fit
        )
        if (price != null) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 5.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xE8272116))
                    .border(1.dp, Color(0xFFFFD34F), RoundedCornerShape(7.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.moneda), contentDescription = null, modifier = Modifier.size(12.dp))
                Text(price.toString(), color = Color(0xFFFFE27A), fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun TowerBlueprintOverlay(card: MatchTowerCard, center: Offset, isValid: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val width = 54.dp
    val height = 74.dp
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    val cardImage = when (card) {
        MatchTowerCard.ARROW_TOWER -> R.drawable.carta_torre_flechas
        MatchTowerCard.CANNON_TOWER -> R.drawable.carta_torre_canon
    }
    val accent = if (isValid) Color(0xFF57D66B) else Color(0xFFE56A5F)
    Box(
        modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(width, height)
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, accent, RoundedCornerShape(10.dp))
            .background(Color(0xD91A222B))
    ) {
        Image(painterResource(R.drawable.marco_carta), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Image(
            painterResource(cardImage),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(5.dp),
            contentScale = ContentScale.Fit
        )
        Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.16f)))
    }
}

private fun arenaSlotTargets(
    map: MatchMap,
    playerNumber: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    touchRadius: Float
): List<ArenaSlotTarget> {
    val boardWidth = minOf(canvasWidth, canvasHeight * 9f / 16f)
    val boardLeft = (canvasWidth - boardWidth) / 2f
    return buildList {
        listOf(1 to map.player1Slots, 2 to map.player2Slots).forEach { (owner, points) ->
            points.forEachIndexed { index, point ->
                val metadata = map.slotMetadata.firstOrNull { it.ownerPlayerNumber == owner && it.slotIndex == index }
                val id = metadata?.id ?: "${if (owner == 1) "player1" else "player2"}-$index"
                val x = if (playerNumber == 2) 1f - point.x else point.x
                val y = if (playerNumber == 2) 1f - point.y else point.y
                add(ArenaSlotTarget(id, owner, boardLeft + x * boardWidth, y * canvasHeight, touchRadius))
            }
        }
    }
}

private fun pointAtMapDistance(map: MatchMap, station: Float): MatchPoint? {
    val points = map.path
    val cumulative = map.pathCumulativeLengths
    if (points.size < 2 || cumulative.size != points.size || map.pathTotalLength <= 0f) return null
    val distance = station.coerceIn(0f, map.pathTotalLength)
    val right = cumulative.indexOfFirst { it >= distance }.let { if (it < 1) 1 else it.coerceAtMost(points.lastIndex) }
    val left = right - 1
    val span = (cumulative[right] - cumulative[left]).coerceAtLeast(0.0001f)
    val fraction = ((distance - cumulative[left]) / span).coerceIn(0f, 1f)
    return MatchPoint(
        points[left].x + (points[right].x - points[left].x) * fraction,
        points[left].y + (points[right].y - points[left].y) * fraction
    )
}

private fun pathSectionPoints(map: MatchMap, start: Float, end: Float): List<MatchPoint> {
    if (end <= start || map.pathCumulativeLengths.size != map.path.size) return emptyList()
    return buildList {
        pointAtMapDistance(map, start)?.let { add(it) }
        map.path.forEachIndexed { index, point ->
            val station = map.pathCumulativeLengths[index]
            if (station > start && station < end) add(point)
        }
        pointAtMapDistance(map, end)?.let { add(it) }
    }
}

/** Returns the server's path station only when the touch lies on this player's valid half. */
internal fun pathStationForTouch(
    map: MatchMap,
    tap: Offset,
    playerNumber: Int,
    boardLeft: Float,
    boardWidth: Float,
    boardHeight: Float
): Float? {
    val points = map.path
    val cumulative = map.pathCumulativeLengths
    val total = map.pathTotalLength
    if (points.size < 2 || cumulative.size != points.size || total <= 0f || boardWidth <= 0f || boardHeight <= 0f) return null
    val validStart = if (playerNumber == 1) 0f else total * 0.525f
    val validEnd = if (playerNumber == 1) total * 0.475f else total
    var closestDistanceSquared = Float.POSITIVE_INFINITY
    var closestStation = 0f
    for (index in 0 until points.lastIndex) {
        val segmentStart = cumulative[index]
        val segmentEnd = cumulative[index + 1]
        if (segmentEnd <= segmentStart) continue
        fun screenPoint(fraction: Float): Offset {
            val rawX = points[index].x + (points[index + 1].x - points[index].x) * fraction
            val rawY = points[index].y + (points[index + 1].y - points[index].y) * fraction
            val orientedX = if (playerNumber == 2) 1f - rawX else rawX
            val orientedY = if (playerNumber == 2) 1f - rawY else rawY
            return Offset(boardLeft + orientedX * boardWidth, orientedY * boardHeight)
        }
        val start = screenPoint(0f)
        val end = screenPoint(1f)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        val progress = if (lengthSquared <= 0.0001f) 0f else
            (((tap.x - start.x) * dx + (tap.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        val projectedX = start.x + dx * progress
        val projectedY = start.y + dy * progress
        val distanceSquared = (tap.x - projectedX) * (tap.x - projectedX) + (tap.y - projectedY) * (tap.y - projectedY)
        if (distanceSquared < closestDistanceSquared) {
            closestDistanceSquared = distanceSquared
            closestStation = segmentStart + (segmentEnd - segmentStart) * progress
        }
    }
    val touchRadius = maxOf(boardWidth * map.pathWidthFraction * 0.72f, 24f)
    return closestStation.takeIf {
        closestDistanceSquared <= touchRadius * touchRadius && it >= validStart && it <= validEnd
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTroopSprite(
    sprite: ImageBitmap,
    troop: MatchGameTroop,
    center: Offset,
    size: Float
) {
    val frameWidth = (sprite.width / 4).coerceAtLeast(1)
    val frameHeight = (sprite.height / 2).coerceAtLeast(1)
    val frame = 0
    val column = frame % 4
    val row = 0
    val width = size.coerceAtLeast(1f).roundToInt()
    val height = size.coerceAtLeast(1f).roundToInt()
    val left = (center.x - width / 2f).roundToInt()
    val top = (center.y - height / 2f).roundToInt()
    drawImage(
        image = sprite,
        srcOffset = IntOffset(column * frameWidth, row * frameHeight),
        srcSize = IntSize(frameWidth, frameHeight),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(width, height)
    )
    val healthRatio = (troop.health / troop.maxHealth).coerceIn(0f, 1f)
    val barWidth = size * 0.72f
    val barHeight = 3.dp.toPx()
    val barTop = top.toFloat() - barHeight - 2.dp.toPx()
    drawRoundRect(Color(0xFF17211B), Offset(center.x - barWidth / 2f, barTop), Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight))
    drawRoundRect(Color(0xFF63DF6D), Offset(center.x - barWidth / 2f, barTop), Size(barWidth * healthRatio, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight))
}

@Composable
private fun ArenaMapCanvas(
    map: MatchMap,
    playerNumber: Int,
    gameState: MatchGameSnapshot?,
    previousGameState: MatchGameSnapshot?,
    previousSnapshotAtMs: Long,
    latestSnapshotAtMs: Long,
    animationFrame: State<Long>,
    destroyedWallOwners: Set<Int>,
    towerPlacements: List<ArenaTowerPlacement>,
    pendingSlotIds: Set<String>,
    selectedSlotId: String?,
    selectedTowerCard: MatchTowerCard?,
    draggedTowerCard: MatchTowerCard?,
    selectedTroopCard: MatchTroopCard?,
    debugTroopPreviews: List<ArenaDebugTroopPreview>,
    dragTargetSlotId: String?,
    dragTargetValid: Boolean,
    canBuild: Boolean,
    coins: Int,
    arrowTowerRangeFraction: Float,
    cannonTowerRangeFraction: Float,
    onTapOwnSlot: (String) -> Unit,
    onTapPath: (Float) -> Unit,
    onInvalidPathTouch: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val swordsmanSprite = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.espadachin_animaciones).asImageBitmap()
    }
    val escuderoSprite = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.escudero_animaciones).asImageBitmap()
    }
    val grass = painterResource(R.drawable.cesped)
    val treeOne = painterResource(R.drawable.arbol_01)
    val treeTwo = painterResource(R.drawable.arbol_02)
    val bushOne = painterResource(R.drawable.arbusto_01)
    val bushTwo = painterResource(R.drawable.arbusto_02)
    val rockOne = painterResource(R.drawable.rocas_01)
    val rockTwo = painterResource(R.drawable.rocas_02)
    val pad = painterResource(R.drawable.plataforma_torre)
    val wallSegment = painterResource(R.drawable.muro_tramo_limpio)
    val intactGate = painterResource(R.drawable.muro_puerta_intacto_v2)
    val destroyedGate = painterResource(R.drawable.muro_puerta_destruido_v2)
    val blueCastle = painterResource(R.drawable.castillo_azul)
    val redCastle = painterResource(R.drawable.castillo_rojo)
    val blueArrow = painterResource(R.drawable.torre_flechas_azul)
    val redArrow = painterResource(R.drawable.torre_flechas_roja)
    val blueCannon = painterResource(R.drawable.torre_canon_azul)
    val redCannon = painterResource(R.drawable.torre_canon_roja)
    val slotVisuals = buildList {
        map.player1Slots.forEachIndexed { index, point ->
            val metadata = map.slotMetadata.firstOrNull { it.ownerPlayerNumber == 1 && it.slotIndex == index }
            add(ArenaSlotVisual(1, index, point, metadata?.id ?: "player1-$index"))
        }
        map.player2Slots.forEachIndexed { index, point ->
            val metadata = map.slotMetadata.firstOrNull { it.ownerPlayerNumber == 2 && it.slotIndex == index }
            add(ArenaSlotVisual(2, index, point, metadata?.id ?: "player2-$index"))
        }
    }

    val currentOnTapOwnSlot by rememberUpdatedState(onTapOwnSlot)
    val currentOnTapPath by rememberUpdatedState(onTapPath)
    val currentOnInvalidPathTouch by rememberUpdatedState(onInvalidPathTouch)

    Box(modifier) {
    Canvas(
        Modifier.fillMaxSize()
            .onGloballyPositioned(onPositioned)
            .pointerInput(map.seed, playerNumber, slotVisuals) {
            detectTapGestures { tap ->
                val boardWidth = minOf(size.width.toFloat(), size.height * 9f / 16f)
                val hitRadius = maxOf(boardWidth * map.slotDiameterFraction * 0.72f, 24.dp.toPx())
                val hitTargets = arenaSlotTargets(map, playerNumber, size.width.toFloat(), size.height.toFloat(), hitRadius)
                val selected = pickOwnSlot(MatchPoint(tap.x, tap.y), hitTargets, playerNumber)
                if (selected != null) currentOnTapOwnSlot(selected.id)
                else {
                    val boardWidth = minOf(size.width.toFloat(), size.height * 9f / 16f)
                    val boardLeft = (size.width - boardWidth) / 2f
                    val station = pathStationForTouch(
                        map = map,
                        tap = tap,
                        playerNumber = playerNumber,
                        boardLeft = boardLeft,
                        boardWidth = boardWidth,
                        boardHeight = size.height.toFloat()
                    )
                    if (station != null) currentOnTapPath(station) else currentOnInvalidPathTouch()
                }
            }
        }
    ) {
        val canvasSize = size
        with(grass) { draw(size = canvasSize) }

        val boardWidth = minOf(size.width, size.height * 9f / 16f)
        val boardLeft = (size.width - boardWidth) / 2f
        val boardHeight = size.height

        fun oriented(point: MatchPoint): Offset {
            val x = if (playerNumber == 2) 1f - point.x else point.x
            val y = if (playerNumber == 2) 1f - point.y else point.y
            return Offset(boardLeft + x * boardWidth, y * boardHeight)
        }

        fun screenOriented(point: MatchPoint): Offset {
            val x = if (playerNumber == 2) 1f - point.x else point.x
            val y = if (playerNumber == 2) 1f - point.y else point.y
            return Offset(boardLeft + x * boardWidth, y * boardHeight)
        }

        // Decorative art is behind the road, pads, gates, and castles.
        map.decorations.sortedBy { it.point.y }.forEach { decoration ->
            val center = screenOriented(decoration.point)
            if (decoration.kind == "flower") {
                val variant = ((decoration.point.x * 997f + decoration.point.y * 619f).toInt() and 1) == 0
                drawArenaFlower(center, boardWidth * 0.018f * decoration.scale, variant)
                return@forEach
            }
            val painter = decorationPainter(
                decoration = decoration,
                treeOne = treeOne,
                treeTwo = treeTwo,
                bushOne = bushOne,
                bushTwo = bushTwo,
                rockOne = rockOne,
                rockTwo = rockTwo
            )
            val baseWidth = when (decoration.kind) {
                "tree" -> boardWidth * 0.14f
                "bush" -> boardWidth * 0.10f
                else -> boardWidth * 0.08f
            }
            val width = baseWidth * decoration.scale.coerceIn(0.5f, 1.5f)
            val aspect = painterAspect(painter)
            drawCenteredPainter(painter, center, width, width / aspect)
        }

        val road = Path().apply {
            map.path.forEachIndexed { index, point ->
                val position = oriented(point)
                if (index == 0) moveTo(position.x, position.y) else lineTo(position.x, position.y)
            }
        }
        val roadWidth = boardWidth * map.pathWidthFraction.coerceIn(0.04f, 0.15f)
        drawPath(
            path = road,
            color = Color(0x553A2B20),
            style = Stroke(
                width = roadWidth * 1.30f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawPath(
            path = road,
            color = Color(0xFF60452B),
            style = Stroke(
                width = roadWidth * 1.14f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawPath(
            path = road,
            color = Color(0xFFC38C4D),
            style = Stroke(
                width = roadWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawPath(
            path = road,
            color = Color(0xFFD4A361),
            style = Stroke(
                width = roadWidth * 0.77f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        if (selectedTroopCard != null && map.pathTotalLength > 0f) {
            val ownStart = if (playerNumber == 1) 0f else map.pathTotalLength * 0.525f
            val ownEnd = if (playerNumber == 1) map.pathTotalLength * 0.475f else map.pathTotalLength
            val validPath = Path().apply {
                pathSectionPoints(map, ownStart, ownEnd).forEachIndexed { index, point ->
                    val position = oriented(point)
                    if (index == 0) moveTo(position.x, position.y) else lineTo(position.x, position.y)
                }
            }
            drawPath(validPath, Color(0xB839D06A), style = Stroke(
                width = roadWidth * 1.18f, cap = StrokeCap.Round, join = StrokeJoin.Round
            ))
            val forbiddenPath = Path().apply {
                pathSectionPoints(map, map.pathTotalLength * 0.475f, map.pathTotalLength * 0.525f)
                    .forEachIndexed { index, point ->
                        val position = oriented(point)
                        if (index == 0) moveTo(position.x, position.y) else lineTo(position.x, position.y)
                    }
            }
            drawPath(forbiddenPath, Color(0xCCDC5651), style = Stroke(
                width = roadWidth * 1.12f, cap = StrokeCap.Round, join = StrokeJoin.Round
            ))
        }

        slotVisuals.forEach { slot ->
            val center = oriented(slot.point)
            val ownerColor = if (slot.ownerPlayerNumber == 1) Color(0xFF59A3FF) else Color(0xFFF47766)
            val padWidth = boardWidth * map.slotDiameterFraction
            val towerInSlot = towerPlacements.any { it.slotId == slot.id && it.ownerPlayerNumber == playerNumber }
            val isTarget = slot.id == dragTargetSlotId
            val activeTowerCard = draggedTowerCard ?: selectedTowerCard
            val slotIsOccupiedOrPending = towerPlacements.any { it.slotId == slot.id } || slot.id in pendingSlotIds
            val selectedTower = towerPlacements.firstOrNull { it.slotId == slot.id && it.ownerPlayerNumber == playerNumber }
            val selectedTowerCardKind = selectedTower?.let {
                if (it.kind == ArenaTowerKind.ARROW) MatchTowerCard.ARROW_TOWER else MatchTowerCard.CANNON_TOWER
            }
            val rangeCard = if (isTarget) activeTowerCard ?: selectedTowerCardKind else selectedTowerCardKind ?: activeTowerCard
            val rangeFraction = when (rangeCard) {
                MatchTowerCard.ARROW_TOWER -> arrowTowerRangeFraction
                MatchTowerCard.CANNON_TOWER -> cannonTowerRangeFraction
                null -> map.towerRangeFraction
            }
            if (slot.ownerPlayerNumber == playerNumber && slot.id == selectedSlotId && towerInSlot && rangeFraction > 0f) {
                val rangeRadius = boardWidth * rangeFraction
                drawCircle(ownerColor.copy(alpha = 0.10f), rangeRadius, center)
                drawCircle(ownerColor.copy(alpha = 0.88f), rangeRadius, center, style = Stroke(width = 2.dp.toPx()))
            }
            if (slot.ownerPlayerNumber == playerNumber && isTarget && activeTowerCard != null && rangeFraction > 0f) {
                val rangeRadius = boardWidth * rangeFraction
                val rangeColor = if (dragTargetValid) Color(0xFF62D877) else Color(0xFFE56A5F)
                drawCircle(rangeColor.copy(alpha = 0.08f), rangeRadius, center)
                drawCircle(rangeColor.copy(alpha = 0.72f), rangeRadius, center, style = Stroke(width = 2.dp.toPx()))
            }
            if (slot.ownerPlayerNumber == playerNumber && activeTowerCard != null) {
                val isFree = !slotIsOccupiedOrPending
                val affordable = coins >= activeTowerCard.cost
                val normalColor = when {
                    isTarget -> if (dragTargetValid) Color(0xFF50D76B) else Color(0xFFE56A5F)
                    isFree && canBuild && affordable -> Color(0xFF50D76B)
                    isFree -> Color(0xFF9AA3AA)
                    else -> Color(0xFF78828A)
                }
                if (isFree || isTarget) {
                    drawCircle(normalColor.copy(alpha = 0.19f), padWidth * 0.78f, center)
                    drawCircle(normalColor, padWidth * 0.76f, center, style = Stroke(width = 3.dp.toPx()))
                }
            } else if (slot.ownerPlayerNumber == playerNumber && slot.id in pendingSlotIds) {
                drawCircle(Color(0xFFFFC54E), padWidth * 0.75f, center, style = Stroke(width = 3.dp.toPx()))
            }
            drawCenteredPainter(pad, center, padWidth, padWidth / painterAspect(pad))
            drawCircle(ownerColor, padWidth * 0.53f, center, style = Stroke(width = 2.5.dp.toPx()))
        }

        // Castles sit behind their wall so the masonry reads as one continuous barrier.
        drawCenteredPainter(blueCastle, oriented(map.player1Castle), boardWidth * 0.22f, boardWidth * 0.22f)
        drawCenteredPainter(redCastle, oriented(map.player2Castle), boardWidth * 0.22f, boardWidth * 0.22f)

        drawContinuousWall(
            centerY = oriented(map.player1WallGate).y,
            canvasWidth = size.width,
            wallHeight = boardWidth * 0.115f,
            gateWidth = boardWidth * 0.19f,
            roadOpeningWidth = boardWidth * 0.15f,
            segment = wallSegment,
            gate = if (1 in destroyedWallOwners) destroyedGate else intactGate,
            destroyed = 1 in destroyedWallOwners
        )
        drawContinuousWall(
            centerY = oriented(map.player2WallGate).y,
            canvasWidth = size.width,
            wallHeight = boardWidth * 0.115f,
            gateWidth = boardWidth * 0.19f,
            roadOpeningWidth = boardWidth * 0.15f,
            segment = wallSegment,
            gate = if (2 in destroyedWallOwners) destroyedGate else intactGate,
            destroyed = 2 in destroyedWallOwners
        )

        towerPlacements.forEach { tower ->
            val slots = if (tower.ownerPlayerNumber == 1) map.player1Slots else map.player2Slots
            val slot = slots.getOrNull(tower.slotIndex) ?: return@forEach
            val painter = when (tower.kind) {
                ArenaTowerKind.ARROW -> if (tower.ownerPlayerNumber == 1) blueArrow else redArrow
                ArenaTowerKind.CANNON -> if (tower.ownerPlayerNumber == 1) blueCannon else redCannon
            }
            drawCenteredPainter(
                painter,
                oriented(slot),
                boardWidth * ARENA_TOWER_WIDTH_FRACTION,
                boardWidth * ARENA_TOWER_WIDTH_FRACTION / painterAspect(painter)
            )
        }

    }

    Canvas(Modifier.fillMaxSize().zIndex(1f).testTag("arenaEntities")) {
        animationFrame.value
        val boardWidth = minOf(size.width, size.height * 9f / 16f)
        val boardLeft = (size.width - boardWidth) / 2f
        fun oriented(point: MatchPoint): Offset {
            val x = if (playerNumber == 2) 1f - point.x else point.x
            val y = if (playerNumber == 2) 1f - point.y else point.y
            return Offset(boardLeft + x * boardWidth, y * size.height)
        }

        val elapsedSinceSnapshot = (SystemClock.elapsedRealtime() - latestSnapshotAtMs).coerceIn(0L, 1_000L).toFloat()
        val transitionMs = (latestSnapshotAtMs - previousSnapshotAtMs).toFloat().coerceIn(50f, 1_000f)
        val fraction = if (previousGameState == null || previousSnapshotAtMs == 0L) 1f
            // Continue the last short segment locally between server snapshots.
            // This removes visible stop-and-go without changing authoritative movement.
            else (elapsedSinceSnapshot / transitionMs).coerceIn(0f, 1.25f)

        gameState?.troops?.forEach { troop ->
            val prior = previousGameState?.troops?.firstOrNull { it.id == troop.id }
            // The server snapshot already includes normalized world coordinates for this exact
            // troop. Interpolate those coordinates instead of reconstructing its position from
            // pathDistance, which depends on the client having identical path-length units.
            val point = prior?.let {
                MatchPoint(
                    it.point.x + (troop.point.x - it.point.x) * fraction,
                    it.point.y + (troop.point.y - it.point.y) * fraction
                )
            } ?: troop.point
            val health = prior?.let { it.health + (troop.health - it.health) * fraction } ?: troop.health
            val presentedTroop = troop.copy(point = point, health = health)
            val center = oriented(point)
            val ownerColor = if (troop.ownerPlayerNumber == 1) Color(0xFF4E9BFF) else Color(0xFFFF6759)
            drawCircle(Color(0x8E101820), boardWidth * 0.056f, Offset(center.x, center.y + boardWidth * 0.032f))
            drawCircle(ownerColor, boardWidth * 0.048f, center)
            drawCircle(Color(0xFF1D2733), boardWidth * 0.048f, center, style = Stroke(width = 1.5.dp.toPx()))
            val sprite = if (troop.card == MatchTroopCard.ESCUDERO.wireId) escuderoSprite else swordsmanSprite
            drawTroopSprite(sprite, presentedTroop, center, boardWidth * 0.105f)
        }
        debugTroopPreviews.forEach { preview ->
            val point = pointAtMapDistance(map, preview.pathDistance) ?: return@forEach
            val center = oriented(point)
            val health = if (preview.card == MatchTroopCard.ESCUDERO.wireId) 110f else 60f
            val visualTroop = MatchGameTroop(
                id = preview.id,
                ownerPlayerNumber = preview.ownerPlayerNumber,
                card = preview.card,
                point = point,
                health = health,
                maxHealth = health,
                pathDistance = preview.pathDistance
            )
            val ownerColor = if (preview.ownerPlayerNumber == 1) Color(0xFF4E9BFF) else Color(0xFFFF6759)
            drawCircle(Color(0x8E101820), boardWidth * 0.056f, Offset(center.x, center.y + boardWidth * 0.032f))
            drawCircle(ownerColor, boardWidth * 0.048f, center)
            drawCircle(Color(0xFF1D2733), boardWidth * 0.048f, center, style = Stroke(width = 1.5.dp.toPx()))
            val sprite = if (preview.card == MatchTroopCard.ESCUDERO.wireId) escuderoSprite else swordsmanSprite
            drawTroopSprite(sprite, visualTroop, center, boardWidth * 0.105f)
        }
        gameState?.projectiles?.forEach { projectile ->
            val start = oriented(projectile.from)
            val end = oriented(projectile.to)
            val progress = (projectile.progress + elapsedSinceSnapshot / 400f).coerceIn(0f, 1f)
            val x = start.x + (end.x - start.x) * progress
            val y = start.y + (end.y - start.y) * progress
            val color = if (projectile.card == MatchTowerCard.ARROW_TOWER.wireId) Color(0xFFFFE58B) else Color(0xFFFF7C34)
            drawCircle(color, boardWidth * 0.012f, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.75f), boardWidth * 0.005f, Offset(x, y))
        }
    }
    }
}

private fun decorationPainter(
    decoration: MatchDecoration,
    treeOne: Painter,
    treeTwo: Painter,
    bushOne: Painter,
    bushTwo: Painter,
    rockOne: Painter,
    rockTwo: Painter
): Painter {
    val variant = ((decoration.point.x * 997f + decoration.point.y * 619f).toInt() and 1) == 0
    return when (decoration.kind) {
        "tree" -> if (variant) treeOne else treeTwo
        "bush" -> if (variant) bushOne else bushTwo
        else -> if (variant) rockOne else rockTwo
    }
}

private fun DrawScope.drawArenaFlower(center: Offset, size: Float, variant: Boolean) {
    val petalColor = if (variant) Color(0xFFFFD978) else Color(0xFFFF91A8)
    val petalOffset = size * 0.34f
    val petalRadius = size * 0.24f
    listOf(
        Offset(-petalOffset, 0f),
        Offset(petalOffset, 0f),
        Offset(0f, -petalOffset),
        Offset(0f, petalOffset)
    ).forEach { offset ->
        drawCircle(petalColor, petalRadius, Offset(center.x + offset.x, center.y + offset.y))
    }
    drawCircle(Color(0xFF70452A), size * 0.16f, center)
}

private fun painterAspect(painter: Painter): Float {
    val intrinsic = painter.intrinsicSize
    return if (intrinsic.width.isFinite() && intrinsic.height.isFinite() && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        1f
    }
}

private fun DrawScope.drawContinuousWall(
    centerY: Float,
    canvasWidth: Float,
    wallHeight: Float,
    gateWidth: Float,
    roadOpeningWidth: Float,
    segment: Painter,
    gate: Painter,
    destroyed: Boolean
) {
    val centerX = canvasWidth / 2f
    val openingLeft = centerX - roadOpeningWidth / 2f
    val openingRight = centerX + roadOpeningWidth / 2f

    // The transparent PNG is the only wall surface. Do not paint a rectangular
    // backdrop behind it: that was the gray halo visible around the masonry.
    val tileWidth = wallHeight * painterAspect(segment)
    val overlap = 2.dp.toPx()
    fun tileSpan(start: Float, end: Float) {
        var x = start
        while (x < end) {
            val width = minOf(tileWidth + overlap, end - x + overlap)
            val center = Offset(x + width / 2f, centerY)
            drawCenteredPainter(segment, center, width, wallHeight)
            x += tileWidth - overlap
        }
    }
    if (destroyed) {
        tileSpan(0f, openingLeft + overlap)
        tileSpan(openingRight - overlap, canvasWidth)
    } else {
        tileSpan(0f, canvasWidth)
    }

    val gateHeight = gateWidth / painterAspect(gate)
    drawCenteredPainter(gate, Offset(centerX, centerY), gateWidth, gateHeight)
}

private fun DrawScope.drawCenteredPainter(
    painter: Painter,
    center: Offset,
    width: Float,
    height: Float
) {
    val left = center.x - width / 2f
    val top = center.y - height / 2f
    withTransform({
        translate(left = left, top = top)
    }) {
        with(painter) {
            draw(size = Size(width, height))
        }
    }
}
