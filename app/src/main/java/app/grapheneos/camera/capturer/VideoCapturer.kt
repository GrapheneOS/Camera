package app.grapheneos.camera.capturer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.camera.core.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.grapheneos.camera.CamConfig.Companion.getVideoThumbnail
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoCapturer(private val mActivity: MainActivity) {

    var isRecording = false
        private set

    private val videoFileFormat = ".mp4"

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

    @SuppressLint("RestrictedApi")
    fun startRecording() {
        if (mActivity.config.camera == null) return
        val videoFile = generateFileForVideo()
        val outputOptions = VideoCapture.OutputFileOptions.Builder(videoFile)
            .build()

        // Will always be true if we reach here
        if (ActivityCompat.checkSelfPermission(
                mActivity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            beforeRecordingStarts()
            mActivity.config.videoCapture!!.startRecording(
                outputOptions,
                ContextCompat.getMainExecutor(mActivity),
                object : VideoCapture.OnVideoSavedCallback {
                    override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                        isRecording = false
                        mActivity.previewLoader.visibility = View.VISIBLE
                        val videoUri = outputFileResults.savedUri
                        if (videoUri != null) {
                            val path: String = videoUri.encodedPath!!
                            var tBm: Bitmap? = null
                            try {
                                tBm = getVideoThumbnail(path)
                            } catch (throwable: Throwable) {
                                throwable.printStackTrace()
                            }

                            val file = File(path)
                            mActivity.config.setLatestFile(file)
                            val mimeType = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(
                                    file.extension
                                )
                            val bm = tBm
                            MediaScannerConnection.scanFile(
                                mActivity, arrayOf(file.parent), arrayOf(mimeType)
                            ) { _: String?, uri: Uri ->
                                Log.d(
                                    TAG, "Image capture scanned" +
                                            " into media store: " + uri
                                )
                                mActivity.runOnUiThread {
                                    mActivity.previewLoader.visibility = View.GONE
                                    if (bm != null) mActivity.imagePreview
                                        .setImageBitmap(bm)
                                }
                            }
                        }
                        afterRecordingStops()
                    }

                    override fun onError(
                        videoCaptureError: Int,
                        message: String,
                        cause: Throwable?
                    ) {
                        isRecording = false
                        afterRecordingStops()
                        if (videoCaptureError == 6) {
                            Toast.makeText(
                                mActivity,
                                "Video too short to be saved", Toast.LENGTH_LONG
                            )
                                .show()
                            return
                        }
                        Toast.makeText(
                            mActivity, """
     Unable to save recording.
     Error Code: $videoCaptureError
     """.trimIndent(), Toast.LENGTH_LONG
                        )
                            .show()
                    }
                })
            isRecording = true
        }
    }

    private fun beforeRecordingStarts() {
        mActivity.captureButton.setImageResource(R.drawable.stop_recording)
        mActivity.flipCameraCircle.visibility = View.INVISIBLE
        mActivity.captureModeView.visibility = View.GONE
        mActivity.thirdCircle.setImageResource(R.drawable.camera_shutter)
        mActivity.tabLayout.visibility = View.INVISIBLE
        mActivity.timerView.setText(R.string.start_value_timer)
        mActivity.timerView.visibility = View.VISIBLE
        startTimer()
    }

    fun afterRecordingStops() {
        mActivity.captureButton.setImageResource(R.drawable.start_recording)
        mActivity.thirdCircle.setImageResource(R.drawable.option_circle)
        mActivity.flipCameraCircle.visibility = View.VISIBLE
        mActivity.captureModeView.visibility = View.VISIBLE
        mActivity.tabLayout.visibility = View.VISIBLE
        mActivity.timerView.visibility = View.GONE
        cancelTimer()
    }

    @SuppressLint("RestrictedApi")
    fun stopRecording() {
        mActivity.config.videoCapture!!.stopRecording()
    }

    companion object {
        private const val TAG = "VideoCapturer"
        fun isVideo(file: File?): Boolean {
            return file?.extension == "mp4"
        }
    }
}