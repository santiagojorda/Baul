package com.santiagojorda.mediasync.domain.upload

import com.santiagojorda.mediasync.domain.model.Rule

interface Destination {
    suspend fun upload(file: MediaFile, rule: Rule): UploadResult
}
