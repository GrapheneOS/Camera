package app.grapheneos.camera.domain.capture.usecase

import android.net.Uri
import android.webkit.MimeTypeMap
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.model.RecordingOutput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

interface CreateRecordingOutput {

    suspend operator fun invoke(
        storageLocation: String,
        foreignUri: Uri?,
    ): RecordingOutput?
}

internal class CreateRecordingOutputImpl @Inject constructor(
    private val captureOutputRepository: CaptureOutputRepository,
) : CreateRecordingOutput {

    override suspend fun invoke(
        storageLocation: String,
        foreignUri: Uri?,
    ): RecordingOutput? {
        val dateString = SimpleDateFormat(DATE_FORMAT, Locale.US).format(Date())

        return when (foreignUri) {
            null -> createOwnOutput(
                storageLocation = storageLocation,
                dateString = dateString,
            )

            else -> openForeignOutput(
                uri = foreignUri,
                dateString = dateString,
            )
        }
    }

    private suspend fun createOwnOutput(
        storageLocation: String,
        dateString: String,
    ): RecordingOutput? {
        val uri = createVideo(storageLocation, dateString) ?: return null

        return captureOutputRepository.openForWriting(uri).fold(
            onSuccess = { fileDescriptor ->
                RecordingOutput(
                    uri = uri,
                    dateString = dateString,
                    fileDescriptor = fileDescriptor,
                    isOwnFile = true,
                    isPendingMediaStoreUri = storageLocation ==
                        CapturedItemRepository.MEDIA_STORE_LOCATION,
                )
            },
            onFailure = {
                captureOutputRepository.delete(uri)
                null
            },
        )
    }

    private suspend fun openForeignOutput(
        uri: Uri,
        dateString: String,
    ): RecordingOutput? {
        return captureOutputRepository.openForWriting(uri).getOrNull()?.let { fileDescriptor ->
            RecordingOutput(
                uri = uri,
                dateString = dateString,
                fileDescriptor = fileDescriptor,
                isOwnFile = false,
                isPendingMediaStoreUri = false,
            )
        }
    }

    private suspend fun createVideo(
        storageLocation: String,
        dateString: String,
    ): Uri? {
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(VIDEO_FILE_FORMAT)
            ?: DEFAULT_VIDEO_MIME_TYPE

        return captureOutputRepository.createVideo(
            storageLocation = storageLocation,
            fileName = CapturedItemType.VIDEO.namePrefix + dateString + VIDEO_FILE_FORMAT,
            mimeType = mimeType,
        ).getOrNull()
    }

    private companion object {
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss"
        private const val VIDEO_FILE_FORMAT = ".mp4"
        private const val DEFAULT_VIDEO_MIME_TYPE = "video/mp4"
    }
}
