package app.grapheneos.camera.capturer

import android.Manifest
import android.animation.ValueAnimator
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
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.R
import app.grapheneos.camera.VIDEO_NAME_PREFIX
import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderScreenModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.util.formatVideoDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class VideoCapturer(private val mActivity: MainActivity) {

    private val viewfinder: ViewfinderScreenModel = mActivity.viewfinder

    private val session = mActivity.session

    val isRecording: Boolean
        get() = viewfinder.uiState.value.isRecordingActive

    private val videoFileFormat = ".mp4"

    private var recording: Recording? = null

    // Invoking this abandons a start still queued behind the record-start sound.
    private var cancelDeferredStart: (() -> Unit)? = null

    val isMuted: Boolean
        get() = viewfinder.uiState.value.isRecordingMuted

    var isPaused: Boolean
        get() = viewfinder.uiState.value.isRecordingPaused
        set(value) {
            if (isRecording) {
                if (value) {
                    recording?.pause()
                } else {
                    recording?.resume()
                }
                reportRecordingState(RecordingAction.RecordingPauseToggled(paused = value))
            }
        }

    private val handler = Handler(Looper.getMainLooper())

    private fun updateTimerTime(timeInNanos: Long) {
        mActivity.timerView.text = formatVideoDuration(timeInNanos / 1_000_000_000)
    }

    private class RecordingContext(
        val pendingRecording: PendingRecording,
        val uri: Uri,
        val fileDescriptor: ParcelFileDescriptor,
        val shouldAddToGallery: Boolean,
        val isPendingMediaStoreUri: Boolean,
    )

    private class RecordingOutput(
        val uri: Uri,
        val shouldAddToGallery: Boolean,
        val isPendingMediaStoreUri: Boolean,
    )

    private suspend fun createRecordingContext(
        recorder: Recorder,
        fileName: String,
    ): RecordingContext? {
        val ctx = mActivity
        val output = createRecordingOutput(fileName) ?: return null

        var location: Location? = null
        if (viewfinder.uiState.value.capture.geoTagging) {
            location = ctx.locationRepository.currentLocation()
            if (location == null) {
                ctx.showMessage(R.string.location_unavailable)
            }
        }

        val fileDescriptor = ctx.captureOutputRepository
            .openForWriting(output.uri)
            .valueOrNull()

        return when (fileDescriptor) {
            null -> null

            else -> {
                val outputOptions = FileDescriptorOutputOptions.Builder(fileDescriptor)
                    .setLocation(location)
                    .build()

                RecordingContext(
                    pendingRecording = recorder.prepareRecording(ctx, outputOptions),
                    uri = output.uri,
                    fileDescriptor = fileDescriptor,
                    shouldAddToGallery = output.shouldAddToGallery,
                    isPendingMediaStoreUri = output.isPendingMediaStoreUri,
                )
            }
        }
    }

    private suspend fun createRecordingOutput(fileName: String): RecordingOutput? {
        val ctx = mActivity
        val foreignUri = (ctx as? VideoCaptureActivity)
            ?.takeIf { it.isOutputUriAvailable() }
            ?.outputUri

        if (foreignUri != null) {
            return RecordingOutput(
                uri = foreignUri,
                shouldAddToGallery = false,
                isPendingMediaStoreUri = false,
            )
        }

        val mimeType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(videoFileFormat) ?: "video/mp4"
        val storageLocation = ctx.capturedItemSession.storageLocation
        val uri = ctx.captureOutputRepository.createVideo(
            storageLocation = storageLocation,
            fileName = fileName,
            mimeType = mimeType,
        ).valueOrNull()

        return when (uri) {
            null -> null

            else -> RecordingOutput(
                uri = uri,
                shouldAddToGallery = true,
                isPendingMediaStoreUri =
                    storageLocation == CapturedItemRepository.MEDIA_STORE_LOCATION,
            )
        }
    }

    fun startRecording() {
        val recorder = recorderForNewRecording() ?: return

        viewfinder.onAction(RecordingAction.RecordingRequested)

        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = VIDEO_NAME_PREFIX + dateString + videoFileFormat

        if (requestsAudioPermission()) return

        val ctx = mActivity

        ctx.applicationScope.launch(ctx.mainDispatcher) {
            val recordingCtx = createRecordingContext(recorder, fileName)
            if (recordingCtx == null) {
                onOutputUnavailable()
                return@launch
            }

            // A stop that arrives while the output is still being created finds nothing to stop,
            // so the start queued behind it has to be abandoned here instead.
            if (!isRecording) {
                closeOutput(recordingCtx)
                discardUnusedOutput(recordingCtx)
                return@launch
            }

            startPreparedRecording(recordingCtx, dateString)
        }
    }

    private fun recorderForNewRecording(): Recorder? {
        return when {
            session.camera == null -> null
            isRecording -> null
            else -> session.videoCapture?.output
        }
    }

    private fun requestsAudioPermission(): Boolean {
        val wantsAudio = viewfinder.uiState.value.capture.includeAudio
        val hasPermission = mActivity
            .checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED

        if (!wantsAudio || hasPermission) {
            return false
        }

        mActivity.restartRecordingWithMicPermission()
        viewfinder.onAction(RecordingAction.RecordingStopped)

        return true
    }

    private fun onOutputUnavailable() {
        val ctx = mActivity
        val foreignUri = ctx is VideoCaptureActivity && ctx.isOutputUriAvailable()
        if (!foreignUri) {
            viewfinder.onAction(CaptureAction.StorageLocationNotFound)
        }
        ctx.showMessage(R.string.unable_to_access_output_file)
        viewfinder.onAction(RecordingAction.RecordingStopped)
    }

    private fun startPreparedRecording(recordingCtx: RecordingContext, dateString: String) {
        val ctx = mActivity
        val pendingRecording = recordingCtx.pendingRecording

        if (viewfinder.uiState.value.capture.includeAudio &&
            ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED
        ) {
            pendingRecording.withAudioEnabled()
        }

        // The sound callback may fire more than once; a second PendingRecording.start() throws.
        var consumed = false

        cancelDeferredStart = {
            consumed = true
            cancelDeferredStart = null
            closeOutput(recordingCtx)
            discardUnusedOutput(recordingCtx)
            afterRecordingStops()
        }

        val onStartSoundPlayed = onStartSoundPlayed@{
            if (consumed) {
                return@onStartSoundPlayed
            }

            consumed = true
            cancelDeferredStart = null

            recording = pendingRecording.start(ctx.mainExecutor) { event ->
                onRecordingEvent(
                    event = event,
                    recordingCtx = recordingCtx,
                    dateString = dateString,
                )
            }

            // The Recording didn't exist yet when the mute/pause setters ran.
            if (isMuted) {
                recording?.mute(true)
            }
            if (isPaused) {
                recording?.pause()
            }

            // FileDescriptorOutputOptions doc says that the file descriptor should be closed by
            // the caller, and that it's safe to do so as soon as pendingRecording.start() returns
            closeOutput(recordingCtx)
        }

        mActivity.tunePlayer.playVRStartSound(handler, onStartSoundPlayed)
    }

    private fun onRecordingEvent(
        event: VideoRecordEvent,
        recordingCtx: RecordingContext,
        dateString: String,
    ) {
        when (event) {
            is VideoRecordEvent.Start -> onRecordingStart()

            is VideoRecordEvent.Status -> {
                updateTimerTime(event.recordingStats.recordedDurationNanos)
            }

            is VideoRecordEvent.Finalize -> {
                onRecordingFinalized(
                    event = event,
                    recordingCtx = recordingCtx,
                    dateString = dateString,
                )
            }
        }
    }

    private fun onRecordingFinalized(
        event: VideoRecordEvent.Finalize,
        recordingCtx: RecordingContext,
        dateString: String,
    ) {
        afterRecordingStops()

        mActivity.tunePlayer.playVRStopSound()

        if (event.hasError() && !keepsWhatItWrote(event, recordingCtx)) {
            return
        }

        saveRecording(recordingCtx, dateString)
    }

    private fun keepsWhatItWrote(
        event: VideoRecordEvent.Finalize,
        recordingCtx: RecordingContext,
    ): Boolean {
        val ctx = mActivity

        return when (event.error) {
            VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> {
                discardUnusedOutput(recordingCtx)
                ctx.showMessage(R.string.recording_too_short_to_be_saved)
                false
            }

            VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
            VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR,
            VideoRecordEvent.Finalize.ERROR_UNKNOWN,
            -> {
                discardUnusedOutput(recordingCtx)
                ctx.showMessage(ctx.getString(R.string.unable_to_save_video_verbose, event.error))
                false
            }

            else -> {
                ctx.showMessage(ctx.getString(R.string.error_during_recording, event.error))

                // The errors left unnamed here (the camera going away, storage running out)
                // finalize whatever was written before they hit, which is worth keeping — but
                // only if anything was.
                val wroteSomething = event.recordingStats.numBytesRecorded != 0L
                if (!wroteSomething) {
                    discardUnusedOutput(recordingCtx)
                }

                wroteSomething
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun saveRecording(recordingCtx: RecordingContext, dateString: String) {
        val ctx = mActivity

        ctx.applicationScope.launch(ctx.mainDispatcher) {
            if (recordingCtx.isPendingMediaStoreUri) {
                val published = ctx.captureOutputRepository.publish(recordingCtx.uri)

                if (published is CaptureOutputResult.Failure) {
                    ctx.showMessage(R.string.unable_to_save_video)
                }
            }

            addRecordingToGallery(recordingCtx, dateString)
        }
    }

    private fun addRecordingToGallery(recordingCtx: RecordingContext, dateString: String) {
        val ctx = mActivity
        val uri = recordingCtx.uri

        if (recordingCtx.shouldAddToGallery) {
            val item = CapturedItem(ITEM_TYPE_VIDEO, dateString, uri)
            ctx.capturedItemSession.recordCapturedItem(item)

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

    private fun onRecordingStart() {
        animateCaptureButtonCorners(dp16, dp8)

        reportRecordingState(RecordingAction.RecordingStarted)

        mActivity.tabLayout.visibility = View.INVISIBLE
        mActivity.timerView.setText(R.string.start_value_timer)
    }

    private fun afterRecordingStops() {
        animateCaptureButtonCorners(dp8, dp16)

        if (mActivity !is VideoCaptureActivity) {
            mActivity.tabLayout.visibility = View.VISIBLE
        }

        reportRecordingState(RecordingAction.RecordingStopped)

        mActivity.forceUpdateOrientationSensor()
    }

    fun muteRecording() {
        if (!isRecording) return
        check(viewfinder.uiState.value.capture.includeAudio)
        viewfinder.onAction(RecordingAction.RecordingMuteToggled(muted = true))
        recording?.mute(true)
    }

    fun unmuteRecording() {
        if (!isRecording) return
        check(viewfinder.uiState.value.capture.includeAudio)
        viewfinder.onAction(RecordingAction.RecordingMuteToggled(muted = false))
        recording?.mute(false)
    }

    fun stopRecording() {
        cancelDeferredStart?.let {
            it()
            return
        }

        recording?.stop()
        recording?.close()
        recording = null
    }

    private fun reportRecordingState(action: RecordingAction) {
        if (mActivity.isDestroyed) {
            return
        }

        viewfinder.onAction(action)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun closeOutput(recordingCtx: RecordingContext) {
        try {
            recordingCtx.fileDescriptor.close()
        } catch (e: Exception) {
            Log.w(TAG, "unable to close the recording output", e)
        }
    }

    private fun discardUnusedOutput(recordingCtx: RecordingContext) {
        if (!recordingCtx.shouldAddToGallery) {
            return
        }

        val ctx = mActivity

        ctx.applicationScope.launch(ctx.mainDispatcher) {
            ctx.captureOutputRepository.delete(recordingCtx.uri)
        }
    }

    private companion object {
        private const val TAG = "VideoCapturer"
    }
}

@Throws(Exception::class)
fun getVideoThumbnail(context: Context, uri: Uri?): Bitmap? {
    MediaMetadataRetriever().use {
        it.setDataSource(context, uri)
        return it.frameAtTime
    }
}
