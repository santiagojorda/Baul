package com.santiagojorda.mediasync.upload

import android.util.Log
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.upload.Destination
import com.santiagojorda.mediasync.domain.upload.MediaFile
import com.santiagojorda.mediasync.domain.upload.UploadResult

/** Stub: todavía no habla con la Photos Library API (scope `photoslibrary.appendonly`). */
class GooglePhotosUploader : Destination {

    override suspend fun upload(file: MediaFile, rule: Rule): UploadResult {
        Log.d(
            TAG,
            "upload() stub: file=${file.displayName} albumId=${rule.googlePhotosMetadata?.albumId} " +
                "albumName=${rule.googlePhotosMetadata?.albumName}",
        )
        return UploadResult.Failure(message = "GooglePhotosUploader todavía no implementa la subida real", retryable = false)
    }

    private companion object {
        const val TAG = "GooglePhotosUploader"
    }
}
