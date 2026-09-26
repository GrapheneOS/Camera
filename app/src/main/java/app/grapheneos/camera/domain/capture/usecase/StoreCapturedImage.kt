package app.grapheneos.camera.domain.capture.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult.Stage
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
        val created = captureOutputRepository.createImage(
            storageLocation = storageLocation,
            fileName = fileName,
            mimeType = mimeType,
        )

        return when (created) {
            is CaptureOutputResult.Success -> writeAndPublish(
                uri = created.value,
                jpegBytes = jpegBytes,
            )

            is CaptureOutputResult.Failure -> createFailure(
                storageLocation = storageLocation,
                cause = created.cause,
            )
        }
    }

    private suspend fun writeAndPublish(uri: Uri, jpegBytes: ByteArray): StoreCapturedImageResult {
        val written = captureOutputRepository.write(uri, jpegBytes)
        if (written is CaptureOutputResult.Failure) {
            captureOutputRepository.delete(uri)

            return StoreCapturedImageResult.Failed(
                stage = Stage.FILE_WRITE,
                cause = written.cause,
            )
        }

        return when (val published = captureOutputRepository.publish(uri)) {
            is CaptureOutputResult.Success -> StoreCapturedImageResult.Stored(uri = uri)

            // don't delete the image in this case, since it's already fully written out
            is CaptureOutputResult.Failure -> StoreCapturedImageResult.Failed(
                stage = Stage.FILE_WRITE_COMPLETION,
                cause = published.cause,
            )
        }
    }

    private fun createFailure(
        storageLocation: String,
        cause: Exception,
    ): StoreCapturedImageResult {
        return when (storageLocation) {
            CapturedItemRepository.MEDIA_STORE_LOCATION -> {
                StoreCapturedImageResult.Failed(
                    stage = Stage.FILE_CREATION,
                    cause = cause,
                )
            }

            else -> StoreCapturedImageResult.StorageLocationNotFound(cause = cause)
        }
    }
}
