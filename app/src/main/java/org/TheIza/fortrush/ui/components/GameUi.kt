package org.TheIza.fortrush.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.TheIza.fortrush.R
import java.text.NumberFormat
import java.util.Locale

// ==========================================
// TOP HUD / BARRA SUPERIOR
// ==========================================
@Composable
fun TopResources(
    level: Int = 0,
    exp: Int = 0,
    maxExp: Int = 100,
    coins: Int = 0,
    modifier: Modifier = Modifier
) {
    val progressRatio = (exp.toFloat() / maxExp.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    val formattedCoins = if (coins == 0) "0" else NumberFormat.getNumberInstance(Locale.US).format(coins).replace(",", " ")

    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. LEVEL BADGE + XP PROGRESS BAR
        Box(
            modifier = Modifier.height(44.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Dark Translucent XP Bar Container
            Row(
                modifier = Modifier
                    .padding(start = 22.dp)
                    .height(34.dp)
                    .width(136.dp)
                    .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                    .background(Color(0xFF0C1726))
                    .drawWithContent {
                        drawContent()
                        // Outer Dark Border
                        drawRoundRect(
                            color = Color(0xFF1B2F45),
                            style = Stroke(width = 3f),
                            cornerRadius = CornerRadius(24f, 24f)
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dynamic Progress Fill
                if (progressRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressRatio)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0077B6), Color(0xFF00A3EF))
                                )
                            )
                    )
                }
            }

            // XP Text centered over XP bar
            OutlinedText(
                text = "$exp/$maxExp",
                fontSize = 13.sp,
                outlineColor = Color(0xFF0A121C),
                fillColor = Color.White,
                strokeWidth = 5f,
                shadowOffset = Offset(1f, 2f),
                modifier = Modifier
                    .offset(x = 38.dp)
                    .width(105.dp)
                    .height(34.dp)
            )

            // Star Level Badge on Left (overlaps start of XP bar)
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = createStarPath(size.width, size.height, points = 8, innerRadiusRatio = 0.72f)
                    // Outer dark border
                    drawPath(
                        path = path,
                        color = Color(0xFF081524),
                        style = Stroke(width = 7f, join = StrokeJoin.Round)
                    )
                    // Inner cyan fill
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1AA3E8), Color(0xFF0073B7))
                        )
                    )
                    // Highlight rim
                    drawPath(
                        path = path,
                        color = Color(0xFF64D2FF),
                        style = Stroke(width = 2f, join = StrokeJoin.Round)
                    )
                }
                OutlinedText(
                    text = "$level",
                    fontSize = 16.sp,
                    outlineColor = Color(0xFF081422),
                    fillColor = Color.White,
                    strokeWidth = 6f,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // 2. TREASURE CHEST ICON IN CENTER
        TreasureChestIcon(modifier = Modifier.size(42.dp))

        // 3. GOLD RESOURCE CONTAINER
        Box(
            modifier = Modifier.height(44.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Dark Gold Container Box
            Row(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F1810))
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = Color(0xFF2B3D2C),
                            style = Stroke(width = 3f),
                            cornerRadius = CornerRadius(24f, 24f)
                        )
                    }
                    .padding(start = 14.dp, end = 26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedText(
                    text = formattedCoins,
                    fontSize = 15.sp,
                    outlineColor = Color(0xFF120E04),
                    fillColor = Color(0xFFFFF7DB),
                    strokeWidth = 5f,
                    shadowOffset = Offset(1f, 2f),
                    modifier = Modifier.height(30.dp)
                )
            }

            // Shiny 3D Gold Coin on Right
            Image(
                painter = painterResource(R.drawable.moneda),
                contentDescription = "Gold Coin",
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

// 3D Chest Icon Canvas
@Composable
private fun TreasureChestIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Drop shadow
        drawOval(
            color = Color(0x66000000),
            topLeft = Offset(w * 0.1f, h * 0.78f),
            size = Size(w * 0.8f, h * 0.2f)
        )

        // Wood Box Body
        val bodyPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(w * 0.12f, h * 0.35f, w * 0.88f, h * 0.85f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            )
        }
        drawPath(
            path = bodyPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF9E6231), Color(0xFF5E3616))
            )
        )
        drawPath(bodyPath, color = Color(0xFF2A1506), style = Stroke(4f))

        // Curved Chest Lid
        val lidPath = Path().apply {
            moveTo(w * 0.1f, h * 0.38f)
            cubicTo(w * 0.1f, h * 0.12f, w * 0.9f, h * 0.12f, w * 0.9f, h * 0.38f)
            close()
        }
        drawPath(
            path = lidPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFB8783E), Color(0xFF7A451E))
            )
        )
        drawPath(lidPath, color = Color(0xFF2A1506), style = Stroke(4f))

        // Silver Metal Bands
        drawRect(
            color = Color(0xFF9DAEBE),
            topLeft = Offset(w * 0.26f, h * 0.18f),
            size = Size(w * 0.1f, h * 0.67f)
        )
        drawRect(
            color = Color(0xFF9DAEBE),
            topLeft = Offset(w * 0.64f, h * 0.18f),
            size = Size(w * 0.1f, h * 0.67f)
        )

        // Gold Latch / Keyhole Plate
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFEA75), Color(0xFFD49A00))
            ),
            center = Offset(w * 0.5f, h * 0.48f),
            radius = w * 0.11f
        )
        drawCircle(
            color = Color(0xFF3B2000),
            center = Offset(w * 0.5f, h * 0.48f),
            radius = w * 0.11f,
            style = Stroke(3f)
        )
        // Keyhole dot
        drawCircle(
            color = Color(0xFF241300),
            center = Offset(w * 0.5f, h * 0.47f),
            radius = w * 0.04f
        )
    }
}

