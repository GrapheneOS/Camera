package app.grapheneos.camera.capturer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import app.grapheneos.camera.App
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
import app.grapheneos.camera.ui.activities.SecureMainActivity

private const val imageFileFormat = ".jpg"
var isTakingPicture: Boolean = false

class ImageCapturer(private val mActivity: MainActivity) {

    @SuppressLint("RestrictedApi")
    fun takePicture() {
        if (camConfig.camera == null) {
            return
        }

        if (!camConfig.canTakePicture) {
            mActivity.showMessage(R.string.unsupported_taking_picture_while_recording)
            return
        }

        if (isTakingPicture) {
            mActivity.showMessage(R.string.image_processing_pending)
            return
        }

        val imageMetadata = ImageCapture.Metadata()
        imageMetadata.isReversedHorizontal = camConfig.lensFacing == CameraSelector.LENS_FACING_FRONT
                && camConfig.saveImageAsPreviewed

        if (camConfig.requireLocation) {
            val location = (mActivity.applicationContext as App).getLocation()
            if (location == null) {
                mActivity.showMessage(R.string.location_unavailable)
            } else {
                imageMetadata.location = location
            }
        }

        val preview = mActivity.imagePreview

        val imageCapture = camConfig.imageCapture!!

        val imageSaver = ImageSaver(
            this,
            mActivity.applicationContext,
            imageCapture.jpegQuality,
            camConfig.storageLocation,
            imageFileFormat,
            imageMetadata,
            camConfig.removeExifAfterCapture,
            targetThumbnailWidth = preview.width,
            targetThumbnailHeight = preview.height,
        )

        isTakingPicture = true

        imageCapture.takePicture(ImageSaver.imageCaptureCallbackExecutor, imageSaver)
    }

    fun onCaptureSuccess() {
        isTakingPicture = false

        camConfig.mPlayer.playShutterSound()
        camConfig.snapPreview()

        mActivity.previewLoader.visibility = View.VISIBLE
        if (camConfig.selfIlluminate) {

            val animation: Animation = AlphaAnimation(0.8f, 0f)
            animation.duration = 200
            animation.interpolator = LinearInterpolator()
            animation.fillAfter = true

            mActivity.mainOverlay.setImageResource(android.R.color.white)

            animation.setAnimationListener(
                object : Animation.AnimationListener {
                    override fun onAnimationStart(p0: Animation?) {
                        mActivity.mainOverlay.visibility = View.VISIBLE
                    }

                    override fun onAnimationEnd(p0: Animation?) {
                        mActivity.mainOverlay.visibility = View.INVISIBLE
                        mActivity.mainOverlay.setImageResource(android.R.color.transparent)
                        mActivity.updateLastFrame()

                        mActivity.mainOverlay.layoutParams =
                            (mActivity.mainOverlay.layoutParams as FrameLayout.LayoutParams).apply {
                                this.setMargins(
                                    leftMargin,
                                    (46 * mActivity.resources.displayMetrics.density).toInt(), // topMargin
                                    rightMargin,
                                    (40 * mActivity.resources.displayMetrics.density).toInt() // bottomMargin
                                )
                            }
                    }

                    override fun onAnimationRepeat(p0: Animation?) {}

                }
            )

            mActivity.mainOverlay.startAnimation(animation)
        }
    }

    fun onCaptureError(exception: ImageCaptureException) {
        Log.e(TAG, "onCaptureError", exception)

        isTakingPicture = false
        mActivity.previewLoader.visibility = View.GONE
        mActivity.showMessage(R.string.unable_to_capture_image)
    }

    fun onImageSaverSuccess(uri: Uri) {
        camConfig.addToGallery(uri)

        if (mActivity is SecureMainActivity) {
            mActivity.capturedFilePaths.add(0, camConfig.latestUri.toString())
        }
    }

    fun onStorageLocationNotFound() {
        camConfig.onStorageLocationNotFound()
    }

    fun onImageSaverError(exception: ImageSaverException) {
        Log.e(TAG, "onImageSaverError", exception)
        mActivity.previewLoader.visibility = View.GONE
        mActivity.showMessage(R.string.unable_to_save_image)
    }

    fun onThumbnailGenerated(thumbnail: Bitmap) {
        mActivity.previewLoader.visibility = View.GONE
        mActivity.imagePreview.setImageBitmap(thumbnail)
    }

    companion object {
        private const val TAG = "ImageCapturer"
    }
}
