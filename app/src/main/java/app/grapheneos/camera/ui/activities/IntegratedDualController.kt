package app.grapheneos.camera.ui.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.WindowManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.CameraSelector
import androidx.camera.core.CompositionSettings
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.R
import app.grapheneos.camera.util.formatVideoDuration
import app.grapheneos.camera.ui.CameraShortcutMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Dual-camera capture and composition within the main camera interface. */
@SuppressLint("UnsafeOptInUsageError")
internal class IntegratedDualController(private val host: MainActivity) {
    private val tag = "FoldIntegratedDual"
    var mode: CameraShortcutMode? = null
        private set
    val active get() = mode != null
    val recordingActive get() = recording != null
    private var provider: ProcessCameraProvider? = null
    private var concurrent: ConcurrentCamera? = null
    private var recorder: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isStarted = false
    private var stopping = false
    private var disposed = false
    private var generation = 0
    private var ready = false
    private var saving = false
    private var muted = false
    private var split = false
    private var bounds = DualVideoCompositionLayout()
    private var appliedBounds = bounds
    private var appliedSplit = false
    private var updateQueued = false
    private var lastUpdate = 0L
    private var recordingDate = ""
    private var pairs: List<List<CameraInfo>> = emptyList()
    private val writer = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val capturePrefs = host.getSharedPreferences(CamConfig.COMMON_SHARED_PREFS_NAME,
        android.content.Context.MODE_PRIVATE)
    private var torchEnabled = false
    private var temporaryTorch = false
    private var lightingOverlayActive = false
    private var previousBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var startingVideo = false

    var photoFlashMode: Int
        get() = capturePrefs.getInt("dual_photo_flash_mode", ImageCapture.FLASH_MODE_AUTO)
        private set(value) {
            capturePrefs.edit().putInt("dual_photo_flash_mode", value).apply()
        }

    var selfIlluminationEnabled: Boolean
        get() = capturePrefs.getBoolean("dual_photo_self_illumination", false)
        set(value) {
            capturePrefs.edit().putBoolean("dual_photo_self_illumination", value).apply()
        }

    val isTorchEnabled: Boolean get() = torchEnabled

    private fun rearCamera(): Camera? {
        val cameras = concurrent?.cameras ?: return null
        return cameras.firstOrNull {
            it.cameraInfo.lensFacing == CameraSelector.LENS_FACING_BACK
        } ?: cameras.firstOrNull()
    }

    fun cyclePhotoFlashMode() {
        if (mode != CameraShortcutMode.DUAL_PHOTO) return
        photoFlashMode = when (photoFlashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        host.settingsDialog.updateFlashMode()
    }

    fun toggleTorch() {
        val camera = rearCamera()
        if (camera?.cameraInfo?.hasFlashUnit() != true) {
            host.showMessage(R.string.flash_unavailable_in_current_mode)
            host.settingsDialog.torchToggle.isChecked = false
            return
        }
        torchEnabled = !torchEnabled
        camera.cameraControl.enableTorch(torchEnabled)
        host.settingsDialog.torchToggle.isChecked = torchEnabled
    }

    private fun resetCaptureLighting() {
        if (temporaryTorch) {
            rearCamera()?.cameraControl?.enableTorch(torchEnabled)
            temporaryTorch = false
        }
        if (lightingOverlayActive) {
            lightingOverlayActive = false
            host.mainOverlay.visibility = View.INVISIBLE
            host.mainOverlay.setImageResource(android.R.color.transparent)
            host.mainOverlay.alpha = 1f
            val attrs = host.window.attributes
            attrs.screenBrightness = previousBrightness
            host.window.attributes = attrs
        }
    }

    private fun enableScreenIllumination() {
        if (lightingOverlayActive) return
        previousBrightness = host.window.attributes.screenBrightness
        val attrs = host.window.attributes
        attrs.screenBrightness = 1f
        host.window.attributes = attrs
        host.mainOverlay.setImageResource(R.color.self_illumination_light)
        host.mainOverlay.alpha = 1f
        host.mainOverlay.visibility = View.VISIBLE
        lightingOverlayActive = true
    }

    private fun isSceneDark(): Boolean {
        val preview = host.previewView.bitmap ?: return false
        return try {
            var sum = 0L
            var samples = 0
            for (y in 1..8) for (x in 1..8) {
                val pixel = preview.getPixel(x * (preview.width - 1) / 9,
                    y * (preview.height - 1) / 9)
                sum += (Color.red(pixel) * 299L + Color.green(pixel) * 587L +
                    Color.blue(pixel) * 114L) / 1000L
                samples++
            }
            samples > 0 && sum / samples < 95L
        } finally {
            preview.recycle()
        }
    }
    private val dp get() = host.resources.displayMetrics.density
    private fun px(value: Float) = (value * dp + .5f).toInt()

    // Draw the draggable overlay within the existing camera preview.
    private val border = FrameLayout(host).apply {
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(px(2f), Color.WHITE)
            cornerRadius = px(12f).toFloat()
        }
        visibility = View.GONE
        elevation = px(4f).toFloat()
        contentDescription = "Move or pinch picture-in-picture"
    }

