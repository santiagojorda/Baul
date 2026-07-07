package com.santiagojorda.baul.upload

import android.util.Log
import com.santiagojorda.baul.domain.model.Rule
import com.santiagojorda.baul.domain.upload.Destination
import com.santiagojorda.baul.domain.upload.MediaFile
import com.santiagojorda.baul.domain.upload.UploadResult

/** Stub: todavía no habla con la Drive API v3. */
class DriveUploader : Destination {

    override suspend fun upload(file: MediaFile, rule: Rule, onProgress: (bytesSent: Long) -> Unit): UploadResult {
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
