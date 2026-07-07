package com.santiagojorda.mediasync.domain.upload

sealed interface UploadResult {
    /**
     * [remoteAlbumId] va seteado solo cuando el uploader tuvo que crear un álbum nuevo (p. ej.
     * Google Photos) y hay que persistirlo en la regla para no recrearlo en cada subida.
     */
    data class Success(val remoteId: String, val remoteAlbumId: String? = null) : UploadResult
    data class Failure(val message: String, val retryable: Boolean = true) : UploadResult
}
