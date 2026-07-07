package com.santiagojorda.mediasync.upload

import android.util.Log
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.upload.Destination
import com.santiagojorda.mediasync.domain.upload.MediaFile
import com.santiagojorda.mediasync.domain.upload.UploadResult

/** Stub: todavía no habla con la Drive API v3. */
class DriveUploader : Destination {

    override suspend fun upload(file: MediaFile, rule: Rule): UploadResult {
        Log.d(
            TAG,
            "upload() stub: file=${file.displayName} destinationFolderId=${rule.driveMetadata?.destinationFolderId}",
        )
        return UploadResult.Failure(message = "DriveUploader todavía no implementa la subida real", retryable = false)
    }

    private companion object {
        const val TAG = "DriveUploader"
    }
}
