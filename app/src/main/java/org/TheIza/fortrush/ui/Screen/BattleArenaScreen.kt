package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.data.matchmaking.MatchDecoration
import org.TheIza.fortrush.data.matchmaking.MatchMap
import org.TheIza.fortrush.data.matchmaking.MatchPoint

enum class ArenaTowerKind { ARROW, CANNON }

data class ArenaTowerPlacement(
    val ownerPlayerNumber: Int,
    val slotIndex: Int,
    val kind: ArenaTowerKind
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
    isDebugBotMode: Boolean = false,
    onExitDebug: (() -> Unit)? = null
) {
    val opponentPlayerNumber = if (playerNumber == 1) 2 else 1
    var selectedSlotId by remember(map.seed, playerNumber) { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF17232D))
    ) {
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
            selectedSlotId = selectedSlotId,
            onSelectSlot = { selectedSlotId = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics {
                    contentDescription = "Arena aleatoria compartida, " +
                        "${map.player1Slots.size + map.player2Slots.size} huecos de torre. " +
                        "Toca un hueco para ver su alcance. Vista del jugador $playerNumber"
                }
        )

        ArenaCardHand(Modifier.fillMaxWidth())
    }
}

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

@Composable
private fun ArenaCardHand(modifier: Modifier = Modifier) {
    val cards = listOf(
        R.drawable.carta_espadachines to "Carta de Espadachín",
        R.drawable.carta_escuderos to "Carta de Escudero",
        R.drawable.carta_torre_flechas to "Carta de Torre de flechas",
        R.drawable.carta_torre_canon to "Carta de Torre de cañón"
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
            Box(
                Modifier
                    .width(86.dp)
                    .height(11.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF101720))
                    .border(1.dp, Color(0xFF647280), CircleShape)
            ) {
                // The balance and regeneration rate are still undecided.
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.35f)
                        .clip(CircleShape)
                        .background(Color(0xFFE9AE32))
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            cards.forEach { (art, description) ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(0.72f)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.marco_carta),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    Image(
                        painter = painterResource(art),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 7.dp, vertical = 9.dp),
                        contentScale = ContentScale.Fit
                    )
                }
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
    selectedSlotId: String?,
    onSelectSlot: (String?) -> Unit,
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

    Canvas(
        modifier.pointerInput(map.seed, playerNumber, slotVisuals) {
            detectTapGestures { tap ->
                val boardWidth = minOf(size.width.toFloat(), size.height * 9f / 16f)
                val boardLeft = (size.width - boardWidth) / 2f
                val nearest = slotVisuals.map { slot ->
                    val x = if (playerNumber == 2) 1f - slot.point.x else slot.point.x
                    val y = if (playerNumber == 2) 1f - slot.point.y else slot.point.y
                    val center = Offset(boardLeft + x * boardWidth, y * size.height)
                    slot to (center - tap).getDistance()
                }.minByOrNull { it.second }
                val hitRadius = maxOf(boardWidth * map.slotDiameterFraction * 0.72f, 24.dp.toPx())
                onSelectSlot(nearest?.takeIf { it.second <= hitRadius }?.first?.id)
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
            if (slot.id == selectedSlotId && map.towerRangeFraction > 0f) {
                val rangeRadius = boardWidth * map.towerRangeFraction
                drawCircle(ownerColor.copy(alpha = 0.10f), rangeRadius, center)
                drawCircle(ownerColor.copy(alpha = 0.88f), rangeRadius, center, style = Stroke(width = 2.dp.toPx()))
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
            drawCenteredPainter(painter, oriented(slot), boardWidth * 0.23f, boardWidth * 0.23f)
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