// Helper path for star polygon
private fun createStarPath(width: Float, height: Float, points: Int, innerRadiusRatio: Float): Path {
    val path = Path()
    val cx = width / 2f
    val cy = height / 2f
    val outerRadius = width / 2f * 0.96f
    val innerRadius = outerRadius * innerRadiusRatio
    val angleStep = Math.PI / points

    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = i * angleStep - Math.PI / 2
        val x = cx + r * Math.cos(angle).toFloat()
        val y = cy + r * Math.sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}


// ==========================================
// BANNER DEL JUGADOR (RED RIBBON)
// ==========================================
@Composable
fun NameBanner(userName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(58.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Red Ribbon Canvas Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Ribbon Body Path with Swallowtail Tail on Right Edge
            val ribbonPath = Path().apply {
                moveTo(h * 0.4f, h * 0.12f)
                lineTo(w - h * 0.35f, h * 0.12f)
                // Swallowtail notch on right edge
                lineTo(w, h * 0.12f)
                lineTo(w - h * 0.25f, h * 0.5f)
                lineTo(w, h * 0.88f)
                lineTo(h * 0.4f, h * 0.88f)
                close()
            }

            // Dark Red Shadow Fold (Bottom Edge)
            val shadowFold = Path().apply {
                moveTo(h * 0.4f, h * 0.88f)
                lineTo(w - h * 0.25f, h * 0.88f)
                lineTo(w, h * 0.88f)
                lineTo(w - h * 0.15f, h * 0.98f)
                lineTo(h * 0.4f, h * 0.98f)
                close()
            }
            drawPath(shadowFold, color = Color(0xFF5E0F0F))

            // Main Red Ribbon Gradient
            drawPath(
                path = ribbonPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD32F2F), Color(0xFF9A0007))
                )
            )
            // Top Edge Highlight Line
            drawLine(
                color = Color(0xFFFF7A7A),
                start = Offset(h * 0.4f, h * 0.14f),
                end = Offset(w - h * 0.35f, h * 0.14f),
                strokeWidth = 3f
            )
            // Dark Outline
            drawPath(
                path = ribbonPath,
                color = Color(0xFF4A0808),
                style = Stroke(width = 4f, join = StrokeJoin.Round)
            )
        }

        // Circular Dark Stone Frame on Left containing Avatar
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.width / 2f
                // Shadow
                drawCircle(color = Color(0x77000000), radius = r, center = Offset(r + 2f, r + 3f))
                // Dark Stone Outer Rim
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF4A5560), Color(0xFF232A30))
                    ),
                    radius = r
                )
                // Metal Inner Ring
                drawCircle(
                    color = Color(0xFF8697A6),
                    radius = r * 0.88f,
                    style = Stroke(width = 4f)
                )
                // Dark Interior Fill
                drawCircle(
                    color = Color(0xFF181F25),
                    radius = r * 0.82f
                )
            }

            Image(
                painter = painterResource(R.drawable.avatar_rey),
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
            )
        }

        // Player Name Text
        OutlinedText(
            text = userName.uppercase(),
            fontSize = 19.sp,
            outlineColor = Color(0xFF2E0909),
            fillColor = Color.White,
            strokeWidth = 8f,
            shadowOffset = Offset(2f, 3f),
            modifier = Modifier
                .padding(start = 58.dp, end = 32.dp)
                .fillMaxWidth()
                .height(44.dp)
        )
    }
}


