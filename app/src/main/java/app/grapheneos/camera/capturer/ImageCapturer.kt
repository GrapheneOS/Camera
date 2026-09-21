package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import android.util.Log
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.R
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.ImageSaverException
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderScreenModel
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureDetails
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.util.printStackTraceToString
import kotlinx.coroutines.launch

class ImageCapturer(val mActivity: MainActivity) {

    private val viewfinder: ViewfinderScreenModel = mActivity.viewfinder

    private val session = mActivity.session

    private val captureImage = mActivity.captureImage

    private var pendingCapture: PendingCapture? = null

    val isTakingPicture: Boolean
        get() = pendingCapture != null

    // Cancelling does not stop the request CameraX is already serving, so its failure
    // still arrives and has to be kept quiet.
    private class PendingCapture {
        var isCancelled = false
    }

    fun takePicture() {
        if (!canTakePicture()) {
            return
        }

        val capture = viewfinder.uiState.value.capture
        val preview = mActivity.imagePreview

        val request = CaptureImageRequest(
            storageLocation = mActivity.capturedItemSession.storageLocation,
            includeLocation = capture.geoTagging,
            saveAsPreviewed = capture.saveImageAsPreviewed,
            removeExif = capture.removeExifAfterCapture,
            targetThumbnailWidth = preview.width,
            targetThumbnailHeight = preview.height,
        )

        val pending = PendingCapture()
        pendingCapture = pending

        mActivity.applicationScope.launch(mActivity.mainDispatcher) {
            captureImage(
                request = request,
                needsThumbnail = { !mActivity.isDestroyed },
                onEvent = { event -> onCapturedImageEvent(pending, event) },
            )
        }

        viewfinder.onAction(CaptureAction.PictureCaptureStarted)
    }

    private fun canTakePicture(): Boolean {
        val capture = viewfinder.uiState.value.capture

        return when {
            session.camera == null -> false

            !capture.canTakePicture -> {
                mActivity.showMessage(R.string.unsupported_taking_picture_while_recording)
                false
            }

            else -> !isTakingPicture
        }
    }

    fun cancelPendingCaptureRequest() {
        val pending = pendingCapture ?: return

        pending.isCancelled = true
        pendingCapture = null

        viewfinder.onAction(CaptureAction.PictureCaptureCancelled)
    }

    private fun onCapturedImageEvent(pending: PendingCapture, event: CapturedImageEvent) {
        when (event) {
            is CapturedImageEvent.Captured -> onCaptureSuccess(pending)
            is CapturedImageEvent.Saved -> onImageSaverSuccess(event.item)
            is CapturedImageEvent.StorageLocationNotFound -> onStorageLocationNotFound()
            is CapturedImageEvent.CaptureFailed -> onCaptureError(pending, event)

            is CapturedImageEvent.LocationUnavailable -> {
                mActivity.showMessage(R.string.location_unavailable)
            }

            is CapturedImageEvent.ThumbnailReady -> {
                onThumbnailGenerated(event.thumbnail)
            }

            is CapturedImageEvent.Failed -> {
                onImageSaverError(event.cause, event.alreadyReported)
            }
        }
    }

    private fun onCaptureSuccess(pending: PendingCapture) {
        finish(pending)

        viewfinder.onAction(CaptureAction.PictureCaptured)
    }

    private fun onCaptureError(pending: PendingCapture, event: CapturedImageEvent.CaptureFailed) {
        Log.e(TAG, "onCaptureError", event.cause)

        finish(pending)

        if (pending.isCancelled) {
            return
        }

        viewfinder.onAction(
            CaptureAction.PictureCaptureFailed(
                errorCode = event.errorCode,
                details = detailsOf(event.cause),
            ),
        )
    }

    private fun onImageSaverSuccess(item: CapturedItem) {
        viewfinder.onAction(CaptureAction.PictureSaved(item = item))
    }

    private fun onStorageLocationNotFound() {
        viewfinder.onAction(CaptureAction.StorageLocationNotFound)
    }

    private fun onImageSaverError(exception: ImageSaverException, skipErrorDialog: Boolean) {
        Log.e(TAG, "onImageSaverError", exception)

        viewfinder.onAction(
            CaptureAction.PictureSaveFailed(
                stage = exception.place.name,
                details = detailsOf(exception),
                alreadyReported = skipErrorDialog,
            ),
        )
    }

    private fun onThumbnailGenerated(thumbnail: Bitmap) {
        viewfinder.onAction(CaptureAction.PictureThumbnailReady(thumbnail = thumbnail))
    }

    private fun detailsOf(exception: Throwable): PictureFailureDetails {
        return PictureFailureDetails(
            name = exception.javaClass.name,
            stackTrace = exception.printStackTraceToString(),
        )
    }

    private fun finish(pending: PendingCapture) {
        if (pendingCapture === pending) {
            pendingCapture = null
        }
    }

    companion object {
        private const val TAG = "ImageCapturer"
    }
}
