package com.santiagojorda.mediasync.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.santiagojorda.mediasync.domain.upload.MediaFile

data class MediaItemMetadata(
    val mediaFile: MediaFile,
    /** Ruta relativa al volumen (p. ej. "DCIM/MyFolder/"), o null si MediaStore no la reporta. */
    val relativePath: String?,
)

class MediaMetadataReader(private val contentResolver: ContentResolver) {

    /** Devuelve null si el ítem ya no existe (por ejemplo, el cambio fue un borrado). */
    fun read(uri: Uri): MediaItemMetadata? {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null

            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
            val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))

            return MediaItemMetadata(
                mediaFile = MediaFile(
                    uri = uri,
                    displayName = displayName ?: uri.lastPathSegment.orEmpty(),
                    mimeType = mimeType ?: "application/octet-stream",
                    sizeBytes = size,
                ),
                relativePath = relativePath,
            )
        }
        return null
    }
}
