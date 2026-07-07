package com.santiagojorda.mediasync

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.santiagojorda.mediasync.data.local.AppDatabase
import com.santiagojorda.mediasync.media.MediaChangeObserver
import com.santiagojorda.mediasync.media.MediaMetadataReader
import com.santiagojorda.mediasync.media.MediaSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MediaSyncApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val coordinator = MediaSyncCoordinator(
            context = this,
            database = AppDatabase.getInstance(this),
            metadataReader = MediaMetadataReader(contentResolver),
            scope = applicationScope,
        )
        val mediaChangeObserver = MediaChangeObserver(Handler(Looper.getMainLooper()), coordinator::onMediaChanged)

        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaChangeObserver)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaChangeObserver)
    }
}
