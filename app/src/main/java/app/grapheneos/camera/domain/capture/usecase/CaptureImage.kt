package app.grapheneos.camera.domain.capture.usecase

import android.location.Location
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.takePicture
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.domain.capture.coordinator.ImageSaver
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
import app.grapheneos.camera.domain.capture.model.CaptureLocation
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import javax.inject.Inject

interface CaptureImage {

    suspend operator fun invoke(
        request: CaptureImageRequest,
        needsThumbnail: () -> Boolean,
        onEvent: (CapturedImageEvent) -> Unit,
    )
}

internal class CaptureImageImpl @Inject constructor(
    private val cameraSession: CameraSession,
    private val resolveCaptureLocation: ResolveCaptureLocation,
    private val imageSaverFactory: ImageSaver.Factory,
) : CaptureImage {

    override suspend fun invoke(
        request: CaptureImageRequest,
        needsThumbnail: () -> Boolean,
        onEvent: (CapturedImageEvent) -> Unit,
    ) {
        val imageCapture = cameraSession.imageCapture
        if (imageCapture == null) {
            val notBound = ImageCaptureException(
                ImageCapture.ERROR_INVALID_CAMERA,
                NOT_BOUND_MESSAGE,
                null,
            )
            onEvent(captureFailed(notBound))
            return
        }

        val reversedHorizontal = cameraSession.lensFacing == LensFacing.FRONT &&
            request.saveAsPreviewed

        val metadata = CaptureMetadata(
            reversedHorizontal = reversedHorizontal,
            location = location(request, onEvent),
        )
        val imageSaver = imageSaverFactory.create(
            request = request,
            metadata = metadata,
            jpegQuality = imageCapture.jpegQuality,
            needsThumbnail = needsThumbnail,
            onEvent = onEvent,
        )

        val image = try {
            imageCapture.takePicture()
        } catch (exception: ImageCaptureException) {
            onEvent(captureFailed(exception))
            return
        }

        imageSaver.onCaptureSuccess(image)
    }

    private fun captureFailed(exception: ImageCaptureException): CapturedImageEvent {
        return CapturedImageEvent.CaptureFailed(
            errorCode = exception.imageCaptureError,
            cause = exception,
        )
    }

    private suspend fun location(
        request: CaptureImageRequest,
        onEvent: (CapturedImageEvent) -> Unit,
    ): Location? {
        return when (val result = resolveCaptureLocation(request.includeLocation)) {
            is CaptureLocation.Found -> result.location
            is CaptureLocation.NotRequested -> null

            is CaptureLocation.Unavailable -> {
                onEvent(CapturedImageEvent.LocationUnavailable)
                null
            }
        }
    }

    private companion object {
        private const val NOT_BOUND_MESSAGE = "No image capture is bound"
    }
}
