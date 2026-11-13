package org.TheIza.fortrush.ui.model

import org.TheIza.fortrush.R

data class CardCatalogItem(
    val name: String,
    val coinCost: Int,
    val imageResource: Int,
    val category: String,
    val summary: String
)

// This catalog supplies presentation only; gameplay rules and costs remain separate.
val fortRushCardCatalog = listOf(
    CardCatalogItem("Escudero", 50, R.drawable.escudero_muestra, "TROPA", "Más defensa, menos fuerza"),
    CardCatalogItem("Guerrero", 70, R.drawable.espadachin_muestra, "TROPA", "Combate cuerpo a cuerpo"),
    CardCatalogItem("Torre de flechas", 110, R.drawable.carta_torre_flechas, "TORRE", "Ataque a distancia"),
    CardCatalogItem("Torre de cañón", 120, R.drawable.carta_torre_canon, "TORRE", "Daño de área cercano")
)
