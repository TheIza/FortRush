package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.TheIza.fortrush.ui.components.AttackButton
import org.TheIza.fortrush.ui.components.CastlePedestal
import org.TheIza.fortrush.ui.components.NameBanner
import org.TheIza.fortrush.ui.components.SettingsButton
import org.TheIza.fortrush.ui.components.TopResources
import org.TheIza.fortrush.ui.theme.FortRushTheme

@Composable
fun HomeScreen(
    userName: String,
    level: Int = 0,
    exp: Int = 0,
    maxExp: Int = 100,
    coins: Int = 0,
    onAttackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 98.dp, start = 14.dp, end = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. TOP HUD BAR (Dynamic Level, EXP, Chest, Coins)
        TopResources(
            level = level,
            exp = exp,
            maxExp = maxExp,
            coins = coins,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. SUB-HEADER ROW (Red Ribbon Banner + Settings Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NameBanner(
                userName = userName,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.padding(horizontal = 6.dp))

            SettingsButton(
                onClick = onSettingsClick
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. HERO CENTRAL CASTLE (Slightly smaller, fits 100% inside pedestal box)
        CastlePedestal(
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .aspectRatio(1.15f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. BOTÓN DE ATACAR (Epic Battle Button)
        AttackButton(
            onClick = onAttackClick,
            modifier = Modifier.fillMaxWidth(0.88f)
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable
private fun HomeScreenPreview() {
    FortRushTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreen(
                userName = "GUERRERO_99",
                level = 0,
                exp = 0,
                coins = 0
            )
        }
    }
}
