package com.santiagojorda.mediasync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.santiagojorda.mediasync.MediaSyncApplication

/**
 * Corre cada 15 min (el mínimo que permite WorkManager para trabajo periódico), sin importar si
 * el proceso de la app está vivo — WorkManager lo despierta solo, igual que hace con
 * [UploadWorker]. Es el respaldo real para cuando Android mató el proceso y el ContentObserver
 * reactivo no puede enterarse de archivos nuevos: acá se vuelve a barrer todo.
 */
class MediaScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MediaSyncApplication
        app.mediaSyncCoordinator.scanAndDispatchAll()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "media-scan-periodic"
    }
}
