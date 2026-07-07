package com.santiagojorda.mediasync.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface SignInResult {
    data class Success(val email: String, val displayName: String?) : SignInResult
    data class Failure(val message: String) : SignInResult
}

sealed interface TokenResult {
    data class Success(val accessToken: String) : TokenResult
    /** Necesita que el usuario reautorice a mano (acceso revocado, contraseña cambiada, etc). */
    data class NeedsReauth(val intent: Intent?) : TokenResult
    data class Failure(val message: String, val retryable: Boolean) : TokenResult
}

/**
 * Usa `GoogleSignInClient` + `GoogleAuthUtil` (la API "clásica", integrada al `AccountManager`
 * de Android) en vez de Credential Manager + la Authorization API de Play Services. Se decidió
 * así a propósito: esa combinación moderna nunca permite renovar el token sin una Activity, ni
 * siquiera en silencio, así que no sirve para una app de sync desatendida en background.
 * `GoogleAuthUtil.getToken` sí puede pedir un token fresco desde un Worker sin ninguna pantalla,
 * salvo el caso raro de que el usuario haya revocado el acceso (ahí sí hace falta reautorizar).
 *
 * Google marca GoogleSignInClient/GoogleAuthUtil como deprecados a favor de Credential Manager +
 * AuthorizationClient; el @Suppress de acá abajo es a propósito, no un descuido.
 */
@Suppress("DEPRECATION")
class GoogleAuthManager(private val context: Context) {

    private fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(GoogleApiScopes.YOUTUBE_UPLOAD),
                Scope(GoogleApiScopes.DRIVE_FILE),
                Scope(GoogleApiScopes.PHOTOS_APPEND_ONLY),
            )
            .build()

    fun signInIntent(activity: Activity): Intent =
        GoogleSignIn.getClient(activity, signInOptions()).signInIntent

    suspend fun handleSignInResult(data: Intent?): SignInResult {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
            val email = account.email
            if (email == null) {
                SignInResult.Failure("Google no devolvió un email para esta cuenta")
            } else {
                SignInResult.Success(email = email, displayName = account.displayName)
            }
        } catch (e: ApiException) {
            SignInResult.Failure(e.message ?: "No se pudo iniciar sesión (código ${e.statusCode})")
        }
    }

    /** Se puede llamar desde cualquier lado, incluido un Worker en background: no necesita Activity. */
    suspend fun getFreshAccessToken(email: String, scopes: Set<String> = GoogleApiScopes.ALL): TokenResult =
        withContext(Dispatchers.IO) {
            val scopeString = "oauth2:" + scopes.joinToString(" ")
            try {
                val token = GoogleAuthUtil.getToken(context, email, scopeString)
                TokenResult.Success(token)
            } catch (e: UserRecoverableAuthException) {
                TokenResult.NeedsReauth(e.intent)
            } catch (e: GoogleAuthException) {
                TokenResult.Failure(e.message ?: "Error de autenticación con Google", retryable = false)
            } catch (e: IOException) {
                TokenResult.Failure(e.message ?: "Error de red autenticando con Google", retryable = true)
            }
        }
}
