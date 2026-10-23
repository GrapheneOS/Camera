package app.grapheneos.camera.capturer

import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.location.Location
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.view.View
import android.webkit.MimeTypeMap
import androidx.annotation.VisibleForTesting
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import app.grapheneos.camera.App
import app.grapheneos.camera.CameraMode
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.R
import app.grapheneos.camera.VIDEO_NAME_PREFIX
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.util.formatVideoDuration
import app.grapheneos.camera.util.getTreeDocumentUri
import app.grapheneos.camera.util.removePendingFlagFromUri
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class VideoCapturer(private val mActivity: MainActivity) {

    val camConfig = mActivity.camConfig

    var isRecording = false
        private set

    private val videoFileFormat = ".mp4"

    private var recording: Recording? = null
    private var slowAudio: SlowMotionModule? = null
    private var slowAudioWanted = false
    private var slowRecordingFps = 120
    private var stopRequested = false

    // Invoking this abandons a start still waiting on its output or queued behind the record-start
    // sound.
    private var cancelDeferredStart: (() -> Unit)? = null

    var isMuted = false
        private set

    var includeAudio: Boolean = false

    var isPaused = false
        set(value) {
            if (isRecording) {
                if (value) {
                    recording?.pause()
                    mActivity.setFlipCameraIcon(R.drawable.play, R.string.resume_recording)
                } else {
                    recording?.resume()
                    mActivity.setFlipCameraIcon(R.drawable.pause, R.string.pause_recording)
                }
            }
            field = value
        }

    private val handler = Handler(Looper.getMainLooper())

    // The output is created, opened, saved and discarded through this
    @set:VisibleForTesting
    var contentResolver: ContentResolver = mActivity.contentResolver

    private fun updateTimerTime(timeInNanos: Long) {
        mActivity.timerView.text = formatVideoDuration(timeInNanos / 1_000_000_000)
    }

    private class RecordingContext(
        val uri: Uri,
        val fileDescriptor: ParcelFileDescriptor,
        val shouldAddToGallery: Boolean,
        val isPendingMediaStoreUri: Boolean,
        val mode: CameraMode,
    )

    // Runs on outputExecutor: each step is a call into another process, MediaProvider or the app
    // the video is for, and can take seconds to answer.
    private fun createRecordingContext(
        fileName: String,
        foreignUri: Uri?,
        storageLocation: String,
        mode: CameraMode,
    ): RecordingContext? {
        val mimeType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(videoFileFormat) ?: "video/mp4"

        val uri: Uri?
        var shouldAddToGallery = true
        var isPendingMediaStoreUri = false

        if (foreignUri != null) {
            uri = foreignUri
            shouldAddToGallery = false
        } else {
            if (storageLocation == CamConfig.SettingValues.Default.STORAGE_LOCATION) {
                val contentValues = ContentValues().apply {
                    put(MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaColumns.MIME_TYPE, mimeType)
                    val directory = when (mode) {
                        CameraMode.SLOW_MOTION -> "DCIM/SlowMotion"
                        else -> DEFAULT_MEDIA_STORE_CAPTURE_PATH
                    }
                    put(MediaColumns.RELATIVE_PATH, directory)
                    put(MediaColumns.IS_PENDING, 1)
                }
                uri = contentResolver.insert(CamConfig.videoCollectionUri, contentValues)
                isPendingMediaStoreUri = true
            } else {
                val treeUri = Uri.parse(storageLocation)
                val treeDocumentUri = getTreeDocumentUri(treeUri)

                uri = DocumentsContract.createDocument(contentResolver, treeDocumentUri, mimeType, fileName)
            }
        }

        if (uri == null) {
            return null
        }

        return contentResolver
            .openFileDescriptor(uri, "w")
            ?.let {
                RecordingContext(uri, it, shouldAddToGallery, isPendingMediaStoreUri, mode)
            }
    }

    private fun prepareRecording(recorder: Recorder, recordingCtx: RecordingContext): PendingRecording {
        var location: Location? = null
        if (camConfig.requireLocation) {
            location = (mActivity.applicationContext as App).getLocation()
            if (location == null) {
                mActivity.showMessage(R.string.location_unavailable)
            }
        }
        val outputOptions = FileDescriptorOutputOptions.Builder(recordingCtx.fileDescriptor)
            .setLocation(location)
            .build()
        return recorder.prepareRecording(mActivity, outputOptions)
    }

    fun startRecording() {
        if (camConfig.camera == null) return
        val recorder = camConfig.videoCapture?.output ?: return
        if (isRecording) return
        isRecording = true
        stopRequested = false
        slowAudio?.discard()
        slowAudio = null

        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val mode = camConfig.currentMode
        val prefix = when (mode) {
            CameraMode.SLOW_MOTION -> "SLOMO_"
            else -> VIDEO_NAME_PREFIX
        }
        val fileName = prefix + dateString + videoFileFormat

        includeAudio = false
        slowAudioWanted = false
        slowRecordingFps = camConfig.activeSlowMotionFps

        val ctx = mActivity
        val audioRequested = ctx.settingsDialog.includeAudioToggle.isChecked
        if (audioRequested) {
            if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED) {
                if (mode == CameraMode.SLOW_MOTION) slowAudioWanted = true
                else includeAudio = true
            } else {
                ctx.restartRecordingWithMicPermission()
                isRecording = false
                return
            }
        }

        val foreignUri = if (ctx is VideoCaptureActivity && ctx.isOutputUriAvailable()) {
            ctx.outputUri
        } else {
            null
        }
        val storageLocation = if (mode == CameraMode.SLOW_MOTION) {
            CamConfig.SettingValues.Default.STORAGE_LOCATION
        } else camConfig.storageLocation

        beforeRecordingStarts()

        // A stop cancels the start until the recording exists, and the sound callback may fire
        // more than once; a second PendingRecording.start() throws.
        var consumed = false
        // A cancel discards the output if it exists by then, and its arrival does otherwise
        var createdCtx: RecordingContext? = null

        cancelDeferredStart = {
            consumed = true
            cancelDeferredStart = null
            createdCtx?.let { abandonOutput(it) }
            afterRecordingStops()
        }

        outputExecutor.execute {
            val recordingCtx = try {
                createRecordingContext(fileName, foreignUri, storageLocation, mode)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            handler.post {
                if (consumed) {
                    recordingCtx?.let { abandonOutput(it) }
                    return@post
                }

                val pendingRecording = try {
                    recordingCtx?.let { prepareRecording(recorder, it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                if (recordingCtx == null || pendingRecording == null) {
                    recordingCtx?.let { abandonOutput(it) }
                    if (foreignUri == null) {
                        camConfig.onStorageLocationNotFound()
                    }
                    ctx.showMessage(R.string.unable_to_access_output_file)
                    cancelDeferredStart = null
                    afterRecordingStops()
                    return@post
                }

                createdCtx = recordingCtx

                if (includeAudio) {
                    pendingRecording.withAudioEnabled()
                }

                camConfig.mPlayer.playVRStartSound(handler) {
                    if (consumed) {
                        return@playVRStartSound
                    }

                    consumed = true
                    cancelDeferredStart = null

                    recording = pendingRecording.start(ctx.mainExecutor) { event ->
                        onRecordEvent(event, recordingCtx, dateString)
                    }

                    // The Recording didn't exist yet when the mute/pause setters ran.
                    if (isMuted) {
                        recording?.mute(true)
                    }

                    if (isPaused) {
                        recording?.pause()
                    }

                    try {
                        // FileDescriptorOutputOptions doc says that the file descriptor should be closed by the
                        // caller, and that it's safe to do so as soon as pendingRecording.start() returns
                        recordingCtx.fileDescriptor.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun onRecordEvent(event: VideoRecordEvent, recordingCtx: RecordingContext, dateString: String) {
        val ctx = mActivity

        if (event is VideoRecordEvent.Start) {
            if (recordingCtx.mode == CameraMode.SLOW_MOTION && slowAudioWanted && !stopRequested) {
                try {
                    val session = SlowMotionModule(mActivity)
                    if (session.start()) slowAudio = session else session.discard()
                } catch (e: Exception) {
                    e.printStackTrace() // Video must remain usable without the microphone.
                }
            }
            onRecordingStart()
        }

        if (event is VideoRecordEvent.Status) {
            updateTimerTime(event.recordingStats.recordedDurationNanos)
        }

        if (event is VideoRecordEvent.Finalize) {
            slowAudio?.stop()
            afterRecordingStops()

            camConfig.mPlayer.playVRStopSound()

            if (event.hasError()) {
                slowAudio?.discard()
                slowAudio = null
                when (event.error) {
                    VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> {
                        abandonOutput(recordingCtx)
                        ctx.showMessage(R.string.recording_too_short_to_be_saved)
                        return
                    }
                    VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
                    VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR,
                    VideoRecordEvent.Finalize.ERROR_UNKNOWN -> {
                        abandonOutput(recordingCtx)
                        ctx.showMessage(ctx.getString(R.string.unable_to_save_video_verbose, event.error))
                        return
                    }
                    else -> {
                        ctx.showMessage(ctx.getString(R.string.error_during_recording, event.error))
                        // The errors left unnamed here (the camera going away, storage
                        // running out) finalize whatever was written before they hit, which
                        // is worth keeping — but only if anything was.
                        if (event.recordingStats.numBytesRecorded == 0L) {
                            abandonOutput(recordingCtx)
                            return
                        }
                    }
                }
            }

            val audioSession = slowAudio
            slowAudio = null
            if (recordingCtx.mode == CameraMode.SLOW_MOTION && audioSession != null &&
                recordingCtx.isPendingMediaStoreUri) {
                finishSlowMotionWithAudio(recordingCtx, dateString, audioSession, slowRecordingFps)
            } else {
                audioSession?.discard()
                saveOutput(recordingCtx, dateString)
            }
        }
    }

    /** Create a new pending MediaStore entry. The silent original is ONLY deleted once
     * the new MP4 has been completely written and published. If any step fails, publish
     * the original instead; never replace it in-place or risk losing a recording.
     */
    private fun finishSlowMotionWithAudio(
        original: RecordingContext, dateString: String,
        session: SlowMotionModule, fps: Int
    ) {
        mActivity.showMessage(R.string.slow_motion_audio_processing)
        outputExecutor.execute {
            val pcm = session.finish()
            val remuxed = pcm?.let {
                SlowMotionMuxer.combine(mActivity, original.uri, it, fps)
            }
            var replacement: RecordingContext? = null
            try {
                if (remuxed != null) {
                    val newName = "SLOMO_${dateString}_AUDIO.mp4"
                    val target = createRecordingContext(
                        newName, null, CamConfig.SettingValues.Default.STORAGE_LOCATION, CameraMode.SLOW_MOTION
                    )
                    if (target != null) {
                        try {
                            FileInputStream(remuxed).use { input ->
                                FileOutputStream(target.fileDescriptor.fileDescriptor).use { output ->
                                    input.copyTo(output, 256 * 1024)
                                    output.flush()
                                }
                            }
                            replacement = target
                        } catch (error: Exception) {
                            error.printStackTrace()
                            abandonOutput(target)
                        }
                    }
                }
            } catch (error: Exception) {
                error.printStackTrace()
            } finally {
                pcm?.delete()
                remuxed?.delete()
                session.discard()
            }
            val ready = replacement
            handler.post {
                if (ready != null) publishOutput(ready, dateString, original)
                else saveOutput(original, dateString)
            }
        }
    }

    // A video in MediaStore stays pending, hidden from other apps and left to be deleted, until
    // MediaProvider is told to make it visible: only then is it saved.
    private fun saveOutput(recordingCtx: RecordingContext, dateString: String) {
        publishOutput(recordingCtx, dateString)
    }

    private fun publishOutput(
        recordingCtx: RecordingContext, dateString: String,
        fallback: RecordingContext? = null
    ) {
        if (!recordingCtx.isPendingMediaStoreUri) {
            onOutputSaved(recordingCtx, dateString)
            if (fallback != null) abandonOutput(fallback)
            return
        }

        // Off the main thread for the same reason the output is created there
        outputExecutor.execute {
            val saved = try {
                removePendingFlagFromUri(contentResolver, recordingCtx.uri)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }

            handler.post {
                if (saved) {
                    onOutputSaved(recordingCtx, dateString)
                    if (fallback != null) abandonOutput(fallback)
                } else if (fallback != null) {
                    abandonOutput(recordingCtx)
                    saveOutput(fallback, dateString)
                } else {
                    mActivity.showMessage(R.string.unable_to_save_video)
                }
            }
        }
    }

    private fun onOutputSaved(recordingCtx: RecordingContext, dateString: String) {
        val ctx = mActivity
        val uri = recordingCtx.uri

        if (recordingCtx.shouldAddToGallery) {
            val displayName = when (recordingCtx.mode) {
                CameraMode.SLOW_MOTION -> "SLOMO_$dateString"
                else -> null
            }
            val item = CapturedItem(ITEM_TYPE_VIDEO, dateString, uri, displayName)
            camConfig.updateLastCapturedItem(item)

            ctx.updateThumbnail()

            if (ctx is SecureMainActivity) {
                ctx.capturedItems.add(item)
            }
        }

        if (ctx is VideoCaptureActivity) {
            ctx.afterRecording(uri)
        }
    }

    private val dp16 = 16 * mActivity.resources.displayMetrics.density
    private val dp8 = 8 * mActivity.resources.displayMetrics.density

    // Skinned devices wrap the capture button shape in selectors and layer-lists
    private fun findGradientDrawable(drawable: Drawable?): GradientDrawable? {
        return when (drawable) {
            is GradientDrawable -> drawable
            is StateListDrawable -> findGradientDrawable(drawable.current)
            is LayerDrawable -> {
                (0 until drawable.numberOfLayers)
                    .firstNotNullOfOrNull { findGradientDrawable(drawable.getDrawable(it)) }
            }
            else -> null
        }
    }

    // If no shape can be dug out, skip the cosmetic animation rather than crash
    private fun animateCaptureButtonCorners(from: Float, to: Float) {
        val gd = findGradientDrawable(mActivity.captureButton.drawable) ?: return

        val animator = ValueAnimator.ofFloat(from, to)
        animator.setDuration(300)
            .addUpdateListener { animation ->
                gd.cornerRadius = animation.animatedValue as Float
            }
        animator.start()
    }

    private fun beforeRecordingStarts() {
        // Don't leak paused/muted state from the previous recording into this one.
        isPaused = false
        isMuted = false

        // The recording is made with the recorder and audio choice of the press, but its output
        // can take seconds to arrive: these must not rebind the camera or flip the audio meanwhile.
        mActivity.settingsDialog.videoQualitySpinner.isEnabled = false
        mActivity.settingsDialog.enableEISToggle.isEnabled = false
        mActivity.settingsDialog.waitForFocusLockSwitch.isEnabled = false
        mActivity.settingsDialog.includeAudioToggle.isEnabled = false

        mActivity.previewView.keepScreenOn = true
    }

    private fun onRecordingStart() {
        animateCaptureButtonCorners(dp16, dp8)

        // High-speed microphone capture is continuous; do not allow pause (A/V drift).
        if (camConfig.currentMode == CameraMode.SLOW_MOTION) {
            mActivity.flipCameraCircle.visibility = View.INVISIBLE
        }
        // The user may have paused before the recording actually started.
        if (isPaused) {
            mActivity.setFlipCameraIcon(R.drawable.play, R.string.resume_recording)
        } else {
            mActivity.setFlipCameraIcon(R.drawable.pause, R.string.pause_recording)
        }
        mActivity.cancelButtonView.visibility = View.GONE

        // Only the description changes: the drawable stays the same one the corner-radius
        // animation above is holding on to, and replacing it would cut that animation short.
        mActivity.captureButton.contentDescription = mActivity.getString(R.string.stop_recording)

        if (mActivity.requiresVideoModeOnly) {
            mActivity.thirdOption.visibility = View.INVISIBLE
        }

        // While recording, the gallery button turns into a shutter for stills
        if (camConfig.currentMode != CameraMode.SLOW_MOTION) {
            mActivity.setThirdCircleIcon(R.drawable.camera_shutter, R.string.capture)
        } else {
            mActivity.thirdOption.visibility = View.INVISIBLE
        }
        mActivity.tabLayout.visibility = View.INVISIBLE
        mActivity.timerView.setText(R.string.start_value_timer)
        mActivity.timerView.visibility = View.VISIBLE

        if (camConfig.currentMode != CameraMode.SLOW_MOTION && camConfig.includeAudio) {
            mActivity.setMuteToggleState(muted = isMuted)
            mActivity.muteToggle.visibility = View.VISIBLE
        }
    }

    private fun afterRecordingStops() {
        animateCaptureButtonCorners(dp8, dp16)

        mActivity.timerView.visibility = View.GONE
        mActivity.setFlipCameraIcon(R.drawable.flip_camera, R.string.flip_camera)
        mActivity.flipCameraCircle.visibility = View.VISIBLE
        mActivity.captureButton.contentDescription =
            mActivity.getString(R.string.start_recording)

        mActivity.settingsDialog.videoQualitySpinner.isEnabled = true
        mActivity.settingsDialog.enableEISToggle.isEnabled = true

        if (mActivity !is VideoCaptureActivity) {
            mActivity.thirdOption.visibility = View.VISIBLE
        }

        if (!mActivity.requiresVideoModeOnly) {
            mActivity.settingsDialog.waitForFocusLockSwitch.isEnabled = true
        }

        // Restore the gallery button after recording ends.
        mActivity.setThirdCircleIcon(R.drawable.option_circle, R.string.open_gallery)

        if (mActivity !is VideoCaptureActivity) {
            mActivity.cancelButtonView.visibility = View.VISIBLE
            mActivity.tabLayout.visibility = View.VISIBLE
        }

        mActivity.previewView.keepScreenOn = false

        mActivity.settingsDialog.includeAudioToggle.isEnabled = true
        mActivity.muteToggle.visibility = View.GONE

        isRecording = false

        mActivity.forceUpdateOrientationSensor()
    }

    fun muteRecording() {
        if (!isRecording) return
        check(camConfig.includeAudio)
        isMuted = true
        recording?.mute(true)
    }

    fun unmuteRecording() {
        if (!isRecording) return
        check(camConfig.includeAudio)
        isMuted = false
        recording?.mute(false)
    }

    fun stopRecording() {
        stopRequested = true
        slowAudio?.stop()
        cancelDeferredStart?.let {
            it()
            return
        }

        recording?.stop()
        recording?.close()
        recording = null
    }

    // Off the main thread for the same reason the output is created there
    private fun abandonOutput(recordingCtx: RecordingContext) {
        outputExecutor.execute {
            try {
                // A no-op once the recording has started: starting it closed this
                recordingCtx.fileDescriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            discardUnusedOutput(recordingCtx)
        }
    }

    private fun discardUnusedOutput(recordingCtx: RecordingContext) {
        if (!recordingCtx.shouldAddToGallery) {
            return
        }

        try {
            if (recordingCtx.isPendingMediaStoreUri) {
                contentResolver.delete(recordingCtx.uri, null, null)
            } else {
                DocumentsContract.deleteDocument(contentResolver, recordingCtx.uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        // Shared across activities like ImageSaver's: a stalled MediaProvider holds one thread, not
        // one per press
        private val outputExecutor = Executors.newSingleThreadExecutor()


    }
}

private const val STALE_PENDING_RECORDING_AGE = 60 * 60 * 1000L

// Delete old pending video rows left by interrupted recordings.
fun deleteStalePendingRecordings(
    context: Context,
    maxAge: Long = STALE_PENDING_RECORDING_AGE,
) {
    val selection = "${MediaColumns.IS_PENDING} = 1" +
            " AND (${MediaColumns.DISPLAY_NAME} LIKE ?" +
            " OR ${MediaColumns.DISPLAY_NAME} LIKE ?" +
            " OR ${MediaColumns.DISPLAY_NAME} LIKE ?)" +
            " AND ${MediaColumns.DATE_ADDED} < ?"
    val cutoffSeconds = (System.currentTimeMillis() - maxAge) / 1000L
    val args = arrayOf("$VIDEO_NAME_PREFIX%", "SLOMO_%", "HYPER_%", cutoffSeconds.toString())

    try {
        // Pending rows are filtered out of every operation unless they are explicitly asked for.
        @Suppress("DEPRECATION")
        val collection = MediaStore.setIncludePending(CamConfig.videoCollectionUri)
        context.contentResolver.delete(collection, selection, args)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Throws(Exception::class)
fun getVideoThumbnail(context: Context, uri: Uri?): Bitmap? {
    MediaMetadataRetriever().use {
        it.setDataSource(context, uri)
        return it.frameAtTime
    }
}