// ==========================================
// SETTINGS BUTTON (TOP RIGHT)
// ==========================================
@Composable
fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1.0f, label = "SettingsScale")

    Box(
        modifier = modifier
            .scale(scale)
            .size(52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Drop Shadow
            drawRoundRect(
                color = Color(0x77000000),
                topLeft = Offset(2f, 4f),
                size = Size(w, h),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            )

            // Rounded Square Slate Base
            val stonePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, w, h),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                )
            }
            drawPath(
                path = stonePath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF4B5763), Color(0xFF262E35))
                )
            )
            // Top Light Rim
            drawPath(
                path = stonePath,
                color = Color(0xFF7A8C9E),
                style = Stroke(width = 3f)
            )
            // Inner Inset Bevel
            drawRoundRect(
                color = Color(0xFF192026),
                topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                size = Size(w - 8.dp.toPx(), h - 8.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 3f)
            )
        }

        Image(
            painter = painterResource(R.drawable.ajustes),
            contentDescription = "Ajustes",
            modifier = Modifier.size(36.dp)
        )
    }
}


// ==========================================
// CASTLE 3D ARENA STAGE PEDESTAL
// ==========================================
@Composable
fun CastlePedestal(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Heavy Soft Drop Shadow Under Pedestal Base
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x99000000), Color(0x00000000)),
                    center = Offset(w / 2f, h * 0.94f),
                    radius = w * 0.54f
                ),
                topLeft = Offset(w * 0.01f, h * 0.76f),
                size = Size(w * 0.98f, h * 0.24f)
            )

            // 2. 3D Slate Stone Pedestal Block (Extruded Base)
            val pedestalCorner = 32.dp.toPx()

            // Outer Shadow & Base Wall Extrusion
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C3640), Color(0xFF151B21), Color(0xFF0C1014))
                ),
                topLeft = Offset(w * 0.03f, h * 0.08f),
                size = Size(w * 0.94f, h * 0.88f),
                cornerRadius = CornerRadius(pedestalCorner, pedestalCorner)
            )

            // 3. Top Slate Bevel Rim
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF6B7B8C), Color(0xFF384450))
                ),
                topLeft = Offset(w * 0.03f, h * 0.06f),
                size = Size(w * 0.94f, h * 0.82f),
                cornerRadius = CornerRadius(pedestalCorner, pedestalCorner),
                style = Stroke(width = 8f)
            )

            // 4. Inner Grass Stage Top Surface
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF4C7D38), Color(0xFF355C24))
                ),
                topLeft = Offset(w * 0.055f, h * 0.085f),
                size = Size(w * 0.89f, h * 0.77f),
                cornerRadius = CornerRadius(pedestalCorner - 8.dp.toPx(), pedestalCorner - 8.dp.toPx())
            )
        }

        // Castle Artwork Centered COMPLETELY INSIDE the Pedestal Box Surface
        Box(
            modifier = Modifier
                .fillMaxSize(0.68f)
                .offset(y = (-2).dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.escena_inicio),
                contentDescription = "Fortaleza",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}


