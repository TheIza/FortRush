package org.TheIza.fortrush.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Locale
import org.TheIza.fortrush.data.nickname.NicknameProfile
import org.TheIza.fortrush.data.nickname.NicknameRepository
import org.TheIza.fortrush.data.matchmaking.MatchmakingConnection
import org.TheIza.fortrush.data.matchmaking.MatchmakingEvent
import org.TheIza.fortrush.data.matchmaking.MatchmakingRepository
import org.TheIza.fortrush.data.matchmaking.MatchMap
import org.TheIza.fortrush.ui.Screen.CardsScreen
import org.TheIza.fortrush.ui.Screen.HomeScreen
import org.TheIza.fortrush.ui.Screen.ProfileScreen
import org.TheIza.fortrush.ui.Screen.MatchmakingScreen
import org.TheIza.fortrush.ui.Screen.MatchmakingStage
import org.TheIza.fortrush.ui.components.FortRushBottomBar
import org.TheIza.fortrush.ui.components.FortRushScreenBackground
import org.TheIza.fortrush.ui.components.NicknameConnectionErrorDialog
import org.TheIza.fortrush.ui.components.NicknameDialog
import org.TheIza.fortrush.ui.theme.FortRushTheme
import org.TheIza.fortrush.R

enum class MainDestination { CARDS, HOME, PROFILE, MATCHMAKING }

