package com.santiagojorda.mediasync.upload

import android.content.Context
import com.santiagojorda.mediasync.data.repository.ConnectedAccountRepository
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.upload.Destination
import com.santiagojorda.mediasync.domain.upload.MediaFile
import com.santiagojorda.mediasync.domain.upload.UploadResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Habla directo por REST con la Photos Library API (scope `photoslibrary.appendonly`) en vez de
 * usar un cliente Java oficial: Google no publica uno liviano apto para Android para esta API
 * (el `google-photos-library-client` oficial es para server/desktop, trae gRPC y de más).
 *
 * Flujo de 2-3 llamadas: subir los bytes crudos -> (opcional) crear álbum si hace falta ->
 * mediaItems:batchCreate referenciando el upload token.
 */
class GooglePhotosUploader(
    private val context: Context,
    private val connectedAccountRepository: ConnectedAccountRepository,
) : Destination {

    override suspend fun upload(file: MediaFile, rule: Rule): UploadResult = withContext(Dispatchers.IO) {
        val account = connectedAccountRepository.getByEmail(rule.googleAccountEmail)
        val accessToken = account?.accessToken

        if (account == null || accessToken == null || !account.hasValidToken()) {
            return@withContext UploadResult.Failure(
                message = "La cuenta ${rule.googleAccountEmail} no tiene un acceso válido, hay que reconectarla",
                retryable = false,
            )
        }

        try {
            val uploadToken = uploadBytes(file, accessToken)

            val existingAlbumId = rule.googlePhotosMetadata?.albumId
            val albumName = rule.googlePhotosMetadata?.albumName
            val albumId = when {
                existingAlbumId != null -> existingAlbumId
                !albumName.isNullOrBlank() -> createAlbum(albumName, accessToken)
                else -> null
            }

            val mediaItemId = createMediaItem(uploadToken, albumId, accessToken)
            val discoveredAlbumId = if (existingAlbumId == null && albumId != null) albumId else null
            UploadResult.Success(remoteId = mediaItemId, remoteAlbumId = discoveredAlbumId)
        } catch (e: PhotosApiException) {
            UploadResult.Failure(message = e.message ?: "Error de la Photos Library API", retryable = e.retryable)
        } catch (e: IOException) {
            UploadResult.Failure(message = e.message ?: "Error de red subiendo a Google Photos", retryable = true)
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
    }
}
