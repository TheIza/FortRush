package org.TheIza.fortrush.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.firestore.FirebaseFirestore
import org.TheIza.fortrush.ui.Screen.CardsScreen
import org.TheIza.fortrush.ui.Screen.HomeScreen
import org.TheIza.fortrush.ui.Screen.ProfileScreen
import org.TheIza.fortrush.ui.components.FortRushBottomBar
import org.TheIza.fortrush.ui.theme.FortRushTheme

enum class MainDestination { CARDS, HOME, PROFILE }

@Composable
fun FortRushApp(userName: String, userId: String = "", onSignOut: () -> Unit = {}) {
    var destination by rememberSaveable { androidx.compose.runtime.mutableStateOf(MainDestination.HOME) }
    var userCoins by remember { mutableIntStateOf(0) }

    DisposableEffect(userId) {
        if (userId.isBlank()) return@DisposableEffect onDispose {}
        val registration = FirebaseFirestore.getInstance()
            .collection("Usuarios")
            .document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    userCoins = snapshot.getLong("monedas")?.toInt() ?: 0
                }
            }
        onDispose { registration.remove() }
    }

    Box(Modifier.fillMaxSize()) {
        when (destination) {
            MainDestination.CARDS -> CardsScreen(coins = userCoins)
            MainDestination.HOME -> HomeScreen(userName = userName, coins = userCoins)
            MainDestination.PROFILE -> ProfileScreen(
                userName = userName,
                coins = userCoins,
                onSignOut = onSignOut
            )
        }
        FortRushBottomBar(
            selected = destination,
            onSelected = { destination = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable
private fun FortRushAppPreview() {
    FortRushTheme { FortRushApp(userName = "Jugador") }
}