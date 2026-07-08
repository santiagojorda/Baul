package com.santiagojorda.baul.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.domain.upload.MediaFile
import com.santiagojorda.baul.work.UploadWorkScheduler
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
 *
 * `open` (clase y [scanExistingFoldersForAutoSync]) a propósito: deja fakear en tests que solo
 * necesitan verificar que *se dispara* un barrido (ej. al conectar una cuenta), sin arrastrar
 * MediaStore real ni el executor async de Room que usa el barrido de verdad.
 */
open class SyncCoordinator(
    private val context: Context,
    private val database: AppDatabase,
    private val metadataReader: MediaMetadataReader,
    private val scope: CoroutineScope,
) {

    private val autoRuleCreationMutex = Mutex()

    fun onMediaChanged(uri: Uri) {
        scope.launch {
            val metadata = metadataReader.read(uri) ?: return@launch
            val allRules = database.ruleDao().getAllRules()
            val rule = allRules.firstOrNull { RuleMatcher.matches(it, metadata.relativePath) }
                ?: maybeAutoCreateRule(metadata.relativePath)
                ?: return@launch
            // Recién creada (o ya existente pero todavía sin prender): el usuario la activa a
            // mano con el switch en Reglas, no se sube nada solo hasta entonces.
            if (!rule.isActive) return@launch
            dispatch(rule, uri, metadata.mediaFile)
        }
    }

    /**
     * Crea una regla automática a Google Photos (cuenta = la marcada como default en Cuentas, o
     * la primera conectada si ninguna quedó marcada) para una carpeta nueva sin regla propia,
     * salvo que esté excluida por [AutoSyncFolderPolicy]. Queda INACTIVA por defecto: el usuario
     * decide a mano, con el switch en Reglas, qué carpetas sincronizar de verdad — esto solo la
     * deja lista en la lista para no tener que crearla manualmente. Todo bajo un lock: si dos
     * archivos de la misma carpeta nueva llegan casi juntos, el segundo tiene que re-chequear
     * (bajo el lock, y entre TODAS las reglas, no solo las activas) que el primero no haya creado
     * ya la regla, para no terminar con dos reglas duplicadas para la misma carpeta.
     */
    private suspend fun maybeAutoCreateRule(relativePath: String?): RuleEntity? {
        if (relativePath == null) return null
        val customExclusions = database.excludedFolderDao().getAllNames().toSet()
        if (AutoSyncFolderPolicy.isExcluded(relativePath, customExclusions)) return null

        return autoRuleCreationMutex.withLock {
            val allRules = database.ruleDao().getAllRules()
            allRules.firstOrNull { RuleMatcher.matches(it, relativePath) }?.let { return@withLock it }

            val accountDao = database.connectedAccountDao()
            val account = (accountDao.getDefault() ?: accountDao.getFirstConnected()) ?: return@withLock null
            val folderName = AutoSyncFolderPolicy.folderDisplayName(relativePath)
            val now = System.currentTimeMillis()
            val newRule = RuleEntity(
                folderUri = "",
                folderRelativePath = relativePath,
                folderDisplayName = folderName,
                destinationType = DestinationType.GOOGLE_PHOTOS,
                googleAccountEmail = account.email,
                photosAlbumName = folderName,
                // Por defecto borra la foto/video de la galería una vez subido con éxito (nunca
                // la carpeta en sí, MediaStore.createDeleteRequest borra archivos puntuales) —
                // el usuario lo puede apagar a mano por regla si quiere conservar el original.
                deleteSourceAfterUpload = true,
                wifiOnly = true,
                isActive = false,
                createdAt = now,
                isAutoCreated = true,
            )
            newRule.copy(id = database.ruleDao().upsert(newRule))
        }
    }

    /**
     * Barrido completo: re-despacha lo pendiente de cada regla activa (cubre lo que el
     * ContentObserver se perdió mientras el proceso estaba muerto — un observer reactivo no
     * sirve de nada si Android mató la app) y además descubre carpetas nuevas sin regla, igual
     * que [scanExistingFoldersForAutoSync]. Es la versión "suspend" pensada para que un Worker
     * periódico (que sí sobrevive al proceso muerto) la pueda esperar de verdad.
     */
    suspend fun scanAndDispatchAll() {
        deduplicateRules()
        val allRules = database.ruleDao().getAllRules()

        allRules.filter { it.isActive }.forEach { rule ->
            queryExistingUris(rule).forEach { uri ->
                val metadata = metadataReader.read(uri) ?: return@forEach
                dispatch(rule, uri, metadata.mediaFile)
            }
        }

        // Solo registra la regla (inactiva, ver maybeAutoCreateRule) para que aparezca en Reglas
        // con el switch apagado — no se backfillea nada hasta que el usuario la prenda a mano.
        val knownPaths = allRules.mapNotNull { RuleMatcher.expectedRelativePath(it)?.lowercase() }.toSet()
        queryAllDistinctRelativePaths()
            .filterNot { it.lowercase() in knownPaths }
            .forEach { relativePath -> maybeAutoCreateRule(relativePath) }
    }

    /** Versión "fire and forget" para disparar desde un LaunchedEffect al abrir la app, o al conectar una cuenta. */
    open fun scanExistingFoldersForAutoSync() {
        scope.launch { scanAndDispatchAll() }
    }

    /**
     * Limpieza de reglas duplicadas para la misma carpeta física (mismo destino y cuenta), que
     * quedaron de antes de que [maybeAutoCreateRule] fuera atómica con el Mutex, o de la
     * comparación case-sensitive que tenía [RuleMatcher] (una carpeta reportada con distinta
     * capitalización por MediaStore generaba una segunda regla "automática" para lo mismo). Se
     * queda con la más vieja de cada grupo y borra el resto; sus logs de subida no se reasignan
     * (HistoryViewModel ya muestra "Carpeta eliminada" para logs huérfanos), total ya se subieron.
     */
    private suspend fun deduplicateRules() {
        val ruleDao = database.ruleDao()
        val allRules = ruleDao.getAllRules()
        allRules
            .groupBy { rule ->
                val path = RuleMatcher.expectedRelativePath(rule)?.lowercase() ?: return@groupBy null
                Triple(path, rule.destinationType, rule.googleAccountEmail)
            }
            .filterKeys { it != null }
            .values
            .filter { group -> group.size > 1 }
            .forEach { duplicates ->
                duplicates.sortedBy { it.createdAt }.drop(1).forEach { ruleDao.delete(it) }
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
     * archivos nuevos.
     */
    private suspend fun dispatch(rule: RuleEntity, uri: Uri, mediaFile: MediaFile) {
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