@Composable
fun FortRushApp(userId: String, onSignOut: () -> Unit = {}) {
    val repository = remember { NicknameRepository() }
    val matchmakingRepository = remember { MatchmakingRepository() }
    var profile by remember(userId) { mutableStateOf<NicknameProfile?>(null) }
    var isLoading by remember(userId) { mutableStateOf(true) }
    var loadError by remember(userId) { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var userCoins by remember { mutableIntStateOf(0) }
    var destination by rememberSaveable(userId) { mutableStateOf(MainDestination.HOME) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showCooldownDialog by remember { mutableStateOf(false) }
    var matchmakingConnection by remember { mutableStateOf<MatchmakingConnection?>(null) }
    var matchmakingStage by remember { mutableStateOf(MatchmakingStage.SEARCHING) }
    var queuePosition by remember { mutableIntStateOf(0) }
    var opponentNickname by remember { mutableStateOf<String?>(null) }
    var opponentNumber by remember { mutableIntStateOf(1) }
    var sharedMap by remember { mutableStateOf<MatchMap?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    var matchmakingError by remember { mutableStateOf<String?>(null) }
    var cancelWhenConnected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentProfile by rememberUpdatedState(profile)

    fun startMatchmaking() {
        matchmakingStage = MatchmakingStage.SEARCHING
        matchmakingError = null
        cancelWhenConnected = false
        queuePosition = 0
        opponentNickname = null
        sharedMap = null
        countdown = 0
        destination = MainDestination.MATCHMAKING
        matchmakingRepository.connect(
            onConnected = {
                matchmakingConnection = it
                if (cancelWhenConnected) {
                    cancelWhenConnected = false
                    it.cancelSearch()
                }
            },
            onEvent = { event ->
                scope.launch {
                    when (event) {
                        is MatchmakingEvent.Waiting -> { matchmakingStage = MatchmakingStage.SEARCHING; queuePosition = event.position }
                        is MatchmakingEvent.Found -> {
                            opponentNickname = event.opponentNickname
                            opponentNumber = event.playerNumber
                            sharedMap = event.map
                            matchmakingStage = MatchmakingStage.LOADING_MAP
                        }
                        is MatchmakingEvent.Countdown -> { countdown = event.seconds; matchmakingStage = MatchmakingStage.COUNTDOWN }
                        is MatchmakingEvent.Started -> matchmakingStage = MatchmakingStage.STARTED
                        MatchmakingEvent.QueueCancelled -> {
                            matchmakingConnection?.close()
                            matchmakingConnection = null
                            destination = MainDestination.HOME
                        }
                        is MatchmakingEvent.Cancelled -> {
                            if (event.requeued) {
                                matchmakingStage = MatchmakingStage.SEARCHING
                                queuePosition = 1
                                opponentNickname = null
                                sharedMap = null
                            } else {
                                matchmakingConnection?.close()
                                matchmakingConnection = null
                                destination = MainDestination.HOME
                            }
                        }
                        is MatchmakingEvent.Failure -> {
                            matchmakingStage = MatchmakingStage.ERROR
                            matchmakingError = event.message
                        }
                    }
                }
            }
        )
    }

    LaunchedEffect(matchmakingStage, sharedMap) {
        if (matchmakingStage == MatchmakingStage.LOADING_MAP && sharedMap != null) {
            // This UI has decoded and drawn the server geometry before acknowledging load.
            matchmakingConnection?.mapLoaded()
        }
    }

    LaunchedEffect(userId, refreshKey) {
        isLoading = true
        loadError = null
        try {
            profile = repository.loadProfile()
        } catch (error: Exception) {
            loadError = error.message ?: "No se pudo cargar tu perfil."
        } finally {
            isLoading = false
        }
    }

    DisposableEffect(userId) {
        val registration = FirebaseFirestore.getInstance().collection("Usuarios").document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    userCoins = snapshot.getLong("monedas")?.toInt() ?: 0
                    val nickname = snapshot.getString("apodo")?.takeIf(String::isNotBlank)
                    val current = currentProfile
                    if (nickname != null && current != null && nickname != current.nickname) {
                        profile = current.copy(
                            nickname = nickname,
                            normalized = snapshot.getString("apodoNormalizado") ?: nickname.lowercase(Locale.ROOT)
                        )
                    }
                }
            }
        onDispose { registration.remove() }
    }

    LaunchedEffect(profile, loadError, isLoading) {
        if (!isLoading && loadError == null && profile?.nickname.isNullOrBlank()) showNicknameDialog = true
    }

    Box(Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                FortRushScreenBackground(R.drawable.fr_fondo_inicio)
                Text("Cargando tu perfil…", Modifier.align(Alignment.Center), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            loadError != null -> {
                FortRushScreenBackground(R.drawable.fr_fondo_inicio)
                NicknameConnectionErrorDialog(message = loadError!!, onRetry = { refreshKey++ }, onSignOut = onSignOut)
            }
            profile?.nickname.isNullOrBlank() -> {
                FortRushScreenBackground(R.drawable.fr_fondo_inicio)
                NicknameDialog(
                    title = "¡Hola, guerrero! ¿Cuál es tu apodo?",
                    repository = repository,
                    dismissible = false,
                    onSaved = { profile = it; showNicknameDialog = false }
                )
            }
            else -> {
                when (destination) {
                    MainDestination.CARDS -> CardsScreen(coins = userCoins)
                    MainDestination.HOME -> HomeScreen(userName = profile!!.nickname!!, coins = userCoins, onAttackClick = ::startMatchmaking)
                    MainDestination.PROFILE -> ProfileScreen(
                        userName = profile!!.nickname!!,
                        coins = userCoins,
                        onChangeNickname = {
                            scope.launch {
                                try {
                                    val latest = repository.loadProfile()
                                    profile = latest
                                    if (latest.changeRemainingMs > 0) showCooldownDialog = true
                                    else showNicknameDialog = true
                                } catch (error: Exception) {
                                    loadError = error.message ?: "No se pudo comprobar el plazo de cambio."
                                }
                            }
                        },
                        onSignOut = onSignOut
                    )
                    MainDestination.MATCHMAKING -> MatchmakingScreen(
                        nickname = profile!!.nickname!!,
                        stage = matchmakingStage,
                        queuePosition = queuePosition,
                        opponentNickname = opponentNickname,
                        playerNumber = opponentNumber,
                        map = sharedMap,
                        countdown = countdown,
                        errorMessage = matchmakingError,
                        onCancel = {
                            val activeConnection = matchmakingConnection
                            if (activeConnection == null) cancelWhenConnected = true else activeConnection.cancelSearch()
                        },
                        onRetry = { matchmakingConnection?.close(); matchmakingConnection = null; startMatchmaking() }
                    )
                }
                if (destination != MainDestination.MATCHMAKING) {
                    FortRushBottomBar(selected = destination, onSelected = { destination = it }, modifier = Modifier.align(Alignment.BottomCenter))
                }
                if (showNicknameDialog && profile != null) {
                    NicknameDialog(
                        title = "Cambiar apodo",
                        repository = repository,
                        dismissible = true,
                        onDismiss = { showNicknameDialog = false },
                        onSaved = { profile = it; showNicknameDialog = false }
                    )
                }
                if (showCooldownDialog) {
                    NicknameCooldownDialog(
                        remainingMs = profile?.changeRemainingMs ?: 0L,
                        onDismiss = { showCooldownDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun NicknameCooldownDialog(remainingMs: Long, onDismiss: () -> Unit) {
    val days = ((remainingMs + 86_400_000L - 1) / 86_400_000L).coerceAtLeast(1)
    org.TheIza.fortrush.ui.components.NicknameNoticeDialog(
        title = "Apodo protegido",
        message = "Podrás cambiar tu apodo dentro de $days ${if (days == 1L) "día" else "días"}.",
        onDismiss = onDismiss
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable
private fun FortRushAppPreview() { FortRushTheme { Text("FortRush") } }
