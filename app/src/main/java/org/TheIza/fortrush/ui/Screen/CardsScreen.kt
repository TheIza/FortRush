package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.components.TopResources

@Composable
fun CardsScreen() {
    Column(
        Modifier.fillMaxSize().padding(top = 42.dp, start = 18.dp, end = 18.dp, bottom = 130.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopResources(Modifier.fillMaxWidth())
        Box(Modifier.padding(top = 18.dp).fillMaxWidth(0.82f).aspectRatio(3.1f), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.cartel_titulo), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Text("CARTAS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        }
        Text("COLECCIÓN DE CARTAS", Modifier.padding(vertical = 22.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
        val cards = listOf(R.drawable.carta_espadachines, R.drawable.carta_escuderos, R.drawable.carta_torre_flechas, R.drawable.carta_torre_canon)
        cards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { card -> Image(painterResource(card), null, Modifier.size(145.dp)) }
            }
        }
    }
}
