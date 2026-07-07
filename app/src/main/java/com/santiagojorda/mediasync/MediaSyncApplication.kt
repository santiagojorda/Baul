package com.santiagojorda.mediasync

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.santiagojorda.mediasync.auth.GoogleAuthManager
import com.santiagojorda.mediasync.data.local.AppDatabase
import com.santiagojorda.mediasync.data.repository.ConnectedAccountRepository
import com.santiagojorda.mediasync.data.repository.ExcludedFolderRepository
import com.santiagojorda.mediasync.data.repository.RuleRepository
import com.santiagojorda.mediasync.data.repository.UploadLogRepository
import com.santiagojorda.mediasync.media.MediaChangeObserver
import com.santiagojorda.mediasync.media.MediaMetadataReader
import com.santiagojorda.mediasync.media.MediaSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MediaSyncApplication : Application() {

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
    val mediaSyncCoordinator: MediaSyncCoordinator by lazy {
        MediaSyncCoordinator(
            context = this,
            database = database,
            metadataReader = MediaMetadataReader(contentResolver),
            scope = applicationScope,
        )
    }

    override fun onCreate() {
        super.onCreate()

        val mediaChangeObserver = MediaChangeObserver(Handler(Looper.getMainLooper()), mediaSyncCoordinator::onMediaChanged)

        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaChangeObserver)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaChangeObserver)
    }
}
