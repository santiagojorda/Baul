package com.santiagojorda.baul.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.santiagojorda.baul.BaulApplication
import com.santiagojorda.baul.data.repository.UploadLogRepository

/**
 * Poda el historial de subidas (ver [UploadLogRepository.pruneOldEntries]) una vez al día: la
 * ventana de retención se mide en meses, no hace falta correr más seguido.
 */
class LogRetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BaulApplication
        app.uploadLogRepository.pruneOldEntries()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "log-retention-periodic"
    }
}
