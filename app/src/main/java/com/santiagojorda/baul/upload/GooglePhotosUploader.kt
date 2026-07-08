package com.santiagojorda.baul.upload

import android.content.Context
import com.santiagojorda.baul.auth.GoogleApiScopes
import com.santiagojorda.baul.auth.GoogleAuthManager
import com.santiagojorda.baul.auth.TokenResult
import com.santiagojorda.baul.data.repository.ConnectedAccountRepository
import com.santiagojorda.baul.data.repository.RuleRepository
import com.santiagojorda.baul.domain.model.Rule
import com.santiagojorda.baul.domain.upload.Destination
import com.santiagojorda.baul.domain.upload.MediaFile
import com.santiagojorda.baul.domain.upload.UploadResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
 * Cuántos archivos a la vez lo decide [AdaptiveWriteLimiter] (la API tiene un límite de escrituras
 * concurrentes por cuenta que Google no publica). Dentro de eso, "¿existe el álbum? si no,
 * crearlo" además se serializa con [albumMutex] (releyendo la regla al tomar el lock) para que dos
 * subidas de la misma regla no terminen creando dos álbumes iguales.
 */
class GooglePhotosUploader(
    private val context: Context,
    private val connectedAccountRepository: ConnectedAccountRepository,
    private val ruleRepository: RuleRepository,
    private val authManager: GoogleAuthManager,
) : Destination {

    override suspend fun upload(file: MediaFile, rule: Rule, onProgress: (bytesSent: Long) -> Unit): UploadResult = withContext(Dispatchers.IO) {
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
            // Google no publica el límite real de "concurrent write requests" (hay reportes de
            // otras apps pisando esta cuota incluso con una sola escritura a la vez y 20s de
            // espacio -> github.com/rclone/rclone/issues/6920), así que en vez de adivinar un
            // número fijo, AdaptiveWriteLimiter arranca conservador y va probando de a poco.
            AdaptiveWriteLimiter.withSlot {
                val uploadToken = uploadBytes(file, accessToken, onProgress)
                val albumId = resolveAlbumId(rule, accessToken)
                val mediaItemId = createMediaItem(uploadToken, albumId, accessToken)
                UploadResult.Success(remoteId = mediaItemId)
            }.also { AdaptiveWriteLimiter.onResult(quotaExceeded = false) }
        } catch (e: PhotosApiException) {
            AdaptiveWriteLimiter.onResult(quotaExceeded = e.isQuota)
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

            // Dos reglas distintas (dos carpetas) pueden pedir el mismo nombre de álbum: buscar
            // primero por nombre entre los álbumes que ya creó esta app (el scope appendonly solo
            // deja ver esos) antes de crear uno nuevo, para no terminar con dos álbumes iguales.
            val resolvedId = findExistingAlbumId(albumName, accessToken) ?: createAlbum(albumName, accessToken)
            freshRule?.let {
                ruleRepository.save(it.copy(googlePhotosMetadata = it.googlePhotosMetadata?.copy(albumId = resolvedId)))
            }
            resolvedId
        }
    }

    private fun findExistingAlbumId(name: String, accessToken: String): String? {
        var pageToken: String? = null
        do {
            val url = buildString {
                append(ALBUMS_URL)
                append("?pageSize=50")
                if (pageToken != null) append("&pageToken=").append(pageToken)
            }
            val response = getJson(url, accessToken)
            val albums = response.optJSONArray("albums")
            if (albums != null) {
                for (i in 0 until albums.length()) {
                    val album = albums.getJSONObject(i)
                    if (album.optString("title") == name) return album.getString("id")
                }
            }
            pageToken = response.optString("nextPageToken").ifEmpty { null }
        } while (pageToken != null)
        return null
    }

    private fun uploadBytes(file: MediaFile, accessToken: String, onProgress: (bytesSent: Long) -> Unit): String {
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
            input.use { it.copyToWithProgress(connection.outputStream, onProgress) }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw PhotosApiException(
                    "Fallo la subida de bytes (HTTP $code): ${connection.readError()}",
                    retryable = isRetryableHttpCode(code),
                    isQuota = code == 429,
                )
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
            val isQuotaError = message?.contains("quota", ignoreCase = true) == true
            throw PhotosApiException(message ?: "La Photos Library API rechazó el ítem", retryable = isQuotaError, isQuota = isQuotaError)
        }
        return result.getJSONObject("mediaItem").getString("id")
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw PhotosApiException(
                    "Error HTTP $code en ${url.substringAfterLast('/')}: ${connection.readError()}",
                    retryable = isRetryableHttpCode(code),
                    isQuota = code == 429,
                )
            }
            return JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
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
                throw PhotosApiException(
                    "Error HTTP $code en ${url.substringAfterLast('/')}: ${connection.readError()}",
                    retryable = isRetryableHttpCode(code),
                    isQuota = code == 429,
                )
            }
            return JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    /** Como [java.io.InputStream.copyTo] pero reportando cuánto se copió hasta ahora en cada bloque. */
    private fun java.io.InputStream.copyToWithProgress(out: java.io.OutputStream, onProgress: (bytesSent: Long) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalSent = 0L
        var bytesRead = read(buffer)
        while (bytesRead >= 0) {
            out.write(buffer, 0, bytesRead)
            totalSent += bytesRead
            onProgress(totalSent)
            bytesRead = read(buffer)
        }
    }

    private fun HttpURLConnection.readError(): String? = errorStream?.bufferedReader()?.readText()

    /** 429 (rate limit / cuota) es transitorio igual que un 5xx: vale la pena reintentar con backoff. */
    private fun isRetryableHttpCode(code: Int): Boolean = code >= 500 || code == 429

    private class PhotosApiException(message: String, val retryable: Boolean, val isQuota: Boolean = false) : Exception(message)

    private companion object {
        const val UPLOAD_URL = "https://photoslibrary.googleapis.com/v1/uploads"
        const val ALBUMS_URL = "https://photoslibrary.googleapis.com/v1/albums"
        const val BATCH_CREATE_URL = "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate"

        /** Comparte el lock entre todas las instancias/Workers del proceso: la creación de un
         *  álbum es rara y barata, no hace falta un lock por regla. */
        val albumMutex = Mutex()
    }
}

