package app.grapheneos.camera.capturer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.takePicture
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.showIgnoringShortEdgeMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderScreenModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.util.printStackTraceToString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        viewfinder.onAction(CaptureAction.PictureCaptureFailed)

        if (mActivity.isStarted) {
            val msg = mActivity.getString(R.string.unable_to_capture_image_verbose, exception.imageCaptureError)
            showErrorDialog(msg, exception)
        }
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
        viewfinder.onAction(CaptureAction.PictureSaveFailed)

        if (!mActivity.isStarted) {
            val channelId = "image_saver_error"
            val channel = NotificationChannel(channelId, mActivity.getString(R.string.unable_to_save_image),
                NotificationManager.IMPORTANCE_HIGH)

            val notif = Notification.Builder(mActivity, channelId).apply {
                setSmallIcon(R.drawable.info)
                setContentTitle(mActivity.getString(R.string.unable_to_save_image))
            }.build()

            mActivity.getSystemService(NotificationManager::class.java).let {
                it.createNotificationChannel(channel)
                it.notify(1, notif)
            }
            return
        }

        if (skipErrorDialog) {
            mActivity.showMessage(R.string.unable_to_save_image)
        } else {
            val msg = mActivity.getString(R.string.unable_to_save_image_verbose, exception.place.name)
            showErrorDialog(msg, exception)
        }
    }

    private fun showErrorDialog(message: String, exception: Throwable) {
        val ctx = mActivity

        MaterialAlertDialogBuilder(ctx).apply {
            setMessage(message)
            setPositiveButton(R.string.show_details) { _, _ ->
                val pkgName = ctx.packageName
                val pkgVersion = ctx.packageManager.getPackageInfo(pkgName, 0).longVersionCode
                val text = "osVersion: ${Build.FINGERPRINT}" +
                        "\npackage: $pkgName:$pkgVersion" +
                        "\n\n${exception.printStackTraceToString()}"

                MaterialAlertDialogBuilder(ctx).apply {
                    setItems(text.lines().toTypedArray(), null)
                    setNeutralButton(R.string.copy_to_clipboard) { _, _ ->
                        val clipData = ClipData.newPlainText(exception.javaClass.name, text)
                        val cm = mActivity.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(clipData)
                        ctx.showMessage(R.string.copied_text_to_clipboard)
                    }
                    showIgnoringShortEdgeMode()
                }
            }
            showIgnoringShortEdgeMode()
        }
    }

    fun onThumbnailGenerated(thumbnail: Bitmap) {
        viewfinder.onAction(CaptureAction.PictureThumbnailShown)
        mActivity.imagePreview.setImageBitmap(thumbnail)
    }

    companion object {
        private const val TAG = "ImageCapturer"
    }
}
