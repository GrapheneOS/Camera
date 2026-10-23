package app.grapheneos.camera.capturer

import android.content.ContentValues
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.CameraMode
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** One in-flight JPEG; monotonic interval scheduling; independent outputs for video and still. */
class IntervalModule(private val activity: MainActivity) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs = activity.getSharedPreferences("interval_capture", 0)
    private var active: Session? = null
    private var exporting = false
    private var disposing = false
    private var offeredRecoveryStamp: String? = null

    val isBusy: Boolean get() = active != null || exporting

    private class Session(
        val mode: CameraMode,
        val stamp: String,
        val intervalMs: Long,
        val durationMs: Long,
        val startedAtMs: Long,
        val folder: File,
    ) {
        var issued = 0
        var processed = 0
        var nextSlot = 1L
        var inFlight = false
        var stopping = false
        var discarded = false
        var encoder: HyperlapseModule? = null
        var stacker: LightTrailsModule? = null
        val draft = File(folder, "${stamp}.partial.jpg")
        val encodedVideo = File(folder, "${stamp}.working.mp4")
        val readyVideo = File(folder, "${stamp}.ready.mp4")
        val captureFile = File(folder, "${stamp}.frame.jpg")
    }

    fun toggle() {
        if (isBusy) {
            stopAndSave()
            return
        }
        val mode = activity.camConfig.currentMode
        if (mode != CameraMode.HYPERLAPSE && mode != CameraMode.TIMELAPSE_PHOTO) return
        if (mode == CameraMode.HYPERLAPSE) {
            val choices = intArrayOf(1, 2, 3, 5, 10)
            val selected = choices.indexOf(prefs.getInt("hyper_interval", 3)).coerceAtLeast(0)
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.hyperlapse_interval_title)
                .setSingleChoiceItems(choices.map { "$it s" }.toTypedArray(), selected) { dialog, which ->
                    prefs.edit().putInt("hyper_interval", choices[which]).apply()
                    dialog.dismiss()
                    start(mode, choices[which] * 1000L, 0L)
                }.setNegativeButton(android.R.string.cancel, null).show()
        } else {
            val choices = intArrayOf(5, 15, 30, 60)
            val selected = choices.indexOf(prefs.getInt("photo_duration", 30)).coerceAtLeast(0)
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.timelapse_duration_title)
                .setSingleChoiceItems(choices.map { "$it min" }.toTypedArray(), selected) { dialog, which ->
                    prefs.edit().putInt("photo_duration", choices[which]).apply()
                    dialog.dismiss()
                    start(mode, PHOTO_REST_MS, choices[which] * 60_000L)
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
    }

    private fun start(mode: CameraMode, intervalMs: Long, durationMs: Long) {
        if (isBusy || activity.camConfig.currentMode != mode ||
            activity.camConfig.camera == null || activity.camConfig.imageCapture == null) return
        if (activity.camConfig.lensFacing != CameraSelector.LENS_FACING_BACK &&
            mode == CameraMode.TIMELAPSE_PHOTO) {
            activity.showMessage(R.string.panorama_rear_required)
            return
        }
        val workFolder = File(activity.filesDir, "interval_sessions")
        if (!workFolder.exists() && !workFolder.mkdirs()) {
            activity.showMessage(R.string.interval_capture_failed)
            return
        }
        if (workFolder.usableSpace < REQUIRED_FREE_BYTES) {
            activity.showMessage(R.string.interval_capture_low_space)
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val session = Session(mode, stamp, intervalMs, durationMs,
            SystemClock.elapsedRealtime(), workFolder)
        active = session
        activity.previewView.keepScreenOn = true
        activity.tabLayout.visibility = View.INVISIBLE
        activity.thirdOption.visibility = View.INVISIBLE
        activity.flipCameraCircle.visibility = View.INVISIBLE
        activity.captureButton.contentDescription = activity.getString(R.string.interval_capture_stop)
        activity.setCaptureButtonIcon(R.drawable.camera_shutter, R.string.interval_capture_stop)
        activity.timerView.visibility = View.VISIBLE
        activity.cbText.visibility = View.VISIBLE
        activity.cbText.text = "0"
        capture(session)
    }

    private fun capture(session: Session) {
        if (active !== session || session.stopping || session.inFlight) return
        if (session.durationMs > 0 &&
            SystemClock.elapsedRealtime() - session.startedAtMs >= session.durationMs) {
            stopAndSave()
            return
        }
        if (session.folder.usableSpace < REQUIRED_FREE_BYTES) {
            activity.showMessage(R.string.interval_capture_low_space)
            stopAndSave()
            return
        }
        val thermal = activity.getSystemService(PowerManager::class.java)
        if (thermal != null && thermal.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            activity.showMessage(R.string.interval_capture_thermal)
            stopAndSave()
            return
        }
        val capture = activity.camConfig.imageCapture ?: run { stopAndSave(); return }
        session.inFlight = true
        session.issued++
        val options = ImageCapture.OutputFileOptions.Builder(session.captureFile).build()
        try {
            capture.takePicture(options, activity.mainExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    // Encode/process directly; the one JPEG is immediately removed after processing.
                    worker.execute {
                        var succeeded = false
                        try {
                            if (!session.discarded) {
                                if (session.mode == CameraMode.HYPERLAPSE) {
                                    val encoder = session.encoder ?: HyperlapseModule(session.encodedVideo, isPortraitPhoto(session.captureFile)).also {
                                        session.encoder = it
                                    }
                                    encoder.addPhoto(session.captureFile)
                                } else {
                                    val stack = session.stacker ?: LightTrailsModule().also { session.stacker = it }
                                    stack.addPhoto(session.captureFile)
                                    if (stack.frames % CHECKPOINT_EVERY == 0) stack.saveJpeg(session.draft)
                                }
                                session.processed++
                            }
                            succeeded = true
                        } catch (error: Exception) {
                            Log.e(TAG, "Interval frame processing failed", error)
                        } finally {
                            session.captureFile.delete()
                            main.post {
                                session.inFlight = false
                                if (!succeeded) {
                                    activity.showMessage(R.string.interval_capture_failed)
                                    session.stopping = true
                                }
                                afterFrame(session)
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Interval frame capture failed", exception)
                    session.inFlight = false
                    session.stopping = true
                    afterFrame(session)
                }
            })
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not request interval frame", error)
            session.inFlight = false
            session.stopping = true
            afterFrame(session)
        }
    }

    private fun afterFrame(session: Session) {
        if (active !== session) return
        val elapsed = SystemClock.elapsedRealtime() - session.startedAtMs
        if (!activity.isDestroyed && !activity.isFinishing) {
            activity.cbText.text = session.processed.toString()
            if (session.mode == CameraMode.HYPERLAPSE) {
                val speed = (HyperlapseModule.FRAME_RATE * session.intervalMs / 1000L).toInt()
                activity.timerView.text = activity.getString(R.string.interval_capture_video_progress,
                    session.processed, (elapsed / 1000).toInt(), speed)
            } else {
                activity.timerView.text = activity.getString(R.string.interval_capture_photo_progress,
                    (elapsed / 60_000).toInt(), (session.durationMs / 60_000).toInt(), session.processed)
            }
        }
        if (session.stopping || session.durationMs > 0 && elapsed >= session.durationMs) {
            finish(session)
            return
        }
        if (session.mode == CameraMode.HYPERLAPSE) {
            // Schedule to fixed monotonic slots; skip missed slots instead of drifting or overlapping.
            val now = SystemClock.elapsedRealtime()
            session.nextSlot = maxOf(session.nextSlot, (now - session.startedAtMs) / session.intervalMs + 1L)
            val nextAt = session.startedAtMs + session.nextSlot * session.intervalMs
            session.nextSlot++
            main.postDelayed({ capture(session) }, (nextAt - now).coerceAtLeast(0L))
        } else {
            main.postDelayed({ capture(session) }, PHOTO_REST_MS)
        }
    }

    /** Normal shutter stop saves all successfully processed frames, including early stops. */
    fun stopAndSave() {
        val session = active ?: return
        if (session.stopping) return
        session.stopping = true
        if (!session.inFlight) finish(session)
    }

    fun confirmDiscard() {
        if (active == null) return
        MaterialAlertDialogBuilder(activity).setMessage(R.string.interval_capture_cancel)
            .setPositiveButton(R.string.interval_capture_discard) { _, _ -> discard() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    /** Stops capture and releases the session worker once outstanding camera callbacks finish. */
    fun dispose() {
        disposing = true
        stopAndSave()
        if (!isBusy) worker.shutdown()
    }

    private fun discard() {
        val session = active ?: return
        session.discarded = true
        session.stopping = true
        if (!session.inFlight) finish(session)
    }

    private fun finish(session: Session) {
        if (active !== session || session.inFlight) return
        active = null
        exporting = true
        activity.captureButton.isEnabled = false
        activity.timerView.setText(R.string.interval_capture_processing)
        worker.execute {
            var saved: CapturedItem? = null
            try {
                if (!session.discarded && session.processed > 0) {
                    if (session.mode == CameraMode.HYPERLAPSE) {
                        session.encoder?.complete()
                        session.encoder?.close()
                        session.encoder = null
                        check(session.encodedVideo.renameTo(session.readyVideo)) {
                            "Could not commit encoded hyperlapse"
                        }
                        saved = publish(session, session.readyVideo, "video/mp4", ITEM_TYPE_VIDEO,
                            "HYPER_${session.stamp}.mp4", "DCIM/Hyperlapse")
                    } else {
                        val finalPhoto = File(session.folder, "${session.stamp}.final.jpg")
                        session.stacker?.saveJpeg(finalPhoto)
                        saved = publish(session, finalPhoto, "image/jpeg", ITEM_TYPE_IMAGE,
                            "TLPHOTO_${session.stamp}.jpg", "DCIM/TimelapsePhoto")
                        finalPhoto.delete()
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Could not finalize interval capture", error)
            } finally {
                try { session.encoder?.close() } catch (error: Exception) {
                    Log.e(TAG, "Could not release encoder", error)
                }
                session.stacker?.close()
                session.captureFile.delete()
                session.encodedVideo.delete()
                if (saved != null || session.discarded) session.readyVideo.delete()
                if (saved != null || session.discarded) session.draft.delete()
                main.post {
                    exporting = false
                    activity.previewView.keepScreenOn = false
                    activity.captureButton.isEnabled = true
                    activity.setCaptureButtonIcon(R.drawable.recording, R.string.start_recording)
                    activity.timerView.visibility = View.GONE
                    activity.cbText.visibility = View.GONE
                    activity.tabLayout.visibility = View.VISIBLE
                    activity.thirdOption.visibility = View.VISIBLE
                    activity.flipCameraCircle.visibility = View.VISIBLE
                    if (saved != null) {
                        activity.camConfig.updateLastCapturedItem(saved)
                        if (!activity.isDestroyed) {
                            activity.updateThumbnail()
                            activity.showMessage(R.string.interval_capture_saved)
                        }
                    } else if (!session.discarded && !activity.isDestroyed) {
                        activity.showMessage(if (session.processed == 0)
                            R.string.interval_capture_no_frames else R.string.interval_capture_failed)
                    }
                    if (disposing) worker.shutdown()
                }
            }
        }
    }

    /** An interrupted long exposure leaves periodic private JPEG checkpoints to recover. */
    fun offerRecovery() {
        if (isBusy || activity.isFinishing || activity.isDestroyed) return
        val folder = File(activity.filesDir, "interval_sessions")
        val drafts = folder.listFiles { file ->
            file.name.endsWith(".partial.jpg") || file.name.endsWith(".final.jpg") ||
                file.name.endsWith(".ready.mp4")
        }.orEmpty()
        val draft = drafts.minByOrNull { it.lastModified() } ?: return
        val stamp = draft.name.removeSuffix(".partial.jpg")
            .removeSuffix(".final.jpg").removeSuffix(".ready.mp4")
        if (offeredRecoveryStamp == stamp) return
        offeredRecoveryStamp = stamp
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.timelapse_recover_title)
            .setMessage(R.string.timelapse_recover_message)
            .setPositiveButton(R.string.timelapse_recover_save) { _, _ ->
                exporting = true
                worker.execute {
                    val saved = try {
                        val video = draft.name.endsWith(".ready.mp4")
                        val recovery = Session(
                            if (video) CameraMode.HYPERLAPSE else CameraMode.TIMELAPSE_PHOTO,
                            stamp, 0, 0, SystemClock.elapsedRealtime(), folder
                        )
                        if (video) publish(recovery, draft, "video/mp4", ITEM_TYPE_VIDEO,
                            "HYPER_${stamp}_recovered.mp4", "DCIM/Hyperlapse")
                        else publish(recovery, draft, "image/jpeg", ITEM_TYPE_IMAGE,
                            "TLPHOTO_${stamp}_recovered.jpg", "DCIM/TimelapsePhoto")
                    } catch (error: Exception) {
                        Log.e(TAG, "Cannot recover light trail checkpoint", error)
                        null
                    }
                    if (saved != null) draft.delete()
                    main.post {
                        exporting = false
                        if (saved != null) {
                            activity.camConfig.updateLastCapturedItem(saved)
                            activity.updateThumbnail()
                            activity.showMessage(R.string.interval_capture_saved)
                        } else activity.showMessage(R.string.interval_capture_failed)
                    }
                }
            }
            .setNegativeButton(R.string.timelapse_recover_later, null).show()
    }

    private fun publish(
        session: Session, file: File, mime: String, kind: Int, name: String, path: String,
    ): CapturedItem {
        require(file.isFile && file.length() > 0L)
        val collection = if (kind == ITEM_TYPE_VIDEO) CamConfig.videoCollectionUri else CamConfig.imageCollectionUri
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = activity.contentResolver.insert(collection, values)
            ?: error("Cannot create pending MediaStore output")
        try {
            activity.contentResolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Cannot open output")
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            check(activity.contentResolver.update(uri, published, null, null) > 0)
        } catch (error: Exception) {
            activity.contentResolver.delete(uri, null, null)
            throw error
        }
        return CapturedItem(kind, session.stamp, uri, name.substringBeforeLast('.'))
    }

    companion object {
        private const val TAG = "IntervalModule"
        private const val PHOTO_REST_MS = 150L
        private const val CHECKPOINT_EVERY = 24
        private const val REQUIRED_FREE_BYTES = 120L * 1024 * 1024
    }
}