// ==========================================
// EPIC ATTACK BUTTON (BOTÓN DE ATACAR)
// ==========================================
@Composable
fun AttackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(if (isPressed) 0.95f else 1.0f, label = "AttackButtonScale")

    Box(
        modifier = modifier
            .scale(buttonScale)
            .height(92.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Wooden Frame + Metal Brackets + Inner Golden Gradient Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val frameCorner = 22.dp.toPx()

            // 1. Drop Shadow
            drawRoundRect(
                color = Color(0x88000000),
                topLeft = Offset(4f, 8f),
                size = Size(w - 8f, h - 8f),
                cornerRadius = CornerRadius(frameCorner, frameCorner)
            )

            // 2. Thick Outer Wooden Frame
            val woodPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 6.dp.toPx(), w, h),
                        cornerRadius = CornerRadius(frameCorner, frameCorner)
                    )
                )
            }
            drawPath(
                path = woodPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF72431E), Color(0xFF48260D), Color(0xFF2C1404))
                )
            )
            // Wood Top Highlight Bevel
            drawPath(
                path = woodPath,
                color = Color(0xFFA66939),
                style = Stroke(width = 4f)
            )

            // 3. Inner Golden Gradient Button
            val innerMarginH = 18.dp.toPx()
            val innerMarginV = 15.dp.toPx()
            val innerRect = Rect(
                innerMarginH,
                6.dp.toPx() + innerMarginV,
                w - innerMarginH,
                h - innerMarginV
            )
            val innerCorner = 16.dp.toPx()

            // Golden Surface Fill
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF275), // Top bright yellow shine
                        Color(0xFFFFB800), // Mid gold
                        Color(0xFFE67E00)  // Bottom orange shadow
                    )
                ),
                topLeft = Offset(innerRect.left, innerRect.top),
                size = Size(innerRect.width, innerRect.height),
                cornerRadius = CornerRadius(innerCorner, innerCorner)
            )

            // Dark Golden Border Stroke
            drawRoundRect(
                color = Color(0xFF944500),
                topLeft = Offset(innerRect.left, innerRect.top),
                size = Size(innerRect.width, innerRect.height),
                cornerRadius = CornerRadius(innerCorner, innerCorner),
                style = Stroke(width = 5f)
            )

            // Top Edge Inner White Highlight Line
            drawLine(
                color = Color(0xEEFFFFFF),
                start = Offset(innerRect.left + 18.dp.toPx(), innerRect.top + 3.dp.toPx()),
                end = Offset(innerRect.right - 18.dp.toPx(), innerRect.top + 3.dp.toPx()),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )

            // Corner Sparkle Accent Stars (Top Left & Bottom Right)
            drawSparkleStar(this, Offset(innerRect.left + 24.dp.toPx(), innerRect.top + 14.dp.toPx()), 11.dp.toPx())
            drawSparkleStar(this, Offset(innerRect.right - 24.dp.toPx(), innerRect.bottom - 14.dp.toPx()), 11.dp.toPx())

            // 4. Metallic Corner Brackets (4 Corners)
            val capW = 32.dp.toPx()
            val capH = 28.dp.toPx()

            // Top-Left Metal Corner Cap
            drawMetalBracket(this, Offset(0f, 6.dp.toPx()), capW, capH, isTop = true, isLeft = true)
            // Top-Right Metal Corner Cap
            drawMetalBracket(this, Offset(w - capW, 6.dp.toPx()), capW, capH, isTop = true, isLeft = false)
            // Bottom-Left Metal Corner Cap
            drawMetalBracket(this, Offset(0f, h - capH), capW, capH, isTop = false, isLeft = true)
            // Bottom-Right Metal Corner Cap
            drawMetalBracket(this, Offset(w - capW, h - capH), capW, capH, isTop = false, isLeft = false)
        }

        // Top Decorative Crossed Swords Icon Header
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
        ) {
            CrossedSwordsHeader()
        }

        // "¡ATACAR!" Text in Center
        OutlinedText(
            text = "¡ATACAR!",
            fontSize = 34.sp,
            outlineColor = Color(0xFF381A00),
            fillColor = Color.White,
            strokeWidth = 11f,
            shadowOffset = Offset(3f, 5f),
            modifier = Modifier
                .offset(y = 4.dp)
                .fillMaxWidth(0.85f)
                .height(60.dp)
        )
    }
}

