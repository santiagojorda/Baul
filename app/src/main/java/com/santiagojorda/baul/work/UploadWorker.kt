package com.santiagojorda.baul.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.santiagojorda.baul.BaulApplication
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.dao.UploadLogDao
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.data.local.toDomain
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.UploadLogEntry.Companion.MAX_RETRY_ATTEMPTS
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.domain.upload.UploadResult
import com.santiagojorda.baul.media.MediaMetadataReader
import com.santiagojorda.baul.upload.DriveUploader
import com.santiagojorda.baul.upload.GooglePhotosUploader
import com.santiagojorda.baul.upload.YouTubeUploader
import com.santiagojorda.baul.widget.SyncStatusWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ruleId = inputData.getLong(KEY_RULE_ID, -1L)
        val mediaUriString = inputData.getString(KEY_MEDIA_URI) ?: return Result.failure()
        if (ruleId == -1L) return Result.failure()

        val database = AppDatabase.getInstance(applicationContext)
        val rule = database.ruleDao().getRuleById(ruleId)?.toDomain() ?: return Result.failure()

        val mediaFile = MediaMetadataReader(applicationContext.contentResolver)
            .read(Uri.parse(mediaUriString))
            ?.mediaFile
            ?: return Result.failure()

        // Hay al menos un servicio de notificación esperando este ping antes de que empiece a
        // subir de verdad: el propio servicio decide cuándo pararse mirando Room, así que da
        // igual si varios Workers concurrentes lo piden a la vez.
        UploadNotificationService.start(applicationContext)

        val logDao = database.uploadLogDao()
        val existingLog = logDao.getLogForMedia(rule.id, mediaUriString)
        val startedAt = System.currentTimeMillis()
        val logId = logDao.upsert(
            UploadLogEntity(
                id = existingLog?.id ?: 0,
                ruleId = rule.id,
                mediaUri = mediaUriString,
                fileName = mediaFile.displayName,
                status = UploadStatus.UPLOADING,
                attemptCount = runAttemptCount,
                createdAt = existingLog?.createdAt ?: startedAt,
                updatedAt = startedAt,
                totalBytes = mediaFile.sizeBytes,
            ),
        )
        SyncStatusWidget().updateAll(applicationContext)

        val app = applicationContext as BaulApplication
        val destination = when (rule.destinationType) {
            DestinationType.YOUTUBE -> YouTubeUploader(
                applicationContext,
                app.connectedAccountRepository,
                app.googleAuthManager,
                app.youTubeQuotaTracker,
            )
            DestinationType.GOOGLE_PHOTOS -> GooglePhotosUploader(
                applicationContext,
                app.connectedAccountRepository,
                app.ruleRepository,
                app.googleAuthManager,
            )
            DestinationType.DRIVE -> DriveUploader()
        }

        val onProgress = throttledProgressReporter(logDao, logId, mediaFile.sizeBytes)

        // Cualquier excepción no contemplada por el uploader (una respuesta que no es el JSON
        // esperado, etc.) tiene que terminar igual en un log FAILED, no cortar antes de escribirlo.
        val uploadResult = try {
            destination.upload(mediaFile, rule, onProgress)
        } catch (e: Exception) {
            UploadResult.Failure(message = e.message ?: "Error inesperado: ${e::class.simpleName}", retryable = true)
        }

        // El borrado del original (si rule.deleteSourceAfterUpload) no se dispara acá: necesita
        // confirmación del sistema vía IntentSender, y este Worker no tiene Activity. En cambio,
        // se marca la subida como SUCCESS/sourceDeleted=false, y DeleteUploadedSourcesEffect (en
        // BaulApp) revisa los pendientes y pide la confirmación la próxima vez que se abre la app.

        // Mientras todavía queden reintentos disponibles para un fallo transitorio, el estado
        // queda PENDING ("reintentando solo") en vez de FAILED — recién se marca FAILED si el
        // error no es reintentable, o si ya se agotaron los reintentos. El errorMessage sí se
        // guarda en ambos casos, para que se pueda ver por qué está reintentando.
        val willRetry = uploadResult is UploadResult.Failure && uploadResult.retryable && runAttemptCount < MAX_RETRY_ATTEMPTS

        val finishedAt = System.currentTimeMillis()
        logDao.upsert(
            UploadLogEntity(
                id = logId,
                ruleId = rule.id,
                mediaUri = mediaUriString,
                fileName = mediaFile.displayName,
                status = when {
                    uploadResult is UploadResult.Success -> UploadStatus.SUCCESS
                    willRetry -> UploadStatus.PENDING
                    else -> UploadStatus.FAILED
                },
                errorMessage = (uploadResult as? UploadResult.Failure)?.message,
                remoteId = (uploadResult as? UploadResult.Success)?.remoteId,
                attemptCount = runAttemptCount + 1,
                createdAt = existingLog?.createdAt ?: startedAt,
                updatedAt = finishedAt,
                totalBytes = mediaFile.sizeBytes,
                bytesUploaded = if (uploadResult is UploadResult.Success) mediaFile.sizeBytes else 0,
            ),
        )
        SyncStatusWidget().updateAll(applicationContext)

        return when (uploadResult) {
            is UploadResult.Success -> Result.success()
            is UploadResult.Failure -> if (willRetry) Result.retry() else Result.failure()
        }
    }

    /**
     * [com.santiagojorda.baul.domain.upload.Destination.upload] llama a `onProgress` desde
     * código síncrono (loop de copia de bytes, callback Java del cliente de YouTube), no desde una
     * corrutina — no se puede llamar a un `suspend fun` del DAO ahí directo. En cambio, se lanza una
     * corrutina de "mejor esfuerzo" por cada tick que pasa el throttle (como mucho cada
     * [PROGRESS_UPDATE_INTERVAL_MS]), total o parcialmente perdible sin romper nada si el proceso
     * termina antes de que corra: el próximo tick (o el upsert final) va a pisarla igual.
     */
    private fun throttledProgressReporter(logDao: UploadLogDao, logId: Long, totalBytes: Long): (Long) -> Unit {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var lastReportedAt = 0L
        return { bytesSent ->
            val now = System.currentTimeMillis()
            if (now - lastReportedAt >= PROGRESS_UPDATE_INTERVAL_MS) {
                lastReportedAt = now
                scope.launch { logDao.updateProgress(logId, bytesSent, totalBytes, now) }
            }
        }
    }

    companion object {
        const val KEY_RULE_ID = "rule_id"
        const val KEY_MEDIA_URI = "media_uri"

        private const val PROGRESS_UPDATE_INTERVAL_MS = 400L
    }
}
