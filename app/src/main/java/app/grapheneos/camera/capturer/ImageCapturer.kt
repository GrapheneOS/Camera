package app.grapheneos.camera.capturer

import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import app.grapheneos.camera.ui.activities.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(imageFile)
            .build()
        mActivity.previewLoader.visibility = View.VISIBLE
        mActivity.config.snapPreview()
        mActivity.config.imageCapture!!.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(mActivity),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "Image saved successfully!")
                    val imageUri = outputFileResults.savedUri
                    if (imageUri != null) {
                        val path = imageUri.encodedPath!!
                        val file = File(path)
                        mActivity.config.latestFile = file
                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(
                                File(path).extension
                            )

                        mActivity.previewLoader.visibility = View.GONE
                        mActivity.imagePreview.setImageURI(imageUri)

                        MediaScannerConnection.scanFile(
                            mActivity,
                            arrayOf(file.absolutePath),
                            arrayOf(mimeType)
                        ) { _: String?, uri: Uri ->
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