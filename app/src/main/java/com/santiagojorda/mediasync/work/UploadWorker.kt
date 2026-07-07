package com.santiagojorda.mediasync.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.santiagojorda.mediasync.data.local.AppDatabase
import com.santiagojorda.mediasync.data.local.entity.UploadLogEntity
import com.santiagojorda.mediasync.data.local.toDomain
import com.santiagojorda.mediasync.domain.model.DestinationType
import com.santiagojorda.mediasync.domain.model.UploadStatus
import com.santiagojorda.mediasync.domain.upload.UploadResult
import com.santiagojorda.mediasync.media.MediaMetadataReader
import com.santiagojorda.mediasync.upload.DriveUploader
import com.santiagojorda.mediasync.upload.GooglePhotosUploader
import com.santiagojorda.mediasync.upload.YouTubeUploader

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

        val logDao = database.uploadLogDao()
        val existingLog = logDao.getLogForMedia(rule.id, mediaUriString)
        val startedAt = System.currentTimeMillis()
        logDao.upsert(
            UploadLogEntity(
                id = existingLog?.id ?: 0,
                ruleId = rule.id,
                mediaUri = mediaUriString,
                fileName = mediaFile.displayName,
                status = UploadStatus.UPLOADING,
                attemptCount = runAttemptCount,
                createdAt = existingLog?.createdAt ?: startedAt,
                updatedAt = startedAt,
            ),
        )

        val destination = when (rule.destinationType) {
            DestinationType.YOUTUBE -> YouTubeUploader()
            DestinationType.GOOGLE_PHOTOS -> GooglePhotosUploader()
            DestinationType.DRIVE -> DriveUploader()
        }
        val uploadResult = destination.upload(mediaFile, rule)

        // TODO: si uploadResult es Success y rule.deleteSourceAfterUpload, disparar
        // MediaStore.createDeleteRequest. Requiere lanzar el IntentSender de confirmación desde
        // una Activity (Activity Result API): no se puede hacer desde un Worker en background.
        // Se resuelve junto con la UI de reglas/historial.

        val finishedAt = System.currentTimeMillis()
        logDao.upsert(
            UploadLogEntity(
                id = existingLog?.id ?: 0,
                ruleId = rule.id,
                mediaUri = mediaUriString,
                fileName = mediaFile.displayName,
                status = if (uploadResult is UploadResult.Success) UploadStatus.SUCCESS else UploadStatus.FAILED,
                errorMessage = (uploadResult as? UploadResult.Failure)?.message,
                remoteId = (uploadResult as? UploadResult.Success)?.remoteId,
                attemptCount = runAttemptCount + 1,
                createdAt = existingLog?.createdAt ?: startedAt,
                updatedAt = finishedAt,
            ),
        )

        return when (uploadResult) {
            is UploadResult.Success -> Result.success()
            is UploadResult.Failure -> if (uploadResult.retryable) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_RULE_ID = "rule_id"
        const val KEY_MEDIA_URI = "media_uri"
    }
}
