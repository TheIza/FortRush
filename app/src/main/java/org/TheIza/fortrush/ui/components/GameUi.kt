package org.TheIza.fortrush.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.TheIza.fortrush.R

@Composable
fun TopResources(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Color(0xFF1976C9), shape = RoundedCornerShape(14.dp)) {
            Text("60", Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Black)
        }
        Surface(
            modifier = Modifier.padding(start = 5.dp),
            color = Color(0xCC142016),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("31/100", Modifier.padding(horizontal = 15.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f))
        Surface(color = Color(0xCC142016), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("168 500", color = Color.White, fontWeight = FontWeight.Black)
                Image(painterResource(R.drawable.moneda), null, Modifier.padding(start = 5.dp).size(28.dp))
            }
        }
    }
}

@Composable
fun NameBanner(userName: String, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(4.6f), contentAlignment = Alignment.CenterStart) {
        Image(
            painterResource(R.drawable.marco_apodo),
            null,
            Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillBounds
        )
        Image(
            painterResource(R.drawable.avatar_rey),
            "Avatar",
            Modifier.padding(start = 8.dp).size(54.dp)
        )
        Text(
            userName.uppercase(),
            modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 14.dp),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
