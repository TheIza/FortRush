package org.TheIza.fortrush.data.nickname

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.TheIza.fortrush.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NicknameApiException(val code: String, message: String, val remainingMs: Long = 0L) : Exception(message)

data class NicknameProfile(
    val nickname: String?,
    val normalized: String?,
    val lastChangedAt: String?,
    val changeRemainingMs: Long,
    val changeAvailableAt: String?
)

class NicknameRepository(
    private val baseUrl: String = BuildConfig.FORT_RUSH_API_BASE_URL,
    private val tokenProvider: suspend () -> String = { currentFirebaseToken() }
) {
    suspend fun loadProfile(): NicknameProfile = request("GET", "/api/profile/me").toProfile()

    suspend fun isAvailable(nickname: String): Boolean {
        val encoded = URLEncoder.encode(nickname, StandardCharsets.UTF_8.name())
        return request("GET", "/api/nicknames/availability?nickname=$encoded").optBoolean("available", false)
    }

    suspend fun saveNickname(nickname: String): NicknameProfile =
        request("PUT", "/api/profile/nickname", JSONObject().put("nickname", nickname)).toProfile()

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val token = tokenProvider()
        return withContext(Dispatchers.IO) {
            val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                // Render Free may need about a minute to wake after 15 minutes idle.
                connectTimeout = 20_000
                readTimeout = 75_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = if (response.isBlank()) JSONObject() else JSONObject(response)
                if (status !in 200..299) {
                    throw NicknameApiException(
                        json.optString("error", "REQUEST_FAILED"),
                        json.optString("message", "No se pudo completar la operación."),
                        json.optLong("remainingMs", 0L)
                    )
                }
                json
            } catch (error: NicknameApiException) {
                throw error
            } catch (error: Exception) {
                throw IOException(
                    "No se pudo conectar con el servidor de FortRush. Si estaba inactivo, puede tardar hasta un minuto en despertar; comprueba tu conexión y pulsa Reintentar.",
                    error
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun JSONObject.toProfile() = NicknameProfile(
        nickname = optString("nickname").takeIf { it.isNotBlank() && it != "null" },
        normalized = optString("nicknameNormalized").takeIf { it.isNotBlank() && it != "null" },
        lastChangedAt = optString("lastNicknameChangeAt").takeIf { it.isNotBlank() && it != "null" },
        changeRemainingMs = optLong("nicknameChangeRemainingMs", 0L),
        changeAvailableAt = optString("nicknameChangeAvailableAt").takeIf { it.isNotBlank() && it != "null" }
    )

    private companion object {
        suspend fun currentFirebaseToken(): String = suspendCancellableCoroutine { continuation ->
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                continuation.resumeWithException(NicknameApiException("AUTH_REQUIRED", "Inicia sesión para continuar."))
                return@suspendCancellableCoroutine
            }
            user.getIdToken(false)
                .addOnSuccessListener { result ->
                    val token = result.token
                    if (token.isNullOrBlank()) continuation.resumeWithException(IOException("No se pudo obtener el token de sesión."))
                    else continuation.resume(token)
                }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }
}
