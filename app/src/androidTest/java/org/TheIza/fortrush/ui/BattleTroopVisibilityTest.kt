package org.TheIza.fortrush.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.TheIza.fortrush.data.matchmaking.MatchGameSnapshot
import org.TheIza.fortrush.data.matchmaking.MatchGameTroop
import org.TheIza.fortrush.data.matchmaking.MatchPlayerVitals
import org.TheIza.fortrush.data.matchmaking.MatchPoint
import org.TheIza.fortrush.data.matchmaking.createDebugBotMap
import org.TheIza.fortrush.ui.Screen.BattleArenaScreen
import org.TheIza.fortrush.ui.theme.FortRushTheme

/** Exercises the actual online Canvas, including a legacy server that repeats sequence. */
class BattleTroopVisibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun troopsAppearMoveAndDisappearForPlayerOne() = checkRender(viewer = 1)
    @Test fun troopsAppearMoveAndDisappearForPlayerTwo() = checkRender(viewer = 2)

    private fun checkRender(viewer: Int) {
        compose.mainClock.autoAdvance = false
        val map = createDebugBotMap()
        val firstPoint = map.path[map.path.lastIndex / 4]
        val secondPoint = map.path[map.path.lastIndex * 3 / 4]
        val empty = MatchGameSnapshot(
            sequence = 1, phase = "IN_MATCH", elapsedMatchMs = 250, remainingMs = 239_750,
            player1 = MatchPlayerVitals(500f, 400f), player2 = MatchPlayerVitals(500f, 400f),
            troops = emptyList(), towers = emptyList(), projectiles = emptyList(),
            result = null, viewerPlayerNumber = viewer
        )
        val state = mutableStateOf(empty)
        compose.setContent {
            FortRushTheme {
                BattleArenaScreen(map, viewer, "Prueba de render", gameState = state.value)
            }
        }
        val baseline = capture(viewer, "empty")
        val troops = listOf(
            MatchGameTroop("blue", 1, "espadachin", firstPoint, 60f, 60f),
            MatchGameTroop("red", 2, "escudero", secondPoint, 110f, 110f)
        )
        // Both sequence and elapsed time repeat: a deployment may occur in the same tick.
        compose.runOnIdle { state.value = empty.copy(troops = troops) }
        val deployed = capture(viewer, "deployed")
        for (troop in troops) {
            assertTrue("No se dibujó ${troop.card} del jugador ${troop.ownerPlayerNumber}",
                changedPixels(baseline, deployed, troop.point, viewer) > 40)
        }

        val movedTroops = troops.mapIndexed { index, troop ->
            troop.copy(point = map.path[map.path.lastIndex * (index + 2) / 5])
        }
        compose.runOnIdle { state.value = empty.copy(elapsedMatchMs = 500, troops = movedTroops) }
        // The production interpolation uses elapsedRealtime; let that transition finish.
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        SystemClock.sleep(1_050)
        val moved = capture(viewer, "moved")
        for (troop in movedTroops) {
            assertTrue("La tropa no avanzó hasta la posición recibida del servidor",
                changedPixels(baseline, moved, troop.point, viewer) > 40)
        }

        compose.runOnIdle { state.value = empty.copy(elapsedMatchMs = 750) }
        val removed = capture(viewer, "removed")
        for (troop in movedTroops) {
            assertTrue("Se sigue dibujando una tropa que ya desapareció del snapshot",
                changedPixels(baseline, removed, troop.point, viewer) < 5)
        }
    }

    private fun capture(viewer: Int, stage: String): ImageBitmap {
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("arenaEntities", useUnmergedTree = true).captureToImage()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = InstrumentationRegistry.getArguments().getString("renderRun", "current")
        val directory = File(context.getExternalFilesDir(null), "render-regression/$run").apply { mkdirs() }
        File(directory, "player-$viewer-$stage.png").outputStream().use {
            bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    private fun changedPixels(before: ImageBitmap, after: ImageBitmap, point: MatchPoint, viewer: Int): Int {
        val boardWidth = minOf(before.width.toFloat(), before.height * 9f / 16f)
        val x = (before.width - boardWidth) / 2f + (if (viewer == 2) 1f - point.x else point.x) * boardWidth
        val y = (if (viewer == 2) 1f - point.y else point.y) * before.height
        val radius = (boardWidth * 0.075f).roundToInt().coerceAtLeast(1)
        val original = before.toPixelMap()
        val rendered = after.toPixelMap()
        var count = 0
        for (py in (y.roundToInt() - radius).coerceAtLeast(0)..(y.roundToInt() + radius).coerceAtMost(before.height - 1)) {
            for (px in (x.roundToInt() - radius).coerceAtLeast(0)..(x.roundToInt() + radius).coerceAtMost(before.width - 1)) {
                if (original[px, py].toArgb() != rendered[px, py].toArgb()) count++
            }
        }
        return count
    }
}
