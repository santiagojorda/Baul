package com.santiagojorda.mediasync.work

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object UploadWorkScheduler {

    fun enqueue(context: Context, ruleId: Long, mediaUri: Uri, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    UploadWorker.KEY_RULE_ID to ruleId,
                    UploadWorker.KEY_MEDIA_URI to mediaUri.toString(),
                ),
            )
            .build()

        // Por archivo (no por regla): las subidas de una misma regla corren en paralelo. La
        // condición de carrera que esto causaba en Google Photos (crear el álbum duplicado) se
        // arregló del lado de GooglePhotosUploader con un Mutex + re-chequeo en la base, no acá.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "upload-$ruleId-$mediaUri",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
