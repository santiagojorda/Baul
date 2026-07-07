package com.santiagojorda.mediasync.media

import android.content.Context
import android.net.Uri
import com.santiagojorda.mediasync.data.local.AppDatabase
import com.santiagojorda.mediasync.data.local.entity.UploadLogEntity
import com.santiagojorda.mediasync.domain.model.UploadStatus
import com.santiagojorda.mediasync.work.UploadWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Conecta el [MediaChangeObserver] con las reglas guardadas: por cada archivo nuevo,
 * busca la primera regla activa cuya carpeta matchea y despacha el [UploadWorkScheduler].
 */
class MediaSyncCoordinator(
    private val context: Context,
    private val database: AppDatabase,
    private val metadataReader: MediaMetadataReader,
    private val scope: CoroutineScope,
) {

    fun onMediaChanged(uri: Uri) {
        scope.launch {
            val metadata = metadataReader.read(uri) ?: return@launch
            val activeRules = database.ruleDao().getActiveRules()
            val matchedRule = activeRules.firstOrNull { RuleMatcher.matches(it, metadata.relativePath) }
                ?: return@launch

            val logDao = database.uploadLogDao()
            if (logDao.getLogForMedia(matchedRule.id, uri.toString()) == null) {
                val now = System.currentTimeMillis()
                logDao.upsert(
                    UploadLogEntity(
                        ruleId = matchedRule.id,
                        mediaUri = uri.toString(),
                        fileName = metadata.mediaFile.displayName,
                        status = UploadStatus.PENDING,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            UploadWorkScheduler.enqueue(context, ruleId = matchedRule.id, mediaUri = uri, wifiOnly = matchedRule.wifiOnly)
        }
    }
}
