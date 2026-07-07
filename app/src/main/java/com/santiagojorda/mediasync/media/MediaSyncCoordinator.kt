package com.santiagojorda.mediasync.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.santiagojorda.mediasync.data.local.AppDatabase
import com.santiagojorda.mediasync.data.local.entity.RuleEntity
import com.santiagojorda.mediasync.data.local.entity.UploadLogEntity
import com.santiagojorda.mediasync.domain.model.DestinationType
import com.santiagojorda.mediasync.domain.model.UploadStatus
import com.santiagojorda.mediasync.domain.upload.MediaFile
import com.santiagojorda.mediasync.work.UploadWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Conecta el [MediaChangeObserver] con las reglas guardadas: por cada archivo nuevo,
 * busca la primera regla activa cuya carpeta matchea y despacha el [UploadWorkScheduler].
 * Si ninguna regla matchea, evalúa [AutoSyncFolderPolicy] para crear una regla automática a
 * Google Photos (carpeta nueva, no excluida). También se usa para el backfill: cuando se
 * crea/edita una regla, sincronizar lo que ya había en la carpeta antes de que existiera la regla.
 */
class MediaSyncCoordinator(
    private val context: Context,
    private val database: AppDatabase,
    private val metadataReader: MediaMetadataReader,
    private val scope: CoroutineScope,
) {

    private val autoRuleCreationMutex = Mutex()

    fun onMediaChanged(uri: Uri) {
        scope.launch {
            val metadata = metadataReader.read(uri) ?: return@launch
            val activeRules = database.ruleDao().getActiveRules()
            val matchedRule = activeRules.firstOrNull { RuleMatcher.matches(it, metadata.relativePath) }
                ?: maybeAutoCreateRule(metadata.relativePath)
                ?: return@launch
            dispatch(matchedRule, uri, metadata.mediaFile)
        }
    }

    /**
     * Crea una regla automática a Google Photos (cuenta = la primera conectada) para una
     * carpeta nueva sin regla propia, salvo que esté excluida por [AutoSyncFolderPolicy]. Todo
     * bajo un lock: si dos archivos de la misma carpeta nueva llegan casi juntos, el segundo
     * tiene que re-chequear (bajo el lock) que el primero no haya creado ya la regla, para no
     * terminar con dos reglas duplicadas para la misma carpeta.
     */
    private suspend fun maybeAutoCreateRule(relativePath: String?): RuleEntity? {
        if (relativePath == null) return null
        val customExclusions = database.excludedFolderDao().getAllNames().toSet()
        if (AutoSyncFolderPolicy.isExcluded(relativePath, customExclusions)) return null

        return autoRuleCreationMutex.withLock {
            val activeRules = database.ruleDao().getActiveRules()
            activeRules.firstOrNull { RuleMatcher.matches(it, relativePath) }?.let { return@withLock it }

            val account = database.connectedAccountDao().getFirstConnected() ?: return@withLock null
            val folderName = AutoSyncFolderPolicy.folderDisplayName(relativePath)
            val now = System.currentTimeMillis()
            val newRule = RuleEntity(
                folderUri = "",
                folderRelativePath = relativePath,
                folderDisplayName = folderName,
                destinationType = DestinationType.GOOGLE_PHOTOS,
                googleAccountEmail = account.email,
                photosAlbumName = folderName,
                deleteSourceAfterUpload = false,
                wifiOnly = true,
                isActive = true,
                createdAt = now,
                isAutoCreated = true,
            )
            newRule.copy(id = database.ruleDao().upsert(newRule))
        }
    }

    /**
     * Escanea TODA la MediaStore (no solo eventos nuevos) para encontrar carpetas que ya tenían
     * fotos/videos de antes de usar la app y todavía no tienen ninguna regla. El ContentObserver
     * por sí solo nunca se entera de archivos que ya estaban ahí, porque no generan ningún evento
     * de cambio — por eso hace falta este barrido explícito. Se corre al abrir la app.
     */
    fun scanExistingFoldersForAutoSync() {
        scope.launch {
            val activeRules = database.ruleDao().getActiveRules()
            val knownPaths = activeRules.mapNotNull { RuleMatcher.expectedRelativePath(it) }.toSet()

            queryAllDistinctRelativePaths()
                .filterNot { it in knownPaths }
                .forEach { relativePath ->
                    val rule = maybeAutoCreateRule(relativePath) ?: return@forEach
                    queryExistingUris(rule).forEach { uri ->
                        val metadata = metadataReader.read(uri) ?: return@forEach
                        dispatch(rule, uri, metadata.mediaFile)
                    }
                }
        }
    }

    private fun queryAllDistinctRelativePaths(): Set<String> {
        val paths = mutableSetOf<String>()
        val collections = listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        for (collection in collections) {
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)
                ?.use { cursor ->
                    val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    while (cursor.moveToNext()) {
                        cursor.getString(pathColumn)?.let { paths.add(it) }
                    }
                }
        }
        return paths
    }

    /** Encola los archivos que ya estaban en la carpeta de [ruleId] antes de crear/editar la regla. */
    fun backfillRule(ruleId: Long) {
        scope.launch {
            val rule = database.ruleDao().getRuleById(ruleId)?.takeIf { it.isActive } ?: return@launch
            queryExistingUris(rule).forEach { uri ->
                val metadata = metadataReader.read(uri) ?: return@forEach
                dispatch(rule, uri, metadata.mediaFile)
            }
        }
    }

    /**
     * Ya subido con éxito -> no repetir. Si no, se (re)encola: cubre pendientes, fallidos y
     * archivos nuevos. Las reglas de YouTube ignoran directamente lo que no sea video (una foto
     * en esa carpeta no tiene sentido subirla ahí).
     */
    private suspend fun dispatch(rule: RuleEntity, uri: Uri, mediaFile: MediaFile) {
        if (rule.destinationType == DestinationType.YOUTUBE && !mediaFile.mimeType.startsWith("video/")) return

        val logDao = database.uploadLogDao()
        val existingLog = logDao.getLogForMedia(rule.id, uri.toString())
        if (existingLog?.status == UploadStatus.SUCCESS) return

        if (existingLog == null) {
            val now = System.currentTimeMillis()
            logDao.upsert(
                UploadLogEntity(
                    ruleId = rule.id,
                    mediaUri = uri.toString(),
                    fileName = mediaFile.displayName,
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
