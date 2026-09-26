package app.grapheneos.camera.domain.capture.usecase

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.takePicture
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.domain.capture.model.CapturePreviewResult
import javax.inject.Inject

interface CapturePreviewImage {
    suspend operator fun invoke(): CapturePreviewResult
}

internal class CapturePreviewImageImpl @Inject constructor(
    private val cameraSession: CameraSession,
) : CapturePreviewImage {

    override suspend fun invoke(): CapturePreviewResult {
        val imageCapture = cameraSession.imageCapture ?: return CapturePreviewResult.Unavailable

        return try {
            val bitmap = imageCapture.takePicture().use(::rotated)
            CapturePreviewResult.Captured(bitmap = bitmap)
        } catch (exception: ImageCaptureException) {
            Log.e(TAG, "unable to capture a picture to hand back", exception)
            CapturePreviewResult.Failed
        }
    }

    private fun rotated(image: ImageProxy): Bitmap {
        val bitmap = image.toBitmap()
        val rotation = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotation, true)
    }

    private companion object {
        private const val TAG = "CapturePreviewImage"
    }
}
