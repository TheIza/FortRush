package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.components.CoinCounter
import org.TheIza.fortrush.ui.components.FortRushAttackButton
import org.TheIza.fortrush.ui.components.FortRushScreenBackground
import org.TheIza.fortrush.ui.components.PlayerRibbon
import org.TheIza.fortrush.ui.components.SettingsButton
import org.TheIza.fortrush.ui.theme.FortRushTheme

@Composable
fun HomeScreen(
    userName: String,
    coins: Int = 0,
    onAttackClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize()) {
        FortRushScreenBackground(R.drawable.fr_fondo_inicio)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoinCounter(coins = coins)
                Spacer(Modifier.weight(1f))
                SettingsButton(onClick = onSettingsClick, modifier = Modifier.size(50.dp))
            }
            Spacer(Modifier.height(10.dp))
            PlayerRibbon(userName = userName, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Image(
                painter = painterResource(R.drawable.escena_inicio),
                contentDescription = "Castillo de FortRush, rodeado de bosque y un camino de entrada",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "PREPARA TU REINO",
                color = Color(0xFFFFE5A0),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = "Reúne tus cartas y defiende el castillo.",
                color = Color(0xFFE9F0DD),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            FortRushAttackButton(
                onClick = onAttackClick,
                modifier = Modifier.fillMaxWidth().height(70.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomeScreenPreview() {
    FortRushTheme {
        HomeScreen(userName = "GUERRERO_1234567", coins = 7500, onAttackClick = {})
    }
}