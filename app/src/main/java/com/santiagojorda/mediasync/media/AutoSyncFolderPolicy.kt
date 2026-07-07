package com.santiagojorda.mediasync.media

/**
 * Decide si una carpeta nueva (sin regla explícita) se sincroniza sola a Google Photos.
 * Se excluye por nombre de carpeta (no ruta completa): las fijas de acá abajo, cualquier carpeta
 * que el usuario prefije con "_" a mano, y las que el usuario agregó a mano desde la pantalla de
 * exclusiones (ExcludedFolderRepository).
 */
object AutoSyncFolderPolicy {

    private val BUILT_IN_EXCLUDED_FOLDER_NAMES = setOf(
        "camera",
        "screenshots",
        "whatsapp images",
        "whatsapp video",
        "whatsapp animated gifs",
        "telegram images",
        "telegram video",
    )

    fun isExcluded(relativePath: String, customExcludedNames: Set<String> = emptySet()): Boolean {
        val folderName = folderDisplayName(relativePath).lowercase()
        if (folderName.startsWith("_")) return true
        if (folderName in BUILT_IN_EXCLUDED_FOLDER_NAMES) return true
        return folderName in customExcludedNames
    }

    fun folderDisplayName(relativePath: String): String = relativePath.trim('/').substringAfterLast('/')
}
