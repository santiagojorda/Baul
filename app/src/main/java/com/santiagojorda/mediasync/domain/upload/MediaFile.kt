package com.santiagojorda.mediasync.domain.upload

import android.net.Uri

data class MediaFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
)
