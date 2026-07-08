package com.santiagojorda.baul.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore

private const val PLACEHOLDER_NAME = ".baul_keep"

/**
 * Android (scoped storage, 11+) borra solo los directorios que quedan vacíos apenas se borra el
 * último archivo real que tenían — eso hace que la carpeta "desaparezca" de la Galería aunque la
 * regla de sync siga viva. Dejar acá un archivo chiquito y oculto evita que la carpeta quede
 * vacía nunca, así el sistema no la limpia solo.
 */
object FolderPlaceholder {

    fun ensure(contentResolver: ContentResolver, relativePath: String) {
        val collection = MediaStore.Files.getContentUri("external")
        val alreadyExists = contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(relativePath, PLACEHOLDER_NAME),
            null,
        )?.use { it.moveToFirst() } ?: false
        if (alreadyExists) return

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, PLACEHOLDER_NAME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
        }
        contentResolver.insert(collection, values)
    }
}
