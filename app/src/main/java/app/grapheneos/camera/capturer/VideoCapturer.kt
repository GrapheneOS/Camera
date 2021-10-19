package app.grapheneos.camera.capturer

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.ActivityCompat
import app.grapheneos.camera.ui.activities.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.animation.ValueAnimator
import app.grapheneos.camera.R
import android.graphics.drawable.GradientDrawable
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import java.lang.Exception

class VideoCapturer(private val mActivity: MainActivity) {

    var isRecording = false
        private set

    private val videoFileFormat = ".mp4"

    var activeRecording: ActiveRecording? = null

    var audioEnabled: Boolean = true

    var isPaused = false
        set(value) {
            if(isRecording) {
                if(value) {
                    activeRecording?.pause()
                    pauseTimer()
                    mActivity.flipCamIcon.setImageResource(R.drawable.play)
                }
                else {
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

    private fun pauseTimer(){
        handler.removeCallbacks(runnable)
    }

    private fun generateFileForVideo(): File {
        var fileName: String
        val sdf = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ) /* w  ww .  j av  a  2s.  co  m*/
        fileName = sdf.format(Date())
        fileName = "VID_$fileName$videoFileFormat"
        return File(mActivity.config.parentDirPath, fileName)
    }

    val isLatestMediaVideo: Boolean
        get() = isVideo(
            mActivity.config.latestMediaFile
        )

    fun startRecording() {
        if (mActivity.config.camera == null) return


        if (ActivityCompat.checkSelfPermission(
                mActivity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            beforeRecordingStarts()

            val pendingRecording =
                if(mActivity is VideoCaptureActivity && mActivity.isOutputUriAvailable()) {

                mActivity.config.videoCapture!!.output.prepareRecording(
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
                mActivity.config.videoCapture!!.output.prepareRecording(
                    mActivity,
                    FileOutputOptions
                        .Builder(generateFileForVideo())
                        .build()
                )
            }

            if (audioEnabled)
                pendingRecording.withAudioEnabled()

            pendingRecording.withEventListener(
                ContextCompat.getMainExecutor(mActivity),
                {
                    if (it is VideoRecordEvent.Finalize) {
                        isRecording = false
                        afterRecordingStops()

                        if (it.hasError()) {

                            if(it.error == 8) {
                                Toast.makeText(mActivity,
                                    "Recording too short to be saved",
                                    Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(mActivity,
                                    "Unable to save recording (Error code: " +
                                            "${it.error})", Toast.LENGTH_LONG).show()
                            }
                        } else {

                            val outputUri = it.outputResults.outputUri

                            if(mActivity is VideoCaptureActivity){
                                mActivity.afterRecording(outputUri)
                                return@withEventListener
                            }

                            mActivity.previewLoader.visibility = View.VISIBLE
                            val path: String = outputUri.encodedPath!!

                            val file = File(path)
                            mActivity.config.setLatestFile(file)
                            val mimeType = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(
                                    file.extension
                                )

                            MediaScannerConnection.scanFile(
                                mActivity, arrayOf(outputUri.encodedPath), arrayOf(mimeType)
                            ) { _: String?, uri: Uri? ->
                                Log.d(
                                    TAG, "Video capture scanned" +
                                            " into media store: " + uri
                                )
                                mActivity.runOnUiThread {
                                    mActivity.previewLoader.visibility = View.GONE
                                    mActivity.config.updatePreview()
                                }
                            }
                        }
                    }
                }
            )

            activeRecording = pendingRecording.start()
            isRecording = true
        }
    }

    private val dp16 = 16 * mActivity.resources.displayMetrics.density
    private val dp8 =  8 * mActivity.resources.displayMetrics.density

    private fun beforeRecordingStarts() {

        val gd: GradientDrawable = mActivity.captureButton.drawable as GradientDrawable

        val animator = ValueAnimator.ofFloat(dp16, dp8)

        animator.setDuration(300)
            .addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                gd.cornerRadius = value
            }

        animator.start()

        mActivity.flipCamIcon.setImageResource(R.drawable.pause)
        isPaused = false
        mActivity.captureModeView.visibility = View.GONE
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

        if(mActivity !is VideoCaptureActivity) {
            mActivity.thirdCircle.setImageResource(R.drawable.option_circle)
            mActivity.captureModeView.visibility = View.VISIBLE
            mActivity.tabLayout.visibility = View.VISIBLE
        }
        cancelTimer()
    }

    fun stopRecording() {

        activeRecording?.stop()
        activeRecording?.close()
    }

    companion object {
        private const val TAG = "VideoCapturer"
        fun isVideo(file: File?): Boolean {
            return file?.extension == "mp4"
        }
    }
}