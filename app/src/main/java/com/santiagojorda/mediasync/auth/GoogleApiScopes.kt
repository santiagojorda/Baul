package com.santiagojorda.mediasync.auth

/**
 * Scopes de las APIs que puede necesitar una regla, según su destino. Son strings planos (no
 * las constantes `DriveScopes`/`YouTubeScopes` de las librerías de cliente) para no depender
 * todavía de esas librerías solo por esto.
 */
object GoogleApiScopes {
    const val YOUTUBE_UPLOAD = "https://www.googleapis.com/auth/youtube.upload"
    const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val PHOTOS_APPEND_ONLY = "https://www.googleapis.com/auth/photoslibrary.appendonly"

    /** Se piden los tres juntos al conectar una cuenta, para no repetir el consentimiento por regla. */
    val ALL = setOf(YOUTUBE_UPLOAD, DRIVE_FILE, PHOTOS_APPEND_ONLY)
}
