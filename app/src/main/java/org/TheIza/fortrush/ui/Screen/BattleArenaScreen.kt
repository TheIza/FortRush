package org.TheIza.fortrush.ui.Screen

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.TheIza.fortrush.R
import org.TheIza.fortrush.data.matchmaking.MatchDecoration
import org.TheIza.fortrush.data.matchmaking.MatchEconomySnapshot
import org.TheIza.fortrush.data.matchmaking.MatchMap
import org.TheIza.fortrush.data.matchmaking.MatchPoint
import org.TheIza.fortrush.data.matchmaking.MatchTowerCard

enum class ArenaTowerKind { ARROW, CANNON }

private const val ARENA_TOWER_SIZE_FRACTION = 0.17f

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

/**
 * Shared map geometry is server generated; this screen only scales, rotates, and draws it.
 */
@Composable
fun BattleArenaScreen(
    map: MatchMap,
    playerNumber: Int,
    opponentNickname: String,
    destroyedWallOwners: Set<Int> = emptySet(),
    towerPlacements: List<ArenaTowerPlacement> = emptyList(),
    pendingSlotIds: Set<String> = emptySet(),
    economy: MatchEconomySnapshot? = null,
    buildError: String? = null,
    isMatchActive: Boolean = false,
    onBuildTower: (MatchTowerCard, String) -> Boolean = { _, _ -> false },
    onClearBuildError: () -> Unit = {},
    isDebugBotMode: Boolean = false,
    onExitDebug: (() -> Unit)? = null
) {
    val opponentPlayerNumber = if (playerNumber == 1) 2 else 1
    var selectedSlotId by remember(map.seed, playerNumber) { mutableStateOf<String?>(null) }
    var selectedTowerCard by remember(map.seed, playerNumber) { mutableStateOf<MatchTowerCard?>(null) }
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
    LaunchedEffect(economy) {
        while (economy != null) {
            delay(250)
            economyTick += 1L
        }
    }
    val currentCoins = remember(economy, economyTick) {
        economy?.coinsAfterElapsed(SystemClock.elapsedRealtime() - economyReceivedAt) ?: 0
    }
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
            hasEnoughCoins = currentCoins >= card.cost
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
            hasEnoughCoins = currentCoins >= card.cost
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
                currentCoins < card.cost -> "No tienes monedas suficientes para esa torre."
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
    }

    val rejectionText = buildError ?: localFeedback ?: selectedTowerCard?.let {
        "${it.displayName} seleccionada · toca un hueco propio libre"
    }

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
                onExitDebug = if (isDebugBotMode) onExitDebug else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )

            ArenaMapCanvas(
                map = map,
                playerNumber = playerNumber,
                destroyedWallOwners = destroyedWallOwners,
                towerPlacements = towerPlacements,
                pendingSlotIds = pendingSlotIds,
                selectedSlotId = selectedSlotId,
                selectedTowerCard = selectedTowerCard,
                draggedTowerCard = draggedTowerCard,
                dragTargetSlotId = dragTargetSlotId,
                dragTargetValid = dragTargetValid,
                canBuild = isMatchActive,
                coins = currentCoins,
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
                onPositioned = { arenaCoordinates = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics {
                        contentDescription = "Arena aleatoria compartida, " +
                            "${map.player1Slots.size + map.player2Slots.size} huecos de torre. " +
                            "Toca una carta de torre y luego uno de tus huecos libres, o mantenla pulsada y arrástrala. " +
                            "Vista del jugador $playerNumber"
                    }
            )

            ArenaCardHand(
                modifier = Modifier.fillMaxWidth(),
                coins = currentCoins,
                incomePerSecond = economy?.incomePerSecond ?: 5,
                selectedTowerCard = selectedTowerCard,
                draggedTowerCard = draggedTowerCard,
                message = rejectionText,
                onTowerCardTap = { card ->
                    selectedTowerCard = if (selectedTowerCard == card) null else card
                    selectedSlotId = null
                    localFeedback = null
                    onClearBuildError()
                },
                onDragStart = { card, coordinates, point ->
                    selectedTowerCard = null
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
    }
}

private data class BlueprintReturn(val card: MatchTowerCard, val from: Offset, val to: Offset)

@Composable
private fun ArenaOpponentStatus(
    nickname: String,
    playerNumber: Int,
    onExitDebug: (() -> Unit)?,
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
                .size(35.dp)
                .clip(CircleShape)
                .background(playerColor),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.icono_castillo),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = nickname.ifBlank { "Rival" },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF151B22))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(playerColor)
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
            text = "4:00",
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
    }
}

private data class ArenaHandCardModel(
    val imageResource: Int,
    val description: String,
    val towerCard: MatchTowerCard? = null
)

