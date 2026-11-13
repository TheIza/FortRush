package org.TheIza.fortrush.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.TheIza.fortrush.data.nickname.NicknameRepository
import org.TheIza.fortrush.ui.components.NicknameDialog
import org.TheIza.fortrush.ui.theme.FortRushTheme

class NicknameDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun mandatoryPanelUsesFortRushCopyAndBlocksInvalidNickname() {
        compose.setContent {
            FortRushTheme {
                NicknameDialog(
                    title = "¡Hola, guerrero! ¿Cuál es tu apodo?",
                    repository = NicknameRepository("http://127.0.0.1:1", { "test-token" }),
                    dismissible = false,
                    onSaved = {}
                )
            }
        }

        compose.onNodeWithText("¡Hola, guerrero! ¿Cuál es tu apodo?").assertIsDisplayed()
        compose.onNodeWithText("3–16 caracteres: letras, números y _").assertIsDisplayed()
        val field = compose.onNodeWithTag("nicknameInput")
        field.performTextInput("ab")
        compose.onNodeWithText("Debe tener al menos 3 caracteres.").assertIsDisplayed()
        compose.onNodeWithText("CONTINUAR").assertIsNotEnabled()
        field.performTextClearance()
        field.performTextInput("No espacios")
        compose.onNodeWithText("Usa letras, tildes, ñ, ü, números o _.").assertIsDisplayed()
        compose.onNodeWithText("CONTINUAR").assertIsNotEnabled()
    }
}
