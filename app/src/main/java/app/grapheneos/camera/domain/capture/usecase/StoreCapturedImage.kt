package app.grapheneos.camera.domain.capture.usecase

import android.net.Uri
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

        return created.fold(
            onSuccess = { uri ->
                writeAndPublish(
                    uri = uri,
                    jpegBytes = jpegBytes,
                )
            },
            onFailure = { cause ->
                createFailure(
                    storageLocation = storageLocation,
                    cause = cause,
                )
            },
        )
    }

    private suspend fun writeAndPublish(
        uri: Uri,
        jpegBytes: ByteArray,
    ): StoreCapturedImageResult {
        val writeFailure = captureOutputRepository.write(
            uri = uri,
            bytes = jpegBytes,
        ).exceptionOrNull()

        if (writeFailure != null) {
            captureOutputRepository.delete(uri)

            return StoreCapturedImageResult.Failed(
                stage = Stage.FILE_WRITE,
                cause = writeFailure,
            )
        }

        return captureOutputRepository.publish(uri).fold(
            onSuccess = {
                StoreCapturedImageResult.Stored(uri = uri)
            },
            // don't delete the image in this case, since it's already fully written out
            onFailure = { cause ->
                StoreCapturedImageResult.Failed(
                    stage = Stage.FILE_WRITE_COMPLETION,
                    cause = cause,
                )
            },
        )
    }

    private fun createFailure(
        storageLocation: String,
        cause: Throwable,
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
