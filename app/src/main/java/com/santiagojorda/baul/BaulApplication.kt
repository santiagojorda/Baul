package com.santiagojorda.baul

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.santiagojorda.baul.auth.GoogleAuthManager
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.repository.ConnectedAccountRepository
import com.santiagojorda.baul.data.repository.ExcludedFolderRepository
import com.santiagojorda.baul.data.repository.RuleRepository
import com.santiagojorda.baul.data.repository.UploadLogRepository
import com.santiagojorda.baul.media.MediaChangeObserver
import com.santiagojorda.baul.media.MediaMetadataReader
import com.santiagojorda.baul.media.SyncCoordinator
import com.santiagojorda.baul.work.MediaScanWorker
import com.santiagojorda.baul.work.UploadNotificationService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BaulApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val ruleRepository: RuleRepository by lazy { RuleRepository(database.ruleDao()) }
    val uploadLogRepository: UploadLogRepository by lazy {
        UploadLogRepository(this, database.uploadLogDao(), database.ruleDao())
    }
    val connectedAccountRepository: ConnectedAccountRepository by lazy {
        ConnectedAccountRepository(database.connectedAccountDao())
    }
    val excludedFolderRepository: ExcludedFolderRepository by lazy {
        ExcludedFolderRepository(database.excludedFolderDao())
    }
    val googleAuthManager: GoogleAuthManager by lazy { GoogleAuthManager(this) }
    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(
            context = this,
            database = database,
            metadataReader = MediaMetadataReader(contentResolver),
            scope = applicationScope,
        )
    }

    override fun onCreate() {
        super.onCreate()

        UploadNotificationService.createNotificationChannel(this)

        val mediaChangeObserver = MediaChangeObserver(Handler(Looper.getMainLooper()), syncCoordinator::onMediaChanged)

        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaChangeObserver)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaChangeObserver)

        // Respaldo para cuando Android mata el proceso: el ContentObserver de arriba no se entera
        // de nada si el proceso no está vivo. KEEP para no resetear el ciclo de 15 min cada vez
        // que el proceso arranca de nuevo (podría terminar sin correr nunca si reinicia seguido).
        val periodicScan = PeriodicWorkRequestBuilder<MediaScanWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MediaScanWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicScan,
        )
    }
}
