package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
fun ProfileScreen(userName: String, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(top = 42.dp, start = 18.dp, end = 18.dp, bottom = 130.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopResources(Modifier.fillMaxWidth())
        NameBanner(userName, Modifier.padding(top = 18.dp).fillMaxWidth(0.92f))
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatPanel("PARTIDAS\nGANADAS", "0", Modifier.weight(1f))
            StatPanel("CARTAS\nDESCUBIERTAS", "4 / 4", Modifier.weight(1f))
        }
        Box(Modifier.padding(top = 24.dp).fillMaxWidth().aspectRatio(1.35f), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.panel_dialogo), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PERFIL", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                Text("Próximamente: historial y logros", color = Color.White, textAlign = TextAlign.Center)
            }
        }
        Box(
            Modifier.padding(top = 22.dp).fillMaxWidth(0.65f).aspectRatio(3.2f).clickable(onClick = onSignOut),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.boton_secundario), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Text("CERRAR SESIÓN", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
    }
}

@Composable
private fun StatPanel(title: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1.7f), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.panel_dialogo), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 13.sp)
            Text(value, color = Color(0xFFFFD35C), fontWeight = FontWeight.Black, fontSize = 24.sp)
        }
    }
}