// Draw Metallic Corner Bracket with Rivet
private fun drawMetalBracket(
    scope: DrawScope,
    offset: Offset,
    w: Float,
    h: Float,
    isTop: Boolean,
    isLeft: Boolean
) {
    with(scope) {
        val bracketPath = Path().apply {
            if (isTop && isLeft) {
                moveTo(offset.x, offset.y + h)
                lineTo(offset.x, offset.y + 8f)
                lineTo(offset.x + 8f, offset.y)
                lineTo(offset.x + w, offset.y)
                lineTo(offset.x + w * 0.6f, offset.y + h * 0.6f)
                lineTo(offset.x, offset.y + h)
            } else if (isTop && !isLeft) {
                moveTo(offset.x + w, offset.y + h)
                lineTo(offset.x + w, offset.y + 8f)
                lineTo(offset.x + w - 8f, offset.y)
                lineTo(offset.x, offset.y)
                lineTo(offset.x + w * 0.4f, offset.y + h * 0.6f)
                lineTo(offset.x + w, offset.y + h)
            } else if (!isTop && isLeft) {
                moveTo(offset.x, offset.y)
                lineTo(offset.x, offset.y + h - 8f)
                lineTo(offset.x + 8f, offset.y + h)
                lineTo(offset.x + w, offset.y + h)
                lineTo(offset.x + w * 0.6f, offset.y + h * 0.4f)
                lineTo(offset.x, offset.y)
            } else {
                moveTo(offset.x + w, offset.y)
                lineTo(offset.x + w, offset.y + h - 8f)
                lineTo(offset.x + w - 8f, offset.y + h)
                lineTo(offset.x, offset.y + h)
                lineTo(offset.x + w * 0.4f, offset.y + h * 0.4f)
                lineTo(offset.x + w, offset.y)
            }
            close()
        }

        // Metallic Fill
        drawPath(
            path = bracketPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFB4C4D4), Color(0xFF6B7B8C), Color(0xFF45505C))
            )
        )
        // Dark Metal Border
        drawPath(
            path = bracketPath,
            color = Color(0xFF232A32),
            style = Stroke(width = 3f)
        )

        // Rivet / Screw Dot
        val rivetX = if (isLeft) offset.x + w * 0.35f else offset.x + w * 0.65f
        val rivetY = if (isTop) offset.y + h * 0.35f else offset.y + h * 0.65f
        drawCircle(
            color = Color(0xFFE8F0F8),
            center = Offset(rivetX, rivetY),
            radius = 4f
        )
        drawCircle(
            color = Color(0xFF1E242B),
            center = Offset(rivetX, rivetY),
            radius = 4f,
            style = Stroke(2f)
        )
    }
}

// Sparkle Star Shape
private fun drawSparkleStar(scope: DrawScope, center: Offset, size: Float) {
    with(scope) {
        val path = Path().apply {
            moveTo(center.x, center.y - size)
            quadraticTo(center.x, center.y, center.x + size, center.y)
            quadraticTo(center.x, center.y, center.x, center.y + size)
            quadraticTo(center.x, center.y, center.x - size, center.y)
            quadraticTo(center.x, center.y, center.x, center.y - size)
            close()
        }
        drawPath(path, color = Color(0xFFFFFFFF))
    }
}

// Top Decorative Crossed Swords Icon Header
@Composable
private fun CrossedSwordsHeader() {
    Canvas(modifier = Modifier.size(width = 48.dp, height = 26.dp)) {
        val w = size.width
        val h = size.height

        // Sword 1 (Diagonal /)
        drawLine(
            color = Color(0xFFD4E2F0),
            start = Offset(w * 0.18f, h * 0.88f),
            end = Offset(w * 0.82f, h * 0.12f),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        // Sword 2 (Diagonal \)
        drawLine(
            color = Color(0xFFD4E2F0),
            start = Offset(w * 0.82f, h * 0.88f),
            end = Offset(w * 0.18f, h * 0.12f),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )

        // Gold Crossguards
        drawCircle(color = Color(0xFFFFD700), center = Offset(w * 0.30f, h * 0.74f), radius = 5f)
        drawCircle(color = Color(0xFFFFD700), center = Offset(w * 0.70f, h * 0.74f), radius = 5f)

        // Sparkle Stars near tips
        drawSparkleStar(this, Offset(w * 0.10f, h * 0.16f), 6f)
        drawSparkleStar(this, Offset(w * 0.90f, h * 0.16f), 6f)
    }
}
