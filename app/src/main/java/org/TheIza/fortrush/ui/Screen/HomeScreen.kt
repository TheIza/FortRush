package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.components.NameBanner
import org.TheIza.fortrush.ui.components.TopResources

@Composable
fun HomeScreen(userName: String) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        TopResources(
            Modifier.align(Alignment.TopCenter).padding(top = 42.dp, start = 14.dp, end = 14.dp).fillMaxWidth()
        )
        NameBanner(
            userName = userName,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp).offset(y = maxHeight * 0.092f).fillMaxWidth(0.72f)
        )
        Image(
            painterResource(R.drawable.ajustes),
            "Ajustes",
            Modifier.align(Alignment.TopEnd).padding(end = 14.dp).offset(y = maxHeight * 0.092f).fillMaxWidth(0.13f)
        )
        Image(
            painterResource(R.drawable.escena_inicio),
            "Fortaleza",
            Modifier.align(Alignment.TopCenter).offset(y = maxHeight * 0.225f).fillMaxWidth(0.64f).aspectRatio(1f)
        )
        Box(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = maxHeight * 0.64f).fillMaxWidth(0.60f).aspectRatio(3.05f).clickable { },
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.boton_principal), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Text("¡ATACAR!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp, textAlign = TextAlign.Center)
        }
    }
}
