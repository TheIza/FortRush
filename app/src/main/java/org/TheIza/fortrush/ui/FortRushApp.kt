package org.TheIza.fortrush.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.firestore.FirebaseFirestore
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.Screen.CardsScreen
import org.TheIza.fortrush.ui.Screen.HomeScreen
import org.TheIza.fortrush.ui.Screen.ProfileScreen
import org.TheIza.fortrush.ui.components.FortRushBottomBar
import org.TheIza.fortrush.ui.theme.FortRushTheme

enum class MainDestination { CARDS, HOME, PROFILE }

@Composable
fun FortRushApp(
    userName: String,
    userId: String = "",
    onSignOut: () -> Unit = {}
) {
    var destination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
    var userCoins by remember { mutableIntStateOf(0) }
    var userExp by remember { mutableIntStateOf(0) }
    var userLevel by remember { mutableIntStateOf(0) }

    // Real-time Firestore Sync for Coins, EXP, Level
    DisposableEffect(userId) {
        if (userId.isBlank()) return@DisposableEffect onDispose {}
        val registration = FirebaseFirestore.getInstance()
            .collection("Usuarios")
            .document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    userCoins = snapshot.getLong("monedas")?.toInt() ?: 0
                    userExp = snapshot.getLong("experiencia")?.toInt() ?: 0
                    userLevel = snapshot.getLong("nivel")?.toInt() ?: 0
                }
            }
        onDispose { registration.remove() }
    }

    Box(Modifier.fillMaxSize()) {
        // Green Diamond Pattern Background
        Image(
            painter = painterResource(R.drawable.fondo_sin_nada),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Main Content Area
        when (destination) {
            MainDestination.CARDS -> CardsScreen(
                level = userLevel,
                exp = userExp,
                coins = userCoins
            )
            MainDestination.HOME -> HomeScreen(
                userName = userName,
                level = userLevel,
                exp = userExp,
                coins = userCoins
            )
            MainDestination.PROFILE -> ProfileScreen(
                userName = userName,
                level = userLevel,
                exp = userExp,
                coins = userCoins,
                onSignOut = onSignOut
            )
        }

        // Medieval Stone & Wood Rampart Navigation Bar at Bottom
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
    FortRushTheme {
        FortRushApp(userName = "GUERRERO_99")
    }
}
