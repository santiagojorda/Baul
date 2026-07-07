package com.santiagojorda.mediasync.upload

import android.content.Context
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.PlaylistItem
import com.google.api.services.youtube.model.PlaylistItemSnippet
import com.google.api.services.youtube.model.ResourceId
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoSnippet
import com.google.api.services.youtube.model.VideoStatus
import com.santiagojorda.mediasync.auth.GoogleApiScopes
import com.santiagojorda.mediasync.auth.GoogleAuthManager
import com.santiagojorda.mediasync.auth.TokenResult
import com.santiagojorda.mediasync.data.repository.ConnectedAccountRepository
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.upload.Destination
import com.santiagojorda.mediasync.domain.upload.MediaFile
import com.santiagojorda.mediasync.domain.upload.UploadResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sube el video a YouTube Data API v3 (subida resumible, la maneja el `MediaHttpUploader` del
 * cliente oficial). Cada archivo se sube como un video individual y separado — no arma un solo
 * video combinado a partir de varios (eso lo hace, a mano, el editor de resumen con Media3).
 *
 * `channelId` de la regla no fuerza a qué canal se sube: la YouTube Data API sube al canal que
 * corresponde a la cuenta autenticada, no hay forma simple de elegir uno de varios canales de la
 * misma cuenta (Brand Accounts) sin mecanismos de Content Owner que no aplican acá. Si la cuenta
 * de Google tiene un solo canal, no hay ambigüedad.
 */
class YouTubeUploader(
    private val context: Context,
    private val connectedAccountRepository: ConnectedAccountRepository,
    private val authManager: GoogleAuthManager,
    private val quotaTracker: YouTubeQuotaTracker,
) : Destination {

    override suspend fun upload(file: MediaFile, rule: Rule): UploadResult = withContext(Dispatchers.IO) {
        val metadata = rule.youTubeMetadata
            ?: return@withContext UploadResult.Failure("La regla no tiene metadata de YouTube", retryable = false)

        if (connectedAccountRepository.getByEmail(rule.googleAccountEmail) == null) {
            return@withContext UploadResult.Failure(
                message = "La cuenta ${rule.googleAccountEmail} ya no está conectada",
                retryable = false,
            )
        }

        // Cortar ANTES de intentar: si ya estamos cerca del límite diario, no tiene sentido gastar
        // la llamada para enterarnos recién con el 403 de Google. Reintentable: al otro día (o
        // cuando resetee la cuota) hay lugar de nuevo.
        if (!quotaTracker.hasRoomForUpload()) {
            return@withContext UploadResult.Failure(
                message = "Cerca del límite diario de cuota de YouTube, se reintenta más tarde",
                retryable = true,
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
            val youtube = buildClient(accessToken)

            val video = Video().apply {
                snippet = VideoSnippet().apply {
                    title = file.displayName
                    tags = metadata.tags
                    categoryId = DEFAULT_CATEGORY_ID
                }
                status = VideoStatus().apply {
                    privacyStatus = metadata.privacyStatus.name.lowercase()
                }
            }

            val inputStream = context.contentResolver.openInputStream(file.uri)
                ?: return@withContext UploadResult.Failure("No se pudo abrir ${file.displayName}", retryable = false)
            val mediaContent = InputStreamContent(file.mimeType, inputStream).apply { length = file.sizeBytes }

            val insertedVideo = youtube.videos()
                .insert(listOf("snippet", "status"), video, mediaContent)
                .execute()

            quotaTracker.recordUpload()
            metadata.playlistId?.let { playlistId -> addToPlaylist(youtube, playlistId, insertedVideo.id) }

            UploadResult.Success(remoteId = insertedVideo.id)
        } catch (e: GoogleJsonResponseException) {
            val isQuotaExceeded = e.details?.errors.orEmpty().any { it.reason == "quotaExceeded" || it.reason == "dailyLimitExceeded" }
            UploadResult.Failure(
                message = e.details?.message ?: e.message ?: "YouTube rechazó la subida",
                // La cuota se resetea ~cada 24h; con el backoff exponencial de WorkManager (tope
                // 5h entre reintentos) eventualmente pasa sin necesidad de calcular el reset exacto.
                retryable = e.statusCode >= 500 || isQuotaExceeded,
            )
        } catch (e: IOException) {
            UploadResult.Failure(message = e.message ?: "Error de red subiendo a YouTube", retryable = true)
        }
    }

    private fun addToPlaylist(youtube: YouTube, playlistId: String, videoId: String) {
        val item = PlaylistItem().apply {
            snippet = PlaylistItemSnippet().apply {
                this.playlistId = playlistId
                resourceId = ResourceId().apply {
                    kind = "youtube#video"
                    this.videoId = videoId
                }
            }
        }
        youtube.playlistItems().insert(listOf("snippet"), item).execute()
    }

    private fun buildClient(accessToken: String): YouTube {
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }
        return YouTube.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    private companion object {
        const val APPLICATION_NAME = "MediaSync"
        const val DEFAULT_CATEGORY_ID = "22" // People & Blogs; la app no le pide categoría al usuario
    }
}
