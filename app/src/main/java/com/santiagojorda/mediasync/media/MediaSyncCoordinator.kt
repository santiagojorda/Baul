package com.santiagojorda.mediasync.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.santiagojorda.mediasync.data.local.AppDatabase
import com.santiagojorda.mediasync.data.local.entity.RuleEntity
import com.santiagojorda.mediasync.data.local.entity.UploadLogEntity
import com.santiagojorda.mediasync.domain.model.UploadStatus
import com.santiagojorda.mediasync.work.UploadWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Conecta el [MediaChangeObserver] con las reglas guardadas: por cada archivo nuevo,
 * busca la primera regla activa cuya carpeta matchea y despacha el [UploadWorkScheduler].
 * También se usa para el backfill: cuando se crea/edita una regla, sincronizar lo que ya
 * había en la carpeta antes de que existiera la regla.
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
            dispatch(matchedRule, uri, metadata.mediaFile.displayName)
        }
    }

    /** Encola los archivos que ya estaban en la carpeta de [ruleId] antes de crear/editar la regla. */
    fun backfillRule(ruleId: Long) {
        scope.launch {
            val rule = database.ruleDao().getRuleById(ruleId)?.takeIf { it.isActive } ?: return@launch
            queryExistingUris(rule).forEach { uri ->
                val metadata = metadataReader.read(uri) ?: return@forEach
                dispatch(rule, uri, metadata.mediaFile.displayName)
            }
        }
    }

    /** Ya subido con éxito -> no repetir. Si no, se (re)encola: cubre pendientes, fallidos y archivos nuevos. */
    private suspend fun dispatch(rule: RuleEntity, uri: Uri, fileName: String) {
        val logDao = database.uploadLogDao()
        val existingLog = logDao.getLogForMedia(rule.id, uri.toString())
        if (existingLog?.status == UploadStatus.SUCCESS) return

        if (existingLog == null) {
            val now = System.currentTimeMillis()
            logDao.upsert(
                UploadLogEntity(
                    ruleId = rule.id,
                    mediaUri = uri.toString(),
                    fileName = fileName,
                    status = UploadStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        UploadWorkScheduler.enqueue(context, ruleId = rule.id, mediaUri = uri, wifiOnly = rule.wifiOnly)
    }

    private fun queryExistingUris(rule: RuleEntity): List<Uri> {
        val relativePath = RuleMatcher.expectedRelativePath(rule) ?: return emptyList()
        val collections = listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(relativePath)

        return collections.flatMap { collection ->
            val uris = mutableListOf<Uri>()
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)
                ?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (cursor.moveToNext()) {
                        uris.add(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)))
                    }
                }
            uris
        }
    }
}
