package app.grapheneos.camera.capturer

import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.webkit.MimeTypeMap
import androidx.camera.video.Recording
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import java.lang.NullPointerException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.FileNotFoundException

class VideoCapturer(private val mActivity: MainActivity) {

    var isRecording = false
        private set

    private val videoFileFormat = ".mp4"

    private var recording: Recording? = null

    var isPaused = false
        set(value) {
            if (isRecording) {
                if (value) {
                    recording?.pause()
                    pauseTimer()
                    mActivity.flipCamIcon.setImageResource(R.drawable.play)
                } else {
                    recording?.resume()
                    startTimer()
                    mActivity.flipCamIcon.setImageResource(R.drawable.pause)
                }
            }
            field = value
        }

    private val handler = Handler(Looper.getMainLooper())
    private var elapsedSeconds: Int = 0
    private val runnable = Runnable {
        ++elapsedSeconds
        val secs = padTo2(elapsedSeconds % 60)
        val min = padTo2(elapsedSeconds / 60 % 60)
        val hours = padTo2(elapsedSeconds / 3600)
        val timerText: String = if (hours == "00") {
            "$min:$secs"
        } else {
            "$hours:$min:$secs"
        }
        mActivity.timerView.text = timerText
        startTimer()
    }

    private fun padTo2(time: Int): String {
        return String.format("%1$" + 2 + "s", time).replace(' ', '0')
    }

    private fun startTimer() {
        handler.postDelayed(runnable, 1000)
    }

    private fun cancelTimer() {
        elapsedSeconds = 0
        handler.removeCallbacks(runnable)
    }

    private fun pauseTimer() {
        handler.removeCallbacks(runnable)
    }

    private fun genPendingRecording(): PendingRecording {
        var fileName: String
        val sdf = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        )

        val date = Date()
        fileName = sdf.format(date)
        fileName = "VID_$fileName$videoFileFormat"

        val mimeType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(videoFileFormat) ?: "video/mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }

