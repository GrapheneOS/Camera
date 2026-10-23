package app.grapheneos.camera.portrait

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.view.PreviewView
import app.grapheneos.camera.CameraMode
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** never cover a moving camera subject with a frozen photo. */
class PortraitLiveController(
    private val activity: MainActivity,
    private val preview: PreviewView,
    private val overlay: ImageView,
    private val controls: FrameLayout,
    private val slider: SeekBar,
    private val label: TextView,
) {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("portrait_live", Context.MODE_PRIVATE)
    private var running = false
    private var inPortrait = false
    private var epoch = 0
    private var frameSeq = 0L
    private var lastSample = 0L
    private var mask: PortraitEngine.LiveSubjectMask? = null
    private var maskSignature: IntArray? = null
    private var maskReceivedAt = 0L
    private var displayed: Bitmap? = null
    private var percentage = prefs.getInt("blur", 55).coerceIn(0, 100)
    private var trackingSlider = false

    // A camera snapshot at about 5 FPS is a bounded compromise. The ACTUAL
    // PreviewView remains live at its normal camera frame rate underneath.
    private val sampleEveryMs = 200L
    private val maximumMaskAgeMs = 8000L
    private val trackingGridSize = 28
    private val maxTranslationCells = 5

    private val collapseControls = Runnable {
        if (trackingSlider || !inPortrait) return@Runnable
        slider.visibility = View.GONE
        label.alpha = 1f
        label.visibility = View.VISIBLE
        updateLabel()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val shouldRun = activity.camConfig.currentMode == CameraMode.PORTRAIT &&
                activity.foldSelectedMode == null && activity.isStarted &&
                activity !is SecureMainActivity
            if (shouldRun != inPortrait) setPortraitActive(shouldRun)

            if (shouldRun && PortraitEngine.isPhotoProcessing()) {
                // Portrait JPEG generation takes precedence over preview work.
                hideOverlay()
            } else if (shouldRun && percentage > 0 &&
                preview.width > 0 && preview.height > 0 &&
                preview.previewStreamState.value == PreviewView.StreamState.STREAMING &&
                SystemClock.elapsedRealtime() - lastSample >= sampleEveryMs &&
                (mask != null || PortraitEngine.canSchedulePreview())) {
                lastSample = SystemClock.elapsedRealtime()
                captureAndUpdate()
            } else if (percentage == 0 && overlay.visibility == View.VISIBLE) {
                hideOverlay()
            }
            main.postDelayed(this, 80L)
        }
    }

    init {
        PortraitEngine.setBlurPercent(percentage)
        slider.progress = percentage
        updateLabel()
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                percentage = progress
                PortraitEngine.setBlurPercent(progress)
                updateLabel()
                if (fromUser) {
                    prefs.edit().putInt("blur", percentage).apply()
                    // A slider change never starts AI inference. The previous
                    // composite fades immediately; next fresh camera frame uses
                    // the new blur radius.
                    overlay.alpha = percentage / 100f
                    lastSample = 0L
                    if (percentage == 0) hideOverlay()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                trackingSlider = true
                main.removeCallbacks(collapseControls)
                showExpanded()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                trackingSlider = false
                collapseControls.run()
            }
        })
        label.setOnClickListener { if (inPortrait) showExpanded() }
    }

    private fun updateLabel() {
        val value = activity.getString(R.string.portrait_blur_strength) + " · " + percentage + "%"
        label.text = value
        label.contentDescription = "$value. Tap to adjust"
    }

    private fun showExpanded() {
        main.removeCallbacks(collapseControls)
        label.visibility = View.INVISIBLE
        slider.visibility = View.VISIBLE
    }

    /** Grayscale grid for estimating small camera translations. */
    private fun sampleSignature(bitmap: Bitmap): IntArray {
        val side = trackingGridSize
        val mini = Bitmap.createScaledBitmap(bitmap, side, side, true)
        try {
            val pixels = IntArray(side * side)
            mini.getPixels(pixels, 0, side, 0, 0, side, side)
            return IntArray(pixels.size) { i ->
                val c = pixels[i]
                (android.graphics.Color.red(c) * 3 +
                    android.graphics.Color.green(c) * 6 +
                    android.graphics.Color.blue(c)) / 10
            }
        } finally {
            if (mini !== bitmap) mini.recycle()
        }
    }

    private data class FrameMotion(val dx: Int, val dy: Int, val mismatch: Float)

    /** Match grayscale frames within a small translation window. */
    private fun trackMotion(reference: IntArray, current: IntArray,
                            frameWidth: Int, frameHeight: Int): FrameMotion? {
        val side = trackingGridSize
        if (reference.size != side * side || current.size != side * side) return null
        val edge = maxTranslationCells
        var bestScore = Float.POSITIVE_INFINITY
        var bestDx = 0
        var bestDy = 0
        var bestResidual = Float.POSITIVE_INFINITY
        for (dy in -edge..edge) for (dx in -edge..edge) {
            // Compare a stable interior region and compensate camera auto-exposure.
            var brightnessDelta = 0
            var count = 0
            for (y in edge until side - edge) for (x in edge until side - edge) {
                brightnessDelta += current[(y + dy) * side + x + dx] - reference[y * side + x]
                count++
            }
            val globalDelta = brightnessDelta.toFloat() / count
            var error = 0f
            for (y in edge until side - edge) for (x in edge until side - edge) {
                error += kotlin.math.abs(
                    current[(y + dy) * side + x + dx] - reference[y * side + x] - globalDelta)
            }
            val residual = error / count
            val candidate = residual + 0.35f * (kotlin.math.abs(dx) + kotlin.math.abs(dy))
            if (candidate < bestScore) {
                bestScore = candidate
                bestResidual = residual
                bestDx = dx
                bestDy = dy
            }
        }
        // Wrong masks are worse than briefly showing an unblurred camera feed.
        if (bestResidual > 26f) return null
        return FrameMotion((bestDx * frameWidth.toFloat() / side).roundToInt(),
            (bestDy * frameHeight.toFloat() / side).roundToInt(), bestResidual)
    }

    private fun captureAndUpdate() {
        var raw: Bitmap? = null
        var frame: Bitmap? = null
        try {
            // Crucially: this is the real camera preview, NOT the AI overlay.
            raw = preview.bitmap ?: return
            val maxEdge = 480
            val scale = min(1f, maxEdge.toFloat() / max(raw.width, raw.height))
            frame = Bitmap.createScaledBitmap(raw,
                max(2, (raw.width * scale).roundToInt()),
                max(2, (raw.height * scale).roundToInt()), true)
            if (frame === raw) raw = null
            val current = frame
            val signature = sampleSignature(current)
            val token = epoch

            if (PortraitEngine.canSchedulePreview()) {
                // Only when previous inference finished: one snapshot, never a queue.
                val w = current.width
                val h = current.height
                val maskScale = min(1f, 320f / max(w, h))
                val sample = Bitmap.createScaledBitmap(current,
                    max(2, (w * maskScale).roundToInt()),
                    max(2, (h * maskScale).roundToInt()), true)
                // For extremely small previews Android may return the same bitmap.
                val owned = if (sample === current) current.copy(Bitmap.Config.ARGB_8888, false) else sample
                PortraitEngine.requestLiveSubjectMask(activity.applicationContext, owned) { result ->
                    if (token == epoch && running && inPortrait && result != null &&
                        !PortraitEngine.isPhotoProcessing()) {
                        mask = result
                        maskSignature = signature
                        maskReceivedAt = SystemClock.elapsedRealtime()
                        // A pending compositor using the previous mask must not
                        // post a stale image after this new segmentation result.
                        displayedSequence = ++frameSeq
                    }
                }
            }

            val reference = maskSignature
            val currentMask = mask
            val shift = if (reference != null) trackMotion(reference, signature,
                current.width, current.height) else null
            if (currentMask == null || shift == null ||
                SystemClock.elapsedRealtime() - maskReceivedAt > maximumMaskAgeMs) {
                hideOverlay()
                return
            }
            val seq = ++frameSeq
            val strength = percentage
            val accepted = PortraitEngine.requestLiveBackground(current, currentMask, strength,
                shift.dx, shift.dy) { output ->
                if (output != null && token == epoch && seq >= displayedSequence &&
                    running && inPortrait && !PortraitEngine.isPhotoProcessing() && percentage > 0) {
                    displayedSequence = seq
                    val previous = displayed
                    displayed = output
                    overlay.setImageBitmap(output)
                    overlay.alpha = percentage / 100f
                    overlay.visibility = View.VISIBLE
                    previous?.recycle()
                } else {
                    output?.recycle()
                }
            }
            if (accepted) frame = null // compositor owns and recycles this bitmap
        } catch (e: Exception) {
            Log.w("FoldPortraitLive", "preview unavailable; camera stays live", e)
            hideOverlay()
        } finally {
            raw?.recycle()
            frame?.recycle()
        }
    }

    private var displayedSequence = 0L

    private fun hideOverlay() {
        displayedSequence = ++frameSeq
        overlay.visibility = View.GONE
        overlay.setImageDrawable(null)
        displayed?.recycle()
        displayed = null
    }

    private fun setPortraitActive(active: Boolean) {
        inPortrait = active
        epoch++
        controls.visibility = if (active) View.VISIBLE else View.GONE
        mask = null
        maskSignature = null
        maskReceivedAt = 0L
        hideOverlay()
        if (!active) {
            main.removeCallbacks(collapseControls)
            slider.animate().cancel()
            label.animate().cancel()
            trackingSlider = false
            slider.visibility = View.GONE
            label.alpha = 1f
            label.visibility = View.VISIBLE
            updateLabel()
        } else {
            lastSample = 0L
            collapseControls.run()
        }
    }

    fun start() {
        if (running) return
        running = true
        main.post(tick)
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
        setPortraitActive(false)
    }
}
