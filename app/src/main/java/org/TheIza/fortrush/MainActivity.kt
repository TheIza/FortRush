package org.TheIza.fortrush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import org.TheIza.fortrush.ui.FortRushApp
import org.TheIza.fortrush.ui.Screen.WelcomeScreen
import org.TheIza.fortrush.ui.theme.FortRushTheme

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var loginStatus by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            FortRushTheme {
                var signedIn by remember { mutableStateOf(auth.currentUser != null) }
                DisposableEffect(auth) {
                    val listener = FirebaseAuth.AuthStateListener { signedIn = it.currentUser != null }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }
                val uid = auth.currentUser?.uid.orEmpty()
                if (signedIn && uid.isNotBlank()) {
                    FortRushApp(userId = uid, onSignOut = {
                        loginStatus = ""
                        auth.signOut()
                    })
                } else {
                    WelcomeScreen(onGoogleSignInClick = ::startGoogleSignIn, loginStatus = loginStatus)
                }
            }
        }
    }

    private fun startGoogleSignIn() {
        loginStatus = getString(R.string.login_in_progress)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        lifecycleScope.launch {
            try {
                val result = CredentialManager.create(this@MainActivity).getCredential(this@MainActivity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    signInWithFirebase(googleCredential.idToken)
                } else {
                    loginStatus = getString(R.string.login_error_credential)
                }
            } catch (_: GoogleIdTokenParsingException) {
                loginStatus = getString(R.string.login_error_credential)
            } catch (exception: Exception) {
                loginStatus = exception.message ?: getString(R.string.login_cancelled)
            }
        }
    }

    private fun signInWithFirebase(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                loginStatus = if (result.user != null) "" else getString(R.string.login_error_user)
            }
            .addOnFailureListener { loginStatus = getString(R.string.login_error_firebase) }
    }
}
