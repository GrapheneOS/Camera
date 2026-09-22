package app.grapheneos.camera.domain.capture.usecase

import android.net.Uri
import android.webkit.MimeTypeMap
import app.grapheneos.camera.VIDEO_NAME_PREFIX
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
        val uri = foreignUri ?: createVideo(storageLocation, dateString)
        val fileDescriptor = uri
            ?.let { captureOutputRepository.openForWriting(it).valueOrNull() }

        return when {
            uri == null || fileDescriptor == null -> null

            else -> RecordingOutput(
                uri = uri,
                dateString = dateString,
                fileDescriptor = fileDescriptor,
                isOwnFile = foreignUri == null,
                isPendingMediaStoreUri = foreignUri == null &&
                    storageLocation == CapturedItemRepository.MEDIA_STORE_LOCATION,
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
            fileName = VIDEO_NAME_PREFIX + dateString + VIDEO_FILE_FORMAT,
            mimeType = mimeType,
        ).valueOrNull()
    }

    private companion object {
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss"
        private const val VIDEO_FILE_FORMAT = ".mp4"
        private const val DEFAULT_VIDEO_MIME_TYPE = "video/mp4"
    }
}
