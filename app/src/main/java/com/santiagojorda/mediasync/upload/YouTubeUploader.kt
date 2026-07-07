package com.santiagojorda.mediasync.upload

import android.util.Log
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.upload.Destination
import com.santiagojorda.mediasync.domain.upload.MediaFile
import com.santiagojorda.mediasync.domain.upload.UploadResult

/**
 * Stub: todavía no habla con la YouTube Data API v3. Sirve para validar que el
 * ContentObserver y el WorkManager job puedan resolver y despachar un [Destination]
 * antes de implementar la subida resumible real.
 */
class YouTubeUploader : Destination {

    override suspend fun upload(file: MediaFile, rule: Rule): UploadResult {
        Log.d(
            TAG,
            "upload() stub: file=${file.displayName} channelId=${rule.youTubeMetadata?.channelId} " +
                "privacyStatus=${rule.youTubeMetadata?.privacyStatus}",
        )
        return UploadResult.Failure(message = "YouTubeUploader todavía no implementa la subida real", retryable = false)
    }

    private companion object {
        const val TAG = "YouTubeUploader"
    }
}
