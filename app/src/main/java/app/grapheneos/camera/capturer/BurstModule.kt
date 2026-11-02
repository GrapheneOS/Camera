package app.grapheneos.camera.capturer

import android.content.ContentValues
import android.media.AudioManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import app.grapheneos.camera.App
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.CameraMode
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Continuous still capture with bounded, asynchronous MediaStore writes. */
class BurstModule(private val activity: MainActivity) {
    private class Sequence(val id: String) {
        var running = true
        var continuous = false
        var issued = 0
        var saved = 0
        var inFlight = 0
        var lastFeedbackAtMs = 0L
    }

    private var current: Sequence? = null
    private var visible: Sequence? = null
    private val audioManager by lazy { activity.getSystemService(AudioManager::class.java) }
    private val vibrator by lazy { activity.getSystemService(Vibrator::class.java) }

    fun start() {
        if (current != null || activity.camConfig.currentMode != CameraMode.BURST) return
        if (activity.camConfig.camera == null || !activity.camConfig.canTakePicture ||
            activity.camConfig.imageCapture == null) return

        val id = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val sequence = Sequence(id)
        current = sequence
        visible = sequence
        showCount(sequence)
        fillQueue(sequence)
    }

    fun enableContinuous() {
        val sequence = current ?: return
        sequence.continuous = true
        fillQueue(sequence)
    }

    fun stop() {
        current?.running = false
        current = null
    }

    fun onModeExit() {
        stop()
        visible = null
    }

    /** Allows keyboard, volume and accessibility shutter actions to capture one frame. */
    fun takeSingle() {
        if (current != null) return
        start()
        stop()
    }

    private fun fillQueue(sequence: Sequence) {
        if (current !== sequence || !sequence.running ||
            activity.camConfig.currentMode != CameraMode.BURST) return
        val limit = if (sequence.continuous) MAX_IN_FLIGHT else if (sequence.issued == 0) 1 else 0
        while (sequence.inFlight < limit && sequence.running && current === sequence) {
            val capture = activity.camConfig.imageCapture ?: run { stop(); return }
            val index = ++sequence.issued
            sequence.inFlight++
            val fileName = "BURST_${sequence.id}_${index.toString().padStart(3, '0')}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, BURST_DIRECTORY)
            }
            val metadata = ImageCapture.Metadata().apply {
                isReversedHorizontal = activity.camConfig.lensFacing ==
                    CameraSelector.LENS_FACING_FRONT && activity.camConfig.saveImageAsPreviewed
                if (activity.camConfig.requireLocation) {
                    location = (activity.applicationContext as App).getLocation()
                }
            }
            val options = ImageCapture.OutputFileOptions.Builder(
                activity.contentResolver, CamConfig.imageCollectionUri, values
            ).setMetadata(metadata).build()

            try {
                capture.takePicture(options, activity.mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            sequence.inFlight--
                            val uri = result.savedUri
                            if (uri != null) {
                                sequence.saved++
                                playCaptureFeedback(sequence)
                                val captured = CapturedItem(ITEM_TYPE_IMAGE, sequence.id, uri, fileName)
                                activity.imageCapturer.onImageSaverSuccess(captured)
                                if (visible === sequence &&
                                    activity.camConfig.currentMode == CameraMode.BURST) {
                                    showCount(sequence)
                                }
                            } else {
                                onSaveFailure(sequence)
                                return
                            }
                            fillQueue(sequence)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            sequence.inFlight--
                            onSaveFailure(sequence)
                        }
                    })
            } catch (exception: RuntimeException) {
                sequence.inFlight--
                onSaveFailure(sequence)
                return
            }
        }
    }

    private fun playCaptureFeedback(sequence: Sequence) {
        if (visible !== sequence || activity.camConfig.currentMode != CameraMode.BURST ||
            activity.isFinishing || activity.isDestroyed) return

        val ringerMode = audioManager?.ringerMode ?: return
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

        val now = SystemClock.uptimeMillis()
        val interval = if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            VIBRATION_INTERVAL_MS
        } else {
            SHUTTER_SOUND_INTERVAL_MS
        }
        if (sequence.lastFeedbackAtMs != 0L &&
            now - sequence.lastFeedbackAtMs < interval) return
        sequence.lastFeedbackAtMs = now

        when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> activity.camConfig.mPlayer.playShutterSound()
            AudioManager.RINGER_MODE_VIBRATE -> vibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            )
        }
    }

    private fun onSaveFailure(sequence: Sequence) {
        if (current === sequence) stop()
        if (visible === sequence && activity.camConfig.currentMode == CameraMode.BURST &&
            !activity.isFinishing && !activity.isDestroyed) {
            activity.showMessage(R.string.unable_to_save_image)
        }
    }

    private fun showCount(sequence: Sequence) {
        activity.cbText.text = sequence.saved.toString()
        activity.cbText.visibility = View.VISIBLE
    }

    companion object {
        private const val MAX_IN_FLIGHT = 2
        private const val SHUTTER_SOUND_INTERVAL_MS = 125L
        private const val VIBRATION_INTERVAL_MS = 160L
        private const val BURST_DIRECTORY = "DCIM/Burst/"
    }
}
