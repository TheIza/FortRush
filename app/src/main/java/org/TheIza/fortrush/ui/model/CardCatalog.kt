package org.TheIza.fortrush.ui.model

import org.TheIza.fortrush.R

data class CardCatalogItem(
    val name: String,
    val coinCost: Int,
    val imageResource: Int
)

// One catalog supplies displayed names and prices; match rules remain separate.
val fortRushCardCatalog = listOf(
    CardCatalogItem("Escudero", 50, R.drawable.escudero_muestra),
    CardCatalogItem("Guerrero", 70, R.drawable.espadachin_muestra),
    CardCatalogItem("Torre de flechas", 110, R.drawable.carta_torre_flechas),
    CardCatalogItem("Torre de cañón", 120, R.drawable.carta_torre_canon)
)