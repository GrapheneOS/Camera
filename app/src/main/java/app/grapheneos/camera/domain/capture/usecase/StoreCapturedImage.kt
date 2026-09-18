package app.grapheneos.camera.domain.capture.usecase

import android.net.Uri
import android.util.Log
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult.Stage
import java.io.IOException
import javax.inject.Inject

interface StoreCapturedImage {

    suspend operator fun invoke(
        jpegBytes: ByteArray,
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): StoreCapturedImageResult
}

internal class StoreCapturedImageImpl @Inject constructor(
    private val captureOutputRepository: CaptureOutputRepository,
) : StoreCapturedImage {

    override suspend fun invoke(
        jpegBytes: ByteArray,
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): StoreCapturedImageResult {
        val uri = try {
            captureOutputRepository.createImage(
                storageLocation = storageLocation,
                fileName = fileName,
                mimeType = mimeType,
            )
        } catch (exception: IOException) {
            return when (storageLocation) {
                CapturedItemRepository.MEDIA_STORE_LOCATION -> {
                    StoreCapturedImageResult.Failed(
                        stage = Stage.FILE_CREATION,
                        cause = exception,
                    )
                }

                else -> StoreCapturedImageResult.StorageLocationNotFound(cause = exception)
            }
        }

        return writeAndPublish(
            uri = uri,
            jpegBytes = jpegBytes,
        )
    }

    private suspend fun writeAndPublish(
        uri: Uri,
        jpegBytes: ByteArray,
    ): StoreCapturedImageResult {
        try {
            captureOutputRepository.write(uri, jpegBytes)
        } catch (exception: IOException) {
            deleteIncompleteImage(uri)
            return StoreCapturedImageResult.Failed(
                stage = Stage.FILE_WRITE,
                cause = exception,
            )
        }

        return try {
            captureOutputRepository.publish(uri)
            StoreCapturedImageResult.Stored(uri = uri)
        } catch (exception: IOException) {
            // don't delete the image in this case, since it's already fully written out
            StoreCapturedImageResult.Failed(
                stage = Stage.FILE_WRITE_COMPLETION,
                cause = exception,
            )
        }
    }

    private suspend fun deleteIncompleteImage(uri: Uri) {
        try {
            captureOutputRepository.delete(uri)
        } catch (deleteException: IOException) {
            Log.w(TAG, "unable to delete an incomplete image $uri", deleteException)
        }
    }

    private companion object {
        private const val TAG = "StoreCapturedImage"
    }
}
