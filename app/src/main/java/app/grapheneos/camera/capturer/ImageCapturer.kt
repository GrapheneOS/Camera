package app.grapheneos.camera.capturer

import app.grapheneos.camera.CamConfig.Companion.getExtension
import app.grapheneos.camera.ui.MainActivity
import androidx.camera.core.ImageCapture
import androidx.core.content.ContextCompat
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.camera.core.ImageCaptureException
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
        mActivity.config.imageCapture!!.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(mActivity),
            object: ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "Image saved successfully!")
                    val imageUri = outputFileResults.savedUri
                    if (imageUri != null) {
                        val path = imageUri.encodedPath!!
                        val bm = BitmapFactory.decodeFile(path)
                        val file = File(path)
                        mActivity.config.setLatestFile(file)
                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(
                                getExtension(
                                    File(path)
                                )
                            )
                        MediaScannerConnection.scanFile(
                            mActivity, arrayOf(file.parent), arrayOf(mimeType)
                        ) { _: String?, uri: Uri ->
                            Log.d(
                                TAG, "Image capture scanned" +
                                        " into media store: " + uri
                            )
                            mActivity.runOnUiThread {
                                mActivity.previewLoader.visibility = View.GONE
                                mActivity.imagePreview.setImageBitmap(bm)
                            }
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