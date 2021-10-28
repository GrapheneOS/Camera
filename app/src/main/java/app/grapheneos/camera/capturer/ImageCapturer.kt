package app.grapheneos.camera.capturer

import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageCapturer(private val mActivity: MainActivity) {
    private val imageFileFormat = ".jpg"
    private fun generateFileForImage(): File {
        var fileName: String
        val sdf = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ) /* w  ww .  j av  a  2s.  co  m*/
        fileName = sdf.format(Date())
        fileName = "IMG_$fileName$imageFileFormat"
        return File(mActivity.config.parentDirPath, fileName)
    }

    val isTakingPicture: Boolean
        get() = mActivity.previewLoader.visibility == View.VISIBLE

    fun takePicture() {
        if (mActivity.config.camera == null) return
        val imageFile = generateFileForImage()
        val outputFileOptionsBuilder = ImageCapture.OutputFileOptions.Builder(imageFile)

        if (mActivity.config.requireLocation) {

            if (mActivity.locationListener.lastKnownLocation == null) {
                Toast.makeText(
                    mActivity,
                    "Couldn't attach location to image " +
                            "since it's currently unavailable",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                outputFileOptionsBuilder.setMetadata(
                    ImageCapture.Metadata().apply {
                        location = mActivity.locationListener.lastKnownLocation
                        Log.i(TAG, "Location added to ${location?.latitude}")
                    }
                )
            }
        }

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
                    if (imageUri != null) {
                        val path = imageUri.encodedPath!!
                        val file = File(path)
                        mActivity.config.latestFile = file
                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(
                                File(path).extension
                            )

                        if(mActivity is SecureMainActivity)
                            mActivity.capturedFilePaths.add(path)

                        mActivity.previewLoader.visibility = View.GONE
                        mActivity.imagePreview.setImageURI(imageUri)

                        MediaScannerConnection.scanFile(
                            mActivity,
                            arrayOf(file.absolutePath),
                            arrayOf(mimeType)
                        ) { _: String?, uri: Uri? ->
                            Log.d(
                                TAG, "Image capture scanned" +
                                        " into media store: " + uri
                            )
                        }
                    }
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