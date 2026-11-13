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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CoinCounter(coins = coins)
                Spacer(Modifier.weight(1f))
                SettingsButton(onClick = onSettingsClick, modifier = Modifier.size(52.dp))
            }
            PlayerRibbon(userName = userName, modifier = Modifier.fillMaxWidth())
            Image(
                painter = painterResource(R.drawable.escena_inicio),
                contentDescription = "Castillo de FortRush",
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
                contentScale = ContentScale.Fit
            )
            FortRushAttackButton(
                onClick = onAttackClick,
                modifier = Modifier.fillMaxWidth(0.9f).height(82.dp)
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