    init {
        host.previewContainer.addView(border, ConstraintLayout.LayoutParams(0, 0))
        installDrag()
        host.previewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (active) arrangeBorder()
        }
        host.previewView.previewStreamState.observe(host) { state ->
            if (active) {
                ready = state == PreviewView.StreamState.STREAMING
                if (ready && concurrent != null) {
                    // Reapply the shared composition when the new camera stream becomes ready.
                    host.previewView.post {
                        if (active && ready && concurrent != null) {
                            updateComposition()
                            arrangeBorder()
                        }
                    }
                }
            }
        }
    }

    fun enter(newMode: CameraShortcutMode) {
        if (newMode != CameraShortcutMode.DUAL_VIDEO && newMode != CameraShortcutMode.DUAL_PHOTO) return
        if (mode == newMode) return
        if (recording != null) { host.showMessage("Stop the recording before changing modes"); return }
        if (mode != null) exit(restoreCamera = false)
        mode = newMode
        // Preserve the shared PIP bounds and layout when switching modes.
        ready = false
        host.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        host.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        host.previewView.keepScreenOn = true
        host.cancelFocusTimer()
        updateUi()
        host.camConfig.cameraProvider?.unbindAll()
        bind()
        host.camConfig.refreshFoldModeTabs()
    }

    fun exit(restoreCamera: Boolean) {
        if (mode == null) return
        ++generation
        startingVideo = false
        saving = false
        resetCaptureLighting()
        if (torchEnabled) rearCamera()?.cameraControl?.enableTorch(false)
        torchEnabled = false
        if (recording != null) recording?.stop()
        recording = null
        isStarted = false
        stopping = false
        mode = null
        ready = false
        recorder = null
        concurrent = null
        border.visibility = View.GONE
        provider?.unbindAll()
        host.previewView.keepScreenOn = false
        host.previewView.scaleType = PreviewView.ScaleType.FIT_START
        host.timerView.visibility = View.GONE
        host.muteToggle.visibility = View.GONE
        host.tabLayout.visibility = View.VISIBLE
        host.imagePreview.visibility = View.VISIBLE
        host.thirdOption.visibility = View.VISIBLE
        if (restoreCamera) host.camConfig.startCamera(true)
        host.setFlipCameraIcon(R.drawable.flip_camera, R.string.flip_camera)
        host.setThirdCircleIcon(R.drawable.option_circle, R.string.open_gallery)
        host.setCaptureButtonIcon(R.drawable.camera_shutter, R.string.capture)
        host.updateThumbnail()
        host.camConfig.refreshFoldModeTabs()
    }

    fun onResume() { if (active) bind() }
    fun onPause() {
        if (active) {
            ++generation
            startingVideo = false
            saving = false
            resetCaptureLighting()
            if (torchEnabled) rearCamera()?.cameraControl?.enableTorch(false)
            torchEnabled = false
            recording?.stop()
            ready = false
            concurrent = null
            recorder = null
        }
    }
    fun onDestroy() {
        disposed = true
        ++generation
        startingVideo = false
        saving = false
        resetCaptureLighting()
        if (torchEnabled) rearCamera()?.cameraControl?.enableTorch(false)
        recording?.stop()
        writer.shutdown()
    }

    private fun bind() {
        if (!active || disposed) return
        val request = ++generation
        val future = ProcessCameraProvider.getInstance(host)
        future.addListener({
            if (!active || disposed || request != generation || host.isDestroyed) return@addListener
            try {
                provider = future.get()
                pairs = provider!!.availableConcurrentCameraInfos.filter { it.size == 2 }
                    .map { pair -> pair.sortedBy { if (it.lensFacing == CameraSelector.LENS_FACING_BACK) 0 else 1 } }
                    .sortedByDescending { pair -> pair.any { it.lensFacing == CameraSelector.LENS_FACING_FRONT } &&
                        pair.any { it.lensFacing == CameraSelector.LENS_FACING_BACK } }
                if (pairs.isEmpty()) { host.showMessage(R.string.dual_unsupported); exit(true); return@addListener }
                bindPair()
            } catch (t: Exception) {
                Log.e(tag, "Dual camera initialization failed", t)
                host.showMessage(R.string.dual_pair_failed)
                exit(true)
            }
        }, ContextCompat.getMainExecutor(host))
    }

    private fun compositions(): List<CompositionSettings> {
        fun make(rect: DualVideoCompositionLayout.Rect) = CompositionSettings.Builder()
            .setScale(rect.sx, rect.sy).setOffset(rect.cx, rect.cy).build()
        return listOf(make(bounds.primary(split)), make(bounds.secondary(split)))
    }

    private fun bindPair() {
        if (!active) return
        val p = provider ?: return
        try {
            p.unbindAll()
            val previewCase = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build()
            val videoCase = VideoCapture.withOutput(
                Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build())
            val group = UseCaseGroup.Builder().addUseCase(previewCase).addUseCase(videoCase).build()
            val pair = pairs.first()
            val compositions = compositions()
            concurrent = p.bindToLifecycle(listOf(
                ConcurrentCamera.SingleCameraConfig(pair[0].cameraSelector, group, compositions[0], host),
                ConcurrentCamera.SingleCameraConfig(pair[1].cameraSelector, group, compositions[1], host)
            ))
            recorder = videoCase
            appliedBounds = bounds
            appliedSplit = split
            previewCase.setSurfaceProvider(host.previewView.surfaceProvider)
            if (torchEnabled) rearCamera()?.cameraControl?.enableTorch(true)
            // The overlay uses the same coordinates as the camera composition.
            host.previewView.post { if (active) arrangeBorder() }
        } catch (t: Exception) {
            Log.e(tag, "Concurrent Preview+VideoCapture composition unsupported", t)
            host.showMessage(R.string.dual_pair_failed)
            exit(true)
        }
    }

    fun toggleSplit() {
        if (!active || stopping) return
        split = !split
        updateComposition()
        arrangeBorder()
        updateUi()
    }

    private fun updateComposition() {
        val camera = concurrent ?: return
        try {
            camera.setCompositionSettings(compositions())
            appliedBounds = bounds
            appliedSplit = split
            lastUpdate = SystemClock.uptimeMillis()
        } catch (t: RuntimeException) {
            Log.w(tag, "Dual composition update rejected", t)
            bounds = appliedBounds
            split = appliedSplit
            arrangeBorder()
            updateUi()
        }
    }
    private fun queueComposition() {
        val wait = (33L - (SystemClock.uptimeMillis() - lastUpdate)).coerceAtLeast(0L)
        if (wait == 0L) { updateComposition(); return }
        if (updateQueued) return
        updateQueued = true
        border.postDelayed({
            updateQueued = false
            if (active && !disposed) updateComposition()
        }, wait)
    }

    private fun arrangeBorder() {
        if (!active || host.previewView.width <= 0 || host.previewView.height <= 0) return
        val p = host.previewView
        val viewport = DualVideoCompositionLayout.fitViewport(p.width.toFloat(), p.height.toFloat())
        val w = viewport.width
        val h = viewport.height
        val width = if (split) w / 2 else w * bounds.fraction
        val height = if (split) h else h * bounds.fraction
        border.layoutParams = ConstraintLayout.LayoutParams(width.roundToInt(), height.roundToInt())
        border.x = p.x + viewport.left + (if (split) w / 2 else w * bounds.x)
        border.y = p.y + viewport.top + (if (split) 0f else h * bounds.y)
        border.visibility = View.VISIBLE
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun installDrag() {
        var x = 0f; var y = 0f; var lastDistance = 0f
        border.setOnTouchListener { _, event ->
            if (!active || split || stopping) return@setOnTouchListener true
            val viewport = DualVideoCompositionLayout.fitViewport(
                host.previewView.width.toFloat(), host.previewView.height.toFloat())
            val w = viewport.width.coerceAtLeast(1f)
            val h = viewport.height.coerceAtLeast(1f)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x=event.rawX; y=event.rawY; true }
                MotionEvent.ACTION_POINTER_DOWN -> { lastDistance=distance(event); true }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount > 1) {
                        val newDistance = distance(event)
                        if (lastDistance > 1f) bounds = bounds.resized(newDistance / lastDistance)
                        lastDistance = newDistance
                    } else bounds = bounds.moved((event.rawX-x)/w, (event.rawY-y)/h)
                    x=event.rawX; y=event.rawY
                    arrangeBorder()
                    queueComposition()
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> { lastDistance=0f; true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { lastDistance=0f; updateComposition(); true }
                else -> false
            }
        }
    }
    private fun distance(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val x=e.getX(0)-e.getX(1)
        val y=e.getY(0)-e.getY(1)
        return sqrt(x*x+y*y)
    }

    fun shutter() {
        when(mode) {
            CameraShortcutMode.DUAL_PHOTO -> captureStill()
            CameraShortcutMode.DUAL_VIDEO -> if (recording == null) beginVideo() else stopVideo()
            else -> Unit
        }
    }
    fun rightButton() { if (recording != null && isStarted) captureStill() else host.openDualGallery() }
    fun toggleMic() { if (recording == null) return; muted=!muted; recording?.mute(muted); host.setMuteToggleState(muted) }

    private fun updateUi() {
        if (!active) return
        host.setFlipCameraIcon(if (split) R.drawable.dual_picture_in_picture else R.drawable.dual_split_screen,
            if (split) R.string.dual_layout_pip else R.string.dual_layout_split)
        host.flipCameraCircle.visibility = View.VISIBLE
        val running = recording != null && isStarted
        val drawable = when {
            mode == CameraShortcutMode.DUAL_PHOTO -> R.drawable.camera_shutter
            running -> R.drawable.dual_stop_recording
            else -> R.drawable.recording
        }
        host.setCaptureButtonIcon(drawable, when {
            mode == CameraShortcutMode.DUAL_PHOTO -> R.string.capture
            running -> R.string.stop_recording
            else -> R.string.start_recording
        })
        host.timerView.visibility = if (running) View.VISIBLE else View.GONE
        host.muteToggle.visibility = if (running) View.VISIBLE else View.GONE
        host.tabLayout.visibility = if (running) View.INVISIBLE else View.VISIBLE
        host.imagePreview.visibility = if (running) View.GONE else View.VISIBLE
        host.setThirdCircleIcon(if (running) R.drawable.camera_shutter else R.drawable.option_circle,
            if (running) R.string.capture else R.string.open_gallery)
        if (!running) host.updateThumbnail()
    }

    fun beginVideoAfterPermission() { if (mode == CameraShortcutMode.DUAL_VIDEO) beginVideo() }
    private fun beginVideo() {
        if (stopping || recording != null || startingVideo) return
        val video = recorder ?: run { host.showMessage(R.string.dual_pair_failed); return }
        val includeAudio = host.settingsDialog.includeAudioToggle.isChecked
        if (includeAudio && host.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            host.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 9132)
            return
        }
        recordingDate = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "DUAL_${recordingDate}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Dual Video")
        }
        startingVideo = true
        val request = generation
        host.camConfig.mPlayer.playVRStartSound(mainHandler, Runnable {
            if (disposed || !active || request != generation ||
                mode != CameraShortcutMode.DUAL_VIDEO || !startingVideo) {
                startingVideo = false
                return@Runnable
            }
            try {
                val options = MediaStoreOutputOptions.Builder(host.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
                @SuppressLint("MissingPermission")
                val prepared = video.output.prepareRecording(host, options)
                val pending = if (includeAudio) prepared.withAudioEnabled() else prepared
                recording = pending.start(ContextCompat.getMainExecutor(host)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isStarted = true; muted = false; stopping = false
                            host.timerView.text = "00:00"; host.setMuteToggleState(false); updateUi()
                        }
                        is VideoRecordEvent.Status -> {
                            host.timerView.text = formatVideoDuration(
                                event.recordingStats.recordedDurationNanos / 1_000_000_000L)
                        }
                        is VideoRecordEvent.Finalize -> {
                            host.camConfig.mPlayer.playVRStopSound()
                            val uri = event.outputResults.outputUri
                            val success = !event.hasError() && uri != Uri.EMPTY
                            if (success) publishMedia(uri, ITEM_TYPE_VIDEO, recordingDate)
                            else Log.e(tag, "Dual Video finalize error ${event.error}", event.cause)
                            recording = null; isStarted = false; stopping = false
                            updateUi()
                            if (active) host.showMessage(if (success) R.string.dual_video_saved
                                else R.string.dual_video_failed)
                        }
                    }
                }
            } catch (t: Exception) {
                Log.e(tag, "Dual video failed to start", t)
                recording = null
                updateUi()
                host.showMessage(R.string.dual_video_failed)
            } finally {
                startingVideo = false
            }
        })
    }

    private fun stopVideo() { if (!stopping) { stopping=true; recording?.stop() } }

    private fun publishMedia(uri:Uri, type:Int, date:String) {
        host.getSharedPreferences(CamConfig.COMMON_SHARED_PREFS_NAME, android.content.Context.MODE_PRIVATE).edit()
            .putInt(CamConfig.SettingValues.Key.LAST_CAPTURED_ITEM_TYPE,type)
            .putString(CamConfig.SettingValues.Key.LAST_CAPTURED_ITEM_DATE_STRING,date)
            .putString(CamConfig.SettingValues.Key.LAST_CAPTURED_ITEM_URI,uri.toString()).apply()
        host.camConfig.fetchLastCapturedItemFromSharedPrefs()
        host.updateThumbnail()
    }

    /** Save one JPEG containing the current combined front/rear composition. */
    private fun captureStill() {
        if (!active || saving || stopping) return
        if (!ready || concurrent == null) {
            host.showMessage("Wait for the dual camera preview")
            return
        }
        saving = true
        updateComposition()
        val request = generation
        val photoMode = mode == CameraShortcutMode.DUAL_PHOTO
        val flashNeeded = photoMode && when (photoFlashMode) {
            ImageCapture.FLASH_MODE_ON -> true
            ImageCapture.FLASH_MODE_AUTO -> isSceneDark()
            else -> false
        }
        if (flashNeeded && !torchEnabled) {
            val camera = rearCamera()
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                temporaryTorch = true
                camera.cameraControl.enableTorch(true)
            } else {
                host.showMessage(R.string.flash_unavailable_in_current_mode)
            }
        }
        if (photoMode && selfIlluminationEnabled) enableScreenIllumination()
        val delay = if (temporaryTorch || lightingOverlayActive) 450L else 0L
        host.previewView.postDelayed({
            if (!active || disposed || request != generation) {
                resetCaptureLighting()
                saving = false
                return@postDelayed
            }
            val snapshot = host.previewView.bitmap
            resetCaptureLighting()
            if (snapshot == null) {
                saving = false
                host.showMessage("The dual preview is not ready")
                return@postDelayed
            }
            val image = try {
                val viewport = DualVideoCompositionLayout.fitViewport(
                    snapshot.width.toFloat(), snapshot.height.toFloat())
                val left = viewport.left.toInt().coerceIn(0, snapshot.width - 1)
                val top = viewport.top.toInt().coerceIn(0, snapshot.height - 1)
                val width = viewport.width.toInt().coerceIn(1, snapshot.width - left)
                val height = viewport.height.toInt().coerceIn(1, snapshot.height - top)
                Bitmap.createBitmap(snapshot, left, top, width, height)
            } catch (t: Exception) {
                Log.e(tag, "Dual preview snapshot failed", t)
                saving = false
                host.showMessage("The dual preview is not ready")
                return@postDelayed
            } finally {
                snapshot.recycle()
            }
            host.captureButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            writer.execute {
                try {
                    val quality = host.getSharedPreferences(CamConfig.COMMON_SHARED_PREFS_NAME,
                        android.content.Context.MODE_PRIVATE).getInt(
                        CamConfig.SettingValues.Key.PHOTO_QUALITY, 95).coerceIn(1, 100)
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "DUAL_${timestamp}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Dual Photo")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = host.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("MediaStore insert failed")
                    try {
                        host.contentResolver.openOutputStream(uri)?.use { stream ->
                            check(image.compress(Bitmap.CompressFormat.JPEG, quality, stream))
                        } ?: error("MediaStore stream unavailable")
                        host.contentResolver.update(uri,
                            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                            null, null)
                        host.runOnUiThread {
                            host.camConfig.mPlayer.playShutterSound()
                            if (active) {
                                publishMedia(uri, ITEM_TYPE_IMAGE, timestamp)
                                host.showMessage("Dual photo saved (preview resolution)")
                            }
                        }
                    } catch (t: Throwable) {
                        host.contentResolver.delete(uri, null, null)
                        throw t
                    }
                } catch (t: Throwable) {
                    Log.e(tag, "Combined photo failed", t)
                    host.runOnUiThread { if (active) host.showMessage("Could not save Dual Photo") }
                } finally {
                    image.recycle()
                    host.runOnUiThread { saving = false }
                }
            }
        }, delay)
    }
}