@Composable
private fun ArenaCardHand(
    modifier: Modifier = Modifier,
    coins: Int,
    incomePerSecond: Int,
    selectedTowerCard: MatchTowerCard?,
    draggedTowerCard: MatchTowerCard?,
    message: String?,
    onTowerCardTap: (MatchTowerCard) -> Unit,
    onDragStart: (MatchTowerCard, LayoutCoordinates, Offset) -> Unit,
    onDragMove: (MatchTowerCard, LayoutCoordinates, Offset) -> Unit,
    onDragEnd: (MatchTowerCard, LayoutCoordinates) -> Unit,
    onDragCancel: (MatchTowerCard, LayoutCoordinates) -> Unit
) {
    val cards = listOf(
        ArenaHandCardModel(R.drawable.carta_espadachines, "Carta de Espadachín"),
        ArenaHandCardModel(R.drawable.carta_escuderos, "Carta de Escudero"),
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
                    selected = card.towerCard != null && selectedTowerCard == card.towerCard,
                    isDragging = card.towerCard != null && draggedTowerCard == card.towerCard,
                    onTap = { card.towerCard?.let(onTowerCardTap) },
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
            contentDescription = if (towerCard == null) model.description else
                "${towerCard.displayName}, cuesta ${towerCard.cost} monedas. Toca para seleccionar o manten pulsado y arrastra."
        }
    if (towerCard != null) {
        cardModifier = cardModifier
            .onGloballyPositioned { coordinates = it }
            .combinedClickable(role = Role.Button, onClick = onTap)
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
        if (towerCard != null) {
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
                Text(towerCard.cost.toString(), color = Color(0xFFFFE27A), fontWeight = FontWeight.Black, fontSize = 10.sp)
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

@Composable
private fun ArenaMapCanvas(
    map: MatchMap,
    playerNumber: Int,
    destroyedWallOwners: Set<Int>,
    towerPlacements: List<ArenaTowerPlacement>,
    pendingSlotIds: Set<String>,
    selectedSlotId: String?,
    selectedTowerCard: MatchTowerCard?,
    draggedTowerCard: MatchTowerCard?,
    dragTargetSlotId: String?,
    dragTargetValid: Boolean,
    canBuild: Boolean,
    coins: Int,
    onTapOwnSlot: (String) -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
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

    Canvas(
        modifier
            .onGloballyPositioned(onPositioned)
            .pointerInput(map.seed, playerNumber, slotVisuals) {
            detectTapGestures { tap ->
                val boardWidth = minOf(size.width.toFloat(), size.height * 9f / 16f)
                val hitRadius = maxOf(boardWidth * map.slotDiameterFraction * 0.72f, 24.dp.toPx())
                val hitTargets = arenaSlotTargets(map, playerNumber, size.width.toFloat(), size.height.toFloat(), hitRadius)
                val selected = pickOwnSlot(MatchPoint(tap.x, tap.y), hitTargets, playerNumber)
                if (selected != null) currentOnTapOwnSlot(selected.id)
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

        slotVisuals.forEach { slot ->
            val center = oriented(slot.point)
            val ownerColor = if (slot.ownerPlayerNumber == 1) Color(0xFF59A3FF) else Color(0xFFF47766)
            val padWidth = boardWidth * map.slotDiameterFraction
            val towerInSlot = towerPlacements.any { it.slotId == slot.id && it.ownerPlayerNumber == playerNumber }
            val isTarget = slot.id == dragTargetSlotId
            val activeTowerCard = draggedTowerCard ?: selectedTowerCard
            val slotIsOccupiedOrPending = towerPlacements.any { it.slotId == slot.id } || slot.id in pendingSlotIds
            if (slot.ownerPlayerNumber == playerNumber && slot.id == selectedSlotId && towerInSlot && map.towerRangeFraction > 0f) {
                val rangeRadius = boardWidth * map.towerRangeFraction
                drawCircle(ownerColor.copy(alpha = 0.10f), rangeRadius, center)
                drawCircle(ownerColor.copy(alpha = 0.88f), rangeRadius, center, style = Stroke(width = 2.dp.toPx()))
            }
            if (slot.ownerPlayerNumber == playerNumber && isTarget && activeTowerCard != null && map.towerRangeFraction > 0f) {
                val rangeRadius = boardWidth * map.towerRangeFraction
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
            wallHeight = boardWidth * 0.09f,
            gateWidth = boardWidth * 0.19f,
            roadOpeningWidth = boardWidth * 0.15f,
            segment = wallSegment,
            gate = if (1 in destroyedWallOwners) destroyedGate else intactGate,
            destroyed = 1 in destroyedWallOwners
        )
        drawContinuousWall(
            centerY = oriented(map.player2WallGate).y,
            canvasWidth = size.width,
            wallHeight = boardWidth * 0.09f,
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
                boardWidth * ARENA_TOWER_SIZE_FRACTION,
                boardWidth * ARENA_TOWER_SIZE_FRACTION
            )
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

    // The same stone strip runs edge to edge; only the destroyed gate leaves a road opening.
    drawRect(
        color = Color(0xFF827363),
        topLeft = Offset(0f, centerY - wallHeight / 2f),
        size = Size(canvasWidth, wallHeight)
    )

    val tileWidth = wallHeight * 2f
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
