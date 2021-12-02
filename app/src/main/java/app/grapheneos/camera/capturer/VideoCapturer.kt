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
import androidx.camera.video.ActiveRecording
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import java.lang.NullPointerException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoCapturer(private val mActivity: MainActivity) {

    var isRecording = false
        private set

    private val videoFileFormat = ".mp4"

    var activeRecording: ActiveRecording? = null

    var isPaused = false
        set(value) {
            if (isRecording) {
                if (value) {
                    activeRecording?.pause()
                    pauseTimer()
                    mActivity.flipCamIcon.setImageResource(R.drawable.play)
                } else {
                    activeRecording?.resume()
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
        val mins = padTo2(elapsedSeconds / 60 % 60)
        val hours = padTo2(elapsedSeconds / 3600)
        val timerText: String = if (hours == "00") {
            "$mins:$secs"
        } else {
            "$hours:$mins:$secs"
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

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(videoFileFormat)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }

        if (mActivity is VideoCaptureActivity
            && mActivity.isOutputUriAvailable()) {

            return MainActivity.camConfig.videoCapture!!.output.prepareRecording(
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

            return MainActivity.camConfig.videoCapture!!.output.prepareRecording(
                mActivity,
                MediaStoreOutputOptions.Builder(
                    mActivity.contentResolver,
                    CamConfig.videoCollectionUri
                ).setContentValues(contentValues)
                    .build()
            )
        }
    }

    fun startRecording() {
        if (MainActivity.camConfig.camera == null) return

        val pendingRecording = genPendingRecording()

        if (mActivity.settingsDialog.includeAudioToggle.isChecked) {
            if (ActivityCompat.checkSelfPermission(
                    mActivity,
                    Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                pendingRecording.withAudioEnabled()
            } else {
                mActivity.requestAudioPermission()
                return
            }
        }

        pendingRecording.withEventListener(
            ContextCompat.getMainExecutor(mActivity),
            {
                if (it is VideoRecordEvent.Finalize) {
                    afterRecordingStops()

                    MainActivity.camConfig.mPlayer.playVRStopSound()

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

                        MainActivity.camConfig.latestUri = outputUri

                        if (mActivity is VideoCaptureActivity) {

                            // Sometimes the uri passed by CameraX is invalid
                            // For e.g. when the uri is explicitly passed
                            //
                            // So to deal with those cases, we're ensuring
                            // the passed uri actually exists by the below
                            // try-catch logic
                            try {

                                val stream = mActivity.contentResolver
                                    .openInputStream(
                                        outputUri
                                    ) ?: throw NullPointerException()

                                stream.close()

                                mActivity.afterRecording(outputUri)

                            } catch (exception : Exception) {
                                mActivity.afterRecording(mActivity.outputUri)
                            }

                            return@withEventListener
                        }

                        MainActivity.camConfig.updatePreview()

                        if(mActivity is SecureMainActivity) {
                            mActivity.capturedFilePaths.add(outputUri.toString())
                        }
                    }
                }
            }
        )

        beforeRecordingStarts()
        activeRecording = pendingRecording.start()
        isRecording = true
    }

    private val dp16 = 16 * mActivity.resources.displayMetrics.density
    private val dp8 = 8 * mActivity.resources.displayMetrics.density

    private fun beforeRecordingStarts() {

        mActivity.previewView.keepScreenOn = true

        // TODO: Uncomment this once the main indicator UI gets implemented
        // mActivity.micOffIcon.visibility = View.GONE
        MainActivity.camConfig.mPlayer.playVRStartSound()

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
        activeRecording?.stop()
        activeRecording?.close()
    }

    companion object {
//        private const val TAG = "VideoCapturer"
        fun isVideo(uri: Uri): Boolean {
            return uri.encodedPath?.contains("video")==true
        }
    }
}