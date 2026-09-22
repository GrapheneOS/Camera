package app.grapheneos.camera.domain.capture.usecase

import android.location.Location
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.takePicture
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.domain.capture.ImageSaver
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
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
    private val locationRepository: LocationRepository,
    private val imageSaverFactory: ImageSaver.Factory,
) : CaptureImage {

    override suspend fun invoke(
        request: CaptureImageRequest,
        needsThumbnail: () -> Boolean,
        onEvent: (CapturedImageEvent) -> Unit,
    ) {
        val imageCapture = cameraSession.imageCapture ?: return

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
            val failure = CapturedImageEvent.CaptureFailed(
                errorCode = exception.imageCaptureError,
                cause = exception,
            )
            onEvent(failure)
            return
        }

        imageSaver.onCaptureSuccess(image)
    }

    private fun location(
        request: CaptureImageRequest,
        onEvent: (CapturedImageEvent) -> Unit,
    ): Location? {
        if (!request.includeLocation) {
            return null
        }

        val location = locationRepository.currentLocation()
        if (location == null) {
            onEvent(CapturedImageEvent.LocationUnavailable)
        }

        return location
    }
}
