package app.grapheneos.camera.domain.capture.usecase

import android.location.Location
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.takePicture
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.JpegExtractor
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.capture.CapturedImagePipeline
import app.grapheneos.camera.domain.capture.ImageSaver
import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapper
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

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
    private val storeCapturedImage: StoreCapturedImage,
    private val exifMapper: CapturedImageExifMapper,
    private val jpegExtractor: JpegExtractor,
    private val pipeline: CapturedImagePipeline,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : CaptureImage {

    override suspend fun invoke(
        request: CaptureImageRequest,
        needsThumbnail: () -> Boolean,
        onEvent: (CapturedImageEvent) -> Unit,
    ) {
        val imageCapture = cameraSession.imageCapture ?: return

        val imageSaver = imageSaver(
            request = request,
            location = location(request, onEvent),
            jpegQuality = imageCapture.jpegQuality,
            needsThumbnail = needsThumbnail,
            onEvent = onEvent,
        )

        val image = try {
            imageCapture.takePicture()
        } catch (exception: ImageCaptureException) {
            onEvent(
                CapturedImageEvent.CaptureFailed(
                    errorCode = exception.imageCaptureError,
                    cause = exception,
                ),
            )
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

    private fun imageSaver(
        request: CaptureImageRequest,
        location: Location?,
        jpegQuality: Int,
        needsThumbnail: () -> Boolean,
        onEvent: (CapturedImageEvent) -> Unit,
    ): ImageSaver {
        return ImageSaver(
            scope = applicationScope,
            mainDispatcher = mainDispatcher,
            storeCapturedImage = storeCapturedImage,
            exifMapper = exifMapper,
            jpegExtractor = jpegExtractor,
            jpegQuality = jpegQuality,
            storageLocation = request.storageLocation,
            imageFileFormat = IMAGE_FILE_FORMAT,
            imageCaptureMetadata = CaptureMetadata(
                reversedHorizontal = cameraSession.lensFacing == LensFacing.FRONT &&
                    request.saveAsPreviewed,
                location = location,
            ),
            removeExifAfterCapture = request.removeExif,
            targetThumbnailWidth = request.targetThumbnailWidth,
            targetThumbnailHeight = request.targetThumbnailHeight,
            pipeline = pipeline,
            needsThumbnail = needsThumbnail,
            onEvent = onEvent,
        )
    }

    private companion object {
        private const val IMAGE_FILE_FORMAT = ".jpg"
    }
}
