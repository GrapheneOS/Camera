package app.grapheneos.camera.capturer

import android.annotation.SuppressLint
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
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import app.grapheneos.camerax.ImageSaver
import app.grapheneos.camerax.OutputFileOptions
import app.grapheneos.camera.App
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.clearExif
import app.grapheneos.camera.fixExif
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
import app.grapheneos.camera.ui.activities.SecureMainActivity
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageCapturer(private val mActivity: MainActivity) {
    private val imageFileFormat = ".jpg"
    private val mainExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @SuppressLint("RestrictedApi")
    private val sequentialExecutor = CameraXExecutors.newSequentialExecutor(executor)

    private fun genOutputBuilderForImage():
            OutputFileOptions {

        var fileName: String

        val sdf = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        )
        val date = Date()
        fileName = sdf.format(date)
        fileName = "IMG_$fileName$imageFileFormat"

        val mimeType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(imageFileFormat) ?: "image/*"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        }

        if (camConfig.storageLocation.isEmpty()) {

            contentValues.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "DCIM/Camera"
            )

            return OutputFileOptions.OutputFileOptionsMediaStore(
                mActivity.contentResolver,
                CamConfig.imageCollectionUri,
                contentValues
            )

        } else {
            try {
                val parent = DocumentFile.fromTreeUri(
                    mActivity,
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

                return OutputFileOptions.OutputFileOptionsOutputStream(oStream)
            } catch (exception: NullPointerException) {
                throw FileNotFoundException("The default storage location seems to have been deleted.")
            }
        }
    }

    val isTakingPicture: Boolean
        get() = mActivity.previewLoader.visibility == View.VISIBLE

    @SuppressLint("RestrictedApi")
    fun takePicture() {
        if (camConfig.camera == null) return

        if (!camConfig.canTakePicture) {
            mActivity.showMessage("Your device unfortunately doesn't support taking pictures while recording a video")
            return
        }

        if (isTakingPicture) {
            mActivity.showMessage(
                "Please wait for the last image to get processed..."
            )
            return
        }

        val outputFileOptions: OutputFileOptions

        try {
            outputFileOptions = genOutputBuilderForImage()
        } catch (exception: FileNotFoundException) {
            camConfig.onStorageLocationNotFound()
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
                mActivity.showMessage(
                    "Couldn't attach location to image since it's" +
                            " currently unavailable"
                )
            } else {
                imageMetadata.location = location
            }
        }

        outputFileOptions.metadata = imageMetadata

        val imageSavedCallbackWrapper: ImageSaver.OnImageSavedCallback =
            object : ImageSaver.OnImageSavedCallback {
                override fun onImageSaved(mSavedUri: Uri?) {
                    if (mSavedUri != null) {
                        camConfig.addToGallery(mSavedUri)
                    }

                    if (mActivity is SecureMainActivity) {
                        mActivity.capturedFilePaths.add(0, camConfig.latestUri.toString())
                    }

                    camConfig.latestUri?.let {
                        fixExif(mActivity, it)
                        clearExif(mActivity, it)
                    }

                    mActivity.runOnUiThread {
                        camConfig.updatePreview()
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
        mActivity.previewLoader.visibility = View.VISIBLE
        camConfig.snapPreview()
        camConfig.imageCapture!!.takePicture(
            ContextCompat.getMainExecutor(mActivity),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)
                    Log.i(TAG, "Image Capture successfully!")
                    camConfig.mPlayer.playShutterSound()

                    mActivity.runOnUiThread {
                        mActivity.previewLoader.visibility = View.GONE
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
                    executor.execute {
                        ImageSaver(
                            image,
                            outputFileOptions,
                            image.imageInfo.rotationDegrees,
                            100,
                            mainExecutor,
                            sequentialExecutor,
                            imageSavedCallbackWrapper
                        ).run()
                    }
                }

            }
        )
    }

    companion object {
        private const val TAG = "ImageCapturer"
    }
}
