package org.TheIza.fortrush.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.MainDestination

@Composable
fun FortRushBottomBar(
    selected: MainDestination,
    onSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Medieval Stone & Wood Rampart Wall Canvas Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cellW = w / 3f

            // 1. Horizontal Dark Oak Planks Background
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF5A381E), Color(0xFF38200E), Color(0xFF221206))
                ),
                topLeft = Offset(0f, 12.dp.toPx()),
                size = Size(w, h - 12.dp.toPx())
            )
            // Wood Plank Dividers
            val plankH = (h - 12.dp.toPx()) / 3f
            for (i in 1..2) {
                drawLine(
                    color = Color(0xFF140A03),
                    start = Offset(0f, 12.dp.toPx() + i * plankH),
                    end = Offset(w, 12.dp.toPx() + i * plankH),
                    strokeWidth = 3f
                )
            }

            // 2. Top Stone Wall Rim / Crenellation
            val stoneRimH = 14.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF5C6B7A), Color(0xFF343E47))
                ),
                topLeft = Offset(0f, 0f),
                size = Size(w, stoneRimH)
            )
            // Top Light Edge
            drawLine(
                color = Color(0xFF8697A6),
                start = Offset(0f, 1f),
                end = Offset(w, 1f),
                strokeWidth = 3f
            )
            // Bottom Rim Shadow
            drawLine(
                color = Color(0xFF1B2228),
                start = Offset(0f, stoneRimH),
                end = Offset(w, stoneRimH),
                strokeWidth = 4f
            )

            // Rivets on Stone Rim
            val rivetCount = 12
            for (i in 0 until rivetCount) {
                val rx = (i + 0.5f) * (w / rivetCount)
                drawCircle(color = Color(0xFF8BA0B2), center = Offset(rx, stoneRimH / 2f), radius = 3f)
                drawCircle(color = Color(0xFF1B2228), center = Offset(rx, stoneRimH / 2f), radius = 3f, style = Stroke(1.5f))
            }

            // 3. Vertical Stone Pillars (4 Dividers)
            val pillarW = 14.dp.toPx()
            for (i in 0..3) {
                val px = when (i) {
                    0 -> 0f
                    1 -> cellW - pillarW / 2f
                    2 -> cellW * 2 - pillarW / 2f
                    else -> w - pillarW
                }

                // Stone Pillar Shaft
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF6B7B8C), Color(0xFF3E4852), Color(0xFF232A30))
                    ),
                    topLeft = Offset(px, stoneRimH),
                    size = Size(pillarW, h - stoneRimH)
                )
                // Pillar Border Lines
                drawLine(
                    color = Color(0xFF1A2026),
                    start = Offset(px, stoneRimH),
                    end = Offset(px, h),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color(0xFF1A2026),
                    start = Offset(px + pillarW, stoneRimH),
                    end = Offset(px + pillarW, h),
                    strokeWidth = 2f
                )
                // Square Capital Rivets
                drawCircle(color = Color(0xFF90A3B5), center = Offset(px + pillarW / 2f, stoneRimH + 12.dp.toPx()), radius = 3f)
                drawCircle(color = Color(0xFF90A3B5), center = Offset(px + pillarW / 2f, h - 16.dp.toPx()), radius = 3f)
            }

        }

        // 3 Interactive Navigation Tabs
        Row(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CARDS (Left)
            NavItem(
                destination = MainDestination.CARDS,
                selected = selected,
                iconRes = R.drawable.nav_cartas,
                label = "Cartas",
                onSelected = onSelected,
                modifier = Modifier.weight(1f)
            )

            // HOME / CASTLE (Center - Raised)
            NavItem(
                destination = MainDestination.HOME,
                selected = selected,
                iconRes = R.drawable.nav_inicio,
                label = "Inicio",
                onSelected = onSelected,
                modifier = Modifier.weight(1f)
            )

            // PROFILE (Right - King Avatar)
            NavItem(
                destination = MainDestination.PROFILE,
                selected = selected,
                iconRes = R.drawable.avatar_rey,
                label = "Perfil",
                onSelected = onSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavItem(
    destination: MainDestination,
    selected: MainDestination,
    iconRes: Int,
    label: String,
    onSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = selected == destination
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = { onSelected(destination) }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x55FFD46A), Color(0x22FFD46A), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )
        }
        Image(
            painter = painterResource(iconRes),
            contentDescription = if (isSelected) "$label, seleccionada" else label,
            modifier = Modifier
                .offset(y = if (isSelected) (-4).dp else 0.dp)
                .size(if (isSelected) 60.dp else 50.dp)
        )
    }
}