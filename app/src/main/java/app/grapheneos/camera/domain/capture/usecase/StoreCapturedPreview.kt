package app.grapheneos.camera.domain.capture.usecase

import android.graphics.Bitmap
import android.net.Uri
import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import java.io.ByteArrayOutputStream
import javax.inject.Inject

interface StoreCapturedPreview {
    suspend operator fun invoke(uri: Uri, bitmap: Bitmap): Boolean
}

internal class StoreCapturedPreviewImpl @Inject constructor(
    private val captureOutputRepository: CaptureOutputRepository,
) : StoreCapturedPreview {

    override suspend fun invoke(
        uri: Uri,
        bitmap: Bitmap,
    ): Boolean {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(compressFormat(uri), FULL_QUALITY, bytes)

        return captureOutputRepository.write(
            uri,
            bytes.toByteArray(),
        ) !is CaptureOutputResult.Failure
    }

    private fun compressFormat(uri: Uri): Bitmap.CompressFormat {
        return when {
            uri.path?.endsWith(PNG_EXTENSION) == true -> Bitmap.CompressFormat.PNG
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private companion object {
        private const val FULL_QUALITY = 100
        private const val PNG_EXTENSION = ".png"
    }
}
