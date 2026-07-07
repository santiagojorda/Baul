package com.santiagojorda.baul.domain.upload

import com.santiagojorda.baul.domain.model.Rule

interface Destination {
    suspend fun upload(file: MediaFile, rule: Rule, onProgress: (bytesSent: Long) -> Unit): UploadResult
}
