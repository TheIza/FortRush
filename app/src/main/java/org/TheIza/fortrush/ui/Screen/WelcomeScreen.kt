package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.theme.FortRushTheme
import kotlin.math.roundToInt

@Composable
fun WelcomeScreen(onGoogleSignInClick: () -> Unit, loginStatus: String = "") {
    val artwork = ImageBitmap.imageResource(R.drawable.bienvenida_referencia)
    val correctedText = ImageBitmap.imageResource(R.drawable.bienvenida_texto_google)
    val buttonLabel = stringResource(R.string.sign_in_google)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val artworkWidth = minOf(maxWidth, maxHeight * (572f / 1024f))
        val artworkHeight = artworkWidth * (1024f / 572f)
        // Mirror the outer strips of the reference to continue its pattern at both edges.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF243519))
            val tileWidth = artworkWidth.toPx()
            val imageHeight = artworkHeight.toPx()
            val x = (size.width - tileWidth) / 2f
            val top = (size.height - imageHeight) / 2f
            val bottom = top + imageHeight
            val topSourceHeight = 120
            val bottomSourceHeight = 180
            val topHeight = imageHeight * topSourceHeight / artwork.height
            val bottomHeight = imageHeight * bottomSourceHeight / artwork.height
            fun drawStrip(y: Float, height: Float, sourceY: Int, sourceHeight: Int, flip: Boolean) {
                val paintStrip: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                    drawImage(
                        image = artwork,
                        srcOffset = IntOffset(0, sourceY),
                        srcSize = IntSize(artwork.width, sourceHeight),
                        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                        dstSize = IntSize(tileWidth.roundToInt(), height.roundToInt())
                    )
                }
                if (flip) scale(1f, -1f, Offset(0f, y + height / 2f), paintStrip)
                else paintStrip()
            }
            var y = top
            var flip = true
            while (y > 0f) {
                y -= topHeight
                drawStrip(y, topHeight, 0, topSourceHeight, flip)
                flip = !flip
            }
            y = bottom
            flip = true
            while (y < size.height) {
                drawStrip(y, bottomHeight, artwork.height - bottomSourceHeight, bottomSourceHeight, flip)
                y += bottomHeight
                flip = !flip
            }
        }
        // Fit the reference without cropping or distorting it, with a real accessible button.
        Box(Modifier.size(artworkWidth, artworkHeight)) {
            Image(
                painter = painterResource(R.drawable.bienvenida_referencia),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            // Replace only the yellow text line, keeping the rest of the original artwork.
            Canvas(Modifier.fillMaxSize()) {
                clipRect(
                    left = size.width * 0.18f,
                    top = size.height * 0.337f,
                    right = size.width * 0.82f,
                    bottom = size.height * 0.397f
                ) {
                    drawImage(
                        image = correctedText,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                }
            }
            Box(
                modifier = Modifier
                    .offset(x = artworkWidth * (104f / 572f), y = artworkHeight * (477f / 1024f))
                    .size(artworkWidth * (364f / 572f), artworkHeight * (120f / 1024f))
                    .semantics { contentDescription = buttonLabel }
                    .clickable(role = Role.Button, onClick = onGoogleSignInClick)
            )
        }
        if (loginStatus.isNotBlank()) {
            Surface(
                color = Color(0xEE293329),
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(loginStatus, Modifier.padding(10.dp), color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun WelcomeScreenPreview() {
    FortRushTheme { WelcomeScreen(onGoogleSignInClick = {}) }
}
