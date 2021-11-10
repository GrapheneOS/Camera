package app.grapheneos.camera.capturer

import android.content.ContentValues
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageCapturer(private val mActivity: MainActivity) {
    private val imageFileFormat = ".jpg"

    private fun genOutputBuilderForImage():
            ImageCapture.OutputFileOptions.Builder {

        var fileName: String

        val sdf = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        )
        val date = Date()
        fileName = sdf.format(date)
        fileName = "IMG_$fileName$imageFileFormat"

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(imageFileFormat)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }

        return ImageCapture.OutputFileOptions.Builder(
            mActivity.contentResolver,
            CamConfig.imageCollectionUri,
            contentValues
        )
    }

    val isTakingPicture: Boolean
        get() = mActivity.previewLoader.visibility == View.VISIBLE

    fun takePicture() {
        if (mActivity.config.camera == null) return

        if(isTakingPicture){
            mActivity.showMessage(
                "Please wait for the last image to get processed..."
            )
            return
        }

        val outputFileOptionsBuilder = genOutputBuilderForImage()
        val imageMetadata = ImageCapture.Metadata()

        imageMetadata.isReversedHorizontal = mActivity.config.saveImageAsPreviewed

        if (mActivity.config.requireLocation) {

            if (mActivity.locationListener.lastKnownLocation == null) {
                mActivity.showMessage(
                    "Couldn't attach location to image since it's" +
                            " currently unavailable"
                )
            } else {
                imageMetadata.location =
                    mActivity.locationListener.lastKnownLocation
            }
        }

        outputFileOptionsBuilder.setMetadata(imageMetadata)

        val outputFileOptions = outputFileOptionsBuilder.build()

        mActivity.previewLoader.visibility = View.VISIBLE
        mActivity.config.snapPreview()
        mActivity.config.imageCapture!!.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(mActivity),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "Image saved successfully!")
                    mActivity.config.mPlayer.playShutterSound()

                    if (mActivity.config.selfIlluminate) {

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

                    val imageUri = outputFileResults.savedUri

                    mActivity.config.latestUri = imageUri

                    if(mActivity is SecureMainActivity) {
                        mActivity.capturedFilePaths.add(imageUri.toString())
                    }

                    mActivity.previewLoader.visibility = View.GONE
                    mActivity.config.updatePreview()
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    mActivity.previewLoader.visibility = View.GONE
                }
            })
    }

    companion object {
        private const val TAG = "ImageCapturer"
    }
}