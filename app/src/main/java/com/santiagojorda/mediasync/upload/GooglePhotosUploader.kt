package com.santiagojorda.mediasync.upload

import android.content.Context
import com.santiagojorda.mediasync.auth.GoogleApiScopes
import com.santiagojorda.mediasync.auth.GoogleAuthManager
import com.santiagojorda.mediasync.auth.TokenResult
import com.santiagojorda.mediasync.data.repository.ConnectedAccountRepository
import com.santiagojorda.mediasync.data.repository.RuleRepository
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.upload.Destination
import com.santiagojorda.mediasync.domain.upload.MediaFile
import com.santiagojorda.mediasync.domain.upload.UploadResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Habla directo por REST con la Photos Library API (scope `photoslibrary.appendonly`) en vez de
 * usar un cliente Java oficial: Google no publica uno liviano apto para Android para esta API
 * (el `google-photos-library-client` oficial es para server/desktop, trae gRPC y de más).
 *
 * Flujo de 2-3 llamadas: subir los bytes crudos -> (opcional) resolver/crear álbum -> mediaItems:batchCreate.
 * La subida de bytes corre en paralelo entre archivos sin problema; solo "¿existe el álbum? si no,
 * crearlo" se serializa con [albumMutex] (releyendo la regla al tomar el lock) para que dos subidas
 * concurrentes de la misma regla no terminen creando dos álbumes iguales.
 */
class GooglePhotosUploader(
    private val context: Context,
    private val connectedAccountRepository: ConnectedAccountRepository,
    private val ruleRepository: RuleRepository,
    private val authManager: GoogleAuthManager,
) : Destination {

    override suspend fun upload(file: MediaFile, rule: Rule): UploadResult = withContext(Dispatchers.IO) {
        if (connectedAccountRepository.getByEmail(rule.googleAccountEmail) == null) {
            return@withContext UploadResult.Failure(
                message = "La cuenta ${rule.googleAccountEmail} ya no está conectada",
                retryable = false,
            )
        }

        val accessToken = when (val tokenResult = authManager.getFreshAccessToken(rule.googleAccountEmail, GoogleApiScopes.ALL)) {
            is TokenResult.Success -> tokenResult.accessToken
            is TokenResult.NeedsReauth -> return@withContext UploadResult.Failure(
                message = "La cuenta ${rule.googleAccountEmail} necesita que la reautorices a mano (abrí la app y reconectala)",
                retryable = false,
            )
            is TokenResult.Failure -> return@withContext UploadResult.Failure(tokenResult.message, tokenResult.retryable)
        }

        try {
            val uploadToken = uploadBytes(file, accessToken)
            val albumId = resolveAlbumId(rule, accessToken)
            val mediaItemId = createMediaItem(uploadToken, albumId, accessToken)
            UploadResult.Success(remoteId = mediaItemId)
        } catch (e: PhotosApiException) {
            UploadResult.Failure(message = e.message ?: "Error de la Photos Library API", retryable = e.retryable)
        } catch (e: IOException) {
            UploadResult.Failure(message = e.message ?: "Error de red subiendo a Google Photos", retryable = true)
        } catch (e: JSONException) {
            UploadResult.Failure(message = "Respuesta inesperada de la Photos Library API: ${e.message}", retryable = true)
        }
    }

    private suspend fun resolveAlbumId(rule: Rule, accessToken: String): String? {
        val albumName = rule.googlePhotosMetadata?.albumName
        if (albumName.isNullOrBlank()) return rule.googlePhotosMetadata?.albumId

        return albumMutex.withLock {
            // Releer de la base al tomar el lock: otra subida concurrente de esta misma regla
            // puede haber creado y persistido el álbum mientras esperábamos acá.
            val freshRule = ruleRepository.getRuleById(rule.id)
            val freshAlbumId = freshRule?.googlePhotosMetadata?.albumId
            if (freshAlbumId != null) return@withLock freshAlbumId

            val createdId = createAlbum(albumName, accessToken)
            freshRule?.let {
                ruleRepository.save(it.copy(googlePhotosMetadata = it.googlePhotosMetadata?.copy(albumId = createdId)))
            }
            createdId
        }
    }

    private fun uploadBytes(file: MediaFile, accessToken: String): String {
        val connection = (URL(UPLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("X-Goog-Upload-Content-Type", file.mimeType)
            setRequestProperty("X-Goog-Upload-Protocol", "raw")
            setFixedLengthStreamingMode(file.sizeBytes)
        }
        try {
            val input = context.contentResolver.openInputStream(file.uri)
                ?: throw PhotosApiException("No se pudo abrir ${file.displayName}", retryable = false)
            input.use { it.copyTo(connection.outputStream) }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw PhotosApiException("Fallo la subida de bytes (HTTP $code): ${connection.readError()}", retryable = code >= 500)
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun createAlbum(name: String, accessToken: String): String {
        val body = JSONObject().put("album", JSONObject().put("title", name))
        val response = postJson(ALBUMS_URL, body, accessToken)
        return response.getString("id")
    }

    private fun createMediaItem(uploadToken: String, albumId: String?, accessToken: String): String {
        val newMediaItem = JSONObject().put("simpleMediaItem", JSONObject().put("uploadToken", uploadToken))
        val body = JSONObject().apply {
            albumId?.let { put("albumId", it) }
            put("newMediaItems", JSONArray().put(newMediaItem))
        }
        val response = postJson(BATCH_CREATE_URL, body, accessToken)
        val result = response.getJSONArray("newMediaItemResults").getJSONObject(0)
        val statusCode = result.optJSONObject("status")?.optInt("code", 0) ?: 0
        if (statusCode != 0) {
            val message = result.optJSONObject("status")?.optString("message")
            throw PhotosApiException(message ?: "La Photos Library API rechazó el ítem", retryable = false)
        }
        return result.getJSONObject("mediaItem").getString("id")
    }

    private fun postJson(url: String, body: JSONObject, accessToken: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw PhotosApiException("Error HTTP $code en ${url.substringAfterLast('/')}: ${connection.readError()}", retryable = code >= 500)
            }
            return JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.readError(): String? = errorStream?.bufferedReader()?.readText()

    private class PhotosApiException(message: String, val retryable: Boolean) : Exception(message)

    private companion object {
        const val UPLOAD_URL = "https://photoslibrary.googleapis.com/v1/uploads"
        const val ALBUMS_URL = "https://photoslibrary.googleapis.com/v1/albums"
        const val BATCH_CREATE_URL = "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate"

        /** Comparte el lock entre todas las instancias/Workers del proceso: la creación de un
         *  álbum es rara y barata, no hace falta un lock por regla. */
        val albumMutex = Mutex()
    }
}
