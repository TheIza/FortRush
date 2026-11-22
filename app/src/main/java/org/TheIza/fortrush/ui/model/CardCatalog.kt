package org.TheIza.fortrush.ui.model

import org.TheIza.fortrush.R
import org.TheIza.fortrush.data.matchmaking.MatchTowerCard

data class CardCatalogItem(
    val name: String,
    val coinCost: Int,
    val imageResource: Int,
    val category: String,
    val summary: String
)

// Los costes de torre usan el modelo de partida. Los costes de tropa siguen siendo solo de presentación.
val fortRushCardCatalog = listOf(
    CardCatalogItem("Escudero", 50, R.drawable.escudero_muestra, "TROPA", "Más defensa, menos fuerza"),
    CardCatalogItem("Guerrero", 70, R.drawable.espadachin_muestra, "TROPA", "Combate cuerpo a cuerpo"),
    CardCatalogItem("Torre de flechas", MatchTowerCard.ARROW_TOWER.cost, R.drawable.carta_torre_flechas, "TORRE", "Ataque a distancia"),
    CardCatalogItem("Torre de cañón", MatchTowerCard.CANNON_TOWER.cost, R.drawable.carta_torre_canon, "TORRE", "Daño de área cercano")
)
