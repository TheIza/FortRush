package org.TheIza.fortrush.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.Screen.CardsScreen
import org.TheIza.fortrush.ui.Screen.HomeScreen
import org.TheIza.fortrush.ui.Screen.ProfileScreen
import org.TheIza.fortrush.ui.components.FortRushBottomBar

enum class MainDestination { CARDS, HOME, PROFILE }

@Composable
fun FortRushApp(userName: String, onSignOut: () -> Unit) {
    var destination by rememberSaveable { mutableStateOf(MainDestination.HOME) }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.fondo_sin_nada),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        when (destination) {
            MainDestination.CARDS -> CardsScreen()
            MainDestination.HOME -> HomeScreen(userName = userName)
            MainDestination.PROFILE -> ProfileScreen(userName = userName, onSignOut = onSignOut)
        }
        FortRushBottomBar(
            selected = destination,
            onSelected = { destination = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
