package app.grapheneos.camera.capturer

import android.annotation.SuppressLint
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
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import app.grapheneos.camera.App
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camerax.ImageSaver

private const val imageFileFormat = ".jpg"
var isTakingPicture: Boolean = false

class ImageCapturer(private val mActivity: MainActivity) {
    @SuppressLint("RestrictedApi")
    fun takePicture() {
        if (camConfig.camera == null) return

        if (!camConfig.canTakePicture) {
            mActivity.showMessage(R.string.unsupported_taking_picture_while_recording)
            return
        }

        if (isTakingPicture) {
            mActivity.showMessage(R.string.image_processing_pending)
            return
        }

        val imageMetadata = ImageCapture.Metadata()

        imageMetadata.isReversedHorizontal =
            camConfig.lensFacing ==
                    CameraSelector.LENS_FACING_FRONT &&
                    camConfig.saveImageAsPreviewed

        if (camConfig.requireLocation) {

            val location = (mActivity.applicationContext as App).getLocation()
            if (location == null) {
                mActivity.showMessage(R.string.location_unavailable)
            } else {
                imageMetadata.location = location
            }
        }

        val imageSavedCallbackWrapper: ImageSaver.OnImageSavedCallback =
            object : ImageSaver.OnImageSavedCallback {
                override fun onImageSaved(mSavedUri: Uri?) {
                    if (mSavedUri != null) {
                        camConfig.addToGallery(mSavedUri)
                    }

                    if (mActivity is SecureMainActivity) {
                        mActivity.capturedFilePaths.add(0, camConfig.latestUri.toString())
                    }

                    mActivity.runOnUiThread {
                        camConfig.updatePreview()
                        mActivity.previewLoader.visibility = View.GONE
                    }
                    Log.i(TAG, "Image saved successfully")
                }

                override fun onError(
                    saveError: ImageSaver.SaveError,
                    message: String,
                    cause: Throwable?
                ) {
                    cause?.printStackTrace()
                    mActivity.runOnUiThread {
                        mActivity.previewLoader.visibility = View.GONE
                    }
                }
            }
        camConfig.imageCapture!!.takePicture(
            ContextCompat.getMainExecutor(mActivity),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)
                    Log.i(TAG, "Image Capture successfully!")
                    camConfig.mPlayer.playShutterSound()
                    camConfig.snapPreview()

                    mActivity.runOnUiThread {
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

                    isTakingPicture = false
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    mActivity.previewLoader.visibility = View.GONE
                    isTakingPicture = false
                }
            }
        )
        isTakingPicture = true
    }

    companion object {
        private const val TAG = "ImageCapturer"
    }
}
