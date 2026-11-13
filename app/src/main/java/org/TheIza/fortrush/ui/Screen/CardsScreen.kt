package org.TheIza.fortrush.ui.Screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.components.CoinCounter
import org.TheIza.fortrush.ui.components.FortRushCardTile
import org.TheIza.fortrush.ui.components.FortRushPageTitle
import org.TheIza.fortrush.ui.components.FortRushScreenBackground
import org.TheIza.fortrush.ui.model.fortRushCardCatalog
import org.TheIza.fortrush.ui.theme.FortRushTheme

@Composable
fun CardsScreen(coins: Int = 0) {
    Box(Modifier.fillMaxSize()) {
        FortRushScreenBackground(R.drawable.fr_fondo_cartas)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 106.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FortRushPageTitle("Cartas", "Unidades y defensas")
                CoinCounter(coins = coins)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = rememberLazyGridState(),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
                contentPadding = PaddingValues(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(fortRushCardCatalog) { card -> FortRushCardTile(card = card) }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun CardsScreenPreview() {
    FortRushTheme { CardsScreen(coins = 7500) }
}
