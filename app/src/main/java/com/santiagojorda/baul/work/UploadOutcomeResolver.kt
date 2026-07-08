package com.santiagojorda.baul.work

import androidx.work.ListenableWorker.Result
import com.santiagojorda.baul.domain.model.UploadLogEntry.Companion.MAX_RETRY_ATTEMPTS
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.domain.upload.UploadResult

/**
 * Decide, a partir del resultado real del upload, el único estado que habilita el borrado seguro
 * del original ([UploadStatus.SUCCESS] — ver
 * [com.santiagojorda.baul.data.repository.UploadLogRepository.getPendingDeletions]) y si
 * [UploadWorker] debe reintentar. Separado de [UploadWorker.doWork] para poder probar esta regla
 * sin Room/WorkManager/Context de por medio.
 */
object UploadOutcomeResolver {

    data class Outcome(
        val status: UploadStatus,
        val workResult: Result,
        val errorMessage: String?,
        val remoteId: String?,
        val bytesUploaded: Long,
    )

    fun resolve(uploadResult: UploadResult, runAttemptCount: Int, totalBytes: Long): Outcome {
        // Mientras todavía queden reintentos disponibles para un fallo transitorio, el estado
        // queda PENDING ("reintentando solo") en vez de FAILED — recién se marca FAILED si el
        // error no es reintentable, o si ya se agotaron los reintentos.
        val willRetry = uploadResult is UploadResult.Failure &&
            uploadResult.retryable &&
            runAttemptCount < MAX_RETRY_ATTEMPTS

        val status = when {
            uploadResult is UploadResult.Success -> UploadStatus.SUCCESS
            willRetry -> UploadStatus.PENDING
            else -> UploadStatus.FAILED
        }
        val workResult = when (uploadResult) {
            is UploadResult.Success -> Result.success()
            is UploadResult.Failure -> if (willRetry) Result.retry() else Result.failure()
        }

        return Outcome(
            status = status,
            workResult = workResult,
            errorMessage = (uploadResult as? UploadResult.Failure)?.message,
            remoteId = (uploadResult as? UploadResult.Success)?.remoteId,
            bytesUploaded = if (uploadResult is UploadResult.Success) totalBytes else 0,
        )
    }
}
