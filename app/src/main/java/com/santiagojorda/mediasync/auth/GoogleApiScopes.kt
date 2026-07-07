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

    /**
     * `appendonly` solo alcanza para crear álbumes/subir contenido, NO para listarlos
     * (`albums.list` tira 403 "insufficient authentication scopes" con solo ese scope). Hace
     * falta este de acá, además, para poder buscar un álbum ya creado por la app antes de crear
     * uno nuevo (evita duplicados con el mismo nombre).
     */
    const val PHOTOS_READONLY_APP_CREATED_DATA = "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata"

    /** Se piden todos juntos al conectar una cuenta, para no repetir el consentimiento por regla. */
    val ALL = setOf(YOUTUBE_UPLOAD, DRIVE_FILE, PHOTOS_APPEND_ONLY, PHOTOS_READONLY_APP_CREATED_DATA)
}