        if (mActivity is VideoCaptureActivity
            && mActivity.isOutputUriAvailable()
        ) {

            return camConfig.videoCapture!!.output.prepareRecording(
                mActivity,
                FileDescriptorOutputOptions
                    .Builder(
                        mActivity.contentResolver.openFileDescriptor(
                            mActivity.outputUri,
                            "w"
                        )!!
                    ).build()
            )

        } else {

            if (camConfig.storageLocation.isEmpty()) {
                return camConfig.videoCapture!!.output.prepareRecording(
                    mActivity,
                    MediaStoreOutputOptions.Builder(
                        mActivity.contentResolver,
                        CamConfig.videoCollectionUri
                    ).setContentValues(contentValues)
                        .build()
                )
            } else {
                try {
                    val parent = DocumentFile.fromTreeUri(
                        mActivity, Uri.parse(
                            camConfig.storageLocation
                        )
                    )!!

                    val child = parent.createFile(
                        mimeType,
                        fileName
                    )!!

                    val fd = mActivity.contentResolver.openFileDescriptor(
                        child.uri,
                        "w"
                    )!!

                    camConfig.addToGallery(child.uri)

                    return camConfig.videoCapture!!.output.prepareRecording(
                        mActivity,
                        FileDescriptorOutputOptions.Builder(fd).build()
                    )

                } catch (exception: NullPointerException) {
                    throw FileNotFoundException()
                }
            }
        }
    }

    fun startRecording() {
        if (camConfig.camera == null) return

        val pendingRecording: PendingRecording?

        try {
            pendingRecording = genPendingRecording()
        } catch (exception: Exception) {
            camConfig.onStorageLocationNotFound()
            return
        }

        if (mActivity.settingsDialog.includeAudioToggle.isChecked) {
            if (ActivityCompat.checkSelfPermission(
                    mActivity,
                    Manifest.permission.RECORD_AUDIO
                )
                == PackageManager.PERMISSION_GRANTED
            ) {
                pendingRecording.withAudioEnabled()
            } else {
                mActivity.requestAudioPermission()
                return
            }
        }

        beforeRecordingStarts()

        recording = pendingRecording.start(
            ContextCompat.getMainExecutor(mActivity)
        ) {
            if (it is VideoRecordEvent.Finalize) {
                afterRecordingStops()

                camConfig.mPlayer.playVRStopSound()

                if (it.hasError()) {

                    if (it.error == 8) {
                        mActivity.showMessage(
                            "Recording too short to be saved"
                        )
                    } else {
                        mActivity.showMessage(
                            "Unable to save recording (Error code: " +
                                    "${it.error})"
                        )
                    }
                } else {

                    val outputUri = it.outputResults.outputUri

                    try {

                        val stream = mActivity.contentResolver
                            .openInputStream(
                                outputUri
                            ) ?: throw NullPointerException()

                        stream.close()

                        if (mActivity is VideoCaptureActivity) {
                            mActivity.afterRecording(outputUri)
                            return@start
                        } else {
                            camConfig.addToGallery(outputUri)
                        }

                    } catch (exception: Exception) {

                        if (mActivity is VideoCaptureActivity) {
                            mActivity.afterRecording(mActivity.outputUri)
                            return@start
                        }
                    }

                    camConfig.updatePreview()

                    if (mActivity is SecureMainActivity) {
                        mActivity.capturedFilePaths.add(0, outputUri.toString())
                    }
                }
            }
        }

        isRecording = true
    }

    private val dp16 = 16 * mActivity.resources.displayMetrics.density
    private val dp8 = 8 * mActivity.resources.displayMetrics.density

    private fun beforeRecordingStarts() {

        mActivity.previewView.keepScreenOn = true

        // TODO: Uncomment this once the main indicator UI gets implemented
        // mActivity.micOffIcon.visibility = View.GONE
        camConfig.mPlayer.playVRStartSound()

        val gd: GradientDrawable = mActivity.captureButton.drawable as GradientDrawable

        val animator = ValueAnimator.ofFloat(dp16, dp8)

        animator.setDuration(300)
            .addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                gd.cornerRadius = value
            }

        animator.start()

        mActivity.settingsDialog.includeAudioToggle.isEnabled = false
        mActivity.settingsDialog.videoQualitySpinner.isEnabled = false

        mActivity.flipCamIcon.setImageResource(R.drawable.pause)
        isPaused = false
        mActivity.cancelButtonView.visibility = View.GONE

        if (mActivity.requiresVideoModeOnly) {
            mActivity.thirdOption.visibility = View.INVISIBLE
        }

        mActivity.settingsDialog.lRadio.isEnabled = false
        mActivity.settingsDialog.qRadio.isEnabled = false

        mActivity.thirdCircle.setImageResource(R.drawable.camera_shutter)
        mActivity.tabLayout.visibility = View.INVISIBLE
        mActivity.timerView.setText(R.string.start_value_timer)
        mActivity.timerView.visibility = View.VISIBLE
        startTimer()
    }

    private fun afterRecordingStops() {

        val gd: GradientDrawable = mActivity.captureButton.drawable as GradientDrawable

        val animator = ValueAnimator.ofFloat(dp8, dp16)

        animator.setDuration(300)
            .addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                gd.cornerRadius = value
            }

        animator.start()

        mActivity.timerView.visibility = View.GONE
        mActivity.flipCamIcon.setImageResource(R.drawable.flip_camera)

        mActivity.settingsDialog.includeAudioToggle.isEnabled = true
        mActivity.settingsDialog.videoQualitySpinner.isEnabled = true

        if (mActivity !is VideoCaptureActivity) {
            mActivity.thirdOption.visibility = View.VISIBLE
        }

        if (!mActivity.requiresVideoModeOnly) {
            mActivity.settingsDialog.lRadio.isEnabled = true
            mActivity.settingsDialog.qRadio.isEnabled = true
        }

        if (mActivity !is VideoCaptureActivity) {
            mActivity.thirdCircle.setImageResource(R.drawable.option_circle)
            mActivity.cancelButtonView.visibility = View.VISIBLE
            mActivity.tabLayout.visibility = View.VISIBLE
        }
        cancelTimer()

        mActivity.previewView.keepScreenOn = false

        // TODO: Uncomment this once the main indicator UI gets implemented
        // if (!mActivity.config.includeAudio)
        //   mActivity.micOffIcon.visibility = View.VISIBLE

        isRecording = false

        mActivity.forceUpdateOrientationSensor()
    }

    fun stopRecording() {
        recording?.stop()
        recording?.close()
    }

    companion object {
        //        private const val TAG = "VideoCapturer"
        fun isVideo(uri: Uri): Boolean {
            return uri.encodedPath?.contains("video") == true ||
                    uri.encodedPath?.endsWith(".mp4") == true
        }
    }
}
