package com.santiagojorda.mediasync.media

import android.net.Uri
import android.provider.DocumentsContract
import com.santiagojorda.mediasync.data.local.entity.RuleEntity

/**
 * Compara la carpeta SAF de una regla (elegida con el selector nativo, p. ej.
 * `content://.../tree/primary:DCIM/MyFolder`) contra la ruta relativa que reporta MediaStore
 * para un archivo (p. ej. "DCIM/MyFolder/"). Solo soporta el volumen "primary" (almacenamiento
 * interno del teléfono), que es el caso común al elegir una carpeta de la galería; una carpeta
 * en una SD card externa no matchea con esta implementación.
 */
object RuleMatcher {

    fun matches(rule: RuleEntity, mediaRelativePath: String?): Boolean {
        if (mediaRelativePath == null) return false
        val expected = expectedRelativePath(rule) ?: return false
        return mediaRelativePath == expected
    }

    /** La ruta relativa (a la MediaStore) que se espera que reporten los archivos de [rule]. */
    fun expectedRelativePath(rule: RuleEntity): String? {
        // Reglas auto-creadas (ver AutoSyncFolderPolicy): no hay árbol SAF, la ruta ya viene resuelta.
        rule.folderRelativePath?.let { return it }

        val treeUri = Uri.parse(rule.folderUri)
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null

        val separatorIndex = docId.indexOf(':')
        if (separatorIndex == -1) return null

        val volume = docId.substring(0, separatorIndex)
        if (volume != "primary") return null

        val folderPath = docId.substring(separatorIndex + 1).trim('/')
        return "$folderPath/"
    }
}
