package com.santiagojorda.baul.media

/**
 * Decide si una carpeta nueva (sin regla explícita) se sincroniza sola a Google Photos. Esto
 * solo aplica al auto-sync — una regla manual puede apuntar a cualquier carpeta, elegida a mano
 * con el selector SAF, sin esta restricción.
 *
 * Primero se limita a carpetas dentro de `DCIM/` (no Pictures/, Movies/, Download/, etc.), y
 * dentro de eso se excluye por nombre de carpeta: las fijas de acá abajo, cualquier carpeta que
 * el usuario prefije con "_" a mano, y las que el usuario agregó desde la pantalla de exclusiones
 * (ExcludedFolderRepository).
 */
object AutoSyncFolderPolicy {

    private const val AUTO_SYNC_ROOT = "DCIM/"

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
        if (!relativePath.startsWith(AUTO_SYNC_ROOT, ignoreCase = true)) return true

        val folderName = folderDisplayName(relativePath).lowercase()
        if (folderName.startsWith("_")) return true
        if (folderName in BUILT_IN_EXCLUDED_FOLDER_NAMES) return true
        return folderName in customExcludedNames
    }

    fun folderDisplayName(relativePath: String): String = relativePath.trim('/').substringAfterLast('/')
}
