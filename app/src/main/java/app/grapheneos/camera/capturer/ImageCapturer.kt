package app.grapheneos.camera.capturer

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.clearExif
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
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

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(imageFileFormat) ?: "image/*"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        }

        if (camConfig.storageLocation.isEmpty()) {

            contentValues.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "DCIM/Camera"
            )

            return ImageCapture.OutputFileOptions.Builder(
                mActivity.contentResolver,
                CamConfig.imageCollectionUri,
                contentValues
            )

        } else {

            val parent = DocumentFile.fromTreeUri(mActivity,
                Uri.parse(
                    camConfig.storageLocation
                )
            )!!

            val child = parent.createFile(
                mimeType,
                fileName
            )!!

            val oStream = mActivity.contentResolver
                .openOutputStream(child.uri)!!

            camConfig.addToGallery(child.uri)

            return ImageCapture.OutputFileOptions.Builder(oStream)
        }
    }

    val isTakingPicture: Boolean
        get() = mActivity.previewLoader.visibility == View.VISIBLE

    fun takePicture() {
        if (camConfig.camera == null) return

        if(isTakingPicture){
            mActivity.showMessage(
                "Please wait for the last image to get processed..."
            )
            return
        }

        val outputFileOptionsBuilder = genOutputBuilderForImage()
        val imageMetadata = ImageCapture.Metadata()

        imageMetadata.isReversedHorizontal =
                camConfig.lensFacing ==
                    CameraSelector.LENS_FACING_FRONT &&
                camConfig.saveImageAsPreviewed

        if (camConfig.requireLocation) {

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
        camConfig.snapPreview()
        camConfig.imageCapture!!.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(mActivity),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "Image saved successfully!")
                    camConfig.mPlayer.playShutterSound()

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

                    val imageUri = outputFileResults.savedUri

                    if (imageUri != null) {
                        camConfig.addToGallery(imageUri)
                    }

                    if(mActivity is SecureMainActivity) {
                        mActivity.capturedFilePaths.add(camConfig.latestUri.toString())
                    }

                    clearExif(mActivity, camConfig.latestUri!!)

                    mActivity.previewLoader.visibility = View.GONE
                    camConfig.updatePreview()
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