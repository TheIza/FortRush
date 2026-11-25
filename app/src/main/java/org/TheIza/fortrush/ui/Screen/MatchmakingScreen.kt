package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.components.FortRushScreenBackground
import org.TheIza.fortrush.data.matchmaking.MatchMap

enum class MatchmakingStage { SEARCHING, LOADING_MAP, COUNTDOWN, STARTED, ERROR }

@Composable
fun MatchmakingScreen(
    nickname: String,
    stage: MatchmakingStage,
    queuePosition: Int = 0,
    opponentNickname: String? = null,
    playerNumber: Int = 1,
    map: MatchMap? = null,
    countdown: Int = 0,
    errorMessage: String? = null,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        FortRushScreenBackground(R.drawable.fr_fondo_inicio)
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(430.dp).align(Alignment.Center), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.panel_dialogo), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (stage) {
                    MatchmakingStage.SEARCHING -> {
                        Text("BUSCANDO RIVAL", color = Color(0xFFFFE7A7), fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(nickname, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text(if (queuePosition > 0) "Posición en cola: $queuePosition" else "Conectando con el servidor…", color = Color(0xFFE9E2C8), fontSize = 15.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Text("Buscaremos al primer jugador disponible.", color = Color(0xFFE9E2C8), fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    MatchmakingStage.LOADING_MAP -> {
                        Text("¡PARTIDA ENCONTRADA!", color = Color(0xFFFFE7A7), fontWeight = FontWeight.Black, fontSize = 21.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(opponentNickname.orEmpty().ifBlank { "Jugador" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        if (map != null) SharedMapPreview(map, playerNumber, Modifier.fillMaxWidth().height(130.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Preparando el mismo mapa para ambos…", color = Color(0xFFE9E2C8), fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                    MatchmakingStage.COUNTDOWN -> {
                        Text("¡PARTIDA LISTA!", color = Color(0xFFFFE7A7), fontWeight = FontWeight.Black, fontSize = 21.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(countdown.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 58.sp)
                        Text("Controles bloqueados", color = Color(0xFFE9E2C8), fontSize = 14.sp)
                    }
                    MatchmakingStage.STARTED -> {
                        Text("¡EMPIEZA LA PARTIDA!", color = Color(0xFFFFE7A7), fontWeight = FontWeight.Black, fontSize = 21.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(opponentNickname.orEmpty().ifBlank { "Jugador" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("La arena de combate se implementará en el siguiente paso.", color = Color(0xFFE9E2C8), fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                    MatchmakingStage.ERROR -> {
                        Text("NO SE PUDO EMPAREJAR", color = Color(0xFFFFE7A7), fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(errorMessage.orEmpty(), color = Color(0xFFE9E2C8), fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(22.dp))
                if (stage == MatchmakingStage.SEARCHING) {
                    MatchmakingButton("CANCELAR", R.drawable.boton_secundario, onCancel)
                } else if (stage == MatchmakingStage.ERROR) {
                    MatchmakingButton("VOLVER A BUSCAR", R.drawable.boton_principal, onRetry)
                }
            }
        }
    }
}

@Composable
private fun SharedMapPreview(map: MatchMap, playerNumber: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Mapa aleatorio compartido, semilla ${map.seed}" }) {
        drawRect(Color(0xFF4D843B))
        fun point(x: Float, y: Float): Offset {
            val orientedX = if (playerNumber == 2) 1f - x else x
            val orientedY = if (playerNumber == 2) 1f - y else y
            return Offset(orientedX * size.width, orientedY * size.height)
        }
        val road = Path().apply {
            map.path.forEachIndexed { index, node ->
                val position = point(node.x, node.y)
                if (index == 0) moveTo(position.x, position.y) else lineTo(position.x, position.y)
            }
        }
        drawPath(road, Color(0xFF38251C), style = Stroke(width = size.minDimension * 0.19f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(road, Color(0xFFB9864C), style = Stroke(width = size.minDimension * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val slots = if (playerNumber == 2) map.player2Slots else map.player1Slots
        slots.forEach { slot ->
            val center = point(slot.x, slot.y)
            drawCircle(Color(0xFFCBD2CE), radius = size.minDimension * 0.045f, center = center)
            drawCircle(Color(0xFF53605E), radius = size.minDimension * 0.045f, center = center, style = Stroke(width = 2.dp.toPx()))
        }
        listOf(map.path.first(), map.path.last()).forEachIndexed { index, castle ->
            val center = point(castle.x, castle.y)
            drawCircle(if (index == 0) Color(0xFF3676B6) else Color(0xFFAF3C34), radius = size.minDimension * 0.075f, center = center)
        }
    }
}

@Composable
private fun MatchmakingButton(label: String, image: Int, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth(0.9f).height(54.dp).clip(RoundedCornerShape(14.dp))
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(image), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Text(label, color = Color(0xFF503018), fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1)
    }
}
