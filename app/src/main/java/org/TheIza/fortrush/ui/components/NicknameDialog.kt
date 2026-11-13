package org.TheIza.fortrush.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.TheIza.fortrush.R
import org.TheIza.fortrush.data.nickname.NicknameApiException
import org.TheIza.fortrush.data.nickname.NicknameProfile
import org.TheIza.fortrush.data.nickname.NicknameRepository
import org.TheIza.fortrush.data.nickname.NicknameValidation
import org.TheIza.fortrush.data.nickname.NicknamePolicy

@Composable
fun NicknameDialog(
    title: String,
    repository: NicknameRepository,
    dismissible: Boolean,
    onSaved: (NicknameProfile) -> Unit,
    onDismiss: () -> Unit = {}
) {
    var value by remember { mutableStateOf("") }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var availabilityRetry by remember { mutableStateOf(0) }
    val validation = NicknamePolicy.validate(value)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(value, validation, availabilityRetry) {
        available = null
        serverError = null
        val valid = validation as? NicknameValidation.Valid ?: return@LaunchedEffect
        delay(350)
        checking = true
        try {
            available = repository.isAvailable(valid.nickname)
        } catch (error: Exception) {
            serverError = error.message ?: "No se pudo comprobar el apodo."
        } finally {
            checking = false
        }
    }

    Dialog(
        onDismissRequest = { if (dismissible && !saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible)
    ) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.92f)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(24.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 440.dp), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.panel_dialogo), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 35.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(title, color = Color(0xFFFFE7A7), fontWeight = FontWeight.Black, fontSize = 21.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Text("3–16 caracteres: letras, números y _", color = Color(0xFFE9E2C8), fontSize = 12.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.marco_apodo), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            BasicTextField(
                                value = value,
                                onValueChange = { value = it.take(32) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).focusRequester(focusRequester).testTag("nicknameInput"),
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus() }),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.Center) {
                                        if (value.isEmpty()) Text("Escribe tu apodo", color = Color(0xFFDED0B1), fontSize = 15.sp)
                                        inner()
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        val validationMessage = when (val result = validation) {
                            is NicknameValidation.Valid -> when {
                                checking -> "Comprobando disponibilidad…"
                                serverError != null -> serverError
                                available == true -> "¡Ese apodo está libre!"
                                available == false -> "Ese apodo ya está en uso."
                                else -> ""
                            }
                            is NicknameValidation.Invalid -> when (result.error) {
                                org.TheIza.fortrush.data.nickname.NicknameError.REQUIRED -> "Escribe un apodo."
                                org.TheIza.fortrush.data.nickname.NicknameError.TOO_SHORT -> "Debe tener al menos 3 caracteres."
                                org.TheIza.fortrush.data.nickname.NicknameError.TOO_LONG -> "No puede superar los 16 caracteres."
                                org.TheIza.fortrush.data.nickname.NicknameError.INVALID_CHARACTERS -> "Usa letras, tildes, ñ, ü, números o _."
                            }
                        }
                        Text(validationMessage.orEmpty(), color = when {
                            serverError != null || available == false || validation is NicknameValidation.Invalid -> Color(0xFFFFB2A4)
                            available == true -> Color(0xFFBDF0A5)
                            else -> Color(0xFFE9E2C8)
                        }, fontSize = 12.sp, minLines = 1, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (serverError != null && available == null && validation is NicknameValidation.Valid && !checking) {
                            Text(
                                "Volver a comprobar",
                                modifier = Modifier.clickable { availabilityRetry++ }.padding(5.dp),
                                color = Color(0xFFFFE3A0), fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        FortRushImageButton(
                            label = if (saving) "GUARDANDO…" else "CONTINUAR",
                            resource = R.drawable.boton_principal,
                            enabled = validation is NicknameValidation.Valid && available == true && !checking && !saving,
                            onClick = {
                                if (saving) return@FortRushImageButton
                                saving = true
                                keyboard?.hide()
                                focusManager.clearFocus()
                                scope.launch {
                                    try {
                                        onSaved(repository.saveNickname(value))
                                    } catch (error: NicknameApiException) {
                                        serverError = when (error.code) {
                                            "NICKNAME_TAKEN" -> { available = false; "Ese apodo acaba de ser reservado. Elige otro." }
                                            "NICKNAME_COOLDOWN" -> "Aún no puedes cambiar tu apodo."
                                            else -> error.message
                                        }
                                    } catch (error: Exception) {
                                        serverError = error.message ?: "No se pudo guardar el apodo. Revisa la conexión e inténtalo de nuevo."
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        )
                    }
                }
                if (dismissible) {
                    Spacer(Modifier.height(4.dp))
                    FortRushImageButton("CANCELAR", R.drawable.boton_secundario, true, onDismiss)
                }
            }
        }
    }

}

@Composable
fun NicknameNoticeDialog(title: String, message: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.9f).height(230.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.panel_dialogo), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(title, color = Color(0xFFFFE7A7), fontWeight = FontWeight.Black, fontSize = 21.sp, textAlign = TextAlign.Center)
                    Text(message, color = Color(0xFFE9E2C8), fontSize = 15.sp, textAlign = TextAlign.Center)
                    FortRushImageButton("ENTENDIDO", R.drawable.boton_principal, true, onDismiss)
                }
            }
        }
    }
}

@Composable
fun NicknameConnectionErrorDialog(message: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.92f).heightIn(min = 260.dp, max = 400.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.panel_dialogo), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("No se pudo conectar", color = Color(0xFFFFE7A7), fontWeight = FontWeight.Black, fontSize = 21.sp, textAlign = TextAlign.Center)
                    Text(message, color = Color(0xFFE9E2C8), fontSize = 14.sp, textAlign = TextAlign.Center)
                    FortRushImageButton("REINTENTAR", R.drawable.boton_principal, true, onRetry)
                    FortRushImageButton("CERRAR SESIÓN", R.drawable.boton_secundario, true, onSignOut)
                }
            }
        }
    }
}

@Composable
private fun FortRushImageButton(label: String, resource: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth(0.78f).height(44.dp).clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center
    ) {
        Image(painterResource(resource), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = if (enabled) 1f else 0.55f)
        Text(label, color = Color(0xFF503018), fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
    }
}
