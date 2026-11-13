package org.TheIza.fortrush.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit

@Composable
fun OutlinedText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    outlineColor: Color = Color(0xFF261405),
    fillColor: Color = Color.White,
    strokeWidth: Float = 8f,
    shadowOffset: Offset = Offset(2f, 4f),
    shadowColor: Color = Color(0x99000000)
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvas = drawContext.canvas.nativeCanvas
            val fontSizePx = fontSize.toPx()
            
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = fontSizePx
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            
            val centerX = size.width / 2f
            val centerY = size.height / 2f - (paint.descent() + paint.ascent()) / 2f

            // 1. Drop Shadow
            if (shadowOffset != Offset.Zero) {
                paint.style = Paint.Style.FILL
                paint.color = shadowColor.toArgb()
                canvas.drawText(text, centerX + shadowOffset.x, centerY + shadowOffset.y, paint)
            }

            // 2. Thick Outer Stroke
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = outlineColor.toArgb()
            canvas.drawText(text, centerX, centerY, paint)

            // 3. Inner Fill
            paint.style = Paint.Style.FILL
            paint.color = fillColor.toArgb()
            canvas.drawText(text, centerX, centerY, paint)
        }
    }
}
