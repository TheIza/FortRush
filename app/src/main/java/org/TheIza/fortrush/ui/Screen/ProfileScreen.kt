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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.components.CoinCounter
import org.TheIza.fortrush.ui.components.FortRushScreenBackground
import org.TheIza.fortrush.ui.theme.FortRushTheme

@Composable
fun ProfileScreen(userName: String, coins: Int = 0, onSignOut: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        FortRushScreenBackground(R.drawable.fr_fondo_usuario)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Perfil", color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
                CoinCounter(coins = coins)
            }
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xE5283527), Color(0xE5122019))))
                    .border(2.dp, Color(0xFF9AA77D), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.avatar_rey),
                    contentDescription = "Avatar predeterminado del jugador",
                    modifier = Modifier.size(148.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = userName.ifBlank { "Jugador" },
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Cuenta de Google", color = Color(0xFFE3EBD8), fontSize = 15.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(58.dp)
                    .semantics {
                        contentDescription = "Cerrar sesión"
                    }
                    .clickable(role = Role.Button, onClick = onSignOut),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.fr_boton_secundario),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Text(
                    text = "CERRAR SESIÓN",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun ProfileScreenPreview() {
    FortRushTheme { ProfileScreen(userName = "GUERRERO_1234567", coins = 7500, onSignOut = {}) }
}