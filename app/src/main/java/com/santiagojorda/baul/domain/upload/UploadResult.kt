package com.santiagojorda.baul.domain.upload

sealed interface UploadResult {
    data class Success(val remoteId: String) : UploadResult
    data class Failure(val message: String, val retryable: Boolean = true) : UploadResult
}
