package app.grapheneos.camera.capturer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.location.Location
import android.util.Log
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.takePicture
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderScreenModel
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureDetails
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.util.printStackTraceToString
import kotlinx.coroutines.launch

private const val imageFileFormat = ".jpg"

class ImageCapturer(val mActivity: MainActivity) {

    private val viewfinder: ViewfinderScreenModel = mActivity.viewfinder

    private val session = mActivity.session

    val isTakingPicture: Boolean
        get() = currentImageSaver != null

    private var currentImageSaver : ImageSaver? = null

    @SuppressLint("RestrictedApi")
    fun takePicture() {
        if (session.camera == null) {
            return
        }

        val capture = viewfinder.uiState.value.capture

        if (!capture.canTakePicture) {
            mActivity.showMessage(R.string.unsupported_taking_picture_while_recording)
            return
        }

        if (isTakingPicture) {
            return
        }

        var location: Location? = null
        if (capture.geoTagging) {
            location = mActivity.locationRepository.currentLocation()
            if (location == null) {
                mActivity.showMessage(R.string.location_unavailable)
            }
        }

        val imageMetadata = CaptureMetadata(
            reversedHorizontal = session.lensFacing == LensFacing.FRONT &&
                capture.saveImageAsPreviewed,
            location = location,
        )

        val preview = mActivity.imagePreview

        val imageCapture = session.imageCapture!!

        val imageSaver = ImageSaver(
            this,
            mActivity.applicationContext,
            mActivity.storeCapturedImage,
            mActivity.exifMapper,
            mActivity.jpegExtractor,
            imageCapture.jpegQuality,
            mActivity.capturedItemSession.storageLocation,
            imageFileFormat,
            imageMetadata,
            capture.removeExifAfterCapture,
            targetThumbnailWidth = preview.width,
            targetThumbnailHeight = preview.height,
            pipeline = mActivity.capturedImagePipeline,
        )

        currentImageSaver = imageSaver

        mActivity.applicationScope.launch(mActivity.mainDispatcher) {
            val image = try {
                imageCapture.takePicture()
            } catch (e: ImageCaptureException) {
                imageSaver.onCaptureError(e)
                return@launch
            }

            imageSaver.onCaptureSuccess(image)
        }

        viewfinder.onAction(CaptureAction.PictureCaptureStarted)
    }

    fun cancelPendingCaptureRequest() {
        if (isTakingPicture) {
            currentImageSaver?.cancelCaptureRequest()

            currentImageSaver = null
            viewfinder.onAction(CaptureAction.PictureCaptureCancelled)
        }
    }

    fun onCaptureSuccess() {
        currentImageSaver = null

        mActivity.tunePlayer.playShutterSound()
        viewfinder.onAction(CaptureAction.PictureCaptured)
    }

    fun onCaptureError(exception: ImageCaptureException) {
        Log.e(TAG, "onCaptureError", exception)

        currentImageSaver = null

        viewfinder.onAction(
            CaptureAction.PictureCaptureFailed(
                errorCode = exception.imageCaptureError,
                details = detailsOf(exception),
            ),
        )
    }

    fun onImageSaverSuccess(item: CapturedItem) {
        mActivity.capturedItemSession.recordCapturedItem(item)

        if (mActivity is SecureMainActivity) {
            mActivity.capturedItems.add(item)
        }
    }

    fun onStorageLocationNotFound() {
        viewfinder.onAction(CaptureAction.StorageLocationNotFound)
    }

    fun onImageSaverError(exception: ImageSaverException, skipErrorDialog: Boolean) {
        Log.e(TAG, "onImageSaverError", exception)

        viewfinder.onAction(
            CaptureAction.PictureSaveFailed(
                stage = exception.place.name,
                details = detailsOf(exception),
                alreadyReported = skipErrorDialog,
            ),
        )
    }

    private fun detailsOf(exception: Throwable): PictureFailureDetails {
        return PictureFailureDetails(
            name = exception.javaClass.name,
            stackTrace = exception.printStackTraceToString(),
        )
    }

    fun onThumbnailGenerated(thumbnail: Bitmap) {
        viewfinder.onAction(CaptureAction.PictureThumbnailShown)
        mActivity.imagePreview.setImageBitmap(thumbnail)
    }

    companion object {
        private const val TAG = "ImageCapturer"
    }
}
