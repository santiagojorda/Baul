package com.santiagojorda.mediasync.media

/**
 * Decide si una carpeta nueva (sin regla explícita) se sincroniza sola a Google Photos.
 * Se excluye por nombre de carpeta (no ruta completa): la cámara, screenshots y las carpetas de
 * medios de WhatsApp/Telegram, más cualquier carpeta que el usuario prefije con "_" a mano.
 */
object AutoSyncFolderPolicy {

    private val EXCLUDED_FOLDER_NAMES = setOf(
        "camera",
        "screenshots",
        "whatsapp images",
        "whatsapp video",
        "whatsapp animated gifs",
        "telegram images",
        "telegram video",
    )

    fun isExcluded(relativePath: String): Boolean {
        val folderName = relativePath.trim('/').substringAfterLast('/')
        if (folderName.startsWith("_")) return true
        return folderName.lowercase() in EXCLUDED_FOLDER_NAMES
    }

    fun folderDisplayName(relativePath: String): String = relativePath.trim('/').substringAfterLast('/')
}
