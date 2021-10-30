package app.grapheneos.camera.capturer

import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.camera.video.ActiveRecording
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoCapturer(private val mActivity: MainActivity) {

    var isRecording = false
        private set

    private val videoFileFormat = ".mp4"

    var activeRecording: ActiveRecording? = null

    private lateinit var outputUri : Uri

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

    private fun generateFileForVideo(): ParcelFileDescriptor {
        var fileName: String
        val sdf = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        )

        val date = Date()
        fileName = sdf.format(date)
        fileName = "VID_$fileName$videoFileFormat"

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(videoFileFormat)

        val resolver = mActivity.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
            put(MediaStore.MediaColumns.DATE_ADDED, date.time)
            put(MediaStore.MediaColumns.DATE_TAKEN, date.time)
            put(MediaStore.MediaColumns.DATE_MODIFIED, date.time)
        }

        val videoUri = resolver.insert(CamConfig.videoCollectionUri, contentValues)!!

        mActivity.config.latestUri = videoUri
        outputUri = videoUri

        return resolver.openFileDescriptor(videoUri, "w")!!
    }

    fun startRecording() {
        if (mActivity.config.camera == null) return


        if (ActivityCompat.checkSelfPermission(
                mActivity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            beforeRecordingStarts()

            val pendingRecording =
                if (mActivity is VideoCaptureActivity && mActivity.isOutputUriAvailable()) {

                    outputUri = mActivity.outputUri

                    mActivity.config.videoCapture!!.output.prepareRecording(
                        mActivity,
                        FileDescriptorOutputOptions
                            .Builder(
                                mActivity.contentResolver.openFileDescriptor(
                                    outputUri,
                                    "w"
                                )!!
                            ).build()
                    )

                } else {
                    mActivity.config.videoCapture!!.output.prepareRecording(
                        mActivity,
                        FileDescriptorOutputOptions
                            .Builder(generateFileForVideo())
                            .build()
                    )
                }

            if (mActivity.settingsDialog.includeAudioToggle.isChecked)
                pendingRecording.withAudioEnabled()

            pendingRecording.withEventListener(
                ContextCompat.getMainExecutor(mActivity),
                {
                    if (it is VideoRecordEvent.Finalize) {
                        afterRecordingStops()

                        mActivity.config.mPlayer.playVRStopSound()

                        if (it.hasError()) {

                            if (it.error == 8) {
                                Toast.makeText(
                                    mActivity,
                                    "Recording too short to be saved",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    mActivity,
                                    "Unable to save recording (Error code: " +
                                            "${it.error})", Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {

                            if (mActivity is VideoCaptureActivity) {
                                mActivity.afterRecording(outputUri)
                                return@withEventListener
                            }

                            mActivity.previewLoader.visibility = View.VISIBLE

                            mActivity.previewLoader.visibility = View.GONE
                            mActivity.config.updatePreview()

                            isRecording = false

                            if(mActivity is SecureMainActivity) {
                                val path: String = outputUri.encodedPath!!
                                mActivity.capturedFilePaths.add(path)
                            }
                        }
                    }
                }
            )

            activeRecording = pendingRecording.start()
            mActivity.config.mPlayer.playVRStartSound()
            isRecording = true
        }
    }

    private val dp16 = 16 * mActivity.resources.displayMetrics.density
    private val dp8 = 8 * mActivity.resources.displayMetrics.density

    private fun beforeRecordingStarts() {

        mActivity.previewView.keepScreenOn = true

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
        mActivity.captureModeView.visibility = View.GONE

        if (mActivity.requiresVideoModeOnly) {
            mActivity.thirdOption.visibility = View.INVISIBLE
        }

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

        mActivity.thirdOption.visibility = View.VISIBLE

        if (mActivity !is VideoCaptureActivity) {
            mActivity.thirdCircle.setImageResource(R.drawable.option_circle)
            mActivity.captureModeView.visibility = View.VISIBLE
            mActivity.tabLayout.visibility = View.VISIBLE
        }
        cancelTimer()

        mActivity.previewView.keepScreenOn = false
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording?.close()
    }

    companion object {
        private const val TAG = "VideoCapturer"
        fun isVideo(uri: Uri): Boolean {
            return uri.encodedPath?.contains("video")==true
        }
    }
}