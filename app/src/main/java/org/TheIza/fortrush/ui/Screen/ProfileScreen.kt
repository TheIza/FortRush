package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.components.CoinCounter
import org.TheIza.fortrush.ui.components.FortRushScreenBackground
import org.TheIza.fortrush.ui.theme.FortRushTheme

@Composable
fun ProfileScreen(userName: String, coins: Int = 0, onChangeNickname: () -> Unit = {}, onSignOut: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        FortRushScreenBackground(R.drawable.fr_fondo_usuario)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 120.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Tu perfil", color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
                    Text("Tu cuenta de FortRush", color = Color(0xFFD5E3D2), fontSize = 13.sp)
                }
                CoinCounter(coins = coins)
            }
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xE522342B), Color(0xE510211A))))
                    .border(1.5.dp, Color(0xFFB6C38C), RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(142.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF354E37))
                            .border(2.dp, Color(0xFFFFD46A), CircleShape)
                    )
                    Image(
                        painter = painterResource(R.drawable.avatar_rey),
                        contentDescription = "Avatar del rey",
                        modifier = Modifier.size(132.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = userName,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 21.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x443FA568))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8DE5A2)))
                    Text("Cuenta de Google conectada", color = Color(0xFFDFF2DF), fontSize = 12.sp)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Cambiar apodo",
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x443C4C37))
                        .border(1.dp, Color(0xFF9B8751), RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onChangeNickname)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    color = Color(0xFFFFE3A0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x443C4C37))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painter = painterResource(R.drawable.moneda), contentDescription = null, modifier = Modifier.size(28.dp))
                    Text("Monedas", color = Color(0xFFDDE7D5), fontSize = 14.sp, modifier = Modifier.padding(start = 9.dp))
                    Spacer(Modifier.weight(1f))
                    Text(coins.coerceAtLeast(0).toString(), color = Color(0xFFFFE27A), fontWeight = FontWeight.Black, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF9F3730), Color(0xFF69231F))))
                    .border(1.5.dp, Color(0xFFE1A356), RoundedCornerShape(16.dp))
                    .semantics { contentDescription = "Cerrar sesión de FortRush" }
                    .clickable(role = Role.Button, onClick = onSignOut),
                contentAlignment = Alignment.Center
            ) {
                Text("CERRAR SESIÓN", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun ProfileScreenPreview() {
    FortRushTheme { ProfileScreen(userName = "Jugador de FortRush", coins = 7500, onSignOut = {}) }
}