/**
 * Cuántas escrituras a la Photos Library API corren en paralelo, pero adaptativo en vez de un
 * número fijo: Google no publica el límite real de "concurrent write requests" por cuenta, y en
 * este mismo uploader ya se había probado con 3 permisos fijos y seguía pisando la cuota (por eso
 * había quedado en 1). En vez de adivinar de nuevo, arranca conservador (1) y sube de a uno cada
 * vez que junta [SUCCESSES_TO_GROW] escrituras seguidas sin error de cuota, hasta [MAX_CONCURRENCY]
 * como techo duro. Ante cualquier error de cuota baja de a uno (nunca por debajo de 1) y reinicia
 * el contador — así encuentra el límite real del momento en vez de tener que hardcodear uno.
 */
internal object AdaptiveWriteLimiter {
    internal const val MAX_CONCURRENCY = 3
    internal const val SUCCESSES_TO_GROW = 8

    private val gate = Semaphore(permits = MAX_CONCURRENCY)
    private val stateLock = Mutex()
    private var softLimit = 1
    private var active = 0
    private var consecutiveSuccesses = 0

    /** Solo para tests: el estado es de un `object` (singleton del proceso), así que cada test
     *  necesita arrancar de un punto conocido en vez de depender del orden de ejecución. */
    internal suspend fun resetForTesting() {
        stateLock.withLock {
            softLimit = 1
            active = 0
            consecutiveSuccesses = 0
        }
    }

    internal suspend fun currentSoftLimit(): Int = stateLock.withLock { softLimit }

    suspend fun <T> withSlot(block: suspend () -> T): T {
        waitForSlot()
        return gate.withPermit {
            stateLock.withLock { active++ }
            try {
                block()
            } finally {
                stateLock.withLock { active-- }
            }
        }
    }

    private suspend fun waitForSlot() {
        while (!stateLock.withLock { active < softLimit }) {
            delay(200)
        }
    }

    suspend fun onResult(quotaExceeded: Boolean) {
        stateLock.withLock {
            if (quotaExceeded) {
                softLimit = (softLimit - 1).coerceAtLeast(1)
                consecutiveSuccesses = 0
            } else {
                consecutiveSuccesses++
                if (consecutiveSuccesses >= SUCCESSES_TO_GROW && softLimit < MAX_CONCURRENCY) {
                    softLimit++
                    consecutiveSuccesses = 0
                }
            }
        }
    }
}
